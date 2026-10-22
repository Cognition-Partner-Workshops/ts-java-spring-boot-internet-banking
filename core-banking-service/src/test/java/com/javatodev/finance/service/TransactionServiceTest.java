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
    void validateBalance_throwsException() {
        BankAccount account = new BankAccount();
        account.setNumber("A1");
        account.setActualBalance(BigDecimal.valueOf(50));
        when(accountService.readBankAccount("A1")).thenReturn(account);
        assertThrows(InsufficientFundsException.class, () -> {
            transactionService.fundTransfer(new FundTransferRequest("A1", "A2", BigDecimal.valueOf(100)));
        });
    }

    private TransactionEntity transactionEntity(Long id, String accountNumber, BigDecimal amount, LocalDateTime timestamp) {
        BankAccountEntity account = new BankAccountEntity();
        account.setNumber(accountNumber);
        return TransactionEntity.builder()
            .id(id)
            .transactionType(TransactionType.FUND_TRANSFER)
            .referenceNumber("REF" + id)
            .transactionId("TXN" + id)
            .amount(amount)
            .timestamp(timestamp)
            .account(account)
            .build();
    }

    @Test
    void getTransactionHistory_happyPath_mapsFieldsAndPreservesOrder() {
        when(accountService.readBankAccount("A1")).thenReturn(new BankAccount());
        TransactionEntity newer = transactionEntity(2L, "A1", BigDecimal.valueOf(-100), LocalDateTime.of(2026, 7, 10, 12, 0));
        TransactionEntity older = transactionEntity(1L, "A1", BigDecimal.valueOf(50), LocalDateTime.of(2026, 7, 9, 12, 0));
        when(transactionRepository.findByAccount_NumberOrderByTimestampDescIdDesc("A1"))
            .thenReturn(List.of(newer, older));

        List<TransactionResponse> result = transactionService.getTransactionHistory("A1", null, null);

        assertEquals(2, result.size());
        assertEquals(2L, result.get(0).getId());
        assertEquals("A1", result.get(0).getAccountNumber());
        assertEquals(BigDecimal.valueOf(-100), result.get(0).getAmount());
        assertEquals(TransactionType.FUND_TRANSFER, result.get(0).getTransactionType());
        assertEquals(LocalDateTime.of(2026, 7, 10, 12, 0), result.get(0).getTimestamp());
        // service preserves the repository's most-recent-first ordering
        assertEquals(1L, result.get(1).getId());
    }

    @Test
    void getTransactionHistory_emptyResult_returnsEmptyListNotNull() {
        when(accountService.readBankAccount("A1")).thenReturn(new BankAccount());
        when(transactionRepository.findByAccount_NumberOrderByTimestampDescIdDesc("A1"))
            .thenReturn(Collections.emptyList());

        List<TransactionResponse> result = transactionService.getTransactionHistory("A1", null, null);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getTransactionHistory_accountNotFound_propagatesEntityNotFound() {
        when(accountService.readBankAccount("UNKNOWN")).thenThrow(EntityNotFoundException.class);

        assertThrows(EntityNotFoundException.class,
            () -> transactionService.getTransactionHistory("UNKNOWN", null, null));
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void getTransactionHistory_bothBounds_usesInclusiveWindow() {
        when(accountService.readBankAccount("A1")).thenReturn(new BankAccount());
        ArgumentCaptor<LocalDateTime> fromCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> toCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        when(transactionRepository.findByAccount_NumberAndTimestampBetweenOrderByTimestampDescIdDesc(
            eq("A1"), fromCaptor.capture(), toCaptor.capture())).thenReturn(Collections.emptyList());

        transactionService.getTransactionHistory("A1", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 10));

        // inclusive lower bound = start of day, inclusive upper bound = end of the "to" day
        assertEquals(LocalDate.of(2026, 7, 1).atStartOfDay(), fromCaptor.getValue());
        assertEquals(LocalDate.of(2026, 7, 10).atTime(LocalTime.MAX), toCaptor.getValue());
    }

    @Test
    void getTransactionHistory_fromOnly_usesGreaterThanEqual() {
        when(accountService.readBankAccount("A1")).thenReturn(new BankAccount());
        ArgumentCaptor<LocalDateTime> fromCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        when(transactionRepository.findByAccount_NumberAndTimestampGreaterThanEqualOrderByTimestampDescIdDesc(
            eq("A1"), fromCaptor.capture())).thenReturn(Collections.emptyList());

        transactionService.getTransactionHistory("A1", LocalDate.of(2026, 7, 1), null);

        assertEquals(LocalDate.of(2026, 7, 1).atStartOfDay(), fromCaptor.getValue());
        verify(transactionRepository, never()).findByAccount_NumberOrderByTimestampDescIdDesc(anyString());
    }

    @Test
    void getTransactionHistory_toOnly_usesLessThanEqualWithEndOfDay() {
        when(accountService.readBankAccount("A1")).thenReturn(new BankAccount());
        ArgumentCaptor<LocalDateTime> toCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        when(transactionRepository.findByAccount_NumberAndTimestampLessThanEqualOrderByTimestampDescIdDesc(
            eq("A1"), toCaptor.capture())).thenReturn(Collections.emptyList());

        transactionService.getTransactionHistory("A1", null, LocalDate.of(2026, 7, 10));

        assertEquals(LocalDate.of(2026, 7, 10).atTime(LocalTime.MAX), toCaptor.getValue());
    }
}

