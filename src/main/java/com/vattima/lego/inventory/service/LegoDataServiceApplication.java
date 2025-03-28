package com.vattima.lego.inventory.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.vattima", "net.lego", "net.bricklink"})
@RequiredArgsConstructor
@Slf4j
public class LegoDataServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(LegoDataServiceApplication.class, args);
    }
}
