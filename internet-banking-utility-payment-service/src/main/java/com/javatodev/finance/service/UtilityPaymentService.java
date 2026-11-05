package com.javatodev.finance.service;

import com.javatodev.finance.configuration.security.AuthenticatedCaller;
import com.javatodev.finance.model.TransactionStatus;
import com.javatodev.finance.model.dto.UtilityPayment;
import com.javatodev.finance.model.entity.UtilityPaymentEntity;
import com.javatodev.finance.model.mapper.UtilityPaymentMapper;
import com.javatodev.finance.model.rest.request.UtilityPaymentRequest;
import com.javatodev.finance.model.rest.response.UtilityPaymentResponse;
import com.javatodev.finance.repository.UtilityPaymentRepository;
import com.javatodev.finance.service.rest.BankingCoreRestClient;

import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class UtilityPaymentService {
    private final UtilityPaymentRepository utilityPaymentRepository;
    private final BankingCoreRestClient bankingCoreRestClient;

    private UtilityPaymentMapper utilityPaymentMapper = new UtilityPaymentMapper();

    public UtilityPaymentResponse utilPayment(UtilityPaymentRequest paymentRequest) {
        log.info("Utility payment processing {}", paymentRequest.toString());

        TransactionAmountValidator.requireValidAmount(paymentRequest.getAmount());
        // Core banking answers 403 unless the authenticated caller owns the debited account.
        bankingCoreRestClient.readAccount(paymentRequest.getAccount());

        UtilityPaymentEntity entity = new UtilityPaymentEntity();
        BeanUtils.copyProperties(paymentRequest, entity);
        entity.setOwnerId(AuthenticatedCaller.subject());
        entity.setStatus(TransactionStatus.PROCESSING);
        UtilityPaymentEntity optUtilPayment = utilityPaymentRepository.save(entity);

        UtilityPaymentResponse utilityPaymentResponse = bankingCoreRestClient.utilityPayment(paymentRequest);
        log.info("Transaction response {}", utilityPaymentResponse.toString());

        optUtilPayment.setStatus(TransactionStatus.SUCCESS);
        optUtilPayment.setTransactionId(utilityPaymentResponse.getTransactionId());
        utilityPaymentRepository.save(optUtilPayment);

        return UtilityPaymentResponse.builder().message("Utility Payment Successfully Processed").transactionId(utilityPaymentResponse.getTransactionId()).build();
    }

    public List<UtilityPayment> readPayments(Pageable pageable) {
        Page<UtilityPaymentEntity> allUtilPayments = AuthenticatedCaller.isStaff()
            ? utilityPaymentRepository.findAll(pageable)
            : utilityPaymentRepository.findAllByOwnerId(AuthenticatedCaller.subject(), pageable);
        return utilityPaymentMapper.convertToDtoList(allUtilPayments.getContent());
    }
}
