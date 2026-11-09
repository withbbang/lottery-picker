package com.lotterypicker.recommendation.engine;

import com.lotterypicker.lotto.LottoNumbers;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * 근거 조합. 다섯 전략 줄이 근거로 꼽은 번호(재료)만으로 6개짜리 줄을 최대 5개 만든다. 난수를 쓰지 않아 같은 입력이면
 * 같은 결과다. 재료가 모자라면 다른 번호로 채우지 않고 줄을 만들지 않는다.
 */
public final class CombinedLines {

	public static final int MAX_LINES = 5;

	/** 근거 순위 0이 2점, 1이 1점. 쌍은 번호마다 점수를 받는다. */
	private static final int TOP_SCORE = 2;

	private static final String FIXED_LABEL = "고정";

	private static final Map<StrategyType, String> LABELS = new EnumMap<>(Map.of(StrategyType.RECENT_HOT, "최근 빈도",
			StrategyType.LONG_COLD, "장기 미출현", StrategyType.ALL_TIME, "전체 빈도", StrategyType.PAIR, "번호 쌍",
			StrategyType.BALANCED, "균형"));

	private CombinedLines() {
	}

	/** 같은 번호 묶음을 여러 전략이 꼽으면 하나로 합치고 출처를 모은다. */
	private record Bundle(List<Integer> numbers, int score, List<StrategyType> sources) {

		String text() {
			String label = sources.stream().map(LABELS::get).collect(Collectors.joining("·"));
			return numbers.stream().map(String::valueOf).collect(Collectors.joining("·")) + "(" + label + ")";
		}

	}

	private record Candidate(List<Integer> numbers, int score, List<Bundle> bundles) {
	}

	/**
	 * @param sources 확정된 다섯 전략 줄
	 * @param fixed 모든 줄에 들어가는 고정 번호
	 * @param rejected 건너뛸 조합(실제 추천은 인기 조합, 시뮬레이션은 없음)
	 */
	public static List<DrawnLine> combine(List<DrawnLine> sources, Set<Integer> fixed,
			Predicate<LottoNumbers> rejected) {
		List<Bundle> bundles = bundles(sources);
		List<Candidate> candidates = candidates(bundles, fixed).stream()
			.filter(candidate -> !rejected.test(new LottoNumbers(candidate.numbers())))
			.toList();

		List<Candidate> chosen = new ArrayList<>();
		List<Candidate> remaining = new ArrayList<>(candidates);
		while (chosen.size() < MAX_LINES && !remaining.isEmpty()) {
			Candidate next = remaining.stream()
				.min(Comparator.comparingInt((Candidate c) -> -c.score())
					.thenComparingInt(c -> maxOverlap(c, chosen))
					.thenComparing(Candidate::numbers, CombinedLines::lexicographic))
				.orElseThrow();
			chosen.add(next);
			remaining.remove(next);
		}
		return chosen.stream()
			.map(c -> new DrawnLine(StrategyType.COMBINED, new LottoNumbers(c.numbers()),
					List.of(evidence(c, fixed))))
			.toList();
	}

	private static List<Bundle> bundles(List<DrawnLine> sources) {
		Map<List<Integer>, Bundle> byNumbers = new LinkedHashMap<>();
		for (DrawnLine line : sources) {
			for (Ingredient ingredient : line.ingredients()) {
				int score = (TOP_SCORE - ingredient.rank()) * ingredient.numbers().size();
				byNumbers.merge(ingredient.numbers(),
						new Bundle(ingredient.numbers(), score, List.of(line.strategy())),
						(a, b) -> new Bundle(a.numbers(), a.score() + b.score(), merged(a.sources(), b.sources())));
			}
		}
		return List.copyOf(byNumbers.values());
	}

	private static List<StrategyType> merged(List<StrategyType> a, List<StrategyType> b) {
		List<StrategyType> all = new ArrayList<>(a);
		b.stream().filter(s -> !all.contains(s)).forEach(all::add);
		return List.copyOf(all);
	}

	/** 묶음 부분집합을 모두 본다. 묶음은 많아야 7개(단독 6 + 쌍 1)라 128가지다. */
	private static List<Candidate> candidates(List<Bundle> bundles, Set<Integer> fixed) {
		Map<List<Integer>, Candidate> byNumbers = new LinkedHashMap<>();
		for (int mask = 1; mask < (1 << bundles.size()); mask++) {
			TreeSet<Integer> union = new TreeSet<>(fixed);
			int score = 0;
			List<Bundle> picked = new ArrayList<>();
			for (int i = 0; i < bundles.size(); i++) {
				if ((mask & (1 << i)) != 0) {
					union.addAll(bundles.get(i).numbers());
					score += bundles.get(i).score();
					picked.add(bundles.get(i));
				}
			}
			if (union.size() != LottoNumbers.SIZE) {
				continue;
			}
			List<Integer> numbers = List.copyOf(union);
			Candidate existing = byNumbers.get(numbers);
			if (existing == null || score > existing.score()) {
				byNumbers.put(numbers, new Candidate(numbers, score, List.copyOf(picked)));
			}
		}
		return List.copyOf(byNumbers.values());
	}

	private static int maxOverlap(Candidate candidate, List<Candidate> chosen) {
		int max = 0;
		for (Candidate other : chosen) {
			max = Math.max(max, (int) candidate.numbers().stream().filter(other.numbers()::contains).count());
		}
		return max;
	}

	private static int lexicographic(List<Integer> a, List<Integer> b) {
		for (int i = 0; i < Math.min(a.size(), b.size()); i++) {
			int diff = Integer.compare(a.get(i), b.get(i));
			if (diff != 0) {
				return diff;
			}
		}
		return Integer.compare(a.size(), b.size());
	}

	/** 예) "1(고정) · 5·15(번호 쌍) · 10(최근 빈도)". 묶음의 가장 작은 번호 순. */
	private static String evidence(Candidate candidate, Set<Integer> fixed) {
		record Part(int first, String text) {
		}
		List<Part> parts = new ArrayList<>();
		fixed.forEach(number -> parts.add(new Part(number, number + "(" + FIXED_LABEL + ")")));
		candidate.bundles().forEach(bundle -> parts.add(new Part(bundle.numbers().get(0), bundle.text())));
		return parts.stream()
			.sorted(Comparator.comparingInt(Part::first))
			.map(Part::text)
			.collect(Collectors.joining(" · "));
	}

}
