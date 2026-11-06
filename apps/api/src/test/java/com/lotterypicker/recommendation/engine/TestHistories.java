package com.lotterypicker.recommendation.engine;

import com.lotterypicker.lotto.LottoNumbers;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

/** 엔진 테스트용 합성 회차. 모두 결정적이다. */
final class TestHistories {

	private TestHistories() {
	}

	static HistoricalDraw draw(int round, Integer... numbers) {
		return new HistoricalDraw(round, numbers(numbers));
	}

	static LottoNumbers numbers(Integer... numbers) {
		return new LottoNumbers(List.of(numbers));
	}

	static DrawHistory seeded(long seed, int rounds) {
		Random random = new Random(seed);
		List<HistoricalDraw> draws = new ArrayList<>();
		for (int round = 1; round <= rounds; round++) {
			List<Integer> pool = new ArrayList<>(IntStream.rangeClosed(1, 45).boxed().toList());
			Collections.shuffle(pool, random);
			draws.add(new HistoricalDraw(round, new LottoNumbers(pool.subList(0, 6))));
		}
		return new DrawHistory(draws);
	}

}
