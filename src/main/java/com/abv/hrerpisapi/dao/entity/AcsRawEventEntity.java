package com.abv.hrerpisapi.dao.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(
        name = "acs_raw_events",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_acs_raw_device_serial",
                columnNames = {"device_id", "serial_no"}
        )
)
@Getter
@Setter
@NoArgsConstructor
public class AcsRawEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false)
    private Long deviceId;

    @Column(name = "serial_no")
    private Long serialNo;

    @Column(name = "event_time")
    private OffsetDateTime eventTime;

    @Column(name = "major_event_type")
    private Integer majorEventType;

    @Column(name = "sub_event_type")
    private Integer subEventType;

    @Column(name = "employee_no_string")
    private String employeeNoString;

    @Column(name = "card_no")
    private String cardNo;

    @Column(name = "raw_json", columnDefinition = "TEXT")
    private String rawJson;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}
