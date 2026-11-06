package com.lotterypicker.collector;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lotterypicker.collector.service.CollectorService;
import com.lotterypicker.recommendation.RecommendationJob;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class CollectorStartupTest {

	private final CollectorService collector = mock(CollectorService.class);
	private final RecommendationJob job = mock(RecommendationJob.class);
	private final CollectorStartup startup = new CollectorStartup(collector, job);

	@Test
	void 회차_적재가_끝난_뒤_보충하고_판매점을_수집한다() {
		// when
		startup.collectOnStartup();

		// then: 판매점 수집은 오래 걸릴 수 있어 보충을 그 앞에 둔다
		InOrder order = inOrder(collector, job);
		order.verify(collector).syncMissingRounds();
		order.verify(job).catchUp();
		order.verify(collector).syncStores();
	}

	@Test
	void 회차_수집이_실패해도_보충은_한다() {
		// given
		when(collector.syncMissingRounds()).thenThrow(new CollectorException("down"));

		// when
		startup.collectOnStartup();

		// then
		verify(job).catchUp();
		verify(collector, never()).syncStores();
	}

	@Test
	void 보충이_실패해도_판매점은_수집한다() {
		// given
		doThrow(new IllegalStateException("db")).when(job).catchUp();

		// when
		startup.collectOnStartup();

		// then
		verify(collector).syncStores();
	}

}
