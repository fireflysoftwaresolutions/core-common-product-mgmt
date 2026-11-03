-- Initial durable aggregate. No runtime allow policy is installed here.
-- Public heads/evidence must never carry private cost counters or fingerprints.

ALTER TABLE public.product ADD CONSTRAINT product_tenant_identity
    UNIQUE (tenant_id, product_id);

CREATE TABLE public.product_definition_root (
    root_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL CHECK (tenant_id <> '00000000-0000-0000-0000-000000000000'),
    product_id UUID NOT NULL UNIQUE,
    product_code VARCHAR(100) NOT NULL,
    business_area VARCHAR(20) NOT NULL,
    profile_code VARCHAR(64) NOT NULL,
    profile_version INTEGER NOT NULL,
    phase VARCHAR(20) NOT NULL,
    public_state_version INTEGER NOT NULL CHECK (public_state_version >= 0),
    public_head UUID NOT NULL,
    active_review_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (tenant_id, root_id),
    UNIQUE (tenant_id, product_code),
    FOREIGN KEY (tenant_id, product_id)
        REFERENCES public.product(tenant_id, product_id)
);

CREATE TABLE public.product_definition_revision (
    revision_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    root_id UUID NOT NULL,
    canonical_definition TEXT NOT NULL,
    public_digest VARCHAR(64) NOT NULL,
    encoding_version VARCHAR(64) NOT NULL,
    source_publication_id UUID,
    source_publication_digest VARCHAR(64),
    actor_issuer VARCHAR(512) NOT NULL,
    actor_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (tenant_id, root_id, revision_id),
    FOREIGN KEY (tenant_id, root_id)
        REFERENCES public.product_definition_root(tenant_id, root_id)
        DEFERRABLE INITIALLY DEFERRED,
    CHECK ((source_publication_id IS NULL) = (source_publication_digest IS NULL))
);

ALTER TABLE public.product_definition_root ADD CONSTRAINT product_definition_public_head
    FOREIGN KEY (tenant_id, root_id, public_head)
    REFERENCES public.product_definition_revision(tenant_id, root_id, revision_id)
    DEFERRABLE INITIALLY DEFERRED;

CREATE TABLE public.product_definition_cost_revision (
    cost_revision_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    root_id UUID NOT NULL,
    canonical_cost TEXT NOT NULL,
    cost_digest VARCHAR(64) NOT NULL,
    encoding_version VARCHAR(64) NOT NULL,
    actor_issuer VARCHAR(512) NOT NULL,
    actor_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (tenant_id, root_id, cost_revision_id),
    FOREIGN KEY (tenant_id, root_id)
        REFERENCES public.product_definition_root(tenant_id, root_id)
        DEFERRABLE INITIALLY DEFERRED
);

CREATE TABLE public.product_definition_control (
    root_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    cost_head UUID NOT NULL,
    cost_state_version INTEGER NOT NULL CHECK (cost_state_version >= 0),
    FOREIGN KEY (tenant_id, root_id)
        REFERENCES public.product_definition_root(tenant_id, root_id)
        DEFERRABLE INITIALLY DEFERRED,
    FOREIGN KEY (tenant_id, root_id, cost_head)
        REFERENCES public.product_definition_cost_revision(tenant_id, root_id, cost_revision_id)
        DEFERRABLE INITIALLY DEFERRED
);

CREATE TABLE public.product_definition_review (
    review_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    root_id UUID NOT NULL,
    revision_id UUID NOT NULL,
    public_digest VARCHAR(64) NOT NULL,
    actor_issuer VARCHAR(512) NOT NULL,
    actor_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (tenant_id, root_id, review_id),
    FOREIGN KEY (tenant_id, root_id, revision_id)
        REFERENCES public.product_definition_revision(tenant_id, root_id, revision_id)
);

ALTER TABLE public.product_definition_root ADD CONSTRAINT product_definition_active_review
    FOREIGN KEY (tenant_id, root_id, active_review_id)
    REFERENCES public.product_definition_review(tenant_id, root_id, review_id)
    DEFERRABLE INITIALLY DEFERRED;

CREATE TABLE public.product_definition_review_private (
    review_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    root_id UUID NOT NULL,
    cost_revision_id UUID NOT NULL,
    selection_fingerprint VARCHAR(64) NOT NULL,
    FOREIGN KEY (tenant_id, root_id, review_id)
        REFERENCES public.product_definition_review(tenant_id, root_id, review_id),
    FOREIGN KEY (tenant_id, root_id, cost_revision_id)
        REFERENCES public.product_definition_cost_revision(tenant_id, root_id, cost_revision_id)
);

CREATE TABLE public.product_definition_action (
    action_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    root_id UUID NOT NULL,
    command VARCHAR(64) NOT NULL,
    visibility VARCHAR(16) NOT NULL,
    canonical_receipt TEXT NOT NULL,
    actor_issuer VARCHAR(512) NOT NULL,
    actor_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (tenant_id, root_id, action_id),
    FOREIGN KEY (tenant_id, root_id)
        REFERENCES public.product_definition_root(tenant_id, root_id)
);

CREATE TABLE public.product_definition_action_private (
    action_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    root_id UUID NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    selection_fingerprint VARCHAR(64) NOT NULL,
    canonical_instruction TEXT NOT NULL,
    FOREIGN KEY (tenant_id, root_id, action_id)
        REFERENCES public.product_definition_action(tenant_id, root_id, action_id)
);

CREATE TABLE public.product_definition_publication (
    publication_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    root_id UUID NOT NULL,
    review_id UUID NOT NULL,
    revision_id UUID NOT NULL,
    product_version_id UUID NOT NULL UNIQUE REFERENCES public.product_version(product_version_id),
    canonical_definition TEXT NOT NULL,
    public_digest VARCHAR(64) NOT NULL,
    encoding_version VARCHAR(64) NOT NULL,
    operational_admission VARCHAR(20) NOT NULL CHECK (operational_admission = 'NOT_ADMITTED'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (tenant_id, root_id, publication_id),
    FOREIGN KEY (tenant_id, root_id, review_id)
        REFERENCES public.product_definition_review(tenant_id, root_id, review_id),
    FOREIGN KEY (tenant_id, root_id, revision_id)
        REFERENCES public.product_definition_revision(tenant_id, root_id, revision_id)
);

CREATE TABLE public.product_definition_publication_control (
    publication_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    root_id UUID NOT NULL,
    state VARCHAR(16) NOT NULL,
    state_version INTEGER NOT NULL CHECK (state_version >= 0),
    FOREIGN KEY (tenant_id, root_id, publication_id)
        REFERENCES public.product_definition_publication(tenant_id, root_id, publication_id)
);

ALTER TABLE public.product_definition_revision ADD CONSTRAINT product_definition_source_publication
    FOREIGN KEY (tenant_id, root_id, source_publication_id)
    REFERENCES public.product_definition_publication(tenant_id, root_id, publication_id)
    DEFERRABLE INITIALLY DEFERRED;

CREATE TABLE public.product_definition_history (
    history_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    root_id UUID NOT NULL,
    action_id UUID NOT NULL,
    public_state_version INTEGER NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    FOREIGN KEY (tenant_id, root_id, action_id)
        REFERENCES public.product_definition_action(tenant_id, root_id, action_id)
);

CREATE TABLE public.product_definition_audit (
    audit_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    root_id UUID NOT NULL,
    action_id UUID NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    actor_issuer VARCHAR(512) NOT NULL,
    actor_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    FOREIGN KEY (tenant_id, root_id, action_id)
        REFERENCES public.product_definition_action(tenant_id, root_id, action_id)
);

CREATE TABLE public.product_definition_outbox (
    event_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    root_id UUID NOT NULL,
    action_id UUID NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    FOREIGN KEY (tenant_id, root_id, action_id)
        REFERENCES public.product_definition_action(tenant_id, root_id, action_id)
);

CREATE TABLE public.product_definition_cost_outbox (
    event_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    root_id UUID NOT NULL,
    action_id UUID NOT NULL,
    cost_revision_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    FOREIGN KEY (tenant_id, root_id, action_id)
        REFERENCES public.product_definition_action(tenant_id, root_id, action_id),
    FOREIGN KEY (tenant_id, root_id, cost_revision_id)
        REFERENCES public.product_definition_cost_revision(tenant_id, root_id, cost_revision_id)
);

DO $$
DECLARE
    relation_name TEXT;
BEGIN
    FOREACH relation_name IN ARRAY ARRAY[
        'product_definition_root', 'product_definition_revision',
        'product_definition_cost_revision', 'product_definition_control',
        'product_definition_review', 'product_definition_review_private',
        'product_definition_action', 'product_definition_action_private',
        'product_definition_publication', 'product_definition_publication_control',
        'product_definition_history', 'product_definition_audit',
        'product_definition_outbox', 'product_definition_cost_outbox'
    ] LOOP
        EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', relation_name);
        EXECUTE format('ALTER TABLE public.%I FORCE ROW LEVEL SECURITY', relation_name);
    END LOOP;
END
$$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_catalog.pg_roles
        WHERE rolname = 'firefly_product_definition_guard'
          AND NOT rolcanlogin AND NOT rolsuper AND NOT rolbypassrls
    ) THEN
        RAISE EXCEPTION USING ERRCODE = '23514',
            MESSAGE = 'PRODUCT_DEFINITION_GUARD_ROLE_REQUIRED';
    END IF;
END
$$;

CREATE SCHEMA firefly_product_definition_private
    AUTHORIZATION firefly_product_definition_guard;
REVOKE ALL ON SCHEMA firefly_product_definition_private FROM PUBLIC;

-- A row version (xmin) cannot distinguish INSERT from a later no-op UPDATE.
-- These inaccessible links retain only identity and the actual INSERT transaction.
CREATE TABLE firefly_product_definition_private.product_birth (
    product_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    birth_transaction XID8 NOT NULL,
    native_root_id UUID
);
ALTER TABLE firefly_product_definition_private.product_birth
    OWNER TO firefly_product_definition_guard;
REVOKE ALL ON firefly_product_definition_private.product_birth FROM PUBLIC;

CREATE TABLE firefly_product_definition_private.product_binding (
    product_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    root_id UUID NOT NULL UNIQUE
);
ALTER TABLE firefly_product_definition_private.product_binding
    OWNER TO firefly_product_definition_guard;
REVOKE ALL ON firefly_product_definition_private.product_binding FROM PUBLIC;

CREATE TABLE firefly_product_definition_private.product_birth_scope (
    product_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    root_id UUID NOT NULL,
    birth_transaction XID8 NOT NULL,
    category_path UUID[] NOT NULL
);
ALTER TABLE firefly_product_definition_private.product_birth_scope
    OWNER TO firefly_product_definition_guard;
REVOKE ALL ON firefly_product_definition_private.product_birth_scope FROM PUBLIC;

CREATE TABLE firefly_product_definition_private.category_binding (
    category_id UUID NOT NULL,
    root_id UUID NOT NULL,
    PRIMARY KEY (category_id, root_id)
);
ALTER TABLE firefly_product_definition_private.category_binding
    OWNER TO firefly_product_definition_guard;
REVOKE ALL ON firefly_product_definition_private.category_binding FROM PUBLIC;

GRANT SELECT (product_category_id, parent_category_id), UPDATE (product_category_id)
    ON public.product_category TO firefly_product_definition_guard;

CREATE FUNCTION firefly_product_definition_private.category_path(leaf UUID) RETURNS UUID[]
LANGUAGE plpgsql SET search_path = pg_catalog AS $$
DECLARE
    result UUID[];
    has_cycle BOOLEAN;
    has_root BOOLEAN;
BEGIN
    WITH RECURSIVE chain AS (
        SELECT c.product_category_id AS id, c.parent_category_id AS parent,
            ARRAY[c.product_category_id] AS visited, 0 AS depth, false AS cycle
        FROM public.product_category c WHERE c.product_category_id = leaf
        UNION ALL
        SELECT c.product_category_id, c.parent_category_id,
            a.visited || c.product_category_id, a.depth + 1,
            c.product_category_id = ANY(a.visited)
        FROM chain a JOIN public.product_category c ON c.product_category_id = a.parent
        WHERE NOT a.cycle AND a.depth < 63
    ) SELECT array_agg(id ORDER BY depth DESC), bool_or(cycle), bool_or(parent IS NULL)
        INTO result, has_cycle, has_root FROM chain;
    IF result IS NULL OR has_cycle OR NOT has_root THEN
        RAISE EXCEPTION USING ERRCODE = '23514',
            MESSAGE = 'PRODUCT_DEFINITION_CATEGORY_SOURCE_REQUIRED';
    END IF;
    RETURN result;
END
$$;
ALTER FUNCTION firefly_product_definition_private.category_path(UUID)
    OWNER TO firefly_product_definition_guard;
REVOKE ALL ON FUNCTION firefly_product_definition_private.category_path(UUID) FROM PUBLIC;

CREATE FUNCTION public.lock_product_definition_category_source() RETURNS TRIGGER
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog AS $$
DECLARE
    candidate_root UUID;
    path UUID[];
    category UUID;
BEGIN
    IF pg_catalog.current_setting('firefly.product_definition.purpose', true)
        IS DISTINCT FROM 'CREATE' THEN RETURN NEW; END IF;
    IF pg_catalog.current_setting('firefly.product_definition.tenant_id', true)
        IS DISTINCT FROM NEW.tenant_id::TEXT
        OR pg_catalog.current_setting('firefly.product_definition.product_id', true)
        IS DISTINCT FROM NEW.product_id::TEXT THEN
        RAISE EXCEPTION USING ERRCODE = '23514',
            MESSAGE = 'PRODUCT_DEFINITION_CREATE_CONTEXT_REQUIRED';
    END IF;
    BEGIN
        candidate_root := pg_catalog.current_setting('firefly.product_definition.root_id', true)::UUID;
    EXCEPTION WHEN invalid_text_representation THEN
        RAISE EXCEPTION USING ERRCODE = '23514',
            MESSAGE = 'PRODUCT_DEFINITION_CREATE_CONTEXT_REQUIRED';
    END;
    IF candidate_root IS NULL OR candidate_root = '00000000-0000-0000-0000-000000000000' THEN
        RAISE EXCEPTION USING ERRCODE = '23514',
            MESSAGE = 'PRODUCT_DEFINITION_CREATE_CONTEXT_REQUIRED';
    END IF;
    path := firefly_product_definition_private.category_path(NEW.product_category_id);
    -- Lock before the Product FK takes leaf KEY SHARE: cascade DELETE locks ancestors first.
    FOREACH category IN ARRAY path LOOP
        PERFORM c.product_category_id FROM public.product_category c
            WHERE c.product_category_id = category FOR NO KEY UPDATE;
        IF NOT FOUND THEN
            RAISE EXCEPTION USING ERRCODE = '23514',
                MESSAGE = 'PRODUCT_DEFINITION_CATEGORY_SOURCE_CHANGED';
        END IF;
    END LOOP;
    IF path IS DISTINCT FROM
        firefly_product_definition_private.category_path(NEW.product_category_id) THEN
        RAISE EXCEPTION USING ERRCODE = '23514',
            MESSAGE = 'PRODUCT_DEFINITION_CATEGORY_SOURCE_CHANGED';
    END IF;
    INSERT INTO firefly_product_definition_private.product_birth_scope(
        product_id, tenant_id, root_id, birth_transaction, category_path
    ) VALUES (NEW.product_id, NEW.tenant_id, candidate_root, pg_catalog.pg_current_xact_id(), path);
    RETURN NEW;
END
$$;
ALTER FUNCTION public.lock_product_definition_category_source()
    OWNER TO firefly_product_definition_guard;
REVOKE ALL ON FUNCTION public.lock_product_definition_category_source() FROM PUBLIC;

CREATE TRIGGER product_definition_category_source
    BEFORE INSERT ON public.product
    FOR EACH ROW EXECUTE FUNCTION public.lock_product_definition_category_source();

CREATE FUNCTION public.record_product_definition_birth() RETURNS TRIGGER
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog AS $$
DECLARE
    native_root UUID;
BEGIN
    IF TG_OP = 'INSERT' THEN
        SELECT s.root_id INTO native_root
            FROM firefly_product_definition_private.product_birth_scope s
            WHERE s.product_id = NEW.product_id AND s.tenant_id = NEW.tenant_id
              AND s.birth_transaction = pg_catalog.pg_current_xact_id()
              AND s.category_path[pg_catalog.array_length(s.category_path, 1)] = NEW.product_category_id;
        INSERT INTO firefly_product_definition_private.product_birth(
            product_id, tenant_id, birth_transaction, native_root_id
        ) VALUES (NEW.product_id, NEW.tenant_id, pg_catalog.pg_current_xact_id(), native_root);
        RETURN NEW;
    END IF;
    IF TG_OP = 'UPDATE' THEN
        IF OLD.product_id IS DISTINCT FROM NEW.product_id
            OR OLD.tenant_id IS DISTINCT FROM NEW.tenant_id THEN
            UPDATE firefly_product_definition_private.product_birth
                SET product_id = NEW.product_id, tenant_id = NEW.tenant_id, native_root_id = NULL
                WHERE product_id = OLD.product_id;
        ELSE
            UPDATE firefly_product_definition_private.product_birth
                SET native_root_id = NULL
                WHERE product_id = OLD.product_id AND native_root_id IS NOT NULL;
        END IF;
        DELETE FROM firefly_product_definition_private.product_birth_scope
            WHERE product_id = OLD.product_id;
        RETURN NEW;
    END IF;
    DELETE FROM firefly_product_definition_private.product_birth
        WHERE product_id = OLD.product_id;
    DELETE FROM firefly_product_definition_private.product_birth_scope
        WHERE product_id = OLD.product_id;
    RETURN OLD;
END
$$;
ALTER FUNCTION public.record_product_definition_birth()
    OWNER TO firefly_product_definition_guard;
REVOKE ALL ON FUNCTION public.record_product_definition_birth() FROM PUBLIC;

CREATE TRIGGER product_definition_birth
    AFTER INSERT OR UPDATE OR DELETE ON public.product
    FOR EACH ROW EXECUTE FUNCTION public.record_product_definition_birth();

CREATE FUNCTION public.enroll_new_product_definition() RETURNS TRIGGER
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog AS $$
DECLARE
    path UUID[];
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM firefly_product_definition_private.product_birth b
        WHERE b.product_id = NEW.product_id AND b.tenant_id = NEW.tenant_id
          AND b.birth_transaction = pg_catalog.pg_current_xact_id()
          AND b.native_root_id = NEW.root_id
    ) THEN
        RAISE EXCEPTION USING ERRCODE = '23514',
            MESSAGE = 'PRODUCT_DEFINITION_NEW_PRODUCT_REQUIRED';
    END IF;
    SELECT s.category_path INTO path
        FROM firefly_product_definition_private.product_birth_scope s
        WHERE s.product_id = NEW.product_id AND s.tenant_id = NEW.tenant_id
          AND s.root_id = NEW.root_id AND s.birth_transaction = pg_catalog.pg_current_xact_id();
    IF path IS NULL THEN
        RAISE EXCEPTION USING ERRCODE = '23514',
            MESSAGE = 'PRODUCT_DEFINITION_CATEGORY_SOURCE_REQUIRED';
    END IF;
    IF EXISTS (SELECT 1 FROM public.product_configuration c WHERE c.product_id = NEW.product_id)
        OR EXISTS (SELECT 1 FROM public.product_version c WHERE c.product_id = NEW.product_id)
        OR EXISTS (SELECT 1 FROM public.product_documentation c WHERE c.product_id = NEW.product_id)
        OR EXISTS (SELECT 1 FROM public.product_documentation_requirement c WHERE c.product_id = NEW.product_id)
        OR EXISTS (SELECT 1 FROM public.product_localization c WHERE c.product_id = NEW.product_id)
        OR EXISTS (SELECT 1 FROM public.product_relationship c
            WHERE c.product_id = NEW.product_id OR c.related_product_id = NEW.product_id) THEN
        RAISE EXCEPTION USING ERRCODE = '23514',
            MESSAGE = 'PRODUCT_DEFINITION_UNSEALED_CHILDREN';
    END IF;
    INSERT INTO firefly_product_definition_private.product_binding(
        product_id, tenant_id, root_id
    ) VALUES (NEW.product_id, NEW.tenant_id, NEW.root_id);
    INSERT INTO firefly_product_definition_private.category_binding(category_id, root_id)
        SELECT category, NEW.root_id FROM pg_catalog.unnest(path) AS category;
    RETURN NEW;
END
$$;
ALTER FUNCTION public.enroll_new_product_definition()
    OWNER TO firefly_product_definition_guard;
REVOKE ALL ON FUNCTION public.enroll_new_product_definition() FROM PUBLIC;

CREATE TRIGGER product_definition_new_product
    BEFORE INSERT ON public.product_definition_root
    FOR EACH ROW EXECUTE FUNCTION public.enroll_new_product_definition();

CREATE FUNCTION public.guard_managed_product_mutation() RETURNS TRIGGER
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog AS $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM firefly_product_definition_private.product_binding b
        WHERE b.product_id = OLD.product_id
    ) THEN
        RAISE EXCEPTION USING ERRCODE = '23514',
            MESSAGE = 'PRODUCT_DEFINITION_MANAGED_PRODUCT_IMMUTABLE';
    END IF;
    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END
$$;
ALTER FUNCTION public.guard_managed_product_mutation()
    OWNER TO firefly_product_definition_guard;
REVOKE ALL ON FUNCTION public.guard_managed_product_mutation() FROM PUBLIC;

CREATE TRIGGER product_definition_managed_product
    BEFORE UPDATE OR DELETE ON public.product
    FOR EACH ROW EXECUTE FUNCTION public.guard_managed_product_mutation();

CREATE FUNCTION public.guard_product_definition_revision() RETURNS TRIGGER
LANGUAGE plpgsql SET search_path = pg_catalog AS $$
BEGIN
    RAISE EXCEPTION USING ERRCODE = '23514',
        MESSAGE = 'PRODUCT_DEFINITION_REVISION_IMMUTABLE';
END
$$;
REVOKE ALL ON FUNCTION public.guard_product_definition_revision() FROM PUBLIC;

CREATE TRIGGER product_definition_revision_immutable
    BEFORE UPDATE OR DELETE ON public.product_definition_revision
    FOR EACH ROW EXECUTE FUNCTION public.guard_product_definition_revision();

CREATE TRIGGER product_definition_cost_revision_immutable
    BEFORE UPDATE OR DELETE ON public.product_definition_cost_revision
    FOR EACH ROW EXECUTE FUNCTION public.guard_product_definition_revision();

CREATE FUNCTION public.guard_product_definition_root_identity() RETURNS TRIGGER
LANGUAGE plpgsql SET search_path = pg_catalog AS $$
BEGIN
    IF ROW(OLD.root_id, OLD.tenant_id, OLD.product_id, OLD.product_code,
           OLD.business_area, OLD.profile_code, OLD.profile_version, OLD.created_at)
        IS DISTINCT FROM
       ROW(NEW.root_id, NEW.tenant_id, NEW.product_id, NEW.product_code,
           NEW.business_area, NEW.profile_code, NEW.profile_version, NEW.created_at) THEN
        RAISE EXCEPTION USING ERRCODE = '23514',
            MESSAGE = 'PRODUCT_DEFINITION_ROOT_IDENTITY_IMMUTABLE';
    END IF;
    RETURN NEW;
END
$$;
REVOKE ALL ON FUNCTION public.guard_product_definition_root_identity() FROM PUBLIC;

CREATE TRIGGER product_definition_root_identity
    BEFORE UPDATE ON public.product_definition_root
    FOR EACH ROW EXECUTE FUNCTION public.guard_product_definition_root_identity();

CREATE FUNCTION public.guard_product_definition_category() RETURNS TRIGGER
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM firefly_product_definition_private.category_binding b
        WHERE b.category_id = OLD.product_category_id) THEN
        RAISE EXCEPTION USING ERRCODE = '23514',
            MESSAGE = 'PRODUCT_DEFINITION_MANAGED_CATEGORY_IMMUTABLE';
    END IF;
    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END
$$;
ALTER FUNCTION public.guard_product_definition_category()
    OWNER TO firefly_product_definition_guard;
REVOKE ALL ON FUNCTION public.guard_product_definition_category() FROM PUBLIC;

CREATE TRIGGER product_definition_category_immutable
    BEFORE UPDATE OR DELETE ON public.product_category
    FOR EACH ROW EXECUTE FUNCTION public.guard_product_definition_category();

GRANT SELECT (product_id) ON public.product_configuration, public.product_version,
    public.product_documentation, public.product_documentation_requirement,
    public.product_localization TO firefly_product_definition_guard;
GRANT SELECT (product_id, related_product_id) ON public.product_relationship
    TO firefly_product_definition_guard;

CREATE FUNCTION public.guard_product_definition_legacy_child() RETURNS TRIGGER
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog AS $$
DECLARE
    identities UUID[] := ARRAY[]::UUID[];
BEGIN
    IF TG_OP <> 'INSERT' THEN
        identities := identities || OLD.product_id;
        IF TG_TABLE_NAME = 'product_relationship' THEN
            identities := identities || OLD.related_product_id;
        END IF;
    END IF;
    IF TG_OP <> 'DELETE' THEN
        identities := identities || NEW.product_id;
        IF TG_TABLE_NAME = 'product_relationship' THEN
            identities := identities || NEW.related_product_id;
        END IF;
    END IF;
    IF EXISTS (SELECT 1 FROM firefly_product_definition_private.product_binding b
        WHERE b.product_id = ANY(identities)) THEN
        RAISE EXCEPTION USING ERRCODE = '23514',
            MESSAGE = 'PRODUCT_DEFINITION_MANAGED_LEGACY_CHILD';
    END IF;
    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END
$$;
ALTER FUNCTION public.guard_product_definition_legacy_child()
    OWNER TO firefly_product_definition_guard;
REVOKE ALL ON FUNCTION public.guard_product_definition_legacy_child() FROM PUBLIC;

DO $$
DECLARE
    relation_name TEXT;
BEGIN
    FOREACH relation_name IN ARRAY ARRAY[
        'product_configuration', 'product_version', 'product_documentation',
        'product_documentation_requirement', 'product_localization', 'product_relationship'
    ] LOOP
        EXECUTE format('CREATE TRIGGER product_definition_legacy_child BEFORE INSERT OR UPDATE OR DELETE'
            || ' ON public.%I FOR EACH ROW EXECUTE FUNCTION public.guard_product_definition_legacy_child()',
            relation_name);
        -- Enrollment may commit after the BEFORE check but before the parent FK check.
        EXECUTE format('CREATE CONSTRAINT TRIGGER product_definition_legacy_child_final'
            || ' AFTER INSERT OR UPDATE OR DELETE ON public.%I DEFERRABLE INITIALLY DEFERRED'
            || ' FOR EACH ROW EXECUTE FUNCTION public.guard_product_definition_legacy_child()',
            relation_name);
    END LOOP;
END
$$;
