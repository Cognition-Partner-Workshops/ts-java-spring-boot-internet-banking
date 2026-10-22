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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class TransactionRepositoryTest {

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private BankAccountRepository bankAccountRepository;

    private BankAccountEntity account;

    @BeforeEach
    void setUp() {
        account = new BankAccountEntity();
        account.setNumber("A1");
        account.setType(AccountType.SAVINGS_ACCOUNT);
        account.setStatus(AccountStatus.ACTIVE);
        account.setActualBalance(BigDecimal.valueOf(1000));
        account.setAvailableBalance(BigDecimal.valueOf(1000));
        account = bankAccountRepository.save(account);
    }

    private void saveTransaction(String reference, LocalDateTime timestamp) {
        transactionRepository.save(TransactionEntity.builder()
            .transactionType(TransactionType.FUND_TRANSFER)
            .referenceNumber(reference)
            .transactionId(reference)
            .amount(BigDecimal.valueOf(10))
            .timestamp(timestamp)
            .account(account)
            .build());
    }

    @Test
    void ordersMostRecentFirst() {
        saveTransaction("OLD", LocalDateTime.of(2026, 7, 1, 9, 0));
        saveTransaction("MID", LocalDateTime.of(2026, 7, 5, 9, 0));
        saveTransaction("NEW", LocalDateTime.of(2026, 7, 10, 9, 0));

        List<TransactionEntity> result = transactionRepository.findByAccount_NumberOrderByTimestampDescIdDesc("A1");

        assertEquals(List.of("NEW", "MID", "OLD"),
            result.stream().map(TransactionEntity::getReferenceNumber).toList());
    }

    @Test
    void betweenIsInclusiveOnBothBoundaries() {
        // lower boundary at start of "from" day
        saveTransaction("LOWER_EDGE", LocalDate.of(2026, 7, 1).atStartOfDay());
        // later on the "to" day — must NOT be dropped when the window ends on that day
        saveTransaction("UPPER_EDGE", LocalDateTime.of(2026, 7, 10, 23, 30));
        saveTransaction("INSIDE", LocalDateTime.of(2026, 7, 5, 12, 0));
        // strictly outside the window on both ends
        saveTransaction("BEFORE", LocalDateTime.of(2026, 6, 30, 23, 59));
        saveTransaction("AFTER", LocalDateTime.of(2026, 7, 11, 0, 1));

        LocalDateTime from = LocalDate.of(2026, 7, 1).atStartOfDay();
        LocalDateTime to = LocalDate.of(2026, 7, 10).atTime(LocalTime.MAX);

        List<TransactionEntity> result = transactionRepository
            .findByAccount_NumberAndTimestampBetweenOrderByTimestampDescIdDesc("A1", from, to);

        List<String> references = result.stream().map(TransactionEntity::getReferenceNumber).toList();
        assertEquals(3, references.size());
        assertTrue(references.contains("LOWER_EDGE"), "start-of-day lower boundary must be included");
        assertTrue(references.contains("UPPER_EDGE"), "later-in-day upper boundary must be included");
        assertTrue(references.contains("INSIDE"));
        assertEquals(List.of("UPPER_EDGE", "INSIDE", "LOWER_EDGE"), references);
    }

    @Test
    void emptyWhenNoTransactionsForAccount() {
        List<TransactionEntity> result = transactionRepository.findByAccount_NumberOrderByTimestampDescIdDesc("A1");
        assertTrue(result.isEmpty());
    }
}
