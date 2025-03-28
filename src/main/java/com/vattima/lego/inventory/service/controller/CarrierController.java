package com.vattima.lego.inventory.service.controller;

import lombok.RequiredArgsConstructor;
import net.lego.data.v2.dao.CarrierDao;
import net.lego.data.v2.dto.Carrier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/carriers")
@RequiredArgsConstructor
public class CarrierController {
    private final CarrierDao carrierDao;

    @GetMapping
    public ResponseEntity<List<Carrier>> findAll() {
        return ResponseEntity.ok(carrierDao.findAll());
    }

    @GetMapping("/{carrierCode}")
    public ResponseEntity<Optional<Carrier>> findByCarrierCode(@PathVariable("carrierCode") String carrierCode) {
        return ResponseEntity.ok(carrierDao.findCarrierByCode(carrierCode));
    }
}
