package com.javatodev.finance.repository;

import com.javatodev.finance.model.entity.UtilityPaymentEntity;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UtilityPaymentRepository extends JpaRepository<UtilityPaymentEntity, Long> {
    Page<UtilityPaymentEntity> findAllByOwnerId(String ownerId, Pageable pageable);
}
