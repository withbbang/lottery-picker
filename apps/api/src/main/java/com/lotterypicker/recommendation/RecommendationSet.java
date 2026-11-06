package com.lotterypicker.recommendation;

import java.time.LocalDate;
import java.util.List;

/** 새로 만든 하루치 추천. 대조군은 포함하지 않는다. */
public record RecommendationSet(LocalDate date, int round, List<StoredLine> lines) {
}
