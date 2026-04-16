package com.abv.hrerpisapi.device.client;

import com.abv.hrerpisapi.dao.entity.DeviceEntity;
import com.abv.hrerpisapi.device.DigestHttpClient;
import com.abv.hrerpisapi.device.model.ParsedAcsEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * ISAPI REST client for UserInfo, CardInfo and AcsEvent endpoints.
 * A new {@link DigestHttpClient} is created per call (stateless/thread-safe).
 */
@Slf4j
@Service
public class IsapiClient {

    private static final ObjectMapper OM = new ObjectMapper();
    private static final DateTimeFormatter ISAPI_DT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    // -----------------------------------------------------------------------
    // Card lookup
    // -----------------------------------------------------------------------

    /**
     * Looks up the employeeNo for a given card number via ISAPI CardInfo/Search.
     *
     * @return employeeNo, or empty if not found / request failed
     */
    public Optional<String> searchCardEmployeeNo(DeviceEntity device, String cardNo)
            throws IOException, InterruptedException {

        String body = """
                {"CardInfoSearchCond":{"searchID":"1","SearchResultPosition":0,"maxResults":1,\
                "CardInfo":{"cardNo":"%s"}}}""".formatted(cardNo);

        HttpResponse<String> resp = clientFor(device)
                .post("/ISAPI/AccessControl/CardInfo/Search?format=json",
                        "application/json", body);

        if (resp.statusCode() != 200) {
            log.warn("CardInfo/Search returned HTTP {} for device {}", resp.statusCode(), device.getId());
            return Optional.empty();
        }

        JsonNode root = OM.readTree(resp.body());
        JsonNode list = root.path("CardInfo");
        if (!list.isArray() || list.isEmpty()) return Optional.empty();

        String empNo = list.get(0).path("employeeNo").asText("");
        return empNo.isBlank() ? Optional.empty() : Optional.of(empNo);
    }

    // -----------------------------------------------------------------------
    // AcsEvent history search
    // -----------------------------------------------------------------------

    /**
     * Searches ACS event history for events with serialNo > afterSerialNo,
     * starting from startTime.
     */
    public List<ParsedAcsEvent> searchAcsEvents(DeviceEntity device,
                                                OffsetDateTime startTime,
                                                long afterSerialNo,
                                                int maxResults)
            throws IOException, InterruptedException {

        String start = startTime.format(ISAPI_DT);
        String end = OffsetDateTime.now().format(ISAPI_DT);

        String body = """
                {"AcsEventCond":{"searchID":"1","searchResultPosition":0,"maxResults":%d,\
                "major":5,"startTime":"%s","endTime":"%s"}}""".formatted(maxResults, start, end);

        HttpResponse<String> resp = clientFor(device)
                .post("/ISAPI/AccessControl/AcsEvent/Search?format=json",
                        "application/json", body);

        if (resp.statusCode() != 200) {
            if (isAcsEventHistoryNotSupported(resp.body())) {
                throw new AcsEventHistoryNotSupportedException(device.getId());
            }
            log.warn("AcsEvent/Search returned HTTP {} for device {}. responseBody={}",
                    resp.statusCode(), device.getId(), resp.body());
            return List.of();
        }

        JsonNode root = OM.readTree(resp.body());
        // History response: root.AcsEventCond.AcsEvent[]
        JsonNode events = root.path("AcsEventCond").path("AcsEvent");
        if (!events.isArray()) return List.of();

        List<ParsedAcsEvent> result = new ArrayList<>();
        for (JsonNode node : events) {
            long serialNo = node.path("serialNo").asLong(-1);
            if (serialNo <= afterSerialNo) continue;
            try {
                result.add(parseHistoryEvent(node));
            } catch (Exception e) {
                log.warn("Failed to parse history event serialNo={}: {}", serialNo, e.getMessage());
            }
        }
        return result;
    }

    public DeviceStatusCheckResult checkDeviceStatus(DeviceEntity device) {
        try {
            HttpResponse<String> resp = clientFor(device).get("/ISAPI/System/deviceInfo?format=json");
            return new DeviceStatusCheckResult(
                    resp.statusCode() == 200,
                    resp.statusCode(),
                    snippet(resp.body()));
        } catch (Exception e) {
            log.info("Device status check failed for device {} ({}): {}", device.getId(), device.getIp(), e.getMessage());
            log.debug("Device status check exception for device {}", device.getId(), e);
            return new DeviceStatusCheckResult(false, -1, snippet(e.getMessage()));
        }
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private ParsedAcsEvent parseHistoryEvent(JsonNode node) {
        long serialNo = node.path("serialNo").asLong(-1);
        // History events use "time" field; alertStream uses "dateTime"
        String timeStr = node.path("time").asText(null);
        OffsetDateTime time = timeStr != null ? OffsetDateTime.parse(timeStr) : null;

        int major = node.path("majorEventType").asInt(-1);
        int minor = node.path("subEventType").asInt(-1);
        String employeeNo = node.path("employeeNoString").asText("");
        String cardNo = node.path("cardNo").asText("");

        return new ParsedAcsEvent(serialNo, time, major, minor, employeeNo, cardNo, node.toString());
    }

    private boolean isAcsEventHistoryNotSupported(String responseBody) {
        try {
            JsonNode root = OM.readTree(responseBody);
            String subStatusCode = root.path("subStatusCode").asText("");
            String statusString = root.path("statusString").asText("");
            return "notSupport".equalsIgnoreCase(subStatusCode)
                    || "Invalid Operation".equalsIgnoreCase(statusString);
        } catch (Exception ignored) {
            return false;
        }
    }

    private DigestHttpClient clientFor(DeviceEntity device) {
        return new DigestHttpClient(
                "http://" + device.getIp(),
                device.getUsername(),
                device.getPassword());
    }

    private String snippet(String raw) {
        if (raw == null) {
            return "";
        }
        String normalized = raw.replace("\n", " ").replace("\r", " ").trim();
        int max = 300;
        return normalized.length() <= max
                ? normalized
                : normalized.substring(0, max) + "...";
    }

    public record DeviceStatusCheckResult(
            boolean online,
            int statusCode,
            String responseSnippet
    ) {
    }

    public static class AcsEventHistoryNotSupportedException extends RuntimeException {
        public AcsEventHistoryNotSupportedException(Long deviceId) {
            super("AcsEvent/Search is not supported for device " + deviceId);
        }
    }
}
