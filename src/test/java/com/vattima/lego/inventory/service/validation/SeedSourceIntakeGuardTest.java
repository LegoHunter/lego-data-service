package com.vattima.lego.inventory.service.validation;

import com.vattima.lego.inventory.service.dto.AddItemInventoryRequest;
import com.vattima.lego.inventory.service.dto.ItemInventoryRequest;
import com.vattima.lego.inventory.service.dto.SeedSourceRequest;
import com.vattima.lego.inventory.service.exception.ValidationException;
import io.legohunter.data.dao.ItemInventorySeedSourceDao;
import io.legohunter.data.dto.ItemInventorySeedSource;
import io.legohunter.lego.data.environment.LegoEnvironmentProperties;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SeedSourceIntakeGuardTest {
    private static final String RUN = "11111111-1111-1111-1111-111111111111";

    private final LegoEnvironmentProperties environment = new LegoEnvironmentProperties();
    private final ItemInventorySeedSourceDao dao = mock(ItemInventorySeedSourceDao.class);
    private final SeedSourceIntakeGuard guard = new SeedSourceIntakeGuard(environment, dao);

    @Test
    void anOrdinaryAcquisitionIsNotChecked() {
        environment.setProduction(true);

        assertThatCode(() -> guard.validate(request(item(null), item(null)))).doesNotThrowAnyException();

        verify(dao, never()).findBySourceSystemAndSourceKey(any(), any());
    }

    @Test
    void seededItemsAreRefusedInProd() {
        environment.setCode("prod");
        environment.setProduction(true);

        assertThatThrownBy(() -> guard.validate(request(item(seed("28")))))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("refused in PROD");

        verify(dao, never()).findBySourceSystemAndSourceKey(any(), any());
    }

    @Test
    void newSourceItemsAreAcceptedInALowerEnvironment() {
        environment.setCode("sandbox");
        when(dao.findBySourceSystemAndSourceKey(any(), any())).thenReturn(Optional.empty());

        assertThatCode(() -> guard.validate(request(item(seed("28")), item(seed("30")), item(null))))
                .doesNotThrowAnyException();
    }

    @Test
    void theSameSourceItemTwiceInOneRequestIsRefused() {
        environment.setCode("sandbox");
        when(dao.findBySourceSystemAndSourceKey(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> guard.validate(request(item(seed("28")), item(seed("28")))))
                .isInstanceOf(ValidationException.class)
                .hasMessage("seedSource LEGO_PROD_LEGACY:28 appears more than once in the request");
    }

    @Test
    void anAlreadySeededSourceItemIsRefused() {
        environment.setCode("sandbox");
        when(dao.findBySourceSystemAndSourceKey("LEGO_PROD_LEGACY", "28"))
                .thenReturn(Optional.of(ItemInventorySeedSource.builder().itemInventoryId(955).build()));

        assertThatThrownBy(() -> guard.validate(request(item(seed("28")))))
                .isInstanceOf(ValidationException.class)
                .hasMessage("seedSource LEGO_PROD_LEGACY:28 is already seeded as item 955");
    }

    private static SeedSourceRequest seed(String sourceKey) {
        return SeedSourceRequest.builder()
                .sourceSystem(ItemInventorySeedSource.SOURCE_SYSTEM_LEGO_PROD_LEGACY)
                .sourceKey(sourceKey)
                .seedRunId(RUN)
                .build();
    }

    private static ItemInventoryRequest item(SeedSourceRequest seedSource) {
        return ItemInventoryRequest.builder().seedSource(seedSource).build();
    }

    private static AddItemInventoryRequest request(ItemInventoryRequest... items) {
        return AddItemInventoryRequest.builder().inventoryItems(new ArrayList<>(Arrays.asList(items))).build();
    }
}
