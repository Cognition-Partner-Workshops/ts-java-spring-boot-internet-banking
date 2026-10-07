package com.javatodev.finance.service;

import com.javatodev.finance.exception.EntityNotFoundException;
import com.javatodev.finance.model.dto.User;
import com.javatodev.finance.model.entity.BankAccountEntity;
import com.javatodev.finance.model.entity.UserEntity;
import com.javatodev.finance.repository.BankAccountRepository;
import com.javatodev.finance.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.Collections;
import java.util.Optional;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserServiceTest {
    private UserRepository userRepository;
    private BankAccountRepository bankAccountRepository;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        bankAccountRepository = mock(BankAccountRepository.class);
        userService = new UserService(userRepository, bankAccountRepository);
    }

    @Test
    void readUser_found() {
        UserEntity entity = new UserEntity();
        entity.setId(7L);
        entity.setIdentificationNumber("ID123");
        BankAccountEntity account = new BankAccountEntity();
        account.setId(70L);
        account.setNumber("100015003000");
        account.setUserId(7L);
        when(userRepository.findByIdentificationNumber("ID123")).thenReturn(Optional.of(entity));
        when(bankAccountRepository.findByUserId(7L)).thenReturn(List.of(account));
        User result = userService.readUser("ID123");
        assertNotNull(result);
        assertEquals("ID123", result.getIdentificationNumber());
        assertEquals(7L, result.getId());
        assertEquals(1, result.getBankAccounts().size());
        assertEquals("100015003000", result.getBankAccounts().get(0).getNumber());
        assertNull(result.getBankAccounts().get(0).getUser());
    }

    @Test
    void readUser_notFound() {
        when(userRepository.findByIdentificationNumber("ID123")).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> userService.readUser("ID123"));
    }

    @Test
    void readUsers_success() {
        UserEntity entity = new UserEntity();
        entity.setId(1L);
        entity.setIdentificationNumber("ID123");
        List<UserEntity> entities = Collections.singletonList(entity);
        Page<UserEntity> page = new PageImpl<>(entities);
        when(userRepository.findAll(any(Pageable.class))).thenReturn(page);
        when(bankAccountRepository.findByUserId(1L)).thenReturn(Collections.emptyList());
        List<User> result = userService.readUsers(Pageable.unpaged());
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("ID123", result.get(0).getIdentificationNumber());
        assertTrue(result.get(0).getBankAccounts().isEmpty());
    }
}
