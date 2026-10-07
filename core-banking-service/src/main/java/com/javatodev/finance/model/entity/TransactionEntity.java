package com.javatodev.finance.model.entity;

import com.javatodev.finance.model.TransactionType;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "bankingCoreTransaction")
public class TransactionEntity {

    @Id
    private Long id;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal amount;

    private TransactionType transactionType;

    private String referenceNumber;

    private String transactionId;

    private Long accountId;

}
