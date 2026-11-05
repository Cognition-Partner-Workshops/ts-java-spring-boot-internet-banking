package com.javatodev.finance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.javatodev.finance.model.dto.UtilityPayment;
import com.javatodev.finance.model.entity.UtilityPaymentEntity;
import com.javatodev.finance.model.mapper.UtilityPaymentMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UtilityPaymentReferenceTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void numericProviderReferenceSurvivesTheHistoryMapping() throws Exception {
        UtilityPaymentEntity entity = new UtilityPaymentEntity();
        entity.setAccount("001234567890");
        entity.setReferenceNumber("12345678");
        UtilityPayment payment = new UtilityPaymentMapper().convertToDto(entity);
        var body = json.readTree(json.writeValueAsString(payment));
        assertEquals("12345678", body.get("referenceNumber").asText());
        assertEquals("****7890", body.get("account").asText());
        assertEquals("001234567890", payment.getAccount());
        assertEquals("12345678", payment.getReferenceNumber());
    }

    @Test
    void aReferenceThatIsThePayerAccountIsStillMasked() throws Exception {
        UtilityPayment payment = new UtilityPayment();
        payment.setAccount("001234567890");
        payment.setReferenceNumber(payment.getAccount());
        assertEquals("****7890", json.readTree(json.writeValueAsString(payment))
                .get("referenceNumber").asText());
        assertEquals("001234567890", payment.getReferenceNumber());
    }

    @Test
    void nullEmptyAndAlphanumericProviderReferencesArePreserved() throws Exception {
        UtilityPayment payment = new UtilityPayment();
        payment.setAccount("001234567890");
        for (String reference : new String[] {null, "", "BILL-12345678"}) {
            payment.setReferenceNumber(reference);
            var value = json.readTree(json.writeValueAsString(payment)).get("referenceNumber");
            if (reference == null) assertTrue(value.isNull());
            else assertEquals(reference, value.asText());
        }
    }

    @Test
    void embeddedPayerAccountsAreMaskedWithoutChangingBillDigitsOrModelValues() throws Exception {
        UtilityPayment payment = new UtilityPayment();
        payment.setAccount("001234567890");
        for (String reference : new String[] {
                "BILL-12345678 payer=001234567890",
                "001234567890/12345678/001234567890",
                "REF12345678001234567890END"}) {
            payment.setReferenceNumber(reference);
            String output = json.readTree(json.writeValueAsString(payment)).get("referenceNumber").asText();
            assertEquals(reference.replace("001234567890", "****7890"), output);
            assertTrue(output.contains("12345678"));
            assertEquals(reference, payment.getReferenceNumber());
            assertEquals("001234567890", payment.getAccount());
        }
    }

    @Test
    void missingPayerAccountsDoNotChangeProviderReferences() throws Exception {
        UtilityPayment payment = new UtilityPayment();
        payment.setReferenceNumber("BILL-12345678");
        for (String account : new String[] {null, ""}) {
            payment.setAccount(account);
            assertEquals("BILL-12345678", json.readTree(json.writeValueAsString(payment)).get("referenceNumber").asText());
        }
    }

    @Test
    void payerAccountReplacementIsLiteralNotARegularExpression() throws Exception {
        UtilityPayment payment = new UtilityPayment();
        payment.setAccount("AB.1234");
        payment.setReferenceNumber("BILL-12345678 ABX1234 AB.1234");
        assertEquals("BILL-12345678 ABX1234 ****1234",
                json.readTree(json.writeValueAsString(payment)).get("referenceNumber").asText());
    }
}
