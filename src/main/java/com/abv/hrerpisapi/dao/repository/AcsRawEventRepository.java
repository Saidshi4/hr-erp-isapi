package com.abv.hrerpisapi.dao.repository;

import com.abv.hrerpisapi.dao.entity.AcsRawEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AcsRawEventRepository extends JpaRepository<AcsRawEventEntity, Long> {

    boolean existsByDeviceIdAndSerialNo(Long deviceId, Long serialNo);
}
