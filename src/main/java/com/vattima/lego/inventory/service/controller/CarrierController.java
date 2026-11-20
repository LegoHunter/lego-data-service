package com.vattima.lego.inventory.service.controller;

import com.vattima.lego.inventory.service.logging.LogExecution;
import io.legohunter.data.dao.CarrierDao;
import io.legohunter.data.dto.Carrier;
import lombok.RequiredArgsConstructor;
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
    @LogExecution
    public ResponseEntity<List<Carrier>> findAll() {
        return ResponseEntity.ok(carrierDao.findAll());
    }

    @GetMapping("/{carrierCode}")
    @LogExecution
    public ResponseEntity<Optional<Carrier>> findByCarrierCode(@PathVariable("carrierCode") String carrierCode) {
        return ResponseEntity.ok(carrierDao.findCarrierByCode(carrierCode));
    }
}
