package com.lotterypicker.collector;

import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 기동 수집, 5분 확인, 06:02 판매점 수집이 동시에 돌지 않게 하는 프로세스 내 잠금.
 * 기동 수집과 5분 확인은 이미 다른 수집이 돌고 있으면 기다리지 않고 건너뛴다({@link #tryRun}).
 * 하루 한 번인 판매점 수집은 건너뛰면 하루를 잃으므로 정해진 시간까지 기다린다({@link #runWaiting}).
 * 같은 스레드에서도 다시 잡을 수 없다.
 */
@Slf4j
@Component
public class CollectionLock {

	private final Semaphore permit = new Semaphore(1);

	/** 잠금을 잡으면 실행하고 true, 다른 수집이 실행 중이면 실행하지 않고 false. */
	public boolean tryRun(String name, Runnable task) {
		if (!permit.tryAcquire()) {
			log.info("다른 수집이 실행 중이라 {}을(를) 건너뜁니다.", name);
			return false;
		}
		return runHolding(task);
	}

	/** 하루 한 번 도는 작업용. 다른 수집이 끝나기를 최대 {@code timeout}만큼 기다렸다가 실행한다. */
	public boolean runWaiting(String name, Duration timeout, Runnable task) {
		try {
			if (!permit.tryAcquire(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
				log.warn("다른 수집이 {} 넘게 실행 중이라 {}을(를) 건너뜁니다.", timeout, name);
				return false;
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		}
		return runHolding(task);
	}

	private boolean runHolding(Runnable task) {
		try {
			task.run();
			return true;
		} finally {
			permit.release();
		}
	}

}
