package com.javatodev.finance.repository;

import com.javatodev.finance.model.entity.TransactionEntity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface TransactionRepository extends JpaRepository<TransactionEntity, Long> {

    List<TransactionEntity> findByAccount_NumberOrderByCreatedAtDesc(String number);

    List<TransactionEntity> findByAccount_NumberAndCreatedAtBetweenOrderByCreatedAtDesc(
        String number, LocalDateTime start, LocalDateTime end);
}
