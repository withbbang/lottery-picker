package com.lotterypicker.round.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.lotterypicker.lotto.LottoNumbers;
import com.lotterypicker.round.entity.LottoRound;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class LottoRoundRepositoryTest {

	private static final java.time.LocalDateTime FETCHED_AT = java.time.LocalDateTime.of(2026, 11, 6, 9, 0);

	@Autowired
	private LottoRoundRepository repository;

	private LottoRound round(int no, LocalDate drawDate) {
		return LottoRound.builder()
			.roundNo(no)
			.numbers(new LottoNumbers(List.of(16, 18, 27, 36, 38, 42)))
			.bonus(22)
			.drawDate(drawDate)
			.rank1Count(15).rank1Amount(1_933_658_450L)
			.rank2Count(91).rank2Amount(53_122_485L)
			.rank3Count(3265).rank3Amount(1_480_597L)
			.rank4Count(167_745).rank4Amount(50_000L)
			.rank5Count(2_723_467).rank5Amount(5_000L)
			.source("https://www.dhlottery.co.kr")
			.fetchedAt(FETCHED_AT)
			.build();
	}

	@Test
	void 최신_회차를_찾는다() {
		// given
		repository.save(round(1247, LocalDate.of(2026, 10, 24)));
		repository.save(round(1248, LocalDate.of(2026, 10, 31)));
		repository.save(round(1246, LocalDate.of(2026, 10, 17)));
		repository.flush();

		// when
		var latest = repository.findTopByOrderByRoundNoDesc();

		// then
		assertThat(latest).isPresent();
		assertThat(latest.get().getRoundNo()).isEqualTo(1248);
	}

	@Test
	void 전체_회차를_오름차순으로_읽는다() {
		// given
		repository.save(round(1248, LocalDate.of(2026, 10, 31)));
		repository.save(round(1246, LocalDate.of(2026, 10, 17)));
		repository.save(round(1247, LocalDate.of(2026, 10, 24)));
		repository.flush();

		// when
		List<LottoRound> rounds = repository.findAllByOrderByRoundNoAsc();

		// then
		assertThat(rounds).extracting(LottoRound::getRoundNo).containsExactly(1246, 1247, 1248);
	}

	@Test
	void 회차가_없으면_최신_회차는_비어_있다() {
		assertThat(repository.findTopByOrderByRoundNoDesc()).isEmpty();
	}

	@Test
	void 번호와_금액을_저장하고_복원한다() {
		// given
		repository.saveAndFlush(round(1248, LocalDate.of(2026, 10, 31)));

		// when
		LottoRound found = repository.findById(1248).orElseThrow();

		// then
		assertThat(found.numbers().values()).containsExactly(16, 18, 27, 36, 38, 42);
		assertThat(found.getBonus()).isEqualTo(22);
		assertThat(found.getDrawDate()).isEqualTo(LocalDate.of(2026, 10, 31));
		assertThat(found.getRank1Amount()).isEqualTo(1_933_658_450L);
		assertThat(found.getRank5Count()).isEqualTo(2_723_467);
		assertThat(found.getFetchedAt()).isEqualTo(FETCHED_AT);
		assertThat(found.getStoreFetchedAt()).isNull();
	}

}
