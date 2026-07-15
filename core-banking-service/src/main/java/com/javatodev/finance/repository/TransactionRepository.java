package com.javatodev.finance.repository;

import com.javatodev.finance.model.entity.TransactionEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface TransactionRepository extends JpaRepository<TransactionEntity, Long> {

    @Query("SELECT t FROM TransactionEntity t "
        + "WHERE t.account.number = :accountNumber "
        + "AND t.timestamp BETWEEN :start AND :end")
    List<TransactionEntity> findForStatement(@Param("accountNumber") String accountNumber,
                                             @Param("start") LocalDateTime start,
                                             @Param("end") LocalDateTime end);
}
