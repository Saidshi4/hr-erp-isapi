package com.abv.hrerpisapi.controller;

import com.abv.hrerpisapi.dao.entity.DeviceEntity;
import com.abv.hrerpisapi.dao.repository.DeviceRepository;
import com.abv.hrerpisapi.device.client.IsapiClient;
import com.abv.hrerpisapi.service.DeviceWorkerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@Slf4j
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
        log.info("ActionLog.device.create.started ip={} enabled={}", trimToNull(request.ip()), request.enabled());
        DeviceEntity device = new DeviceEntity();
        applyUpsert(device, request, true);
        DeviceEntity saved = deviceRepository.save(device);
        if (saved.isEnabled()) {
            deviceWorkerService.startDevice(saved);
        }
        log.info("ActionLog.device.create.ended deviceId={} ip={} enabled={}", saved.getId(), saved.getIp(), saved.isEnabled());
        return toResponse(saved);
    }

    @PutMapping("/{id}")
    public DeviceResponse update(@PathVariable Long id, @RequestBody DeviceUpsertRequest request) {
        log.info("ActionLog.device.update.started deviceId={} ip={} enabled={}", id, trimToNull(request.ip()), request.enabled());
        DeviceEntity device = requireDevice(id);
        applyUpsert(device, request, false);
        DeviceEntity saved = deviceRepository.save(device);
        if (saved.isEnabled()) {
            deviceWorkerService.startDevice(saved);
        } else {
            deviceWorkerService.stopDevice(saved.getId());
        }
        log.info("ActionLog.device.update.ended deviceId={} ip={} enabled={}", saved.getId(), saved.getIp(), saved.isEnabled());
        return toResponse(saved);
    }

    @PatchMapping("/{id}/enabled")
    public DeviceResponse updateEnabled(@PathVariable Long id, @RequestBody DeviceEnabledRequest request) {
        log.info("ActionLog.device.enabled.update.started deviceId={} enabled={}", id, request.enabled());
        DeviceEntity device = requireDevice(id);
        device.setEnabled(request.enabled());
        DeviceEntity saved = deviceRepository.save(device);
        if (saved.isEnabled()) {
            deviceWorkerService.startDevice(saved);
        } else {
            deviceWorkerService.stopDevice(saved.getId());
        }
        log.info("ActionLog.device.enabled.update.ended deviceId={} enabled={}", saved.getId(), saved.isEnabled());
        return toResponse(saved);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        log.info("ActionLog.device.delete.started deviceId={}", id);
        DeviceEntity device = requireDevice(id);
        deviceWorkerService.stopDevice(device.getId());
        deviceRepository.delete(device);
        log.info("ActionLog.device.delete.ended deviceId={}", id);
    }

    @PostMapping("/{id}/start")
    public DeviceRuntimeResponse start(@PathVariable Long id) {
        log.info("ActionLog.device.alertStream.start.started deviceId={}", id);
        DeviceEntity device = requireDevice(id);
        deviceWorkerService.startDevice(device);
        DeviceRuntimeResponse response = new DeviceRuntimeResponse(
                device.getId(),
                device.isEnabled(),
                deviceWorkerService.isRunning(device.getId()));
        log.info("ActionLog.device.alertStream.start.ended deviceId={} running={}", response.id(), response.running());
        return response;
    }

    @PostMapping("/{id}/stop")
    public DeviceRuntimeResponse stop(@PathVariable Long id) {
        log.info("ActionLog.device.alertStream.stop.started deviceId={}", id);
        DeviceEntity device = requireDevice(id);
        deviceWorkerService.stopDevice(device.getId());
        DeviceRuntimeResponse response = new DeviceRuntimeResponse(
                device.getId(),
                device.isEnabled(),
                deviceWorkerService.isRunning(device.getId()));
        log.info("ActionLog.device.alertStream.stop.ended deviceId={} running={}", response.id(), response.running());
        return response;
    }

    @GetMapping("/{id}/status")
    public DeviceStatusResponse status(@PathVariable Long id) {
        DeviceEntity device = requireDevice(id);
        log.info("ActionLog.device.status.check.started deviceId={} ip={}", device.getId(), device.getIp());
        IsapiClient.DeviceStatusCheckResult status = isapiClient.checkDeviceStatus(device);
        DeviceStatusResponse response = new DeviceStatusResponse(
                device.getId(),
                status.online(),
                status.statusCode(),
                status.responseSnippet());
        log.info("ActionLog.device.status.check.ended deviceId={} ip={} online={} statusCode={}",
                device.getId(), device.getIp(), response.online(), response.statusCode());
        return response;
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
