package com.lotterypicker.recommendation.engine;

import com.lotterypicker.lotto.LottoNumbers;

/**
 * 통계 입력 회차. 통계는 본번호만 쓰고, 보너스는 시뮬레이션이 2등을 판정할 때만 쓴다.
 * 보너스를 모르는 입력은 {@link #NO_BONUS}(0)로 두며, 이때 5개 일치는 3등으로 판정된다.
 */
public record HistoricalDraw(int round, LottoNumbers numbers, int bonus) {

	public static final int NO_BONUS = 0;

	public HistoricalDraw(int round, LottoNumbers numbers) {
		this(round, numbers, NO_BONUS);
	}

}
