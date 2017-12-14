package org.rookit.crawler.factory;

import static org.rookit.crawler.AvailableServices.*;
import static org.rookit.crawler.MusicService.*;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Date;
import java.util.Set;

import org.bson.Document;
import org.rookit.crawler.config.MusicServiceConfig;
import org.rookit.crawler.utils.CrawlerIOUtils;
import org.rookit.dm.album.Album;
import org.rookit.dm.artist.Artist;
import org.rookit.dm.track.Track;
import org.rookit.parser.result.SingleTrackAlbumBuilder;

import de.umass.lastfm.ImageSize;
import de.umass.lastfm.MusicEntry;

@SuppressWarnings("javadoc")
public class LastFMFactory extends AbstractModelFactory<de.umass.lastfm.Artist, de.umass.lastfm.Album, de.umass.lastfm.Track> {

	public LastFMFactory(MusicServiceConfig config, int levenshteinThreshold) {
		super(config, levenshteinThreshold);
	}

	@Override
	public Track toTrack(de.umass.lastfm.Track source) {
		final Set<Artist> artists = toArtists(source.getArtist(), source.getArtistMbid());
		final Document mBrainz = new Document(ID, source.getMbid());
		final Document lastFM = new Document(ID, source.getId())
				.append(LISTENERS, source.getListeners())
				.append(LOCATION, source.getLocation())
				.append(URL, source.getUrl())
				.append(TAGS, source.getTags())
				.append(WIKI, source.getWikiSummary())
				.append(PLAYS, source.getPlaycount());
		final SingleTrackAlbumBuilder builder = parseTrackTitle(source.getName())
				.withAlbum(toAlbum(source, artists))
				.withMainArtists(artists)
				.withExternalMetadata(MBRAINZ.name(), mBrainz)
				.withExternalMetadata(LASTFM.name(), lastFM)
				.withDuration(Duration.ofSeconds(source.getDuration()));
		final Track track = builder.getTrack();
		final long plays = source.getPlaycount();
		if(plays > 0) {
			track.setPlays(plays);
		}
		return track;
	}

	@Override
	public Album toAlbum(de.umass.lastfm.Album source) {
		final Set<Artist> artists = artistFactory.getArtistsFromFormat(source.getArtist());
		final Album album = albumFactory.createSingleArtistAlbum(source.getName(), artists);
		final Document mBrainz = new Document(ID, source.getMbid());
		final Document lastFM = new Document(ID, source.getId())
				.append(PLAYS, source.getPlaycount())
				.append(LISTENERS, source.getListeners())
				.append(TAGS, source.getTags())
				.append(URL, source.getUrl())
				.append(WIKI, source.getWikiSummary());
		album.putExternalMetadata(MBRAINZ.name(), mBrainz);
		album.putExternalMetadata(LASTFM.name(), lastFM);
		final byte[] cover = getBiggest(source);
		if(cover != null) {
			album.setCover(cover);
		}
		final LocalDate releaseDate = getReleaseDate(source);
		if(releaseDate != null) {
			album.setReleaseDate(releaseDate);
		}
		final Collection<de.umass.lastfm.Track> tracks = source.getTracks();
		if(tracks != null) {
			tracks.forEach(track -> album.addTrack(toTrack(track), track.getPosition()));
		}
		return album;
	}
	
	private byte[] getBiggest(MusicEntry source) {
		final ImageSize biggest = source.availableSizes().parallelStream()
				.reduce((left, right) -> compare(left, right, source))
				.get();
		if(biggest != null && !source.getImageURL(biggest).isEmpty()) {
			return CrawlerIOUtils.downloadImage(source.getImageURL(biggest));
		}
		return null;
	}
	
	private ImageSize compare(ImageSize left, ImageSize right, MusicEntry source) {
		final String urlLeft = source.getImageURL(left);
		final String urlRight = source.getImageURL(right);
		if(urlLeft == null || urlLeft.isEmpty()) {
			return right;
		}
		if(urlRight == null || urlRight.isEmpty()) {
			return left;
		}
		return left.compareTo(right) > 0 ? left : right;
	}
	
	private LocalDate getReleaseDate(de.umass.lastfm.Album source) {
		final Date releaseDate = source.getReleaseDate();
		return releaseDate != null ? releaseDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate() : null;
	}
	
	private Album toAlbum(de.umass.lastfm.Track track, Set<Artist> artists) {
		final Album album = albumFactory.createSingleArtistAlbum(track.getAlbum(), artists);
		final Document mBrainz = new Document(ID, track.getAlbumMbid());
		album.setCover(getBiggest(track));
		album.putExternalMetadata(MBRAINZ.name(), mBrainz);
		
		return album;
	}

	@Override
	public Artist toArtist(de.umass.lastfm.Artist source, String originalName) {
		final Set<Artist> artists = artistFactory.getArtistsFromFormat(source.getName());
		final Artist artist = bestMatchArtist(artists, originalName);
		if(artist != null) {
			final byte[] cover = getBiggest(source);
			final Document lastFM = new Document(ID, source.getId())
					.append(LISTENERS, source.getListeners())
					.append(TAGS, source.getTags())
					.append(URL, source.getUrl())
					.append(WIKI, source.getWikiSummary())
					.append(PLAYS, source.getPlaycount());
			artist.putExternalMetadata(LASTFM.name(), lastFM);
			// only sets the mbid if artists = artist
			setMBid(source.getMbid(), artists);
			if(cover != null) {
				artist.setPicture(cover);
			}
			return artist;
		}
		return null;
	}

	@Override
	public Set<Artist> toArtist(de.umass.lastfm.Artist source) {
		return artistFactory.getArtistsFromFormat(source.getName());
	}

}
