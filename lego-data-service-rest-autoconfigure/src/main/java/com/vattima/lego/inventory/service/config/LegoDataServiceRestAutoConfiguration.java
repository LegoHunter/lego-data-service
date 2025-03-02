package com.vattima.lego.inventory.service.config;

import com.vattima.lego.inventory.service.controller.ItemController;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;

@Configuration
@ConditionalOnClass(ItemController.class)
@EnableConfigurationProperties(LegoDataServiceRestProperties.class)
@ComponentScan(basePackages = {"com.vattima.lego.inventory.service.controller"})
@RequiredArgsConstructor
public class LegoDataServiceRestAutoConfiguration {
    private final LegoDataServiceRestProperties legoDataServiceRestProperties;

}
