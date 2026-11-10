package com.lotterypicker.round.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.lotterypicker.lotto.LottoNumbers;
import com.lotterypicker.round.entity.LottoRound;
import com.lotterypicker.round.entity.WinningStore;
import com.lotterypicker.store.StoreWin;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class WinningStoreRepositoryTest {

	@Autowired
	private LottoRoundRepository rounds;

	@Autowired
	private WinningStoreRepository repository;

	private LottoRound round(int no) {
		return rounds.save(LottoRound.builder()
			.roundNo(no)
			.numbers(new LottoNumbers(List.of(16, 18, 27, 36, 38, 42)))
			.bonus(22)
			.drawDate(LocalDate.of(2026, 11, 7))
			.source("https://www.dhlottery.co.kr")
			.fetchedAt(LocalDateTime.of(2026, 11, 7, 21, 0))
			.build());
	}

	private WinningStore store(LottoRound round, int rank, String name, Double lat, String status) {
		return repository.save(WinningStore.builder()
			.round(round)
			.winRank(rank)
			.region("서울")
			.name(name)
			.address("서울 중랑구 망우로 1")
			.purchaseType("자동")
			.latitude(lat)
			.longitude(127.1)
			.status(status)
			.build());
	}

	@Test
	void 등수가_같은_행만_id순으로_읽는다() {
		// given
		LottoRound r1248 = round(1248);
		LottoRound r1249 = round(1249);
		store(r1249, 1, "행운점", 37.5, "정상");
		store(r1248, 2, "이등점", 37.6, "정상");
		store(r1248, 1, "복권점", null, "폐점");
		repository.flush();

		// when
		List<StoreWin> wins = repository.findWinsByRank(1);

		// then
		assertThat(wins).extracting(StoreWin::name, StoreWin::roundNo, StoreWin::latitude, StoreWin::status)
			.containsExactly(
				org.assertj.core.groups.Tuple.tuple("행운점", 1249, 37.5, "정상"),
				org.assertj.core.groups.Tuple.tuple("복권점", 1248, null, "폐점"));
	}

	@Test
	void 등수별_행수와_최대_id를_센다() {
		// given
		LottoRound r1249 = round(1249);
		store(r1249, 1, "행운점", 37.5, "정상");
		store(r1249, 2, "이등점", 37.6, "정상");
		WinningStore last = store(r1249, 1, "복권점", 37.7, "정상");
		repository.flush();

		// when / then
		assertThat(repository.countByWinRank(2)).isEqualTo(1);
		assertThat(repository.findMaxIdByWinRank(1)).contains(last.getId());
		assertThat(repository.findMaxIdByWinRank(3)).isEmpty();
	}

}
