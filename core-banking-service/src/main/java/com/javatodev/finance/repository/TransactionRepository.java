package com.javatodev.finance.repository;

import com.javatodev.finance.model.entity.TransactionEntity;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface TransactionRepository extends MongoRepository<TransactionEntity, Long> {

    Optional<TransactionEntity> findFirstByOrderByIdDesc();
}
