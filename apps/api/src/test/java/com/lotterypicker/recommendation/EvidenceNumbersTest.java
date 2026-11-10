package com.lotterypicker.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import com.lotterypicker.lotto.LottoNumbers;
import com.lotterypicker.recommendation.engine.DrawnLine;
import com.lotterypicker.recommendation.engine.Ingredient;
import com.lotterypicker.recommendation.engine.StrategyType;
import java.util.List;
import org.junit.jupiter.api.Test;

class EvidenceNumbersTest {

	private static final LottoNumbers NUMBERS = new LottoNumbers(List.of(3, 7, 12, 20, 33, 41));

	@Test
	void 근거_번호는_전략_근거_순서대로_펼친다() {
		// given
		DrawnLine line = new DrawnLine(StrategyType.RECENT_HOT, NUMBERS, List.of("a", "b"),
				List.of(new Ingredient(List.of(12), 0), new Ingredient(List.of(7), 1)));

		// when
		List<Integer> numbers = EvidenceNumbers.of(line);

		// then
		assertThat(numbers).containsExactly(12, 7);
	}

	@Test
	void 통계_지표가_없는_전략은_근거_번호를_저장하지_않는다() {
		// given
		DrawnLine line = new DrawnLine(StrategyType.PAIR, NUMBERS, List.of("7·12 쌍이 9회 함께 출현"),
				List.of(new Ingredient(List.of(7, 12), 0)));

		// when
		List<Integer> numbers = EvidenceNumbers.of(line);

		// then
		assertThat(numbers).isEmpty();
	}

	@Test
	void 옛_근거_문구에서_번호_접두어만_읽는다() {
		// given
		List<String> evidence = List.of("번호 7: 최근 50회 중 4회 출현", "번호 12: 최근 50회 중 3회 출현", "겹침 제한을 완화함");

		// when
		List<Integer> numbers = EvidenceNumbers.fromLegacyEvidence(evidence);

		// then
		assertThat(numbers).containsExactly(7, 12);
	}

	@Test
	void 번호_접두어가_없으면_빈_목록이다() {
		assertThat(EvidenceNumbers.fromLegacyEvidence(List.of("균형 조건 미충족"))).isEmpty();
	}

	@Test
	void 컬럼_문자열과_목록을_오간다() {
		assertThat(EvidenceNumbers.toColumn(List.of(7, 12))).isEqualTo("7,12");
		assertThat(EvidenceNumbers.toColumn(List.of())).isEmpty();
		assertThat(EvidenceNumbers.fromColumn("7,12")).containsExactly(7, 12);
		assertThat(EvidenceNumbers.fromColumn("")).isEmpty();
	}

}
