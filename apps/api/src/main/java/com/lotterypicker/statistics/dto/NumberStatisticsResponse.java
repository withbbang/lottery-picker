package com.lotterypicker.statistics.dto;

import com.lotterypicker.recommendation.engine.NumberMetric;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 한 날짜 추천을 만들 때 쓴 회차 범위 기준의 번호 통계.
 * @param date 기준 추천 날짜
 * @param totalRounds 범위 안에 있는 회차 수
 * @param recentWindow 최근 지표의 기준 회차 수
 * @param metrics 지표마다 45개 번호를 순위 순서대로. 화면은 이 순서 그대로 그린다.
 */
public record NumberStatisticsResponse(LocalDate date, int historyFromRound, int historyToRound, int totalRounds,
		int recentWindow, Map<NumberMetric, List<RankedNumberResponse>> metrics) {
}
