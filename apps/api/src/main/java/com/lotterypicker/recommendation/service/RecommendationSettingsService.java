package com.lotterypicker.recommendation.service;

import com.lotterypicker.recommendation.dto.RecommendationSettingsRequest;
import com.lotterypicker.recommendation.dto.RecommendationSettingsResponse;
import com.lotterypicker.recommendation.engine.RecommendationConstraint;
import com.lotterypicker.recommendation.entity.RecommendationSettings;
import com.lotterypicker.recommendation.repository.RecommendationSettingsRepository;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecommendationSettingsService {

	private final RecommendationSettingsRepository repository;

	public RecommendationSettingsResponse get() {
		return toResponse(currentConstraint());
	}

	/** 다음 추천 생성부터 쓰는 값. */
	public RecommendationConstraint currentConstraint() {
		return settings().toConstraint();
	}

	@Transactional
	public RecommendationSettingsResponse update(RecommendationSettingsRequest request) {
		RecommendationConstraint constraint = RecommendationConstraint.of(Set.copyOf(request.fixed()),
				Set.copyOf(request.excluded()));
		settings().update(constraint);
		return toResponse(constraint);
	}

	private RecommendationSettings settings() {
		return repository.findById(RecommendationSettings.SINGLE_ROW_ID)
			.orElseThrow(() -> new IllegalStateException("recommendation_settings 행이 없습니다."));
	}

	private static RecommendationSettingsResponse toResponse(RecommendationConstraint constraint) {
		return new RecommendationSettingsResponse(List.copyOf(constraint.fixed()), List.copyOf(constraint.excluded()));
	}

}
