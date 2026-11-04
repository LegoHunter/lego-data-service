package com.vattima.lego.inventory.service;

import io.legohunter.data.dao.CarrierDao;
import io.legohunter.data.dao.BricklinkMarketplaceListingDao;
import io.legohunter.data.dao.ConditionDao;
import io.legohunter.data.dao.ExternalCatalogItemDao;
import io.legohunter.data.dao.ExternalServiceDao;
import io.legohunter.data.dao.ItemInventoryDao;
import io.legohunter.data.dao.ItemInventoryExternalCatalogItemDao;
import io.legohunter.data.dao.ItemInventoryPhotoDao;
import io.legohunter.data.dao.ItemInventorySaleIntentDao;
import io.legohunter.data.dao.ItemInventoryStateDao;
import io.legohunter.data.dao.MarketplaceListingDao;
import io.legohunter.data.dao.MarketplaceListingSyncRequestDao;
import io.legohunter.data.dao.PaymentDao;
import io.legohunter.data.dao.PaymentPlatformDao;
import io.legohunter.data.dao.TransactionCostDao;
import io.legohunter.data.dao.TransactionItemDao;
import io.legohunter.data.dao.TransactionPlatformDao;
import io.legohunter.data.dao.TransactionsDao;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
		"lego.data.enabled=false",
		"management.tracing.enabled=false",
		"otel.sdk.disabled=true",
		"spring.autoconfigure.exclude=io.opentelemetry.instrumentation.spring.autoconfigure.OpenTelemetryAutoConfiguration,io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.logging.OpenTelemetryAppenderAutoConfiguration,org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration"
})
@ExtendWith(OutputCaptureExtension.class)
class LegoDataServiceApplicationTests {

	@Autowired
	private ApplicationContext context;

	@MockitoBean
	private CarrierDao carrierDao;

	@MockitoBean
	private BricklinkMarketplaceListingDao bricklinkMarketplaceListingDao;

	@MockitoBean
	private ConditionDao conditionDao;

	@MockitoBean
	private ExternalCatalogItemDao externalCatalogItemDao;

	@MockitoBean
	private ExternalServiceDao externalServiceDao;

	@MockitoBean
	private ItemInventoryExternalCatalogItemDao itemInventoryExternalCatalogItemDao;

	@MockitoBean
	private ItemInventoryPhotoDao itemInventoryPhotoDao;

	@MockitoBean
	private ItemInventoryDao itemInventoryDao;

	@MockitoBean
	private ItemInventorySaleIntentDao itemInventorySaleIntentDao;

	@MockitoBean
	private ItemInventoryStateDao itemInventoryStateDao;

	@MockitoBean
	private MarketplaceListingDao marketplaceListingDao;

	@MockitoBean
	private MarketplaceListingSyncRequestDao marketplaceListingSyncRequestDao;

	@MockitoBean
	private PaymentDao paymentDao;

	@MockitoBean
	private PaymentPlatformDao paymentPlatformDao;

	@MockitoBean
	private TransactionCostDao transactionCostDao;

	@MockitoBean
	private TransactionItemDao transactionItemDao;

	@MockitoBean
	private TransactionPlatformDao transactionPlatformDao;

	@MockitoBean
	private TransactionsDao transactionsDao;

	@Test
	void contextLoads() {
	}

	@Test
	void noDefaultUserIsCreatedSoNoGeneratedPasswordIsLogged(CapturedOutput output) {
		// Spring Security's default in-memory user would log its password in clear text at startup.
		// The exclusion lives on the application class; this test no longer excludes it itself.
		assertThat(context.getBeansOfType(UserDetailsService.class)).isEmpty();
		assertThat(output.getAll()).doesNotContain("generated security password");
	}

}
