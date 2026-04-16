package com.abv.hrerpisapi.dao.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "attendance_punches")
@Getter
@Setter
@NoArgsConstructor
public class AttendancePunchEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "raw_event_id")
    private Long rawEventId;

    @Column(name = "device_id", nullable = false)
    private Long deviceId;

    @Column(name = "employee_no")
    private String employeeNo;

    @Column(name = "punch_time")
    private OffsetDateTime punchTime;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}
