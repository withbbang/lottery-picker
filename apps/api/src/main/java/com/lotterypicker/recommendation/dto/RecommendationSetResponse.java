package com.lotterypicker.recommendation.dto;

import com.lotterypicker.notification.NotificationStatus;
import java.time.LocalDate;
import java.util.List;

public record RecommendationSetResponse(LocalDate date, int round, List<RecommendationLineResponse> lines,
		int historyFromRound, int historyToRound, String disclaimer, NotificationStatus notificationStatus) {

	public static final String DISCLAIMER = "실제 당첨 확률과 무관합니다.";

}
