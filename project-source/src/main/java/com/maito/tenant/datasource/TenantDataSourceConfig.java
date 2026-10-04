package com.maito.tenant.datasource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * Primary DataSource configuration setting up the dynamic multi-tenant routing pipeline.
 */
@Configuration
public class TenantDataSourceConfig {

    @Value("${spring.datasource.url:jdbc:postgresql://localhost:5432/maito_db}")
    private String masterUrl;

    @Value("${spring.datasource.username:maito_user}")
    private String masterUsername;

    @Value("${spring.datasource.password:maito_pass}")
    private String masterPassword;

    @Value("${spring.datasource.driver-class-name:org.postgresql.Driver}")
    private String driverClassName;

    @Bean
    public DataSource masterDataSource() {
        HikariConfig config = new HikariConfig();
        config.setPoolName("HikariPool-Master-ControlPlane");
        config.setJdbcUrl(masterUrl);
        config.setUsername(masterUsername);
        config.setPassword(masterPassword);
        config.setDriverClassName(driverClassName);
        config.setMinimumIdle(5);
        config.setMaximumPoolSize(15);
        config.setIdleTimeout(300000);
        config.setConnectionTimeout(20000);
        return new HikariDataSource(config);
    }

    @Bean
    public DynamicTenantRoutingDataSource dynamicTenantRoutingDataSource(DataSource masterDataSource) {
        return new DynamicTenantRoutingDataSource(masterDataSource);
    }

    @Bean
    @Primary
    public DataSource dataSource(DynamicTenantRoutingDataSource dynamicTenantRoutingDataSource) {
        return dynamicTenantRoutingDataSource;
    }
}