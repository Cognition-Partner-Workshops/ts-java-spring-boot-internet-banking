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
import com.javatodev.finance.exception.InvalidStatementPeriodException;
import com.javatodev.finance.exception.UnsupportedExportFormatException;
import com.javatodev.finance.model.StatementFormat;
import com.javatodev.finance.model.dto.response.StatementExportResult;
import com.javatodev.finance.model.dto.response.StatementLineDto;
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

    // ---------- Statement export ----------

    private static final String ACCT = "100015003000";

    private TransactionEntity txn(Long id, String transactionId, TransactionType type,
                                  BigDecimal amount, String referenceNumber, LocalDateTime timestamp) {
        BankAccountEntity account = new BankAccountEntity();
        account.setNumber(ACCT);
        return TransactionEntity.builder()
            .id(id)
            .transactionId(transactionId)
            .transactionType(type)
            .amount(amount)
            .referenceNumber(referenceNumber)
            .timestamp(timestamp)
            .account(account)
            .build();
    }

    private void stubAccountExists() {
        BankAccount account = new BankAccount();
        account.setNumber(ACCT);
        when(accountService.readBankAccount(ACCT)).thenReturn(account);
    }

    private void stubTransactions(TransactionEntity... transactions) {
        when(transactionRepository.findByAccount_NumberOrderByTimestampDescIdDesc(ACCT))
            .thenReturn(List.of(transactions));
    }

    @Test
    void exportStatement_json_happyPath_typedProjection() {
        stubAccountExists();
        stubTransactions(
            txn(2L, "TX2", TransactionType.UTILITY_PAYMENT, BigDecimal.valueOf(-50), "REF-2",
                LocalDateTime.of(2024, 5, 2, 10, 0)),
            txn(1L, "TX1", TransactionType.FUND_TRANSFER, BigDecimal.valueOf(100), "REF-1",
                LocalDateTime.of(2024, 5, 1, 9, 0)));

        StatementExportResult result = transactionService.exportStatement(ACCT, "JSON", null, null);

        assertEquals(StatementFormat.JSON, result.format());
        assertNull(result.csvBody());
        assertEquals(ACCT, result.data().accountNumber());
        assertEquals(2, result.data().count());
        assertEquals(2, result.data().lines().size());

        StatementLineDto first = result.data().lines().get(0);
        assertEquals("TX2", first.transactionId());
        assertEquals(TransactionType.UTILITY_PAYMENT, first.transactionType());
        assertEquals(0, BigDecimal.valueOf(-50).compareTo(first.amount()));
        assertEquals("REF-2", first.referenceNumber());
        assertEquals(ACCT, first.accountNumber());
        assertEquals(LocalDateTime.of(2024, 5, 2, 10, 0), first.timestamp());
    }

    @Test
    void exportStatement_json_empty() {
        stubAccountExists();
        stubTransactions();

        StatementExportResult result = transactionService.exportStatement(ACCT, "JSON", null, null);

        assertEquals(0, result.data().count());
        assertTrue(result.data().lines().isEmpty());
    }

    @Test
    void exportStatement_csv_happyPath_headerAndColumnOrder() {
        stubAccountExists();
        stubTransactions(
            txn(1L, "TX1", TransactionType.FUND_TRANSFER, new BigDecimal("100.00"), "REF-1",
                LocalDateTime.of(2024, 5, 1, 9, 0)));

        StatementExportResult result = transactionService.exportStatement(ACCT, "CSV", null, null);

        assertEquals(StatementFormat.CSV, result.format());
        String[] rows = result.csvBody().split("\r\n", -1);
        assertEquals("timestamp,transactionId,transactionType,amount,referenceNumber,accountNumber", rows[0]);
        assertEquals("2024-05-01T09:00,TX1,FUND_TRANSFER,100.00,REF-1," + ACCT, rows[1]);
    }

    @Test
    void exportStatement_csv_empty_headerOnly() {
        stubAccountExists();
        stubTransactions();

        StatementExportResult result = transactionService.exportStatement(ACCT, "CSV", null, null);

        assertEquals("timestamp,transactionId,transactionType,amount,referenceNumber,accountNumber\r\n",
            result.csvBody());
    }

    @Test
    void exportStatement_orderingMostRecentFirst_withIdTieBreak() {
        stubAccountExists();
        LocalDateTime shared = LocalDateTime.of(2024, 5, 3, 12, 0);
        // Supplied deliberately out of order; service must sort deterministically.
        stubTransactions(
            txn(5L, "OLD", TransactionType.FUND_TRANSFER, BigDecimal.ONE, "R",
                LocalDateTime.of(2024, 5, 1, 0, 0)),
            txn(10L, "TIE_LOW_ID", TransactionType.FUND_TRANSFER, BigDecimal.ONE, "R", shared),
            txn(11L, "TIE_HIGH_ID", TransactionType.FUND_TRANSFER, BigDecimal.ONE, "R", shared));

        StatementExportResult result = transactionService.exportStatement(ACCT, "JSON", null, null);

        List<StatementLineDto> lines = result.data().lines();
        assertEquals("TIE_HIGH_ID", lines.get(0).transactionId());
        assertEquals("TIE_LOW_ID", lines.get(1).transactionId());
        assertEquals("OLD", lines.get(2).transactionId());
    }

    @Test
    void exportStatement_dateRange_isInclusiveOnBothBounds() {
        stubAccountExists();
        stubTransactions(
            txn(1L, "BEFORE", TransactionType.FUND_TRANSFER, BigDecimal.ONE, "R",
                LocalDateTime.of(2024, 4, 30, 23, 59)),
            txn(2L, "ON_FROM", TransactionType.FUND_TRANSFER, BigDecimal.ONE, "R",
                LocalDateTime.of(2024, 5, 1, 0, 0)),
            txn(3L, "MIDDLE", TransactionType.FUND_TRANSFER, BigDecimal.ONE, "R",
                LocalDateTime.of(2024, 5, 2, 12, 0)),
            txn(4L, "ON_TO", TransactionType.FUND_TRANSFER, BigDecimal.ONE, "R",
                LocalDateTime.of(2024, 5, 3, 23, 59)),
            txn(5L, "AFTER", TransactionType.FUND_TRANSFER, BigDecimal.ONE, "R",
                LocalDateTime.of(2024, 5, 4, 0, 0)));

        StatementExportResult result = transactionService.exportStatement(
            ACCT, "JSON", LocalDate.of(2024, 5, 1), LocalDate.of(2024, 5, 3));

        List<String> ids = result.data().lines().stream().map(StatementLineDto::transactionId).toList();
        assertEquals(3, ids.size());
        assertTrue(ids.containsAll(List.of("ON_FROM", "MIDDLE", "ON_TO")));
        assertFalse(ids.contains("BEFORE"));
        assertFalse(ids.contains("AFTER"));
    }

    @Test
    void exportStatement_csv_escapesSpecialCharacters() {
        stubAccountExists();
        stubTransactions(
            txn(1L, "TX1", TransactionType.FUND_TRANSFER, new BigDecimal("10.00"),
                "a,b \"quoted\"\nline", LocalDateTime.of(2024, 5, 1, 9, 0)));

        StatementExportResult result = transactionService.exportStatement(ACCT, "CSV", null, null);

        String[] rows = result.csvBody().split("\r\n", -1);
        // The reference field contains comma, quote and newline -> quoted, quotes doubled,
        // and the embedded newline keeps the record spanning two physical lines.
        assertTrue(result.csvBody().contains("\"a,b \"\"quoted\"\"\nline\""),
            "escaped field not found in: " + result.csvBody());
        assertEquals("timestamp,transactionId,transactionType,amount,referenceNumber,accountNumber", rows[0]);
        assertTrue(rows[1].startsWith("2024-05-01T09:00,TX1,FUND_TRANSFER,10.00,\"a,b \"\"quoted\"\""));
    }

    @Test
    void exportStatement_defaultsToJson_whenFormatBlank() {
        stubAccountExists();
        stubTransactions();

        StatementExportResult result = transactionService.exportStatement(ACCT, "", null, null);

        assertEquals(StatementFormat.JSON, result.format());
    }

    @Test
    void exportStatement_formatIsCaseInsensitive() {
        stubAccountExists();
        stubTransactions();

        assertEquals(StatementFormat.CSV, transactionService.exportStatement(ACCT, "csv", null, null).format());
    }

    @Test
    void exportStatement_invalidPeriod_throws() {
        assertThrows(InvalidStatementPeriodException.class, () -> transactionService.exportStatement(
            ACCT, "JSON", LocalDate.of(2024, 5, 10), LocalDate.of(2024, 5, 1)));
    }

    @Test
    void exportStatement_accountNotFound_throws() {
        when(accountService.readBankAccount(ACCT)).thenThrow(EntityNotFoundException.class);
        assertThrows(EntityNotFoundException.class,
            () -> transactionService.exportStatement(ACCT, "JSON", null, null));
    }

    @Test
    void exportStatement_unsupportedFormat_throws() {
        assertThrows(UnsupportedExportFormatException.class,
            () -> transactionService.exportStatement(ACCT, "XML", null, null));
    }

    @Test
    void fundTransfer_setsTimestampOnWrittenTransactions() {
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

        transactionService.fundTransfer(request);

        ArgumentCaptor<TransactionEntity> captor = ArgumentCaptor.forClass(TransactionEntity.class);
        verify(transactionRepository, times(2)).save(captor.capture());
        assertTrue(captor.getAllValues().stream().allMatch(t -> t.getTimestamp() != null));
    }
}

