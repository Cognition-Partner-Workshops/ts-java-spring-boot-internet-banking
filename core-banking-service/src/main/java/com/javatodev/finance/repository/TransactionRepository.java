package com.javatodev.finance.repository;

import com.javatodev.finance.model.TransactionType;
import com.javatodev.finance.model.entity.TransactionEntity;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface TransactionRepository extends JpaRepository<TransactionEntity, Long> {

    @Query("SELECT t FROM TransactionEntity t WHERE t.account.number = :accountNumber " +
        "AND (:fromDate IS NULL OR t.createdAt >= :fromDate) " +
        "AND (:toDate IS NULL OR t.createdAt <= :toDate) " +
        "AND (:transactionType IS NULL OR t.transactionType = :transactionType) " +
        "ORDER BY t.createdAt DESC")
    Page<TransactionEntity> findByAccountAndFilters(
        @Param("accountNumber") String accountNumber,
        @Param("fromDate") LocalDateTime fromDate,
        @Param("toDate") LocalDateTime toDate,
        @Param("transactionType") TransactionType transactionType,
        Pageable pageable);

    @Query("SELECT t FROM TransactionEntity t WHERE t.account.number = :accountNumber " +
        "AND t.createdAt >= :startDate AND t.createdAt < :endDate " +
        "ORDER BY t.createdAt ASC")
    List<TransactionEntity> findByAccountAndDateRange(
        @Param("accountNumber") String accountNumber,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate);

    @Query("SELECT t FROM TransactionEntity t WHERE t.account.number = :accountNumber " +
        "AND t.createdAt < :beforeDate " +
        "ORDER BY t.createdAt DESC")
    List<TransactionEntity> findByAccountBeforeDate(
        @Param("accountNumber") String accountNumber,
        @Param("beforeDate") LocalDateTime beforeDate);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM TransactionEntity t " +
        "WHERE t.account.number = :accountNumber AND t.createdAt > :afterDate")
    BigDecimal sumAllTransactionsAfterDate(
        @Param("accountNumber") String accountNumber,
        @Param("afterDate") LocalDateTime afterDate);
}
