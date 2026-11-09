package com.lotterypicker.result.entity;

import com.lotterypicker.lotto.PrizeRank;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/** 추천 또는 대조군 한 줄의 채점 결과. 당첨번호가 저장된 회차만 채점하고, 한 줄에 하나만 둔다. */
@Getter
@Entity
@Immutable
@Table(name = "recommendation_result")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecommendationResult {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "recommendation_id")
	private Long recommendationId;

	@Column(name = "round_no")
	private int roundNo;

	@Column(name = "match_count")
	private int matchCount;

	@Enumerated(EnumType.STRING)
	@Column(name = "prize_rank")
	private PrizeRank prizeRank;

	/** 그 회차 해당 등수의 1인당 금액. 낙첨은 0. 구매했다고 가정한 가상 금액이다. */
	@Column(name = "prize_amount")
	private long prizeAmount;

	@Column(name = "created_at")
	private LocalDateTime createdAt;

	@Builder
	private RecommendationResult(Long recommendationId, int roundNo, int matchCount, PrizeRank prizeRank,
			long prizeAmount, LocalDateTime createdAt) {
		this.recommendationId = recommendationId;
		this.roundNo = roundNo;
		this.matchCount = matchCount;
		this.prizeRank = prizeRank;
		this.prizeAmount = prizeAmount;
		this.createdAt = createdAt;
	}

}
