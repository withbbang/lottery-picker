package com.lotterypicker.recommendation;

import com.lotterypicker.lotto.LottoNumbers;
import com.lotterypicker.recommendation.engine.Relaxation;
import com.lotterypicker.recommendation.engine.StrategyType;
import java.util.List;

public record StoredLine(StrategyType strategy, LottoNumbers numbers, List<String> evidence, Relaxation relaxation) {
}
