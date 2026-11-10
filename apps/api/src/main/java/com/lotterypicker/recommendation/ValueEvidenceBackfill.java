package com.lotterypicker.recommendation;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntUnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 근거 문구에 번호가 없던 시절(2026-11-06 저녁 이전, 예: "최근 50회 중 11번 출현")의 추천 줄 근거 번호를 채운다(V11 전용).
 * 그 추천이 본 회차(범위 안, 추천 시각까지 수집된 회차)로 값을 다시 세어, 고정 번호를 뺀 줄 번호 중 문구의 값과 같은 번호를
 * 근거 번호로 둔다. 옛 엔진은 가중치 상위 2개 번호의 값을 문구로 쓰고 같은 값은 하나로 합쳤다. 그래서 문구가 둘이면 값마다
 * 번호가 하나, 하나면 번호가 하나나 둘이어야 한다. 이보다 많아 어느 번호였는지 특정할 수 없거나, 전체 출현 문구의 회차 수가
 * 다시 센 회차 수와 다르면 틀린 번호를 강조하지 않도록 비워 둔다.
 * <p>
 * 이미 적용된 마이그레이션이 새 DB에서도 같은 결과를 내도록 문구 형식과 통계 계산을 앱 코드에 기대지 않고 여기 고정한다.
 * 이 클래스는 고치지 않는다. 형식이 바뀌면 새 마이그레이션을 만든다. 이미 채운 줄은 건드리지 않아 다시 돌려도 안전하다.
 */
public final class ValueEvidenceBackfill {

	private static final ObjectMapper MAPPER = JsonMapper.builder().build();
	private static final TypeReference<List<String>> EVIDENCE = new TypeReference<>() {
	};

	private static final Pattern RECENT = Pattern.compile("^최근 (\\d+)회 중 (\\d+)번 출현$");
	private static final Pattern COLD = Pattern.compile("^(\\d+)회째 미출현$");
	private static final Pattern ALL_TIME = Pattern.compile("^전체 (\\d+)회 중 (\\d+)번 출현$");

	/** 옛 엔진이 근거를 쓴 번호 수({@code WeightedSampler.MAX_EVIDENCE}). */
	private static final int MAX_EVIDENCE = 2;

	private static final String SELECT = """
			SELECT id, strategy, evidence, fixed_snapshot, n1, n2, n3, n4, n5, n6, history_from_round, history_to_round,
			  created_at
			FROM daily_recommendation
			WHERE strategy IN ('RECENT_HOT', 'LONG_COLD', 'ALL_TIME') AND evidence_numbers = ''
			ORDER BY rec_date, line_index
			""";
	private static final String ROUNDS = """
			SELECT n1, n2, n3, n4, n5, n6 FROM lotto_round
			WHERE round_no BETWEEN ? AND ? AND fetched_at <= ?
			ORDER BY round_no
			""";
	private static final String UPDATE = "UPDATE daily_recommendation SET evidence_numbers = ? WHERE id = ?";

	private ValueEvidenceBackfill() {
	}

	private record Basis(int from, int to, Timestamp createdAt) {
	}

	/** @return 근거 번호를 채운 줄 수 */
	public static int run(Connection connection) throws SQLException {
		// 줄은 날짜순이라 같은 추천의 줄이 붙어 온다. 직전 추천의 회차만 들고 있는다.
		Basis lastBasis = null;
		List<Set<Integer>> draws = List.of();
		Map<Long, String> filled = new LinkedHashMap<>();
		try (PreparedStatement select = connection.prepareStatement(SELECT); ResultSet rows = select.executeQuery()) {
			while (rows.next()) {
				List<String> evidence = MAPPER.readValue(rows.getString("evidence"), EVIDENCE);
				List<Integer> numbers = new ArrayList<>();
				for (int i = 1; i <= 6; i++) {
					numbers.add(rows.getInt("n" + i));
				}
				Basis basis = new Basis(rows.getInt("history_from_round"), rows.getInt("history_to_round"),
						rows.getTimestamp("created_at"));
				if (!basis.equals(lastBasis)) {
					draws = draws(connection, basis);
					lastBasis = basis;
				}
				numbers.removeAll(fixed(rows.getString("fixed_snapshot")));
				String column = evidenceNumbers(rows.getString("strategy"), evidence, numbers, draws);
				if (!column.isEmpty()) {
					filled.put(rows.getLong("id"), column);
				}
			}
		}
		try (PreparedStatement update = connection.prepareStatement(UPDATE)) {
			for (Map.Entry<Long, String> entry : filled.entrySet()) {
				update.setString(1, entry.getValue());
				update.setLong(2, entry.getKey());
				update.addBatch();
			}
			if (!filled.isEmpty()) {
				update.executeBatch();
			}
		}
		return filled.size();
	}

	private static List<Set<Integer>> draws(Connection connection, Basis basis) throws SQLException {
		List<Set<Integer>> draws = new ArrayList<>();
		try (PreparedStatement select = connection.prepareStatement(ROUNDS)) {
			select.setInt(1, basis.from());
			select.setInt(2, basis.to());
			select.setTimestamp(3, basis.createdAt());
			try (ResultSet rows = select.executeQuery()) {
				while (rows.next()) {
					Set<Integer> draw = new LinkedHashSet<>();
					for (int i = 1; i <= 6; i++) {
						draw.add(rows.getInt("n" + i));
					}
					draws.add(draw);
				}
			}
		}
		return draws;
	}

	private static Set<Integer> fixed(String snapshot) {
		Set<Integer> fixed = new LinkedHashSet<>();
		if (snapshot != null && !snapshot.isBlank()) {
			for (String part : snapshot.split(",")) {
				fixed.add(Integer.valueOf(part.trim()));
			}
		}
		return fixed;
	}

	/**
	 * 문구 순서대로, 같은 값이면 줄 번호 순서대로. 특정할 수 없으면 빈 문자열.
	 *
	 * @param numbers 고정 번호를 뺀 줄 번호
	 */
	private static String evidenceNumbers(String strategy, List<String> evidence, List<Integer> numbers,
			List<Set<Integer>> draws) {
		List<List<Integer>> matched = new ArrayList<>();
		for (String text : evidence) {
			Target target = target(strategy, text, draws);
			if (target == null) {
				continue;
			}
			if (!target.consistent()) {
				return "";
			}
			matched.add(numbers.stream().filter(n -> target.valueOf().applyAsInt(n) == target.expected()).toList());
		}
		if (matched.isEmpty() || matched.size() > MAX_EVIDENCE) {
			return "";
		}
		// 문구가 둘이면 상위 두 번호의 값이 서로 달랐다. 하나면 상위 번호 한두 개가 같은 값이었다.
		int allowed = matched.size() == MAX_EVIDENCE ? 1 : MAX_EVIDENCE;
		Set<Integer> picked = new LinkedHashSet<>();
		for (List<Integer> candidates : matched) {
			if (candidates.isEmpty() || candidates.size() > allowed) {
				return "";
			}
			picked.addAll(candidates);
		}
		return picked.stream().map(String::valueOf).collect(Collectors.joining(","));
	}

	/**
	 * 문구 하나가 가리키는 값과 그 값을 세는 방법. 이 전략의 근거 문구가 아니면 null(완화 메모 등).
	 *
	 * @param consistent 문구에 적힌 회차 수가 다시 센 회차 수와 맞는지. 전체 출현 문구만 회차 수를 적는다.
	 */
	private record Target(int expected, IntUnaryOperator valueOf, boolean consistent) {
	}

	private static Target target(String strategy, String text, List<Set<Integer>> draws) {
		switch (strategy) {
			case "RECENT_HOT" -> {
				Matcher matcher = RECENT.matcher(text);
				if (!matcher.matches()) {
					return null;
				}
				int window = Integer.parseInt(matcher.group(1));
				List<Set<Integer>> recent = draws.subList(Math.max(0, draws.size() - window), draws.size());
				return new Target(Integer.parseInt(matcher.group(2)), n -> count(recent, n), true);
			}
			case "LONG_COLD" -> {
				Matcher matcher = COLD.matcher(text);
				if (!matcher.matches()) {
					return null;
				}
				return new Target(Integer.parseInt(matcher.group(1)), n -> roundsSinceLast(draws, n), true);
			}
			case "ALL_TIME" -> {
				Matcher matcher = ALL_TIME.matcher(text);
				if (!matcher.matches()) {
					return null;
				}
				return new Target(Integer.parseInt(matcher.group(2)), n -> count(draws, n),
						Integer.parseInt(matcher.group(1)) == draws.size());
			}
			default -> {
				return null;
			}
		}
	}

	private static int count(List<Set<Integer>> draws, int number) {
		return (int) draws.stream().filter(draw -> draw.contains(number)).count();
	}

	/** 마지막 출현 뒤로 지난 회차 수. 최신 회차에 나왔으면 0, 나온 적 없으면 전체 회차 수. */
	private static int roundsSinceLast(List<Set<Integer>> draws, int number) {
		for (int i = draws.size() - 1; i >= 0; i--) {
			if (draws.get(i).contains(number)) {
				return draws.size() - 1 - i;
			}
		}
		return draws.size();
	}

}
