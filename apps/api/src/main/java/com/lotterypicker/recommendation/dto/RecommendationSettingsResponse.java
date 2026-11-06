package com.lotterypicker.recommendation.dto;

import java.util.List;

public record RecommendationSettingsResponse(List<Integer> fixed, List<Integer> excluded) {
}
