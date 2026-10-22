package com.javatodev.finance.repository;

import com.javatodev.finance.model.AccountStatus;
import com.javatodev.finance.model.AccountType;
import com.javatodev.finance.model.TransactionType;
import com.javatodev.finance.model.entity.BankAccountEntity;
import com.javatodev.finance.model.entity.TransactionEntity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@TestPropertySource(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false"
})
class TransactionRepositoryTest {

    private static final String ACCOUNT = "ACC-1";
    private static final String OTHER_ACCOUNT = "ACC-2";

    @Autowired
    private TransactionRepository transactionRepository;

    @BeforeEach
    void seed() {
        // before range
        persist(ACCOUNT, "T0", LocalDateTime.of(2025, 12, 31, 23, 59, 59));
        // lower boundary (start of fromDate)
        persist(ACCOUNT, "T1", LocalDateTime.of(2026, 1, 1, 0, 0, 0));
        // middle
        persist(ACCOUNT, "T2", LocalDateTime.of(2026, 1, 15, 12, 0, 0));
        // upper boundary (within toDate day)
        persist(ACCOUNT, "T3", LocalDateTime.of(2026, 1, 31, 23, 59, 59));
        // after range
        persist(ACCOUNT, "T4", LocalDateTime.of(2026, 2, 1, 0, 0, 0));
        // different account, in range - must not leak into ACCOUNT queries
        persist(OTHER_ACCOUNT, "T5", LocalDateTime.of(2026, 1, 10, 10, 0, 0));
    }

    @Test
    void findByAccountNumber_returnsAllForAccount_orderedMostRecentFirst() {
        List<TransactionEntity> result =
            transactionRepository.findByAccount_NumberOrderByCreatedAtDesc(ACCOUNT);

        assertEquals(List.of("T4", "T3", "T2", "T1", "T0"), ids(result));
    }

    @Test
    void findByAccountNumberAndCreatedAtBetween_isInclusiveOnBothBoundaries() {
        LocalDateTime start = LocalTime.MIN.atDate(java.time.LocalDate.of(2026, 1, 1));
        LocalDateTime end = LocalTime.MAX.atDate(java.time.LocalDate.of(2026, 1, 31));

        List<TransactionEntity> result =
            transactionRepository.findByAccount_NumberAndCreatedAtBetweenOrderByCreatedAtDesc(ACCOUNT, start, end);

        // T1 (exactly at start) and T3 (on toDate) included; T0/T4 out of range; T5 other account
        assertEquals(List.of("T3", "T2", "T1"), ids(result));
    }

    @Test
    void findByAccountNumber_unknownAccount_returnsEmpty() {
        assertTrue(transactionRepository.findByAccount_NumberOrderByCreatedAtDesc("UNKNOWN").isEmpty());
    }

    private List<String> ids(List<TransactionEntity> transactions) {
        return transactions.stream().map(TransactionEntity::getTransactionId).toList();
    }

    private void persist(String accountNumber, String transactionId, LocalDateTime createdAt) {
        BankAccountEntity account = new BankAccountEntity();
        account.setNumber(accountNumber);
        account.setType(AccountType.SAVINGS_ACCOUNT);
        account.setStatus(AccountStatus.ACTIVE);
        account.setActualBalance(BigDecimal.valueOf(1000));
        account.setAvailableBalance(BigDecimal.valueOf(1000));

        TransactionEntity transaction = TransactionEntity.builder()
            .transactionId(transactionId)
            .referenceNumber("REF-" + transactionId)
            .transactionType(TransactionType.FUND_TRANSFER)
            .amount(BigDecimal.valueOf(100))
            .createdAt(createdAt)
            .account(account)
            .build();

        transactionRepository.saveAndFlush(transaction);
    }
}
