package com.lotterypicker.recommendation.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.lotterypicker.lotto.LottoNumbers;
import com.lotterypicker.recommendation.engine.Relaxation;
import com.lotterypicker.recommendation.engine.StrategyType;
import com.lotterypicker.recommendation.entity.DailyRecommendation;
import jakarta.persistence.EntityManager;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DailyRecommendationRepositoryTest {

	private static final LocalDate DATE = LocalDate.of(2026, 11, 6);

	@Autowired
	private DailyRecommendationRepository repository;

	@Autowired
	private EntityManager entityManager;

	private static DailyRecommendation line(StrategyType strategy, int lineIndex, List<String> evidence) {
		return line(DATE, strategy, lineIndex, evidence);
	}

	private static DailyRecommendation line(LocalDate date, StrategyType strategy, int lineIndex,
			List<String> evidence) {
		return DailyRecommendation.builder()
			.date(date)
			.roundNo(1249)
			.strategy(strategy)
			.lineIndex(lineIndex)
			.numbers(new LottoNumbers(List.of(45, 1, 19, 27, 38, 44)))
			.evidence(evidence)
			.relaxation(Relaxation.OVERLAP)
			.fixedSnapshot("3,7")
			.excludedSnapshot("")
			.historyFromRound(1)
			.historyToRound(1248)
			.createdAt(LocalDateTime.of(2026, 11, 6, 17, 0))
			.build();
	}

	private List<DailyRecommendation> reload() {
		entityManager.flush();
		entityManager.clear();
		return repository.findByDateOrderByLineIndexAsc(DATE);
	}

	@Test
	void 저장한_값을_그대로_읽는다() {
		// given
		List<String> evidence = List.of("12·34 쌍이 \"7번\" 함께 출현", "중복 제한 완화");
		repository.saveAll(List.of(line(StrategyType.PAIR, 3, evidence)));

		// when
		DailyRecommendation stored = reload().get(0);

		// then
		assertThat(stored.getDate()).isEqualTo(DATE);
		assertThat(stored.getRoundNo()).isEqualTo(1249);
		assertThat(stored.getStrategy()).isEqualTo(StrategyType.PAIR);
		assertThat(stored.getLineIndex()).isEqualTo(3);
		assertThat(stored.numbers().values()).containsExactly(1, 19, 27, 38, 44, 45);
		assertThat(stored.getEvidence()).isEqualTo(evidence);
		assertThat(stored.getRelaxation()).isEqualTo(Relaxation.OVERLAP);
		assertThat(stored.getFixedSnapshot()).isEqualTo("3,7");
		assertThat(stored.getHistoryToRound()).isEqualTo(1248);
		assertThat(stored.isControl()).isFalse();
		assertThat(stored.hasSettings()).isTrue();
	}

	@Test
	void 가장_최근_날짜의_줄_하나를_찾는다() {
		// given
		repository.saveAll(List.of(line(LocalDate.of(2026, 11, 4), StrategyType.PAIR, 0, List.of()),
				line(LocalDate.of(2026, 11, 5), StrategyType.PAIR, 0, List.of()),
				line(LocalDate.of(2026, 11, 5), StrategyType.BALANCED, 1, List.of()),
				line(LocalDate.of(2026, 11, 7), StrategyType.PAIR, 0, List.of())));
		entityManager.flush();
		entityManager.clear();

		// when
		var found = repository.findFirstByDateLessThanEqualOrderByDateDesc(DATE);

		// then
		assertThat(found).hasValueSatisfying(line -> assertThat(line.getDate()).isEqualTo(LocalDate.of(2026, 11, 5)));
	}

	@Test
	void 그날_대조군을_뺀_첫_줄을_찾는다() {
		// given
		repository.saveAll(List.of(line(DATE, StrategyType.CONTROL, 0, List.of()),
				line(DATE, StrategyType.BALANCED, 1, List.of()), line(DATE, StrategyType.PAIR, 0, List.of())));
		entityManager.flush();
		entityManager.clear();

		// when
		var found = repository.findFirstByDateAndControlFalseOrderByLineIndexAsc(DATE);

		// then
		assertThat(found).hasValueSatisfying(line -> assertThat(line.getStrategy()).isEqualTo(StrategyType.PAIR));
	}

	@Test
	void 가장_최근_추천의_대조군을_뺀_첫_줄을_찾는다() {
		// given: 11/7은 기준일 뒤, 11/5가 가장 최근이다
		repository.saveAll(List.of(line(LocalDate.of(2026, 11, 4), StrategyType.PAIR, 0, List.of()),
				line(LocalDate.of(2026, 11, 5), StrategyType.CONTROL, 0, List.of()),
				line(LocalDate.of(2026, 11, 5), StrategyType.BALANCED, 1, List.of()),
				line(LocalDate.of(2026, 11, 7), StrategyType.PAIR, 0, List.of())));
		entityManager.flush();
		entityManager.clear();

		// when
		var found = repository.findFirstByDateLessThanEqualAndControlFalseOrderByDateDescLineIndexAsc(DATE);

		// then
		assertThat(found).hasValueSatisfying(line -> {
			assertThat(line.getDate()).isEqualTo(LocalDate.of(2026, 11, 5));
			assertThat(line.getStrategy()).isEqualTo(StrategyType.BALANCED);
		});
	}

	@Test
	void 추천_날짜를_최근순으로_중복없이_나눠_센다() {
		// given: 11/4 대조군만, 11/5 두 줄, 11/6·11/7 한 줄씩
		repository.saveAll(List.of(line(LocalDate.of(2026, 11, 4), StrategyType.CONTROL, 0, List.of()),
				line(LocalDate.of(2026, 11, 5), StrategyType.PAIR, 0, List.of()),
				line(LocalDate.of(2026, 11, 5), StrategyType.BALANCED, 1, List.of()),
				line(LocalDate.of(2026, 11, 6), StrategyType.PAIR, 0, List.of()),
				line(LocalDate.of(2026, 11, 7), StrategyType.PAIR, 0, List.of())));
		entityManager.flush();
		entityManager.clear();

		// when
		var page = repository.findRecommendationDates(PageRequest.of(0, 2));

		// then
		assertThat(page.getContent()).containsExactly(LocalDate.of(2026, 11, 7), LocalDate.of(2026, 11, 6));
		assertThat(page.getTotalElements()).isEqualTo(3);
	}

	@Test
	void 여러_날짜의_추천_줄을_대조군_빼고_날짜_최근순_줄_순서로_읽는다() {
		// given
		LocalDate older = LocalDate.of(2026, 11, 5);
		repository.saveAll(List.of(line(older, StrategyType.BALANCED, 1, List.of()),
				line(older, StrategyType.PAIR, 0, List.of()), line(DATE, StrategyType.PAIR, 0, List.of()),
				line(DATE, StrategyType.CONTROL, 0, List.of()),
				line(LocalDate.of(2026, 11, 7), StrategyType.PAIR, 0, List.of())));
		entityManager.flush();
		entityManager.clear();

		// when
		var lines = repository.findByDateInAndControlFalseOrderByDateDescLineIndexAsc(List.of(older, DATE));

		// then
		assertThat(lines).extracting(DailyRecommendation::getDate, DailyRecommendation::getStrategy)
			.containsExactly(tuple(DATE, StrategyType.PAIR),
					tuple(older, StrategyType.PAIR),
					tuple(older, StrategyType.BALANCED));
	}

	@Test
	void 긴_근거도_저장한다() {
		// given: TEXT 컬럼이라 VARCHAR(255)보다 길어도 들어간다
		List<String> evidence = List.of("가".repeat(2_000));
		repository.saveAll(List.of(line(StrategyType.PAIR, 0, evidence)));

		// when, then
		assertThat(reload().get(0).getEvidence()).isEqualTo(evidence);
	}

	@Test
	void 대조군_줄은_control이다() {
		// given
		repository.saveAll(List.of(line(StrategyType.CONTROL, 0, List.of())));

		// when
		DailyRecommendation stored = reload().get(0);

		// then
		assertThat(stored.isControl()).isTrue();
		assertThat(stored.getEvidence()).isEmpty();
	}

	@Test
	void 같은_날짜_전략_줄번호는_유니크_위반이다() {
		// given
		repository.saveAll(List.of(line(StrategyType.PAIR, 3, List.of())));
		entityManager.flush();

		// when, then
		assertThatThrownBy(() -> {
			repository.saveAll(List.of(line(StrategyType.PAIR, 3, List.of())));
			entityManager.flush();
		}).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void 필드를_바꿔도_DB에_반영되지_않는다() {
		// given
		repository.saveAll(List.of(line(StrategyType.PAIR, 3, List.of())));
		DailyRecommendation stored = reload().get(0);

		// when
		ReflectionTestUtils.setField(stored, "roundNo", 9999);
		ReflectionTestUtils.setField(stored, "n1", 2);

		// then
		DailyRecommendation reloaded = reload().get(0);
		assertThat(reloaded.getRoundNo()).isEqualTo(1249);
		assertThat(reloaded.numbers().values()).contains(1).doesNotContain(2);
	}

	@Test
	void 저장한_근거_목록은_수정할_수_없다() {
		// given
		repository.saveAll(List.of(line(StrategyType.PAIR, 3, List.of("근거"))));
		DailyRecommendation stored = reload().get(0);

		// when, then
		assertThatThrownBy(() -> stored.getEvidence().add("추가")).isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void 리포지토리에_수정_삭제_메서드가_없다() {
		// when
		List<String> names = Arrays.stream(DailyRecommendationRepository.class.getMethods())
			.map(Method::getName)
			.toList();

		// then
		assertThat(names).containsExactlyInAnyOrder("saveAll", "existsByDate", "findByDateOrderByLineIndexAsc",
				"findByRoundNoOrderByDateAscLineIndexAsc", "findAllById", "findFirstByDateLessThanEqualOrderByDateDesc", "findRecommendationDates",
				"findByDateInAndControlFalseOrderByDateDescLineIndexAsc", "findFirstByDateAndControlFalseOrderByLineIndexAsc",
				"findFirstByDateLessThanEqualAndControlFalseOrderByDateDescLineIndexAsc");
	}

}
