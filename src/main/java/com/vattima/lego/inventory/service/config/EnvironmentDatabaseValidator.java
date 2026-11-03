package com.vattima.lego.inventory.service.config;

import io.legohunter.data.environment.EnvironmentDatabasePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * Refuses to start the application when its configured environment does not match the database it
 * is connected to.
 *
 * <p>The draft settings' production flag decides whether drafts are stamped for PROD (public) or
 * forced into a lower environment's stockroom, so it must agree with the connected schema: PROD
 * only on {@code lego_prod}, SANDBOX only on {@code lego_sandbox}, DEV only on {@code lego_dev}, and
 * local on anything except {@code lego_prod}. The rules are shared with lego-data-ingress
 * ({@link EnvironmentDatabasePolicy}).
 *
 * <p>The schema is read from the database at startup, so the application also refuses to start
 * while the database cannot be reached: an unverified environment is treated as unsafe. With no
 * data source at all ({@code lego.data.enabled=false}) the service cannot reach any database, and
 * there is nothing to check.
 */
@Component
@RequiredArgsConstructor
public class EnvironmentDatabaseValidator implements InitializingBean {

    private final MarketplaceListingDraftProperties properties;
    private final ObjectProvider<DataSource> dataSource;

    @Override
    public void afterPropertiesSet() {
        DataSource connected = dataSource.getIfAvailable();
        if (connected == null) {
            return;
        }
        List<String> violations = EnvironmentDatabasePolicy.violations(
                MarketplaceListingDraftStockroomValidator.PREFIX,
                properties.getEnvironmentCode(),
                properties.isProduction(),
                connectedSchema(connected));
        if (!violations.isEmpty()) {
            throw new IllegalStateException("Configured environment does not match the connected database:\n - "
                    + String.join("\n - ", violations));
        }
    }

    static String connectedSchema(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT DATABASE()")) {
            return resultSet.next() ? resultSet.getString(1) : null;
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot verify the configured environment: reading the connected database schema failed: "
                    + e.getMessage(), e);
        }
    }
}
