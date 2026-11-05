package com.javatodev.finance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.javatodev.finance.controller.AccountController;
import com.javatodev.finance.controller.TransactionController;
import com.javatodev.finance.exception.GlobalExceptionHandler;
import com.javatodev.finance.exception.InsufficientFundsException;
import com.javatodev.finance.model.dto.*;
import com.javatodev.finance.model.dto.request.FundTransferRequest;
import com.javatodev.finance.repository.*;
import com.javatodev.finance.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class AccountDisclosureTest {
    private final ObjectMapper json = new ObjectMapper();
    private static final String NUMBER = "001234567890";

    @Test
    void accountEndpointMasksOutputButRetainsFullLookupAndModelValue() throws Exception {
        AccountService service = mock(AccountService.class);
        BankAccount account = new BankAccount();
        account.setNumber(NUMBER);
        when(service.readBankAccount(NUMBER)).thenReturn(account);
        var mvc = MockMvcBuilders.standaloneSetup(new AccountController(service)).build();
        String body = mvc.perform(get("/api/v1/account/bank-account/" + NUMBER)).andReturn().getResponse().getContentAsString();
        assertEquals("****7890", json.readTree(body).get("number").asText());
        assertFalse(body.contains(NUMBER));
        assertEquals(NUMBER, account.getNumber());
        verify(service).readBankAccount(NUMBER);
    }

    @Test
    void nestedUserAccountsAndUtilityAccountsAreMasked() throws Exception {
        BankAccount account = new BankAccount();
        account.setNumber(NUMBER);
        User user = new User();
        user.setBankAccounts(List.of(account));
        assertEquals("****7890", json.readTree(json.writeValueAsString(user)).at("/bankAccounts/0/number").asText());
        UtilityAccount utility = new UtilityAccount();
        utility.setNumber(NUMBER);
        assertEquals("****7890", json.readTree(json.writeValueAsString(utility)).get("number").asText());
    }

    @Test
    void transactionReferencesAreSafeWithoutChangingInternalRecords() throws Exception {
        Transaction transaction = new Transaction();
        transaction.setReferenceNumber(NUMBER);
        assertEquals("****7890", json.readTree(json.writeValueAsString(transaction)).get("referenceNumber").asText());
        assertEquals(NUMBER, transaction.getReferenceNumber());
        transaction.setReferenceNumber("REF123");
        assertEquals("REF123", json.readTree(json.writeValueAsString(transaction)).get("referenceNumber").asText());
    }

    @Test
    void insufficientFundsErrorMasksTheAccount() throws Exception {
        AccountService accounts = mock(AccountService.class);
        BankAccount source = new BankAccount();
        source.setNumber(NUMBER);
        source.setActualBalance(BigDecimal.ZERO);
        when(accounts.readBankAccount(NUMBER)).thenReturn(source);
        when(accounts.readBankAccount("009876543210")).thenReturn(new BankAccount());
        TransactionService service = new TransactionService(accounts, mock(BankAccountRepository.class), mock(TransactionRepository.class));
        var error = assertThrows(InsufficientFundsException.class,
            () -> service.fundTransfer(new FundTransferRequest(NUMBER, "009876543210", BigDecimal.ONE)));
        assertEquals("Insufficient funds in the account ****7890", error.getCode());
        var mvc = MockMvcBuilders.standaloneSetup(new TransactionController(service))
            .setControllerAdvice(new GlobalExceptionHandler()).build();
        String body = mvc.perform(post("/api/v1/transaction/fund-transfer")
            .contentType("application/json")
            .content("{\"fromAccount\":\"001234567890\",\"toAccount\":\"009876543210\",\"amount\":1}"))
            .andReturn().getResponse().getContentAsString();
        assertFalse(body.contains(NUMBER), body);
        assertEquals("Insufficient funds in the account ****7890", json.readTree(body).get("code").asText());
        verify(accounts, times(2)).readBankAccount(NUMBER);
    }

    @Test
    void unexpectedErrorsAndMissingResourcesDoNotEchoAccountUrls() throws Exception {
        AccountService service = mock(AccountService.class);
        when(service.readBankAccount(NUMBER)).thenThrow(new IllegalStateException("lookup /account/" + NUMBER));
        var mvc = MockMvcBuilders.standaloneSetup(new AccountController(service))
            .setControllerAdvice(new GlobalExceptionHandler()).build();
        String body = mvc.perform(get("/api/v1/account/bank-account/" + NUMBER)).andReturn().getResponse().getContentAsString();
        assertFalse(body.contains(NUMBER), body);
    }

    @Test
    void nullEmptyShortAndAlreadyMaskedNumbersAreSafe() throws Exception {
        for (String value : new String[] {null, "", "123", "****7890"}) {
            BankAccount account = new BankAccount();
            account.setNumber(value);
            var result = json.readTree(json.writeValueAsString(account)).get("number");
            if (value == null) assertTrue(result.isNull());
            else assertEquals(value.isEmpty() ? "" : value.startsWith("****") ? value : "****" + value, result.asText());
            assertEquals(value, account.getNumber());
        }
    }
}
