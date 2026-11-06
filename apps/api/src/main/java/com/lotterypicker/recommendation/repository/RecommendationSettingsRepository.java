package com.lotterypicker.recommendation.repository;

import com.lotterypicker.recommendation.entity.RecommendationSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationSettingsRepository extends JpaRepository<RecommendationSettings, Integer> {
}
