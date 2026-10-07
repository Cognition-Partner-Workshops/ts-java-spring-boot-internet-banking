package com.javatodev.finance.service;

import com.javatodev.finance.exception.InsufficientFundsException;
import com.javatodev.finance.model.AccountStatus;
import com.javatodev.finance.model.AccountType;
import com.javatodev.finance.model.TransactionType;
import com.javatodev.finance.model.dto.request.FundTransferRequest;
import com.javatodev.finance.model.dto.request.UtilityPaymentRequest;
import com.javatodev.finance.model.dto.response.FundTransferResponse;
import com.javatodev.finance.model.dto.response.UtilityPaymentResponse;
import com.javatodev.finance.model.entity.BankAccountEntity;
import com.javatodev.finance.model.entity.TransactionEntity;
import com.javatodev.finance.model.entity.UserEntity;
import com.javatodev.finance.model.entity.UtilityAccountEntity;
import com.javatodev.finance.repository.BankAccountRepository;
import com.javatodev.finance.repository.TransactionRepository;
import com.javatodev.finance.repository.UserRepository;
import com.javatodev.finance.repository.UtilityAccountRepository;

import org.bson.Document;
import org.bson.types.Decimal128;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Persistence semantics against a real MongoDB (Testcontainers mongo:7 replica set, not Atlas):
 * numeric _id kept, Decimal128 balances, case-insensitive collation lookups, unique indexes,
 * fund transfer = 2 account updates + 2 transaction docs sharing one transactionId, utility
 * payment = 1 + 1, and rollback when the multi-document transaction fails half-way.
 */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = "spring.data.mongodb.uri=")
class TransactionServiceMongoIT {

    @Container
    @ServiceConnection
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7");

    @Autowired TransactionService transactionService;
    @Autowired AccountService accountService;
    @Autowired UserService userService;
    @Autowired BankAccountRepository bankAccountRepository;
    @Autowired TransactionRepository transactionRepository;
    @Autowired UserRepository userRepository;
    @Autowired UtilityAccountRepository utilityAccountRepository;
    @Autowired MongoTemplate mongoTemplate;
    @Autowired MongoTransactionManager transactionManager;

    @BeforeEach
    void seed() {
        bankAccountRepository.deleteAll();
        transactionRepository.deleteAll();
        userRepository.deleteAll();
        utilityAccountRepository.deleteAll();

        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setFirstName("Sam");
        user.setLastName("Silva");
        user.setEmail("sam@gmail.com");
        user.setIdentificationNumber("808829932V");
        userRepository.save(user);

        bankAccountRepository.save(account(1L, "100015003000", "100000.00", 1L));
        bankAccountRepository.save(account(2L, "100015003001", "12000.00", 1L));

        UtilityAccountEntity provider = new UtilityAccountEntity();
        provider.setId(1L);
        provider.setNumber("8203232565");
        provider.setProviderName("VODAFONE");
        utilityAccountRepository.save(provider);
    }

    @Test
    void documentsKeepNumericIdsAndDecimal128() {
        Document raw = mongoTemplate.getCollection("bankingCoreAccount").find(new Document("_id", 1L)).first();
        assertNotNull(raw);
        assertEquals(Long.class, raw.get("_id").getClass());
        assertEquals(Decimal128.class, raw.get("actualBalance").getClass());
        assertEquals(new BigDecimal("100000.00"), ((Decimal128) raw.get("actualBalance")).bigDecimalValue());
        assertEquals("SAVINGS_ACCOUNT", raw.getString("type"));
        assertEquals(Long.class, raw.get("userId").getClass());
        assertFalse(raw.containsKey("user"));
    }

    @Test
    void lookupsAreCaseInsensitiveLikeMySql() {
        assertEquals(1L, utilityAccountRepository.findByProviderName("vodafone").orElseThrow().getId());
        assertEquals(1L, userRepository.findByIdentificationNumber("808829932v").orElseThrow().getId());
        assertEquals("VODAFONE", accountService.readUtilityAccount("Vodafone").getProviderName());
        assertEquals(2, userService.readUser("808829932V").getBankAccounts().size());
    }

    @Test
    void collationIndexesExistAndUniqueIsEnforced() {
        List<Document> indexes = mongoTemplate.getCollection("bankingCoreAccount").listIndexes().into(new java.util.ArrayList<>());
        Document numberIndex = indexes.stream().filter(d -> d.getString("name").equals("number_1")).findFirst().orElseThrow();
        assertTrue(numberIndex.getBoolean("unique"));
        assertEquals("en", numberIndex.get("collation", Document.class).getString("locale"));
        assertEquals(2, numberIndex.get("collation", Document.class).getInteger("strength"));

        assertThrows(DuplicateKeyException.class,
            () -> bankAccountRepository.save(account(3L, "100015003000", "1.00", 1L)));
    }

    @Test
    void fundTransferWritesTwoAccountUpdatesAndTwoTransactionsAtomically() {
        FundTransferResponse response = transactionService.fundTransfer(new FundTransferRequest("100015003000", "100015003001", new BigDecimal("100.00")));

        assertEquals("Transaction successfully completed", response.getMessage());
        List<TransactionEntity> txs = transactionRepository.findAll();
        assertEquals(2, txs.size());
        assertTrue(txs.stream().allMatch(t -> response.getTransactionId().equals(t.getTransactionId())));
        assertTrue(txs.stream().allMatch(t -> t.getTransactionType() == TransactionType.FUND_TRANSFER));
        assertEquals(List.of(1L, 2L), txs.stream().map(TransactionEntity::getId).sorted().toList());
        assertEquals(new BigDecimal("99900.00"), bankAccountRepository.findById(1L).orElseThrow().getActualBalance());
        // legacy arithmetic reproduced on purpose: availableBalance = updated actual -/+ amount
        assertEquals(new BigDecimal("99800.00"), bankAccountRepository.findById(1L).orElseThrow().getAvailableBalance());
        assertEquals(new BigDecimal("12100.00"), bankAccountRepository.findById(2L).orElseThrow().getActualBalance());
        assertEquals(new BigDecimal("12200.00"), bankAccountRepository.findById(2L).orElseThrow().getAvailableBalance());
    }

    @Test
    void utilityPaymentWritesOneAccountUpdateAndOneTransaction() {
        UtilityPaymentRequest request = new UtilityPaymentRequest();
        request.setAccount("100015003001");
        request.setProviderId(1L);
        request.setAmount(new BigDecimal("50.00"));
        request.setReferenceNumber("REF123");

        UtilityPaymentResponse response = transactionService.utilPayment(request);

        List<TransactionEntity> txs = transactionRepository.findAll();
        assertEquals(1, txs.size());
        assertEquals(response.getTransactionId(), txs.get(0).getTransactionId());
        assertEquals(TransactionType.UTILITY_PAYMENT, txs.get(0).getTransactionType());
        assertEquals(new BigDecimal("-50.00"), txs.get(0).getAmount());
        assertEquals(2L, txs.get(0).getAccountId());
        assertEquals(new BigDecimal("11950.00"), bankAccountRepository.findById(2L).orElseThrow().getActualBalance());
    }

    @Test
    void insufficientFundsLeavesNothingBehind() {
        assertThrows(InsufficientFundsException.class,
            () -> transactionService.fundTransfer(new FundTransferRequest("100015003001", "100015003000", new BigDecimal("999999.00"))));
        assertEquals(0, transactionRepository.count());
        assertEquals(new BigDecimal("12000.00"), bankAccountRepository.findById(2L).orElseThrow().getActualBalance());
    }

    @Test
    void writesJoinOneMongoTransactionAndRollBackTogether() {
        // the service's writes enlist in the surrounding MongoTransactionManager transaction; a failure
        // after both account updates and both transaction inserts leaves no partial state behind
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        assertThrows(IllegalStateException.class, () -> template.executeWithoutResult(status -> {
            transactionService.internalFundTransfer(accountService.readBankAccount("100015003000"),
                accountService.readBankAccount("100015003001"), new BigDecimal("100.00"));
            throw new IllegalStateException("boom after 2 account updates + 2 transaction inserts");
        }));

        assertEquals(0, transactionRepository.count());
        assertEquals(new BigDecimal("100000.00"), bankAccountRepository.findById(1L).orElseThrow().getActualBalance());
        assertEquals(new BigDecimal("12000.00"), bankAccountRepository.findById(2L).orElseThrow().getActualBalance());
    }

    private static BankAccountEntity account(Long id, String number, String balance, Long userId) {
        BankAccountEntity account = new BankAccountEntity();
        account.setId(id);
        account.setNumber(number);
        account.setType(AccountType.SAVINGS_ACCOUNT);
        account.setStatus(AccountStatus.ACTIVE);
        account.setActualBalance(new BigDecimal(balance));
        account.setAvailableBalance(new BigDecimal(balance));
        account.setUserId(userId);
        return account;
    }
}
