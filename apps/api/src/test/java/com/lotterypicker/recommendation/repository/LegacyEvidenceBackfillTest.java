package com.lotterypicker.recommendation.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.lotterypicker.recommendation.LegacyEvidenceBackfill;
import java.sql.Connection;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class LegacyEvidenceBackfillTest {

	@Autowired
	private DataSource dataSource;

	private JdbcTemplate jdbc;

	@BeforeEach
	void setUp() {
		jdbc = new JdbcTemplate(dataSource);
		insert(1, "RECENT_HOT", 0, "[\"번호 7: 최근 50회 중 4회 출현\",\"번호 12: 최근 50회 중 3회 출현\"]");
		insert(2, "LONG_COLD", 1, "[\"번호 30: 18회째 미출현\",\"겹침 제한을 완화함\"]");
		insert(3, "PAIR", 3, "[\"7·12 쌍이 9회 함께 출현\"]");
	}

	private void insert(long id, String strategy, int lineIndex, String evidence) {
		// V9 이전에 저장된 행: evidence_numbers는 기본값 ''
		jdbc.update("""
				INSERT INTO daily_recommendation (id, rec_date, round_no, strategy, line_index, n1, n2, n3, n4, n5, n6,
				  evidence, relaxation, fixed_snapshot, excluded_snapshot, history_from_round, history_to_round,
				  is_control, created_at)
				VALUES (?, DATE '2026-11-06', 1249, ?, ?, 3, 7, 12, 20, 30, 41, ?, 'NONE', '', '', 1, 1248, FALSE,
				  TIMESTAMP '2026-11-06 17:00:00')
				""", id, strategy, lineIndex, evidence);
	}

	private String evidenceNumbersOf(long id) {
		return jdbc.queryForObject("SELECT evidence_numbers FROM daily_recommendation WHERE id = ?", String.class, id);
	}

	private int runBackfill() throws Exception {
		Connection connection = DataSourceUtils.getConnection(dataSource);
		try {
			return LegacyEvidenceBackfill.run(connection);
		} finally {
			DataSourceUtils.releaseConnection(connection, dataSource);
		}
	}

	@Test
	void 통계_지표가_있는_옛_줄의_근거_번호를_채운다() throws Exception {
		// when
		int updated = runBackfill();

		// then
		assertThat(updated).isEqualTo(2);
		assertThat(evidenceNumbersOf(1)).isEqualTo("7,12");
		assertThat(evidenceNumbersOf(2)).isEqualTo("30");
		assertThat(evidenceNumbersOf(3)).isEmpty();
	}

	@Test
	void Flyway가_자바_마이그레이션_V10을_적용한다() {
		// when
		Boolean applied = jdbc.queryForObject(
				"SELECT \"success\" FROM \"flyway_schema_history\" WHERE \"version\" = '10'", Boolean.class);

		// then
		assertThat(applied).isTrue();
	}

	@Test
	void 두_번_실행해도_다시_바꾸지_않는다() throws Exception {
		// given
		runBackfill();

		// when
		int updated = runBackfill();

		// then
		assertThat(updated).isZero();
	}

}
