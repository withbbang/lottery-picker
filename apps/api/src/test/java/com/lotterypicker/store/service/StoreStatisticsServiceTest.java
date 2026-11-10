package com.lotterypicker.store.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.lotterypicker.common.BusinessException;
import com.lotterypicker.common.ErrorCode;
import com.lotterypicker.round.repository.WinningStoreRepository;
import com.lotterypicker.store.StoreWin;
import com.lotterypicker.store.dto.AreaCountResponse;
import com.lotterypicker.store.dto.StoreResponse;
import com.lotterypicker.store.dto.StoreStatisticsResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StoreStatisticsServiceTest {

	private static final String ONLINE = "동행복권(dhlottery.co.kr)";

	private WinningStoreRepository repository;
	private StoreStatisticsService service;
	private long nextId;

	@BeforeEach
	void setUp() {
		repository = mock(WinningStoreRepository.class);
		service = new StoreStatisticsService(repository);
		nextId = 1;
	}

	private StoreWin win(int round, String region, String name, String address) {
		return win(round, region, name, address, 37.5, "정상");
	}

	private StoreWin win(int round, String region, String name, String address, Double lat, String status) {
		return new StoreWin(nextId++, round, region, name, address, lat, 127.0, status);
	}

	private void givenRank1(List<StoreWin> wins) {
		given(repository.countByWinRank(1)).willReturn((long) wins.size());
		given(repository.findMaxIdByWinRank(1))
			.willReturn(wins.isEmpty() ? Optional.empty() : Optional.of(wins.get(wins.size() - 1).id()));
		given(repository.findWinsByRank(1)).willReturn(wins);
	}

	private List<StoreWin> seoulAndGyeonggi() {
		return List.of(
			win(1200, "서울", "망우점", "서울 중랑구 망우로 1"),
			win(1210, "서울", "망우점", "서울 중랑구 망우로 1"),
			win(1220, "서울", "강남점", "서울 강남구 테헤란로 1"),
			win(1230, "경기", "수원점", "경기 수원시 장안구 팔달로 1"),
			win(1240, "경기", "분당점", "경기 성남시 분당구 판교로 1"),
			win(1249, "서울", "온라인", ONLINE));
	}

	@Test
	void 시도별_당첨_건수_순위를_준다() {
		// given
		givenRank1(seoulAndGyeonggi());

		// when
		StoreStatisticsResponse result = service.find(1, null, null).orElseThrow();

		// then
		assertThat(result.regions()).containsExactly(
			new AreaCountResponse("서울", 3), new AreaCountResponse("경기", 2), new AreaCountResponse("온라인", 1));
		assertThat(result.districts()).isNull();
		assertThat(result.pins()).isEmpty();
	}

	@Test
	void 광주와_전남_행을_전남광주로_합쳐_센다() {
		// given
		givenRank1(List.of(
			win(1100, "광주", "상무점", "광주 서구 상무대로 1"),
			win(1110, "전남", "여수점", "전남 여수시 시청로 1"),
			win(1240, "전남광주", "담양점", "전남광주 담양군 중앙로 1"),
			win(1241, "서울", "망우점", "서울 중랑구 망우로 1")));

		// when
		StoreStatisticsResponse result = service.find(1, null, null).orElseThrow();

		// then
		assertThat(result.regions()).containsExactly(
			new AreaCountResponse("전남광주", 3), new AreaCountResponse("서울", 1));
	}

	@Test
	void 시도를_고르면_시군구_순위와_핀을_준다() {
		// given
		givenRank1(seoulAndGyeonggi());

		// when
		StoreStatisticsResponse result = service.find(1, "서울", null).orElseThrow();

		// then
		assertThat(result.districts()).containsExactly(
			new AreaCountResponse("중랑구", 2), new AreaCountResponse("강남구", 1));
		assertThat(result.pins()).extracting(StoreResponse::name).containsExactly("망우점", "강남점");
	}

	@Test
	void 시군구를_고르면_그_안만_준다() {
		// given
		givenRank1(seoulAndGyeonggi());

		// when
		StoreStatisticsResponse result = service.find(1, "서울", "중랑구").orElseThrow();

		// then
		assertThat(result.pins()).extracting(StoreResponse::name).containsExactly("망우점");
		assertThat(result.repeatStores()).extracting(StoreResponse::name).containsExactly("망우점");
		assertThat(result.districts()).hasSize(2);
	}

	@Test
	void 같은_판매점은_이름과_주소로_묶는다() {
		// given
		givenRank1(List.of(
			win(1200, "서울", "망우점", "서울 중랑구 망우로 1", 37.1, "정상"),
			win(1210, "서울", "망우점 ", "서울 중랑구 망우로 1  ", 37.2, "폐점")));

		// when
		StoreStatisticsResponse result = service.find(1, "서울", null).orElseThrow();

		// then
		assertThat(result.pins()).singleElement().satisfies(store -> {
			assertThat(store.wins()).isEqualTo(2);
			assertThat(store.lastRound()).isEqualTo(1210);
			assertThat(store.latitude()).isEqualTo(37.2);
			assertThat(store.status()).isEqualTo("폐점");
			assertThat(store.district()).isEqualTo("중랑구");
		});
	}

	@Test
	void 두번_이상_당첨된_판매점만_상위_20개까지_순위에_둔다() {
		// given: 2회 판매점 21곳(뒤로 갈수록 최근), 1회 판매점 1곳
		List<StoreWin> wins = new ArrayList<>();
		for (int i = 0; i < 21; i++) {
			String name = "판매점" + i;
			String address = "서울 중랑구 망우로 " + i;
			wins.add(win(1000 + i, "서울", name, address));
			wins.add(win(1100 + i, "서울", name, address));
		}
		wins.add(win(1249, "서울", "한번점", "서울 강남구 테헤란로 1"));
		givenRank1(wins);

		// when
		StoreStatisticsResponse result = service.find(1, null, null).orElseThrow();

		// then
		assertThat(result.repeatStores()).hasSize(20);
		assertThat(result.repeatStores().get(0).name()).isEqualTo("판매점20");
		assertThat(result.repeatStores()).extracting(StoreResponse::name).doesNotContain("한번점", "판매점0");
	}

	@Test
	void 온라인을_고르면_시군구와_핀이_비어_있다() {
		// given
		givenRank1(List.of(
			win(1248, "서울", "동행복권", ONLINE),
			win(1249, "서울", "동행복권", ONLINE),
			win(1249, "서울", "망우점", "서울 중랑구 망우로 1")));

		// when
		StoreStatisticsResponse result = service.find(1, "온라인", null).orElseThrow();

		// then
		assertThat(result.districts()).isEmpty();
		assertThat(result.pins()).isEmpty();
		assertThat(result.repeatStores()).extracting(StoreResponse::name).containsExactly("동행복권");
	}

	@Test
	void 집계_기간은_그_등수_행의_최소_최대_회차다() {
		// given
		givenRank1(seoulAndGyeonggi());

		// when
		StoreStatisticsResponse result = service.find(1, "경기", null).orElseThrow();

		// then
		assertThat(result.rank()).isEqualTo(1);
		assertThat(result.fromRound()).isEqualTo(1200);
		assertThat(result.toRound()).isEqualTo(1249);
	}

	@Test
	void 없는_지역이면_404() {
		// given
		givenRank1(seoulAndGyeonggi());

		// when / then
		assertNotFound(() -> service.find(1, "화성", null));
		assertNotFound(() -> service.find(1, null, "중랑구"));
		assertNotFound(() -> service.find(1, "서울", "수원시"));
	}

	private void assertNotFound(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
		assertThatThrownBy(call).isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException) e).getErrorCode())
			.isEqualTo(ErrorCode.COMMON_NOT_FOUND);
	}

	@Test
	void 행이_없으면_빈_값() {
		// given
		givenRank1(List.of());

		// when / then
		assertThat(service.find(1, null, null)).isEmpty();
	}

	@Test
	void 행수와_최대_id가_같으면_다시_읽지_않는다() {
		// given
		givenRank1(seoulAndGyeonggi());

		// when
		service.find(1, null, null);
		service.find(1, "서울", null);
		given(repository.findMaxIdByWinRank(1)).willReturn(Optional.of(99L));
		service.find(1, null, null);

		// then
		verify(repository, times(2)).findWinsByRank(1);
	}

}
