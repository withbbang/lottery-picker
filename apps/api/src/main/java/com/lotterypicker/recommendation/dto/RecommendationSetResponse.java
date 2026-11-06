package com.lotterypicker.recommendation.dto;

import java.time.LocalDate;
import java.util.List;

public record RecommendationSetResponse(LocalDate date, int round, List<RecommendationLineResponse> lines,
		int historyFromRound, int historyToRound, String disclaimer) {

	public static final String DISCLAIMER = "번호 선택 방식 설명이며 당첨 확률과 무관합니다.";

}
