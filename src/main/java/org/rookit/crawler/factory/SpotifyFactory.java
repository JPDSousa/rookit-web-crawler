package org.rookit.crawler.factory;

import org.rookit.dm.track.Track;

import com.google.common.collect.Sets;
import com.wrapper.spotify.models.AlbumType;
import com.wrapper.spotify.models.Image;
import com.wrapper.spotify.models.SimpleAlbum;
import com.wrapper.spotify.models.SimpleArtist;

import static org.rookit.crawler.AvailableServices.SPOTIFY;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.rookit.crawler.MusicService.*;

import org.apache.commons.lang3.tuple.Pair;
import org.bson.Document;
import org.rookit.crawler.utils.CrawlerIOUtils;
import org.rookit.dm.album.Album;
import org.rookit.dm.album.TypeRelease;
import org.rookit.dm.artist.Artist;
import org.rookit.dm.genre.Genre;

@SuppressWarnings("javadoc")
public class SpotifyFactory extends AbstractModelFactory<com.wrapper.spotify.models.Artist, com.wrapper.spotify.models.Album, com.wrapper.spotify.models.Track> {



	@Override
	public Track toTrack(com.wrapper.spotify.models.Track source) {
		// TODO add these fields
		source.getExternalIds();
		source.getExternalUrls();
		final Document spotify = new Document(ID, source.getId())
				.append(POPULARITY, source.getPopularity())
				.append(MARKETS, source.getAvailableMarkets())
				.append(URL, source.getHref())
				.append(PREVIEW, source.getPreviewUrl())
				.append(URI, source.getUri());
		final Set<Artist> artists = source.getArtists().parallelStream()
				.map(this::flatArtist)
				.flatMap(Collection::parallelStream)
				.collect(Collectors.toSet());
		return parser.parse(source.getName())
				.withMainArtists(artists)
				.withAlbum(toAlbum(source.getAlbum(), artists))
				.withDisc(source.getDiscNumber()+"")
				.withNumber(source.getTrackNumber())
				.withDuration(Duration.ofMillis(source.getDuration()))
				.withExplicit(source.isExplicit())
				.withExternalMetadata(SPOTIFY.name(), spotify)
				.getTrack();
	}

	@Override
	public Album toAlbum(com.wrapper.spotify.models.Album source) {
		// TODO Auto-generated method stub
		return null;
	}

	private Album toAlbum(SimpleAlbum source, Set<Artist> artists) {
		final Pair<TypeRelease, String> pair = TypeRelease.parseAlbumName(source.getName(), toTypeRelease(source.getAlbumType()));
		final Album album = albumFactory.createSingleArtistAlbum(pair.getRight(), pair.getLeft(), artists);
		final Document spotify = new Document(ID, source.getId())
				.append(MARKETS, source.getAvailableMarkets())
				.append(URL, source.getHref())
				.append(URI, source.getUri());
		// TODO handle these fields
		source.getExternalUrls();
		album.setCover(biggest(source.getImages()));
		album.putExternalMetadata(SPOTIFY.name(), spotify);
		return album;
	}
	
	private byte[] biggest(List<Image> images) {
		final Image biggest = images.stream()
				.reduce(this::compare)
				.get();
		if(biggest != null) {
			return CrawlerIOUtils.downloadImage(biggest.getUrl());
		}
		return null;
	}
	
	private Image compare(Image left, Image right) {
		final int dimLeft = left.getHeight()*left.getWidth();
		final int dimRight = right.getHeight()*right.getWidth();
		return dimLeft > dimRight ? left : right;
	}

	private TypeRelease toTypeRelease(AlbumType albumType) {
		switch(albumType) {
		case ALBUM:
			return TypeRelease.STUDIO;
		case COMPILATION:
			return TypeRelease.COMPILATION;
		case SINGLE:
			return TypeRelease.SINGLE;
		}
		return null;
	}

	@Override
	public Artist toArtist(com.wrapper.spotify.models.Artist source, String originalName) {
		final Set<Artist> artists = artistFactory.getArtistsFromFormat(source.getName());
		final Set<Genre> genres = Sets.newLinkedHashSetWithExpectedSize(source.getGenres().size());
		final Artist artist = bestMatchArtist(artists, originalName);
		for(String genreName : source.getGenres()) {
			genres.add(genreFactory.createGenre(genreName));
		}
		if(artist != null) {
			artist.setGenres(genres);
			artist.setPicture(biggest(source.getImages()));
			//TODO add these fields
			source.getFollowers();
			source.getId();
			source.getImages();
			source.getPopularity();
		}
		return artist;
	}

	private Set<Artist> flatArtist(SimpleArtist source) {
		final Set<Artist> flatArtists = artistFactory.getArtistsFromFormat(source.getName());
		for(Artist artist : flatArtists) {
			Document spotify = artist.getExternalMetadata(SPOTIFY.name());
			if(spotify == null) {
				spotify = new Document();
				artist.putExternalMetadata(SPOTIFY.name(), spotify);
			}
			spotify.append(ID, source.getId())
			.append(URL, source.getHref())
			.append(URI, source.getUri());
			//TODO add these fields
			source.getExternalUrls();
		}
		return flatArtists;
	}

}
