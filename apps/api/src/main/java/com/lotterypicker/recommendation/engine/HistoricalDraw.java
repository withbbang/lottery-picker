package com.lotterypicker.recommendation.engine;

import com.lotterypicker.lotto.LottoNumbers;

public record HistoricalDraw(int round, LottoNumbers numbers) {
}
