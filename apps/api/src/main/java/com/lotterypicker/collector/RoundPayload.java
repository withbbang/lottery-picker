package com.lotterypicker.collector;

import com.lotterypicker.lotto.LottoNumbers;
import java.time.LocalDate;
import java.util.List;

public record RoundPayload(int round, LottoNumbers numbers, int bonus, LocalDate drawDate, List<PrizeTier> tiers) {
}
