package com.abv.hrerpisapi.service;

import com.abv.hrerpisapi.dao.entity.DeviceEntity;
import com.abv.hrerpisapi.dao.repository.DeviceRepository;
import com.abv.hrerpisapi.device.IsapiAlertStreamRunner;
import com.abv.hrerpisapi.device.mapper.IsapiEventMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class DeviceWorkerServiceTest {
    @Mock
    private DeviceRepository deviceRepository;
    @Mock
    private AcsIngestService acsIngestService;
    @Mock
    private IsapiEventMapper eventMapper;

    @Test
    void stopDeviceShouldInterruptBeforeStoppingRunnerAndWaitForTermination() throws Exception {
        TestableDeviceWorkerService service = new TestableDeviceWorkerService(deviceRepository, acsIngestService, eventMapper);
        IsapiAlertStreamRunner runner = new IsapiAlertStreamRunner(device(7L), acsIngestService, eventMapper, 3, 300);
        Thread thread = new Thread(() -> {
        }, "alertStream-device-7");

        activeThreads(service).put(7L, thread);
        activeRunners(service).put(7L, runner);

        service.stopDevice(7L);

        assertEquals(List.of("runnerStop", "interrupt", "waitAsync"), service.calls);
    }

    @SuppressWarnings("unchecked")
    private static Map<Long, Thread> activeThreads(DeviceWorkerService service) throws Exception {
        Field field = DeviceWorkerService.class.getDeclaredField("activeThreads");
        field.setAccessible(true);
        return (Map<Long, Thread>) field.get(service);
    }

    @SuppressWarnings("unchecked")
    private static Map<Long, IsapiAlertStreamRunner> activeRunners(DeviceWorkerService service) throws Exception {
        Field field = DeviceWorkerService.class.getDeclaredField("activeRunners");
        field.setAccessible(true);
        return (Map<Long, IsapiAlertStreamRunner>) field.get(service);
    }

    private static DeviceEntity device(Long id) {
        DeviceEntity d = new DeviceEntity();
        d.setId(id);
        d.setIp("10.0.0.1");
        d.setUsername("admin");
        d.setPassword("secret");
        return d;
    }

    private static final class TestableDeviceWorkerService extends DeviceWorkerService {
        private final List<String> calls = new ArrayList<>();

        private TestableDeviceWorkerService(DeviceRepository deviceRepository,
                                            AcsIngestService acsIngestService,
                                            IsapiEventMapper eventMapper) {
            super(deviceRepository, acsIngestService, eventMapper);
        }

        @Override
        protected void interruptThread(Thread thread) {
            calls.add("interrupt");
        }

        @Override
        protected void stopRunner(IsapiAlertStreamRunner runner) {
            calls.add("runnerStop");
        }

        @Override
        protected void waitForThreadStopAsync(Thread thread) {
            calls.add("waitAsync");
        }

        @Override
        protected void waitForThreadStop(Thread thread) {
            calls.add("wait");
        }
    }
}
