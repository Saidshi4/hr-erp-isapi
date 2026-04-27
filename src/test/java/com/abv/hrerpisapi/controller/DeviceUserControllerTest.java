package com.abv.hrerpisapi.controller;

import com.abv.hrerpisapi.service.DeviceUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
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
    void createUserShouldReturnCreatedResponse() {
        DeviceUserController.DeviceUserCreateRequest req =
                new DeviceUserController.DeviceUserCreateRequest(
                        "1001", "John Doe", "normal", "male",
                        "2026-01-01T00:00:00", "2036-01-01T23:59:59", null);
        DeviceUserController.DeviceUserResponse expected = userResponse(1L, 10L, "1001", "John Doe", true);
        when(deviceUserService.createDeviceUser(eq(10L), any())).thenReturn(expected);

        DeviceUserController.DeviceUserResponse response = controller.createUser(10L, req);

        assertEquals(expected, response);
        verify(deviceUserService).createDeviceUser(eq(10L), any());
    }

    @Test
    void createUserShouldRejectMissingEmployeeNo() {
        DeviceUserController.DeviceUserCreateRequest req =
                new DeviceUserController.DeviceUserCreateRequest(
                        "  ", "John Doe", "normal", null, null, null, null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.createUser(10L, req));
        assertEquals("400 BAD_REQUEST \"employeeNo is required\"", ex.getMessage());
        verifyNoInteractions(deviceUserService);
    }

    @Test
    void createUserShouldRejectMissingName() {
        DeviceUserController.DeviceUserCreateRequest req =
                new DeviceUserController.DeviceUserCreateRequest(
                        "1001", null, "normal", null, null, null, null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.createUser(10L, req));
        assertEquals("400 BAD_REQUEST \"name is required\"", ex.getMessage());
        verifyNoInteractions(deviceUserService);
    }

    @Test
    void getUserShouldDelegateToService() {
        DeviceUserController.DeviceUserResponse expected = userResponse(1L, 10L, "1001", "John Doe", true);
        when(deviceUserService.getDeviceUser(10L, 1L)).thenReturn(expected);

        DeviceUserController.DeviceUserResponse response = controller.getUser(10L, 1L);

        assertEquals(expected, response);
        verify(deviceUserService).getDeviceUser(10L, 1L);
    }

    @Test
    void updateUserShouldDelegateToService() {
        DeviceUserController.DeviceUserUpdateRequest req =
                new DeviceUserController.DeviceUserUpdateRequest(
                        "John Updated", "normal", "male", null, null, null);
        DeviceUserController.DeviceUserResponse expected = userResponse(1L, 10L, "1001", "John Updated", true);
        when(deviceUserService.updateDeviceUser(eq(10L), eq(1L), any())).thenReturn(expected);

        DeviceUserController.DeviceUserResponse response = controller.updateUser(10L, 1L, req);

        assertEquals(expected, response);
        verify(deviceUserService).updateDeviceUser(eq(10L), eq(1L), any());
    }

    @Test
    void deleteUserShouldDelegateToService() {
        doNothing().when(deviceUserService).deleteDeviceUser(10L, 1L);

        controller.deleteUser(10L, 1L);

        verify(deviceUserService).deleteDeviceUser(10L, 1L);
    }

    @Test
    void listUsersShouldReturnList() {
        List<DeviceUserController.DeviceUserResponse> users = List.of(
                userResponse(1L, 10L, "1001", "Alice", true),
                userResponse(2L, 10L, "1002", "Bob", false)
        );
        when(deviceUserService.listDeviceUsers(10L)).thenReturn(users);

        List<DeviceUserController.DeviceUserResponse> response = controller.listUsers(10L);

        assertEquals(2, response.size());
        assertEquals("1001", response.get(0).employeeNo());
        assertEquals("1002", response.get(1).employeeNo());
    }

    @Test
    void syncUserShouldDelegateToService() {
        DeviceUserController.DeviceUserSyncResponse expected =
                new DeviceUserController.DeviceUserSyncResponse(
                        1L, 10L, "1001", true, LocalDateTime.now(), "SUCCESS", "User synced successfully");
        when(deviceUserService.syncUserToDevice(10L, 1L)).thenReturn(expected);

        DeviceUserController.DeviceUserSyncResponse response = controller.syncUser(10L, 1L);

        assertEquals(expected, response);
        verify(deviceUserService).syncUserToDevice(10L, 1L);
    }

    @Test
    void uploadFaceShouldDelegateToService() {
        MockMultipartFile file = new MockMultipartFile("file", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});
        DeviceUserController.DeviceUserResponse expected = userResponse(1L, 10L, "1001", "John Doe", true);
        when(deviceUserService.uploadFaceData(eq(10L), eq(1L), any())).thenReturn(expected);

        DeviceUserController.DeviceUserResponse response = controller.uploadFace(10L, 1L, file);

        assertEquals(expected, response);
        verify(deviceUserService).uploadFaceData(eq(10L), eq(1L), any());
    }

    private DeviceUserController.DeviceUserResponse userResponse(
            Long id, Long deviceId, String employeeNo, String name, boolean synced) {
        LocalDateTime now = synced ? LocalDateTime.now() : null;
        return new DeviceUserController.DeviceUserResponse(
                id, deviceId, employeeNo, name, "normal", "male",
                LocalDateTime.of(2026, 1, 1, 0, 0),
                LocalDateTime.of(2036, 1, 1, 23, 59, 59),
                null, synced, now, LocalDateTime.now(), LocalDateTime.now());
    }
}
