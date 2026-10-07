package com.javatodev.finance.repository;

import com.javatodev.finance.model.entity.UserEntity;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.Optional;

public interface UserRepository extends MongoRepository<UserEntity, Long> {

    @Query(value = "{ 'identificationNumber': ?0 }", collation = MongoCollations.CASE_INSENSITIVE)
    Optional<UserEntity> findByIdentificationNumber(String identificationNumber);
}
