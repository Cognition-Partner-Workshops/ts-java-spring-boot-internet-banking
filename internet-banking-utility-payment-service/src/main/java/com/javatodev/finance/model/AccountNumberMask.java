package com.javatodev.finance.model;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.javatodev.finance.model.dto.UtilityPayment;
import java.io.IOException;
import java.util.regex.Pattern;

public class AccountNumberMask extends JsonSerializer<String> {
    private static final Pattern NUMBERS = Pattern.compile("\\d{5,}");

    public static String mask(String number) {
        if (number == null || number.isEmpty()) return number;
        return "****" + number.substring(Math.max(0, number.length() - 4));
    }

    public static String redact(String text) {
        return text == null ? null : NUMBERS.matcher(text).replaceAll(match -> mask(match.group()));
    }

    @Override
    public void serialize(String value, JsonGenerator output, SerializerProvider provider) throws IOException {
        output.writeString(mask(value));
    }

    public static class ReferenceSerializer extends JsonSerializer<String> {
        @Override
        public void serialize(String value, JsonGenerator output, SerializerProvider provider) throws IOException {
            Object owner = output.getCurrentValue();
            boolean isAccount = owner instanceof UtilityPayment
                    && value.equals(((UtilityPayment) owner).getAccount());
            output.writeString(isAccount ? mask(value) : value);
        }
    }
}
