package com.vattima.lego.inventory.service.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The startup check reads the schema with {@code SELECT DATABASE()} and refuses to start when the
 * configured environment does not match it. The rule combinations are covered in lego-data's
 * {@code EnvironmentDatabasePolicyTest}; these tests cover the wiring.
 */
class EnvironmentDatabaseValidatorTest {

    @ParameterizedTest(name = "{0} on {2}")
    @CsvSource({
            "sandbox, false, lego_sandbox",
            "dev,     false, lego_dev",
            "prod,    true,  lego_prod",
            "local,   false, lego_sandbox"
    })
    void startsWhenTheEnvironmentMatchesTheConnectedSchema(String code, boolean production, String schema) throws SQLException {
        assertThatCode(() -> validator(code, production, connectedTo(schema)).afterPropertiesSet())
                .doesNotThrowAnyException();
    }

    @Test
    void refusesALowerEnvironmentConnectedToProd() throws SQLException {
        assertThatThrownBy(() -> validator("sandbox", false, connectedTo("lego_prod")).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Configured environment does not match the connected database:\n"
                        + " - lego.marketplace-listing-drafts.environment-code 'sandbox' must be connected to database schema 'lego_sandbox', not 'lego_prod'");
    }

    @Test
    void refusesLocalConnectedToProd() throws SQLException {
        assertThatThrownBy(() -> validator("local", false, connectedTo("lego_prod")).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("environment-code 'local' is not PROD but is connected to PROD's database schema 'lego_prod'");
    }

    @Test
    void refusesProdConnectedToALowerEnvironment() throws SQLException {
        assertThatThrownBy(() -> validator("prod", true, connectedTo("lego_dev")).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("environment-code 'prod' must be connected to database schema 'lego_prod', not 'lego_dev'");
    }

    @Test
    void refusesToStartWhenTheSchemaCannotBeRead() throws SQLException {
        DataSource unreachable = mock(DataSource.class);
        when(unreachable.getConnection()).thenThrow(new SQLException("Communications link failure"));

        assertThatThrownBy(() -> validator("sandbox", false, unreachable).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cannot verify the configured environment: reading the connected database schema failed: Communications link failure");
    }

    @Test
    void closesTheConnectionItOpens() throws SQLException {
        DataSource dataSource = connectedTo("lego_sandbox");
        Connection connection = dataSource.getConnection();

        validator("sandbox", false, dataSource).afterPropertiesSet();

        verify(connection).close();
    }

    @Test
    void hasNothingToCheckWithoutADataSource() {
        assertThatCode(() -> new EnvironmentDatabaseValidator(properties("prod", false), dataSources(null)).afterPropertiesSet())
                .doesNotThrowAnyException();
    }

    private static EnvironmentDatabaseValidator validator(String code, boolean production, DataSource dataSource) {
        return new EnvironmentDatabaseValidator(properties(code, production), dataSources(dataSource));
    }

    private static MarketplaceListingDraftProperties properties(String code, boolean production) {
        MarketplaceListingDraftProperties properties = new MarketplaceListingDraftProperties();
        properties.setEnvironmentCode(code);
        properties.setProduction(production);
        return properties;
    }

    private static ObjectProvider<DataSource> dataSources(DataSource dataSource) {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        if (dataSource != null) {
            beans.addBean("dataSource", dataSource);
        }
        return beans.getBeanProvider(DataSource.class);
    }

    private static DataSource connectedTo(String schema) throws SQLException {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getString(1)).thenReturn(schema);
        Statement statement = mock(Statement.class);
        when(statement.executeQuery("SELECT DATABASE()")).thenReturn(resultSet);
        Connection connection = mock(Connection.class);
        when(connection.createStatement()).thenReturn(statement);
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenReturn(connection);
        return dataSource;
    }
}
