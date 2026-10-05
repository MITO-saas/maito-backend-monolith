package com.maito.notification.api.service;

import com.maito.notification.api.dto.NotificationLogDto;
import com.maito.notification.api.dto.NotificationMessage;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface NotificationDispatchService {
    CompletableFuture<NotificationLogDto> dispatchAsync(NotificationMessage message);
    NotificationLogDto dispatchSync(NotificationMessage message);
    List<NotificationLogDto> getNotificationLogsByRecipient(String recipient);
}
