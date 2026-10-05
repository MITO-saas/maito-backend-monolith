package com.maito.fulfillment.internal.carrier;

import java.time.Instant;

public record TrackingDetailsResult(
        String trackingNumber,
        String status,
        String locationHub,
        String statusDescription,
        Instant timestamp
) {}
