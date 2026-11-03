-- Read-only serving uses a trusted application projection after current policy evaluation.
-- This context is not PostgreSQL verification of a JWT or a policy decision.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_catalog.pg_roles
        WHERE rolname = 'firefly_product_definition_serving'
          AND NOT rolcanlogin AND NOT rolsuper AND NOT rolbypassrls
    ) THEN
        RAISE EXCEPTION USING ERRCODE = '23514', MESSAGE = 'PRODUCT_DEFINITION_SERVING_ROLE_REQUIRED';
    END IF;
    IF pg_catalog.pg_has_role('firefly_product_definition_serving', 'firefly_product_definition_guard', 'MEMBER')
      OR pg_catalog.has_schema_privilege('firefly_product_definition_serving', 'firefly_product_definition_private', 'USAGE,CREATE')
      OR EXISTS (
          SELECT 1 FROM pg_catalog.pg_class c
          JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
          WHERE n.nspname = 'public' AND c.relname LIKE 'product_definition_%'
            AND c.relkind IN ('r', 'p')
            AND (pg_catalog.pg_has_role('firefly_product_definition_serving', c.relowner, 'MEMBER')
              OR pg_catalog.has_table_privilege('firefly_product_definition_serving', c.oid,
                   'INSERT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN')
              OR pg_catalog.has_any_column_privilege('firefly_product_definition_serving', c.oid,
                   'INSERT,UPDATE,REFERENCES'))
      ) THEN
        RAISE EXCEPTION USING ERRCODE = '23514', MESSAGE = 'PRODUCT_DEFINITION_SERVING_ROLE_REQUIRED';
    END IF;
END
$$;

CREATE FUNCTION public.product_definition_serving_runtime_allowed() RETURNS BOOLEAN
LANGUAGE SQL STABLE SECURITY INVOKER SET search_path = pg_catalog AS $$
    SELECT current_user = session_user AND r.rolcanlogin AND NOT r.rolsuper AND NOT r.rolbypassrls
       AND pg_catalog.pg_has_role(current_user, 'firefly_product_definition_serving', 'USAGE')
       AND NOT pg_catalog.pg_has_role(current_user, 'firefly_product_definition_guard', 'MEMBER')
       AND NOT pg_catalog.has_database_privilege(current_user, pg_catalog.current_database(), 'CREATE,TEMPORARY')
       AND NOT pg_catalog.has_schema_privilege(current_user, 'public', 'CREATE')
       AND NOT pg_catalog.has_schema_privilege(current_user, 'firefly_product_definition_private', 'USAGE,CREATE')
       AND NOT EXISTS (
           SELECT 1 FROM pg_catalog.pg_class c
           JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
           WHERE n.nspname = 'public' AND c.relname LIKE 'product_definition_%'
             AND c.relkind IN ('r', 'p')
             AND (pg_catalog.pg_has_role(current_user, c.relowner, 'MEMBER')
               OR pg_catalog.has_table_privilege(current_user, c.oid,
                    'INSERT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN')
               OR pg_catalog.has_any_column_privilege(current_user, c.oid,
                    'INSERT,UPDATE,REFERENCES'))
       )
    FROM pg_catalog.pg_roles r WHERE r.rolname = current_user
$$;
REVOKE ALL ON FUNCTION public.product_definition_serving_runtime_allowed() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.product_definition_serving_runtime_allowed()
    TO firefly_product_definition_serving;

CREATE FUNCTION public.product_definition_serving_context_matches(tenant UUID, root UUID) RETURNS BOOLEAN
LANGUAGE plpgsql VOLATILE SECURITY INVOKER SET search_path = pg_catalog AS $$
DECLARE
    until_value TEXT;
    until_millis BIGINT;
    now_millis BIGINT;
BEGIN
    IF NOT COALESCE(public.product_definition_serving_runtime_allowed(), FALSE)
       OR tenant IS NULL OR root IS NULL
       OR pg_catalog.current_setting('firefly.product_definition.read_kind', TRUE) IS DISTINCT FROM 'ACCOUNT_TARIFF_V1'
       OR pg_catalog.current_setting('firefly.product_definition.read_tenant_id', TRUE) IS DISTINCT FROM tenant::TEXT
       OR pg_catalog.current_setting('firefly.product_definition.read_root_id', TRUE) IS DISTINCT FROM root::TEXT THEN
        RETURN FALSE;
    END IF;
    until_value := pg_catalog.current_setting('firefly.product_definition.read_until_epoch_millis', TRUE);
    IF until_value IS NULL OR until_value !~ '^[0-9]{1,15}$' THEN RETURN FALSE; END IF;
    until_millis := until_value::BIGINT;
    now_millis := FLOOR(EXTRACT(EPOCH FROM pg_catalog.clock_timestamp()) * 1000)::BIGINT;
    RETURN until_millis <= 253402300799999 AND now_millis < until_millis
        AND until_millis <= now_millis + 30000;
END
$$;
REVOKE ALL ON FUNCTION public.product_definition_serving_context_matches(UUID, UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.product_definition_serving_context_matches(UUID, UUID)
    TO firefly_product_definition_serving;

CREATE FUNCTION public.open_product_definition_serving_read(
    tenant UUID, root UUID, actor_until BIGINT, permit_until BIGINT)
RETURNS TABLE(observed_at_epoch_millis BIGINT, valid_until_epoch_millis BIGINT)
LANGUAGE plpgsql VOLATILE SECURITY INVOKER SET search_path = pg_catalog AS $$
DECLARE
    now_millis BIGINT;
    until_millis BIGINT;
BEGIN
    now_millis := FLOOR(EXTRACT(EPOCH FROM pg_catalog.clock_timestamp()) * 1000)::BIGINT;
    IF NOT COALESCE(public.product_definition_serving_runtime_allowed(), FALSE)
       OR tenant IS NULL OR root IS NULL OR actor_until IS NULL OR permit_until IS NULL
       OR actor_until > 253402300799999 OR permit_until > 253402300799999
       OR actor_until <= now_millis OR permit_until <= now_millis THEN
        RAISE EXCEPTION USING ERRCODE = '42501', MESSAGE = 'CURRENT_PRODUCT_READ_CONTEXT_REQUIRED';
    END IF;
    until_millis := LEAST(actor_until, permit_until, now_millis + 30000);
    PERFORM pg_catalog.set_config('firefly.product_definition.read_kind', 'ACCOUNT_TARIFF_V1', TRUE);
    PERFORM pg_catalog.set_config('firefly.product_definition.read_tenant_id', tenant::TEXT, TRUE);
    PERFORM pg_catalog.set_config('firefly.product_definition.read_root_id', root::TEXT, TRUE);
    PERFORM pg_catalog.set_config('firefly.product_definition.read_until_epoch_millis', until_millis::TEXT, TRUE);
    RETURN QUERY SELECT now_millis, until_millis;
END
$$;
REVOKE ALL ON FUNCTION public.open_product_definition_serving_read(UUID, UUID, BIGINT, BIGINT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.open_product_definition_serving_read(UUID, UUID, BIGINT, BIGINT)
    TO firefly_product_definition_serving;

GRANT USAGE ON SCHEMA public TO firefly_product_definition_serving;

GRANT SELECT ON public.product_definition_root, public.product_definition_revision,
    public.product_definition_publication, public.product_definition_publication_control
    TO firefly_product_definition_serving;

CREATE POLICY product_definition_serving_select ON public.product_definition_root
    FOR SELECT TO firefly_product_definition_serving
    USING (public.product_definition_serving_context_matches(tenant_id, root_id)
        AND profile_code = 'ACCOUNT_TARIFF_V1' AND profile_version = 1);

CREATE POLICY product_definition_serving_select ON public.product_definition_revision
    FOR SELECT TO firefly_product_definition_serving
    USING (public.product_definition_serving_context_matches(tenant_id, root_id)
        AND EXISTS (SELECT 1 FROM public.product_definition_root r
            WHERE r.tenant_id = product_definition_revision.tenant_id
              AND r.root_id = product_definition_revision.root_id));

CREATE POLICY product_definition_serving_select ON public.product_definition_publication
    FOR SELECT TO firefly_product_definition_serving
    USING (public.product_definition_serving_context_matches(tenant_id, root_id)
        AND EXISTS (SELECT 1 FROM public.product_definition_root r
            WHERE r.tenant_id = product_definition_publication.tenant_id
              AND r.root_id = product_definition_publication.root_id));

CREATE POLICY product_definition_serving_select ON public.product_definition_publication_control
    FOR SELECT TO firefly_product_definition_serving
    USING (public.product_definition_serving_context_matches(tenant_id, root_id)
        AND EXISTS (SELECT 1 FROM public.product_definition_root r
            WHERE r.tenant_id = product_definition_publication_control.tenant_id
              AND r.root_id = product_definition_publication_control.root_id));
