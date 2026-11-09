package com.lotterypicker.result.repository;

import com.lotterypicker.result.entity.RecommendationResult;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/** 채점 결과는 바꾸지 않으므로 필요한 메서드만 연다. */
public interface RecommendationResultRepository extends Repository<RecommendationResult, Long> {

	List<RecommendationResult> saveAll(Iterable<RecommendationResult> results);

	List<RecommendationResult> findByRoundNo(int roundNo);

	/** 성적표 집계용. 채점된 줄 전체. */
	List<RecommendationResult> findAll();

	/** 당첨번호는 저장됐는데 채점하지 않은 추천 줄이 있는 회차. 아직 추첨 전인 회차는 빠진다. */
	@Query("""
			select distinct d.roundNo from DailyRecommendation d
			where exists (select 1 from LottoRound l where l.roundNo = d.roundNo)
			  and not exists (select 1 from RecommendationResult r where r.recommendationId = d.id)
			order by d.roundNo
			""")
	List<Integer> findPendingRoundNos();

}
