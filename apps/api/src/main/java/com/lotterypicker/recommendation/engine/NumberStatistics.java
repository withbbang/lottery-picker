package com.lotterypicker.recommendation.engine;

import com.lotterypicker.lotto.LottoNumbers;
import java.util.Arrays;
import java.util.List;
import java.util.function.ToIntFunction;

/** 입력 회차의 번호별 통계. 재추출 중 반복 호출되므로 생성 시 미리 계산한다. */
public final class NumberStatistics {

	private static final int LOW_MAX = 22;
	private static final int NEVER = -1;

	private final int totalRounds;
	private final int[][] cumulative;
	private final int[] lastIndex;
	private final int[][] pairs;
	private final IntRange oddCountRange;
	private final IntRange lowCountRange;
	private final IntRange sumRange;

	private NumberStatistics(List<LottoNumbers> draws) {
		totalRounds = draws.size();
		cumulative = new int[LottoNumbers.MAX + 1][totalRounds + 1];
		lastIndex = new int[LottoNumbers.MAX + 1];
		pairs = new int[LottoNumbers.MAX + 1][LottoNumbers.MAX + 1];
		Arrays.fill(lastIndex, NEVER);

		for (int i = 0; i < totalRounds; i++) {
			List<Integer> values = draws.get(i).values();
			for (int number = LottoNumbers.MIN; number <= LottoNumbers.MAX; number++) {
				cumulative[number][i + 1] = cumulative[number][i];
			}
			for (int number : values) {
				cumulative[number][i + 1]++;
				lastIndex[number] = i;
				for (int other : values) {
					if (other != number) {
						pairs[number][other]++;
					}
				}
			}
		}

		oddCountRange = interquartile(draws, NumberStatistics::oddCount);
		lowCountRange = interquartile(draws, NumberStatistics::lowCount);
		sumRange = interquartile(draws, NumberStatistics::sum);
	}

	public static NumberStatistics from(DrawHistory history) {
		return new NumberStatistics(history.draws().stream().map(HistoricalDraw::numbers).toList());
	}

	static int oddCount(LottoNumbers numbers) {
		return (int) numbers.values().stream().filter(n -> n % 2 == 1).count();
	}

	static int lowCount(LottoNumbers numbers) {
		return (int) numbers.values().stream().filter(n -> n <= LOW_MAX).count();
	}

	static int sum(LottoNumbers numbers) {
		return numbers.values().stream().mapToInt(Integer::intValue).sum();
	}

	public int totalRounds() {
		return totalRounds;
	}

	public int frequency(int number) {
		return cumulative[number][totalRounds];
	}

	public int recentFrequency(int number, int window) {
		int start = Math.max(0, totalRounds - window);
		return cumulative[number][totalRounds] - cumulative[number][start];
	}

	/** 마지막 출현 뒤로 지난 회차 수. 최신 회차에 나왔으면 0, 나온 적 없으면 전체 회차 수다. */
	public int roundsSinceLast(int number) {
		return lastIndex[number] == NEVER ? totalRounds : totalRounds - 1 - lastIndex[number];
	}

	public int pairCount(int a, int b) {
		return pairs[a][b];
	}

	public IntRange oddCountRange() {
		return oddCountRange;
	}

	public IntRange lowCountRange() {
		return lowCountRange;
	}

	public IntRange sumRange() {
		return sumRange;
	}

	private static IntRange interquartile(List<LottoNumbers> draws, ToIntFunction<LottoNumbers> metric) {
		if (draws.isEmpty()) {
			return new IntRange(0, 0);
		}
		int[] sorted = draws.stream().mapToInt(metric).sorted().toArray();
		return new IntRange(nearestRank(sorted, 25), nearestRank(sorted, 75));
	}

	private static int nearestRank(int[] sorted, int percentile) {
		int rank = (percentile * sorted.length + 99) / 100;
		return sorted[rank - 1];
	}

}
