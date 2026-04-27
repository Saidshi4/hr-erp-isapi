package com.abv.hrerpisapi.dao.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "device_users",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_device_user_employee_no",
                columnNames = {"device_id", "employee_no"}
        )
)
@Getter
@Setter
@NoArgsConstructor
public class DeviceUserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false)
    private Long deviceId;

    @Column(name = "employee_no", nullable = false)
    private String employeeNo;

    @Column(nullable = false)
    private String name;

    @Column(name = "user_type", nullable = false)
    private String userType;

    private String gender;

    @Column(name = "begin_time")
    private LocalDateTime beginTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "face_data_url")
    private String faceDataUrl;

    @Column(name = "synced_to_device", nullable = false)
    private boolean syncedToDevice = false;

    @Column(name = "last_sync_time")
    private LocalDateTime lastSyncTime;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
