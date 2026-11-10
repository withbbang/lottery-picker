package com.lotterypicker.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.lotterypicker.lotto.LottoNumbers;
import com.lotterypicker.lotto.PrizeRank;
import com.lotterypicker.recommendation.RecommendationSet;
import com.lotterypicker.recommendation.StoredLine;
import com.lotterypicker.recommendation.dto.RecommendationSetResponse;
import com.lotterypicker.recommendation.dto.RecommendationSettingsResponse;
import com.lotterypicker.recommendation.engine.NumberMetric;
import com.lotterypicker.recommendation.engine.Relaxation;
import com.lotterypicker.recommendation.engine.StrategyType;
import com.lotterypicker.result.dto.StrategyPerformanceResponse;
import com.lotterypicker.result.dto.StrategyPerformancesResponse;
import com.lotterypicker.round.dto.RoundDetailResponse;
import com.lotterypicker.round.dto.PrizeTierResponse;
import com.lotterypicker.round.dto.WinningStoreResponse;
import com.lotterypicker.statistics.dto.NumberStatisticsResponse;
import com.lotterypicker.statistics.dto.RankedNumberResponse;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BotMessageFormatterTest {

	private static final LocalDate NOV_9 = LocalDate.of(2026, 11, 9);

	private static RecommendationSet set() {
		return new RecommendationSet(NOV_9, 1250, List.of(new StoredLine(StrategyType.RECENT_HOT,
				new LottoNumbers(List.of(1, 11, 19, 27, 38, 44)), List.of("근거"), Relaxation.NONE)));
	}

	private static RecommendationSetResponse latest(boolean carriedOver, boolean todayPending) {
		return new RecommendationSetResponse(NOV_9, 1250, List.of(), 1, 1249, RecommendationSetResponse.DISCLAIMER,
				NotificationStatus.SENT, carriedOver, false, todayPending);
	}

	private static WinningStoreResponse store(String region) {
		return new WinningStoreResponse(1, region, "판매점", "주소", "자동", "정상");
	}

	private static RoundDetailResponse round() {
		return new RoundDetailResponse(1249, List.of(16, 18, 27, 36, 38, 42), 22, LocalDate.of(2026, 11, 7),
				List.of(new PrizeTierResponse(1, 15, 1_933_658_450L), new PrizeTierResponse(2, 91, 53_122_485L),
						new PrizeTierResponse(3, 3265, 1_480_597L), new PrizeTierResponse(4, 167_745, 50_000L),
						new PrizeTierResponse(5, 2_723_467, 5_000L)));
	}

	@Test
	void 도움말은_메뉴_순서로_명령을_보여_준다() {
		// given / when
		String text = BotMessageFormatter.help();

		// then
		assertThat(text).isEqualTo("""
				사용할 수 있는 명령
				/today 오늘 추천
				/result 최근 회차 채점 결과
				/performance 전략별 성적표
				/round 회차 당첨 정보 (예: /round 1249)
				/stats 번호 통계 상위 5개
				/settings 고정·제외 번호 보기
				/help 명령 목록""");
	}

	@Test
	void 이월된_추천은_안내_한_줄을_앞에_붙인다() {
		// given / when
		String text = BotMessageFormatter.today(latest(true, true), set());

		// then
		assertThat(text).isEqualTo("오늘 추천이 아직 없어 11월 9일 추천을 보여 드립니다. 오늘 추천은 17시에 나옵니다.\n\n"
				+ MessageFormatter.recommendation(set()));
	}

	@Test
	void 오늘_추천은_알림과_같은_문구다() {
		// given / when
		String text = BotMessageFormatter.today(latest(false, false), set());

		// then
		assertThat(text).isEqualTo(MessageFormatter.recommendation(set()));
	}

	@Test
	void 회차_정보는_번호_등수별_금액_판매점_지역을_보인다() {
		// given
		List<WinningStoreResponse> stores = List.of(store("서울"), store("경기"), store("서울"), store("온라인"));

		// when
		String text = BotMessageFormatter.round(round(), stores);

		// then
		assertThat(text).isEqualTo("""
				1249회 · 2026-11-07(토)

				<pre>16 · 18 · 27 · 36 · 38 · 42 + 22</pre>

				1등 15명 · 1,933,658,450원
				2등 91명 · 53,122,485원
				3등 3,265명 · 1,480,597원
				4등 167,745명 · 50,000원
				5등 2,723,467명 · 5,000원

				1등 판매점 지역
				서울 2 · 경기 1 · 온라인 1""");
	}

	@Test
	void 판매점이_없으면_그렇게_말한다() {
		// given / when
		String text = BotMessageFormatter.round(round(), List.of());

		// then
		assertThat(text).endsWith("\n\n1등 판매점 정보가 없습니다.");
	}

	@Test
	void 회차_인자는_이스케이프한다() {
		// given / when / then
		assertThat(BotMessageFormatter.roundNotFound("<b>")).isEqualTo("&lt;b&gt;회 당첨 정보가 없습니다.");
	}

	@Test
	void 번호_통계는_지표마다_앞_5개다() {
		// given
		Map<NumberMetric, List<RankedNumberResponse>> metrics = new EnumMap<>(NumberMetric.class);
		metrics.put(NumberMetric.RECENT, List.of(new RankedNumberResponse(27, 9, 1, false),
				new RankedNumberResponse(7, 8, 2, false), new RankedNumberResponse(12, 6, 3, true),
				new RankedNumberResponse(40, 6, 3, true), new RankedNumberResponse(1, 5, 5, false),
				new RankedNumberResponse(2, 4, 6, false)));
		metrics.put(NumberMetric.COLD, List.of(new RankedNumberResponse(33, 0, 1, false)));
		metrics.put(NumberMetric.ALL_TIME, List.of(new RankedNumberResponse(43, 190, 1, false)));
		NumberStatisticsResponse stats = new NumberStatisticsResponse(LocalDate.of(2026, 11, 10), 1, 1199, 1199, 50,
				metrics);

		// when
		String text = BotMessageFormatter.stats(stats);

		// then
		assertThat(text).isEqualTo("""
				번호 통계 · 11월 10일 추천 기준 (1~1199회)

				최근 50회 출현
				<pre>27  9회  1위
				 7  8회  2위
				12  6회  공동 3위
				40  6회  공동 3위
				 1  5회  5위</pre>

				미출현 회차
				<pre>33  직전 회차 출현  1위</pre>

				전체 출현
				<pre>43  190회  1위</pre>

				과거에 자주·드물게 나온 정도일 뿐, 실제 당첨 확률과 무관합니다.""");
	}

	private static StrategyPerformanceResponse performance(StrategyType strategy, int lines, double average,
			double rate, boolean sufficient) {
		return new StrategyPerformanceResponse(strategy, lines, average, Map.of(PrizeRank.FIFTH, 3), rate, lines,
				rate, sufficient);
	}

	@Test
	void 성적표는_숫자를_앞에_두고_표본_부족을_표시한다() {
		// given
		StrategyPerformancesResponse response = new StrategyPerformancesResponse(0.0224, List.of(
				performance(StrategyType.RECENT_HOT, 120, 0.82, 0.025, true),
				performance(StrategyType.CONTROL, 12, 0.75, 0.0, false)));

		// when
		String text = BotMessageFormatter.performance(response);

		// then
		assertThat(text).isEqualTo("""
				전략별 성적표 (채점된 줄 기준)
				5등 이상 비율 · 평균 맞힌 개수 · 줄 수
				<pre>  2.5%  0.82개  120줄  최근 빈도
				  0.0%  0.75개  12줄  대조군 (표본 부족)</pre>
				이론값 2.2%

				실제 당첨 확률과 무관합니다.""");
	}

	@Test
	void 채점한_줄이_없으면_성적표_대신_안내() {
		// given
		StrategyPerformancesResponse response = new StrategyPerformancesResponse(0.0224,
				List.of(performance(StrategyType.RECENT_HOT, 0, 0, 0, false)));

		// when / then
		assertThat(BotMessageFormatter.performance(response)).isEqualTo(BotMessageFormatter.noPerformance());
	}

	@Test
	void 설정이_비어_있으면_없음이다() {
		// given / when
		String text = BotMessageFormatter.settings(new RecommendationSettingsResponse(List.of(3, 7), List.of()));

		// then
		assertThat(text).isEqualTo("""
				고정 번호: 3, 7
				제외 번호: 없음
				바꾸기는 웹의 설정 화면에서 할 수 있습니다.""");
	}

}
