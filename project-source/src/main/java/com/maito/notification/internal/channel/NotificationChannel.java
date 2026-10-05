package com.maito.notification.internal.channel;

import com.maito.notification.api.dto.NotificationChannelType;
import com.maito.notification.api.dto.NotificationMessage;

public interface NotificationChannel {
    NotificationChannelType getChannelType();
    boolean send(NotificationMessage message);
}
