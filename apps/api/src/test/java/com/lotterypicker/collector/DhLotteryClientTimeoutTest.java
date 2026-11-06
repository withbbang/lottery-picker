package com.lotterypicker.collector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.http.client.autoconfigure.HttpClientAutoConfiguration;
import org.springframework.boot.http.client.autoconfigure.imperative.ImperativeHttpClientAutoConfiguration;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Import;

/** 실제 소켓으로 application.yml 의 HTTP 타임아웃이 DhLotteryClient 에 적용되는지 확인한다. 외부 호출은 없다. */
class DhLotteryClientTimeoutTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withInitializer(new ConfigDataApplicationContextInitializer())
		.withConfiguration(AutoConfigurations.of(HttpClientAutoConfiguration.class,
			ImperativeHttpClientAutoConfiguration.class, RestClientAutoConfiguration.class))
		.withUserConfiguration(ClientConfig.class);

	@Import(DhLotteryClient.class)
	static class ClientConfig {
	}

	@Test
	void application_yml에_HTTP_타임아웃과_스케줄러_풀이_설정되어_있다() {
		// given, when, then
		runner.run(context -> {
			var env = context.getEnvironment();
			assertThat(env.getProperty("spring.http.clients.connect-timeout")).isEqualTo("5s");
			assertThat(env.getProperty("spring.http.clients.read-timeout")).isEqualTo("30s");
			assertThat(env.getProperty("spring.task.scheduling.pool.size")).isEqualTo("2");
		});
	}

	@Test
	void 응답이_없는_서버는_읽기_타임아웃으로_CollectorException이_된다() throws Exception {
		// given: 연결은 받지만 응답하지 않는 서버
		List<Socket> held = new ArrayList<>();
		try (ServerSocket silent = new ServerSocket(0)) {
			Thread acceptor = new Thread(() -> {
				try {
					while (true) {
						held.add(silent.accept());
					}
				} catch (IOException ignored) {
					// 서버 종료
				}
			});
			acceptor.setDaemon(true);
			acceptor.start();
			runner.withPropertyValues(
				"app.dhlottery.base-url=http://localhost:" + silent.getLocalPort(),
				"app.dhlottery.min-interval-ms=0",
				"spring.http.clients.read-timeout=300ms").run(context -> {
					DhLotteryClient client = context.getBean(DhLotteryClient.class);

					// when
					long start = System.nanoTime();
					assertThatThrownBy(client::fetchRoundList)
						.isInstanceOf(CollectorException.class);
					long elapsedMs = (System.nanoTime() - start) / 1_000_000;

					// then
					assertThat(elapsedMs).isLessThan(5_000);
				});
		} finally {
			for (Socket s : held) {
				s.close();
			}
		}
	}

}
