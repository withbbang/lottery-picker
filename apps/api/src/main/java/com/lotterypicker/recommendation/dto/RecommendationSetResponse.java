package com.lotterypicker.recommendation.dto;

import com.lotterypicker.notification.NotificationStatus;
import java.time.LocalDate;
import java.util.List;

/**
 * @param carriedOver 오늘 추천이 아직 없어 가장 최근 추천을 대신 보여 주는지
 * @param roundClosed 이 추천의 회차가 판매 마감(토요일 20시)됐는지. 번호는 새 회차에서도 살 수 있다.
 * @param todayPending 이월 중이고 오늘 추천 생성 시각(17시) 전이라 곧 새 추천이 나오는지
 */
public record RecommendationSetResponse(LocalDate date, int round, List<RecommendationLineResponse> lines,
		int historyFromRound, int historyToRound, String disclaimer, NotificationStatus notificationStatus,
		boolean carriedOver, boolean roundClosed, boolean todayPending) {

	public static final String DISCLAIMER = "실제 당첨 확률과 무관합니다.";

}
