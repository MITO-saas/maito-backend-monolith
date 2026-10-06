-- =============================================================================
-- Maito Multi-Tenant PostgreSQL 16 Idempotent Database Initialization
-- =============================================================================

-- 1. Ensure application role exists
DO 
BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'maito_user') THEN
        CREATE ROLE maito_user WITH LOGIN PASSWORD 'maito_pass' SUPERUSER CREATEDB;
    END IF;
END
;

-- 2. Idempotent Database Creation for Master Control Plane & Tenant DBs
SELECT 'CREATE DATABASE maito_db OWNER maito_user'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'maito_db')\gexec

SELECT 'CREATE DATABASE db_mitocrunch OWNER maito_user'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'db_mitocrunch')\gexec

SELECT 'CREATE DATABASE db_vijiyasolar OWNER maito_user'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'db_vijiyasolar')\gexec

SELECT 'CREATE DATABASE db_everrites OWNER maito_user'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'db_everrites')\gexec

-- 3. Grant Permissions
GRANT ALL PRIVILEGES ON DATABASE maito_db TO maito_user;
GRANT ALL PRIVILEGES ON DATABASE db_mitocrunch TO maito_user;
GRANT ALL PRIVILEGES ON DATABASE db_vijiyasolar TO maito_user;
GRANT ALL PRIVILEGES ON DATABASE db_everrites TO maito_user;