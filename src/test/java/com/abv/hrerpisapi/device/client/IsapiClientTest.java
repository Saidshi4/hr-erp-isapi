package com.abv.hrerpisapi.device.client;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IsapiClientTest {

    @Test
    void shouldFormatIsapiDateTimeWithOffsetAndWithoutFractionalSeconds() {
        OffsetDateTime value = OffsetDateTime.parse("2026-04-09T16:59:35.987+04:00");

        String actual = IsapiClient.formatIsapiDateTime(value);

        assertEquals("2026-04-09T16:59:35+04:00", actual);
    }

    @Test
    void shouldBuildAcsEventBodyWithStableSearchIdAndFormattedTimes() {
        IsapiClient client = new IsapiClient();
        ReflectionTestUtils.setField(client, "historyMajor", 5);
        ReflectionTestUtils.setField(client, "historyMinor", 75);

        String body = ReflectionTestUtils.invokeMethod(
                client,
                "buildAcsEventBody",
                "poll-1",
                "2026-04-09T16:59:35+04:00",
                "2026-04-10T16:59:35+04:00",
                30,
                30,
                101L
        );

        assertTrue(body.contains("\"searchID\":\"poll-1\""));
        assertTrue(body.contains("\"searchResultPosition\":30"));
        assertTrue(body.contains("\"maxResults\":30"));
        assertTrue(body.contains("\"major\":5"));
        assertTrue(body.contains("\"minor\":75"));
        assertTrue(body.contains("\"beginSerialNo\":101"));
        assertTrue(body.contains("\"startTime\":\"2026-04-09T16:59:35+04:00\""));
        assertTrue(body.contains("\"endTime\":\"2026-04-10T16:59:35+04:00\""));
    }
}
