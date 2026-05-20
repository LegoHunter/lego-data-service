package com.vattima.lego.inventory.service.impl;

import com.vattima.lego.inventory.service.api.TransactionService;
import com.vattima.lego.inventory.service.dto.AddTransactionRequest;
import com.vattima.lego.inventory.service.dto.AddTransactionResponse;
import com.vattima.lego.inventory.service.dto.ItemInventoryRequest;
import com.vattima.lego.inventory.service.exception.CompletenessNotFoundException;
import com.vattima.lego.inventory.service.exception.ConditionCodeNotFoundException;
import com.vattima.lego.inventory.service.exception.NewOrUsedNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import io.legohunter.data.dao.*;
import io.legohunter.data.dto.*;
import io.legohunter.data.enums.CurrencyCode;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static io.legohunter.data.dto.ExternalService.ExternalServiceType.BRICKLINK;

@Component
@RequiredArgsConstructor
@Validated
@Slf4j
public class TransactionServiceImpl implements TransactionService {
    private final ExternalItemDao externalItemDao;
    private final ExternalItemInventoryDao externalItemInventoryDao;
    private final ConditionDao conditionDao;
    private final ItemInventoryDao itemInventoryDao;
    private final TransactionPlatformDao transactionPlatformDao;
    private final TransactionsDao transactionsDao;
    private final TransactionItemDao transactionItemDao;
    private final TransactionCostDao transactionCostDao;

    @Override
    public AddTransactionResponse addTransaction(@Valid AddTransactionRequest addTransactionRequest) {
        log.info("Add transaction request: {}", addTransactionRequest);

        // Get transactionPlatformId.
        Integer transactionPlatformId = transactionPlatformDao.findTransactionPlatformByName(addTransactionRequest.getPlatformName())
                .map(TransactionPlatform::getTransactionPlatformId)
                .orElseThrow(RuntimeException::new);

        // Insert Transaction.
        Transactions transactions = Transactions.builder()
                .transactionDateTime(addTransactionRequest.getTransactionDateTime())
                .fromPartyId(addTransactionRequest.getFromPartyId())
                .toPartyId(addTransactionRequest.getToPartyId())
                .notes(addTransactionRequest.getNotes())
                .transactionPlatformId(transactionPlatformId)
                .transactionOrderId(addTransactionRequest.getOrderId())
                .build();
        transactionsDao.insert(transactions);

        // Insert Transaction Costs.
        transactionCostDao.setTransactionCosts(transactions.getTransactionId(),
                addTransactionRequest.getCosts()
                        .stream()
                        .map(costRequest ->
                                TransactionCost.builder()
                                        .transactionId(transactions.getTransactionId())
                                        .costTypeCode(costRequest.getCostTypeCode())
                                        .amount(costRequest.getAmount())
                                        .currencyCode(CurrencyCode.valueOf(costRequest.getCurrencyCode()))
                                        .notes(costRequest.getNotes())
                                        .build()
                        ).toList());

        // Insert ItemInventory.
        addTransactionRequest.getTransactionItems()
                .forEach(transactionItemRequest -> {
                    ItemInventoryRequest itemInventoryRequest = transactionItemRequest.getItemInventory();

                    // Determine if Item exists or will be inserted.
                    ExternalItem externalItem = externalItemDao.findByExternalServiceAndNumber(BRICKLINK.getExternalServiceId(), itemInventoryRequest.getItemNumber()).orElseThrow(RuntimeException::new);

                    // Find Conditions
                    Integer itemConditionId = conditionDao.findByConditionCode(itemInventoryRequest.getItemConditionCode()).map(Condition::getConditionId).orElse(null);
                    Integer boxConditionId = conditionDao.findByConditionCode(itemInventoryRequest.getBoxConditionCode()).map(Condition::getConditionId).orElse(null);
                    Integer instructionsConditionId = conditionDao.findByConditionCode(itemInventoryRequest.getInstructionsConditionCode()).map(Condition::getConditionId).orElse(null);

                    // Insert ItemInventory.
                    ItemInventory itemInventory = toItemInventory(itemInventoryRequest, itemConditionId, boxConditionId, instructionsConditionId);
                    itemInventoryDao.insert(itemInventory);

                    externalItemInventoryDao.insert(ExternalItemInventory.builder()
                            .externalItemId(externalItem.getExternalItemId())
                            .itemInventoryId(itemInventory.getItemInventoryId())
                            .build());

                    // Insert TransactionItem.
                    TransactionItem transactionItem = TransactionItem.builder()
                            .transactionId(transactions.getTransactionId())
                            .itemInventoryId(itemInventory.getItemInventoryId())
                            .transactionTypeCode(transactionItemRequest.getTransactionTypeCode())
                            .notes(transactionItemRequest.getNotes())
                            .build();
                    transactionItemDao.insert(transactionItem);

                    // Insert TransactionItem Costs
                    // Insert Transaction Costs.
                    transactionCostDao.setTransactionItemCosts(transactionItem.getTransactionItemId(),
                            transactionItemRequest.getCosts()
                                    .stream()
                                    .map(costRequest -> TransactionItemCost.builder()
                                            .costTypeCode(costRequest.getCostTypeCode())
                                            .currencyCode(costRequest.getCurrencyCode())
                                            .amount(costRequest.getAmount())
                                            .notes(costRequest.getNotes())
                                            .build()
                                    ).toList());
                });

        return AddTransactionResponse.builder()
                .transactionId(transactions.getTransactionId())
                .build();
    }

    private ItemInventory toItemInventory(
            ItemInventoryRequest itemInventoryRequest,
            Integer itemConditionId,
            Integer boxConditionId,
            Integer instructionsConditionId) {
        ItemInventory itemInventory = new ItemInventory();
        itemInventory.setUuid(UUID.randomUUID().toString());
        itemInventory.setBoxNumber(itemInventoryRequest.getBoxNumber());
        itemInventory.setDescription(itemInventoryRequest.getDescription());
        itemInventory.setActive(itemInventoryRequest.getActive());
        itemInventory.setForSale(itemInventoryRequest.getForSale());
        itemInventory.setNewOrUsed(itemInventoryRequest.getNewOrUsed());
        itemInventory.setCompleteness(itemInventoryRequest.getCompleteness());
        itemInventory.setItemConditionId(itemConditionId);
        itemInventory.setBoxConditionId(boxConditionId);
        itemInventory.setInstructionsConditionId(instructionsConditionId);
        itemInventory.setSealed(itemInventoryRequest.getSealed());
        itemInventory.setBuiltOnce(itemInventoryRequest.getBuiltOnce());
        return itemInventory;
    }

    private void validateAndSetCondition(final String conditionCode, final Consumer<Integer> consumer) {
        Optional.ofNullable(conditionCode)
                .flatMap(conditionDao::findByConditionCode)
                .ifPresentOrElse(condition -> consumer.accept(condition.getConditionId()),
                        () -> {
                            throw new ConditionCodeNotFoundException(conditionCode);
                        });
    }

    private void validateAndSetNewOrUsed(final String newOrUsed, final Consumer<String> consumer) {
        if (List.of("N", "U").contains(newOrUsed)) {
            consumer.accept(newOrUsed);
        } else {
            throw new NewOrUsedNotFoundException(newOrUsed);
        }
    }

    private void validateAndSetCompleteness(final String completeness, final Consumer<String> consumer) {
        if (List.of("C", "I").contains(completeness)) {
            consumer.accept(completeness);
        } else {
            throw new CompletenessNotFoundException(completeness);
        }
    }
}
