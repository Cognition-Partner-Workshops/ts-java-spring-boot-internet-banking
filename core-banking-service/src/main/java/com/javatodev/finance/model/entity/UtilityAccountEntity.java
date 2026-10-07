package com.javatodev.finance.model.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Document(collection = "bankingCoreUtilityAccount")
public class UtilityAccountEntity {

    @Id
    private Long id;

    private String number;

    private String providerName;

}
