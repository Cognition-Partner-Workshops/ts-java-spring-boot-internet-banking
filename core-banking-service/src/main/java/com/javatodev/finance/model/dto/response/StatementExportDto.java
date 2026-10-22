package com.javatodev.finance.model.dto.response;

import java.time.LocalDate;
import java.util.List;

/**
 * Deterministic JSON projection of an account statement export. Lines are
 * ordered most-recent-first. {@code fromDate}/{@code toDate} echo the requested
 * (optional) inclusive date filter; {@code count} is the number of lines.
 */
public record StatementExportDto(
    String accountNumber,
    LocalDate fromDate,
    LocalDate toDate,
    int count,
    List<StatementLineDto> lines
) {
}
