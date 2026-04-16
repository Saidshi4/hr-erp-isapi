package com.abv.hrerpisapi.scheduler;

import com.abv.hrerpisapi.dao.entity.DeviceCursorEntity;
import com.abv.hrerpisapi.dao.entity.DeviceEntity;
import com.abv.hrerpisapi.dao.repository.DeviceCursorRepository;
import com.abv.hrerpisapi.dao.repository.DeviceRepository;
import com.abv.hrerpisapi.device.client.IsapiClient;
import com.abv.hrerpisapi.device.model.ParsedAcsEvent;
import com.abv.hrerpisapi.service.AcsIngestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Runs every minute and fills any events that were missed while the
 * alertStream was disconnected, by querying the device history API.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AcsEventHistoryPoller {

    private static final int MAX_RESULTS = 50;
    /** Small backward overlap to tolerate clock skew / edge races */
    private static final int OVERLAP_MINUTES = 2;

    private final DeviceRepository deviceRepository;
    private final DeviceCursorRepository cursorRepository;
    private final IsapiClient isapiClient;
    private final AcsIngestService acsIngestService;

    @Scheduled(fixedDelay = 60_000)
    public void poll() {
        deviceRepository.findByEnabledTrue().forEach(device -> {
            try {
                List<ParsedAcsEvent> events = fetchMissedEvents(device);
                if (!events.isEmpty()) {
                    log.info("HistoryPoller: device={} fetched {} missed event(s)",
                            device.getId(), events.size());
                    events.forEach(e -> acsIngestService.ingest(device, e));
                }
            } catch (Exception e) {
                log.warn("HistoryPoller failed for device {} ({}): {}",
                        device.getId(), device.getIp(), e.getMessage());
            }
        });
    }

    private List<ParsedAcsEvent> fetchMissedEvents(DeviceEntity device)
            throws IOException, InterruptedException {

        DeviceCursorEntity cursor = cursorRepository.findById(device.getId()).orElse(null);

        OffsetDateTime startTime = cursor != null && cursor.getLastEventTime() != null
                ? cursor.getLastEventTime().minusMinutes(OVERLAP_MINUTES)
                : OffsetDateTime.now().minusHours(1);

        long afterSerialNo = cursor != null && cursor.getLastSerialNo() != null
                ? cursor.getLastSerialNo()
                : 0L;

        return isapiClient.searchAcsEvents(device, startTime, afterSerialNo, MAX_RESULTS);
    }
}
