package com.abv.hrerpisapi.controller;

import com.abv.hrerpisapi.service.DeviceUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@Slf4j
@RequestMapping("/api/devices/{deviceId}/users")
@RequiredArgsConstructor
public class DeviceUserController {

    private final DeviceUserService deviceUserService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DeviceUserResponse addUser(@PathVariable Long deviceId,
                                      @RequestBody DeviceUserRequest request) {
        validateRequest(request, true);
        return deviceUserService.addUserToDevice(deviceId, request);
    }

    @PutMapping("/{userId}")
    public DeviceUserResponse updateUser(@PathVariable Long deviceId,
                                         @PathVariable Long userId,
                                         @RequestBody DeviceUserRequest request) {
        return deviceUserService.updateUserOnDevice(deviceId, userId, request);
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(@PathVariable Long deviceId,
                           @PathVariable Long userId) {
        deviceUserService.deleteUserFromDevice(deviceId, userId);
    }

    @GetMapping
    public DeviceUserListResponse listUsers(@PathVariable Long deviceId) {
        List<DeviceUserResponse> users = deviceUserService.listDeviceUsers(deviceId);
        return new DeviceUserListResponse(users);
    }

    @PostMapping("/{userId}/sync")
    public DeviceUserResponse syncUser(@PathVariable Long deviceId,
                                       @PathVariable Long userId) {
        return deviceUserService.syncUserToDevice(deviceId, userId);
    }

    private void validateRequest(DeviceUserRequest request, boolean requirePassword) {
        if (request.username() == null || request.username().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "username is required");
        }
        if (requirePassword && (request.password() == null || request.password().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "password is required");
        }
    }

    public record DeviceUserRequest(
            String username,
            String password,
            String userType,
            String name
    ) {
    }

    public record DeviceUserResponse(
            Long id,
            Long deviceId,
            String username,
            String userType,
            String name,
            boolean syncedToDevice,
            OffsetDateTime lastSyncTime
    ) {
    }

    public record DeviceUserListResponse(
            List<DeviceUserResponse> users
    ) {
    }
}
