package com.lotterypicker.collector;

import com.lotterypicker.lotto.LottoNumbers;
import java.time.LocalDate;
import java.util.List;

public record RoundPayload(int round, LottoNumbers numbers, int bonus, LocalDate drawDate, List<PrizeTier> tiers) {

	/**
	 * 추첨 직후에는 번호가 먼저 나오고 등수별 당첨자 수와 금액이 비어 있을 수 있다. 3~5등은 매회 당첨자가 있으므로
	 * 이 중 하나라도 0이면 아직 공개 전으로 본다. 1·2등은 당첨자가 없는 회차가 있어 보지 않는다.
	 */
	public boolean prizesPublished() {
		return tiers.stream()
			.filter(tier -> tier.rank() >= 3)
			.allMatch(tier -> tier.winnerCount() > 0 && tier.amountPerWinner() > 0);
	}

}
