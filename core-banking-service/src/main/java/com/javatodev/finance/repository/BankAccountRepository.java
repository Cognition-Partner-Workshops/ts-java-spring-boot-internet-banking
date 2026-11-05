package com.javatodev.finance.repository;

import com.javatodev.finance.model.entity.BankAccountEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface BankAccountRepository extends JpaRepository<BankAccountEntity, Long> {
    Optional<BankAccountEntity> findByNumber(String accountNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from BankAccountEntity a where a.number = :number")
    Optional<BankAccountEntity> findByNumberForUpdate(@Param("number") String accountNumber);
}
