package com.vattima.lego.inventory.service.controller;

import com.vattima.lego.inventory.service.logging.LogExecution;
import lombok.RequiredArgsConstructor;
import io.legohunter.data.dao.ExternalItemDao;
import io.legohunter.data.dto.ExternalItem;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.Set;

import static io.legohunter.data.dto.ExternalService.ExternalServiceType.BRICKLINK;

@RestController
@RequestMapping("/api/v1/items")
@RequiredArgsConstructor
public class ExternalItemController {

    private final ExternalItemDao externalItemDao;

    @GetMapping
    @LogExecution
    public ResponseEntity<Set<ExternalItem>> findAll() {
        return ResponseEntity.ok(externalItemDao.findAllByExternalService(BRICKLINK.name()));
    }

    @GetMapping("/{itemId}")
    @LogExecution
    public ResponseEntity<Optional<ExternalItem>> findBy(@PathVariable("itemId") Integer itemId) {
        return ResponseEntity.ok(externalItemDao.findByExternalServiceAndUniqueId(BRICKLINK.getExternalServiceId(), itemId));
    }

    @GetMapping("/number/{itemNumber}")
    @LogExecution
    public ResponseEntity<Optional<ExternalItem>> findByItemNumber(@PathVariable("itemNumber") String itemNumber) {
        return ResponseEntity.ok(externalItemDao.findByExternalServiceAndNumber(BRICKLINK.getExternalServiceId(), itemNumber));
    }
}
