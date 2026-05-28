package com.javatodev.finance.service;

import com.javatodev.finance.exception.EntityNotFoundException;
import com.javatodev.finance.model.TransactionType;
import com.javatodev.finance.model.dto.response.MonthlySummaryResponse;
import com.javatodev.finance.model.dto.response.StatementResponse;
import com.javatodev.finance.model.entity.BankAccountEntity;
import com.javatodev.finance.model.entity.TransactionEntity;
import com.javatodev.finance.repository.BankAccountRepository;
import com.javatodev.finance.repository.TransactionRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AccountStatementServiceTest {

    private TransactionRepository transactionRepository;
    private BankAccountRepository bankAccountRepository;
    private AccountStatementService accountStatementService;

    @BeforeEach
    void setUp() {
        transactionRepository = mock(TransactionRepository.class);
        bankAccountRepository = mock(BankAccountRepository.class);
        accountStatementService = new AccountStatementService(transactionRepository, bankAccountRepository);
    }

    @Test
    void getAccountStatement_success_noFilters() {
        BankAccountEntity account = new BankAccountEntity();
        account.setNumber("100015003000");
        account.setAvailableBalance(BigDecimal.valueOf(100000));

        TransactionEntity tx = TransactionEntity.builder()
            .id(1L)
            .amount(BigDecimal.valueOf(-500))
            .transactionType(TransactionType.FUND_TRANSFER)
            .referenceNumber("100015003001")
            .transactionId("uuid-1")
            .account(account)
            .createdAt(LocalDateTime.of(2025, 5, 1, 10, 0))
            .description("Fund transfer to 100015003001")
            .build();

        Page<TransactionEntity> page = new PageImpl<>(List.of(tx), PageRequest.of(0, 20), 1);

        when(bankAccountRepository.findByNumber("100015003000")).thenReturn(Optional.of(account));
        when(transactionRepository.findByAccountAndFilters(eq("100015003000"), isNull(), isNull(), isNull(), any()))
            .thenReturn(page);
        when(transactionRepository.sumAllTransactionsAfterDate(eq("100015003000"),
            eq(LocalDateTime.of(2025, 5, 1, 10, 0))))
            .thenReturn(BigDecimal.ZERO);

        StatementResponse response = accountStatementService.getAccountStatement(
            "100015003000", null, null, "ALL", 0, 20);

        assertNotNull(response);
        assertEquals("100015003000", response.getAccountNumber());
        assertEquals(1, response.getTotalTransactions());
        assertEquals(1, response.getTransactions().size());
        assertEquals(BigDecimal.valueOf(100000), response.getCurrentBalance());
        assertEquals(BigDecimal.valueOf(100000), response.getTransactions().get(0).getRunningBalance());
    }

    @Test
    void getAccountStatement_success_withDateFilter() {
        BankAccountEntity account = new BankAccountEntity();
        account.setNumber("100015003000");
        account.setAvailableBalance(BigDecimal.valueOf(95000));

        TransactionEntity tx = TransactionEntity.builder()
            .id(2L)
            .amount(BigDecimal.valueOf(1000))
            .transactionType(TransactionType.FUND_TRANSFER)
            .referenceNumber("100015003002")
            .transactionId("uuid-2")
            .account(account)
            .createdAt(LocalDateTime.of(2025, 5, 15, 14, 30))
            .build();

        Page<TransactionEntity> page = new PageImpl<>(List.of(tx), PageRequest.of(0, 20), 1);

        when(bankAccountRepository.findByNumber("100015003000")).thenReturn(Optional.of(account));
        when(transactionRepository.findByAccountAndFilters(eq("100015003000"), any(), any(), isNull(), any()))
            .thenReturn(page);
        when(transactionRepository.sumAllTransactionsAfterDate(eq("100015003000"),
            eq(LocalDateTime.of(2025, 5, 15, 14, 30))))
            .thenReturn(BigDecimal.ZERO);

        StatementResponse response = accountStatementService.getAccountStatement(
            "100015003000", LocalDate.of(2025, 5, 1), LocalDate.of(2025, 5, 31), "ALL", 0, 20);

        assertNotNull(response);
        assertEquals(1, response.getTransactions().size());
        assertEquals("CREDIT", response.getTransactions().get(0).getType());
        assertEquals(BigDecimal.valueOf(95000), response.getTransactions().get(0).getRunningBalance());
    }

    @Test
    void getAccountStatement_success_withTypeFilter() {
        BankAccountEntity account = new BankAccountEntity();
        account.setNumber("100015003000");
        account.setAvailableBalance(BigDecimal.valueOf(95000));

        Page<TransactionEntity> emptyPage = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 20), 0);

        when(bankAccountRepository.findByNumber("100015003000")).thenReturn(Optional.of(account));
        when(transactionRepository.findByAccountAndFilters(eq("100015003000"), isNull(), isNull(),
            eq(TransactionType.UTILITY_PAYMENT), any()))
            .thenReturn(emptyPage);

        StatementResponse response = accountStatementService.getAccountStatement(
            "100015003000", null, null, "UTILITY_PAYMENT", 0, 20);

        assertNotNull(response);
        assertEquals(0, response.getTotalTransactions());
        assertTrue(response.getTransactions().isEmpty());
    }

    @Test
    void getAccountStatement_accountNotFound() {
        when(bankAccountRepository.findByNumber("INVALID")).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () ->
            accountStatementService.getAccountStatement("INVALID", null, null, "ALL", 0, 20));
    }

    @Test
    void getAccountStatement_emptyResults() {
        BankAccountEntity account = new BankAccountEntity();
        account.setNumber("100015003000");
        account.setAvailableBalance(BigDecimal.valueOf(100000));

        Page<TransactionEntity> emptyPage = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 20), 0);

        when(bankAccountRepository.findByNumber("100015003000")).thenReturn(Optional.of(account));
        when(transactionRepository.findByAccountAndFilters(eq("100015003000"), isNull(), isNull(), isNull(), any()))
            .thenReturn(emptyPage);

        StatementResponse response = accountStatementService.getAccountStatement(
            "100015003000", null, null, "ALL", 0, 20);

        assertNotNull(response);
        assertEquals(0, response.getTotalTransactions());
        assertEquals(0, response.getTotalPages());
        assertTrue(response.getTransactions().isEmpty());
    }

    @Test
    void getAccountStatement_transactionDescriptionFallback() {
        BankAccountEntity account = new BankAccountEntity();
        account.setNumber("100015003000");
        account.setAvailableBalance(BigDecimal.valueOf(100000));

        TransactionEntity tx = TransactionEntity.builder()
            .id(3L)
            .amount(BigDecimal.valueOf(-200))
            .transactionType(TransactionType.UTILITY_PAYMENT)
            .referenceNumber("REF-001")
            .transactionId("uuid-3")
            .account(account)
            .createdAt(LocalDateTime.of(2025, 5, 10, 9, 0))
            .description(null)
            .build();

        Page<TransactionEntity> page = new PageImpl<>(List.of(tx), PageRequest.of(0, 20), 1);

        when(bankAccountRepository.findByNumber("100015003000")).thenReturn(Optional.of(account));
        when(transactionRepository.findByAccountAndFilters(eq("100015003000"), isNull(), isNull(), isNull(), any()))
            .thenReturn(page);
        when(transactionRepository.sumAllTransactionsAfterDate(eq("100015003000"),
            eq(LocalDateTime.of(2025, 5, 10, 9, 0))))
            .thenReturn(BigDecimal.ZERO);

        StatementResponse response = accountStatementService.getAccountStatement(
            "100015003000", null, null, "ALL", 0, 20);

        assertEquals("UTILITY_PAYMENT", response.getTransactions().get(0).getDescription());
        assertEquals("DEBIT", response.getTransactions().get(0).getType());
    }

    @Test
    void getAccountStatement_runningBalance_descOrder() {
        BankAccountEntity account = new BankAccountEntity();
        account.setNumber("100015003000");
        account.setAvailableBalance(BigDecimal.valueOf(1600));

        TransactionEntity t3 = TransactionEntity.builder()
            .id(3L).amount(BigDecimal.valueOf(300))
            .transactionType(TransactionType.FUND_TRANSFER).referenceNumber("REF-3")
            .transactionId("uuid-3").account(account)
            .createdAt(LocalDateTime.of(2025, 5, 3, 10, 0)).build();

        TransactionEntity t2 = TransactionEntity.builder()
            .id(2L).amount(BigDecimal.valueOf(-200))
            .transactionType(TransactionType.FUND_TRANSFER).referenceNumber("REF-2")
            .transactionId("uuid-2").account(account)
            .createdAt(LocalDateTime.of(2025, 5, 2, 10, 0)).build();

        TransactionEntity t1 = TransactionEntity.builder()
            .id(1L).amount(BigDecimal.valueOf(500))
            .transactionType(TransactionType.FUND_TRANSFER).referenceNumber("REF-1")
            .transactionId("uuid-1").account(account)
            .createdAt(LocalDateTime.of(2025, 5, 1, 10, 0)).build();

        Page<TransactionEntity> page = new PageImpl<>(List.of(t3, t2, t1), PageRequest.of(0, 20), 3);

        when(bankAccountRepository.findByNumber("100015003000")).thenReturn(Optional.of(account));
        when(transactionRepository.findByAccountAndFilters(eq("100015003000"), isNull(), isNull(), isNull(), any()))
            .thenReturn(page);
        when(transactionRepository.sumAllTransactionsAfterDate("100015003000",
            LocalDateTime.of(2025, 5, 3, 10, 0)))
            .thenReturn(BigDecimal.ZERO);

        StatementResponse response = accountStatementService.getAccountStatement(
            "100015003000", null, null, "ALL", 0, 20);

        assertEquals(3, response.getTransactions().size());
        assertEquals(BigDecimal.valueOf(1600), response.getTransactions().get(0).getRunningBalance());
        assertEquals(BigDecimal.valueOf(1300), response.getTransactions().get(1).getRunningBalance());
        assertEquals(BigDecimal.valueOf(1500), response.getTransactions().get(2).getRunningBalance());
        verify(transactionRepository, times(1)).sumAllTransactionsAfterDate(any(), any());
    }

    @Test
    void getAccountStatement_runningBalance_pagination() {
        BankAccountEntity account = new BankAccountEntity();
        account.setNumber("100015003000");
        account.setAvailableBalance(BigDecimal.valueOf(1600));

        TransactionEntity t1 = TransactionEntity.builder()
            .id(1L).amount(BigDecimal.valueOf(500))
            .transactionType(TransactionType.FUND_TRANSFER).referenceNumber("REF-1")
            .transactionId("uuid-1").account(account)
            .createdAt(LocalDateTime.of(2025, 5, 1, 10, 0)).build();

        Page<TransactionEntity> page1 = new PageImpl<>(List.of(t1), PageRequest.of(1, 2), 3);

        when(bankAccountRepository.findByNumber("100015003000")).thenReturn(Optional.of(account));
        when(transactionRepository.findByAccountAndFilters(eq("100015003000"), isNull(), isNull(), isNull(), any()))
            .thenReturn(page1);
        when(transactionRepository.sumAllTransactionsAfterDate("100015003000",
            LocalDateTime.of(2025, 5, 1, 10, 0)))
            .thenReturn(BigDecimal.valueOf(100));

        StatementResponse response = accountStatementService.getAccountStatement(
            "100015003000", null, null, "ALL", 1, 2);

        assertEquals(1, response.getTransactions().size());
        assertEquals(BigDecimal.valueOf(1500), response.getTransactions().get(0).getRunningBalance());
    }

    @Test
    void getMonthlySummary_success() {
        BankAccountEntity account = new BankAccountEntity();
        account.setNumber("100015003000");
        account.setAvailableBalance(BigDecimal.valueOf(95000));

        TransactionEntity credit = TransactionEntity.builder()
            .id(1L)
            .amount(BigDecimal.valueOf(5000))
            .transactionType(TransactionType.FUND_TRANSFER)
            .referenceNumber("100015003001")
            .transactionId("uuid-c1")
            .account(account)
            .createdAt(LocalDateTime.of(2025, 5, 5, 10, 0))
            .build();

        TransactionEntity debit = TransactionEntity.builder()
            .id(2L)
            .amount(BigDecimal.valueOf(-2000))
            .transactionType(TransactionType.UTILITY_PAYMENT)
            .referenceNumber("REF-002")
            .transactionId("uuid-d1")
            .account(account)
            .createdAt(LocalDateTime.of(2025, 5, 10, 14, 0))
            .build();

        when(bankAccountRepository.findByNumber("100015003000")).thenReturn(Optional.of(account));
        when(transactionRepository.findByAccountAndDateRange(eq("100015003000"),
            eq(LocalDateTime.of(2025, 5, 1, 0, 0)),
            eq(LocalDateTime.of(2025, 6, 1, 0, 0))))
            .thenReturn(List.of(credit, debit));
        when(transactionRepository.sumAllTransactionsFromDate(eq("100015003000"),
            eq(LocalDateTime.of(2025, 5, 1, 0, 0))))
            .thenReturn(BigDecimal.valueOf(3000));

        MonthlySummaryResponse response = accountStatementService.getMonthlySummary("100015003000", 5, 2025);

        assertNotNull(response);
        assertEquals("100015003000", response.getAccountNumber());
        assertEquals(5, response.getMonth());
        assertEquals(2025, response.getYear());
        assertEquals(BigDecimal.valueOf(5000), response.getTotalCredits());
        assertEquals(BigDecimal.valueOf(2000), response.getTotalDebits());
        assertEquals(BigDecimal.valueOf(3000), response.getNetChange());
        assertEquals(2, response.getTransactionCount());
    }

    @Test
    void getMonthlySummary_accountNotFound() {
        when(bankAccountRepository.findByNumber("INVALID")).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () ->
            accountStatementService.getMonthlySummary("INVALID", 5, 2025));
    }

    @Test
    void getMonthlySummary_noTransactions() {
        BankAccountEntity account = new BankAccountEntity();
        account.setNumber("100015003000");
        account.setAvailableBalance(BigDecimal.valueOf(100000));

        when(bankAccountRepository.findByNumber("100015003000")).thenReturn(Optional.of(account));
        when(transactionRepository.findByAccountAndDateRange(eq("100015003000"), any(), any()))
            .thenReturn(Collections.emptyList());
        when(transactionRepository.sumAllTransactionsFromDate(eq("100015003000"), any()))
            .thenReturn(BigDecimal.ZERO);

        MonthlySummaryResponse response = accountStatementService.getMonthlySummary("100015003000", 1, 2025);

        assertNotNull(response);
        assertEquals(BigDecimal.ZERO, response.getTotalCredits());
        assertEquals(BigDecimal.ZERO, response.getTotalDebits());
        assertEquals(BigDecimal.ZERO, response.getNetChange());
        assertEquals(0, response.getTransactionCount());
    }

    @Test
    void getAccountStatement_invalidTransactionType() {
        BankAccountEntity account = new BankAccountEntity();
        account.setNumber("100015003000");
        account.setAvailableBalance(BigDecimal.valueOf(100000));

        when(bankAccountRepository.findByNumber("100015003000")).thenReturn(Optional.of(account));

        assertThrows(IllegalArgumentException.class, () ->
            accountStatementService.getAccountStatement("100015003000", null, null, "INVALID_TYPE", 0, 20));
    }

}
