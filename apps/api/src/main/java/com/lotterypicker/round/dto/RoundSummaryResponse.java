package com.lotterypicker.round.dto;

import com.lotterypicker.round.entity.LottoRound;
import java.time.LocalDate;
import java.util.List;

public record RoundSummaryResponse(int round, List<Integer> numbers, int bonus, LocalDate drawDate) {

	public static RoundSummaryResponse from(LottoRound r) {
		return new RoundSummaryResponse(r.getRoundNo(), r.numbers().values(), r.getBonus(), r.getDrawDate());
	}

}
