package com.abv.hrerpisapi.service;

import com.abv.hrerpisapi.dao.entity.*;
import com.abv.hrerpisapi.dao.repository.*;
import com.abv.hrerpisapi.device.model.ParsedAcsEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Persists ACS events into the appropriate tables:
 * <ul>
 *   <li>Every event → {@code acs_raw_events}</li>
 *   <li>Successful door open (major=5, minor in SUCCESS_MINORS) → {@code attendance_punches}</li>
 *   <li>Failed attempt (major=5, minor in FAILED_MINORS) → {@code acs_failed_attempts}</li>
 *   <li>Cursor updated in {@code device_cursors}</li>
 * </ul>
 * Duplicate events (same deviceId + serialNo) are silently ignored.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AcsIngestService {

    /** Card swipe (0x01) and face recognition (0x4B) — verified on real device */
    private static final Set<Integer> SUCCESS_MINORS = Set.of(1, 75);

    /** Door-open failure event codes */
    private static final Set<Integer> FAILED_MINORS = Set.of(39, 40, 65, 69, 76, 86);

    private final AcsRawEventRepository rawEventRepo;
    private final AttendancePunchRepository punchRepo;
    private final AcsFailedAttemptRepository failedRepo;
    private final DeviceCursorRepository cursorRepo;
    private final IdentityResolveService identityService;

    @Transactional
    public void ingest(DeviceEntity device, ParsedAcsEvent event) {
        // Duplicate guard
        if (rawEventRepo.existsByDeviceIdAndSerialNo(device.getId(), event.serialNo())) {
            log.debug("Duplicate event ignored: device={} serialNo={}", device.getId(), event.serialNo());
            return;
        }

        // 1) Persist raw event
        AcsRawEventEntity raw = new AcsRawEventEntity();
        raw.setDeviceId(device.getId());
        raw.setSerialNo(event.serialNo());
        raw.setEventTime(event.eventTime());
        raw.setMajorEventType(event.majorEventType());
        raw.setSubEventType(event.subEventType());
        raw.setEmployeeNoString(event.employeeNoString());
        raw.setCardNo(event.cardNo());
        raw.setRawJson(event.rawJson());
        rawEventRepo.save(raw);

        // 2) Resolve employee identity
        String employeeNo = identityService.resolve(device, event.employeeNoString(), event.cardNo());

        int major = event.majorEventType();
        int minor = event.subEventType();

        // 3) Route to correct table
        if (major == 5 && SUCCESS_MINORS.contains(minor)) {
            AttendancePunchEntity punch = new AttendancePunchEntity();
            punch.setRawEventId(raw.getId());
            punch.setDeviceId(device.getId());
            punch.setEmployeeNo(employeeNo);
            punch.setPunchTime(event.eventTime());
            punchRepo.save(punch);
            log.debug("Punch saved: device={} employee={} time={}", device.getId(), employeeNo, event.eventTime());

        } else if (major == 5 && FAILED_MINORS.contains(minor)) {
            AcsFailedAttemptEntity fail = new AcsFailedAttemptEntity();
            fail.setRawEventId(raw.getId());
            fail.setDeviceId(device.getId());
            fail.setIdentity(buildIdentity(employeeNo, event));
            fail.setSubEventType(minor);
            fail.setEventTime(event.eventTime());
            failedRepo.save(fail);
            log.debug("Failed attempt saved: device={} minor={}", device.getId(), minor);
        }

        // 4) Advance cursor
        DeviceCursorEntity cursor = cursorRepo.findById(device.getId()).orElseGet(() -> {
            DeviceCursorEntity c = new DeviceCursorEntity();
            c.setDeviceId(device.getId());
            return c;
        });

        if (cursor.getLastSerialNo() == null || event.serialNo() > cursor.getLastSerialNo()) {
            cursor.setLastSerialNo(event.serialNo());
        }
        if (event.eventTime() != null
                && (cursor.getLastEventTime() == null
                || event.eventTime().isAfter(cursor.getLastEventTime()))) {
            cursor.setLastEventTime(event.eventTime());
        }
        cursorRepo.save(cursor);
    }

    private String buildIdentity(String employeeNo, ParsedAcsEvent event) {
        if (employeeNo != null && !employeeNo.isBlank()) return "E:" + employeeNo;
        if (!event.cardNo().isBlank()) return "C:" + event.cardNo();
        return "U:" + event.serialNo();
    }
}
