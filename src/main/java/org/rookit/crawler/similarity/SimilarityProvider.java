package org.rookit.crawler.similarity;

import org.rookit.dm.RookitModel;
import org.rookit.dm.similarity.calculator.SimilarityMeasure;

@SuppressWarnings("javadoc")
public interface SimilarityProvider {

	static SimilarityProvider create() {
		return new SimilarityProviderImpl();
	}
	
	<T extends RookitModel> SimilarityMeasure<T> getMeasure(Class<T> clazz, T base);
}
