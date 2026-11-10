package db.migration;

import com.lotterypicker.recommendation.LegacyEvidenceBackfill;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** V9 이전에 저장된 추천 줄의 근거 번호를 근거 문구에서 채운다. 로직은 {@link LegacyEvidenceBackfill}. */
public class V10__Backfill_evidence_numbers extends BaseJavaMigration {

	@Override
	public void migrate(Context context) throws Exception {
		LegacyEvidenceBackfill.run(context.getConnection());
	}

}
