package com.abv.hrerpisapi.scheduler;

import com.abv.hrerpisapi.dao.entity.DeviceEntity;
import com.abv.hrerpisapi.dao.repository.DeviceCursorRepository;
import com.abv.hrerpisapi.dao.repository.DeviceRepository;
import com.abv.hrerpisapi.device.client.IsapiClient;
import com.abv.hrerpisapi.service.AcsIngestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AcsEventHistoryPollerTest {

    @Mock
    private DeviceRepository deviceRepository;
    @Mock
    private DeviceCursorRepository cursorRepository;
    @Mock
    private IsapiClient isapiClient;
    @Mock
    private AcsIngestService acsIngestService;

    private AcsEventHistoryPoller poller;

    @BeforeEach
    void setUp() {
        poller = new AcsEventHistoryPoller(deviceRepository, cursorRepository, isapiClient, acsIngestService);
        ReflectionTestUtils.setField(poller, "unsupportedCooldownMinutes", 60L);
    }

    @Test
    void shouldDisableHistoryPollingForCooldownWhenAcsEventSearchIsNotSupported() throws Exception {
        DeviceEntity device = device(1L, "192.168.1.10");
        when(deviceRepository.findByEnabledTrue()).thenReturn(List.of(device));
        when(cursorRepository.findById(1L)).thenReturn(Optional.empty());
        when(isapiClient.searchAcsEvents(eq(device), any(), anyLong(), anyInt()))
                .thenThrow(new IsapiClient.AcsEventHistoryNotSupportedException(1L));

        poller.poll();
        poller.poll();

        verify(isapiClient, times(1)).searchAcsEvents(eq(device), any(), anyLong(), anyInt());
        verifyNoInteractions(acsIngestService);
    }

    @Test
    void shouldKeepPollingForOtherFailures() throws Exception {
        DeviceEntity device = device(1L, "192.168.1.10");
        when(deviceRepository.findByEnabledTrue()).thenReturn(List.of(device));
        when(cursorRepository.findById(1L)).thenReturn(Optional.empty());
        when(isapiClient.searchAcsEvents(eq(device), any(), anyLong(), anyInt()))
                .thenThrow(new RuntimeException("network error"));

        poller.poll();
        poller.poll();

        verify(isapiClient, times(2)).searchAcsEvents(eq(device), any(), anyLong(), anyInt());
    }

    private DeviceEntity device(Long id, String ip) {
        DeviceEntity d = new DeviceEntity();
        d.setId(id);
        d.setIp(ip);
        d.setEnabled(true);
        return d;
    }
}
