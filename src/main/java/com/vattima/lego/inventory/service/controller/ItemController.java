package com.vattima.lego.inventory.service.controller;

import lombok.RequiredArgsConstructor;
import net.lego.data.v2.dao.ItemDao;
import net.lego.data.v2.dto.Item;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/items")
@RequiredArgsConstructor
public class ItemController {

    private final ItemDao itemDao;

    @GetMapping
    public ResponseEntity<List<Item>> findAll() {
        return ResponseEntity.ok(itemDao.findAll());
    }

    @GetMapping("/{itemId}")
    public ResponseEntity<Optional<Item>> findByItemId(@PathVariable("itemId") Integer itemId) {
        return ResponseEntity.ok(itemDao.findByItemId(itemId));
    }

    @GetMapping("/number/{itemNumber}")
    public ResponseEntity<Optional<Item>> findByItemNumber(@PathVariable("itemNumber") String itemNumber) {
        return ResponseEntity.ok(itemDao.findByItemNumber(itemNumber));
    }
}
