package com.lotterypicker.recommendation;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
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
 * 그 추천이 본 회차(범위 안, 추천 시각까지 수집된 회차)로 값을 다시 세어, 줄 번호 중 문구의 값과 같은 번호를 근거 번호로 둔다.
 * 같은 값의 번호가 여럿이면 모두 넣는다. 옛 엔진은 같은 값의 근거 문구를 하나로 합쳤기 때문이다.
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
	private static final Pattern ALL_TIME = Pattern.compile("^전체 \\d+회 중 (\\d+)번 출현$");

	private static final String SELECT = """
			SELECT id, strategy, evidence, n1, n2, n3, n4, n5, n6, history_from_round, history_to_round, created_at
			FROM daily_recommendation
			WHERE strategy IN ('RECENT_HOT', 'LONG_COLD', 'ALL_TIME') AND evidence_numbers = ''
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
		Map<Basis, List<Set<Integer>>> history = new HashMap<>();
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
				List<Set<Integer>> draws = history.get(basis);
				if (draws == null) {
					draws = draws(connection, basis);
					history.put(basis, draws);
				}
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

	/** 문구 순서대로, 같은 값이면 줄 번호 순서대로. 맞는 번호가 없으면 빈 문자열. */
	private static String evidenceNumbers(String strategy, List<String> evidence, List<Integer> numbers,
			List<Set<Integer>> draws) {
		Set<Integer> picked = new LinkedHashSet<>();
		for (String text : evidence) {
			Matcher matcher;
			int expected;
			IntUnaryOperator valueOf;
			switch (strategy) {
				case "RECENT_HOT" -> {
					matcher = RECENT.matcher(text);
					if (!matcher.matches()) {
						continue;
					}
					int window = Integer.parseInt(matcher.group(1));
					expected = Integer.parseInt(matcher.group(2));
					List<Set<Integer>> recent = draws.subList(Math.max(0, draws.size() - window), draws.size());
					valueOf = n -> (int) recent.stream().filter(draw -> draw.contains(n)).count();
				}
				case "LONG_COLD" -> {
					matcher = COLD.matcher(text);
					if (!matcher.matches()) {
						continue;
					}
					expected = Integer.parseInt(matcher.group(1));
					valueOf = n -> roundsSinceLast(draws, n);
				}
				case "ALL_TIME" -> {
					matcher = ALL_TIME.matcher(text);
					if (!matcher.matches()) {
						continue;
					}
					expected = Integer.parseInt(matcher.group(1));
					valueOf = n -> (int) draws.stream().filter(draw -> draw.contains(n)).count();
				}
				default -> {
					continue;
				}
			}
			for (int number : numbers) {
				if (valueOf.applyAsInt(number) == expected) {
					picked.add(number);
				}
			}
		}
		return picked.stream().map(String::valueOf).collect(Collectors.joining(","));
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
