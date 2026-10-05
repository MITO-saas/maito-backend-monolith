package com.maito.notification.internal.service;

import com.maito.notification.api.dto.NotificationChannelType;
import com.maito.notification.api.dto.NotificationLogDto;
import com.maito.notification.api.dto.NotificationMessage;
import com.maito.notification.api.service.NotificationDispatchService;
import com.maito.notification.internal.channel.NotificationChannel;
import com.maito.notification.internal.domain.NotificationLog;
import com.maito.notification.internal.repository.NotificationLogRepository;
import com.maito.shared.exception.BusinessException;
import com.maito.shared.exception.ErrorCode;
import com.maito.tenant.routing.TenantContext;
import com.maito.tenant.routing.TenantContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class NotificationDispatchServiceImpl implements NotificationDispatchService {

    private final Map<NotificationChannelType, NotificationChannel> channelMap = new EnumMap<>(NotificationChannelType.class);
    private final NotificationLogRepository notificationLogRepository;

    public NotificationDispatchServiceImpl(List<NotificationChannel> channels, NotificationLogRepository notificationLogRepository) {
        for (NotificationChannel channel : channels) {
            this.channelMap.put(channel.getChannelType(), channel);
        }
        this.notificationLogRepository = notificationLogRepository;
    }

    @Override
    @Async
    public CompletableFuture<NotificationLogDto> dispatchAsync(NotificationMessage message) {
        TenantContext currentContext = TenantContextHolder.get();
        return CompletableFuture.supplyAsync(() -> {
            if (currentContext != null) {
                TenantContextHolder.set(currentContext);
            }
            try {
                return dispatchSync(message);
            } finally {
                TenantContextHolder.clear();
            }
        });
    }

    @Override
    @Transactional
    public NotificationLogDto dispatchSync(NotificationMessage message) {
        NotificationChannel channel = channelMap.get(message.channel());
        if (channel == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Unsupported notification channel: " + message.channel());
        }

        boolean success = false;
        try {
            success = channel.send(message);
        } catch (Exception ex) {
            log.error("Failed to send notification via [{}]: {}", message.channel(), ex.getMessage(), ex);
        }

        Map<String, Object> payloadSnapshot = new HashMap<>();
        payloadSnapshot.put("subject", message.subject());
        payloadSnapshot.put("content", message.content());
        payloadSnapshot.put("executedThread", Thread.currentThread().getName());
        if (message.metadata() != null) {
            payloadSnapshot.put("metadata", message.metadata());
        }

        NotificationLog logEntry = NotificationLog.builder()
                .recipient(message.recipient())
                .channel(message.channel().name())
                .templateCode(message.templateCode() != null ? message.templateCode() : "GENERIC")
                .status(success ? "SENT" : "FAILED")
                .payloadSnapshot(payloadSnapshot)
                .sentAt(Instant.now())
                .build();

        NotificationLog saved = notificationLogRepository.save(logEntry);
        log.info("Notification log stored: id=[{}] channel=[{}] recipient=[{}] status=[{}] thread=[{}]",
                saved.getId(), saved.getChannel(), saved.getRecipient(), saved.getStatus(), Thread.currentThread().getName());

        return toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationLogDto> getNotificationLogsByRecipient(String recipient) {
        return notificationLogRepository.findByRecipientOrderByCreatedAtDesc(recipient).stream()
                .map(this::toDto)
                .toList();
    }

    private NotificationLogDto toDto(NotificationLog n) {
        return new NotificationLogDto(
                n.getId(),
                n.getRecipient(),
                n.getChannel(),
                n.getTemplateCode(),
                n.getStatus(),
                n.getPayloadSnapshot(),
                n.getSentAt(),
                n.getCreatedAt()
        );
    }
}
