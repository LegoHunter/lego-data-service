package com.vattima.lego.inventory.service;

import io.legohunter.data.dao.CarrierDao;
import io.legohunter.data.dao.ConditionDao;
import io.legohunter.data.dao.ExternalCatalogItemDao;
import io.legohunter.data.dao.ItemInventoryDao;
import io.legohunter.data.dao.ItemInventoryExternalCatalogItemDao;
import io.legohunter.data.dao.ItemInventorySaleIntentDao;
import io.legohunter.data.dao.ItemInventoryStateDao;
import io.legohunter.data.dao.PaymentDao;
import io.legohunter.data.dao.PaymentPlatformDao;
import io.legohunter.data.dao.TransactionCostDao;
import io.legohunter.data.dao.TransactionItemDao;
import io.legohunter.data.dao.TransactionPlatformDao;
import io.legohunter.data.dao.TransactionsDao;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
		"lego.data.enabled=false",
		"management.tracing.enabled=false",
		"otel.sdk.disabled=true",
		"spring.autoconfigure.exclude=io.opentelemetry.instrumentation.spring.autoconfigure.OpenTelemetryAutoConfiguration,io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.logging.OpenTelemetryAppenderAutoConfiguration,org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration,org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration"
})
class LegoDataServiceApplicationTests {

	@MockitoBean
	private CarrierDao carrierDao;

	@MockitoBean
	private ConditionDao conditionDao;

	@MockitoBean
	private ExternalCatalogItemDao externalCatalogItemDao;

	@MockitoBean
	private ItemInventoryExternalCatalogItemDao itemInventoryExternalCatalogItemDao;

	@MockitoBean
	private ItemInventoryDao itemInventoryDao;

	@MockitoBean
	private ItemInventorySaleIntentDao itemInventorySaleIntentDao;

	@MockitoBean
	private ItemInventoryStateDao itemInventoryStateDao;

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

}
