package com.javatodev.finance.service;

import com.javatodev.finance.model.dto.response.StatementExportDto;
import com.javatodev.finance.model.dto.response.StatementLineDto;

/**
 * Renders a {@link StatementExportDto} as an RFC-4180 CSV document with a fixed,
 * deterministic header/column order and CRLF line terminators. A field is quoted
 * iff it contains a comma, double-quote, CR or LF; embedded double-quotes are
 * doubled. An empty export yields the header row only.
 */
public final class StatementCsvRenderer {

    static final String[] HEADERS = {
        "timestamp", "transactionId", "transactionType", "amount", "referenceNumber", "accountNumber"
    };

    private static final String LINE_TERMINATOR = "\r\n";

    private StatementCsvRenderer() {
    }

    public static String render(StatementExportDto data) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(",", HEADERS)).append(LINE_TERMINATOR);
        for (StatementLineDto line : data.lines()) {
            sb.append(escape(line.timestamp() == null ? "" : line.timestamp().toString())).append(",")
                .append(escape(line.transactionId())).append(",")
                .append(escape(line.transactionType() == null ? "" : line.transactionType().name())).append(",")
                .append(escape(line.amount() == null ? "" : line.amount().toPlainString())).append(",")
                .append(escape(line.referenceNumber())).append(",")
                .append(escape(line.accountNumber()))
                .append(LINE_TERMINATOR);
        }
        return sb.toString();
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        boolean mustQuote = value.contains(",") || value.contains("\"")
            || value.contains("\n") || value.contains("\r");
        String escaped = value.replace("\"", "\"\"");
        return mustQuote ? "\"" + escaped + "\"" : escaped;
    }
}
