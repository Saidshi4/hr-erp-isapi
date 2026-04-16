package com.abv.hrerpisapi.dao.repository;

import com.abv.hrerpisapi.dao.entity.AttendancePunchEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendancePunchRepository extends JpaRepository<AttendancePunchEntity, Long> {
}
