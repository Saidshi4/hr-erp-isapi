package com.abv.hrerpisapi.dao.repository;

import com.abv.hrerpisapi.dao.entity.AttendancePunchEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface AttendancePunchRepository extends JpaRepository<AttendancePunchEntity, Long> {
    List<AttendancePunchEntity> findByOrderByPunchTimeDesc(Pageable pageable);

    List<AttendancePunchEntity> findByDeviceIdOrderByPunchTimeDesc(Long deviceId, Pageable pageable);

    List<AttendancePunchEntity> findByEmployeeNoOrderByPunchTimeDesc(String employeeNo, Pageable pageable);

    List<AttendancePunchEntity> findByDeviceIdAndEmployeeNoOrderByPunchTimeDesc(Long deviceId, String employeeNo, Pageable pageable);
}
