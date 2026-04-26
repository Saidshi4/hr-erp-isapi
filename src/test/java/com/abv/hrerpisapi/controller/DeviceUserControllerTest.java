package com.abv.hrerpisapi.controller;

import com.abv.hrerpisapi.service.DeviceUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeviceUserControllerTest {

    @Mock
    private DeviceUserService deviceUserService;

    private DeviceUserController controller;

    @BeforeEach
    void setUp() {
        controller = new DeviceUserController(deviceUserService);
    }

    @Test
    void addUserShouldReturnCreatedResponse() {
        DeviceUserController.DeviceUserRequest req =
                new DeviceUserController.DeviceUserRequest("johndoe", "secret123", "normal", "John Doe");
        DeviceUserController.DeviceUserResponse expected = userResponse(1L, 10L, "johndoe", true);
        when(deviceUserService.addUserToDevice(eq(10L), any())).thenReturn(expected);

        DeviceUserController.DeviceUserResponse response = controller.addUser(10L, req);

        assertEquals(expected, response);
        verify(deviceUserService).addUserToDevice(eq(10L), any());
    }

    @Test
    void addUserShouldRejectMissingUsername() {
        DeviceUserController.DeviceUserRequest req =
                new DeviceUserController.DeviceUserRequest("  ", "secret", "normal", null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.addUser(10L, req));
        assertEquals("400 BAD_REQUEST \"username is required\"", ex.getMessage());
        verifyNoInteractions(deviceUserService);
    }

    @Test
    void addUserShouldRejectMissingPassword() {
        DeviceUserController.DeviceUserRequest req =
                new DeviceUserController.DeviceUserRequest("johndoe", null, "normal", null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.addUser(10L, req));
        assertEquals("400 BAD_REQUEST \"password is required\"", ex.getMessage());
        verifyNoInteractions(deviceUserService);
    }

    @Test
    void updateUserShouldDelegateToService() {
        DeviceUserController.DeviceUserRequest req =
                new DeviceUserController.DeviceUserRequest("johndoe", "newpass", "admin", "John Updated");
        DeviceUserController.DeviceUserResponse expected = userResponse(1L, 10L, "johndoe", true);
        when(deviceUserService.updateUserOnDevice(eq(10L), eq(1L), any())).thenReturn(expected);

        DeviceUserController.DeviceUserResponse response = controller.updateUser(10L, 1L, req);

        assertEquals(expected, response);
        verify(deviceUserService).updateUserOnDevice(eq(10L), eq(1L), any());
    }

    @Test
    void deleteUserShouldDelegateToService() {
        doNothing().when(deviceUserService).deleteUserFromDevice(10L, 1L);

        controller.deleteUser(10L, 1L);

        verify(deviceUserService).deleteUserFromDevice(10L, 1L);
    }

    @Test
    void listUsersShouldReturnListResponse() {
        List<DeviceUserController.DeviceUserResponse> users = List.of(
                userResponse(1L, 10L, "alice", true),
                userResponse(2L, 10L, "bob", false)
        );
        when(deviceUserService.listDeviceUsers(10L)).thenReturn(users);

        DeviceUserController.DeviceUserListResponse response = controller.listUsers(10L);

        assertEquals(2, response.users().size());
        assertEquals("alice", response.users().get(0).username());
        assertEquals("bob", response.users().get(1).username());
    }

    @Test
    void syncUserShouldDelegateToService() {
        DeviceUserController.DeviceUserResponse expected = userResponse(1L, 10L, "johndoe", true);
        when(deviceUserService.syncUserToDevice(10L, 1L)).thenReturn(expected);

        DeviceUserController.DeviceUserResponse response = controller.syncUser(10L, 1L);

        assertEquals(expected, response);
        verify(deviceUserService).syncUserToDevice(10L, 1L);
    }

    private DeviceUserController.DeviceUserResponse userResponse(Long id, Long deviceId, String username, boolean synced) {
        return new DeviceUserController.DeviceUserResponse(
                id, deviceId, username, "normal", null, synced, synced ? OffsetDateTime.now() : null);
    }
}
