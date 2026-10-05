package com.maito.notification.internal.channel;

import com.maito.notification.api.dto.NotificationChannelType;
import com.maito.notification.api.dto.NotificationMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class SmsNotificationChannel implements NotificationChannel {

    @Override
    public NotificationChannelType getChannelType() {
        return NotificationChannelType.SMS;
    }

    @Override
    public boolean send(NotificationMessage message) {
        log.info("[SANDBOX SMS DISPATCH] To: [{}] | Template: [{}] | Message: [{}]",
                message.recipient(), message.templateCode(), message.content());
        return true;
    }
}
