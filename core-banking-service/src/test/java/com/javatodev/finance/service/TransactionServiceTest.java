package com.javatodev.finance.service;

import com.javatodev.finance.exception.EntityNotFoundException;
import com.javatodev.finance.exception.GlobalErrorCode;
import com.javatodev.finance.exception.InsufficientFundsException;
import com.javatodev.finance.exception.ValidationException;
import com.javatodev.finance.model.TransactionType;
import com.javatodev.finance.model.dto.BankAccount;
import com.javatodev.finance.model.dto.UtilityAccount;
import com.javatodev.finance.model.dto.request.FundTransferRequest;
import com.javatodev.finance.model.dto.request.UtilityPaymentRequest;
import com.javatodev.finance.model.dto.response.FundTransferResponse;
import com.javatodev.finance.model.dto.response.TransactionResponse;
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
    void getAccountTransactions_noDates_returnsAllOrderedAndMapped() {
        BankAccount account = new BankAccount();
        account.setNumber("A1");
        when(accountService.readBankAccount("A1")).thenReturn(account);

        TransactionEntity entity = TransactionEntity.builder()
            .transactionId("T1")
            .referenceNumber("REF1")
            .transactionType(TransactionType.FUND_TRANSFER)
            .amount(BigDecimal.valueOf(-100))
            .createdAt(LocalDateTime.of(2026, 1, 10, 9, 0))
            .build();
        when(transactionRepository.findByAccount_NumberOrderByCreatedAtDesc("A1"))
            .thenReturn(List.of(entity));

        List<TransactionResponse> result = transactionService.getAccountTransactions("A1", null, null);

        assertEquals(1, result.size());
        TransactionResponse dto = result.get(0);
        assertEquals("T1", dto.getTransactionId());
        assertEquals("REF1", dto.getReferenceNumber());
        assertEquals(TransactionType.FUND_TRANSFER, dto.getTransactionType());
        assertEquals(BigDecimal.valueOf(-100), dto.getAmount());
        assertEquals(LocalDateTime.of(2026, 1, 10, 9, 0), dto.getCreatedAt());
        verify(transactionRepository).findByAccount_NumberOrderByCreatedAtDesc("A1");
        verify(transactionRepository, never())
            .findByAccount_NumberAndCreatedAtBetweenOrderByCreatedAtDesc(anyString(), any(), any());
    }

    @Test
    void getAccountTransactions_withRange_usesInclusiveDayBounds() {
        BankAccount account = new BankAccount();
        account.setNumber("A1");
        when(accountService.readBankAccount("A1")).thenReturn(account);
        when(transactionRepository.findByAccount_NumberAndCreatedAtBetweenOrderByCreatedAtDesc(
            anyString(), any(), any())).thenReturn(Collections.emptyList());

        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 1, 31);
        transactionService.getAccountTransactions("A1", from, to);

        ArgumentCaptor<LocalDateTime> startCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> endCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(transactionRepository).findByAccount_NumberAndCreatedAtBetweenOrderByCreatedAtDesc(
            eq("A1"), startCaptor.capture(), endCaptor.capture());

        //start-of-day .. end-of-day so both boundary days are inclusive (AC3)
        assertEquals(from.atStartOfDay(), startCaptor.getValue());
        assertEquals(to.atTime(LocalTime.MAX), endCaptor.getValue());
    }

    @Test
    void getAccountTransactions_emptyResult_returnsEmptyList() {
        BankAccount account = new BankAccount();
        account.setNumber("A1");
        when(accountService.readBankAccount("A1")).thenReturn(account);
        when(transactionRepository.findByAccount_NumberOrderByCreatedAtDesc("A1"))
            .thenReturn(Collections.emptyList());

        List<TransactionResponse> result = transactionService.getAccountTransactions("A1", null, null);

        assertTrue(result.isEmpty());
    }

    @Test
    void getAccountTransactions_unknownAccount_throwsEntityNotFound() {
        when(accountService.readBankAccount("NOPE")).thenThrow(EntityNotFoundException.class);

        assertThrows(EntityNotFoundException.class,
            () -> transactionService.getAccountTransactions("NOPE", null, null));
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void getAccountTransactions_partialRange_throwsValidationException() {
        BankAccount account = new BankAccount();
        account.setNumber("A1");
        when(accountService.readBankAccount("A1")).thenReturn(account);

        assertThrows(ValidationException.class,
            () -> transactionService.getAccountTransactions("A1", LocalDate.of(2026, 1, 1), null));
        verifyNoInteractions(transactionRepository);
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

