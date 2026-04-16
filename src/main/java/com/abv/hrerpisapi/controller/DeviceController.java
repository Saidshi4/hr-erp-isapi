package com.abv.hrerpisapi.controller;

import com.abv.hrerpisapi.dao.entity.DeviceEntity;
import com.abv.hrerpisapi.dao.repository.DeviceRepository;
import com.abv.hrerpisapi.device.client.IsapiClient;
import com.abv.hrerpisapi.service.DeviceWorkerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceRepository deviceRepository;
    private final DeviceWorkerService deviceWorkerService;
    private final IsapiClient isapiClient;

    @GetMapping
    public List<DeviceResponse> list(@RequestParam(required = false) Boolean enabled) {
        List<DeviceEntity> devices = enabled == null
                ? deviceRepository.findAll()
                : deviceRepository.findByEnabled(enabled);
        return devices.stream().map(this::toResponse).toList();
    }

    @GetMapping("/{id}")
    public DeviceResponse get(@PathVariable Long id) {
        return toResponse(requireDevice(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DeviceResponse create(@RequestBody DeviceUpsertRequest request) {
        DeviceEntity device = new DeviceEntity();
        applyUpsert(device, request, true);
        DeviceEntity saved = deviceRepository.save(device);
        if (saved.isEnabled()) {
            deviceWorkerService.startDevice(saved);
        }
        return toResponse(saved);
    }

    @PutMapping("/{id}")
    public DeviceResponse update(@PathVariable Long id, @RequestBody DeviceUpsertRequest request) {
        DeviceEntity device = requireDevice(id);
        applyUpsert(device, request, false);
        DeviceEntity saved = deviceRepository.save(device);
        if (saved.isEnabled()) {
            deviceWorkerService.startDevice(saved);
        } else {
            deviceWorkerService.stopDevice(saved.getId());
        }
        return toResponse(saved);
    }

    @PatchMapping("/{id}/enabled")
    public DeviceResponse updateEnabled(@PathVariable Long id, @RequestBody DeviceEnabledRequest request) {
        DeviceEntity device = requireDevice(id);
        device.setEnabled(request.enabled());
        DeviceEntity saved = deviceRepository.save(device);
        if (saved.isEnabled()) {
            deviceWorkerService.startDevice(saved);
        } else {
            deviceWorkerService.stopDevice(saved.getId());
        }
        return toResponse(saved);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        DeviceEntity device = requireDevice(id);
        deviceWorkerService.stopDevice(device.getId());
        deviceRepository.delete(device);
    }

    @PostMapping("/{id}/start")
    public DeviceRuntimeResponse start(@PathVariable Long id) {
        DeviceEntity device = requireDevice(id);
        deviceWorkerService.startDevice(device);
        return new DeviceRuntimeResponse(
                device.getId(),
                device.isEnabled(),
                deviceWorkerService.isRunning(device.getId()));
    }

    @PostMapping("/{id}/stop")
    public DeviceRuntimeResponse stop(@PathVariable Long id) {
        DeviceEntity device = requireDevice(id);
        deviceWorkerService.stopDevice(device.getId());
        return new DeviceRuntimeResponse(
                device.getId(),
                device.isEnabled(),
                deviceWorkerService.isRunning(device.getId()));
    }

    @GetMapping("/{id}/status")
    public DeviceStatusResponse status(@PathVariable Long id) {
        DeviceEntity device = requireDevice(id);
        IsapiClient.DeviceStatusCheckResult status = isapiClient.checkDeviceStatus(device);
        return new DeviceStatusResponse(
                device.getId(),
                status.online(),
                status.statusCode(),
                status.responseSnippet());
    }

    private DeviceEntity requireDevice(Long id) {
        return deviceRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Device not found"));
    }

    private void applyUpsert(DeviceEntity device, DeviceUpsertRequest request, boolean create) {
        device.setIp(requireNotBlank(request.ip(), "ip is required"));
        device.setUsername(requireNotBlank(request.username(), "username is required"));
        device.setName(trimToNull(request.name()));
        if (request.enabled() != null) {
            device.setEnabled(request.enabled());
        }

        String password = trimToNull(request.password());
        if (create && password == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "password is required");
        }
        if (password != null) {
            device.setPassword(password);
        }
    }

    private String requireNotBlank(String value, String message) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private DeviceResponse toResponse(DeviceEntity device) {
        return new DeviceResponse(
                device.getId(),
                device.getIp(),
                device.getUsername(),
                device.getName(),
                device.isEnabled(),
                deviceWorkerService.isRunning(device.getId()));
    }

    public record DeviceResponse(
            Long id,
            String ip,
            String username,
            String name,
            boolean enabled,
            boolean running
    ) {
    }

    public record DeviceUpsertRequest(
            String ip,
            String username,
            String password,
            String name,
            Boolean enabled
    ) {
    }

    public record DeviceEnabledRequest(boolean enabled) {
    }

    public record DeviceRuntimeResponse(
            Long id,
            boolean enabled,
            boolean running
    ) {
    }

    public record DeviceStatusResponse(
            Long id,
            boolean online,
            int statusCode,
            String responseSnippet
    ) {
    }
}
