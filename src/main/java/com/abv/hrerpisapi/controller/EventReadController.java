package com.abv.hrerpisapi.controller;

import com.abv.hrerpisapi.dao.entity.AcsFailedAttemptEntity;
import com.abv.hrerpisapi.dao.entity.AcsRawEventEntity;
import com.abv.hrerpisapi.dao.entity.AttendancePunchEntity;
import com.abv.hrerpisapi.dao.repository.AcsFailedAttemptRepository;
import com.abv.hrerpisapi.dao.repository.AcsRawEventRepository;
import com.abv.hrerpisapi.dao.repository.AttendancePunchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class EventReadController {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 500;

    private final AttendancePunchRepository attendancePunchRepository;
    private final AcsRawEventRepository acsRawEventRepository;
    private final AcsFailedAttemptRepository acsFailedAttemptRepository;

    @GetMapping("/punches")
    public List<PunchResponse> getPunches(
            @RequestParam(required = false) Long deviceId,
            @RequestParam(required = false) String employeeNo,
            @RequestParam(required = false) Integer limit
    ) {
        String normalizedEmployeeNo = normalizeToNull(employeeNo);
        Pageable pageable = PageRequest.of(0, validateLimit(limit));

        List<AttendancePunchEntity> punches;
        if (deviceId != null && normalizedEmployeeNo != null) {
            punches = attendancePunchRepository.findByDeviceIdAndEmployeeNoOrderByPunchTimeDesc(deviceId, normalizedEmployeeNo, pageable);
        } else if (deviceId != null) {
            punches = attendancePunchRepository.findByDeviceIdOrderByPunchTimeDesc(deviceId, pageable);
        } else if (normalizedEmployeeNo != null) {
            punches = attendancePunchRepository.findByEmployeeNoOrderByPunchTimeDesc(normalizedEmployeeNo, pageable);
        } else {
            punches = attendancePunchRepository.findByOrderByPunchTimeDesc(pageable);
        }

        return punches.stream()
                .map(p -> new PunchResponse(
                        p.getId(),
                        p.getDeviceId(),
                        p.getEmployeeNo(),
                        p.getPunchTime(),
                        p.getRawEventId()
                ))
                .toList();
    }

    @GetMapping("/raw-events")
    public List<RawEventResponse> getRawEvents(
            @RequestParam(required = false) Long deviceId,
            @RequestParam(required = false) Integer major,
            @RequestParam(required = false) Integer minor,
            @RequestParam(defaultValue = "false") boolean includeRawJson,
            @RequestParam(required = false) Integer limit
    ) {
        Pageable pageable = PageRequest.of(0, validateLimit(limit));

        return acsRawEventRepository.findRecent(deviceId, major, minor, pageable).stream()
                .map(e -> new RawEventResponse(
                        e.getId(),
                        e.getDeviceId(),
                        e.getSerialNo(),
                        e.getEventTime(),
                        e.getMajorEventType(),
                        e.getSubEventType(),
                        e.getEmployeeNoString(),
                        e.getCardNo(),
                        includeRawJson ? e.getRawJson() : null
                ))
                .toList();
    }

    @GetMapping("/failed-attempts")
    public List<FailedAttemptResponse> getFailedAttempts(
            @RequestParam(required = false) Long deviceId,
            @RequestParam(required = false) Integer limit
    ) {
        Pageable pageable = PageRequest.of(0, validateLimit(limit));

        List<AcsFailedAttemptEntity> attempts = deviceId == null
                ? acsFailedAttemptRepository.findByOrderByEventTimeDesc(pageable)
                : acsFailedAttemptRepository.findByDeviceIdOrderByEventTimeDesc(deviceId, pageable);

        return attempts.stream()
                .map(a -> new FailedAttemptResponse(
                        a.getId(),
                        a.getDeviceId(),
                        a.getIdentity(),
                        a.getSubEventType(),
                        a.getEventTime(),
                        a.getRawEventId()
                ))
                .toList();
    }

    private static int validateLimit(Integer limit) {
        int resolved = limit == null ? DEFAULT_LIMIT : limit;
        if (resolved < 1 || resolved > MAX_LIMIT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be between 1 and 500");
        }
        return resolved;
    }

    private static String normalizeToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record PunchResponse(
            Long id,
            Long deviceId,
            String employeeNo,
            OffsetDateTime punchTime,
            Long rawEventId
    ) {
    }

    public record RawEventResponse(
            Long id,
            Long deviceId,
            Long serialNo,
            OffsetDateTime eventTime,
            Integer majorEventType,
            Integer subEventType,
            String employeeNoString,
            String cardNo,
            String rawJson
    ) {
    }

    public record FailedAttemptResponse(
            Long id,
            Long deviceId,
            String identity,
            Integer subEventType,
            OffsetDateTime eventTime,
            Long rawEventId
    ) {
    }
}
