package com.abv.hrerpisapi.device.client;

import com.abv.hrerpisapi.dao.entity.DeviceEntity;
import com.abv.hrerpisapi.device.DigestHttpClient;
import com.abv.hrerpisapi.device.model.ParsedAcsEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * ISAPI REST client for UserInfo, CardInfo and AcsEvent endpoints.
 * A new {@link DigestHttpClient} is created per call (stateless/thread-safe).
 */
@Slf4j
@Service
public class IsapiClient {

    private static final ObjectMapper OM = new ObjectMapper();

    // Device expects ISO8601 with offset (e.g. 2026-04-21T15:19:00+04:00)
    private static final DateTimeFormatter ISAPI_DT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    // DS-K1T series expects local datetime without offset (e.g. 2026-01-01T00:00:00)
    private static final DateTimeFormatter ISAPI_LOCAL_DT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private static final int ACS_EVENT_MAX_RESULTS_CAP = 30;
    private static final int MAX_HISTORY_PAGES = 200;
    private static final String ACS_EVENT_ENDPOINT = "/ISAPI/AccessControl/AcsEvent?format=json";

    @Value("${acs.history.major:5}")
    private int historyMajor;

    @Value("${acs.history.minor:75}")
    private int historyMinor;

    // -----------------------------------------------------------------------
    // Card lookup
    // -----------------------------------------------------------------------

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
     * Searches ACS event history. Sends beginSerialNo when afterSerialNo > 0
     * to allow firmware-level filtering. A local serialNo guard is applied as
     * an additional de-duplication safeguard for devices that ignore beginSerialNo.
     */
    public List<ParsedAcsEvent> searchAcsEvents(DeviceEntity device,
                                                OffsetDateTime startTime,
                                                long afterSerialNo,
                                                int maxResults)
            throws IOException, InterruptedException {

        ZoneOffset deviceOffset = OffsetDateTime.now().getOffset();

        OffsetDateTime startForDevice = startTime.withOffsetSameInstant(deviceOffset);
        OffsetDateTime endForDevice = OffsetDateTime.now().withOffsetSameInstant(deviceOffset);

        String start = formatIsapiDateTime(startForDevice);
        String end = formatIsapiDateTime(endForDevice);

        String searchId = UUID.randomUUID().toString();
        int cappedResults = Math.max(1, Math.min(maxResults, ACS_EVENT_MAX_RESULTS_CAP));

        List<ParsedAcsEvent> result = new ArrayList<>();
        int searchResultPosition = 0;
        boolean exceededPageLimit = true;
        int filteredBySerialGuard = 0;

        for (int page = 0; page < MAX_HISTORY_PAGES; page++) {
            String body = buildAcsEventBody(searchId, start, end, searchResultPosition, cappedResults, afterSerialNo);

            log.info("AcsEvent history request device={} endpoint={} searchID={} position={} maxResults={} major={} minor={} startTime={} endTime={}",
                    device.getId(), ACS_EVENT_ENDPOINT, searchId, searchResultPosition, cappedResults,
                    historyMajor, historyMinor, start, end);

            log.info("AcsEvent history request body: {}", body);

            HttpResponse<String> resp = clientFor(device)
                    .post(ACS_EVENT_ENDPOINT, "application/json", body);

            if (resp.statusCode() != 200) {
                if (isAcsEventHistoryNotSupported(resp.statusCode(), resp.body())) {
                    throw new AcsEventHistoryNotSupportedException(device.getId());
                }
                log.warn("AcsEvent history request failed: device={} endpoint={} status={} startTime={} endTime={} body={}",
                        device.getId(), ACS_EVENT_ENDPOINT, resp.statusCode(), start, end, snippet(resp.body()));
                return result;
            }

            JsonNode acsEvent = OM.readTree(resp.body()).path("AcsEvent");
            int numOfMatches = acsEvent.path("numOfMatches").asInt(0);

            String responseStatus = acsEvent.path("responseStatusStrg").asText("");
            int totalMatches = acsEvent.path("totalMatches").asInt(0);

            log.info("AcsEvent history response: device={} totalMatches={} numOfMatches={} responseStatusStrg={}",
                    device.getId(), totalMatches, numOfMatches, responseStatus);

            JsonNode events = acsEvent.path("InfoList");
            if (events.isArray()) {
                for (JsonNode node : events) {
                    long serialNo = node.path("serialNo").asLong(-1);

                    // Local serial guard (de-dup and ignore already-processed events)
                    if (afterSerialNo > 0 && serialNo > 0 && serialNo <= afterSerialNo) {
                        filteredBySerialGuard++;
                        continue;
                    }

                    try {
                        result.add(parseHistoryEvent(node));
                    } catch (Exception e) {
                        log.warn("Failed to parse history event serialNo={}: {}", serialNo, e.getMessage());
                    }
                }
            }

            if (numOfMatches <= 0 || !"MORE".equalsIgnoreCase(responseStatus)) {
                exceededPageLimit = false;
                break;
            }

            int nextPosition = searchResultPosition + numOfMatches;
            if (nextPosition <= searchResultPosition) {
                log.warn("Stopping AcsEvent history pagination due to non-advancing position: device={} position={} numOfMatches={}",
                        device.getId(), searchResultPosition, numOfMatches);
                break;
            }
            searchResultPosition = nextPosition;
        }

        if (exceededPageLimit) {
            log.warn("Stopped AcsEvent history pagination after reaching safety page limit: device={} pages={}.",
                    device.getId(), MAX_HISTORY_PAGES);
        }
        if (filteredBySerialGuard > 0) {
            log.info("AcsEvent history serial guard filtered {} event(s) for device={} afterSerialNo={}",
                    filteredBySerialGuard, device.getId(), afterSerialNo);
        }
        return result;
    }

    // -----------------------------------------------------------------------
    // DS-K1T series device user management
    // -----------------------------------------------------------------------

    /**
     * Adds a user to a DS-K1T series device via UserInfo/Record endpoint.
     */
    public UserOperationResult addDeviceUser(DeviceEntity device,
                                             String employeeNo,
                                             String name,
                                             String userType,
                                             String gender,
                                             LocalDateTime beginTime,
                                             LocalDateTime endTime)
            throws IOException, InterruptedException {

        String body = OM.writeValueAsString(
                Map.of("UserInfo", buildUserInfoMap(employeeNo, name, userType, gender, beginTime, endTime)));

        HttpResponse<String> resp = clientFor(device)
                .post("/ISAPI/AccessControl/UserInfo/Record?format=json", "application/json", body);

        return toUserOperationResult(resp);
    }

    /**
     * Updates a user on a DS-K1T series device via UserInfo/Modify endpoint.
     */
    public UserOperationResult updateDeviceUser(DeviceEntity device,
                                                String employeeNo,
                                                String name,
                                                String userType,
                                                String gender,
                                                LocalDateTime beginTime,
                                                LocalDateTime endTime)
            throws IOException, InterruptedException {

        String body = OM.writeValueAsString(
                Map.of("UserInfo", buildUserInfoMap(employeeNo, name, userType, gender, beginTime, endTime)));

        HttpResponse<String> resp = clientFor(device)
                .put("/ISAPI/AccessControl/UserInfo/Modify?format=json", "application/json", body);

        return toUserOperationResult(resp);
    }

    /**
     * Deletes a user from a DS-K1T series device.
     */
    public UserOperationResult deleteDeviceUser(DeviceEntity device, String employeeNo)
            throws IOException, InterruptedException {

        String encoded = URLEncoder.encode(employeeNo, StandardCharsets.UTF_8);
        HttpResponse<String> resp = clientFor(device)
                .delete("/ISAPI/AccessControl/UserInfo/Delete?format=json&userName=" + encoded,
                        "application/json", "");

        return toUserOperationResult(resp);
    }

    /**
     * Checks whether a user with the given employeeNo exists on the device.
     */
    public boolean deviceUserExists(DeviceEntity device, String employeeNo)
            throws IOException, InterruptedException {

        String body = OM.writeValueAsString(
                Map.of("UserInfoSearchCond", Map.of(
                        "searchID", "search_" + employeeNo,
                        "searchResultPosition", 0,
                        "maxResults", 1,
                        "UserInfo", Map.of("employeeNo", employeeNo))));

        HttpResponse<String> resp = clientFor(device)
                .post("/ISAPI/AccessControl/UserInfo/Search?format=json", "application/json", body);

        if (resp.statusCode() != 200) return false;

        JsonNode root = OM.readTree(resp.body());
        JsonNode list = root.path("UserInfo");
        return list.isArray() && !list.isEmpty();
    }

    /**
     * Uploads a face photo to a DS-K1T device via URL reference.
     */
    public UserOperationResult uploadFaceByUrl(DeviceEntity device, String employeeNo, String faceUrl)
            throws IOException, InterruptedException {

        String body = OM.writeValueAsString(
                Map.of("FaceData", Map.of(
                        "faceLibType", "normalFD",
                        "employeeNo", employeeNo,
                        "faceURL", faceUrl)));

        HttpResponse<String> resp = clientFor(device)
                .put("/ISAPI/AccessControl/Face/FaceData?format=json", "application/json", body);

        return toUserOperationResult(resp);
    }

    /**
     * Uploads a face photo to a DS-K1T device as binary multipart/form-data.
     */
    public UserOperationResult uploadFaceBinary(DeviceEntity device, String employeeNo, byte[] imageBytes)
            throws IOException, InterruptedException {

        String boundary = "----HikIsapiBoundary" + UUID.randomUUID().toString().replace("-", "");
        String jsonPart = "{\"FaceData\":{\"faceLibType\":\"normalFD\",\"employeeNo\":\""
                + employeeNo + "\"}}";

        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        baos.write(("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"FaceData\"; filename=\"FaceData.json\"\r\n"
                + "Content-Type: application/json\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        baos.write(jsonPart.getBytes(StandardCharsets.UTF_8));
        baos.write(("\r\n--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"FaceImage\"; filename=\"face.jpg\"\r\n"
                + "Content-Type: image/jpeg\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        baos.write(imageBytes);
        baos.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpResponse<String> resp = clientFor(device)
                .putBytes("/ISAPI/AccessControl/Face/FaceData?format=json",
                        "multipart/form-data; boundary=" + boundary, baos.toByteArray());

        return toUserOperationResult(resp);
    }

    // -----------------------------------------------------------------------
    // Legacy user management (generic devices)
    // -----------------------------------------------------------------------

    public UserOperationResult addUser(DeviceEntity device, String userName, String password, String userType)
            throws IOException, InterruptedException {

        String body = OM.writeValueAsString(
                Map.of("UserInfo", Map.of("userName", userName, "password", password, "userType", userType)));

        HttpResponse<String> resp = clientFor(device)
                .post("/ISAPI/AccessControl/UserInfo/SetUp?format=json", "application/json", body);

        return toUserOperationResult(resp);
    }

    public UserOperationResult updateUser(DeviceEntity device, String userName, String password, String userType)
            throws IOException, InterruptedException {

        String body = OM.writeValueAsString(
                Map.of("UserInfo", Map.of("userName", userName, "password", password, "userType", userType)));

        HttpResponse<String> resp = clientFor(device)
                .put("/ISAPI/AccessControl/UserInfo/Modify?format=json", "application/json", body);

        return toUserOperationResult(resp);
    }

    public UserOperationResult deleteUser(DeviceEntity device, String userName)
            throws IOException, InterruptedException {

        String encodedUserName = URLEncoder.encode(userName, StandardCharsets.UTF_8);
        HttpResponse<String> resp = clientFor(device)
                .delete("/ISAPI/AccessControl/UserInfo/Delete?format=json&userName=" + encodedUserName, "application/json", "");

        return toUserOperationResult(resp);
    }

    public List<String> listUsers(DeviceEntity device)
            throws IOException, InterruptedException {

        HttpResponse<String> resp = clientFor(device)
                .get("/ISAPI/AccessControl/UserInfo/UserInfoList?format=json");

        if (resp.statusCode() != 200) {
            log.warn("UserInfo/UserInfoList returned HTTP {} for device {}", resp.statusCode(), device.getId());
            return List.of();
        }

        JsonNode root = OM.readTree(resp.body());
        JsonNode list = root.path("UserInfo");
        if (!list.isArray()) return List.of();

        List<String> userNames = new ArrayList<>();
        for (JsonNode node : list) {
            String userName = node.path("userName").asText("");
            if (!userName.isBlank()) userNames.add(userName);
        }
        return userNames;
    }

    public boolean userExists(DeviceEntity device, String userName)
            throws IOException, InterruptedException {

        String body = OM.writeValueAsString(
                Map.of("UserInfoSearchCond", Map.of(
                        "searchID", "1",
                        "SearchResultPosition", 0,
                        "maxResults", 1,
                        "UserInfo", Map.of("userName", userName))));

        HttpResponse<String> resp = clientFor(device)
                .post("/ISAPI/AccessControl/UserInfo/Search?format=json", "application/json", body);

        if (resp.statusCode() != 200) return false;

        JsonNode root = OM.readTree(resp.body());
        JsonNode list = root.path("UserInfo");
        return list.isArray() && !list.isEmpty();
    }

    private UserOperationResult toUserOperationResult(HttpResponse<String> resp) {
        boolean success = resp.statusCode() >= 200 && resp.statusCode() < 300;
        return new UserOperationResult(success, resp.statusCode(), snippet(resp.body()));
    }

    public record UserOperationResult(boolean success, int statusCode, String responseSnippet) {}

    public DeviceStatusCheckResult checkDeviceStatus(DeviceEntity device) {
        try {
            HttpResponse<String> resp = clientFor(device).get("/ISAPI/System/deviceInfo?format=json");
            return new DeviceStatusCheckResult(
                    resp.statusCode() == 200,
                    resp.statusCode(),
                    snippet(resp.body()));
        } catch (Exception e) {
            log.info("Device status check failed for device {} ({}): {}", device.getId(), device.getIp(), e.getMessage());
            log.info("Device status check exception for device {}", device.getId(), e);
            return new DeviceStatusCheckResult(false, -1, snippet(e.getMessage()));
        }
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private Map<String, Object> buildUserInfoMap(String employeeNo,
                                                 String name,
                                                 String userType,
                                                 String gender,
                                                 LocalDateTime beginTime,
                                                 LocalDateTime endTime) {
        LocalDateTime effectiveBegin = beginTime != null ? beginTime
                : LocalDateTime.parse("2026-04-27T00:00:00");
        LocalDateTime effectiveEnd = endTime != null ? endTime
                : LocalDateTime.parse("2036-04-26T23:59:59");

        Map<String, Object> valid = new LinkedHashMap<>();
        valid.put("enable", true);
        valid.put("beginTime", effectiveBegin.format(ISAPI_LOCAL_DT));
        valid.put("endTime", effectiveEnd.format(ISAPI_LOCAL_DT));
        valid.put("timeType", "local");

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("employeeNo", employeeNo);
        map.put("name", name);
        map.put("userType", userType != null ? userType : "normal");
        if (gender != null) map.put("gender", gender);
        map.put("localUIRight", false);
        map.put("maxOpenDoorTime", 0);
        map.put("Valid", valid);
        map.put("doorRight", "1");
        map.put("RightPlan", List.of(Map.of("doorNo", 1, "planTemplateNo", "1")));
        map.put("userVerifyMode", "");
        return map;
    }

    private ParsedAcsEvent parseHistoryEvent(JsonNode node) {
        long serialNo = node.path("serialNo").asLong(-1);
        String timeStr = node.path("time").asText(null);
        OffsetDateTime time = timeStr != null ? OffsetDateTime.parse(timeStr) : null;

        int major = node.path("major").asInt(node.path("majorEventType").asInt(-1));
        int minor = node.path("minor").asInt(node.path("subEventType").asInt(-1));
        String employeeNo = node.path("employeeNoString").asText("");
        String cardNo = node.path("cardNo").asText("");

        return new ParsedAcsEvent(serialNo, time, major, minor, employeeNo, cardNo, node.toString());
    }

    private boolean isAcsEventHistoryNotSupported(int statusCode, String responseBody) {
        if (statusCode == 404) return true;
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

    static String formatIsapiDateTime(OffsetDateTime value) {
        return value.truncatedTo(ChronoUnit.SECONDS).format(ISAPI_DT);
    }

    private String buildAcsEventBody(String searchId,
                                     String start,
                                     String end,
                                     int searchResultPosition,
                                     int maxResults,
                                     long beginSerialNo) {
        String serialPart = "";
        if (beginSerialNo > 0) {
            // Device hər dəfə serialNo-nu sıfırlamaq istəmir -
            // Elə burada serial filtri istəmir, sırf pagination edir
            serialPart = ",\"beginSerialNo\":" + beginSerialNo;
            // endSerialNo SILMƏ - device onu qəbul etmir!
        }
        return """
            {"AcsEventCond":{"searchID":"%s","searchResultPosition":%d,"maxResults":%d,\
            "major":%d,"minor":%d,"startTime":"%s","endTime":"%s"%s}}"""
                .formatted(searchId, searchResultPosition, maxResults, historyMajor, historyMinor, start, end, serialPart);
    }

    private DigestHttpClient clientFor(DeviceEntity device) {
        return new DigestHttpClient(
                "http://" + device.getIp(),
                device.getUsername(),
                device.getPassword());
    }

    private String snippet(String raw) {
        if (raw == null) return "";
        String normalized = raw.replace("\n", " ").replace("\r", " ").trim();
        int max = 300;
        return normalized.length() <= max ? normalized : normalized.substring(0, max) + "...";
    }

    public record DeviceStatusCheckResult(boolean online, int statusCode, String responseSnippet) {}

    public static class AcsEventHistoryNotSupportedException extends RuntimeException {
        public AcsEventHistoryNotSupportedException(Long deviceId) {
            super("AcsEvent history is not supported for device " + deviceId);
        }
    }
}
