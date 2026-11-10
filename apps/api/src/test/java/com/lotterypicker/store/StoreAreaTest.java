package com.lotterypicker.store;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StoreAreaTest {

	@Test
	void 온라인_판매는_온라인_지역이고_시군구가_없다() {
		// given / when
		StoreArea area = StoreArea.of("서울", "동행복권(dhlottery.co.kr)");

		// then
		assertThat(area).isEqualTo(new StoreArea("온라인", null));
	}

	@Test
	void 서울_자치구를_뽑는다() {
		// given / when
		StoreArea area = StoreArea.of("서울", "서울 중랑구 망우로 1");

		// then
		assertThat(area).isEqualTo(new StoreArea("서울", "중랑구"));
	}

	@Test
	void 일반구가_있는_시는_시로_묶는다() {
		// given / when
		StoreArea area = StoreArea.of("경기", "경기 수원시 장안구 팔달로 255");

		// then
		assertThat(area).isEqualTo(new StoreArea("경기", "수원시"));
	}

	@Test
	void 군을_뽑는다() {
		// given / when
		StoreArea area = StoreArea.of("전남광주", "전남광주 담양군 중앙로 32-1");

		// then
		assertThat(area).isEqualTo(new StoreArea("전남광주", "담양군"));
	}

	@Test
	void 광주와_전남은_전남광주로_합친다() {
		// given / when
		StoreArea gwangju = StoreArea.of("광주", "광주 서구 상무대로 1");
		StoreArea jeonnam = StoreArea.of("전남", "전남 여수시 시청로 1");

		// then
		assertThat(gwangju).isEqualTo(new StoreArea("전남광주", "서구"));
		assertThat(jeonnam).isEqualTo(new StoreArea("전남광주", "여수시"));
	}

	@Test
	void 세종은_시군구가_세종이다() {
		// given / when
		StoreArea area = StoreArea.of("세종", "세종   조치원로 9-1");

		// then
		assertThat(area).isEqualTo(new StoreArea("세종", "세종"));
	}

	@Test
	void 규칙에_안_맞는_주소는_기타다() {
		// given / when
		StoreArea area = StoreArea.of("전남", "전남 해보면 해보로 1");

		// then
		assertThat(area).isEqualTo(new StoreArea("전남광주", "기타"));
	}

	@Test
	void 여러_칸_공백을_하나로_본다() {
		// given / when
		StoreArea area = StoreArea.of("서울", "  서울   중랑구  망우로 1 ");

		// then
		assertThat(area).isEqualTo(new StoreArea("서울", "중랑구"));
	}

}
