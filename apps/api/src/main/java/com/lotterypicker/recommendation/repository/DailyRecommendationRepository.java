package com.lotterypicker.recommendation.repository;

import com.lotterypicker.recommendation.entity.DailyRecommendation;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.repository.Repository;

/** 생성 후 수정 경로를 두지 않으려고 JpaRepository 대신 필요한 메서드만 연다. */
public interface DailyRecommendationRepository extends Repository<DailyRecommendation, Long> {

	List<DailyRecommendation> saveAll(Iterable<DailyRecommendation> lines);

	boolean existsByDate(LocalDate date);

	List<DailyRecommendation> findByDateOrderByLineIndexAsc(LocalDate date);

	/** 추천과 대조군을 모두 돌려준다. 대조군 제외는 쓰는 쪽에서 한다. */
	List<DailyRecommendation> findByRoundNoOrderByDateAscLineIndexAsc(int roundNo);

}
