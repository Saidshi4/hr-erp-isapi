package com.abv.hrerpisapi.scheduler;

import com.abv.hrerpisapi.dao.entity.DeviceCursorEntity;
import com.abv.hrerpisapi.dao.entity.DeviceEntity;
import com.abv.hrerpisapi.dao.repository.DeviceCursorRepository;
import com.abv.hrerpisapi.dao.repository.DeviceRepository;
import com.abv.hrerpisapi.device.client.IsapiClient;
import com.abv.hrerpisapi.device.model.ParsedAcsEvent;
import com.abv.hrerpisapi.service.AcsIngestService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runs every minute and fills any events that were missed while the
 * alertStream was disconnected, by querying the device history API.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AcsEventHistoryPoller {

    private static final int MAX_RESULTS = 30;
    /** Small backward overlap to tolerate clock skew / edge races */
    private static final int OVERLAP_MINUTES = 2;

    @Value("${acs.history.unsupported.cooldown-minutes:60}")
    private long unsupportedCooldownMinutes;

    private final DeviceRepository deviceRepository;
    private final DeviceCursorRepository cursorRepository;
    private final IsapiClient isapiClient;
    private final AcsIngestService acsIngestService;

    private final Map<Long, OffsetDateTime> historyPollingDisabledUntil = new ConcurrentHashMap<>();

    @Scheduled(fixedDelay = 60_000)
    public void poll() {
        log.info("HistoryPoller tick");
        var devices = deviceRepository.findByEnabledTrue();
        log.info("HistoryPoller tick: enabledDevices={}", devices.size());
        devices.forEach(device -> {
            log.info("HistoryPoller: device={} polling started", device.getId());

            boolean disabled = isHistoryPollingDisabled(device.getId());
            log.info("HistoryPoller: device={} disabled={}", device.getId(), disabled);
            if (disabled) return;

            try {

                List<ParsedAcsEvent> events = fetchMissedEvents(device);
                log.info("HistoryPoller: device={} history returned {} event(s)", device.getId(), events.size());
                if (events.isEmpty()) {
                    return;
                }

                log.info("HistoryPoller: device={} fetched {} event(s) from history API",
                        device.getId(), events.size());

                long maxSerialIngested = 0L;
                OffsetDateTime maxEventTimeIngested = null;

                for (ParsedAcsEvent e : events) {
                    try {
                        acsIngestService.ingest(device, e);

                        if (e.serialNo() > maxSerialIngested) {
                            maxSerialIngested = e.serialNo();
                        }
                        if (e.eventTime() != null
                                && (maxEventTimeIngested == null || e.eventTime().isAfter(maxEventTimeIngested))) {
                            maxEventTimeIngested = e.eventTime();
                        }
                    } catch (Exception ex) {
                        // Do not advance cursor beyond an event that failed to ingest
                        log.warn("HistoryPoller: ingest failed device={} serialNo={} eventTime={} msg={}",
                                device.getId(), e.serialNo(), e.eventTime(), ex.getMessage());
                        log.debug("HistoryPoller: ingest exception", ex);
                    }
                }

                updateCursorIfAdvanced(device.getId(), maxSerialIngested, maxEventTimeIngested);

            } catch (IsapiClient.AcsEventHistoryNotSupportedException e) {
                disableHistoryPolling(device);
            } catch (Exception e) {
                log.warn("HistoryPoller failed for device {} ({}): {}",
                        device.getId(), device.getIp(), e.getMessage());
                log.debug("HistoryPoller exception for device {}", device.getId(), e);
            }
        });
    }

    private List<ParsedAcsEvent> fetchMissedEvents(DeviceEntity device)
            throws IOException, InterruptedException {

        DeviceCursorEntity cursor = cursorRepository.findById(device.getId()).orElse(null);

        OffsetDateTime startTime = (cursor != null && cursor.getLastEventTime() != null)
                ? cursor.getLastEventTime().minusMinutes(OVERLAP_MINUTES)
                : OffsetDateTime.now().minusHours(1);

        long afterSerialNo = (cursor != null && cursor.getLastSerialNo() != null)
                ? cursor.getLastSerialNo()
                : 0L;

        return isapiClient.searchAcsEvents(device, startTime, afterSerialNo, MAX_RESULTS);
    }

    private void updateCursorIfAdvanced(Long deviceId, long maxSerialIngested, OffsetDateTime maxEventTimeIngested) {
        if (maxSerialIngested <= 0 && maxEventTimeIngested == null) {
            return;
        }

        DeviceCursorEntity cursor = cursorRepository.findById(deviceId)
                .orElseGet(() -> {
                    DeviceCursorEntity c = new DeviceCursorEntity();
                    c.setDeviceId(deviceId);
                    c.setLastSerialNo(0L);
                    return c;
                });

        boolean changed = false;

        Long currentSerial = cursor.getLastSerialNo();
        if (maxSerialIngested > 0 && (currentSerial == null || maxSerialIngested > currentSerial)) {
            cursor.setLastSerialNo(maxSerialIngested);
            changed = true;
        }

        OffsetDateTime currentEventTime = cursor.getLastEventTime();
        if (maxEventTimeIngested != null && (currentEventTime == null || maxEventTimeIngested.isAfter(currentEventTime))) {
            cursor.setLastEventTime(maxEventTimeIngested);
            changed = true;
        }

        if (changed) {
            cursorRepository.save(cursor);
            log.info("HistoryPoller: cursor updated deviceId={} lastSerialNo={} lastEventTime={}",
                    deviceId, cursor.getLastSerialNo(), cursor.getLastEventTime());
        }
    }

    private boolean isHistoryPollingDisabled(Long deviceId) {
        OffsetDateTime disabledUntil = historyPollingDisabledUntil.get(deviceId);
        if (disabledUntil == null) {
            return false;
        }
        if (disabledUntil.isAfter(OffsetDateTime.now())) {
            return true;
        }
        historyPollingDisabledUntil.remove(deviceId, disabledUntil);
        return false;
    }

    private void disableHistoryPolling(DeviceEntity device) {
        OffsetDateTime disabledUntil = OffsetDateTime.now()
                .plusMinutes(Math.max(1L, unsupportedCooldownMinutes));

        historyPollingDisabledUntil.put(device.getId(), disabledUntil);

        log.info("HistoryPoller: disabling history polling for device {} ({}) until {} " +
                        "because /ISAPI/AccessControl/AcsEvent is not supported",
                device.getId(), device.getIp(), disabledUntil);
    }

    @PostConstruct
    void init() {
        log.info("HistoryPoller bean initialized");
    }
}