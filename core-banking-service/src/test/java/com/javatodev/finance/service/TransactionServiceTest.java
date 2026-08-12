package com.javatodev.finance.service;

import com.javatodev.finance.exception.EntityNotFoundException;
import com.javatodev.finance.exception.GlobalErrorCode;
import com.javatodev.finance.exception.InsufficientFundsException;
import com.javatodev.finance.model.TransactionType;
import com.javatodev.finance.model.dto.BankAccount;
import com.javatodev.finance.model.dto.TransactionHistoryDto;
import com.javatodev.finance.model.dto.UtilityAccount;
import com.javatodev.finance.model.dto.request.FundTransferRequest;
import com.javatodev.finance.model.dto.request.UtilityPaymentRequest;
import com.javatodev.finance.model.dto.response.FundTransferResponse;
import com.javatodev.finance.model.dto.response.UtilityPaymentResponse;
import com.javatodev.finance.model.entity.BankAccountEntity;
import com.javatodev.finance.model.entity.TransactionEntity;
import com.javatodev.finance.repository.BankAccountRepository;
import com.javatodev.finance.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
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

    private TransactionEntity transaction(long id, TransactionType type, String reference, BigDecimal amount, LocalDateTime createdAt) {
        return TransactionEntity.builder()
            .id(id)
            .transactionType(type)
            .referenceNumber(reference)
            .transactionId("TX" + id)
            .amount(amount)
            .createdAt(createdAt)
            .build();
    }

    @Test
    void getTransactionHistory_happyPath() {
        LocalDateTime base = LocalDateTime.of(2026, 8, 10, 12, 0);
        when(transactionRepository.findByAccount_NumberOrderByCreatedAtDesc("A1")).thenReturn(List.of(
            transaction(2L, TransactionType.UTILITY_PAYMENT, "REF2", BigDecimal.valueOf(-50), base.plusHours(1)),
            transaction(1L, TransactionType.FUND_TRANSFER, "REF1", BigDecimal.valueOf(100), base)
        ));

        List<TransactionHistoryDto> history = transactionService.getTransactionHistory("A1", null, null, null, 0, 20);

        assertEquals(2, history.size());
        assertEquals(2L, history.get(0).getId());
        assertEquals(TransactionType.UTILITY_PAYMENT, history.get(0).getType());
        assertEquals("REF2", history.get(0).getReferenceNumber());
        assertEquals(BigDecimal.valueOf(-50), history.get(0).getAmount());
        assertEquals(base.plusHours(1), history.get(0).getTimestamp());
    }

    @Test
    void getTransactionHistory_emptyResult() {
        when(transactionRepository.findByAccount_NumberOrderByCreatedAtDesc("A1")).thenReturn(Collections.emptyList());

        List<TransactionHistoryDto> history = transactionService.getTransactionHistory("A1", null, null, null, 0, 20);

        assertTrue(history.isEmpty());
    }

    @Test
    void getTransactionHistory_mostRecentFirstOrdering() {
        LocalDateTime base = LocalDateTime.of(2026, 8, 10, 12, 0);
        when(transactionRepository.findByAccount_NumberOrderByCreatedAtDesc("A1")).thenReturn(List.of(
            transaction(3L, TransactionType.FUND_TRANSFER, "REF3", BigDecimal.valueOf(30), base.plusDays(2)),
            transaction(2L, TransactionType.FUND_TRANSFER, "REF2", BigDecimal.valueOf(20), base.plusDays(1)),
            transaction(1L, TransactionType.FUND_TRANSFER, "REF1", BigDecimal.valueOf(10), base)
        ));

        List<TransactionHistoryDto> history = transactionService.getTransactionHistory("A1", null, null, null, 0, 20);

        assertEquals(List.of(3L, 2L, 1L), history.stream().map(TransactionHistoryDto::getId).toList());
        assertTrue(history.get(0).getTimestamp().isAfter(history.get(1).getTimestamp()));
        assertTrue(history.get(1).getTimestamp().isAfter(history.get(2).getTimestamp()));
    }

    @Test
    void getTransactionHistory_includesBoundaryTransactions() {
        LocalDateTime from = LocalDateTime.of(2026, 8, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 8, 10, 23, 59, 59);
        when(transactionRepository.findByAccount_NumberOrderByCreatedAtDesc("A1")).thenReturn(List.of(
            transaction(4L, TransactionType.FUND_TRANSFER, "REF4", BigDecimal.valueOf(40), to.plusSeconds(1)),
            transaction(3L, TransactionType.FUND_TRANSFER, "REF3", BigDecimal.valueOf(30), to),
            transaction(2L, TransactionType.FUND_TRANSFER, "REF2", BigDecimal.valueOf(20), from.plusDays(3)),
            transaction(1L, TransactionType.FUND_TRANSFER, "REF1", BigDecimal.valueOf(10), from),
            transaction(0L, TransactionType.FUND_TRANSFER, "REF0", BigDecimal.valueOf(5), from.minusSeconds(1))
        ));

        List<TransactionHistoryDto> history = transactionService.getTransactionHistory("A1", from, to, null, 0, 20);

        assertEquals(List.of(3L, 2L, 1L), history.stream().map(TransactionHistoryDto::getId).toList());
    }

    @Test
    void getTransactionHistory_typeFilter() {
        LocalDateTime base = LocalDateTime.of(2026, 8, 10, 12, 0);
        when(transactionRepository.findByAccount_NumberOrderByCreatedAtDesc("A1")).thenReturn(List.of(
            transaction(2L, TransactionType.UTILITY_PAYMENT, "REF2", BigDecimal.valueOf(-50), base.plusHours(1)),
            transaction(1L, TransactionType.FUND_TRANSFER, "REF1", BigDecimal.valueOf(100), base)
        ));

        List<TransactionHistoryDto> history = transactionService.getTransactionHistory("A1", null, null, TransactionType.UTILITY_PAYMENT, 0, 20);

        assertEquals(1, history.size());
        assertEquals(2L, history.get(0).getId());
    }

    @Test
    void getTransactionHistory_pagination() {
        LocalDateTime base = LocalDateTime.of(2026, 8, 10, 12, 0);
        when(transactionRepository.findByAccount_NumberOrderByCreatedAtDesc("A1")).thenReturn(List.of(
            transaction(3L, TransactionType.FUND_TRANSFER, "REF3", BigDecimal.valueOf(30), base.plusDays(2)),
            transaction(2L, TransactionType.FUND_TRANSFER, "REF2", BigDecimal.valueOf(20), base.plusDays(1)),
            transaction(1L, TransactionType.FUND_TRANSFER, "REF1", BigDecimal.valueOf(10), base)
        ));

        List<TransactionHistoryDto> page0 = transactionService.getTransactionHistory("A1", null, null, null, 0, 2);
        List<TransactionHistoryDto> page1 = transactionService.getTransactionHistory("A1", null, null, null, 1, 2);
        List<TransactionHistoryDto> page2 = transactionService.getTransactionHistory("A1", null, null, null, 2, 2);

        assertEquals(List.of(3L, 2L), page0.stream().map(TransactionHistoryDto::getId).toList());
        assertEquals(List.of(1L), page1.stream().map(TransactionHistoryDto::getId).toList());
        assertTrue(page2.isEmpty());
    }
}

