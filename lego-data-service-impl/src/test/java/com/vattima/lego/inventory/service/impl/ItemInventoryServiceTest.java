package com.vattima.lego.inventory.service.impl;

import lombok.extern.slf4j.Slf4j;
import net.lego.data.v2.dao.ConditionDao;
import net.lego.data.v2.dao.ExternalItemDao;
import net.lego.data.v2.dao.ItemDao;
import net.lego.data.v2.dao.ItemInventoryDao;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.*;

@Slf4j
class ItemInventoryServiceTest {

    private ItemDao itemDao;
    private ExternalItemDao externalItemDao;
    private ConditionDao conditionDao;
    private ItemInventoryDao itemInventoryDao;

    @Test
    void addItemInventory() {
        itemDao = mock(ItemDao.class);
        when(itemDao.findAll()).thenReturn(List.of());
        log.info("{}", itemDao.findAll());
    }
}