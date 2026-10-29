-- Separate administrator preparation; this creates no login, business row, or admission.
DO $reader_role$
DECLARE actual RECORD;
BEGIN
    IF NOT EXISTS(SELECT 1 FROM pg_catalog.pg_roles WHERE rolname='firefly_product_definition_deposit_enrollment_read') THEN
        CREATE ROLE firefly_product_definition_deposit_enrollment_read
            NOLOGIN NOINHERIT NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE NOREPLICATION;
    END IF;
    SELECT * INTO STRICT actual FROM pg_catalog.pg_roles WHERE rolname='firefly_product_definition_deposit_enrollment_read';
    IF actual.rolcanlogin OR actual.rolinherit OR actual.rolsuper OR actual.rolbypassrls
            OR actual.rolcreatedb OR actual.rolcreaterole OR actual.rolreplication
            OR EXISTS(SELECT 1 FROM pg_catalog.pg_auth_members WHERE member=actual.oid)
            OR EXISTS(SELECT 1 FROM pg_catalog.pg_namespace WHERE nspowner=actual.oid)
            OR EXISTS(SELECT 1 FROM pg_catalog.pg_class WHERE relowner=actual.oid)
            OR EXISTS(SELECT 1 FROM pg_catalog.pg_proc WHERE proowner=actual.oid)
            OR EXISTS(SELECT 1 FROM pg_catalog.pg_type WHERE typowner=actual.oid)
            OR EXISTS(SELECT 1 FROM pg_catalog.pg_database WHERE datdba=actual.oid) THEN
        RAISE EXCEPTION 'Exact protected deposit reader role required' USING ERRCODE='42501';
    END IF;
END $reader_role$;
GRANT USAGE ON SCHEMA public TO firefly_product_definition_deposit_enrollment_read;
