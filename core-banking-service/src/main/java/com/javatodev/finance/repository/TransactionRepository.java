package com.javatodev.finance.repository;

import com.javatodev.finance.model.entity.TransactionEntity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface TransactionRepository extends JpaRepository<TransactionEntity, Long> {

    List<TransactionEntity> findByAccount_NumberOrderByTimestampDescIdDesc(String accountNumber);

    List<TransactionEntity> findByAccount_NumberAndTimestampBetweenOrderByTimestampDescIdDesc(
        String accountNumber, LocalDateTime from, LocalDateTime to);

    List<TransactionEntity> findByAccount_NumberAndTimestampGreaterThanEqualOrderByTimestampDescIdDesc(
        String accountNumber, LocalDateTime from);

    List<TransactionEntity> findByAccount_NumberAndTimestampLessThanEqualOrderByTimestampDescIdDesc(
        String accountNumber, LocalDateTime to);
}
