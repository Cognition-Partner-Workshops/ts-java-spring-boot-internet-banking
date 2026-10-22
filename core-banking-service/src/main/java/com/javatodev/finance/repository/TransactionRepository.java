package com.javatodev.finance.repository;

import com.javatodev.finance.model.entity.TransactionEntity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransactionRepository extends JpaRepository<TransactionEntity, Long> {

    List<TransactionEntity> findByAccount_NumberOrderByTimestampDescIdDesc(String accountNumber);
}
