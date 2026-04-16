package com.abv.hrerpisapi.dao.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "device_cursors")
@Getter
@Setter
@NoArgsConstructor
public class DeviceCursorEntity {

    @Id
    @Column(name = "device_id")
    private Long deviceId;

    @Column(name = "last_serial_no")
    private Long lastSerialNo = 0L;

    @Column(name = "last_event_time")
    private OffsetDateTime lastEventTime;
}
