package com.lotterypicker;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "app.scheduling.enabled=false")
class ApiApplicationTests {

	@Test
	void 기본_프로파일로_애플리케이션_컨텍스트가_로드된다() {
	}

}
