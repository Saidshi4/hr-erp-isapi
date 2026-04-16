package com.abv.hrerpisapi.controller;

import com.abv.hrerpisapi.dao.entity.AcsRawEventEntity;
import com.abv.hrerpisapi.dao.entity.AttendancePunchEntity;
import com.abv.hrerpisapi.dao.repository.AcsFailedAttemptRepository;
import com.abv.hrerpisapi.dao.repository.AcsRawEventRepository;
import com.abv.hrerpisapi.dao.repository.AttendancePunchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventReadControllerTest {

    @Mock
    private AttendancePunchRepository attendancePunchRepository;
    @Mock
    private AcsRawEventRepository acsRawEventRepository;
    @Mock
    private AcsFailedAttemptRepository acsFailedAttemptRepository;

    private EventReadController controller;

    @BeforeEach
    void setUp() {
        controller = new EventReadController(attendancePunchRepository, acsRawEventRepository, acsFailedAttemptRepository);
    }

    @Test
    void getPunchesShouldTrimEmployeeNoAndUseFilteredRepositoryMethod() {
        AttendancePunchEntity entity = new AttendancePunchEntity();
        entity.setId(10L);
        entity.setDeviceId(1L);
        entity.setEmployeeNo("alma");
        entity.setPunchTime(OffsetDateTime.now());
        entity.setRawEventId(99L);

        when(attendancePunchRepository.findByDeviceIdAndEmployeeNoOrderByPunchTimeDesc(eq(1L), eq("alma"), any(Pageable.class)))
                .thenReturn(List.of(entity));

        List<EventReadController.PunchResponse> response = controller.getPunches(1L, "  alma  ", 25);

        assertEquals(1, response.size());
        assertEquals("alma", response.getFirst().employeeNo());
        verify(attendancePunchRepository).findByDeviceIdAndEmployeeNoOrderByPunchTimeDesc(eq(1L), eq("alma"), any(Pageable.class));
    }

    @Test
    void getRawEventsShouldExcludeRawJsonWhenFlagIsFalse() {
        AcsRawEventEntity entity = new AcsRawEventEntity();
        entity.setId(3L);
        entity.setDeviceId(1L);
        entity.setSerialNo(100L);
        entity.setRawJson("{\"x\":1}");

        when(acsRawEventRepository.findRecent(eq(1L), eq(5), eq(1), any(Pageable.class)))
                .thenReturn(List.of(entity));

        List<EventReadController.RawEventResponse> response = controller.getRawEvents(1L, 5, 1, false, 10);

        assertEquals(1, response.size());
        assertNull(response.getFirst().rawJson());
    }

    @Test
    void getRawEventsShouldValidateLimitRange() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> controller.getRawEvents(null, null, null, false, 501));

        assertEquals("400 BAD_REQUEST \"limit must be between 1 and 500\"", exception.getMessage());
    }

    @Test
    void getPunchesShouldTreatBlankEmployeeNoAsMissingFilter() {
        when(attendancePunchRepository.findByDeviceIdOrderByPunchTimeDesc(eq(1L), any(Pageable.class)))
                .thenReturn(List.of());

        controller.getPunches(1L, "   ", null);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(attendancePunchRepository).findByDeviceIdOrderByPunchTimeDesc(eq(1L), pageableCaptor.capture());
        assertEquals(50, pageableCaptor.getValue().getPageSize());
    }
}
