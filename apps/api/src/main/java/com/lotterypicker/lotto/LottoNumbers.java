package com.lotterypicker.lotto;

import java.util.Comparator;
import java.util.List;

/** 로또 6/45 본번호. 1~45 정수 6개, 중복 없음, 오름차순으로 정규화된다. */
public record LottoNumbers(List<Integer> values) {

	public static final int SIZE = 6;
	public static final int MIN = 1;
	public static final int MAX = 45;

	public LottoNumbers {
		if (values == null || values.size() != SIZE) {
			throw new IllegalArgumentException("번호는 " + SIZE + "개여야 합니다.");
		}
		for (Integer value : values) {
			if (value == null || value < MIN || value > MAX) {
				throw new IllegalArgumentException("번호는 " + MIN + "~" + MAX + " 범위여야 합니다.");
			}
		}
		if (values.stream().distinct().count() != SIZE) {
			throw new IllegalArgumentException("번호는 중복될 수 없습니다.");
		}
		values = values.stream().sorted(Comparator.naturalOrder()).toList();
	}

	public boolean contains(int number) {
		return values.contains(number);
	}

	public int countMatches(LottoNumbers other) {
		return (int) values.stream().filter(other::contains).count();
	}

}
