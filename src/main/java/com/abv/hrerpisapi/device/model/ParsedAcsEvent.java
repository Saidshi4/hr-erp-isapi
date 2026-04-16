package com.abv.hrerpisapi.device.model;

import java.time.OffsetDateTime;

/**
 * Internal representation of a single ACS event — used between mapper,
 * alertStream runner, history poller, and ingest service.
 */
public record ParsedAcsEvent(
        long serialNo,
        OffsetDateTime eventTime,
        int majorEventType,
        int subEventType,
        String employeeNoString,
        String cardNo,
        String rawJson
) {
}
