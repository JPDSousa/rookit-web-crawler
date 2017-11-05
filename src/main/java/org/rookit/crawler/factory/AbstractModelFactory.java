package org.rookit.crawler.factory;

import static org.rookit.crawler.AvailableServices.*;
import static org.rookit.crawler.MusicService.*;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.bson.Document;
import org.rookit.dm.album.AlbumFactory;
import org.rookit.dm.artist.Artist;
import org.rookit.dm.artist.ArtistFactory;
import org.rookit.dm.genre.GenreFactory;
import org.rookit.parser.config.ParserConfiguration;
import org.rookit.parser.config.ParsingConfig;
import org.rookit.parser.formatlist.FormatList;
import org.rookit.parser.parser.Field;
import org.rookit.parser.parser.Parser;
import org.rookit.parser.parser.ParserFactory;
import org.rookit.parser.result.SingleTrackAlbumBuilder;

import com.google.common.collect.Maps;

abstract class AbstractModelFactory<Ar, Al, Tr> implements ModelFactory<Ar, Al, Tr> {
	
	protected final Parser<String, SingleTrackAlbumBuilder> parser;
	protected final LevenshteinDistance distance;
	protected final int levenshteinThreshold;
	
	protected final ArtistFactory artistFactory;
	protected final AlbumFactory albumFactory;
	protected final GenreFactory genreFactory;
	
	protected AbstractModelFactory() {
		this(10);
	}
	
	protected AbstractModelFactory(int levenshteinThreshold) {
		parser = createParser(); 
		this.artistFactory = ArtistFactory.getDefault();
		this.albumFactory = AlbumFactory.getDefault();
		this.genreFactory = GenreFactory.getDefault();
		this.distance = LevenshteinDistance.getDefaultInstance();
		this.levenshteinThreshold = levenshteinThreshold;
	}
	
	protected void setMBid(String id, final Set<Artist> artists) {
		if(artists.size() == 1) {
			final Document mBrainz = new Document(ID, id);
			//get single artist
			for(Artist artist : artists) {
				artist.putExternalMetadata(MBRAINZ.name(), mBrainz);
			}
		}
	}
	
	protected Parser<String, SingleTrackAlbumBuilder> createParser() {
		try {
			FormatList formats = FormatList.readFromPath(Paths.get("src", "main", "resources", "parser", "formats.txt"));
			final ParserFactory factory = ParserFactory.create();
			final ParsingConfig topConfig = new ParsingConfig();
			final ParserConfiguration config = ParserConfiguration.create(SingleTrackAlbumBuilder.class, topConfig);
			config.withRequiredFields(new Field[0]);
			config.withTrackFormats(formats.getAll().collect(Collectors.toList()));
			return factory.newFormatParser(config);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	protected SingleTrackAlbumBuilder parseTrackTitle(String title) {
		final SingleTrackAlbumBuilder result = parser.parse(title);
		// TODO set score in else clause
		return result.getScore() > 0 ? result : SingleTrackAlbumBuilder.create().withTitle(title);
	}
	
	protected Set<Artist> toArtists(String name, String mbId) {
		final Set<Artist> artists = artistFactory.getArtistsFromFormat(name);
		setMBid(mbId, artists);
		return artists;
	}
	
	protected Artist bestMatchArtist(Collection<Artist> artists, String originalName) {
		final Map<String, Artist> names = artists.stream()
				.collect(Collectors.groupingBy(
						Artist::getName, 
						Maps::newLinkedHashMap, 
						Collectors.collectingAndThen(
								Collectors.reducing((left, right) -> left), 
								Optional::get)));
		
		final String name = bestMatch(names.keySet(), originalName);
		if(name != null) {
			return names.get(name);
		}
		return null;
	}
	
	protected String bestMatch(Collection<String> search, String str) {
		if(search.isEmpty()) {
			return null;
		}
		final String lowerString = str.toLowerCase();
		return search.parallelStream()
				.map(String::toLowerCase)
				.filter(searchStr -> !containsAny(searchStr, Artist.SUSPICIOUS_NAME_CHARSEQS))
				.map(searchStr -> Pair.of(searchStr, distance.apply(searchStr, lowerString)))
				.filter(pair -> pair.getRight() >= 0)
				.filter(pair -> pair.getRight() < levenshteinThreshold)
				.reduce(this::compare)
				.map(Pair::getLeft)
				.orElse(null);
	}
	
	private boolean containsAny(String searchStr, String[] strs) {
		return Arrays.stream(strs).anyMatch(str -> searchStr.contains(str));
	}

	private Pair<String, Integer> compare(Pair<String, Integer> left, Pair<String, Integer> right) {
		final int leftScore = left.getRight();
		final int rigthScore = right.getRight();
		return leftScore < rigthScore ? left : right;
	}

}
