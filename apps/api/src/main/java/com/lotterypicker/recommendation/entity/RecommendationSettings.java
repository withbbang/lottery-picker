package com.lotterypicker.recommendation.entity;

import com.lotterypicker.recommendation.engine.RecommendationConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 고정·제외 번호 설정. 사용자가 한 명이라 행도 하나이며 V3 마이그레이션이 만든다. */
@Getter
@Entity
@Table(name = "recommendation_settings")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecommendationSettings {

	public static final int SINGLE_ROW_ID = 1;

	private static final String SEPARATOR = ",";

	@Id
	private Integer id;

	@Column(name = "fixed_numbers")
	private String fixedNumbers;

	@Column(name = "excluded_numbers")
	private String excludedNumbers;

	public static RecommendationSettings empty() {
		RecommendationSettings settings = new RecommendationSettings();
		settings.id = SINGLE_ROW_ID;
		settings.fixedNumbers = "";
		settings.excludedNumbers = "";
		return settings;
	}

	public RecommendationConstraint toConstraint() {
		return RecommendationConstraint.of(parse(fixedNumbers), parse(excludedNumbers));
	}

	public void update(RecommendationConstraint constraint) {
		fixedNumbers = format(constraint.fixed());
		excludedNumbers = format(constraint.excluded());
	}

	private static Set<Integer> parse(String numbers) {
		if (numbers.isEmpty()) {
			return Set.of();
		}
		return Arrays.stream(numbers.split(SEPARATOR)).map(Integer::valueOf).collect(Collectors.toSet());
	}

	private static String format(Set<Integer> numbers) {
		return numbers.stream().map(String::valueOf).collect(Collectors.joining(SEPARATOR));
	}

}
