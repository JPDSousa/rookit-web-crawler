package org.rookit.crawler.similarity;

import java.util.Map;

import org.apache.commons.collections4.map.LazyMap;
import org.rookit.dm.RookitModel;
import org.rookit.dm.album.Album;
import org.rookit.dm.album.similarity.AlbumComparator;
import org.rookit.dm.artist.Artist;
import org.rookit.dm.artist.similarity.ArtistComparator;
import org.rookit.dm.genre.Genre;
import org.rookit.dm.genre.similarity.GenreComparator;
import org.rookit.dm.play.similarity.PlaylistComparator;
import org.rookit.dm.similarity.Similarity;
import org.rookit.dm.similarity.calculator.SimilarityMeasure;
import org.rookit.dm.track.Track;
import org.rookit.dm.track.similarity.TrackComparator;

import com.google.common.collect.Maps;
import com.wrapper.spotify.models.playlist.Playlist;

class SimilarityProviderImpl implements SimilarityProvider {

	private Map<Class<?>, Similarity<?>> similarities;
	
	SimilarityProviderImpl() {
		similarities = LazyMap.lazyMap(Maps.newHashMap(), input -> {
			if(Track.class.isAssignableFrom(input)) {
				return new TrackComparator();
			}
			else if(Artist.class.isAssignableFrom(input)) {
				return new ArtistComparator();
			}
			else if(Album.class.isAssignableFrom(input)) {
				return new AlbumComparator();
			}
			else if(Genre.class.isAssignableFrom(input)) {
				return new GenreComparator();
			}
			else if(Playlist.class.isAssignableFrom(input)) {
				return new PlaylistComparator();
			}
			throw new UnsupportedOperationException("Cannot find a similarity measure for: " + input);
		});
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T extends RookitModel> SimilarityMeasure<T> getMeasure(Class<T> clazz, T base) {
		final Similarity<T> similarity = (Similarity<T>) similarities.get(clazz);
		return SimilarityMeasure.create(base, similarity);
	}
}
