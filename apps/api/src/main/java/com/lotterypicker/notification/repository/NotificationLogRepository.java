package com.lotterypicker.notification.repository;

import com.lotterypicker.notification.NotificationKind;
import com.lotterypicker.notification.NotificationStatus;
import com.lotterypicker.notification.entity.NotificationLog;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface NotificationLogRepository extends Repository<NotificationLog, Long> {

	NotificationLog saveAndFlush(NotificationLog log);

	Optional<NotificationLog> findByKindAndTargetKey(NotificationKind kind, String targetKey);

	/** 실패했거나 발송 중 멈춘 기록을 선점한다. 갱신된 행이 1이면 이 호출이 보낼 차례다. */
	@Transactional
	@Modifying
	@Query("""
			update NotificationLog n
			set n.status = com.lotterypicker.notification.NotificationStatus.PENDING, n.updatedAt = :now
			where n.kind = :kind and n.targetKey = :targetKey
			  and (n.status = com.lotterypicker.notification.NotificationStatus.FAILED
			    or (n.status = com.lotterypicker.notification.NotificationStatus.PENDING
			      and n.updatedAt < :staleBefore))
			""")
	int reclaim(@Param("kind") NotificationKind kind, @Param("targetKey") String targetKey,
			@Param("now") LocalDateTime now, @Param("staleBefore") LocalDateTime staleBefore);

	@Transactional
	@Modifying
	@Query("""
			update NotificationLog n
			set n.status = :status, n.attempts = n.attempts + :attempts, n.updatedAt = :now
			where n.kind = :kind and n.targetKey = :targetKey
			""")
	int finish(@Param("kind") NotificationKind kind, @Param("targetKey") String targetKey,
			@Param("status") NotificationStatus status, @Param("attempts") int attempts,
			@Param("now") LocalDateTime now);

}
