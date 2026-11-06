package com.lotterypicker.round.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "winning_store")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WinningStore {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "round_no")
	private LottoRound round;

	@Column(name = "win_rank")
	private int winRank;

	private String region;
	private String name;
	private String address;

	@Column(name = "purchase_type")
	private String purchaseType;

	private Double latitude;
	private Double longitude;
	private String status;

	@Builder
	private WinningStore(LottoRound round, int winRank, String region, String name, String address,
		String purchaseType, Double latitude, Double longitude, String status) {
		this.round = round;
		this.winRank = winRank;
		this.region = region;
		this.name = name;
		this.address = address;
		this.purchaseType = purchaseType;
		this.latitude = latitude;
		this.longitude = longitude;
		this.status = status;
	}

}
