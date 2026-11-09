package com.lotterypicker.lotto;

/** 6/45 등수. 1등 6개 / 2등 5개+보너스 / 3등 5개 / 4등 4개 / 5등 3개 / 그 외 낙첨. 보너스는 2등 판정에만 쓴다. */
public enum PrizeRank {

	FIRST, SECOND, THIRD, FOURTH, FIFTH, NONE;

	public static PrizeRank of(LottoNumbers line, LottoNumbers winning, int bonus) {
		return switch (line.countMatches(winning)) {
			case 6 -> FIRST;
			case 5 -> line.contains(bonus) ? SECOND : THIRD;
			case 4 -> FOURTH;
			case 3 -> FIFTH;
			default -> NONE;
		};
	}

}
