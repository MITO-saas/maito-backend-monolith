package com.maito.notification.internal.channel;

import com.maito.notification.api.dto.NotificationChannelType;
import com.maito.notification.api.dto.NotificationMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class EmailNotificationChannel implements NotificationChannel {

    @Override
    public NotificationChannelType getChannelType() {
        return NotificationChannelType.EMAIL;
    }

    @Override
    public boolean send(NotificationMessage message) {
        log.info("[SANDBOX EMAIL DISPATCH] To: [{}] | Template: [{}] | Subject: [{}] | Content: [{}]",
                message.recipient(), message.templateCode(), message.subject(), message.content());
        return true;
    }
}
