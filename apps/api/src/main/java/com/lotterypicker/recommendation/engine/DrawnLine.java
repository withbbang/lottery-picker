package com.lotterypicker.recommendation.engine;

import com.lotterypicker.lotto.LottoNumbers;
import java.util.List;

/** @param ingredients 근거 문구에 적힌 번호를 구조로 담은 것. 근거 조합이 재료로 쓴다. */
public record DrawnLine(StrategyType strategy, LottoNumbers numbers, List<String> evidence,
		List<Ingredient> ingredients) {

	public DrawnLine(StrategyType strategy, LottoNumbers numbers, List<String> evidence) {
		this(strategy, numbers, evidence, List.of());
	}

}
