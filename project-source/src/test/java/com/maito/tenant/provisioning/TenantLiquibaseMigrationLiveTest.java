package com.maito.tenant.provisioning;

import liquibase.Contexts;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TenantLiquibaseMigrationLiveTest {

    @Test
    @DisplayName("Applies tenant changelog against db_everrites and provisions all 10 domain schemas")
    void testTenantChangelogOnEverrites() throws Exception {
        String url = "jdbc:postgresql://localhost:5432/db_everrites";
        try (Connection conn = DriverManager.getConnection(url, "maito_user", "maito_pass")) {
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(conn));

            try (Liquibase liquibase = new Liquibase(
                    "db/changelog/tenant/tenant-base-schema.xml",
                    new ClassLoaderResourceAccessor(),
                    database)) {
                liquibase.update(new Contexts());
            }
        }

        List<String> tables = new ArrayList<>();
        try (Connection queryConn = DriverManager.getConnection(url, "maito_user", "maito_pass");
             Statement stmt = queryConn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT tablename FROM pg_tables WHERE schemaname = 'public' ORDER BY tablename")) {
            while (rs.next()) {
                tables.add(rs.getString("tablename"));
            }
        }

        assertThat(tables).contains(
                "tenant_user_profiles",
                "tenant_audit_log",
                "cms_pages",
                "cms_sections",
                "cms_themes",
                "catalog_categories",
                "catalog_products",
                "catalog_product_variants",
                "inventory_levels",
                "carts",
                "cart_items",
                "orders",
                "order_items",
                "promotions",
                "carrier_configurations",
                "shipments",
                "shipment_checkpoints",
                "audit_logs",
                "notification_logs",
                "wallets",
                "wallet_transactions",
                "payment_transactions",
                "return_requests",
                "return_items",
                "support_tickets",
                "ticket_messages",
                "b2b_partners",
                "b2b_price_tiers",
                "b2b_invoices",
                "b2b_credit_ledgers"
        );
    }
}