package com.abv.hrerpisapi.device.mapper;

import com.abv.hrerpisapi.device.model.ParsedAcsEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.OffsetDateTime;

/**
 * Maps alertStream multipart JSON payloads to {@link ParsedAcsEvent}.
 * The alertStream envelope looks like:
 * <pre>
 * {
 *   "eventType": "AccessControllerEvent",
 *   "dateTime": "2024-01-01T08:00:00+04:00",
 *   "AccessControllerEvent": { "majorEventType": 5, "subEventType": 1, ... }
 * }
 * </pre>
 */
@Component
public class IsapiEventMapper {

    private static final ObjectMapper om = new ObjectMapper();

    /**
     * @param json alertStream part body
     * @return parsed event, or {@code null} if not an AccessControllerEvent
     */
    public ParsedAcsEvent map(String json) throws IOException {
        JsonNode root = om.readTree(json);

        if (!"AccessControllerEvent".equals(root.path("eventType").asText())) {
            return null;
        }

        JsonNode ace = root.path("AccessControllerEvent");
        if (ace.isMissingNode() || ace.isNull()) {
            return null;
        }

        String dt = root.path("dateTime").asText(null);
        OffsetDateTime time = dt != null ? OffsetDateTime.parse(dt) : null;

        long serialNo = ace.path("serialNo").asLong(-1);
        int major = ace.path("majorEventType").asInt(-1);
        int minor = ace.path("subEventType").asInt(-1);
        String employeeNo = ace.path("employeeNoString").asText("");
        String cardNo = ace.path("cardNo").asText("");

        return new ParsedAcsEvent(serialNo, time, major, minor, employeeNo, cardNo, json);
    }
}
