package com.lotterypicker.lotto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class LottoNumbersTest {

	@Test
	void 값은_오름차순으로_정렬된다() {
		// given
		List<Integer> input = List.of(45, 1, 20, 3, 7, 9);

		// when
		LottoNumbers numbers = new LottoNumbers(input);

		// then
		assertThat(numbers.values()).containsExactly(1, 3, 7, 9, 20, 45);
	}

	@Test
	void 값_목록은_수정할_수_없다() {
		// given
		LottoNumbers numbers = new LottoNumbers(List.of(1, 2, 3, 4, 5, 6));

		// when & then
		assertThatThrownBy(() -> numbers.values().add(7)).isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void 경계값_1과_45는_허용된다() {
		// given & when
		LottoNumbers numbers = new LottoNumbers(List.of(1, 2, 3, 43, 44, 45));

		// then
		assertThat(numbers.values()).containsExactly(1, 2, 3, 43, 44, 45);
	}

	@Test
	void 범위_밖_0은_거부된다() {
		assertThatThrownBy(() -> new LottoNumbers(List.of(0, 2, 3, 4, 5, 6)))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void 범위_밖_46은_거부된다() {
		assertThatThrownBy(() -> new LottoNumbers(List.of(1, 2, 3, 4, 5, 46)))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void 개수가_5개면_거부된다() {
		assertThatThrownBy(() -> new LottoNumbers(List.of(1, 2, 3, 4, 5)))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void 개수가_7개면_거부된다() {
		assertThatThrownBy(() -> new LottoNumbers(List.of(1, 2, 3, 4, 5, 6, 7)))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void 중복은_거부된다() {
		assertThatThrownBy(() -> new LottoNumbers(List.of(1, 2, 3, 4, 5, 5)))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void 일치_개수를_센다() {
		// given
		LottoNumbers a = new LottoNumbers(List.of(1, 2, 3, 4, 5, 6));
		LottoNumbers b = new LottoNumbers(List.of(4, 5, 6, 7, 8, 9));

		// when
		int matches = a.countMatches(b);

		// then
		assertThat(matches).isEqualTo(3);
	}

	@Test
	void 포함_여부를_확인한다() {
		// given
		LottoNumbers numbers = new LottoNumbers(List.of(1, 2, 3, 4, 5, 6));

		// when & then
		assertThat(numbers.contains(3)).isTrue();
		assertThat(numbers.contains(7)).isFalse();
	}

}
