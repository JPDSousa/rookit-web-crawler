package org.rookit.crawler;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.text.similarity.LevenshteinDistance;
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

import com.google.common.util.concurrent.RateLimiter;

abstract class AbstractMusicService implements MusicService {

	protected final Parser<String, SingleTrackAlbumBuilder> parser;
	protected final RateLimiter limiter;
	protected final LevenshteinDistance distance;
	protected final int levenshteinThreshold;
	
	protected final ArtistFactory artistFactory;
	protected final AlbumFactory albumFactory;
	protected final GenreFactory genreFactory;
	
	protected AbstractMusicService(int rateLimit) {
		this(rateLimit, 10);
	}
	
	protected AbstractMusicService(int rateLimit, int levenshteinThreshold) {
		parser = createParser(); 
		limiter = RateLimiter.create(rateLimit);
		this.artistFactory = ArtistFactory.getDefault();
		this.albumFactory = AlbumFactory.getDefault();
		this.genreFactory = GenreFactory.getDefault();
		this.distance = LevenshteinDistance.getDefaultInstance();
		this.levenshteinThreshold = levenshteinThreshold;
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
