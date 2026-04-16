package com.abv.hrerpisapi.dao.repository;

import com.abv.hrerpisapi.dao.entity.AcsFailedAttemptEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AcsFailedAttemptRepository extends JpaRepository<AcsFailedAttemptEntity, Long> {
}
