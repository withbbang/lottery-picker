package com.lotterypicker.recommendation.engine;

import com.lotterypicker.lotto.LottoNumbers;
import java.util.List;

public record DrawnLine(StrategyType strategy, LottoNumbers numbers, List<String> evidence) {
}
