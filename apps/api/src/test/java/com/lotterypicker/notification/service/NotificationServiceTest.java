package com.lotterypicker.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lotterypicker.notification.NotificationKind;
import com.lotterypicker.notification.NotificationStatus;
import com.lotterypicker.notification.TelegramClient;
import com.lotterypicker.notification.TelegramException;
import com.lotterypicker.notification.entity.NotificationLog;
import com.lotterypicker.notification.repository.NotificationLogRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 선점 쿼리를 실제 DB로 검증한다. 커밋이 일어나므로 로컬 파일 DB 대신 인메모리 DB를 쓴다. */
@DataJpaTest(properties = "spring.datasource.url=jdbc:h2:mem:notification;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class NotificationServiceTest {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-06T08:00:00Z"), SEOUL);
	private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);
	private static final NotificationKind KIND = NotificationKind.DAILY_RECOMMENDATION;

	@Autowired
	private NotificationLogRepository logs;

	private final TelegramClient telegram = mock(TelegramClient.class);
	private NotificationService service;
	private String key;

	@BeforeEach
	void setUp() {
		when(telegram.isConfigured()).thenReturn(true);
		service = new NotificationService(telegram, logs, CLOCK, 0);
		key = UUID.randomUUID().toString();
	}

	@Test
	void 발송_전에_기록을_남긴다() {
		// given
		AtomicReference<NotificationStatus> atSend = new AtomicReference<>();
		doAnswer(inv -> {
			atSend.set(service.statusOf(KIND, key));
			return null;
		}).when(telegram).send("본문");

		// when
		NotificationStatus result = service.sendOnce(KIND, key, "본문");

		// then
		assertThat(atSend.get()).isEqualTo(NotificationStatus.PENDING);
		assertThat(result).isEqualTo(NotificationStatus.SENT);
		assertThat(service.statusOf(KIND, key)).isEqualTo(NotificationStatus.SENT);
	}

	@Test
	void 같은_알림은_한번만_보낸다() {
		// given
		service.sendOnce(KIND, key, "본문");

		// when
		NotificationStatus second = service.sendOnce(KIND, key, "본문");

		// then
		assertThat(second).isEqualTo(NotificationStatus.SENT);
		verify(telegram, times(1)).send(anyString());
	}

	@Test
	void 종류가_다르면_같은_키도_따로_보낸다() {
		// given
		service.sendOnce(KIND, key, "본문");

		// when
		service.sendOnce(NotificationKind.RECOMMENDATION_FAILED, key, "실패");

		// then
		verify(telegram).send("실패");
	}

	@Test
	void _3회_실패하면_FAILED이고_예외를_던지지_않는다() {
		// given
		doThrow(new TelegramException("HTTP 500")).when(telegram).send(anyString());

		// when
		NotificationStatus result = service.sendOnce(KIND, key, "본문");

		// then
		assertThat(result).isEqualTo(NotificationStatus.FAILED);
		assertThat(service.statusOf(KIND, key)).isEqualTo(NotificationStatus.FAILED);
		assertThat(logs.findByKindAndTargetKey(KIND, key).orElseThrow().getAttempts()).isEqualTo(3);
		verify(telegram, times(3)).send("본문");
	}

	@Test
	void 두번째_시도에_성공하면_SENT이다() {
		// given
		doThrow(new TelegramException("HTTP 500")).doNothing().when(telegram).send(anyString());

		// when
		NotificationStatus result = service.sendOnce(KIND, key, "본문");

		// then
		assertThat(result).isEqualTo(NotificationStatus.SENT);
		assertThat(logs.findByKindAndTargetKey(KIND, key).orElseThrow().getAttempts()).isEqualTo(2);
		verify(telegram, times(2)).send("본문");
	}

	@Test
	void 설정이_비어_있으면_SKIPPED이다() {
		// given
		when(telegram.isConfigured()).thenReturn(false);

		// when
		NotificationStatus result = service.sendOnce(KIND, key, "본문");

		// then
		assertThat(result).isEqualTo(NotificationStatus.SKIPPED);
		verify(telegram, never()).send(anyString());
	}

	@Test
	void 나중에_설정하면_건너뛴_알림을_보낼_수_있다() {
		// given
		when(telegram.isConfigured()).thenReturn(false);
		service.sendOnce(KIND, key, "본문");
		when(telegram.isConfigured()).thenReturn(true);

		// when
		NotificationStatus result = service.sendOnce(KIND, key, "본문");

		// then
		assertThat(result).isEqualTo(NotificationStatus.SENT);
	}

	@Test
	void 기록이_없으면_상태는_SKIPPED이다() {
		// when
		NotificationStatus result = service.statusOf(KIND, key);

		// then
		assertThat(result).isEqualTo(NotificationStatus.SKIPPED);
	}

	@Test
	void FAILED_기록은_다음_호출에서_다시_보낸다() {
		// given
		doThrow(new TelegramException("HTTP 500")).when(telegram).send(anyString());
		service.sendOnce(KIND, key, "본문");
		doNothing().when(telegram).send(anyString());

		// when
		NotificationStatus result = service.sendOnce(KIND, key, "본문");

		// then
		assertThat(result).isEqualTo(NotificationStatus.SENT);
		assertThat(logs.findByKindAndTargetKey(KIND, key).orElseThrow().getAttempts()).isEqualTo(4);
	}

	@Test
	void 진행_중인_PENDING은_보내지_않는다() {
		// given: 다른 스레드가 방금 선점했다
		logs.saveAndFlush(NotificationLog.pending(KIND, key, NOW.minusMinutes(5)));

		// when
		NotificationStatus result = service.sendOnce(KIND, key, "본문");

		// then
		assertThat(result).isEqualTo(NotificationStatus.PENDING);
		verify(telegram, never()).send(anyString());
	}

	@Test
	void 오래된_PENDING은_발송_중_종료로_보고_다시_보낸다() {
		// given
		logs.saveAndFlush(NotificationLog.pending(KIND, key, NOW.minusMinutes(5).minusSeconds(1)));

		// when
		NotificationStatus result = service.sendOnce(KIND, key, "본문");

		// then
		assertThat(result).isEqualTo(NotificationStatus.SENT);
		verify(telegram).send("본문");
	}

	@Test
	void 동시_기록으로_유니크_위반이_나면_보내지_않는다() {
		// given: 확인 시점에는 없었지만 저장 시점에는 다른 쪽이 먼저 기록했다
		NotificationLogRepository racing = mock(NotificationLogRepository.class);
		when(racing.findByKindAndTargetKey(KIND, key)).thenReturn(Optional.empty(),
				Optional.of(NotificationLog.pending(KIND, key, NOW)));
		when(racing.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uk"));
		NotificationService racingService = new NotificationService(telegram, racing, CLOCK, 0);

		// when
		NotificationStatus result = racingService.sendOnce(KIND, key, "본문");

		// then
		assertThat(result).isEqualTo(NotificationStatus.PENDING);
		verify(telegram, never()).send(anyString());
	}

}
