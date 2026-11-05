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
}
