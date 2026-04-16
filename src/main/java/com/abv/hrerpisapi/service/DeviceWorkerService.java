package com.abv.hrerpisapi.service;

import com.abv.hrerpisapi.dao.entity.DeviceEntity;
import com.abv.hrerpisapi.dao.repository.DeviceRepository;
import com.abv.hrerpisapi.device.IsapiAlertStreamRunner;
import com.abv.hrerpisapi.device.mapper.IsapiEventMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads all enabled devices from the database on startup and launches
 * one {@link IsapiAlertStreamRunner} daemon thread per device.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceWorkerService {

    private final DeviceRepository deviceRepository;
    private final AcsIngestService acsIngestService;
    private final IsapiEventMapper eventMapper;

    private final Map<Long, Thread> activeThreads = new ConcurrentHashMap<>();

    @EventListener(ApplicationReadyEvent.class)
    public void startAll() {
        deviceRepository.findByEnabledTrue().forEach(this::startDevice);
        log.info("DeviceWorkerService: started {} alertStream thread(s)", activeThreads.size());
    }

    public void startDevice(DeviceEntity device) {
        if (activeThreads.containsKey(device.getId())) {
            log.warn("Device {} ({}) is already running, skipping", device.getId(), device.getIp());
            return;
        }

        var runner = new IsapiAlertStreamRunner(device, acsIngestService, eventMapper);
        Thread t = new Thread(runner, "alertStream-device-" + device.getId());
        t.setDaemon(true);
        t.start();
        activeThreads.put(device.getId(), t);
        log.info("Started alertStream for device {} ({})", device.getId(), device.getIp());
    }

    public void stopDevice(Long deviceId) {
        Thread t = activeThreads.remove(deviceId);
        if (t != null) {
            t.interrupt();
            log.info("Stopped alertStream for device {}", deviceId);
        }
    }
}
