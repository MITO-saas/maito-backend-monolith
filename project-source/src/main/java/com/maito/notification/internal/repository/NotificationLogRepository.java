package com.maito.notification.internal.repository;

import com.maito.notification.internal.domain.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {
    List<NotificationLog> findByRecipientOrderByCreatedAtDesc(String recipient);
    List<NotificationLog> findByChannelOrderByCreatedAtDesc(String channel);
}
