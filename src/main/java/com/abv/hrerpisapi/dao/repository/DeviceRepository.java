package com.abv.hrerpisapi.dao.repository;

import com.abv.hrerpisapi.dao.entity.DeviceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeviceRepository extends JpaRepository<DeviceEntity, Long> {

    List<DeviceEntity> findByEnabledTrue();

    List<DeviceEntity> findByEnabled(boolean enabled);
}
