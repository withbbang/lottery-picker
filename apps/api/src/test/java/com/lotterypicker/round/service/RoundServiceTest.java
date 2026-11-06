package com.lotterypicker.round.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.lotterypicker.common.BusinessException;
import com.lotterypicker.common.ErrorCode;
import com.lotterypicker.common.PageResponse;
import com.lotterypicker.lotto.LottoNumbers;
import com.lotterypicker.round.dto.RoundDetailResponse;
import com.lotterypicker.round.dto.RoundSummaryResponse;
import com.lotterypicker.round.dto.WinningStoreResponse;
import com.lotterypicker.round.entity.LottoRound;
import com.lotterypicker.round.entity.WinningStore;
import com.lotterypicker.round.repository.LottoRoundRepository;
import com.lotterypicker.round.repository.WinningStoreRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class RoundServiceTest {

	@Mock
	private LottoRoundRepository roundRepository;

	@Mock
	private WinningStoreRepository storeRepository;

	@InjectMocks
	private RoundService service;

	private LottoRound round(int no) {
		return LottoRound.builder()
			.roundNo(no)
			.numbers(new LottoNumbers(List.of(16, 18, 27, 36, 38, 42)))
			.bonus(22)
			.drawDate(LocalDate.of(2026, 10, 31))
			.rank1Count(15).rank1Amount(1_933_658_450L)
			.rank2Count(91).rank2Amount(53_122_485L)
			.rank3Count(3265).rank3Amount(1_480_597L)
			.rank4Count(167_745).rank4Amount(50_000L)
			.rank5Count(2_723_467).rank5Amount(5_000L)
			.build();
	}

	private WinningStore store(int rank, String region, String name, String address) {
		return WinningStore.builder().winRank(rank).region(region).name(name).address(address)
			.purchaseType("자동").status("영업중").build();
	}

	@Test
	void 회차_목록은_최신순으로_조회한다() {
		// given
		Pageable expected = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "roundNo"));
		given(roundRepository.findAll(expected))
			.willReturn(new PageImpl<>(List.of(round(1248), round(1247)), expected, 2));

		// when
		PageResponse<RoundSummaryResponse> page = service.list(0, 20);

		// then
		assertThat(page.items()).extracting(RoundSummaryResponse::round).containsExactly(1248, 1247);
		assertThat(page.items().get(0).numbers()).containsExactly(16, 18, 27, 36, 38, 42);
		assertThat(page.items().get(0).bonus()).isEqualTo(22);
		assertThat(page.totalElements()).isEqualTo(2);
		assertThat(page.size()).isEqualTo(20);
	}

	@Test
	void 상세는_1등부터_5등까지_다섯_항목을_돌려준다() {
		// given
		given(roundRepository.findById(1248)).willReturn(Optional.of(round(1248)));

		// when
		RoundDetailResponse detail = service.detail(1248);

		// then
		assertThat(detail.prizes()).hasSize(5);
		assertThat(detail.prizes()).extracting(p -> p.rank()).containsExactly(1, 2, 3, 4, 5);
		assertThat(detail.prizes().get(0).winnerCount()).isEqualTo(15);
		assertThat(detail.prizes().get(0).amountPerWinner()).isEqualTo(1_933_658_450L);
		assertThat(detail.prizes().get(4).winnerCount()).isEqualTo(2_723_467);
		assertThat(detail.prizes().get(4).amountPerWinner()).isEqualTo(5_000L);
	}

	@Test
	void 없는_회차의_상세는_COMMON_NOT_FOUND이다() {
		// given
		given(roundRepository.findById(9999)).willReturn(Optional.empty());

		// when, then
		assertThatThrownBy(() -> service.detail(9999))
			.isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COMMON_NOT_FOUND));
	}

	@Test
	void 없는_회차의_판매점은_COMMON_NOT_FOUND이다() {
		// given
		given(roundRepository.existsById(9999)).willReturn(false);

		// when, then
		assertThatThrownBy(() -> service.winningStores(9999, null, null))
			.isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COMMON_NOT_FOUND));
	}

	private void givenStores() {
		given(roundRepository.existsById(1248)).willReturn(true);
		given(storeRepository.findByRoundRoundNoOrderByWinRankAscIdAsc(1248)).willReturn(List.of(
			store(1, "서울", "가게A", "서울 강남구 1"),
			store(1, "서울", "동행복권", "동행복권(dhlottery.co.kr)"),
			store(2, "부산", "가게B", "부산 중구 2")));
	}

	@Test
	void 판매점은_등수로_거른다() {
		// given
		givenStores();

		// when
		List<WinningStoreResponse> result = service.winningStores(1248, 2, null);

		// then
		assertThat(result).extracting(WinningStoreResponse::name).containsExactly("가게B");
	}

	@Test
	void 판매점은_지역으로_거른다() {
		// given
		givenStores();

		// when
		List<WinningStoreResponse> result = service.winningStores(1248, null, "부산");

		// then
		assertThat(result).extracting(WinningStoreResponse::name).containsExactly("가게B");
	}

	@Test
	void 온라인_판매는_응답_지역이_온라인이다() {
		// given
		givenStores();

		// when
		List<WinningStoreResponse> result = service.winningStores(1248, null, null);

		// then
		assertThat(result).extracting(WinningStoreResponse::region).containsExactly("서울", "온라인", "부산");
	}

	@Test
	void 지역_온라인은_온라인_판매만_돌려준다() {
		// given
		givenStores();

		// when
		List<WinningStoreResponse> result = service.winningStores(1248, null, "온라인");

		// then
		assertThat(result).extracting(WinningStoreResponse::name).containsExactly("동행복권");
	}

	@Test
	void 지역_서울은_온라인_판매를_제외한다() {
		// given
		givenStores();

		// when
		List<WinningStoreResponse> result = service.winningStores(1248, null, "서울");

		// then
		assertThat(result).extracting(WinningStoreResponse::name).containsExactly("가게A");
	}

	@Test
	void 지역_데이터가_없는_3등_이상_rank는_빈_목록이다() {
		// given
		givenStores();

		// when
		List<WinningStoreResponse> result = service.winningStores(1248, 3, null);

		// then
		assertThat(result).isEmpty();
	}

	@Test
	void 판매점이_없는_회차는_빈_목록이다() {
		// given
		given(roundRepository.existsById(1)).willReturn(true);
		given(storeRepository.findByRoundRoundNoOrderByWinRankAscIdAsc(1)).willReturn(List.of());

		// when
		List<WinningStoreResponse> result = service.winningStores(1, null, null);

		// then
		assertThat(result).isEmpty();
	}

}
