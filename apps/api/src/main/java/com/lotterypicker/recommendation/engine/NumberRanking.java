package com.lotterypicker.recommendation.engine;

import com.lotterypicker.lotto.LottoNumbers;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** 45개 번호를 한 지표로 줄 세운다. 값이 클수록 위, 같은 값은 번호 오름차순. */
public final class NumberRanking {

	private NumberRanking() {
	}

	public static List<RankedNumber> rank(NumberStatistics stats, NumberMetric metric) {
		record Entry(int number, int value) {
		}
		List<Entry> sorted = IntStream.rangeClosed(LottoNumbers.MIN, LottoNumbers.MAX)
			.mapToObj(n -> new Entry(n, metric.valueOf(stats, n)))
			.sorted(Comparator.comparingInt(Entry::value).reversed().thenComparingInt(Entry::number))
			.toList();
		Map<Integer, Long> counts = sorted.stream()
			.collect(Collectors.groupingBy(Entry::value, Collectors.counting()));

		List<RankedNumber> result = new ArrayList<>();
		int rank = 0;
		for (int i = 0; i < sorted.size(); i++) {
			Entry entry = sorted.get(i);
			if (i == 0 || entry.value() != sorted.get(i - 1).value()) {
				rank = i + 1;
			}
			result.add(new RankedNumber(entry.number(), entry.value(), rank, counts.get(entry.value()) > 1));
		}
		return List.copyOf(result);
	}

}
