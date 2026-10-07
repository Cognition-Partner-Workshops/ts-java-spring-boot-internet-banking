package com.javatodev.finance.service;

import com.javatodev.finance.exception.EntityNotFoundException;
import com.javatodev.finance.model.dto.User;
import com.javatodev.finance.model.entity.UserEntity;
import com.javatodev.finance.model.mapper.BankAccountMapper;
import com.javatodev.finance.model.mapper.UserMapper;
import com.javatodev.finance.repository.BankAccountRepository;
import com.javatodev.finance.repository.UserRepository;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

    private UserMapper userMapper = new UserMapper();
    private BankAccountMapper bankAccountMapper = new BankAccountMapper();

    private final UserRepository userRepository;
    private final BankAccountRepository bankAccountRepository;

    public User readUser(String identification) {
        UserEntity userEntity = userRepository.findByIdentificationNumber(identification).orElseThrow(EntityNotFoundException::new);
        return toDto(userEntity);
    }

    public List<User> readUsers(Pageable pageable) {
        return userRepository.findAll(pageable).getContent().stream().map(this::toDto).toList();
    }

    // accounts live in their own collection (reference by userId, no @DBRef); the User DTO
    // keeps embedding them one level deep exactly as the JPA @OneToMany did
    private User toDto(UserEntity userEntity) {
        User user = userMapper.convertToDto(userEntity);
        user.setBankAccounts(bankAccountMapper.convertToDtoList(bankAccountRepository.findByUserId(userEntity.getId())));
        return user;
    }
}
