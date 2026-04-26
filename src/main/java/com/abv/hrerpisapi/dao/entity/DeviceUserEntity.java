package com.abv.hrerpisapi.dao.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(
        name = "device_users",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_device_user_username",
                columnNames = {"device_id", "username"}
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

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(name = "user_type", nullable = false)
    private String userType;

    private String name;

    @Column(name = "synced_to_device", nullable = false)
    private boolean syncedToDevice = false;

    @Column(name = "last_sync_time")
    private OffsetDateTime lastSyncTime;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
