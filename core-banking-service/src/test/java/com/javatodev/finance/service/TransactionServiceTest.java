package com.javatodev.finance.service;

import com.javatodev.finance.exception.EntityNotFoundException;
import com.javatodev.finance.exception.GlobalErrorCode;
import com.javatodev.finance.exception.InsufficientFundsException;
import com.javatodev.finance.model.TransactionType;
import com.javatodev.finance.model.dto.BankAccount;
import com.javatodev.finance.model.dto.UtilityAccount;
import com.javatodev.finance.model.dto.request.FundTransferRequest;
import com.javatodev.finance.model.dto.request.UtilityPaymentRequest;
import com.javatodev.finance.model.dto.response.FundTransferResponse;
import com.javatodev.finance.model.dto.response.UtilityPaymentResponse;
import com.javatodev.finance.model.entity.BankAccountEntity;
import com.javatodev.finance.model.entity.TransactionEntity;
import com.javatodev.finance.repository.BankAccountRepository;
import com.javatodev.finance.repository.TransactionRepository;
import com.javatodev.finance.model.dto.TransactionHistoryDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

    private TransactionEntity transaction(Long id, TransactionType type, String reference, BigDecimal amount, LocalDateTime createdAt) {
        return TransactionEntity.builder()
            .id(id)
            .transactionType(type)
            .referenceNumber(reference)
            .transactionId("T" + id)
            .amount(amount)
            .createdAt(createdAt)
            .build();
    }

    @Test
    void getTransactionHistory_happyPath() {
        TransactionEntity newer = transaction(2L, TransactionType.FUND_TRANSFER, "A2", BigDecimal.valueOf(-100), LocalDateTime.of(2026, 7, 10, 9, 0));
        TransactionEntity older = transaction(1L, TransactionType.UTILITY_PAYMENT, "REF1", BigDecimal.valueOf(-50), LocalDateTime.of(2026, 7, 1, 9, 0));
        when(transactionRepository.findByAccount_NumberOrderByCreatedAtDesc("A1")).thenReturn(List.of(newer, older));

        Page<TransactionHistoryDto> result = transactionService.getTransactionHistory("A1", null, null, null, PageRequest.of(0, 10));

        assertEquals(2, result.getTotalElements());
        assertEquals(2, result.getContent().size());
        // most-recent-first ordering preserved from the repository
        assertEquals(2L, result.getContent().get(0).getId());
        assertEquals(1L, result.getContent().get(1).getId());
        TransactionHistoryDto first = result.getContent().get(0);
        assertEquals(TransactionType.FUND_TRANSFER, first.getTransactionType());
        assertEquals("A2", first.getReferenceNumber());
        assertEquals(BigDecimal.valueOf(-100), first.getAmount());
        assertEquals(LocalDateTime.of(2026, 7, 10, 9, 0), first.getCreatedAt());
    }

    @Test
    void getTransactionHistory_emptyResult() {
        when(transactionRepository.findByAccount_NumberOrderByCreatedAtDesc("A1")).thenReturn(List.of());

        Page<TransactionHistoryDto> result = transactionService.getTransactionHistory("A1", null, null, null, PageRequest.of(0, 10));

        assertTrue(result.getContent().isEmpty());
        assertEquals(0, result.getTotalElements());
    }

    @Test
    void getTransactionHistory_dateRangeFilteringInclusive() {
        TransactionEntity beforeRange = transaction(1L, TransactionType.FUND_TRANSFER, "R1", BigDecimal.TEN, LocalDateTime.of(2026, 6, 30, 23, 0));
        TransactionEntity onFromBoundary = transaction(2L, TransactionType.FUND_TRANSFER, "R2", BigDecimal.TEN, LocalDateTime.of(2026, 7, 1, 0, 0));
        TransactionEntity inRange = transaction(3L, TransactionType.FUND_TRANSFER, "R3", BigDecimal.TEN, LocalDateTime.of(2026, 7, 15, 12, 0));
        TransactionEntity onToBoundary = transaction(4L, TransactionType.FUND_TRANSFER, "R4", BigDecimal.TEN, LocalDateTime.of(2026, 7, 31, 23, 59, 59));
        TransactionEntity afterRange = transaction(5L, TransactionType.FUND_TRANSFER, "R5", BigDecimal.TEN, LocalDateTime.of(2026, 8, 1, 0, 0));
        when(transactionRepository.findByAccount_NumberOrderByCreatedAtDesc("A1"))
            .thenReturn(List.of(afterRange, onToBoundary, inRange, onFromBoundary, beforeRange));

        Page<TransactionHistoryDto> result = transactionService.getTransactionHistory(
            "A1", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31), null, PageRequest.of(0, 10));

        assertEquals(3, result.getTotalElements());
        assertEquals(List.of(4L, 3L, 2L), result.getContent().stream().map(TransactionHistoryDto::getId).toList());
    }

    @Test
    void getTransactionHistory_typeFiltering() {
        TransactionEntity transfer = transaction(1L, TransactionType.FUND_TRANSFER, "R1", BigDecimal.TEN, LocalDateTime.of(2026, 7, 10, 9, 0));
        TransactionEntity utility = transaction(2L, TransactionType.UTILITY_PAYMENT, "R2", BigDecimal.TEN, LocalDateTime.of(2026, 7, 9, 9, 0));
        when(transactionRepository.findByAccount_NumberOrderByCreatedAtDesc("A1")).thenReturn(List.of(transfer, utility));

        Page<TransactionHistoryDto> result = transactionService.getTransactionHistory(
            "A1", null, null, TransactionType.UTILITY_PAYMENT, PageRequest.of(0, 10));

        assertEquals(1, result.getTotalElements());
        assertEquals(TransactionType.UTILITY_PAYMENT, result.getContent().get(0).getTransactionType());
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
}

