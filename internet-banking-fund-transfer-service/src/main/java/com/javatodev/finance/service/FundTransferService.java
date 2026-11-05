package com.javatodev.finance.service;

import com.javatodev.finance.configuration.security.AuthenticatedCaller;
import com.javatodev.finance.exception.SimpleBankingGlobalException;
import com.javatodev.finance.model.TransactionStatus;
import com.javatodev.finance.model.dto.FundTransfer;
import com.javatodev.finance.model.dto.request.FundTransferRequest;
import com.javatodev.finance.model.dto.response.FundTransferResponse;
import com.javatodev.finance.model.entity.FundTransferEntity;
import com.javatodev.finance.model.mapper.FundTransferMapper;
import com.javatodev.finance.model.repository.FundTransferRepository;
import com.javatodev.finance.service.rest.client.BankingCoreFeignClient;

import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@Service
public class FundTransferService {

    private final FundTransferRepository fundTransferRepository;
    private final BankingCoreFeignClient bankingCoreFeignClient;

    private FundTransferMapper mapper = new FundTransferMapper();

    public FundTransferResponse fundTransfer(FundTransferRequest request) {
        log.info("Sending fund transfer request {}" + request.toString());

        TransactionAmountValidator.requireValidAmount(request.getAmount());
        if (request.getFromAccount() == null || request.getFromAccount().equals(request.getToAccount())) {
            throw new SimpleBankingGlobalException("INVALID_TRANSACTION", "Source and destination accounts must be different");
        }
        // Core banking answers 403 unless the authenticated caller owns the source account.
        bankingCoreFeignClient.readAccount(request.getFromAccount());

        FundTransferEntity entity = new FundTransferEntity();
        BeanUtils.copyProperties(request, entity);
        entity.setOwnerId(AuthenticatedCaller.subject());
        entity.setStatus(TransactionStatus.PENDING);
        FundTransferEntity optFundTransfer = fundTransferRepository.save(entity);

        FundTransferResponse fundTransferResponse = bankingCoreFeignClient.fundTransfer(request);
        optFundTransfer.setTransactionReference(fundTransferResponse.getTransactionId());
        optFundTransfer.setStatus(TransactionStatus.SUCCESS);
        fundTransferRepository.save(optFundTransfer);

        fundTransferResponse.setMessage("Fund Transfer Successfully Completed");
        return fundTransferResponse;

    }

    public List<FundTransfer> readAllTransfers(Pageable pageable) {
        if (AuthenticatedCaller.isStaff()) {
            return mapper.convertToDtoList(fundTransferRepository.findAll(pageable).getContent());
        }
        return mapper.convertToDtoList(fundTransferRepository.findAllByOwnerId(AuthenticatedCaller.subject(), pageable).getContent());
    }
}
