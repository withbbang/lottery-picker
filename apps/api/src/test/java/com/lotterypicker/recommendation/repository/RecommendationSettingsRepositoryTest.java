package com.lotterypicker.recommendation.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.lotterypicker.recommendation.engine.RecommendationConstraint;
import com.lotterypicker.recommendation.entity.RecommendationSettings;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RecommendationSettingsRepositoryTest {

	@Autowired
	private RecommendationSettingsRepository repository;

	@Autowired
	private TestEntityManager entityManager;

	@Test
	void 마이그레이션이_빈_설정_한_행을_만든다() {
		// when
		RecommendationSettings settings = repository.findById(RecommendationSettings.SINGLE_ROW_ID).orElseThrow();

		// then
		assertThat(repository.count()).isEqualTo(1);
		assertThat(settings.toConstraint()).isEqualTo(RecommendationConstraint.NONE);
	}

	@Test
	void 변경한_번호를_쉼표_구분으로_저장한다() {
		// given
		RecommendationSettings settings = repository.findById(RecommendationSettings.SINGLE_ROW_ID).orElseThrow();
		RecommendationConstraint constraint = RecommendationConstraint.of(Set.of(7, 3), Set.of(45, 1));

		// when
		settings.update(constraint);
		entityManager.flush();
		entityManager.clear();

		// then
		RecommendationSettings loaded = repository.findById(RecommendationSettings.SINGLE_ROW_ID).orElseThrow();
		assertThat(loaded.getFixedNumbers()).isEqualTo("3,7");
		assertThat(loaded.getExcludedNumbers()).isEqualTo("1,45");
		assertThat(loaded.toConstraint()).isEqualTo(constraint);
	}

}
