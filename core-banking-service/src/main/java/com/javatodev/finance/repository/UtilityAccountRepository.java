package com.javatodev.finance.repository;

import com.javatodev.finance.model.entity.UtilityAccountEntity;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.Optional;

public interface UtilityAccountRepository extends MongoRepository<UtilityAccountEntity, Long> {

    @Query(value = "{ 'providerName': ?0 }", collation = MongoCollations.CASE_INSENSITIVE)
    Optional<UtilityAccountEntity> findByProviderName(String provider);
}
