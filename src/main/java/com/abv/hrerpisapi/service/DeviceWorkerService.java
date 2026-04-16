package com.abv.hrerpisapi.service;

import com.abv.hrerpisapi.dao.entity.DeviceEntity;
import com.abv.hrerpisapi.dao.repository.DeviceRepository;
import com.abv.hrerpisapi.device.IsapiAlertStreamRunner;
import com.abv.hrerpisapi.device.mapper.IsapiEventMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
    private static final long THREAD_STOP_WAIT_MILLIS = 3000L;

    private final DeviceRepository deviceRepository;
    private final AcsIngestService acsIngestService;
    private final IsapiEventMapper eventMapper;

    @Value("${isapi.alertStream.disconnectBackoffBaseSeconds:3}")
    private int disconnectBackoffBaseSeconds;

    @Value("${isapi.alertStream.deployExceedMaxBackoffSeconds:300}")
    private int deployExceedMaxBackoffSeconds;

    private final Map<Long, Thread> activeThreads = new ConcurrentHashMap<>();
    private final Map<Long, IsapiAlertStreamRunner> activeRunners = new ConcurrentHashMap<>();

    @EventListener(ApplicationReadyEvent.class)
    public void startAll() {
        deviceRepository.findByEnabledTrue().forEach(this::startDevice);
        log.info("ActionLog.device.alertStream.startAll.ended count={}", activeThreads.size());
    }

    public void startDevice(DeviceEntity device) {
        Thread existing = activeThreads.get(device.getId());
        if (existing != null) {
            if (existing.isAlive()) {
                log.warn("ActionLog.device.alertStream.start.skipped deviceId={} ip={} reason=alreadyRunning",
                        device.getId(), device.getIp());
                return;
            }
            activeThreads.remove(device.getId(), existing);
            activeRunners.remove(device.getId());
        }

        var runner = new IsapiAlertStreamRunner(
                device,
                acsIngestService,
                eventMapper,
                disconnectBackoffBaseSeconds,
                deployExceedMaxBackoffSeconds);
        Thread t = new Thread(runner, "alertStream-device-" + device.getId());
        t.setDaemon(true);
        t.start();
        activeThreads.put(device.getId(), t);
        activeRunners.put(device.getId(), runner);
        log.info("ActionLog.device.alertStream.start.ended deviceId={} ip={} disconnectBackoffBaseSeconds={} deployExceedMaxBackoffSeconds={}",
                device.getId(), device.getIp(), disconnectBackoffBaseSeconds, deployExceedMaxBackoffSeconds);
    }

    public void stopDevice(Long deviceId) {
        Thread t = activeThreads.remove(deviceId);
        IsapiAlertStreamRunner runner = activeRunners.remove(deviceId);
        if (t != null) {
            interruptThread(t);
            if (runner != null) {
                stopRunner(runner);
            }
            waitForThreadStop(t);
            log.info("ActionLog.device.alertStream.stop.ended deviceId={}", deviceId);
        }
    }

    protected void interruptThread(Thread thread) {
        thread.interrupt();
    }

    protected void stopRunner(IsapiAlertStreamRunner runner) {
        runner.stop();
    }

    protected void waitForThreadStop(Thread thread) {
        try {
            thread.join(THREAD_STOP_WAIT_MILLIS);
            if (thread.isAlive()) {
                log.warn("ActionLog.device.alertStream.stop.wait.timeout threadName={} waitMillis={}",
                        thread.getName(), THREAD_STOP_WAIT_MILLIS);
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("ActionLog.device.alertStream.stop.wait.interrupted threadName={}", thread.getName());
        }
    }

    public boolean isRunning(Long deviceId) {
        Thread t = activeThreads.get(deviceId);
        if (t == null) {
            return false;
        }
        if (t.isAlive()) {
            return true;
        }
        activeThreads.remove(deviceId, t);
        activeRunners.remove(deviceId);
        return false;
    }
}
