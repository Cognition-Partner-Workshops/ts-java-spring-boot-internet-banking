package com.javatodev.finance.repository;

import com.javatodev.finance.model.entity.TransactionEntity;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRepository extends JpaRepository<TransactionEntity, Long> {

    List<TransactionEntity> findByAccount_NumberOrderByCreatedAtDesc(String accountNumber);

}
