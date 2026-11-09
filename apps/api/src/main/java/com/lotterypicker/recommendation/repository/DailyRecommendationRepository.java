package com.lotterypicker.recommendation.repository;

import com.lotterypicker.recommendation.entity.DailyRecommendation;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

/** 생성 후 수정 경로를 두지 않으려고 JpaRepository 대신 필요한 메서드만 연다. */
public interface DailyRecommendationRepository extends Repository<DailyRecommendation, Long> {

	List<DailyRecommendation> saveAll(Iterable<DailyRecommendation> lines);

	boolean existsByDate(LocalDate date);

	List<DailyRecommendation> findByDateOrderByLineIndexAsc(LocalDate date);

	/** {@code date} 이전(포함) 중 가장 최근 추천의 줄 하나. 그 날짜를 알아내는 데만 쓴다. */
	Optional<DailyRecommendation> findFirstByDateLessThanEqualOrderByDateDesc(LocalDate date);

	/** 성적표 집계에서 채점된 줄의 추천을 한 번에 가져온다. */
	List<DailyRecommendation> findAllById(Iterable<Long> ids);

	/** 추천과 대조군을 모두 돌려준다. 대조군 제외는 쓰는 쪽에서 한다. */
	List<DailyRecommendation> findByRoundNoOrderByDateAscLineIndexAsc(int roundNo);

}
