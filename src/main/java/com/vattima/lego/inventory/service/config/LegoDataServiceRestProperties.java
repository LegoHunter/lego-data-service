package com.vattima.lego.inventory.service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "lego.data.service")
public class LegoDataServiceRestProperties {
    private String url;
}
