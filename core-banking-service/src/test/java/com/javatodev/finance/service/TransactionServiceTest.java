package com.javatodev.finance.service;

import com.javatodev.finance.exception.EntityNotFoundException;
import com.javatodev.finance.exception.GlobalErrorCode;
import com.javatodev.finance.exception.InsufficientFundsException;
import com.javatodev.finance.model.TransactionType;
import com.javatodev.finance.model.dto.BankAccount;
import com.javatodev.finance.model.dto.UtilityAccount;
import com.javatodev.finance.model.dto.request.FundTransferRequest;
import com.javatodev.finance.model.dto.request.UtilityPaymentRequest;
import com.javatodev.finance.exception.SimpleBankingGlobalException;
import com.javatodev.finance.model.dto.response.FundTransferResponse;
import com.javatodev.finance.model.dto.response.StatementSummaryResponse;
import com.javatodev.finance.model.dto.response.TransactionTypeTotal;
import com.javatodev.finance.model.dto.response.UtilityPaymentResponse;
import com.javatodev.finance.model.entity.BankAccountEntity;
import com.javatodev.finance.model.entity.TransactionEntity;
import com.javatodev.finance.repository.BankAccountRepository;
import com.javatodev.finance.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TransactionServiceTest {
    private AccountService accountService;
    private BankAccountRepository bankAccountRepository;
    private TransactionRepository transactionRepository;
    private TransactionService transactionService;

    @BeforeEach
    void setUp() {
        accountService = mock(AccountService.class);
        bankAccountRepository = mock(BankAccountRepository.class);
        transactionRepository = mock(TransactionRepository.class);
        transactionService = new TransactionService(accountService, bankAccountRepository, transactionRepository);
    }

    @Test
    void fundTransfer_success() {
        FundTransferRequest request = new FundTransferRequest();
        request.setFromAccount("A1");
        request.setToAccount("A2");
        request.setAmount(BigDecimal.valueOf(100));

        BankAccount from = new BankAccount();
        from.setNumber("A1");
        from.setActualBalance(BigDecimal.valueOf(200));
        BankAccount to = new BankAccount();
        to.setNumber("A2");
        to.setActualBalance(BigDecimal.valueOf(50));

        when(accountService.readBankAccount("A1")).thenReturn(from);
        when(accountService.readBankAccount("A2")).thenReturn(to);
        BankAccountEntity fromEntity = new BankAccountEntity();
        fromEntity.setNumber("A1");
        fromEntity.setActualBalance(BigDecimal.valueOf(200));
        BankAccountEntity toEntity = new BankAccountEntity();
        toEntity.setNumber("A2");
        toEntity.setActualBalance(BigDecimal.valueOf(50));
        when(bankAccountRepository.findByNumber("A1")).thenReturn(Optional.of(fromEntity));
        when(bankAccountRepository.findByNumber("A2")).thenReturn(Optional.of(toEntity));

        FundTransferResponse response = transactionService.fundTransfer(request);
        assertNotNull(response.getTransactionId());
        assertEquals("Transaction successfully completed", response.getMessage());
    }

    @Test
    void fundTransfer_insufficientFunds() {
        FundTransferRequest request = new FundTransferRequest();
        request.setFromAccount("A1");
        request.setToAccount("A2");
        request.setAmount(BigDecimal.valueOf(300));
        BankAccount from = new BankAccount();
        from.setNumber("A1");
        from.setActualBalance(BigDecimal.valueOf(200));
        BankAccount to = new BankAccount();
        to.setNumber("A2");
        to.setActualBalance(BigDecimal.valueOf(50));
        when(accountService.readBankAccount("A1")).thenReturn(from);
        when(accountService.readBankAccount("A2")).thenReturn(to);
        assertThrows(InsufficientFundsException.class, () -> transactionService.fundTransfer(request));
    }

    @Test
    void fundTransfer_accountNotFound() {
        FundTransferRequest request = new FundTransferRequest();
        request.setFromAccount("A1");
        request.setToAccount("A2");
        request.setAmount(BigDecimal.valueOf(100));
        when(accountService.readBankAccount("A1")).thenThrow(EntityNotFoundException.class);
        assertThrows(EntityNotFoundException.class, () -> transactionService.fundTransfer(request));
    }

    @Test
    void utilPayment_success() {
        UtilityPaymentRequest request = new UtilityPaymentRequest();
        request.setAccount("A1");
        request.setProviderId(1L);
        request.setAmount(BigDecimal.valueOf(50));
        request.setReferenceNumber("REF123");
        BankAccount from = new BankAccount();
        from.setNumber("A1");
        from.setActualBalance(BigDecimal.valueOf(100));
        UtilityAccount utility = new UtilityAccount();
        utility.setId(1L);
        when(accountService.readBankAccount("A1")).thenReturn(from);
        when(accountService.readUtilityAccount(1L)).thenReturn(utility);
        BankAccountEntity fromEntity = new BankAccountEntity();
        fromEntity.setNumber("A1");
        fromEntity.setActualBalance(BigDecimal.valueOf(100));
        fromEntity.setAvailableBalance(BigDecimal.valueOf(100));
        when(bankAccountRepository.findByNumber("A1")).thenReturn(Optional.of(fromEntity));
        UtilityPaymentResponse response = transactionService.utilPayment(request);
        assertNotNull(response.getTransactionId());
        assertEquals("Utility payment successfully completed", response.getMessage());
    }

    @Test
    void utilPayment_insufficientFunds() {
        UtilityPaymentRequest request = new UtilityPaymentRequest();
        request.setAccount("A1");
        request.setProviderId(1L);
        request.setAmount(BigDecimal.valueOf(150));
        BankAccount from = new BankAccount();
        from.setNumber("A1");
        from.setActualBalance(BigDecimal.valueOf(100));
        when(accountService.readBankAccount("A1")).thenReturn(from);
        assertThrows(InsufficientFundsException.class, () -> transactionService.utilPayment(request));
    }

    @Test
    void utilPayment_accountNotFound() {
        UtilityPaymentRequest request = new UtilityPaymentRequest();
        request.setAccount("A1");
        request.setProviderId(1L);
        request.setAmount(BigDecimal.valueOf(50));
        when(accountService.readBankAccount("A1")).thenThrow(EntityNotFoundException.class);
        assertThrows(EntityNotFoundException.class, () -> transactionService.utilPayment(request));
    }

    @Test
    void internalFundTransfer_success() {
        BankAccount from = new BankAccount();
        from.setNumber("A1");
        from.setActualBalance(BigDecimal.valueOf(200));
        BankAccount to = new BankAccount();
        to.setNumber("A2");
        to.setActualBalance(BigDecimal.valueOf(50));
        BankAccountEntity fromEntity = new BankAccountEntity();
        fromEntity.setNumber("A1");
        fromEntity.setActualBalance(BigDecimal.valueOf(200));
        fromEntity.setAvailableBalance(BigDecimal.valueOf(200));
        BankAccountEntity toEntity = new BankAccountEntity();
        toEntity.setNumber("A2");
        toEntity.setActualBalance(BigDecimal.valueOf(50));
        toEntity.setAvailableBalance(BigDecimal.valueOf(50));
        when(bankAccountRepository.findByNumber("A1")).thenReturn(Optional.of(fromEntity));
        when(bankAccountRepository.findByNumber("A2")).thenReturn(Optional.of(toEntity));
        String transactionId = transactionService.internalFundTransfer(from, to, BigDecimal.valueOf(100));
        assertNotNull(transactionId);
        verify(bankAccountRepository, times(2)).save(any(BankAccountEntity.class));
        verify(transactionRepository, times(2)).save(any(TransactionEntity.class));
    }

    @Test
    void internalFundTransfer_entityNotFound() {
        BankAccount from = new BankAccount();
        from.setNumber("A1");
        BankAccount to = new BankAccount();
        to.setNumber("A2");
        when(bankAccountRepository.findByNumber("A1")).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> transactionService.internalFundTransfer(from, to, BigDecimal.valueOf(100)));
    }

    @Test
    void validateBalance_throwsException() {
        BankAccount account = new BankAccount();
        account.setNumber("A1");
        account.setActualBalance(BigDecimal.valueOf(50));
        when(accountService.readBankAccount("A1")).thenReturn(account);
        assertThrows(InsufficientFundsException.class, () -> {
            transactionService.fundTransfer(new FundTransferRequest("A1", "A2", BigDecimal.valueOf(100)));
        });
    }

    private TransactionEntity transactionEntity(TransactionType type, String amount, LocalDateTime timestamp) {
        return TransactionEntity.builder()
            .transactionType(type)
            .amount(new BigDecimal(amount))
            .timestamp(timestamp)
            .build();
    }

    private void accountExists(String accountNumber) {
        BankAccount account = new BankAccount();
        account.setNumber(accountNumber);
        when(accountService.readBankAccount(accountNumber)).thenReturn(account);
    }

    @Test
    void getStatementSummary_happyPath_singleType() {
        accountExists("A1");
        LocalDate from = LocalDate.of(2021, 1, 1);
        LocalDate to = LocalDate.of(2021, 1, 31);
        when(transactionRepository.findForStatement(eq("A1"), any(), any())).thenReturn(List.of(
            transactionEntity(TransactionType.FUND_TRANSFER, "-100.00", LocalDateTime.of(2021, 1, 10, 9, 0)),
            transactionEntity(TransactionType.FUND_TRANSFER, "-25.50", LocalDateTime.of(2021, 1, 20, 15, 0))
        ));

        StatementSummaryResponse response = transactionService.getStatementSummary("A1", from, to);

        assertEquals("A1", response.getAccountNumber());
        assertEquals(from, response.getFrom());
        assertEquals(to, response.getTo());
        assertEquals(1, response.getTotals().size());
        TransactionTypeTotal line = response.getTotals().get(0);
        assertEquals(TransactionType.FUND_TRANSFER, line.getTransactionType());
        assertEquals(new BigDecimal("-125.50"), line.getTotalAmount());
        assertEquals(2L, line.getTransactionCount());
        assertEquals(new BigDecimal("-125.50"), response.getNetTotal());
    }

    @Test
    void getStatementSummary_multipleTypes_aggregatedInDeterministicOrder() {
        accountExists("A1");
        LocalDate from = LocalDate.of(2021, 1, 1);
        LocalDate to = LocalDate.of(2021, 1, 31);
        //intentionally unsorted insertion order to prove output ordering is deterministic
        when(transactionRepository.findForStatement(eq("A1"), any(), any())).thenReturn(List.of(
            transactionEntity(TransactionType.UTILITY_PAYMENT, "-50.00", LocalDateTime.of(2021, 1, 5, 8, 0)),
            transactionEntity(TransactionType.FUND_TRANSFER, "-100.00", LocalDateTime.of(2021, 1, 10, 9, 0)),
            transactionEntity(TransactionType.FUND_TRANSFER, "200.00", LocalDateTime.of(2021, 1, 12, 9, 0)),
            transactionEntity(TransactionType.UTILITY_PAYMENT, "-20.00", LocalDateTime.of(2021, 1, 15, 8, 0))
        ));

        StatementSummaryResponse response = transactionService.getStatementSummary("A1", from, to);

        assertEquals(2, response.getTotals().size());
        //deterministic alphabetical ordering by transaction type name
        assertEquals(TransactionType.FUND_TRANSFER, response.getTotals().get(0).getTransactionType());
        assertEquals(TransactionType.UTILITY_PAYMENT, response.getTotals().get(1).getTransactionType());

        assertEquals(new BigDecimal("100.00"), response.getTotals().get(0).getTotalAmount());
        assertEquals(2L, response.getTotals().get(0).getTransactionCount());
        assertEquals(new BigDecimal("-70.00"), response.getTotals().get(1).getTotalAmount());
        assertEquals(2L, response.getTotals().get(1).getTransactionCount());

        assertEquals(new BigDecimal("30.00"), response.getNetTotal());
    }

    @Test
    void getStatementSummary_exactMonetaryTotals() {
        accountExists("A1");
        when(transactionRepository.findForStatement(eq("A1"), any(), any())).thenReturn(List.of(
            transactionEntity(TransactionType.FUND_TRANSFER, "0.10", LocalDateTime.of(2021, 1, 2, 9, 0)),
            transactionEntity(TransactionType.FUND_TRANSFER, "0.20", LocalDateTime.of(2021, 1, 3, 9, 0))
        ));

        StatementSummaryResponse response = transactionService.getStatementSummary(
            "A1", LocalDate.of(2021, 1, 1), LocalDate.of(2021, 1, 31));

        //exact BigDecimal arithmetic: 0.10 + 0.20 == 0.30 (no binary-float drift), scale preserved
        assertEquals(new BigDecimal("0.30"), response.getTotals().get(0).getTotalAmount());
        assertEquals(new BigDecimal("0.30"), response.getNetTotal());
    }

    @Test
    void getStatementSummary_emptyResult() {
        accountExists("A1");
        when(transactionRepository.findForStatement(eq("A1"), any(), any())).thenReturn(Collections.emptyList());

        StatementSummaryResponse response = transactionService.getStatementSummary(
            "A1", LocalDate.of(2021, 1, 1), LocalDate.of(2021, 1, 31));

        assertTrue(response.getTotals().isEmpty());
        assertEquals(BigDecimal.ZERO, response.getNetTotal());
        assertEquals("A1", response.getAccountNumber());
    }

    @Test
    void getStatementSummary_usesInclusiveBoundaryWindow() {
        accountExists("A1");
        LocalDate from = LocalDate.of(2021, 1, 1);
        LocalDate to = LocalDate.of(2021, 1, 31);
        //transactions timestamped exactly at both inclusive ends of the period
        LocalDateTime atStart = from.atStartOfDay();
        LocalDateTime atEnd = to.atTime(LocalTime.MAX);
        when(transactionRepository.findForStatement(eq("A1"), any(), any())).thenReturn(List.of(
            transactionEntity(TransactionType.FUND_TRANSFER, "-100.00", atStart),
            transactionEntity(TransactionType.FUND_TRANSFER, "-40.00", atEnd)
        ));

        StatementSummaryResponse response = transactionService.getStatementSummary("A1", from, to);

        ArgumentCaptor<LocalDateTime> startCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> endCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(transactionRepository).findForStatement(eq("A1"), startCaptor.capture(), endCaptor.capture());

        //window must be inclusive on both ends: start-of-first-day .. last instant of last day
        assertEquals(from.atStartOfDay(), startCaptor.getValue());
        assertEquals(to.atTime(LocalTime.MAX), endCaptor.getValue());

        //both boundary transactions are counted
        assertEquals(2L, response.getTotals().get(0).getTransactionCount());
        assertEquals(new BigDecimal("-140.00"), response.getTotals().get(0).getTotalAmount());
    }

    @Test
    void getStatementSummary_invalidRange() {
        accountExists("A1");
        LocalDate from = LocalDate.of(2021, 2, 1);
        LocalDate to = LocalDate.of(2021, 1, 1);
        SimpleBankingGlobalException ex = assertThrows(SimpleBankingGlobalException.class,
            () -> transactionService.getStatementSummary("A1", from, to));
        assertEquals(GlobalErrorCode.INVALID_DATE_RANGE, ex.getCode());
        verify(transactionRepository, never()).findForStatement(any(), any(), any());
    }

    @Test
    void getStatementSummary_accountNotFound() {
        when(accountService.readBankAccount("A1")).thenThrow(EntityNotFoundException.class);
        assertThrows(EntityNotFoundException.class,
            () -> transactionService.getStatementSummary("A1", LocalDate.of(2021, 1, 1), LocalDate.of(2021, 1, 31)));
        verify(transactionRepository, never()).findForStatement(any(), any(), any());
    }
}

