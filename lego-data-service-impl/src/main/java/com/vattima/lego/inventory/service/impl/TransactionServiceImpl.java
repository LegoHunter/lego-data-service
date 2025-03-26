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
import net.lego.data.v2.dao.*;
import net.lego.data.v2.dto.*;
import net.lego.data.v2.enums.CostCategory;
import net.lego.data.v2.enums.CurrencyCode;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.lego.data.v2.enums.CostCategory.TRANSACTION;
import static net.lego.data.v2.enums.CostCategory.TRANSACTION_ITEM;

@Component
@RequiredArgsConstructor
@Validated
@Slf4j
public class TransactionServiceImpl implements TransactionService {
    private final ItemDao itemDao;
    private final ExternalItemDao externalItemDao;
    private final ConditionDao conditionDao;
    private final ItemInventoryDao itemInventoryDao;
    private final TransactionPlatformDao transactionPlatformDao;
    private final TransactionsDao transactionsDao;
    private final TransactionItemDao transactionItemDao;
    private final TransactionCostDao transactionCostDao;

    private final Pattern itemNumberPattern = Pattern.compile("^([0-9,A-Z,a-z]*).*$");

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
                                        .costCategoryCode(TRANSACTION)
                                        .costReferenceId(transactions.getTransactionId())
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

                    // Lookup item.
                    AtomicReference<String> itemNumber = new AtomicReference<>(itemInventoryRequest.getItemNumber());
                    Matcher itemNumberMatcher = itemNumberPattern.matcher(itemNumber.get());
                    if (itemNumberMatcher.find()) {
                        itemNumber.set(itemNumberMatcher.group(1));
                    }

                    // Determine if Item exists or will be inserted.
                    ExternalItem externalItem = externalItemDao.findByExternalNumber(itemInventoryRequest.getItemNumber()).orElseThrow(RuntimeException::new);
                    Integer itemId = Optional.ofNullable(externalItem.getExternalServiceItem())
                            .map(ExternalServiceItem::getItemId)
                            .orElseGet(() -> {
                                Item item = Item.builder()
                                        .itemNumber(itemNumber.get())
                                        .itemName(externalItem.getName())
                                        .notes(null)
                                        .isObsolete(null)
                                        .build();
                                itemDao.insert(item);
                                return item.getItemId();
                            });

                    // Find Conditions
                    Integer itemConditionId = conditionDao.findByConditionCode(itemInventoryRequest.getItemConditionCode()).map(Condition::getConditionId).orElse(null);
                    Integer boxConditionId = conditionDao.findByConditionCode(itemInventoryRequest.getBoxConditionCode()).map(Condition::getConditionId).orElse(null);
                    Integer instructionsConditionId = conditionDao.findByConditionCode(itemInventoryRequest.getInstructionsConditionCode()).map(Condition::getConditionId).orElse(null);

                    // Insert ItemInventory.
                    ItemInventory itemInventory = ItemInventory.builder()
                            .uuid(UUID.randomUUID().toString())
                            .itemId(itemId)
                            .boxNumber(itemInventoryRequest.getBoxNumber())
                            .quantity(itemInventoryRequest.getQuantity())
                            .description(itemInventoryRequest.getDescription())
                            .active(itemInventoryRequest.getActive())
                            .forSale(itemInventoryRequest.getForSale())
                            .newOrUsed(itemInventoryRequest.getNewOrUsed())
                            .completeness(itemInventoryRequest.getCompleteness())
                            .itemConditionId(itemConditionId)
                            .boxConditionId(boxConditionId)
                            .instructionsConditionId(instructionsConditionId)
                            .sealed(itemInventoryRequest.getSealed())
                            .builtOnce(itemInventoryRequest.getForSale())
                            .build();
                    itemInventoryDao.insert(itemInventory);

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
                                    .map(costRequest ->
                                            TransactionCost.builder()
                                                    .costCategoryCode(TRANSACTION_ITEM)
                                                    .costReferenceId(transactionItem.getTransactionItemId())
                                                    .costTypeCode(costRequest.getCostTypeCode())
                                                    .amount(costRequest.getAmount())
                                                    .currencyCode(CurrencyCode.valueOf(costRequest.getCurrencyCode()))
                                                    .notes(costRequest.getNotes())
                                                    .build()
                                    ).toList());
                });

        return AddTransactionResponse.builder()
                .transactionId(transactions.getTransactionId())
                .build();


//        final String itemNumber = addItemInventoryRequest.getItemNumber();
//
//        ItemInventory.ItemInventoryBuilder itemInventoryBuilder = ItemInventory.builder();
//
//        // Find itemNumber in external items
//        Optional<ExternalItem> externalItem = externalItemDao.findByExternalNumber(itemNumber);
//
//        // If external item not found, attempt to find item using itemNumber and then get external item.
//        if (externalItem.isEmpty()) {
//            externalItem = itemDao.findByItemNumber(itemNumber)
//                    .flatMap(foundItem -> externalItemDao.findByItemId(foundItem.getItemId()));
//        }
//
//        // If found, set itemId; otherwise throw exception
//        externalItem.ifPresentOrElse(ei -> {
//            itemInventoryBuilder.itemId(ei.getExternalServiceItem().getItemId());
//        }, () -> {
//            throw new ItemNumberNotFoundException(itemNumber);
//        });
//
//        // Check condition codes if not null
//        validateAndSetCondition(addItemInventoryRequest.getBoxConditionCode(), itemInventoryBuilder::boxConditionId);
//        validateAndSetCondition(addItemInventoryRequest.getItemConditionCode(), itemInventoryBuilder::itemConditionId);
//        validateAndSetCondition(addItemInventoryRequest.getInstructionsConditionCode(), itemInventoryBuilder::instructionsConditionId);
//
//        Optional.ofNullable(addItemInventoryRequest.getDescription()).ifPresent(itemInventoryBuilder::description);
//        Optional.ofNullable(addItemInventoryRequest.getBoxNumber()).ifPresent(itemInventoryBuilder::boxNumber);
//
//        validateAndSetNewOrUsed(addItemInventoryRequest.getNewOrUsed(), itemInventoryBuilder::newOrUsed);
//        validateAndSetCompleteness(addItemInventoryRequest.getCompleteness(), itemInventoryBuilder::completeness);
//        itemInventoryBuilder.sealed(addItemInventoryRequest.getSealed());
//        itemInventoryBuilder.builtOnce(addItemInventoryRequest.getBuiltOnce());
//        itemInventoryBuilder.forSale(addItemInventoryRequest.getForSale());
//        itemInventoryBuilder.quantity(addItemInventoryRequest.getQuantity());
//        itemInventoryBuilder.active(addItemInventoryRequest.getActive());
//        itemInventoryBuilder.uuid(UUID.randomUUID().toString());
//
//        ItemInventory itemInventory = itemInventoryBuilder.build();
//        itemInventoryDao.insert(itemInventory);
//        log.info("Item Inventory {}", itemInventory);

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
