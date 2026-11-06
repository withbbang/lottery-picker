package com.lotterypicker.recommendation.engine;

import com.lotterypicker.lotto.LottoNumbers;
import java.util.List;
import java.util.Set;

/** 1등 시 나눠 가질 사람이 많을 만한 조합을 가려낸다. 당첨 확률과는 무관하다. */
public final class PopularityFilter {

	private static final int BIRTHDAY_MAX = 31;
	private static final int CONSECUTIVE_LIMIT = 4;

	private PopularityFilter() {
	}

	public static boolean isPopular(LottoNumbers n, Set<LottoNumbers> pastWinners) {
		List<Integer> values = n.values();
		return values.get(values.size() - 1) <= BIRTHDAY_MAX
				|| hasConsecutiveRun(values)
				|| isArithmetic(values)
				|| pastWinners.contains(n);
	}

	private static boolean hasConsecutiveRun(List<Integer> values) {
		int run = 1;
		for (int i = 1; i < values.size(); i++) {
			run = values.get(i) == values.get(i - 1) + 1 ? run + 1 : 1;
			if (run >= CONSECUTIVE_LIMIT) {
				return true;
			}
		}
		return false;
	}

	private static boolean isArithmetic(List<Integer> values) {
		int step = values.get(1) - values.get(0);
		for (int i = 2; i < values.size(); i++) {
			if (values.get(i) - values.get(i - 1) != step) {
				return false;
			}
		}
		return true;
	}

}
