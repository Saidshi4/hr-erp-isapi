package com.abv.hrerpisapi.dao.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "acs_failed_attempts")
@Getter
@Setter
@NoArgsConstructor
public class AcsFailedAttemptEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "raw_event_id")
    private Long rawEventId;

    @Column(name = "device_id", nullable = false)
    private Long deviceId;

    /** "E:<employeeNo>", "C:<cardNo>", or "U:<serialNo>" */
    private String identity;

    @Column(name = "sub_event_type")
    private Integer subEventType;

    @Column(name = "event_time")
    private OffsetDateTime eventTime;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}
