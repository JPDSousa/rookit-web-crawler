package org.rookit.crawler;

import static org.rookit.crawler.AvailableServices.values;

import java.io.Closeable;
import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

import org.rookit.crawler.config.MusicServiceConfig;
import org.rookit.dm.album.Album;
import org.rookit.dm.album.similarity.AlbumComparator;
import org.rookit.dm.artist.Artist;
import org.rookit.dm.artist.similarity.ArtistComparator;
import org.rookit.dm.genre.Genre;
import org.rookit.dm.genre.similarity.GenreComparator;
import org.rookit.dm.play.Playable;
import org.rookit.dm.play.StaticPlaylist;
import org.rookit.dm.play.similarity.PlaylistComparator;
import org.rookit.dm.track.Track;
import org.rookit.dm.track.audio.AudioFeature;
import org.rookit.dm.track.audio.TrackKey;
import org.rookit.dm.track.audio.TrackMode;
import org.rookit.dm.track.similarity.TrackComparator;

import com.google.common.collect.Maps;

import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.functions.BiFunction;

@SuppressWarnings("javadoc")
public class RookitCrawler implements Closeable {

	private static final Logger LOGGER = Logger.getLogger(RookitCrawler.class.getName());

	private final Map<Class<?>, Comparator<?>> comparators;
	private final ServiceProvider provider;

	public RookitCrawler(MusicServiceConfig config) {
		provider = new ServiceProviderImpl(config);
		comparators = Maps.newHashMapWithExpectedSize(5);
		comparators.put(Track.class, new TrackComparator());
		comparators.put(Artist.class, new ArtistComparator());
		comparators.put(Album.class, new AlbumComparator());
		comparators.put(Genre.class, new GenreComparator());
		comparators.put(StaticPlaylist.class, new PlaylistComparator());
		LOGGER.info("Crawler created with: " + comparators.keySet().toString());
	}

	public Completable fillTrack(Track source) {
		LOGGER.info("Filling: " + source.getLongFullTitle());
		final Comparator<Track> comparator = getSimilarityCalculator(Track.class);

		return Observable.fromArray(values())
				.map(provider::getService)
				.filter(Optional::isPresent)
				.map(Optional::get)
				.filter(service -> !source.getExternalMetadata().containsKey(service.getName()))
				.flatMapMaybe(service -> service
						.searchTrack(source)
						.reduce(new Accumulator<>(source, comparator)))
				.doAfterNext(track -> resolveTracks(source, track))
				.ignoreElements();
	}

	private void resolveTracks(Track master, Track slave) {
		LOGGER.info("Pushing '" + slave.getLongFullTitle() + "' to " + master.getLongFullTitle());
		if (master.getType() != slave.getType()) {
			throw new RuntimeException("[" + master.getLongFullTitle() + "] and [" + master.getLongFullTitle()
			+ "] do not share the same type.");
		}
		// TODO how to resolve title conflicts??????
		master.getTitle();
		resolveAudioFeatures(master, slave);
		resolvePlayable(master, slave);
		for (String key : slave.getExternalMetadata().keySet()) {
			master.putExternalMetadata(key, slave.getExternalMetadata(key));
		}
		if (master.getHiddenTrack() == null && slave.getHiddenTrack() != null) {
			master.setHiddenTrack(slave.getHiddenTrack());
		}
		if (master.getLyrics() == null && slave.getLyrics() != null) {
			// TODO this can be improved
			master.setLyrics(slave.getLyrics());
		}
		if (slave.isExplicit()) {
			master.setExplicit(slave.isExplicit());
		}
		slave.getGenres().forEach(master::addGenre);
		if (master.isVersionTrack() && !slave.getAsVersionTrack().getVersionToken().isEmpty()) {
			master.getAsVersionTrack().setVersionToken(slave.getAsVersionTrack().getVersionToken());
		}
		if (master.isVersionTrack()) {
			handleArtists(
					Arrays.asList(master.getMainArtists(), master.getFeatures(),
							master.getAsVersionTrack().getVersionArtists(), master.getProducers()),
					Arrays.asList(slave.getMainArtists(), slave.getFeatures(),
							slave.getAsVersionTrack().getVersionArtists(), slave.getProducers()));
		} else {
			handleArtists(Arrays.asList(master.getMainArtists(), master.getFeatures(), master.getProducers()),
					Arrays.asList(slave.getMainArtists(), slave.getFeatures(), slave.getProducers()));
		}
	}
	
	private void resolvePlayable(Playable master, Playable slave) {
		final Duration masterDuration = master.getDuration();
		final Duration slaveDuration = slave.getDuration();
		if ((masterDuration == null || masterDuration.isZero()) 
				&& slaveDuration != null && !slaveDuration.isZero()) {
			master.setDuration(slaveDuration);
		}
	}
	
	private void resolveAudioFeatures(AudioFeature master, AudioFeature slave) {
		final short bpm = slave.getBPM();
		final double danceability = slave.getDanceability();
		final double energy = slave.getEnergy();
		final TrackKey trackKey = slave.getTrackKey();
		final TrackMode trackMode = slave.getTrackMode();
		final double valence = slave.getValence();
		final Boolean acoustic = slave.isAcoustic();
		final Boolean instrumental = slave.isInstrumental();
		final Boolean live = slave.isLive();
		if (bpm > 0) {
			master.setBPM(bpm);
		}
		if (danceability >= 0) {
			master.setDanceability(danceability);
		}
		if (energy >= 0) {
			master.setEnergy(energy);
		}
		if (trackKey != null) {
			master.setTrackKey(trackKey);
		}
		if (trackMode != null) {
			master.setTrackMode(trackMode);
		}
		if (valence >= 0) {
			master.setValence(valence);
		}
		if (acoustic != null) {
			master.setAcoustic(acoustic);
		}
		if (instrumental != null) {
			master.setInstrumental(instrumental);
		}
		if (live != null) {
			master.setLive(live);
		}
	}

	/**
	 * if artist A: master -> MA; slave -> P then A should be moved to producers
	 * Rule: any artist in MA mentioned in F or P moves to F or P What if A is in F
	 * and is mentioned in P (or vice versa)?
	 * 
	 * @param masterArtists
	 * @param slaveArtists
	 */
	private void handleArtists(List<Collection<Artist>> masterArtists, List<Collection<Artist>> slaveArtists) {
		for (int i = 0; i < slaveArtists.size(); i++) {
			final Collection<Artist> slaveGroup = slaveArtists.get(i);
			final Collection<Artist> masterGroup = masterArtists.get(i);
			for (Artist slaveArtist : slaveGroup) {
				// first search in the same group
				boolean found = masterGroup.contains(slaveArtist);
				// if not in same group, search in more general groups
				for (int j = 0; j < i && !found; j++) {
					// if found, remove and add in the appropriate group
					if (masterArtists.get(j).remove(slaveArtist)) {
						masterGroup.add(slaveArtist);
						found = true;
					}
				}
				// if not in same and more general groups, search in more specific groups
				for (int j = i + 1; j < masterArtists.size() && !found; j++) {
					// if found break
					found = masterArtists.get(j).contains(slaveArtist);
				}
				// if not in any group, add to the same group as in slave
				if (!found) {
					masterGroup.add(slaveArtist);
				}
			}
		}
	}

	@SuppressWarnings("unchecked")
	private <T> Comparator<T> getSimilarityCalculator(Class<T> type) {
		return (Comparator<T>) comparators.get(type);
	}

	private final class Accumulator<T> implements BiFunction<T, T, T> {

		private final T master;
		private final Comparator<T> comparator;

		private Accumulator(T master, Comparator<T> comparator) {
			super();
			this.master = master;
			this.comparator = comparator;
		}

		@Override
		public T apply(T t, T u) {
			final int score1 = Math.abs(comparator.compare(master, t));
			final int score2 = Math.abs(comparator.compare(master, u));
			return score1 < score2 ? t : u;
		}

	}

	@Override
	public void close() throws IOException {
		provider.close();
	}

}
