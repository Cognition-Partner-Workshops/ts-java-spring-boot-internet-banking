package com.javatodev.finance.repository;

/**
 * MySQL 8's default collation (utf8mb4_0900_ai_ci) is case-insensitive; the three lookup
 * fields keep that behaviour through a collation-aware index plus the same collation on
 * the query (migration ledger s3.2 entry 3, d-collation).
 */
public final class MongoCollations {

    public static final String CASE_INSENSITIVE = "{ 'locale': 'en', 'strength': 2 }";

    private MongoCollations() {
    }
}
