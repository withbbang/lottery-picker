package com.lotterypicker.collector;

import java.util.concurrent.Semaphore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 기동 수집, 5분 확인, 06시 판매점 수집이 동시에 돌지 않게 하는 프로세스 내 잠금.
 * 이미 다른 수집이 돌고 있으면 기다리지 않고 이번 실행을 건너뛴다. 같은 스레드에서도 다시 잡을 수 없다.
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
		try {
			task.run();
			return true;
		} finally {
			permit.release();
		}
	}

}
