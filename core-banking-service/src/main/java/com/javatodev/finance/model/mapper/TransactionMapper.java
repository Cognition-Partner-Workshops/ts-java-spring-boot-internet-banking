package com.javatodev.finance.model.mapper;

import com.javatodev.finance.model.dto.response.TransactionResponse;
import com.javatodev.finance.model.entity.TransactionEntity;

public class TransactionMapper extends BaseMapper<TransactionEntity, TransactionResponse> {

    @Override
    public TransactionEntity convertToEntity(TransactionResponse dto, Object... args) {
        if (dto == null) {
            return null;
        }
        return TransactionEntity.builder()
            .id(dto.getId())
            .transactionId(dto.getTransactionId())
            .referenceNumber(dto.getReferenceNumber())
            .transactionType(dto.getTransactionType())
            .amount(dto.getAmount())
            .timestamp(dto.getTimestamp())
            .build();
    }

    @Override
    public TransactionResponse convertToDto(TransactionEntity entity, Object... args) {
        if (entity == null) {
            return null;
        }
        return TransactionResponse.builder()
            .id(entity.getId())
            .transactionId(entity.getTransactionId())
            .referenceNumber(entity.getReferenceNumber())
            .transactionType(entity.getTransactionType())
            .amount(entity.getAmount())
            .accountNumber(entity.getAccount() != null ? entity.getAccount().getNumber() : null)
            .timestamp(entity.getTimestamp())
            .build();
    }
}
