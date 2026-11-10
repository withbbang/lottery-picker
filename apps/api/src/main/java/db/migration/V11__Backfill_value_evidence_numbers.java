package db.migration;

import com.lotterypicker.recommendation.ValueEvidenceBackfill;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** 근거 문구에 번호가 없던 시절의 추천 줄 근거 번호를 문구의 값으로 채운다. 로직은 {@link ValueEvidenceBackfill}. */
public class V11__Backfill_value_evidence_numbers extends BaseJavaMigration {

	@Override
	public void migrate(Context context) throws Exception {
		ValueEvidenceBackfill.run(context.getConnection());
	}

}
