package com.lotterypicker.round.dto;

import com.lotterypicker.round.entity.LottoRound;
import java.time.LocalDate;
import java.util.List;

public record RoundDetailResponse(int round, List<Integer> numbers, int bonus, LocalDate drawDate,
		List<PrizeTierResponse> prizes) {

	public static RoundDetailResponse from(LottoRound r) {
		return new RoundDetailResponse(r.getRoundNo(), r.numbers().values(), r.getBonus(), r.getDrawDate(),
			List.of(
				new PrizeTierResponse(1, r.getRank1Count(), r.getRank1Amount()),
				new PrizeTierResponse(2, r.getRank2Count(), r.getRank2Amount()),
				new PrizeTierResponse(3, r.getRank3Count(), r.getRank3Amount()),
				new PrizeTierResponse(4, r.getRank4Count(), r.getRank4Amount()),
				new PrizeTierResponse(5, r.getRank5Count(), r.getRank5Amount())));
	}

}
