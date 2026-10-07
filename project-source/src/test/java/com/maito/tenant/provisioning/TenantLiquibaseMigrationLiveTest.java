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

        // Also ensure db_mitocrunch has tenant_payment_configs and tax columns
        ensureMitoCrunchSchema();

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
                "tenant_payment_configs",
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

    private void ensureMitoCrunchSchema() {
        String mitoUrl = "jdbc:postgresql://localhost:5432/db_mitocrunch";
        try (Connection conn = DriverManager.getConnection(mitoUrl, "maito_user", "maito_pass");
             Statement stmt = conn.createStatement()) {

            // Create tenant_payment_configs table if not exists
            stmt.execute("CREATE TABLE IF NOT EXISTS tenant_payment_configs (" +
                    "id UUID PRIMARY KEY DEFAULT gen_random_uuid(), " +
                    "provider VARCHAR(32) NOT NULL, " +
                    "is_enabled BOOLEAN NOT NULL DEFAULT true, " +
                    "is_test_mode BOOLEAN NOT NULL DEFAULT true, " +
                    "key_id VARCHAR(255) NOT NULL, " +
                    "secret_key VARCHAR(512) NOT NULL, " +
                    "webhook_secret VARCHAR(512) NOT NULL, " +
                    "merchant_account_id VARCHAR(255), " +
                    "created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(), " +
                    "updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(), " +
                    "CONSTRAINT uq_tenant_payment_provider UNIQUE (provider))");

            // Add tax_rate and hsn_code to catalog_product_variants if not exists
            stmt.execute("ALTER TABLE catalog_product_variants ADD COLUMN IF NOT EXISTS tax_rate NUMERIC(5,4)");
            stmt.execute("ALTER TABLE catalog_product_variants ADD COLUMN IF NOT EXISTS hsn_code VARCHAR(32)");
            stmt.execute("ALTER TABLE tenant_payment_configs ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0 NOT NULL");

        } catch (Exception ex) {
            System.err.println("Notice updating db_mitocrunch schema: " + ex.getMessage());
        }
    }
}
