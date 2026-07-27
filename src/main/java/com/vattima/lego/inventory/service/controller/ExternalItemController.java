package com.vattima.lego.inventory.service.controller;

import com.vattima.lego.inventory.service.logging.LogExecution;
import io.legohunter.data.dao.ExternalCatalogItemDao;
import io.legohunter.data.dto.ExternalCatalogItem;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.Set;

import static io.legohunter.data.dto.ExternalService.Service.BRICKLINK;

@RestController
@RequestMapping("/api/v1/items")
@RequiredArgsConstructor
public class ExternalItemController {

    private final ExternalCatalogItemDao externalCatalogItemDao;

    @GetMapping
    @LogExecution
    public ResponseEntity<Set<ExternalCatalogItem>> findAll() {
        return ResponseEntity.ok(externalCatalogItemDao.findAll());
    }

    @GetMapping("/{itemId}")
    @LogExecution
    public ResponseEntity<Optional<ExternalCatalogItem>> findBy(@PathVariable("itemId") Integer itemId) {
        return ResponseEntity.ok(externalCatalogItemDao.findByExternalCatalogItemId(itemId));
    }

    @GetMapping("/number/{itemNumber}")
    @LogExecution
    public ResponseEntity<Optional<ExternalCatalogItem>> findByItemNumber(@PathVariable("itemNumber") String itemNumber) {
        return ResponseEntity.ok(externalCatalogItemDao.findByExternalServiceIdAndExternalItemKey(BRICKLINK.getExternalServiceId(), itemNumber));
    }
}
