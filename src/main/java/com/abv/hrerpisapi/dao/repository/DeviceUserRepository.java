package com.abv.hrerpisapi.dao.repository;

import com.abv.hrerpisapi.dao.entity.DeviceUserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeviceUserRepository extends JpaRepository<DeviceUserEntity, Long> {

    List<DeviceUserEntity> findByDeviceId(Long deviceId);

    Optional<DeviceUserEntity> findByDeviceIdAndId(Long deviceId, Long id);

    boolean existsByDeviceIdAndEmployeeNo(Long deviceId, String employeeNo);

    Optional<DeviceUserEntity> findByDeviceIdAndEmployeeNo(Long deviceId, String employeeNo);
}
