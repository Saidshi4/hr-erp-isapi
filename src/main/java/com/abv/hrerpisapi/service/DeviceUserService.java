package com.abv.hrerpisapi.service;

import com.abv.hrerpisapi.controller.DeviceUserController.DeviceUserRequest;
import com.abv.hrerpisapi.controller.DeviceUserController.DeviceUserResponse;
import com.abv.hrerpisapi.dao.entity.DeviceEntity;
import com.abv.hrerpisapi.dao.entity.DeviceUserEntity;
import com.abv.hrerpisapi.dao.repository.DeviceRepository;
import com.abv.hrerpisapi.dao.repository.DeviceUserRepository;
import com.abv.hrerpisapi.device.client.IsapiClient;
import com.abv.hrerpisapi.device.client.IsapiClient.UserOperationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceUserService {

    private final DeviceRepository deviceRepository;
    private final DeviceUserRepository deviceUserRepository;
    private final IsapiClient isapiClient;

    public DeviceUserResponse addUserToDevice(Long deviceId, DeviceUserRequest request) {
        log.info("ActionLog.deviceUser.add.started deviceId={} username={}", deviceId, request.username());
        DeviceEntity device = requireDevice(deviceId);

        if (deviceUserRepository.existsByDeviceIdAndUsername(deviceId, request.username())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User already exists on this device");
        }

        DeviceUserEntity entity = new DeviceUserEntity();
        entity.setDeviceId(deviceId);
        entity.setUsername(request.username());
        entity.setPassword(request.password());
        entity.setUserType(request.userType() != null ? request.userType() : "normal");
        entity.setName(request.name());
        entity.setSyncedToDevice(false);
        DeviceUserEntity saved = deviceUserRepository.save(entity);

        try {
            UserOperationResult result = isapiClient.addUser(device, saved.getUsername(), saved.getPassword(), saved.getUserType());
            if (result.success()) {
                saved.setSyncedToDevice(true);
                saved.setLastSyncTime(OffsetDateTime.now());
                saved = deviceUserRepository.save(saved);
                log.info("ActionLog.deviceUser.add.ended deviceId={} userId={} username={} synced=true", deviceId, saved.getId(), saved.getUsername());
            } else {
                log.warn("ActionLog.deviceUser.add.syncFailed deviceId={} userId={} username={} statusCode={} response={}",
                        deviceId, saved.getId(), saved.getUsername(), result.statusCode(), result.responseSnippet());
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            log.warn("ActionLog.deviceUser.add.syncError deviceId={} userId={} username={} error={}",
                    deviceId, saved.getId(), saved.getUsername(), e.getMessage());
        }

        return toResponse(saved);
    }

    public DeviceUserResponse updateUserOnDevice(Long deviceId, Long userId, DeviceUserRequest request) {
        log.info("ActionLog.deviceUser.update.started deviceId={} userId={}", deviceId, userId);
        DeviceEntity device = requireDevice(deviceId);
        DeviceUserEntity entity = requireDeviceUser(deviceId, userId);

        entity.setPassword(request.password() != null ? request.password() : entity.getPassword());
        entity.setUserType(request.userType() != null ? request.userType() : entity.getUserType());
        entity.setName(request.name() != null ? request.name() : entity.getName());
        entity.setSyncedToDevice(false);
        DeviceUserEntity saved = deviceUserRepository.save(entity);

        try {
            UserOperationResult result = isapiClient.updateUser(device, saved.getUsername(), saved.getPassword(), saved.getUserType());
            if (result.success()) {
                saved.setSyncedToDevice(true);
                saved.setLastSyncTime(OffsetDateTime.now());
                saved = deviceUserRepository.save(saved);
                log.info("ActionLog.deviceUser.update.ended deviceId={} userId={} username={} synced=true", deviceId, saved.getId(), saved.getUsername());
            } else {
                log.warn("ActionLog.deviceUser.update.syncFailed deviceId={} userId={} username={} statusCode={} response={}",
                        deviceId, saved.getId(), saved.getUsername(), result.statusCode(), result.responseSnippet());
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            log.warn("ActionLog.deviceUser.update.syncError deviceId={} userId={} username={} error={}",
                    deviceId, saved.getId(), saved.getUsername(), e.getMessage());
        }

        return toResponse(saved);
    }

    public void deleteUserFromDevice(Long deviceId, Long userId) {
        log.info("ActionLog.deviceUser.delete.started deviceId={} userId={}", deviceId, userId);
        DeviceEntity device = requireDevice(deviceId);
        DeviceUserEntity entity = requireDeviceUser(deviceId, userId);

        try {
            UserOperationResult result = isapiClient.deleteUser(device, entity.getUsername());
            if (!result.success()) {
                log.warn("ActionLog.deviceUser.delete.syncFailed deviceId={} userId={} username={} statusCode={} response={}",
                        deviceId, userId, entity.getUsername(), result.statusCode(), result.responseSnippet());
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            log.warn("ActionLog.deviceUser.delete.syncError deviceId={} userId={} username={} error={}",
                    deviceId, userId, entity.getUsername(), e.getMessage());
        }

        deviceUserRepository.delete(entity);
        log.info("ActionLog.deviceUser.delete.ended deviceId={} userId={} username={}", deviceId, userId, entity.getUsername());
    }

    public List<DeviceUserResponse> listDeviceUsers(Long deviceId) {
        requireDevice(deviceId);
        return deviceUserRepository.findByDeviceId(deviceId).stream()
                .map(this::toResponse)
                .toList();
    }

    public DeviceUserResponse syncUserToDevice(Long deviceId, Long userId) {
        log.info("ActionLog.deviceUser.sync.started deviceId={} userId={}", deviceId, userId);
        DeviceEntity device = requireDevice(deviceId);
        DeviceUserEntity entity = requireDeviceUser(deviceId, userId);

        try {
            boolean exists = isapiClient.userExists(device, entity.getUsername());
            UserOperationResult result = exists
                    ? isapiClient.updateUser(device, entity.getUsername(), entity.getPassword(), entity.getUserType())
                    : isapiClient.addUser(device, entity.getUsername(), entity.getPassword(), entity.getUserType());

            if (result.success()) {
                entity.setSyncedToDevice(true);
                entity.setLastSyncTime(OffsetDateTime.now());
                entity = deviceUserRepository.save(entity);
                log.info("ActionLog.deviceUser.sync.ended deviceId={} userId={} username={} synced=true", deviceId, entity.getId(), entity.getUsername());
            } else {
                log.warn("ActionLog.deviceUser.sync.failed deviceId={} userId={} username={} statusCode={} response={}",
                        deviceId, entity.getId(), entity.getUsername(), result.statusCode(), result.responseSnippet());
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Failed to sync user to device: " + result.responseSnippet());
            }
        } catch (ResponseStatusException e) {
            throw e;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            log.warn("ActionLog.deviceUser.sync.error deviceId={} userId={} username={} error={}",
                    deviceId, entity.getId(), entity.getUsername(), e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to sync user to device: " + e.getMessage());
        }

        return toResponse(entity);
    }

    private DeviceEntity requireDevice(Long deviceId) {
        return deviceRepository.findById(deviceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Device not found"));
    }

    private DeviceUserEntity requireDeviceUser(Long deviceId, Long userId) {
        return deviceUserRepository.findByDeviceIdAndId(deviceId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Device user not found"));
    }

    private DeviceUserResponse toResponse(DeviceUserEntity entity) {
        return new DeviceUserResponse(
                entity.getId(),
                entity.getDeviceId(),
                entity.getUsername(),
                entity.getUserType(),
                entity.getName(),
                entity.isSyncedToDevice(),
                entity.getLastSyncTime());
    }
}
