package com.abv.hrerpisapi.service;

import com.abv.hrerpisapi.controller.DeviceUserController.DeviceUserCreateRequest;
import com.abv.hrerpisapi.controller.DeviceUserController.DeviceUserResponse;
import com.abv.hrerpisapi.controller.DeviceUserController.DeviceUserSyncResponse;
import com.abv.hrerpisapi.controller.DeviceUserController.DeviceUserUpdateRequest;
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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceUserService {

    private final DeviceRepository deviceRepository;
    private final DeviceUserRepository deviceUserRepository;
    private final IsapiClient isapiClient;

    public DeviceUserResponse createDeviceUser(Long deviceId, DeviceUserCreateRequest request) {
        log.info("ActionLog.deviceUser.create.started deviceId={} employeeNo={}", deviceId, request.employeeNo());
        DeviceEntity device = requireDevice(deviceId);

        if (deviceUserRepository.existsByDeviceIdAndEmployeeNo(deviceId, request.employeeNo())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User already exists on this device");
        }

        DeviceUserEntity entity = new DeviceUserEntity();
        entity.setDeviceId(deviceId);
        entity.setEmployeeNo(request.employeeNo());
        entity.setName(request.name());
        entity.setUserType(request.userType() != null ? request.userType() : "normal");
        entity.setGender(request.gender());
        entity.setBeginTime(parseDateTime(request.beginTime()));
        entity.setEndTime(parseDateTime(request.endTime()));
        entity.setFaceDataUrl(request.faceDataUrl());
        entity.setSyncedToDevice(false);
        DeviceUserEntity saved = deviceUserRepository.save(entity);

        try {
            UserOperationResult result = isapiClient.addDeviceUser(
                    device, saved.getEmployeeNo(), saved.getName(), saved.getUserType(),
                    saved.getGender(), saved.getBeginTime(), saved.getEndTime());
            if (result.success()) {
                saved.setSyncedToDevice(true);
                saved.setLastSyncTime(LocalDateTime.now());
                saved = deviceUserRepository.save(saved);
                log.info("ActionLog.deviceUser.create.ended deviceId={} userId={} employeeNo={} synced=true",
                        deviceId, saved.getId(), saved.getEmployeeNo());
            } else {
                log.warn("ActionLog.deviceUser.create.syncFailed deviceId={} userId={} employeeNo={} statusCode={} response={}",
                        deviceId, saved.getId(), saved.getEmployeeNo(), result.statusCode(), result.responseSnippet());
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            log.warn("ActionLog.deviceUser.create.syncError deviceId={} userId={} employeeNo={} error={}",
                    deviceId, saved.getId(), saved.getEmployeeNo(), e.getMessage());
        }

        if (saved.isSyncedToDevice() && saved.getFaceDataUrl() != null && !saved.getFaceDataUrl().isBlank()) {
            uploadFaceByUrl(device, saved);
        }

        return toResponse(saved);
    }

    public DeviceUserResponse getDeviceUser(Long deviceId, Long userId) {
        requireDevice(deviceId);
        return toResponse(requireDeviceUser(deviceId, userId));
    }

    public DeviceUserResponse updateDeviceUser(Long deviceId, Long userId, DeviceUserUpdateRequest request) {
        log.info("ActionLog.deviceUser.update.started deviceId={} userId={}", deviceId, userId);
        DeviceEntity device = requireDevice(deviceId);
        DeviceUserEntity entity = requireDeviceUser(deviceId, userId);

        if (request.name() != null) entity.setName(request.name());
        if (request.userType() != null) entity.setUserType(request.userType());
        if (request.gender() != null) entity.setGender(request.gender());
        if (request.beginTime() != null) entity.setBeginTime(parseDateTime(request.beginTime()));
        if (request.endTime() != null) entity.setEndTime(parseDateTime(request.endTime()));
        if (request.faceDataUrl() != null) entity.setFaceDataUrl(request.faceDataUrl());
        entity.setSyncedToDevice(false);
        DeviceUserEntity saved = deviceUserRepository.save(entity);

        try {
            UserOperationResult result = isapiClient.updateDeviceUser(
                    device, saved.getEmployeeNo(), saved.getName(), saved.getUserType(),
                    saved.getGender(), saved.getBeginTime(), saved.getEndTime());
            if (result.success()) {
                saved.setSyncedToDevice(true);
                saved.setLastSyncTime(LocalDateTime.now());
                saved = deviceUserRepository.save(saved);
                log.info("ActionLog.deviceUser.update.ended deviceId={} userId={} employeeNo={} synced=true",
                        deviceId, saved.getId(), saved.getEmployeeNo());
            } else {
                log.warn("ActionLog.deviceUser.update.syncFailed deviceId={} userId={} employeeNo={} statusCode={} response={}",
                        deviceId, saved.getId(), saved.getEmployeeNo(), result.statusCode(), result.responseSnippet());
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            log.warn("ActionLog.deviceUser.update.syncError deviceId={} userId={} employeeNo={} error={}",
                    deviceId, saved.getId(), saved.getEmployeeNo(), e.getMessage());
        }

        if (saved.isSyncedToDevice() && request.faceDataUrl() != null && !request.faceDataUrl().isBlank()) {
            uploadFaceByUrl(device, saved);
        }

        return toResponse(saved);
    }

    public void deleteDeviceUser(Long deviceId, Long userId) {
        log.info("ActionLog.deviceUser.delete.started deviceId={} userId={}", deviceId, userId);
        DeviceEntity device = requireDevice(deviceId);
        DeviceUserEntity entity = requireDeviceUser(deviceId, userId);

        try {
            UserOperationResult result = isapiClient.deleteDeviceUser(device, entity.getEmployeeNo());
            if (!result.success()) {
                log.warn("ActionLog.deviceUser.delete.syncFailed deviceId={} userId={} employeeNo={} statusCode={} response={}",
                        deviceId, userId, entity.getEmployeeNo(), result.statusCode(), result.responseSnippet());
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            log.warn("ActionLog.deviceUser.delete.syncError deviceId={} userId={} employeeNo={} error={}",
                    deviceId, userId, entity.getEmployeeNo(), e.getMessage());
        }

        deviceUserRepository.delete(entity);
        log.info("ActionLog.deviceUser.delete.ended deviceId={} userId={} employeeNo={}",
                deviceId, userId, entity.getEmployeeNo());
    }

    public List<DeviceUserResponse> listDeviceUsers(Long deviceId) {
        requireDevice(deviceId);
        return deviceUserRepository.findByDeviceId(deviceId).stream()
                .map(this::toResponse)
                .toList();
    }

    public DeviceUserSyncResponse syncUserToDevice(Long deviceId, Long userId) {
        log.info("ActionLog.deviceUser.sync.started deviceId={} userId={}", deviceId, userId);
        DeviceEntity device = requireDevice(deviceId);
        DeviceUserEntity entity = requireDeviceUser(deviceId, userId);

        try {
            boolean exists = isapiClient.deviceUserExists(device, entity.getEmployeeNo());
            UserOperationResult result = exists
                    ? isapiClient.updateDeviceUser(device, entity.getEmployeeNo(), entity.getName(),
                            entity.getUserType(), entity.getGender(), entity.getBeginTime(), entity.getEndTime())
                    : isapiClient.addDeviceUser(device, entity.getEmployeeNo(), entity.getName(),
                            entity.getUserType(), entity.getGender(), entity.getBeginTime(), entity.getEndTime());

            if (result.success()) {
                entity.setSyncedToDevice(true);
                entity.setLastSyncTime(LocalDateTime.now());
                entity = deviceUserRepository.save(entity);
                log.info("ActionLog.deviceUser.sync.ended deviceId={} userId={} employeeNo={} synced=true",
                        deviceId, entity.getId(), entity.getEmployeeNo());
                return toSyncResponse(entity, "SUCCESS", "User synced successfully");
            } else {
                log.warn("ActionLog.deviceUser.sync.failed deviceId={} userId={} employeeNo={} statusCode={} response={}",
                        deviceId, entity.getId(), entity.getEmployeeNo(), result.statusCode(), result.responseSnippet());
                return toSyncResponse(entity, "FAILED", "Sync failed: " + result.responseSnippet());
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            log.warn("ActionLog.deviceUser.sync.error deviceId={} userId={} employeeNo={} error={}",
                    deviceId, entity.getId(), entity.getEmployeeNo(), e.getMessage());
            return toSyncResponse(entity, "FAILED", "Sync error: " + e.getMessage());
        }
    }

    public DeviceUserResponse uploadFaceData(Long deviceId, Long userId, MultipartFile file) {
        log.info("ActionLog.deviceUser.face.upload.started deviceId={} userId={}", deviceId, userId);
        DeviceEntity device = requireDevice(deviceId);
        DeviceUserEntity entity = requireDeviceUser(deviceId, userId);

        try {
            byte[] imageBytes = file.getBytes();
            UserOperationResult result = isapiClient.uploadFaceBinary(device, entity.getEmployeeNo(), imageBytes);
            if (result.success()) {
                log.info("ActionLog.deviceUser.face.upload.ended deviceId={} userId={} employeeNo={} synced=true",
                        deviceId, entity.getId(), entity.getEmployeeNo());
            } else {
                log.warn("ActionLog.deviceUser.face.upload.failed deviceId={} userId={} employeeNo={} statusCode={} response={}",
                        deviceId, entity.getId(), entity.getEmployeeNo(), result.statusCode(), result.responseSnippet());
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Face upload failed: " + result.responseSnippet());
            }
        } catch (ResponseStatusException e) {
            throw e;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            log.warn("ActionLog.deviceUser.face.upload.error deviceId={} userId={} employeeNo={} error={}",
                    deviceId, entity.getId(), entity.getEmployeeNo(), e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Face upload error: " + e.getMessage());
        }

        return toResponse(entity);
    }

    private void uploadFaceByUrl(DeviceEntity device, DeviceUserEntity entity) {
        try {
            UserOperationResult faceResult = isapiClient.uploadFaceByUrl(
                    device, entity.getEmployeeNo(), entity.getFaceDataUrl());
            if (faceResult.success()) {
                log.info("ActionLog.deviceUser.face.url.upload.ended deviceId={} userId={} employeeNo={}",
                        entity.getDeviceId(), entity.getId(), entity.getEmployeeNo());
            } else {
                log.warn("ActionLog.deviceUser.face.url.upload.failed deviceId={} userId={} employeeNo={} statusCode={} response={}",
                        entity.getDeviceId(), entity.getId(), entity.getEmployeeNo(),
                        faceResult.statusCode(), faceResult.responseSnippet());
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            log.warn("ActionLog.deviceUser.face.url.upload.error deviceId={} userId={} employeeNo={} error={}",
                    entity.getDeviceId(), entity.getId(), entity.getEmployeeNo(), e.getMessage());
        }
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid datetime format, expected ISO 8601 (e.g. 2026-01-01T00:00:00): " + value);
        }
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
                entity.getEmployeeNo(),
                entity.getName(),
                entity.getUserType(),
                entity.getGender(),
                entity.getBeginTime(),
                entity.getEndTime(),
                entity.getFaceDataUrl(),
                entity.isSyncedToDevice(),
                entity.getLastSyncTime(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private DeviceUserSyncResponse toSyncResponse(DeviceUserEntity entity, String status, String message) {
        return new DeviceUserSyncResponse(
                entity.getId(),
                entity.getDeviceId(),
                entity.getEmployeeNo(),
                entity.isSyncedToDevice(),
                entity.getLastSyncTime(),
                status,
                message);
    }
}
