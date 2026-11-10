package com.lotterypicker.result.dto;

import java.time.LocalDate;
import java.util.List;

/** 하루치 추천 한 벌과 줄별 채점 결과. 대조군은 넣지 않는다. */
public record RecommendationHistoryResponse(LocalDate date, int round, List<RecommendationHistoryLineResponse> lines) {
}
