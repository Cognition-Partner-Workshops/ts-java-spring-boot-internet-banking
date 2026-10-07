package com.javatodev.finance.model.entity;

import com.javatodev.finance.model.AccountStatus;
import com.javatodev.finance.model.AccountType;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Document(collection = "bankingCoreAccount")
public class BankAccountEntity {

    @Id
    private Long id;

    private String number;

    private AccountType type;

    private AccountStatus status;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal availableBalance;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal actualBalance;

    private Long userId;

}
