package com.javatodev.finance.config;

import com.javatodev.finance.model.entity.BankAccountEntity;
import com.javatodev.finance.model.entity.TransactionEntity;
import com.javatodev.finance.model.entity.UserEntity;
import com.javatodev.finance.model.entity.UtilityAccountEntity;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Collation;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import lombok.RequiredArgsConstructor;

/**
 * Atlas is a replica set, so the JPA transaction around fund transfers / utility payments
 * is kept as a MongoDB multi-document transaction (MongoTransactionManager + @Transactional).
 * Indexes mirror .migration/mapping_spec.json collections[].indexes; the three lookup
 * indexes carry the MySQL-equivalent case-insensitive collation.
 */
@Configuration
@EnableTransactionManagement
@RequiredArgsConstructor
public class MongoConfig {

    public static final Collation CASE_INSENSITIVE = Collation.of("en").strength(2);

    @Bean
    public MongoTransactionManager transactionManager(MongoDatabaseFactory databaseFactory) {
        return new MongoTransactionManager(databaseFactory);
    }

    @Bean
    public ApplicationRunner mongoIndexInitializer(MongoTemplate mongoTemplate) {
        return new MongoIndexInitializer(mongoTemplate);
    }

    @RequiredArgsConstructor
    static class MongoIndexInitializer implements ApplicationRunner {

        private final MongoTemplate mongoTemplate;

        @Override
        public void run(ApplicationArguments args) {
            mongoTemplate.indexOps(BankAccountEntity.class)
                .ensureIndex(new Index().on("number", Sort.Direction.ASC).unique().collation(CASE_INSENSITIVE));
            mongoTemplate.indexOps(BankAccountEntity.class)
                .ensureIndex(new Index().on("userId", Sort.Direction.ASC));
            mongoTemplate.indexOps(UserEntity.class)
                .ensureIndex(new Index().on("identificationNumber", Sort.Direction.ASC).unique().collation(CASE_INSENSITIVE));
            mongoTemplate.indexOps(UtilityAccountEntity.class)
                .ensureIndex(new Index().on("providerName", Sort.Direction.ASC).collation(CASE_INSENSITIVE));
            mongoTemplate.indexOps(TransactionEntity.class)
                .ensureIndex(new Index().on("accountId", Sort.Direction.ASC));
        }
    }
}
