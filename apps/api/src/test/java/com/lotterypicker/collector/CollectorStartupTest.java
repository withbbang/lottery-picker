package com.lotterypicker.collector;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.lotterypicker.recommendation.RecommendationJob;
import com.lotterypicker.simulation.service.SimulationService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class CollectorStartupTest {

	private final CollectionJob collection = mock(CollectionJob.class);
	private final RecommendationJob job = mock(RecommendationJob.class);
	private final SimulationService simulation = mock(SimulationService.class);
	private final CollectionLock lock = new CollectionLock();
	private final CollectorStartup startup = new CollectorStartup(lock, collection, job, simulation);

	@Test
	void 회차_적재와_처리가_끝난_뒤_보충하고_판매점을_수집한다() {
		// when
		startup.collectOnStartup();

		// then: 판매점 수집은 오래 걸릴 수 있어 보충을 그 앞에 둔다
		InOrder order = inOrder(collection, job);
		order.verify(collection).collectRounds();
		order.verify(job).catchUp();
		order.verify(collection).collectStores();
	}

	@Test
	void 회차_수집이_실패해도_보충은_한다() {
		// given
		doThrow(new CollectorException("down")).when(collection).collectRounds();

		// when
		startup.collectOnStartup();

		// then
		verify(job).catchUp();
		verify(collection, never()).collectStores();
	}

	@Test
	void 보충이_실패해도_판매점은_수집한다() {
		// given
		doThrow(new IllegalStateException("db")).when(job).catchUp();

		// when
		startup.collectOnStartup();

		// then
		verify(collection).collectStores();
	}

	@Test
	void 다른_수집이_실행_중이면_수집은_건너뛰고_보충만_한다() {
		// when
		lock.tryRun("5분 확인", startup::collectOnStartup);

		// then
		verify(collection, never()).collectRounds();
		verify(collection, never()).collectStores();
		verify(job).catchUp();
	}

	@Test
	void 기동_시_시뮬레이션_재계산_여부를_확인한다() {
		// when
		startup.collectOnStartup();

		// then: 회차 적재 뒤에 확인해 적재한 회차로 계산한다
		InOrder order = inOrder(collection, simulation);
		order.verify(collection).collectRounds();
		order.verify(simulation).refresh();
	}

	@Test
	void 다른_수집이_실행_중이어도_시뮬레이션_결과는_확인한다() {
		// when
		lock.tryRun("5분 확인", startup::collectOnStartup);

		// then
		verify(simulation).refresh();
	}

	@Test
	void 회차_수집이_실패해도_시뮬레이션_결과는_확인한다() {
		// given
		doThrow(new CollectorException("down")).when(collection).collectRounds();

		// when
		startup.collectOnStartup();

		// then
		verify(simulation).refresh();
	}

}
