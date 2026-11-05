package com.javatodev.finance.model.repository;

import com.javatodev.finance.model.entity.FundTransferEntity;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FundTransferRepository extends JpaRepository<FundTransferEntity, Long> {
    Page<FundTransferEntity> findAllByOwnerId(String ownerId, Pageable pageable);
}
