package com.vattima.lego.inventory.service.internal.config;

import com.vattima.lego.inventory.service.api.InventoryService;
import com.vattima.lego.inventory.service.internal.impl.InternalInventoryService;
import io.legohunter.data.dao.ItemInventoryDao;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class InternalInventoryServiceConfiguration {
    @Bean
    public InventoryService internalInventoryService(final ItemInventoryDao itemInventoryDao) {
        return new InternalInventoryService(itemInventoryDao);
    }
}
