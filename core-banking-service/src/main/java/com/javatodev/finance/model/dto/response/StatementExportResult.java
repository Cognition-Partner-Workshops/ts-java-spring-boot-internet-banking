package com.javatodev.finance.model.dto.response;

import com.javatodev.finance.model.StatementFormat;

/**
 * Carrier from the service to the controller. {@code data} is the typed
 * projection (used directly as the JSON body); {@code csvBody} holds the
 * rendered CSV document and is {@code null} for JSON exports.
 */
public record StatementExportResult(
    StatementFormat format,
    StatementExportDto data,
    String csvBody
) {
}
