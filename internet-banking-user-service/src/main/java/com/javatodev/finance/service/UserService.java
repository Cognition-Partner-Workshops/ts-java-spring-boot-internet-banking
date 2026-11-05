package com.javatodev.finance.service;

import com.javatodev.finance.exception.EntityNotFoundException;
import com.javatodev.finance.exception.GlobalErrorCode;
import com.javatodev.finance.exception.InvalidBankingUserException;
import com.javatodev.finance.exception.SimpleBankingGlobalException;
import com.javatodev.finance.exception.UserAlreadyRegisteredException;
import com.javatodev.finance.exception.SimpleBankingGlobalException;
import com.javatodev.finance.model.dto.Status;
import com.javatodev.finance.model.dto.User;
import com.javatodev.finance.model.dto.UserUpdateRequest;
import com.javatodev.finance.model.entity.UserEntity;
import com.javatodev.finance.model.mapper.UserMapper;
import com.javatodev.finance.model.repository.UserRepository;
import com.javatodev.finance.model.rest.response.UserResponse;
import com.javatodev.finance.service.rest.BankingCoreRestClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {
    private final KeycloakUserService keycloakUserService;
    private final UserRepository userRepository;
    private final BankingCoreRestClient bankingCoreRestClient;

    private UserMapper userMapper = new UserMapper();

    public User createUser(User user) {
        validatePassword(user.getPassword());

        List<UserRepresentation> userRepresentations = keycloakUserService.readUserByEmail(user.getEmail());
        if (!userRepresentations.isEmpty()) {
            throw new UserAlreadyRegisteredException("This email already registered as a user. Please check and retry.", GlobalErrorCode.ERROR_EMAIL_REGISTERED);
        }

        UserResponse userResponse = bankingCoreRestClient.readUser(user.getIdentification());

        if (userResponse == null || userResponse.getId() == null
            || !Objects.equals(userResponse.getEmail(), user.getEmail())) {
            throw new SimpleBankingGlobalException(
                GlobalErrorCode.ERROR_USER_NOT_FOUND_UNDER_NIC,
                "We couldn't find user under given identification. Please check and retry");
        }

        UserRepresentation userRepresentation = new UserRepresentation();
        userRepresentation.setEmail(userResponse.getEmail());
        userRepresentation.setEmailVerified(false);
        userRepresentation.setEnabled(false);
        userRepresentation.setUsername(userResponse.getEmail());
        userRepresentation.setFirstName(userResponse.getFirstName());
        userRepresentation.setLastName(userResponse.getLastName());

        CredentialRepresentation credentialRepresentation = new CredentialRepresentation();
        credentialRepresentation.setValue(user.getPassword());
        credentialRepresentation.setTemporary(false);
        userRepresentation.setCredentials(Collections.singletonList(credentialRepresentation));

        Integer userCreationResponse = keycloakUserService.createUser(userRepresentation);

        if (userCreationResponse == 201) {
            log.info("User created under given username {}", user.getEmail());

            List<UserRepresentation> userRepresentations1 = keycloakUserService.readUserByEmail(user.getEmail());
            user.setAuthId(userRepresentations1.get(0).getId());
            user.setStatus(Status.PENDING);
            UserEntity save = userRepository.save(userMapper.convertToEntity(user));
            User response = userMapper.convertToDto(save);
            response.setAuthId(null);
            response.setEmail(user.getEmail());
            response.setIdentification(user.getIdentification());
            response.setPassword(null);
            response.setStatus(Status.PENDING);
            return response;
        }

        throw new SimpleBankingGlobalException(
            GlobalErrorCode.ERROR_USER_NOT_FOUND_UNDER_NIC,
            "We couldn't find user under given identification. Please check and retry");

    }

    public List<User> readUsers(Pageable pageable) {
        Page<UserEntity> allUsersInDb = userRepository.findAll(pageable);
        List<User> users = userMapper.convertToDtoList(allUsersInDb.getContent());
        users.forEach(user -> {
            UserRepresentation userRepresentation = keycloakUserService.readUser(user.getAuthId());
            user.setId(user.getId());
            user.setEmail(userRepresentation.getEmail());
            user.setIdentification(user.getIdentification());
        });
        return users;
    }

    public User readUser(Long userId) {
        return userMapper.convertToDto(userRepository.findById(userId).orElseThrow(EntityNotFoundException::new));
    }

    public User updateUser(Long id, UserUpdateRequest userUpdateRequest) {
        UserEntity userEntity = userRepository.findById(id).orElseThrow(EntityNotFoundException::new);

        if (userUpdateRequest.getStatus() == Status.APPROVED) {
            UserRepresentation userRepresentation = keycloakUserService.readUser(userEntity.getAuthId());
            userRepresentation.setEnabled(true);
            userRepresentation.setEmailVerified(true);
            keycloakUserService.updateUser(userRepresentation);
            keycloakUserService.grantBankCustomerRole(userEntity.getAuthId());
        }

        userEntity.setStatus(userUpdateRequest.getStatus());
        return userMapper.convertToDto(userRepository.save(userEntity));
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 12
            || password.chars().noneMatch(Character::isUpperCase)
            || password.chars().noneMatch(Character::isLowerCase)
            || password.chars().noneMatch(Character::isDigit)) {
            throw new IllegalArgumentException("Password does not meet the required policy");
        }
    }

}
