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
    @Query("select account from BankAccountEntity account where account.number = :accountNumber")
    Optional<BankAccountEntity> findByNumberForUpdate(@Param("accountNumber") String accountNumber);
}
