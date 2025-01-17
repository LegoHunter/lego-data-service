package com.vattima.lego.inventory.service.impl;

import com.vattima.lego.inventory.service.dto.AddItemInventoryRequest;
import com.vattima.lego.inventory.service.exception.CompletenessNotFoundException;
import com.vattima.lego.inventory.service.exception.ConditionCodeNotFoundException;
import com.vattima.lego.inventory.service.exception.ItemNumberNotFoundException;
import com.vattima.lego.inventory.service.exception.NewOrUsedNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.lego.data.v2.dao.ConditionDao;
import net.lego.data.v2.dao.ExternalItemDao;
import net.lego.data.v2.dao.ItemDao;
import net.lego.data.v2.dao.ItemInventoryDao;
import net.lego.data.v2.dto.ExternalItem;
import net.lego.data.v2.dto.ItemInventory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

@Component
@RequiredArgsConstructor
@Slf4j
public class ItemInventoryService {

    private final ItemDao itemDao;
    private final ExternalItemDao externalItemDao;
    private final ConditionDao conditionDao;
    private final ItemInventoryDao itemInventoryDao;

    public ItemInventory addItemInventory(AddItemInventoryRequest addItemInventoryRequest) {
        final String itemNumber = addItemInventoryRequest.getItemNumber();

        ItemInventory.ItemInventoryBuilder itemInventoryBuilder = ItemInventory.builder();

        // Find itemNumber in external items
        Optional<ExternalItem> externalItem = externalItemDao.findByExternalNumber(itemNumber);

        // If external item not found, attempt to find item using itemNumber and then get external item.
        if (externalItem.isEmpty()) {
            externalItem = itemDao.findByItemNumber(itemNumber)
                    .flatMap(foundItem -> externalItemDao.findByItemId(foundItem.getItemId()));
        }

        // If found, set itemId; otherwise throw exception
        externalItem.ifPresentOrElse(ei -> {
            itemInventoryBuilder.itemId(ei.getExternalServiceItem().getItemId());
        }, () -> {
            throw new ItemNumberNotFoundException(itemNumber);
        });

        // Check condition codes if not null
        validateAndSetCondition(addItemInventoryRequest.getBoxConditionCode(), itemInventoryBuilder::boxConditionId);
        validateAndSetCondition(addItemInventoryRequest.getItemConditionCode(), itemInventoryBuilder::itemConditionId);
        validateAndSetCondition(addItemInventoryRequest.getInstructionsConditionCode(), itemInventoryBuilder::instructionsConditionId);

        Optional.ofNullable(addItemInventoryRequest.getDescription()).ifPresent(itemInventoryBuilder::description);
        Optional.ofNullable(addItemInventoryRequest.getBoxNumber()).ifPresent(itemInventoryBuilder::boxNumber);

        validateAndSetNewOrUsed(addItemInventoryRequest.getNewOrUsed(), itemInventoryBuilder::newOrUsed);
        validateAndSetCompleteness(addItemInventoryRequest.getCompleteness(), itemInventoryBuilder::completeness);
        itemInventoryBuilder.sealed(addItemInventoryRequest.getSealed());
        itemInventoryBuilder.builtOnce(addItemInventoryRequest.getBuiltOnce());
        itemInventoryBuilder.forSale(addItemInventoryRequest.getForSale());
        itemInventoryBuilder.quantity(addItemInventoryRequest.getQuantity());
        itemInventoryBuilder.active(addItemInventoryRequest.getActive());
        itemInventoryBuilder.uuid(UUID.randomUUID().toString());

        ItemInventory itemInventory = itemInventoryBuilder.build();
        itemInventoryDao.insert(itemInventory);
        log.info("Item Inventory {}", itemInventory);

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

