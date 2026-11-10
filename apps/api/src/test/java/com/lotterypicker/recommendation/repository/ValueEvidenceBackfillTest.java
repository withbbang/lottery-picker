package com.lotterypicker.recommendation.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.lotterypicker.recommendation.ValueEvidenceBackfill;
import java.sql.Connection;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;

/**
 * 근거 문구에 번호가 없던 시절(2026-11-06 저녁 이전)의 줄. 그 추천이 본 회차로 값을 다시 세어 같은 값의 번호를 찾는다.
 * <p>
 * 회차 1~3은 추천 전에, 4회는 추천 뒤에 수집됐다(빠진 회차가 나중에 채워진 경우). 추천은 1~3회만 봤다.
 * 줄 번호 1, 2, 7, 12, 30, 41 기준 값:
 * 전체 출현 1→3, 2→2, 7→1, 12→1, 30·41→0 / 미출현 1·2·12→0, 7→1, 30·41→3 / 최근 2회 출현 1→2, 2·7·12→1.
 * 4회까지 셌다면 7의 전체 출현은 2가 된다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ValueEvidenceBackfillTest {

	@Autowired
	private DataSource dataSource;

	private JdbcTemplate jdbc;

	@BeforeEach
	void setUp() {
		jdbc = new JdbcTemplate(dataSource);
		round(1, "2026-11-01 09:00:00", 1, 2, 3, 4, 5, 6);
		round(2, "2026-11-01 09:00:00", 1, 7, 8, 9, 10, 11);
		round(3, "2026-11-01 09:00:00", 1, 2, 12, 13, 14, 15);
		round(4, "2026-11-08 09:00:00", 7, 20, 21, 22, 23, 24);
	}

	private void round(int no, String fetchedAt, int n1, int n2, int n3, int n4, int n5, int n6) {
		jdbc.update("""
				INSERT INTO lotto_round (round_no, n1, n2, n3, n4, n5, n6, bonus, draw_date,
				  rank1_count, rank2_count, rank3_count, rank4_count, rank5_count,
				  rank1_amount, rank2_amount, rank3_amount, rank4_amount, rank5_amount, source, fetched_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, 45, DATE '2026-01-03', 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 'test', ?)
				""", no, n1, n2, n3, n4, n5, n6, java.sql.Timestamp.valueOf(fetchedAt));
	}

	private void line(long id, String strategy, String evidence) {
		line(id, strategy, evidence, "");
	}

	private void line(long id, String strategy, String evidence, String fixed) {
		jdbc.update("""
				INSERT INTO daily_recommendation (id, rec_date, round_no, strategy, line_index, n1, n2, n3, n4, n5, n6,
				  evidence, relaxation, fixed_snapshot, excluded_snapshot, history_from_round, history_to_round,
				  is_control, created_at)
				VALUES (?, DATE '2026-11-06', 5, ?, ?, 1, 2, 7, 12, 30, 41, ?, 'NONE', ?, '', 1, 4, FALSE,
				  TIMESTAMP '2026-11-06 17:00:00')
				""", id, strategy, (int) id, evidence, fixed);
	}

	private String evidenceNumbersOf(long id) {
		return jdbc.queryForObject("SELECT evidence_numbers FROM daily_recommendation WHERE id = ?", String.class, id);
	}

	private int runBackfill() throws Exception {
		Connection connection = DataSourceUtils.getConnection(dataSource);
		try {
			return ValueEvidenceBackfill.run(connection);
		} finally {
			DataSourceUtils.releaseConnection(connection, dataSource);
		}
	}

	@Test
	void 전체_출현_값마다_맞는_번호를_근거_순서대로_채운다() throws Exception {
		// given
		line(1, "ALL_TIME", "[\"전체 3회 중 3번 출현\",\"전체 3회 중 2번 출현\"]");

		// when
		runBackfill();

		// then: 3번 출현은 1, 2번 출현은 2. 추천 뒤 수집된 4회는 세지 않는다
		assertThat(evidenceNumbersOf(1)).isEqualTo("1,2");
	}

	@Test
	void 고정_번호는_근거_번호에_넣지_않는다() throws Exception {
		// given: 옛 엔진은 고정 번호의 근거를 쓰지 않았다
		line(8, "LONG_COLD", "[\"3회째 미출현\"]", "30");

		// when
		runBackfill();

		// then
		assertThat(evidenceNumbersOf(8)).isEqualTo("41");
	}

	@Test
	void 어느_번호였는지_특정할_수_없으면_비워_둔다() throws Exception {
		// given: 문구가 둘이면 값마다 번호 하나였는데, 1번 출현은 7과 12 둘이다
		line(9, "ALL_TIME", "[\"전체 3회 중 3번 출현\",\"전체 3회 중 1번 출현\"]");

		// when
		runBackfill();

		// then
		assertThat(evidenceNumbersOf(9)).isEmpty();
	}

	@Test
	void 문구의_전체_회차_수가_다시_센_회차_수와_다르면_비워_둔다() throws Exception {
		// given: 엔진은 4회를 봤다는데 추천 시각까지 수집된 회차는 3회다
		line(10, "ALL_TIME", "[\"전체 4회 중 3번 출현\"]");

		// when
		runBackfill();

		// then
		assertThat(evidenceNumbersOf(10)).isEmpty();
	}

	@Test
	void 미출현_회차_값이_같은_번호를_채운다() throws Exception {
		// given
		line(2, "LONG_COLD", "[\"3회째 미출현\"]");

		// when
		runBackfill();

		// then
		assertThat(evidenceNumbersOf(2)).isEqualTo("30,41");
	}

	@Test
	void 최근_출현은_문구에_적힌_구간으로_센다() throws Exception {
		// given: 최근 2회(2·3회)에 2번 나온 번호
		line(3, "RECENT_HOT", "[\"최근 2회 중 2번 출현\",\"겹침 제한을 완화함\"]");

		// when
		runBackfill();

		// then
		assertThat(evidenceNumbersOf(3)).isEqualTo("1");
	}

	@Test
	void 값이_맞는_번호가_없거나_지표가_없는_줄은_그대로_둔다() throws Exception {
		// given
		line(4, "ALL_TIME", "[\"전체 3회 중 9번 출현\"]");
		line(5, "PAIR", "[\"7·12 쌍이 1번 함께 출현\"]");
		line(6, "RECENT_HOT", "[]");

		// when
		int updated = runBackfill();

		// then
		assertThat(updated).isZero();
		assertThat(evidenceNumbersOf(4)).isEmpty();
		assertThat(evidenceNumbersOf(5)).isEmpty();
		assertThat(evidenceNumbersOf(6)).isEmpty();
	}

	@Test
	void 번호가_적힌_새_문구의_줄은_건드리지_않는다() throws Exception {
		// given: V10이 이미 채운 줄
		line(7, "ALL_TIME", "[\"번호 7: 전체 3회 중 1회 출현\"]");
		jdbc.update("UPDATE daily_recommendation SET evidence_numbers = '7' WHERE id = 7");

		// when
		int updated = runBackfill();

		// then
		assertThat(updated).isZero();
		assertThat(evidenceNumbersOf(7)).isEqualTo("7");
	}

	@Test
	void Flyway가_자바_마이그레이션_V11을_적용한다() {
		// when
		Boolean applied = jdbc.queryForObject(
				"SELECT \"success\" FROM \"flyway_schema_history\" WHERE \"version\" = '11'", Boolean.class);

		// then
		assertThat(applied).isTrue();
	}

}
