package com.lotterypicker.recommendation.engine;

import java.util.Comparator;
import java.util.List;

/** 통계 입력 회차. 입력 순서와 무관하게 회차 오름차순으로 정규화한다. */
public record DrawHistory(List<HistoricalDraw> draws) {

	public DrawHistory {
		draws = draws.stream().sorted(Comparator.comparingInt(HistoricalDraw::round)).toList();
	}

	public DrawHistory before(int round) {
		return new DrawHistory(draws.stream().filter(draw -> draw.round() < round).toList());
	}

}
