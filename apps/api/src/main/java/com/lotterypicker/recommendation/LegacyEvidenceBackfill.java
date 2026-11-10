package com.lotterypicker.recommendation;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 근거 번호 컬럼(V9)이 생기기 전에 저장된 추천 줄의 근거 번호를 근거 문구에서 한 번 채운다.
 * 추천 엔티티는 바꿀 수 없으므로(@Immutable) JDBC로만 갱신한다. 이미 채운 줄은 건드리지 않아 다시 돌려도 안전하다.
 */
public final class LegacyEvidenceBackfill {

	private static final ObjectMapper MAPPER = JsonMapper.builder().build();
	private static final TypeReference<List<String>> EVIDENCE = new TypeReference<>() {
	};

	private static final String SELECT = """
			SELECT id, evidence FROM daily_recommendation
			WHERE strategy IN ('RECENT_HOT', 'LONG_COLD', 'ALL_TIME') AND evidence_numbers = ''
			""";
	private static final String UPDATE = "UPDATE daily_recommendation SET evidence_numbers = ? WHERE id = ?";

	private LegacyEvidenceBackfill() {
	}

	/** @return 근거 번호를 채운 줄 수 */
	public static int run(Connection connection) throws SQLException {
		Map<Long, String> filled = new LinkedHashMap<>();
		try (PreparedStatement select = connection.prepareStatement(SELECT); ResultSet rows = select.executeQuery()) {
			while (rows.next()) {
				List<Integer> numbers = EvidenceNumbers
					.fromLegacyEvidence(MAPPER.readValue(rows.getString("evidence"), EVIDENCE));
				if (!numbers.isEmpty()) {
					filled.put(rows.getLong("id"), EvidenceNumbers.toColumn(numbers));
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
