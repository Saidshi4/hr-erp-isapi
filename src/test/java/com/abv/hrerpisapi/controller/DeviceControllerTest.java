package com.abv.hrerpisapi.controller;

import com.abv.hrerpisapi.dao.entity.DeviceEntity;
import com.abv.hrerpisapi.dao.repository.DeviceRepository;
import com.abv.hrerpisapi.device.client.IsapiClient;
import com.abv.hrerpisapi.service.DeviceWorkerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeviceControllerTest {

    @Mock
    private DeviceRepository deviceRepository;
    @Mock
    private DeviceWorkerService deviceWorkerService;
    @Mock
    private IsapiClient isapiClient;

    private DeviceController controller;

    @BeforeEach
    void setUp() {
        controller = new DeviceController(deviceRepository, deviceWorkerService, isapiClient);
    }

    @Test
    void createShouldStartRunnerWhenEnabledAndHidePasswordInResponse() {
        DeviceEntity saved = device(1L, true);
        saved.setIp("10.0.0.8");
        saved.setUsername("admin");
        saved.setName("Front Door");
        when(deviceRepository.save(any(DeviceEntity.class))).thenReturn(saved);
        when(deviceWorkerService.isRunning(1L)).thenReturn(true);

        DeviceController.DeviceResponse response = controller.create(
                new DeviceController.DeviceUpsertRequest(" 10.0.0.8 ", " admin ", " secret ", " Front Door ", true));

        assertEquals(1L, response.id());
        assertEquals("10.0.0.8", response.ip());
        assertEquals("admin", response.username());
        assertEquals("Front Door", response.name());
        assertTrue(response.enabled());
        assertTrue(response.running());
        verify(deviceWorkerService).startDevice(saved);
    }

    @Test
    void updateEnabledShouldStopRunnerWhenDisabled() {
        DeviceEntity existing = device(5L, true);
        when(deviceRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(deviceRepository.save(existing)).thenReturn(existing);

        controller.updateEnabled(5L, new DeviceController.DeviceEnabledRequest(false));

        assertFalse(existing.isEnabled());
        verify(deviceWorkerService).stopDevice(5L);
    }

    @Test
    void startShouldOnlyControlRuntimeAndNotPersistEnabled() {
        DeviceEntity existing = device(9L, false);
        when(deviceRepository.findById(9L)).thenReturn(Optional.of(existing));
        when(deviceWorkerService.isRunning(9L)).thenReturn(true);

        DeviceController.DeviceRuntimeResponse response = controller.start(9L);

        assertFalse(response.enabled());
        assertTrue(response.running());
        verify(deviceWorkerService).startDevice(existing);
        verify(deviceRepository, never()).save(any(DeviceEntity.class));
    }

    @Test
    void statusShouldReturnIsapiStatusPayload() {
        DeviceEntity existing = device(2L, true);
        when(deviceRepository.findById(2L)).thenReturn(Optional.of(existing));
        when(isapiClient.checkDeviceStatus(existing))
                .thenReturn(new IsapiClient.DeviceStatusCheckResult(true, 200, "{\"DeviceInfo\":{}}"));

        DeviceController.DeviceStatusResponse response = controller.status(2L);

        assertTrue(response.online());
        assertEquals(200, response.statusCode());
        assertEquals("{\"DeviceInfo\":{}}", response.responseSnippet());
    }

    @Test
    void createShouldRequirePassword() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.create(new DeviceController.DeviceUpsertRequest("10.0.0.1", "admin", "   ", null, true)));
        assertEquals("400 BAD_REQUEST \"password is required\"", ex.getMessage());
        verifyNoInteractions(deviceRepository);
    }

    private DeviceEntity device(Long id, boolean enabled) {
        DeviceEntity d = new DeviceEntity();
        d.setId(id);
        d.setIp("10.0.0.1");
        d.setUsername("admin");
        d.setPassword("secret");
        d.setName("Main");
        d.setEnabled(enabled);
        return d;
    }
}
