package com.javatodev.finance.model.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Document(collection = "bankingCoreUser")
public class UserEntity {

    @Id
    private Long id;

    private String firstName;
    private String lastName;
    private String email;
    private String identificationNumber;

}
