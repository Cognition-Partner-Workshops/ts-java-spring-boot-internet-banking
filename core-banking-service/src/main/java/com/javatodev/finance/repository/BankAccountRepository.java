package com.javatodev.finance.repository;

import com.javatodev.finance.model.entity.BankAccountEntity;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface BankAccountRepository extends MongoRepository<BankAccountEntity, Long> {

    @Query(value = "{ 'number': ?0 }", collation = MongoCollations.CASE_INSENSITIVE)
    Optional<BankAccountEntity> findByNumber(String accountNumber);

    List<BankAccountEntity> findByUserId(Long userId);
}
