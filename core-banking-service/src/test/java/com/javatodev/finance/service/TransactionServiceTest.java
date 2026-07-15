package com.javatodev.finance.service;

import com.javatodev.finance.exception.EntityNotFoundException;
import com.javatodev.finance.exception.GlobalErrorCode;
import com.javatodev.finance.exception.InsufficientFundsException;
import com.javatodev.finance.exception.InvalidRequestException;
import com.javatodev.finance.model.TransactionType;
import com.javatodev.finance.model.dto.BankAccount;
import com.javatodev.finance.model.dto.UtilityAccount;
import com.javatodev.finance.model.dto.request.FundTransferRequest;
import com.javatodev.finance.model.dto.request.UtilityPaymentRequest;
import com.javatodev.finance.model.dto.response.AccountBalanceResponse;
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

    private BankAccountEntity account(String number, BigDecimal actualBalance) {
        BankAccountEntity entity = new BankAccountEntity();
        entity.setNumber(number);
        entity.setActualBalance(actualBalance);
        return entity;
    }

    private TransactionEntity transaction(BigDecimal amount, LocalDateTime timestamp) {
        return TransactionEntity.builder().amount(amount).timestamp(timestamp).build();
    }

    @Test
    void getBalanceAsOf_happyPath_reversesOnlyTransactionsAfterAsOf() {
        LocalDateTime asOf = LocalDateTime.of(2026, 7, 15, 12, 0, 0);
        when(bankAccountRepository.findByNumber("A1")).thenReturn(Optional.of(account("A1", BigDecimal.valueOf(200))));
        when(transactionRepository.findByAccountNumber("A1")).thenReturn(List.of(
            transaction(BigDecimal.valueOf(30), asOf.minusDays(1)),   // before asOf -> kept
            transaction(BigDecimal.valueOf(50), asOf.plusDays(1)),    // after asOf  -> reversed
            transaction(BigDecimal.valueOf(-20), asOf.plusHours(2))   // after asOf  -> reversed
        ));

        AccountBalanceResponse response = transactionService.getBalanceAsOf("A1", asOf);

        // 200 - (50) - (-20) = 200 - 50 + 20 = 170
        assertEquals(0, BigDecimal.valueOf(170).compareTo(response.getBalance()));
        assertEquals("A1", response.getAccountNumber());
        assertEquals(asOf, response.getAsOf());
    }

    @Test
    void getBalanceAsOf_noTransactions_returnsCurrentBalance() {
        LocalDateTime asOf = LocalDateTime.of(2026, 7, 15, 12, 0, 0);
        when(bankAccountRepository.findByNumber("A1")).thenReturn(Optional.of(account("A1", BigDecimal.valueOf(500))));
        when(transactionRepository.findByAccountNumber("A1")).thenReturn(Collections.emptyList());

        AccountBalanceResponse response = transactionService.getBalanceAsOf("A1", asOf);

        assertEquals(0, BigDecimal.valueOf(500).compareTo(response.getBalance()));
    }

    @Test
    void getBalanceAsOf_boundaryTransactionIsInclusive() {
        LocalDateTime asOf = LocalDateTime.of(2026, 7, 15, 12, 0, 0);
        when(bankAccountRepository.findByNumber("A1")).thenReturn(Optional.of(account("A1", BigDecimal.valueOf(100))));
        when(transactionRepository.findByAccountNumber("A1")).thenReturn(List.of(
            transaction(BigDecimal.valueOf(40), asOf)   // exactly at asOf -> included, NOT reversed
        ));

        AccountBalanceResponse response = transactionService.getBalanceAsOf("A1", asOf);

        // boundary transaction stays reflected in the balance
        assertEquals(0, BigDecimal.valueOf(100).compareTo(response.getBalance()));
    }

    @Test
    void getBalanceAsOf_signedCreditAfterAsOf_lowersHistoricalBalance() {
        LocalDateTime asOf = LocalDateTime.of(2026, 7, 15, 12, 0, 0);
        when(bankAccountRepository.findByNumber("A1")).thenReturn(Optional.of(account("A1", BigDecimal.valueOf(100))));
        when(transactionRepository.findByAccountNumber("A1")).thenReturn(List.of(
            transaction(BigDecimal.valueOf(60), asOf.plusDays(1))   // incoming credit after asOf
        ));

        AccountBalanceResponse response = transactionService.getBalanceAsOf("A1", asOf);

        // credit had not happened yet -> historical balance is lower: 100 - 60 = 40
        assertEquals(0, BigDecimal.valueOf(40).compareTo(response.getBalance()));
    }

    @Test
    void getBalanceAsOf_signedDebitAfterAsOf_raisesHistoricalBalance() {
        LocalDateTime asOf = LocalDateTime.of(2026, 7, 15, 12, 0, 0);
        when(bankAccountRepository.findByNumber("A1")).thenReturn(Optional.of(account("A1", BigDecimal.valueOf(100))));
        when(transactionRepository.findByAccountNumber("A1")).thenReturn(List.of(
            transaction(BigDecimal.valueOf(-60), asOf.plusDays(1))   // outgoing debit after asOf
        ));

        AccountBalanceResponse response = transactionService.getBalanceAsOf("A1", asOf);

        // debit had not happened yet -> historical balance is higher: 100 - (-60) = 160
        assertEquals(0, BigDecimal.valueOf(160).compareTo(response.getBalance()));
    }

    @Test
    void getBalanceAsOf_dateAfterAllTransactions_returnsCurrentBalance() {
        LocalDateTime asOf = LocalDateTime.of(2026, 7, 15, 12, 0, 0);
        when(bankAccountRepository.findByNumber("A1")).thenReturn(Optional.of(account("A1", BigDecimal.valueOf(300))));
        when(transactionRepository.findByAccountNumber("A1")).thenReturn(List.of(
            transaction(BigDecimal.valueOf(10), asOf.minusDays(2)),
            transaction(BigDecimal.valueOf(20), asOf.minusDays(1))
        ));

        AccountBalanceResponse response = transactionService.getBalanceAsOf("A1", asOf);

        assertEquals(0, BigDecimal.valueOf(300).compareTo(response.getBalance()));
    }

    @Test
    void getBalanceAsOf_dateBeforeAllTransactions_returnsDerivedOpeningBalance() {
        LocalDateTime asOf = LocalDateTime.of(2026, 7, 15, 12, 0, 0);
        when(bankAccountRepository.findByNumber("A1")).thenReturn(Optional.of(account("A1", BigDecimal.valueOf(300))));
        when(transactionRepository.findByAccountNumber("A1")).thenReturn(List.of(
            transaction(BigDecimal.valueOf(100), asOf.plusDays(1)),
            transaction(BigDecimal.valueOf(-40), asOf.plusDays(2))
        ));

        AccountBalanceResponse response = transactionService.getBalanceAsOf("A1", asOf);

        // reverse all: 300 - 100 - (-40) = 240 (opening balance)
        assertEquals(0, BigDecimal.valueOf(240).compareTo(response.getBalance()));
    }

    @Test
    void getBalanceAsOf_nullAsOf_throwsInvalidRequest() {
        assertThrows(InvalidRequestException.class, () -> transactionService.getBalanceAsOf("A1", null));
    }

    @Test
    void getBalanceAsOf_accountNotFound_throwsEntityNotFound() {
        LocalDateTime asOf = LocalDateTime.of(2026, 7, 15, 12, 0, 0);
        when(bankAccountRepository.findByNumber("A1")).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> transactionService.getBalanceAsOf("A1", asOf));
    }
}

