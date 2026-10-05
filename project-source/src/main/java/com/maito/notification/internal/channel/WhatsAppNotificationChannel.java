package com.maito.notification.internal.channel;

import com.maito.notification.api.dto.NotificationChannelType;
import com.maito.notification.api.dto.NotificationMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class WhatsAppNotificationChannel implements NotificationChannel {

    @Override
    public NotificationChannelType getChannelType() {
        return NotificationChannelType.WHATSAPP;
    }

    @Override
    public boolean send(NotificationMessage message) {
        log.info("[SANDBOX WHATSAPP DISPATCH] To: [{}] | Template: [{}] | Interactive Payload: [{}]",
                message.recipient(), message.templateCode(), message.content());
        return true;
    }
}
