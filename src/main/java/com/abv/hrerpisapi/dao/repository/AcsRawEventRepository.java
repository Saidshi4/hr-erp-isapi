package com.abv.hrerpisapi.dao.repository;

import com.abv.hrerpisapi.dao.entity.AcsRawEventEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AcsRawEventRepository extends JpaRepository<AcsRawEventEntity, Long> {

    boolean existsByDeviceIdAndSerialNo(Long deviceId, Long serialNo);

    @Query("""
            select e from AcsRawEventEntity e
            where (:deviceId is null or e.deviceId = :deviceId)
              and (:major is null or e.majorEventType = :major)
              and (:minor is null or e.subEventType = :minor)
            order by e.eventTime desc
            """)
    List<AcsRawEventEntity> findRecent(
            @Param("deviceId") Long deviceId,
            @Param("major") Integer major,
            @Param("minor") Integer minor,
            Pageable pageable
    );
}
