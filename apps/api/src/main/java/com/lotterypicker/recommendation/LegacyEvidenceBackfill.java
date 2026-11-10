package com.lotterypicker.recommendation;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 근거 번호 컬럼(V9)이 생기기 전에 저장된 추천 줄의 근거 번호를 근거 문구에서 한 번 채운다(V10 전용).
 * 추천 엔티티는 바꿀 수 없으므로(@Immutable) JDBC로만 갱신한다. 이미 채운 줄은 건드리지 않아 다시 돌려도 안전하다.
 * <p>
 * 이미 적용된 마이그레이션이 새 DB에서도 같은 결과를 내도록 문구 형식·컬럼 형식을 앱 코드에 기대지 않고 여기 고정한다.
 * 이 클래스는 고치지 않는다. 형식이 바뀌면 새 마이그레이션을 만든다.
 */
public final class LegacyEvidenceBackfill {

	private static final ObjectMapper MAPPER = JsonMapper.builder().build();
	private static final TypeReference<List<String>> EVIDENCE = new TypeReference<>() {
	};

	private static final String SELECT = """
			SELECT id, evidence FROM daily_recommendation
			WHERE strategy IN ('RECENT_HOT', 'LONG_COLD', 'ALL_TIME') AND evidence_numbers = ''
			""";
	/** 2026-11-06 이후 엔진이 만든 근거 문구의 접두어 "번호 N: ". 그 전 문구에는 번호가 없어 채우지 않는다. */
	private static final Pattern PREFIX = Pattern.compile("^번호 (\\d+): ");

	private static final String UPDATE = "UPDATE daily_recommendation SET evidence_numbers = ? WHERE id = ?";

	private LegacyEvidenceBackfill() {
	}

	/** @return 근거 번호를 채운 줄 수 */
	public static int run(Connection connection) throws SQLException {
		Map<Long, String> filled = new LinkedHashMap<>();
		try (PreparedStatement select = connection.prepareStatement(SELECT); ResultSet rows = select.executeQuery()) {
			while (rows.next()) {
				String numbers = MAPPER.readValue(rows.getString("evidence"), EVIDENCE).stream()
					.map(PREFIX::matcher)
					.filter(Matcher::find)
					.map(m -> m.group(1))
					.collect(Collectors.joining(","));
				if (!numbers.isEmpty()) {
					filled.put(rows.getLong("id"), numbers);
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

}
