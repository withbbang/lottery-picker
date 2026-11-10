package com.lotterypicker.recommendation;

import com.lotterypicker.recommendation.engine.DrawnLine;
import com.lotterypicker.recommendation.engine.NumberMetric;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 추천 줄의 근거 번호. 번호 통계 화면에서 강조할 번호이며, 통계 지표가 있는 전략에만 있다.
 * 근거 문구와 같은 번호를 같은 순서로 담는다.
 */
public final class EvidenceNumbers {

	/** 엔진이 만드는 근거 문구의 고정 접두어 "번호 N: " ({@code WeightedSampler.Sample.evidence}). */
	private static final Pattern LEGACY_PREFIX = Pattern.compile("^번호 (\\d+): ");

	private EvidenceNumbers() {
	}

	/** 엔진의 구조화된 근거에서 뽑는다. 통계 지표가 없는 전략이면 빈 목록. */
	public static List<Integer> of(DrawnLine line) {
		if (NumberMetric.forStrategy(line.strategy()).isEmpty()) {
			return List.of();
		}
		return line.ingredients().stream().flatMap(ingredient -> ingredient.numbers().stream()).toList();
	}

	/**
	 * 근거 번호 컬럼이 생기기 전에 저장된 줄용. 우리 엔진이 만든 고정 형식 문구에서만 읽으며,
	 * 접두어가 없는 줄(완화 메모 등)은 건너뛴다.
	 */
	public static List<Integer> fromLegacyEvidence(List<String> evidence) {
		return evidence.stream()
			.map(LEGACY_PREFIX::matcher)
			.filter(Matcher::find)
			.map(m -> Integer.parseInt(m.group(1)))
			.toList();
	}

	public static String toColumn(List<Integer> numbers) {
		return numbers.stream().map(String::valueOf).collect(Collectors.joining(","));
	}

	public static List<Integer> fromColumn(String column) {
		if (column == null || column.isEmpty()) {
			return List.of();
		}
		return Arrays.stream(column.split(",")).map(Integer::valueOf).toList();
	}

}
