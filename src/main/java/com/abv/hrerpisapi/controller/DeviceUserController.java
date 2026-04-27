package com.abv.hrerpisapi.controller;

import com.abv.hrerpisapi.service.DeviceUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@Slf4j
@RequestMapping("/api/devices/{deviceId}/users")
@RequiredArgsConstructor
public class DeviceUserController {

    private final DeviceUserService deviceUserService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DeviceUserResponse createUser(@PathVariable Long deviceId,
                                         @RequestBody DeviceUserCreateRequest request) {
        validateCreateRequest(request);
        return deviceUserService.createDeviceUser(deviceId, request);
    }

    @GetMapping
    public List<DeviceUserResponse> listUsers(@PathVariable Long deviceId) {
        return deviceUserService.listDeviceUsers(deviceId);
    }

    @GetMapping("/{userId}")
    public DeviceUserResponse getUser(@PathVariable Long deviceId,
                                      @PathVariable Long userId) {
        return deviceUserService.getDeviceUser(deviceId, userId);
    }

    @PutMapping("/{userId}")
    public DeviceUserResponse updateUser(@PathVariable Long deviceId,
                                         @PathVariable Long userId,
                                         @RequestBody DeviceUserUpdateRequest request) {
        return deviceUserService.updateDeviceUser(deviceId, userId, request);
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(@PathVariable Long deviceId,
                           @PathVariable Long userId) {
        deviceUserService.deleteDeviceUser(deviceId, userId);
    }

    @PostMapping("/{userId}/sync")
    public DeviceUserSyncResponse syncUser(@PathVariable Long deviceId,
                                           @PathVariable Long userId) {
        return deviceUserService.syncUserToDevice(deviceId, userId);
    }

    @PostMapping("/{userId}/face")
    public DeviceUserResponse uploadFace(@PathVariable Long deviceId,
                                         @PathVariable Long userId,
                                         @RequestParam("file") MultipartFile file) {
        return deviceUserService.uploadFaceData(deviceId, userId, file);
    }

    private void validateCreateRequest(DeviceUserCreateRequest request) {
        if (request.employeeNo() == null || request.employeeNo().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "employeeNo is required");
        }
        if (request.name() == null || request.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
        }
    }

    public record DeviceUserCreateRequest(
            String employeeNo,
            String name,
            String userType,
            String gender,
            String beginTime,
            String endTime,
            String faceDataUrl
    ) {
    }

    public record DeviceUserUpdateRequest(
            String name,
            String userType,
            String gender,
            String beginTime,
            String endTime,
            String faceDataUrl
    ) {
    }

    public record DeviceUserResponse(
            Long id,
            Long deviceId,
            String employeeNo,
            String name,
            String userType,
            String gender,
            LocalDateTime beginTime,
            LocalDateTime endTime,
            String faceDataUrl,
            boolean syncedToDevice,
            LocalDateTime lastSyncTime,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record DeviceUserSyncResponse(
            Long id,
            Long deviceId,
            String employeeNo,
            boolean syncedToDevice,
            LocalDateTime lastSyncTime,
            String status,
            String message
    ) {
    }
}
