package com.lotterypicker.recommendation.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.hibernate.validator.constraints.UniqueElements;

public record RecommendationSettingsRequest(
		@NotNull(message = "고정 번호 목록이 필요합니다.")
		@UniqueElements(message = "고정 번호에 중복이 있습니다.")
		List<@NotNull(message = "번호는 비어 있을 수 없습니다.") Integer> fixed,

		@NotNull(message = "제외 번호 목록이 필요합니다.")
		@UniqueElements(message = "제외 번호에 중복이 있습니다.")
		List<@NotNull(message = "번호는 비어 있을 수 없습니다.") Integer> excluded) {
}
