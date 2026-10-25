-- SOURCE CANDIDATE ONLY. Outside production Flyway resources; no execution approved.
-- Apply as one atomic V20 only after exact DDL/roles/runner/fixture review.
DO $$ BEGIN
 IF NOT EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname=current_user AND (rolsuper OR rolbypassrls)) THEN
  RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='PRODUCT_MIGRATION_OWNER_REQUIRED';
 END IF;
 IF EXISTS (SELECT 1 FROM pg_catalog.unnest(ARRAY['firefly_product_definition_guard','firefly_product_definition_serving','firefly_product_definition_commands']) n
   WHERE NOT EXISTS (SELECT 1 FROM pg_catalog.pg_roles r WHERE r.rolname=n AND NOT r.rolcanlogin AND NOT r.rolsuper AND NOT r.rolbypassrls)) THEN
  RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='PRODUCT_NATIVE_CAPABILITY_ROLES_REQUIRED';
 END IF;
END $$;
LOCK TABLE public.product, public.product_category, public.product_definition_action, public.product_definition_action_private, public.product_definition_audit, public.product_definition_control, public.product_definition_cost_outbox, public.product_definition_cost_revision, public.product_definition_history, public.product_definition_outbox, public.product_definition_publication, public.product_definition_publication_control, public.product_definition_review, public.product_definition_review_private, public.product_definition_revision, public.product_definition_root, public.product_version IN ACCESS EXCLUSIVE MODE;
DO $$ BEGIN
 IF EXISTS (SELECT 1 FROM public.product_definition_publication_control WHERE state='ACTIVE' GROUP BY tenant_id,root_id HAVING count(*)>1) THEN
  RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='PRODUCT_ACTIVE_PUBLICATION_REVIEW_REQUIRED';
 END IF;
END $$;
-- REVIEW-ONLY TABLE/CONSTRAINT FRAGMENT. NOT AN EXECUTABLE FULL MIGRATION.
-- V20 slot is proposed from the pinned remote migration readback.
-- Runtime policies, guard bodies, native functions and finite fixture need exact review.

ALTER TABLE public.product_version
    ADD CONSTRAINT product_definition_version_product_identity
    UNIQUE (product_version_id, product_id);
ALTER TABLE public.product_definition_root
    ADD CONSTRAINT product_definition_root_product_identity
    UNIQUE (tenant_id, root_id, product_id);
ALTER TABLE public.product_definition_publication
    ADD CONSTRAINT product_definition_publication_version_identity
    UNIQUE (tenant_id, root_id, publication_id, product_version_id);

CREATE TABLE public.product_definition_submission (
    submission_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    root_id UUID NOT NULL,
    revision_id UUID NOT NULL,
    public_digest VARCHAR(64) NOT NULL CHECK (public_digest ~ '^[0-9a-f]{64}$'),
    public_version INTEGER NOT NULL CHECK (public_version >= 0),
    actor_issuer VARCHAR(512) NOT NULL,
    actor_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (tenant_id, root_id, submission_id),
    UNIQUE (tenant_id, root_id, submission_id, public_version),
    CONSTRAINT product_definition_submission_current_tuple UNIQUE (tenant_id, root_id, revision_id, public_version),
    FOREIGN KEY (tenant_id, root_id, revision_id)
        REFERENCES public.product_definition_revision(tenant_id, root_id, revision_id)
);

CREATE TABLE public.product_definition_review_decision (
    review_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    root_id UUID NOT NULL,
    submission_id UUID NOT NULL,
    public_version INTEGER NOT NULL CHECK (public_version >= 0),
    cost_version INTEGER NOT NULL CHECK (cost_version >= 0),
    state VARCHAR(16) NOT NULL CHECK (state IN ('APPROVED', 'REJECTED')),
    reason VARCHAR(1000),
    actor_issuer VARCHAR(512) NOT NULL,
    actor_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (tenant_id, root_id, review_id),
    CHECK (state <> 'REJECTED' OR (reason IS NOT NULL AND char_length(reason) > 0)),
    FOREIGN KEY (tenant_id, root_id, review_id)
        REFERENCES public.product_definition_review(tenant_id, root_id, review_id),
    FOREIGN KEY (tenant_id, root_id, submission_id, public_version)
        REFERENCES public.product_definition_submission(
            tenant_id, root_id, submission_id, public_version)
);

ALTER TABLE public.product_definition_publication
    ADD CONSTRAINT product_definition_publication_decision
    FOREIGN KEY (tenant_id, root_id, review_id)
    REFERENCES public.product_definition_review_decision(tenant_id, root_id, review_id)
    NOT VALID;

CREATE TABLE public.product_definition_version_identity (
    product_version_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    root_id UUID NOT NULL,
    product_id UUID NOT NULL,
    publication_id UUID NOT NULL,
    version_number BIGINT NOT NULL CHECK (version_number > 0),
    recorded_at TIMESTAMPTZ NOT NULL,
    UNIQUE (tenant_id, root_id, version_number),
    UNIQUE (tenant_id, root_id, product_version_id, publication_id),
    FOREIGN KEY (product_version_id, product_id)
        REFERENCES public.product_version(product_version_id, product_id),
    FOREIGN KEY (tenant_id, root_id, product_id)
        REFERENCES public.product_definition_root(tenant_id, root_id, product_id),
    FOREIGN KEY (tenant_id, root_id, publication_id, product_version_id)
        REFERENCES public.product_definition_publication(
            tenant_id, root_id, publication_id, product_version_id)
        DEFERRABLE INITIALLY DEFERRED
);

-- Preflight must reject existing duplicate ACTIVE controls before this statement.
-- It must never choose/withdraw a historical publication automatically.
CREATE UNIQUE INDEX product_definition_one_active_publication
    ON public.product_definition_publication_control(tenant_id, root_id)
    WHERE state = 'ACTIVE';
ALTER TABLE public.product_definition_publication_control
    ADD CONSTRAINT product_definition_publication_state
    CHECK (state IN ('ACTIVE', 'WITHDRAWN')) NOT VALID;

ALTER TABLE public.product_definition_submission OWNER TO firefly_product_definition_guard;
ALTER TABLE public.product_definition_submission ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.product_definition_submission FORCE ROW LEVEL SECURITY;
REVOKE ALL ON public.product_definition_submission FROM PUBLIC;
ALTER TABLE public.product_definition_review_decision OWNER TO firefly_product_definition_guard;
ALTER TABLE public.product_definition_review_decision ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.product_definition_review_decision FORCE ROW LEVEL SECURITY;
REVOKE ALL ON public.product_definition_review_decision FROM PUBLIC;
ALTER TABLE public.product_definition_version_identity OWNER TO firefly_product_definition_guard;
ALTER TABLE public.product_definition_version_identity ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.product_definition_version_identity FORCE ROW LEVEL SECURITY;
REVOKE ALL ON public.product_definition_version_identity FROM PUBLIC;

-- Guard policies, immutable/native admission triggers, exact column grants and
-- trusted native functions are deliberately absent from this review fragment.
-- This fragment alone does not grant runtime rights or qualify any publication.

-- REVIEW-ONLY PRIVATE STATE FRAGMENT. NOT AN EXECUTABLE FULL MIGRATION.
-- No application schema, table or sequence privileges are granted.
CREATE TABLE firefly_product_definition_private.operation_claim (
    tenant_id UUID NOT NULL, root_id UUID NOT NULL, operation_id UUID NOT NULL,
    command VARCHAR(64) NOT NULL,
    original_intent TEXT NOT NULL CHECK (octet_length(original_intent) <= 131072),
    original_intent_digest VARCHAR(64) NOT NULL CHECK (original_intent_digest ~ '^[0-9a-f]{64}$'),
    prepared_plan TEXT NOT NULL CHECK (octet_length(prepared_plan) <= 524288),
    prepared_plan_digest VARCHAR(64) NOT NULL CHECK (prepared_plan_digest ~ '^[0-9a-f]{64}$'),
    claimed_at TIMESTAMPTZ NOT NULL, claim_transaction XID8 NOT NULL, action_id UUID,
    PRIMARY KEY (tenant_id, root_id, operation_id),
    FOREIGN KEY (tenant_id, root_id) REFERENCES public.product_definition_root(tenant_id, root_id)
        DEFERRABLE INITIALLY DEFERRED,
    FOREIGN KEY (tenant_id, root_id, action_id)
        REFERENCES public.product_definition_action(tenant_id, root_id, action_id)
        DEFERRABLE INITIALLY DEFERRED
);
CREATE TABLE firefly_product_definition_private.native_command (
    command_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL, root_id UUID NOT NULL, operation_id UUID NOT NULL,
    command VARCHAR(64) NOT NULL, mode VARCHAR(8) NOT NULL CHECK (mode IN ('FRESH', 'REPLAY')),
    native_transaction XID8 NOT NULL, backend_pid INTEGER NOT NULL CHECK (backend_pid > 0),
    session_login NAME NOT NULL,
    authority_profile VARCHAR(64) NOT NULL CHECK (authority_profile = 'product-definition-human-staff-v1'),
    actor_issuer VARCHAR(512) NOT NULL, actor_id VARCHAR(255) NOT NULL,
    actor_until_millis BIGINT NOT NULL, permit_until_millis BIGINT NOT NULL, deadline_millis BIGINT NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL, action_id UUID NOT NULL, retained_action_recorded_at TIMESTAMPTZ,
    expected_rows JSONB NOT NULL CHECK (jsonb_typeof(expected_rows) = 'array'),
    state VARCHAR(8) NOT NULL CHECK (state IN ('OPEN', 'FINISHED')),
    UNIQUE (native_transaction, backend_pid),
    CHECK ((mode = 'REPLAY') = (retained_action_recorded_at IS NOT NULL)),
    FOREIGN KEY (tenant_id, root_id, operation_id)
        REFERENCES firefly_product_definition_private.operation_claim(tenant_id, root_id, operation_id),
    FOREIGN KEY (tenant_id, root_id, action_id)
        REFERENCES public.product_definition_action(tenant_id, root_id, action_id)
        DEFERRABLE INITIALLY DEFERRED
);
CREATE TABLE firefly_product_definition_private.row_witness (
    witness_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    command_id UUID NOT NULL REFERENCES firefly_product_definition_private.native_command(command_id),
    tenant_id UUID NOT NULL, root_id UUID NOT NULL,
    native_transaction XID8 NOT NULL, backend_pid INTEGER NOT NULL,
    relation_oid OID NOT NULL, relation_schema NAME NOT NULL, relation_name NAME NOT NULL,
    row_identity JSONB NOT NULL CHECK (jsonb_typeof(row_identity) = 'object'),
    operation VARCHAR(6) NOT NULL CHECK (operation IN ('INSERT', 'UPDATE')),
    old_row JSONB, new_row JSONB NOT NULL,
    CHECK ((operation = 'INSERT') = (old_row IS NULL)),
    UNIQUE (command_id, relation_oid, row_identity, operation)
);
ALTER TABLE firefly_product_definition_private.operation_claim OWNER TO firefly_product_definition_guard;
ALTER TABLE firefly_product_definition_private.operation_claim ENABLE ROW LEVEL SECURITY;
ALTER TABLE firefly_product_definition_private.operation_claim FORCE ROW LEVEL SECURITY;
REVOKE ALL ON firefly_product_definition_private.operation_claim FROM PUBLIC;
ALTER TABLE firefly_product_definition_private.native_command OWNER TO firefly_product_definition_guard;
ALTER TABLE firefly_product_definition_private.native_command ENABLE ROW LEVEL SECURITY;
ALTER TABLE firefly_product_definition_private.native_command FORCE ROW LEVEL SECURITY;
REVOKE ALL ON firefly_product_definition_private.native_command FROM PUBLIC;
ALTER TABLE firefly_product_definition_private.row_witness OWNER TO firefly_product_definition_guard;
ALTER TABLE firefly_product_definition_private.row_witness ENABLE ROW LEVEL SECURITY;
ALTER TABLE firefly_product_definition_private.row_witness FORCE ROW LEVEL SECURITY;
REVOKE ALL ON firefly_product_definition_private.row_witness FROM PUBLIC;
REVOKE ALL ON SEQUENCE firefly_product_definition_private.row_witness_witness_id_seq FROM PUBLIC;
-- Full reviewed bodies add guard-only RLS, exact immutable claim/command deltas,
-- append-only witnesses, native owner stamps and deferred final-row closure.

CREATE TABLE firefly_product_definition_private.relation_contract (
 relation_name TEXT PRIMARY KEY, column_names TEXT[] NOT NULL,
 insert_columns TEXT[] NOT NULL, update_columns TEXT[] NOT NULL, select_columns TEXT[] NOT NULL,
 managed BOOLEAN NOT NULL
);
ALTER TABLE firefly_product_definition_private.relation_contract OWNER TO firefly_product_definition_guard;
REVOKE ALL ON firefly_product_definition_private.relation_contract FROM PUBLIC;
INSERT INTO firefly_product_definition_private.relation_contract VALUES ('product',ARRAY['product_id','tenant_id','product_category_id','product_type','product_name','product_code','product_description','product_status','launch_date','end_date','date_created','date_updated','marketing_features']::TEXT[],ARRAY['product_id','tenant_id','product_category_id','product_type','product_name','product_code','product_description','product_status','launch_date','end_date','date_created','date_updated','marketing_features']::TEXT[],ARRAY[]::TEXT[],ARRAY['product_id']::TEXT[],FALSE);
INSERT INTO firefly_product_definition_private.relation_contract VALUES ('product_definition_action',ARRAY['action_id','tenant_id','root_id','command','visibility','canonical_receipt','actor_issuer','actor_id','created_at']::TEXT[],ARRAY['action_id','tenant_id','root_id','command','visibility','canonical_receipt','actor_issuer','actor_id','created_at']::TEXT[],ARRAY[]::TEXT[],ARRAY['action_id','tenant_id','root_id','command','visibility','canonical_receipt','actor_issuer','actor_id','created_at']::TEXT[],TRUE);
INSERT INTO firefly_product_definition_private.relation_contract VALUES ('product_definition_action_private',ARRAY['action_id','tenant_id','root_id','request_fingerprint','selection_fingerprint','canonical_instruction']::TEXT[],ARRAY['action_id','tenant_id','root_id','request_fingerprint','selection_fingerprint','canonical_instruction']::TEXT[],ARRAY[]::TEXT[],ARRAY['action_id','tenant_id','root_id','request_fingerprint','selection_fingerprint','canonical_instruction']::TEXT[],TRUE);
INSERT INTO firefly_product_definition_private.relation_contract VALUES ('product_definition_audit',ARRAY['audit_id','tenant_id','root_id','action_id','event_type','actor_issuer','actor_id','created_at']::TEXT[],ARRAY['audit_id','tenant_id','root_id','action_id','event_type','actor_issuer','actor_id','created_at']::TEXT[],ARRAY[]::TEXT[],ARRAY['audit_id','tenant_id','root_id','action_id','event_type','actor_issuer','actor_id','created_at']::TEXT[],TRUE);
INSERT INTO firefly_product_definition_private.relation_contract VALUES ('product_definition_control',ARRAY['root_id','tenant_id','cost_head','cost_state_version']::TEXT[],ARRAY['root_id','tenant_id','cost_head','cost_state_version']::TEXT[],ARRAY['cost_head','cost_state_version']::TEXT[],ARRAY['root_id','tenant_id','cost_head','cost_state_version']::TEXT[],TRUE);
INSERT INTO firefly_product_definition_private.relation_contract VALUES ('product_definition_cost_outbox',ARRAY['event_id','tenant_id','root_id','action_id','cost_revision_id','created_at']::TEXT[],ARRAY['event_id','tenant_id','root_id','action_id','cost_revision_id','created_at']::TEXT[],ARRAY[]::TEXT[],ARRAY['event_id','tenant_id','root_id','action_id','cost_revision_id','created_at']::TEXT[],TRUE);
INSERT INTO firefly_product_definition_private.relation_contract VALUES ('product_definition_cost_revision',ARRAY['cost_revision_id','tenant_id','root_id','canonical_cost','cost_digest','encoding_version','actor_issuer','actor_id','created_at']::TEXT[],ARRAY['cost_revision_id','tenant_id','root_id','canonical_cost','cost_digest','encoding_version','actor_issuer','actor_id','created_at']::TEXT[],ARRAY[]::TEXT[],ARRAY['cost_revision_id','tenant_id','root_id','canonical_cost','cost_digest','encoding_version','actor_issuer','actor_id','created_at']::TEXT[],TRUE);
INSERT INTO firefly_product_definition_private.relation_contract VALUES ('product_definition_history',ARRAY['history_id','tenant_id','root_id','action_id','public_state_version','event_type']::TEXT[],ARRAY['history_id','tenant_id','root_id','action_id','public_state_version','event_type']::TEXT[],ARRAY[]::TEXT[],ARRAY['history_id','tenant_id','root_id','action_id','public_state_version','event_type']::TEXT[],TRUE);
INSERT INTO firefly_product_definition_private.relation_contract VALUES ('product_definition_outbox',ARRAY['event_id','tenant_id','root_id','action_id','event_type','created_at']::TEXT[],ARRAY['event_id','tenant_id','root_id','action_id','event_type','created_at']::TEXT[],ARRAY[]::TEXT[],ARRAY['event_id','tenant_id','root_id','action_id','event_type','created_at']::TEXT[],TRUE);
INSERT INTO firefly_product_definition_private.relation_contract VALUES ('product_definition_publication',ARRAY['publication_id','tenant_id','root_id','review_id','revision_id','product_version_id','canonical_definition','public_digest','encoding_version','operational_admission','created_at']::TEXT[],ARRAY['publication_id','tenant_id','root_id','review_id','revision_id','product_version_id','canonical_definition','public_digest','encoding_version','operational_admission','created_at']::TEXT[],ARRAY[]::TEXT[],ARRAY['publication_id','tenant_id','root_id','review_id','revision_id','product_version_id','canonical_definition','public_digest','encoding_version','operational_admission','created_at']::TEXT[],TRUE);
INSERT INTO firefly_product_definition_private.relation_contract VALUES ('product_definition_publication_control',ARRAY['publication_id','tenant_id','root_id','state','state_version']::TEXT[],ARRAY['publication_id','tenant_id','root_id','state','state_version']::TEXT[],ARRAY['state','state_version']::TEXT[],ARRAY['publication_id','tenant_id','root_id','state','state_version']::TEXT[],TRUE);
INSERT INTO firefly_product_definition_private.relation_contract VALUES ('product_definition_review',ARRAY['review_id','tenant_id','root_id','revision_id','public_digest','actor_issuer','actor_id','created_at']::TEXT[],ARRAY['review_id','tenant_id','root_id','revision_id','public_digest','actor_issuer','actor_id','created_at']::TEXT[],ARRAY[]::TEXT[],ARRAY['review_id','tenant_id','root_id','revision_id','public_digest','actor_issuer','actor_id','created_at']::TEXT[],TRUE);
INSERT INTO firefly_product_definition_private.relation_contract VALUES ('product_definition_review_decision',ARRAY['review_id','tenant_id','root_id','submission_id','public_version','cost_version','state','reason','actor_issuer','actor_id','created_at']::TEXT[],ARRAY['review_id','tenant_id','root_id','submission_id','public_version','cost_version','state','reason','actor_issuer','actor_id','created_at']::TEXT[],ARRAY[]::TEXT[],ARRAY['review_id','tenant_id','root_id','submission_id','public_version','cost_version','state','reason','actor_issuer','actor_id','created_at']::TEXT[],TRUE);
INSERT INTO firefly_product_definition_private.relation_contract VALUES ('product_definition_review_private',ARRAY['review_id','tenant_id','root_id','cost_revision_id','selection_fingerprint']::TEXT[],ARRAY['review_id','tenant_id','root_id','cost_revision_id','selection_fingerprint']::TEXT[],ARRAY[]::TEXT[],ARRAY['review_id','tenant_id','root_id','cost_revision_id','selection_fingerprint']::TEXT[],TRUE);
INSERT INTO firefly_product_definition_private.relation_contract VALUES ('product_definition_revision',ARRAY['revision_id','tenant_id','root_id','canonical_definition','public_digest','encoding_version','source_publication_id','source_publication_digest','actor_issuer','actor_id','created_at']::TEXT[],ARRAY['revision_id','tenant_id','root_id','canonical_definition','public_digest','encoding_version','source_publication_id','source_publication_digest','actor_issuer','actor_id','created_at']::TEXT[],ARRAY[]::TEXT[],ARRAY['revision_id','tenant_id','root_id','canonical_definition','public_digest','encoding_version','source_publication_id','source_publication_digest','actor_issuer','actor_id','created_at']::TEXT[],TRUE);
INSERT INTO firefly_product_definition_private.relation_contract VALUES ('product_definition_root',ARRAY['root_id','tenant_id','product_id','product_code','business_area','profile_code','profile_version','phase','public_state_version','public_head','active_review_id','created_at']::TEXT[],ARRAY['root_id','tenant_id','product_id','product_code','business_area','profile_code','profile_version','phase','public_state_version','public_head','active_review_id','created_at']::TEXT[],ARRAY['public_head','phase','public_state_version','active_review_id']::TEXT[],ARRAY['root_id','tenant_id','product_id','product_code','business_area','profile_code','profile_version','phase','public_state_version','public_head','active_review_id','created_at']::TEXT[],TRUE);
INSERT INTO firefly_product_definition_private.relation_contract VALUES ('product_definition_submission',ARRAY['submission_id','tenant_id','root_id','revision_id','public_digest','public_version','actor_issuer','actor_id','created_at']::TEXT[],ARRAY['submission_id','tenant_id','root_id','revision_id','public_digest','public_version','actor_issuer','actor_id','created_at']::TEXT[],ARRAY[]::TEXT[],ARRAY['submission_id','tenant_id','root_id','revision_id','public_digest','public_version','actor_issuer','actor_id','created_at']::TEXT[],TRUE);
INSERT INTO firefly_product_definition_private.relation_contract VALUES ('product_definition_version_identity',ARRAY['product_version_id','tenant_id','root_id','product_id','publication_id','version_number','recorded_at']::TEXT[],ARRAY['product_version_id','tenant_id','root_id','product_id','publication_id','version_number','recorded_at']::TEXT[],ARRAY[]::TEXT[],ARRAY['product_version_id','tenant_id','root_id','product_id','publication_id','version_number','recorded_at']::TEXT[],TRUE);
INSERT INTO firefly_product_definition_private.relation_contract VALUES ('product_version',ARRAY['product_version_id','product_id','version_number','version_description','effective_date','date_created','date_updated']::TEXT[],ARRAY['product_version_id','product_id','version_number','version_description','effective_date','date_created','date_updated']::TEXT[],ARRAY[]::TEXT[],ARRAY['product_version_id']::TEXT[],FALSE);
CREATE POLICY product_native_guard ON firefly_product_definition_private.operation_claim FOR ALL TO firefly_product_definition_guard USING (TRUE) WITH CHECK (TRUE);
CREATE POLICY product_native_guard ON firefly_product_definition_private.native_command FOR ALL TO firefly_product_definition_guard USING (TRUE) WITH CHECK (TRUE);
CREATE POLICY product_native_guard ON firefly_product_definition_private.row_witness FOR ALL TO firefly_product_definition_guard USING (TRUE) WITH CHECK (TRUE);
GRANT SELECT ON public.product TO firefly_product_definition_guard;
CREATE POLICY product_native_guard ON public.product_definition_action FOR ALL TO firefly_product_definition_guard USING (TRUE) WITH CHECK (TRUE);
GRANT SELECT ON public.product_definition_action TO firefly_product_definition_guard;
CREATE POLICY product_native_guard ON public.product_definition_action_private FOR ALL TO firefly_product_definition_guard USING (TRUE) WITH CHECK (TRUE);
GRANT SELECT ON public.product_definition_action_private TO firefly_product_definition_guard;
CREATE POLICY product_native_guard ON public.product_definition_audit FOR ALL TO firefly_product_definition_guard USING (TRUE) WITH CHECK (TRUE);
GRANT SELECT ON public.product_definition_audit TO firefly_product_definition_guard;
CREATE POLICY product_native_guard ON public.product_definition_control FOR ALL TO firefly_product_definition_guard USING (TRUE) WITH CHECK (TRUE);
GRANT SELECT ON public.product_definition_control TO firefly_product_definition_guard;
CREATE POLICY product_native_guard ON public.product_definition_cost_outbox FOR ALL TO firefly_product_definition_guard USING (TRUE) WITH CHECK (TRUE);
GRANT SELECT ON public.product_definition_cost_outbox TO firefly_product_definition_guard;
CREATE POLICY product_native_guard ON public.product_definition_cost_revision FOR ALL TO firefly_product_definition_guard USING (TRUE) WITH CHECK (TRUE);
GRANT SELECT ON public.product_definition_cost_revision TO firefly_product_definition_guard;
CREATE POLICY product_native_guard ON public.product_definition_history FOR ALL TO firefly_product_definition_guard USING (TRUE) WITH CHECK (TRUE);
GRANT SELECT ON public.product_definition_history TO firefly_product_definition_guard;
CREATE POLICY product_native_guard ON public.product_definition_outbox FOR ALL TO firefly_product_definition_guard USING (TRUE) WITH CHECK (TRUE);
GRANT SELECT ON public.product_definition_outbox TO firefly_product_definition_guard;
CREATE POLICY product_native_guard ON public.product_definition_publication FOR ALL TO firefly_product_definition_guard USING (TRUE) WITH CHECK (TRUE);
GRANT SELECT ON public.product_definition_publication TO firefly_product_definition_guard;
CREATE POLICY product_native_guard ON public.product_definition_publication_control FOR ALL TO firefly_product_definition_guard USING (TRUE) WITH CHECK (TRUE);
GRANT SELECT ON public.product_definition_publication_control TO firefly_product_definition_guard;
CREATE POLICY product_native_guard ON public.product_definition_review FOR ALL TO firefly_product_definition_guard USING (TRUE) WITH CHECK (TRUE);
GRANT SELECT ON public.product_definition_review TO firefly_product_definition_guard;
CREATE POLICY product_native_guard ON public.product_definition_review_decision FOR ALL TO firefly_product_definition_guard USING (TRUE) WITH CHECK (TRUE);
GRANT SELECT ON public.product_definition_review_decision TO firefly_product_definition_guard;
CREATE POLICY product_native_guard ON public.product_definition_review_private FOR ALL TO firefly_product_definition_guard USING (TRUE) WITH CHECK (TRUE);
GRANT SELECT ON public.product_definition_review_private TO firefly_product_definition_guard;
CREATE POLICY product_native_guard ON public.product_definition_revision FOR ALL TO firefly_product_definition_guard USING (TRUE) WITH CHECK (TRUE);
GRANT SELECT ON public.product_definition_revision TO firefly_product_definition_guard;
CREATE POLICY product_native_guard ON public.product_definition_root FOR ALL TO firefly_product_definition_guard USING (TRUE) WITH CHECK (TRUE);
GRANT SELECT ON public.product_definition_root TO firefly_product_definition_guard;
CREATE POLICY product_native_guard ON public.product_definition_submission FOR ALL TO firefly_product_definition_guard USING (TRUE) WITH CHECK (TRUE);
GRANT SELECT ON public.product_definition_submission TO firefly_product_definition_guard;
CREATE POLICY product_native_guard ON public.product_definition_version_identity FOR ALL TO firefly_product_definition_guard USING (TRUE) WITH CHECK (TRUE);
GRANT SELECT ON public.product_definition_version_identity TO firefly_product_definition_guard;
GRANT SELECT ON public.product_version TO firefly_product_definition_guard;
GRANT USAGE ON SCHEMA public TO firefly_product_definition_guard;
GRANT UPDATE (root_id) ON public.product_definition_root,public.product_definition_control TO firefly_product_definition_guard;
GRANT UPDATE (publication_id) ON public.product_definition_publication_control TO firefly_product_definition_guard;


CREATE FUNCTION firefly_product_definition_private.native_hash(namespace TEXT, value TEXT)
RETURNS TEXT LANGUAGE SQL IMMUTABLE STRICT SET search_path = pg_catalog AS $$
    SELECT pg_catalog.encode(pg_catalog.sha256(pg_catalog.convert_to(namespace || E'\n' || value, 'UTF8')), 'hex')
$$;

CREATE FUNCTION firefly_product_definition_private.fixed_json(value JSONB)
RETURNS TEXT LANGUAGE plpgsql IMMUTABLE STRICT SET search_path = pg_catalog AS $$
DECLARE result TEXT; kind TEXT; bytes BYTEA; i INTEGER;
BEGIN
    kind := pg_catalog.jsonb_typeof(value);
    IF kind = 'object' THEN
        SELECT '{' || COALESCE(pg_catalog.string_agg(pg_catalog.to_jsonb(e.key)::TEXT || ':' ||
            firefly_product_definition_private.fixed_json(e.value), ',' ORDER BY e.key COLLATE "C"), '') || '}'
        INTO result FROM pg_catalog.jsonb_each(value) e;
        RETURN result;
    ELSIF kind = 'null' THEN RETURN 'null';
    ELSIF kind = 'boolean' THEN RETURN value::TEXT;
    ELSIF kind = 'number' AND value::TEXT ~ '^-?[0-9]{1,19}$' THEN RETURN value::TEXT;
    ELSIF kind = 'string' THEN
        bytes := pg_catalog.convert_to(value #>> '{}', 'UTF8');
        FOR i IN 0..pg_catalog.octet_length(bytes)-1 LOOP
            IF pg_catalog.get_byte(bytes, i) < 32 OR pg_catalog.get_byte(bytes, i) > 126 THEN
                RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_NATIVE_INTERNAL_VALUE_REQUIRED';
            END IF;
        END LOOP;
        RETURN value::TEXT;
    END IF;
    RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_NATIVE_INTERNAL_VALUE_REQUIRED';
END
$$;

CREATE FUNCTION firefly_product_definition_private.instant_text(value TIMESTAMPTZ)
RETURNS TEXT LANGUAGE plpgsql IMMUTABLE STRICT SET search_path = pg_catalog SET TimeZone = 'UTC' AS $$
DECLARE seconds TEXT; micros TEXT;
BEGIN
    seconds := pg_catalog.to_char(value AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS');
    micros := pg_catalog.to_char(value AT TIME ZONE 'UTC', 'US');
    IF micros = '000000' THEN RETURN seconds || 'Z'; END IF;
    IF pg_catalog.right(micros, 3) = '000' THEN RETURN seconds || '.' || pg_catalog.left(micros, 3) || 'Z'; END IF;
    RETURN seconds || '.' || micros || 'Z';
END
$$;

CREATE FUNCTION firefly_product_definition_private.closed(value JSONB, expected TEXT[])
RETURNS BOOLEAN LANGUAGE SQL IMMUTABLE SET search_path = pg_catalog AS $$
    SELECT COALESCE(pg_catalog.jsonb_typeof(value) = 'object' AND
        (SELECT pg_catalog.array_agg(k ORDER BY k COLLATE "C") FROM pg_catalog.jsonb_object_keys(value) k) =
        (SELECT pg_catalog.array_agg(k ORDER BY k COLLATE "C") FROM pg_catalog.unnest(expected) k), FALSE)
$$;

CREATE FUNCTION firefly_product_definition_private.uuid_value(value JSONB)
RETURNS UUID LANGUAGE plpgsql IMMUTABLE SET search_path = pg_catalog AS $$
DECLARE text_value TEXT;
BEGIN
    IF value IS NULL OR pg_catalog.jsonb_typeof(value) <> 'string' THEN
        RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_NATIVE_IDENTITY_REQUIRED';
    END IF;
    text_value := value #>> '{}';
    IF text_value !~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
       OR text_value = '00000000-0000-0000-0000-000000000000' THEN
        RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_NATIVE_IDENTITY_REQUIRED';
    END IF;
    RETURN text_value::UUID;
END
$$;

CREATE FUNCTION firefly_product_definition_private.integer_value(value JSONB, positive BOOLEAN DEFAULT FALSE)
RETURNS BIGINT LANGUAGE plpgsql IMMUTABLE SET search_path = pg_catalog AS $$
DECLARE text_value TEXT;
BEGIN
    IF value IS NULL OR pg_catalog.jsonb_typeof(value) <> 'number' THEN
        RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_NATIVE_VERSION_REQUIRED';
    END IF;
    text_value := value::TEXT;
    IF text_value !~ '^(0|[1-9][0-9]{0,18})$' OR text_value::NUMERIC > 9223372036854775807
       OR (positive AND text_value = '0') THEN
        RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_NATIVE_VERSION_REQUIRED';
    END IF;
    RETURN text_value::BIGINT;
END
$$;

CREATE FUNCTION firefly_product_definition_private.next_version(value INTEGER)
RETURNS INTEGER LANGUAGE plpgsql IMMUTABLE SET search_path = pg_catalog AS $$
BEGIN
    IF value IS NULL OR value < 0 OR value = 2147483647 THEN
        RAISE EXCEPTION USING ERRCODE='PD409', MESSAGE='PRODUCT_SELECTION_CONFLICT';
    END IF;
    RETURN value + 1;
END
$$;

CREATE FUNCTION firefly_product_definition_private.primary_key(relation_name TEXT)
RETURNS TEXT LANGUAGE SQL IMMUTABLE SET search_path = pg_catalog AS $$
    SELECT CASE relation_name
        WHEN 'product' THEN 'product_id' WHEN 'product_version' THEN 'product_version_id'
        WHEN 'product_definition_root' THEN 'root_id' WHEN 'product_definition_revision' THEN 'revision_id'
        WHEN 'product_definition_cost_revision' THEN 'cost_revision_id' WHEN 'product_definition_control' THEN 'root_id'
        WHEN 'product_definition_submission' THEN 'submission_id' WHEN 'product_definition_review' THEN 'review_id'
        WHEN 'product_definition_review_private' THEN 'review_id' WHEN 'product_definition_review_decision' THEN 'review_id'
        WHEN 'product_definition_action' THEN 'action_id' WHEN 'product_definition_action_private' THEN 'action_id'
        WHEN 'product_definition_publication' THEN 'publication_id' WHEN 'product_definition_publication_control' THEN 'publication_id'
        WHEN 'product_definition_version_identity' THEN 'product_version_id' WHEN 'product_definition_history' THEN 'history_id'
        WHEN 'product_definition_audit' THEN 'audit_id' WHEN 'product_definition_outbox' THEN 'event_id'
        WHEN 'product_definition_cost_outbox' THEN 'event_id' END
$$;

CREATE FUNCTION firefly_product_definition_private.normalized_row(relation_name TEXT, value JSONB)
RETURNS JSONB LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = pg_catalog SET TimeZone = 'UTC' AS $$
DECLARE result JSONB;
BEGIN
    IF firefly_product_definition_private.primary_key(relation_name) IS NULL THEN
        RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_NATIVE_RELATION_REQUIRED';
    END IF;
    EXECUTE pg_catalog.format('SELECT to_jsonb(jsonb_populate_record(NULL::public.%I, $1))', relation_name)
        INTO result USING value;
    RETURN result;
END
$$;

CREATE FUNCTION firefly_product_definition_private.expected_row(relation_name TEXT, operation TEXT, old_value JSONB, new_value JSONB)
RETURNS JSONB LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = pg_catalog SET TimeZone = 'UTC' AS $$
DECLARE key_name TEXT; normalized JSONB; present BOOLEAN;
BEGIN
    key_name := firefly_product_definition_private.primary_key(relation_name);
    IF key_name IS NULL OR operation NOT IN ('INSERT','UPDATE') OR new_value IS NULL
       OR (operation = 'INSERT') IS DISTINCT FROM (old_value IS NULL) THEN
        RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_NATIVE_ROW_REQUIRED';
    END IF;
    normalized := firefly_product_definition_private.normalized_row(relation_name, new_value);
    PERFORM firefly_product_definition_private.uuid_value(normalized -> key_name);
    IF operation = 'INSERT' THEN
        EXECUTE pg_catalog.format('SELECT EXISTS (SELECT 1 FROM public.%I WHERE %I = $1)', relation_name, key_name)
            INTO present USING (normalized ->> key_name)::UUID;
        IF present THEN RAISE EXCEPTION USING ERRCODE='PD409', MESSAGE='PRODUCT_SELECTION_CONFLICT'; END IF;
    END IF;
    RETURN pg_catalog.jsonb_build_object('relation', relation_name,
        'relationOid', pg_catalog.to_regclass(pg_catalog.format('public.%I', relation_name))::OID,
        'key', pg_catalog.jsonb_build_object(key_name, normalized -> key_name),
        'operation', operation, 'old', old_value, 'new', normalized);
END
$$;

CREATE FUNCTION firefly_product_definition_private.gate_held(tenant UUID, root UUID)
RETURNS BOOLEAN LANGUAGE SQL VOLATILE SET search_path = pg_catalog AS $$
    SELECT EXISTS (SELECT 1 FROM pg_catalog.pg_locks l WHERE l.locktype='advisory'
        AND l.pid=pg_catalog.pg_backend_pid() AND l.granted AND l.mode='ExclusiveLock'
        AND l.classid=62140::OID AND l.objsubid=2
        AND l.objid=((pg_catalog.hashtext(tenant::TEXT || ':' || root::TEXT)::BIGINT & 4294967295)::OID))
$$;


CREATE FUNCTION firefly_product_definition_private.session_allowed()
RETURNS BOOLEAN LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE login NAME := session_user; role_row RECORD; relation_row RECORD; attribute_row RECORD; contract RECORD; function_row RECORD;
BEGIN
    SELECT * INTO role_row FROM pg_catalog.pg_roles WHERE rolname=login;
    IF NOT FOUND OR NOT role_row.rolcanlogin OR role_row.rolsuper OR role_row.rolbypassrls
       OR pg_catalog.current_setting('role',TRUE) IS DISTINCT FROM 'none'
       OR NOT pg_catalog.pg_has_role(login,'firefly_product_definition_commands','USAGE')
       OR pg_catalog.pg_has_role(login,'firefly_product_definition_guard','MEMBER')
       OR pg_catalog.pg_has_role(login,'firefly_product_definition_serving','MEMBER')
       OR pg_catalog.has_database_privilege(login,pg_catalog.current_database(),'CREATE,TEMPORARY')
       OR pg_catalog.has_schema_privilege(login,'public','CREATE')
       OR pg_catalog.has_schema_privilege(login,'firefly_product_definition_private','USAGE,CREATE') THEN RETURN FALSE; END IF;
    IF EXISTS (SELECT 1 FROM pg_catalog.unnest(ARRAY['firefly_product_definition_guard','firefly_product_definition_serving','firefly_product_definition_commands']) name
        WHERE NOT EXISTS (SELECT 1 FROM pg_catalog.pg_roles r WHERE r.rolname=name AND NOT r.rolcanlogin AND NOT r.rolsuper AND NOT r.rolbypassrls)) THEN RETURN FALSE; END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_catalog.pg_namespace n JOIN pg_catalog.pg_roles r ON r.oid=n.nspowner
        WHERE n.nspname='firefly_product_definition_private' AND r.rolname='firefly_product_definition_guard') THEN RETURN FALSE; END IF;
    FOR function_row IN SELECT p.oid,p.proname,p.prosecdef,p.proowner,p.proconfig,p.proacl,n.nspname FROM pg_catalog.pg_proc p
        JOIN pg_catalog.pg_namespace n ON n.oid=p.pronamespace
        WHERE n.nspname='firefly_product_definition_private' OR (n.nspname='public' AND p.proname=ANY(ARRAY[
            'begin_product_definition_command','assert_product_definition_command_live','finish_product_definition_command',
            'open_product_definition_review_read','assert_product_definition_review_read_live',
            'open_product_definition_cost_read','assert_product_definition_cost_read_live','open_product_definition_current_review_read','assert_product_definition_current_review_read_live',
            'product_definition_command_runtime_allowed','product_definition_command_scope_matches','product_definition_private_read_matches',
            'product_definition_native_before','product_definition_native_after','product_definition_native_complete',
            'guard_product_definition_legacy_child'])) LOOP
        IF function_row.proowner<>(SELECT oid FROM pg_catalog.pg_roles WHERE rolname='firefly_product_definition_guard')
           OR NOT ('search_path=pg_catalog'=ANY(COALESCE(function_row.proconfig,ARRAY[]::TEXT[])))
           OR (function_row.nspname='public' AND function_row.prosecdef IS DISTINCT FROM
               (function_row.proname<>ALL(ARRAY['open_product_definition_review_read','assert_product_definition_review_read_live',
                   'open_product_definition_cost_read','assert_product_definition_cost_read_live','open_product_definition_current_review_read','assert_product_definition_current_review_read_live','product_definition_private_read_matches'])))
           OR EXISTS (SELECT 1 FROM pg_catalog.aclexplode(COALESCE(function_row.proacl,pg_catalog.acldefault('f',function_row.proowner))) a
               WHERE a.grantee=0 AND a.privilege_type='EXECUTE')
           OR (function_row.nspname='firefly_product_definition_private' AND pg_catalog.has_function_privilege(login,function_row.oid,'EXECUTE')) THEN RETURN FALSE; END IF;
    END LOOP;
    IF (SELECT count(*) FROM pg_catalog.pg_proc p JOIN pg_catalog.pg_namespace n ON n.oid=p.pronamespace
        WHERE n.nspname='public' AND p.proname=ANY(ARRAY['begin_product_definition_command','assert_product_definition_command_live','finish_product_definition_command',
            'open_product_definition_review_read','assert_product_definition_review_read_live','open_product_definition_cost_read','assert_product_definition_cost_read_live','open_product_definition_current_review_read','assert_product_definition_current_review_read_live',
            'product_definition_command_runtime_allowed','product_definition_command_scope_matches','product_definition_private_read_matches']))<>12 THEN RETURN FALSE; END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_catalog.pg_constraint c WHERE c.conrelid='public.product_definition_submission'::REGCLASS
        AND c.conname='product_definition_submission_current_tuple' AND c.contype='u' AND c.convalidated
        AND (SELECT array_agg(a.attname::TEXT ORDER BY k.ord) FROM unnest(c.conkey) WITH ORDINALITY k(attnum,ord)
             JOIN pg_catalog.pg_attribute a ON a.attrelid=c.conrelid AND a.attnum=k.attnum)
            = ARRAY['tenant_id','root_id','revision_id','public_version']::TEXT[]) THEN RETURN FALSE; END IF;
    IF (SELECT count(*) FROM firefly_product_definition_private.relation_contract)<>19 THEN RETURN FALSE; END IF;
    FOR relation_row IN SELECT c.oid,c.relname,c.relowner,c.relrowsecurity,c.relforcerowsecurity
        FROM pg_catalog.pg_class c JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace
        WHERE n.nspname='public' AND c.relkind IN ('r','p') LOOP
        SELECT * INTO contract FROM firefly_product_definition_private.relation_contract WHERE relation_name=relation_row.relname;
        IF pg_catalog.pg_has_role(login,relation_row.relowner,'MEMBER') THEN RETURN FALSE; END IF;
        IF NOT FOUND THEN
            IF pg_catalog.has_table_privilege(login,relation_row.oid,'SELECT,INSERT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN')
               OR pg_catalog.has_any_column_privilege(login,relation_row.oid,'SELECT,INSERT,UPDATE,REFERENCES') THEN RETURN FALSE; END IF;
            CONTINUE;
        END IF;
        IF contract.managed AND (NOT relation_row.relrowsecurity OR NOT relation_row.relforcerowsecurity) THEN RETURN FALSE; END IF;
        IF pg_catalog.has_table_privilege(login,relation_row.oid,'INSERT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN') THEN RETURN FALSE; END IF;
        IF (SELECT array_agg(a.attname::TEXT ORDER BY a.attname COLLATE "C") FROM pg_catalog.pg_attribute a
            WHERE a.attrelid=relation_row.oid AND a.attnum>0 AND NOT a.attisdropped)
           IS DISTINCT FROM (SELECT array_agg(c ORDER BY c COLLATE "C") FROM pg_catalog.unnest(contract.column_names)c) THEN RETURN FALSE; END IF;
        FOR attribute_row IN SELECT a.attnum,a.attname FROM pg_catalog.pg_attribute a
            WHERE a.attrelid=relation_row.oid AND a.attnum>0 AND NOT a.attisdropped LOOP
            IF pg_catalog.has_column_privilege(login,relation_row.oid,attribute_row.attnum,'SELECT')
                  IS DISTINCT FROM (attribute_row.attname=ANY(contract.select_columns))
               OR pg_catalog.has_column_privilege(login,relation_row.oid,attribute_row.attnum,'INSERT')
                  IS DISTINCT FROM (attribute_row.attname=ANY(contract.insert_columns))
               OR pg_catalog.has_column_privilege(login,relation_row.oid,attribute_row.attnum,'UPDATE')
                  IS DISTINCT FROM (attribute_row.attname=ANY(contract.update_columns))
               OR pg_catalog.has_column_privilege(login,relation_row.oid,attribute_row.attnum,'REFERENCES') THEN RETURN FALSE; END IF;
        END LOOP;
    END LOOP;
    RETURN pg_catalog.current_setting('server_encoding')='UTF8';
END
$$;

CREATE FUNCTION public.product_definition_command_runtime_allowed()
RETURNS BOOLEAN LANGUAGE SQL VOLATILE SECURITY DEFINER SET search_path=pg_catalog AS $$
 SELECT firefly_product_definition_private.session_allowed()
$$;

CREATE FUNCTION firefly_product_definition_private.current_command(allow_finished BOOLEAN DEFAULT FALSE)
RETURNS firefly_product_definition_private.native_command
LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE result firefly_product_definition_private.native_command; identity TEXT; millis BIGINT;
BEGIN
    IF NOT firefly_product_definition_private.session_allowed() THEN
        RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_NATIVE_RUNTIME_UNAVAILABLE';
    END IF;
    identity:=pg_catalog.current_setting('firefly.product_definition.command_id',TRUE);
    IF identity IS NULL OR identity !~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' THEN
        RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_NATIVE_CONTEXT_REQUIRED';
    END IF;
    SELECT * INTO result FROM firefly_product_definition_private.native_command WHERE command_id=identity::UUID
      AND native_transaction=pg_catalog.pg_current_xact_id() AND backend_pid=pg_catalog.pg_backend_pid() AND session_login=session_user;
    IF NOT FOUND OR (result.state<>'OPEN' AND NOT (allow_finished AND result.state='FINISHED'))
       OR NOT firefly_product_definition_private.gate_held(result.tenant_id,result.root_id) THEN
        RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_NATIVE_CONTEXT_REQUIRED';
    END IF;
    millis:=floor(extract(epoch FROM pg_catalog.clock_timestamp())*1000)::BIGINT;
    IF millis>=result.deadline_millis OR result.deadline_millis>millis+30000
       OR result.deadline_millis>result.actor_until_millis OR result.deadline_millis>result.permit_until_millis THEN
        RAISE EXCEPTION USING ERRCODE='PD403', MESSAGE='CURRENT_PRODUCT_PERMISSION_REQUIRED';
    END IF;
    RETURN result;
END
$$;


CREATE FUNCTION firefly_product_definition_private.validate_plan(
    tenant UUID, root UUID, operation_id UUID, command_name TEXT,
    original_text TEXT, intent_digest TEXT, plan_text TEXT)
RETURNS JSONB LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE plan JSONB; original JSONB; body JSONB; resources JSONB; expected TEXT[]; names TEXT[];
    entry RECORD; ids UUID[] := ARRAY[]::UUID[]; id UUID; key_name TEXT; snapshot JSONB; canonical TEXT;
BEGIN
    IF tenant IS NULL OR root IS NULL OR operation_id IS NULL
       OR tenant='00000000-0000-0000-0000-000000000000' OR root='00000000-0000-0000-0000-000000000000'
       OR operation_id='00000000-0000-0000-0000-000000000000'
       OR command_name IS NULL OR command_name<>ALL(ARRAY['CREATE_DRAFT','EDIT_PUBLIC','EDIT_COST','SUBMIT_PUBLIC','APPROVE_SELECTION','REJECT_SELECTION','PUBLISH','WITHDRAW_PUBLICATION'])
       OR original_text IS NULL OR pg_catalog.octet_length(original_text)>131072
       OR plan_text IS NULL OR pg_catalog.octet_length(plan_text)>524288
       OR intent_digest IS DISTINCT FROM firefly_product_definition_private.native_hash('product-definition-action-v1',original_text) THEN
        RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_NATIVE_PLAN_REQUIRED';
    END IF;
    plan:=plan_text::JSONB; original:=original_text::JSONB;
    IF NOT firefly_product_definition_private.closed(plan,ARRAY['encoding','validationBoundary','scope','command','originalIntentDigest','resources','preparedBody','publicSnapshot','privateSnapshot'])
       OR NOT firefly_product_definition_private.closed(original,ARRAY['encoding','command','scope','request'])
       OR plan->>'encoding' IS DISTINCT FROM 'product-definition-native-plan-v1'
       OR plan->>'validationBoundary' IS DISTINCT FROM 'TRUSTED_SERVICE'
       OR original->>'encoding' IS DISTINCT FROM 'product-definition-lifecycle-instruction-v1'
       OR plan->>'command' IS DISTINCT FROM command_name OR original->>'command' IS DISTINCT FROM command_name
       OR plan->>'originalIntentDigest' IS DISTINCT FROM intent_digest
       OR plan->'scope' IS DISTINCT FROM pg_catalog.jsonb_build_object('tenantId',tenant,'rootId',root,'operationId',operation_id)
       OR original->'scope' IS DISTINCT FROM plan->'scope' THEN
        RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_NATIVE_PLAN_REQUIRED';
    END IF;
    resources:=plan->'resources'; body:=plan->'preparedBody';
    names:=ARRAY['actionId','revisionId','costRevisionId','submissionId','reviewId','publicationId','productVersionId','historyId','auditId','publicEventId','costEventId'];
    IF NOT firefly_product_definition_private.closed(resources,names) THEN
        RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_NATIVE_RESOURCES_REQUIRED';
    END IF;
    expected:=ARRAY['actionId','auditId'];
    IF command_name<>ALL(ARRAY['EDIT_COST','APPROVE_SELECTION','REJECT_SELECTION']) THEN expected:=expected||ARRAY['historyId','publicEventId']; END IF;
    CASE command_name
      WHEN 'CREATE_DRAFT' THEN expected:=expected||ARRAY['revisionId','costRevisionId','costEventId']; names:=ARRAY['operationId','productId','categoryId','definition','cost'];
      WHEN 'EDIT_PUBLIC' THEN expected:=expected||ARRAY['revisionId']; names:=ARRAY['operationId','expectedPublicVersion','expectedPublicHead','definition','sourcePublicationId','sourcePublicationDigest'];
      WHEN 'EDIT_COST' THEN expected:=expected||ARRAY['costRevisionId','costEventId']; names:=ARRAY['operationId','expectedCostVersion','expectedCostHead','publicRevisionId','publicDigest','publicDefinition','cost'];
      WHEN 'SUBMIT_PUBLIC' THEN expected:=expected||ARRAY['submissionId']; names:=ARRAY['operationId','expectedPublicVersion','expectedPublicHead'];
      WHEN 'APPROVE_SELECTION','REJECT_SELECTION' THEN expected:=expected||ARRAY['reviewId','costEventId']; names:=ARRAY['operationId','submissionId','revisionId','publicDigest','publicVersion','costRevisionId','costDigest','costVersion','selectionFingerprint','reason'];
      WHEN 'PUBLISH' THEN expected:=expected||ARRAY['publicationId','productVersionId']; names:=ARRAY['operationId','reviewId','expectedPublicVersion','expectedCostVersion','versionNumber'];
      WHEN 'WITHDRAW_PUBLICATION' THEN names:=ARRAY['operationId','publicationId','expectedStateVersion'];
    END CASE;
    FOR entry IN SELECT * FROM pg_catalog.jsonb_each(resources) LOOP
        IF entry.key=ANY(expected) THEN
            id:=firefly_product_definition_private.uuid_value(entry.value);
            IF id=ANY(ids) THEN RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_NATIVE_RESOURCES_REQUIRED'; END IF;
            ids:=ids||id;
        ELSIF entry.value IS DISTINCT FROM 'null'::JSONB THEN
            RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_NATIVE_RESOURCES_REQUIRED';
        END IF;
    END LOOP;
    IF NOT firefly_product_definition_private.closed(body,names)
       OR pg_catalog.jsonb_typeof(original->'request') IS DISTINCT FROM 'object'
       OR EXISTS (SELECT 1 FROM pg_catalog.jsonb_object_keys(original->'request') k WHERE NOT k=ANY(names))
       OR body->'operationId' IS DISTINCT FROM pg_catalog.to_jsonb(operation_id) THEN
        RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_NATIVE_BODY_REQUIRED';
    END IF;
    FOREACH key_name IN ARRAY names LOOP
        IF key_name<>ALL(ARRAY['definition','publicDefinition','cost'])
           AND COALESCE(body->key_name,'null'::JSONB) IS DISTINCT FROM COALESCE(original->'request'->key_name,'null'::JSONB) THEN
            RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_NATIVE_BODY_REQUIRED';
        END IF;
    END LOOP;
    FOREACH key_name IN ARRAY ARRAY['publicSnapshot','privateSnapshot'] LOOP
        snapshot:=plan->key_name;
        IF (key_name='publicSnapshot' AND command_name=ANY(ARRAY['CREATE_DRAFT','EDIT_PUBLIC','EDIT_COST']))
           OR (key_name='privateSnapshot' AND command_name=ANY(ARRAY['CREATE_DRAFT','EDIT_COST'])) THEN
            IF NOT firefly_product_definition_private.closed(snapshot,ARRAY['encoding','canonical','digest'])
               OR snapshot->>'encoding' IS DISTINCT FROM 'product-definition-canonical-v1'
               OR pg_catalog.jsonb_typeof(snapshot->'canonical') IS DISTINCT FROM 'string'
               OR pg_catalog.octet_length(snapshot->>'canonical')>131072 THEN
                RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_NATIVE_SNAPSHOT_REQUIRED';
            END IF;
            canonical:=snapshot->>'canonical';
            IF snapshot->>'digest' IS DISTINCT FROM firefly_product_definition_private.native_hash(
                CASE key_name WHEN 'publicSnapshot' THEN 'product-definition-public-v1' ELSE 'product-definition-private-cost-v1' END, canonical)
               OR canonical::JSONB IS DISTINCT FROM body->(CASE WHEN key_name='privateSnapshot' THEN 'cost'
                    WHEN command_name='EDIT_COST' THEN 'publicDefinition' ELSE 'definition' END) THEN
                RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_NATIVE_SNAPSHOT_REQUIRED';
            END IF;
            IF key_name='publicSnapshot' AND (canonical::JSONB#>>'{common,businessArea}' IS DISTINCT FROM 'BANKING'
                OR canonical::JSONB#>>'{common,profileCode}' IS DISTINCT FROM 'ACCOUNT_TARIFF_V1'
                OR canonical::JSONB#>>'{common,profileVersion}' IS DISTINCT FROM '1') THEN
                RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_NATIVE_PROFILE_REQUIRED';
            END IF;
        ELSIF snapshot IS DISTINCT FROM 'null'::JSONB THEN
            RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_NATIVE_SNAPSHOT_REQUIRED';
        END IF;
    END LOOP;
    RETURN plan;
END
$$;


CREATE FUNCTION firefly_product_definition_private.plan_rows(
    p_plan JSONB, p_issuer TEXT, p_actor TEXT, p_stamp TIMESTAMPTZ, p_original TEXT, p_digest TEXT)
RETURNS JSONB LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog SET TimeZone='UTC' AS $$
DECLARE tenant UUID:=(p_plan#>>'{scope,tenantId}')::UUID; root UUID:=(p_plan#>>'{scope,rootId}')::UUID;
    command_name TEXT:=p_plan->>'command'; body JSONB:=p_plan->'preparedBody'; ids JSONB:=p_plan->'resources';
    pub JSONB:=p_plan->'publicSnapshot'; private_snapshot JSONB:=p_plan->'privateSnapshot';
    action UUID:=(ids->>'actionId')::UUID; rows JSONB:='[]'::JSONB; old_root JSONB; new_root JSONB;
    header public.product_definition_root; ctrl public.product_definition_control;
    revision public.product_definition_revision; cost public.product_definition_cost_revision;
    submission public.product_definition_submission; review_row public.product_definition_review;
    review_private public.product_definition_review_private; decision public.product_definition_review_decision;
    publication public.product_definition_publication; publication_control public.product_definition_publication_control;
    identity_row public.product_definition_version_identity;
    selected JSONB; fingerprint TEXT; receipt JSONB; receipt_text TEXT; visibility TEXT;
    cost_reference UUID; public_revision UUID; public_version INTEGER; phase TEXT;
    publication_id_value UUID; version_id_value UUID; version_number_value BIGINT;
    publication_state TEXT; publication_state_version INTEGER; review_id_value UUID;
    decision_state TEXT; cost_version INTEGER; count_value BIGINT; source_publication public.product_definition_publication;
BEGIN
    IF NOT firefly_product_definition_private.gate_held(tenant,root) THEN
        RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_NATIVE_LOCK_REQUIRED';
    END IF;
    IF command_name='CREATE_DRAFT' THEN
        public_revision:=(ids->>'revisionId')::UUID; public_version:=0; phase:='DRAFT';
        cost_reference:=(ids->>'costRevisionId')::UUID; cost_version:=0;
        IF EXISTS (SELECT 1 FROM public.product_definition_root r WHERE r.tenant_id=tenant AND r.product_code=(pub->>'canonical')::JSONB#>>'{common,code}') THEN
            RAISE EXCEPTION USING ERRCODE='PD409', MESSAGE='PRODUCT_SELECTION_CONFLICT';
        END IF;
        rows:=rows||jsonb_build_array(firefly_product_definition_private.expected_row('product','INSERT',NULL,jsonb_build_object(
            'product_id',body->'productId','tenant_id',tenant,'product_category_id',body->'categoryId',
            'product_type','FINANCIAL','product_name',(pub->>'canonical')::JSONB#>>'{common,name}',
            'product_code',(pub->>'canonical')::JSONB#>>'{common,code}',
            'product_description',(pub->>'canonical')::JSONB#>>'{common,description}','product_status','DRAFT',
            'launch_date',((pub->>'canonical')::JSONB#>>'{common,effectiveFrom}')::TIMESTAMPTZ AT TIME ZONE 'UTC',
            'end_date',((pub->>'canonical')::JSONB#>>'{common,effectiveTo}')::TIMESTAMPTZ AT TIME ZONE 'UTC',
            'date_created',p_stamp AT TIME ZONE 'UTC','date_updated',p_stamp AT TIME ZONE 'UTC','marketing_features','[]'::JSONB)));
        rows:=rows||jsonb_build_array(firefly_product_definition_private.expected_row('product_definition_root','INSERT',NULL,jsonb_build_object(
            'root_id',root,'tenant_id',tenant,'product_id',body->'productId',
            'product_code',(pub->>'canonical')::JSONB#>>'{common,code}','business_area','BANKING','profile_code','ACCOUNT_TARIFF_V1',
            'profile_version',1,'phase','DRAFT','public_state_version',0,'public_head',public_revision,'active_review_id',NULL,'created_at',p_stamp)));
        rows:=rows||jsonb_build_array(firefly_product_definition_private.expected_row('product_definition_control','INSERT',NULL,
            jsonb_build_object('root_id',root,'tenant_id',tenant,'cost_head',cost_reference,'cost_state_version',0)));
        selected:=jsonb_build_object('tenantId',tenant,'rootId',root,'revisionId',public_revision,'publicDigest',pub->>'digest','publicVersion',0,
            'costRevisionId',cost_reference,'costDigest',private_snapshot->>'digest','costVersion',0);
    ELSE
        SELECT * INTO header FROM public.product_definition_root r WHERE r.tenant_id=tenant AND r.root_id=root;
        IF NOT FOUND THEN RAISE EXCEPTION USING ERRCODE='PD404', MESSAGE='PRODUCT_DEFINITION_NOT_FOUND'; END IF;
        IF header.business_area<>'BANKING' OR header.profile_code<>'ACCOUNT_TARIFF_V1' OR header.profile_version<>1
           OR header.phase<>ALL(ARRAY['DRAFT','IN_REVIEW','PUBLISHED']) OR header.public_state_version<0
           OR NOT EXISTS (SELECT 1 FROM firefly_product_definition_private.product_binding b
                WHERE b.product_id=header.product_id AND b.tenant_id=tenant AND b.root_id=root) THEN
            RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_NATIVE_ROOT_UNAVAILABLE';
        END IF;
        old_root:=to_jsonb(header); public_revision:=header.public_head; public_version:=header.public_state_version; phase:=header.phase;
        IF command_name=ANY(ARRAY['SUBMIT_PUBLIC','EDIT_COST','APPROVE_SELECTION','REJECT_SELECTION','PUBLISH']) THEN
            SELECT * INTO revision FROM public.product_definition_revision v WHERE v.tenant_id=tenant AND v.root_id=root AND v.revision_id=header.public_head;
            IF NOT FOUND OR revision.encoding_version<>'product-definition-canonical-v1'
               OR revision.public_digest IS DISTINCT FROM firefly_product_definition_private.native_hash('product-definition-public-v1',revision.canonical_definition)
               OR revision.canonical_definition::JSONB#>>'{common,code}' IS DISTINCT FROM header.product_code THEN
                RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_NATIVE_REVISION_UNAVAILABLE';
            END IF;
        END IF;
        IF command_name=ANY(ARRAY['EDIT_COST','APPROVE_SELECTION','REJECT_SELECTION','PUBLISH']) THEN
            SELECT * INTO ctrl FROM public.product_definition_control c WHERE c.tenant_id=tenant AND c.root_id=root;
            IF NOT FOUND OR ctrl.cost_state_version<0 THEN
                RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_NATIVE_COST_UNAVAILABLE';
            END IF;
            cost_reference:=ctrl.cost_head; cost_version:=ctrl.cost_state_version;
        END IF;
        IF command_name=ANY(ARRAY['APPROVE_SELECTION','REJECT_SELECTION','PUBLISH']) THEN
            SELECT * INTO cost FROM public.product_definition_cost_revision c WHERE c.tenant_id=tenant AND c.root_id=root AND c.cost_revision_id=ctrl.cost_head;
            IF NOT FOUND OR cost.encoding_version<>'product-definition-canonical-v1'
               OR cost.cost_digest IS DISTINCT FROM firefly_product_definition_private.native_hash('product-definition-private-cost-v1',cost.canonical_cost) THEN
                RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_NATIVE_COST_UNAVAILABLE';
            END IF;
        END IF;
        CASE command_name
          WHEN 'EDIT_PUBLIC' THEN
            IF header.public_state_version IS DISTINCT FROM firefly_product_definition_private.integer_value(body->'expectedPublicVersion')
               OR header.public_head IS DISTINCT FROM firefly_product_definition_private.uuid_value(body->'expectedPublicHead')
               OR (pub->>'canonical')::JSONB#>>'{common,code}' IS DISTINCT FROM header.product_code THEN
                RAISE EXCEPTION USING ERRCODE='PD409', MESSAGE='PRODUCT_SELECTION_CONFLICT';
            END IF;
            IF body->'sourcePublicationId'<>'null'::JSONB THEN
                SELECT * INTO source_publication FROM public.product_definition_publication p
                    WHERE p.tenant_id=tenant AND p.root_id=root AND p.publication_id=(body->>'sourcePublicationId')::UUID;
                IF NOT FOUND OR source_publication.public_digest IS DISTINCT FROM body->>'sourcePublicationDigest' THEN
                    RAISE EXCEPTION USING ERRCODE='PD409', MESSAGE='PRODUCT_SELECTION_CONFLICT';
                END IF;
            ELSIF body->'sourcePublicationDigest'<>'null'::JSONB THEN
                RAISE EXCEPTION USING ERRCODE='PD409', MESSAGE='PRODUCT_SELECTION_CONFLICT';
            END IF;
            public_revision:=(ids->>'revisionId')::UUID; public_version:=firefly_product_definition_private.next_version(header.public_state_version); phase:='DRAFT';
            new_root:=old_root||jsonb_build_object('public_head',public_revision,'public_state_version',public_version,'phase',phase,'active_review_id',NULL);
            selected:=jsonb_build_object('tenantId',tenant,'rootId',root,'revisionId',public_revision,'publicDigest',pub->>'digest','publicVersion',public_version);
          WHEN 'EDIT_COST' THEN
            IF header.public_head IS DISTINCT FROM firefly_product_definition_private.uuid_value(body->'publicRevisionId')
               OR revision.public_digest IS DISTINCT FROM body->>'publicDigest'
               OR revision.canonical_definition IS DISTINCT FROM pub->>'canonical'
               OR ctrl.cost_head IS DISTINCT FROM firefly_product_definition_private.uuid_value(body->'expectedCostHead')
               OR ctrl.cost_state_version IS DISTINCT FROM firefly_product_definition_private.integer_value(body->'expectedCostVersion') THEN
                RAISE EXCEPTION USING ERRCODE='PD409', MESSAGE='PRODUCT_SELECTION_CONFLICT';
            END IF;
            cost_reference:=(ids->>'costRevisionId')::UUID; cost_version:=firefly_product_definition_private.next_version(ctrl.cost_state_version);
            rows:=rows||jsonb_build_array(firefly_product_definition_private.expected_row('product_definition_control','UPDATE',to_jsonb(ctrl),
                to_jsonb(ctrl)||jsonb_build_object('cost_head',cost_reference,'cost_state_version',cost_version)));
            new_root:=old_root||jsonb_build_object('active_review_id',NULL);
            selected:=jsonb_build_object('tenantId',tenant,'rootId',root,'revisionId',revision.revision_id,'publicDigest',revision.public_digest,'publicVersion',public_version,
                'costRevisionId',cost_reference,'costDigest',private_snapshot->>'digest','costVersion',cost_version);
            decision_state:='EDITED';
          WHEN 'SUBMIT_PUBLIC' THEN
            IF header.phase<>'DRAFT' OR header.public_head IS DISTINCT FROM firefly_product_definition_private.uuid_value(body->'expectedPublicHead')
               OR header.public_state_version IS DISTINCT FROM firefly_product_definition_private.integer_value(body->'expectedPublicVersion') THEN
                RAISE EXCEPTION USING ERRCODE='PD409', MESSAGE='PRODUCT_SELECTION_CONFLICT';
            END IF;
            public_version:=firefly_product_definition_private.next_version(header.public_state_version); phase:='IN_REVIEW';
            rows:=rows||jsonb_build_array(firefly_product_definition_private.expected_row('product_definition_submission','INSERT',NULL,
                jsonb_build_object('submission_id',ids->'submissionId','tenant_id',tenant,'root_id',root,'revision_id',public_revision,'public_digest',revision.public_digest,
                    'public_version',public_version,'actor_issuer',p_issuer,'actor_id',p_actor,'created_at',p_stamp)));
            new_root:=old_root||jsonb_build_object('public_state_version',public_version,'phase',phase,'active_review_id',NULL);
            selected:=jsonb_build_object('tenantId',tenant,'rootId',root,'submissionId',ids->'submissionId','revisionId',public_revision,'publicDigest',revision.public_digest,'publicVersion',public_version);
          WHEN 'APPROVE_SELECTION','REJECT_SELECTION' THEN
            SELECT * INTO submission FROM public.product_definition_submission s WHERE s.tenant_id=tenant AND s.root_id=root AND s.submission_id=(body->>'submissionId')::UUID;
            IF NOT FOUND OR header.phase<>'IN_REVIEW' OR submission.revision_id IS DISTINCT FROM header.public_head
               OR submission.public_digest IS DISTINCT FROM revision.public_digest OR submission.public_version IS DISTINCT FROM header.public_state_version
               OR header.public_head IS DISTINCT FROM firefly_product_definition_private.uuid_value(body->'revisionId')
               OR revision.public_digest IS DISTINCT FROM body->>'publicDigest'
               OR header.public_state_version IS DISTINCT FROM firefly_product_definition_private.integer_value(body->'publicVersion')
               OR ctrl.cost_head IS DISTINCT FROM firefly_product_definition_private.uuid_value(body->'costRevisionId')
               OR cost.cost_digest IS DISTINCT FROM body->>'costDigest'
               OR ctrl.cost_state_version IS DISTINCT FROM firefly_product_definition_private.integer_value(body->'costVersion') THEN
                RAISE EXCEPTION USING ERRCODE='PD409', MESSAGE='PRODUCT_SELECTION_CONFLICT';
            END IF;
            IF EXISTS (SELECT 1 FROM (
                SELECT v.actor_issuer,v.actor_id FROM public.product_definition_revision v WHERE v.tenant_id=tenant AND v.root_id=root
                UNION SELECT c.actor_issuer,c.actor_id FROM public.product_definition_cost_revision c WHERE c.tenant_id=tenant AND c.root_id=root
                UNION SELECT s.actor_issuer,s.actor_id FROM public.product_definition_submission s WHERE s.tenant_id=tenant AND s.root_id=root
            ) a WHERE a.actor_issuer=p_issuer AND a.actor_id=p_actor) THEN
                RAISE EXCEPTION USING ERRCODE='PD409', MESSAGE='PRODUCT_INDEPENDENT_CHECKER_REQUIRED';
            END IF;
            SELECT count(*) INTO count_value FROM (SELECT * FROM (
                SELECT v.actor_issuer,v.actor_id FROM public.product_definition_revision v WHERE v.tenant_id=tenant AND v.root_id=root
                UNION SELECT c.actor_issuer,c.actor_id FROM public.product_definition_cost_revision c WHERE c.tenant_id=tenant AND c.root_id=root
                UNION SELECT s.actor_issuer,s.actor_id FROM public.product_definition_submission s WHERE s.tenant_id=tenant AND s.root_id=root
            ) a LIMIT 513) bounded;
            IF count_value=0 OR count_value>512 THEN RAISE EXCEPTION USING ERRCODE='PD409', MESSAGE='PRODUCT_INDEPENDENT_CHECKER_REQUIRED'; END IF;
            selected:=jsonb_build_object('tenantId',tenant,'rootId',root,'submissionId',submission.submission_id,'revisionId',revision.revision_id,
                'publicDigest',revision.public_digest,'publicVersion',public_version,'costRevisionId',cost.cost_revision_id,'costDigest',cost.cost_digest,'costVersion',cost_version);
            fingerprint:=firefly_product_definition_private.native_hash('product-definition-selection-v1',firefly_product_definition_private.fixed_json(selected));
            IF fingerprint IS DISTINCT FROM body->>'selectionFingerprint' THEN RAISE EXCEPTION USING ERRCODE='PD409', MESSAGE='PRODUCT_SELECTION_CONFLICT'; END IF;
            review_id_value:=(ids->>'reviewId')::UUID; decision_state:=CASE command_name WHEN 'APPROVE_SELECTION' THEN 'APPROVED' ELSE 'REJECTED' END;
            IF decision_state='REJECTED' AND (body->>'reason' IS NULL OR length(body->>'reason')=0) THEN
                RAISE EXCEPTION USING ERRCODE='PD409', MESSAGE='PRODUCT_SELECTION_CONFLICT';
            END IF;
            rows:=rows||jsonb_build_array(firefly_product_definition_private.expected_row('product_definition_review','INSERT',NULL,
                jsonb_build_object('review_id',review_id_value,'tenant_id',tenant,'root_id',root,'revision_id',revision.revision_id,'public_digest',revision.public_digest,
                    'actor_issuer',p_issuer,'actor_id',p_actor,'created_at',p_stamp)));
            rows:=rows||jsonb_build_array(firefly_product_definition_private.expected_row('product_definition_review_private','INSERT',NULL,
                jsonb_build_object('review_id',review_id_value,'tenant_id',tenant,'root_id',root,'cost_revision_id',cost.cost_revision_id,'selection_fingerprint',fingerprint)));
            rows:=rows||jsonb_build_array(firefly_product_definition_private.expected_row('product_definition_review_decision','INSERT',NULL,
                jsonb_build_object('review_id',review_id_value,'tenant_id',tenant,'root_id',root,'submission_id',submission.submission_id,'public_version',public_version,'cost_version',cost_version,
                    'state',decision_state,'reason',body->'reason','actor_issuer',p_issuer,'actor_id',p_actor,'created_at',p_stamp)));
            new_root:=old_root||jsonb_build_object('active_review_id',CASE WHEN decision_state='APPROVED' THEN review_id_value ELSE NULL END);
          WHEN 'PUBLISH' THEN
            IF header.phase<>'IN_REVIEW' OR header.active_review_id IS DISTINCT FROM firefly_product_definition_private.uuid_value(body->'reviewId')
               OR header.public_state_version IS DISTINCT FROM firefly_product_definition_private.integer_value(body->'expectedPublicVersion')
               OR ctrl.cost_state_version IS DISTINCT FROM firefly_product_definition_private.integer_value(body->'expectedCostVersion') THEN
                RAISE EXCEPTION USING ERRCODE='PD409', MESSAGE='PRODUCT_SELECTION_CONFLICT';
            END IF;
            SELECT * INTO review_row FROM public.product_definition_review r WHERE r.tenant_id=tenant AND r.root_id=root AND r.review_id=header.active_review_id;
            IF NOT FOUND THEN RAISE EXCEPTION USING ERRCODE='PD409', MESSAGE='PRODUCT_SELECTION_CONFLICT'; END IF;
            SELECT * INTO review_private FROM public.product_definition_review_private r WHERE r.tenant_id=tenant AND r.root_id=root AND r.review_id=header.active_review_id;
            IF NOT FOUND THEN RAISE EXCEPTION USING ERRCODE='PD409', MESSAGE='PRODUCT_SELECTION_CONFLICT'; END IF;
            SELECT * INTO decision FROM public.product_definition_review_decision d WHERE d.tenant_id=tenant AND d.root_id=root AND d.review_id=header.active_review_id;
            IF NOT FOUND THEN RAISE EXCEPTION USING ERRCODE='PD409', MESSAGE='PRODUCT_SELECTION_CONFLICT'; END IF;
            SELECT * INTO submission FROM public.product_definition_submission s WHERE s.tenant_id=tenant AND s.root_id=root AND s.submission_id=decision.submission_id;
            IF NOT FOUND OR decision.state<>'APPROVED' OR decision.public_version IS DISTINCT FROM header.public_state_version
               OR decision.cost_version IS DISTINCT FROM ctrl.cost_state_version OR decision.actor_issuer IS DISTINCT FROM review_row.actor_issuer
               OR decision.actor_id IS DISTINCT FROM review_row.actor_id OR decision.created_at IS DISTINCT FROM review_row.created_at
               OR review_row.revision_id IS DISTINCT FROM header.public_head OR review_row.public_digest IS DISTINCT FROM revision.public_digest
               OR review_private.cost_revision_id IS DISTINCT FROM ctrl.cost_head OR submission.revision_id IS DISTINCT FROM header.public_head
               OR submission.public_digest IS DISTINCT FROM revision.public_digest OR submission.public_version IS DISTINCT FROM header.public_state_version THEN
                RAISE EXCEPTION USING ERRCODE='PD409', MESSAGE='PRODUCT_SELECTION_CONFLICT';
            END IF;
            selected:=jsonb_build_object('tenantId',tenant,'rootId',root,'submissionId',submission.submission_id,'revisionId',revision.revision_id,
                'publicDigest',revision.public_digest,'publicVersion',public_version,'costRevisionId',cost.cost_revision_id,'costDigest',cost.cost_digest,'costVersion',cost_version);
            fingerprint:=firefly_product_definition_private.native_hash('product-definition-selection-v1',firefly_product_definition_private.fixed_json(selected));
            IF review_private.selection_fingerprint IS DISTINCT FROM fingerprint OR EXISTS (SELECT 1 FROM (
                SELECT v.actor_issuer,v.actor_id FROM public.product_definition_revision v WHERE v.tenant_id=tenant AND v.root_id=root
                UNION SELECT c.actor_issuer,c.actor_id FROM public.product_definition_cost_revision c WHERE c.tenant_id=tenant AND c.root_id=root
                UNION SELECT s.actor_issuer,s.actor_id FROM public.product_definition_submission s WHERE s.tenant_id=tenant AND s.root_id=root
            ) a WHERE a.actor_issuer=review_row.actor_issuer AND a.actor_id=review_row.actor_id) THEN
                RAISE EXCEPTION USING ERRCODE='PD409', MESSAGE='PRODUCT_INDEPENDENT_CHECKER_REQUIRED';
            END IF;
            SELECT count(*) INTO count_value FROM (SELECT * FROM (
                SELECT v.actor_issuer,v.actor_id FROM public.product_definition_revision v WHERE v.tenant_id=tenant AND v.root_id=root
                UNION SELECT c.actor_issuer,c.actor_id FROM public.product_definition_cost_revision c WHERE c.tenant_id=tenant AND c.root_id=root
                UNION SELECT s.actor_issuer,s.actor_id FROM public.product_definition_submission s WHERE s.tenant_id=tenant AND s.root_id=root
            ) a LIMIT 513) bounded;
            IF count_value=0 OR count_value>512 THEN RAISE EXCEPTION USING ERRCODE='PD409', MESSAGE='PRODUCT_INDEPENDENT_CHECKER_REQUIRED'; END IF;
            IF EXISTS (SELECT 1 FROM public.product_definition_publication_control c WHERE c.tenant_id=tenant AND c.root_id=root AND c.state='ACTIVE') THEN
                RAISE EXCEPTION USING ERRCODE='PD409', MESSAGE='PRODUCT_ACTIVE_PUBLICATION_CONFLICT';
            END IF;
            version_number_value:=firefly_product_definition_private.integer_value(body->'versionNumber',TRUE);
            IF EXISTS (SELECT 1 FROM public.product_version v WHERE v.product_id=header.product_id AND v.version_number=version_number_value)
               OR EXISTS (SELECT 1 FROM public.product_definition_version_identity i WHERE i.tenant_id=tenant AND i.root_id=root AND i.version_number=version_number_value) THEN
                RAISE EXCEPTION USING ERRCODE='PD409', MESSAGE='PRODUCT_VERSION_CONFLICT';
            END IF;
            publication_id_value:=(ids->>'publicationId')::UUID; version_id_value:=(ids->>'productVersionId')::UUID;
            publication_state:='ACTIVE'; publication_state_version:=0;
            rows:=rows||jsonb_build_array(firefly_product_definition_private.expected_row('product_version','INSERT',NULL,
                jsonb_build_object('product_version_id',version_id_value,'product_id',header.product_id,'version_number',version_number_value,
                    'version_description','Governed product definition publication',
                    'effective_date',(revision.canonical_definition::JSONB#>>'{common,effectiveFrom}')::TIMESTAMPTZ AT TIME ZONE 'UTC',
                    'date_created',p_stamp AT TIME ZONE 'UTC','date_updated',p_stamp AT TIME ZONE 'UTC')));
            rows:=rows||jsonb_build_array(firefly_product_definition_private.expected_row('product_definition_publication','INSERT',NULL,
                jsonb_build_object('publication_id',publication_id_value,'tenant_id',tenant,'root_id',root,'review_id',review_row.review_id,
                    'revision_id',revision.revision_id,'product_version_id',version_id_value,'canonical_definition',revision.canonical_definition,
                    'public_digest',revision.public_digest,'encoding_version','product-definition-canonical-v1','operational_admission','NOT_ADMITTED','created_at',p_stamp)));
            rows:=rows||jsonb_build_array(firefly_product_definition_private.expected_row('product_definition_version_identity','INSERT',NULL,
                jsonb_build_object('product_version_id',version_id_value,'tenant_id',tenant,'root_id',root,'product_id',header.product_id,
                    'publication_id',publication_id_value,'version_number',version_number_value,'recorded_at',p_stamp)));
            rows:=rows||jsonb_build_array(firefly_product_definition_private.expected_row('product_definition_publication_control','INSERT',NULL,
                jsonb_build_object('publication_id',publication_id_value,'tenant_id',tenant,'root_id',root,'state','ACTIVE','state_version',0)));
            public_version:=firefly_product_definition_private.next_version(header.public_state_version); phase:='PUBLISHED';
            new_root:=old_root||jsonb_build_object('public_state_version',public_version,'phase',phase,'active_review_id',NULL);
          WHEN 'WITHDRAW_PUBLICATION' THEN
            SELECT * INTO publication FROM public.product_definition_publication p WHERE p.tenant_id=tenant AND p.root_id=root AND p.publication_id=(body->>'publicationId')::UUID;
            IF NOT FOUND THEN RAISE EXCEPTION USING ERRCODE='PD404', MESSAGE='PRODUCT_PUBLICATION_NOT_FOUND'; END IF;
            SELECT * INTO publication_control FROM public.product_definition_publication_control c WHERE c.tenant_id=tenant AND c.root_id=root AND c.publication_id=publication.publication_id;
            IF NOT FOUND THEN RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_NATIVE_CONTROL_UNAVAILABLE'; END IF;
            IF publication_control.state<>'ACTIVE' OR publication_control.state_version IS DISTINCT FROM firefly_product_definition_private.integer_value(body->'expectedStateVersion') THEN
                RAISE EXCEPTION USING ERRCODE='PD409', MESSAGE='PRODUCT_SELECTION_CONFLICT';
            END IF;
            SELECT * INTO identity_row FROM public.product_definition_version_identity i WHERE i.tenant_id=tenant AND i.root_id=root AND i.publication_id=publication.publication_id;
            IF NOT FOUND OR identity_row.product_id IS DISTINCT FROM header.product_id OR identity_row.product_version_id IS DISTINCT FROM publication.product_version_id
               OR identity_row.recorded_at IS DISTINCT FROM publication.created_at OR identity_row.version_number<=0 THEN
                RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_NATIVE_IDENTITY_UNAVAILABLE';
            END IF;
            SELECT * INTO revision FROM public.product_definition_revision v WHERE v.tenant_id=tenant AND v.root_id=root AND v.revision_id=publication.revision_id;
            IF NOT FOUND OR publication.encoding_version<>'product-definition-canonical-v1' OR publication.operational_admission<>'NOT_ADMITTED'
               OR publication.canonical_definition IS DISTINCT FROM revision.canonical_definition OR publication.public_digest IS DISTINCT FROM revision.public_digest
               OR revision.public_digest IS DISTINCT FROM firefly_product_definition_private.native_hash('product-definition-public-v1',revision.canonical_definition)
               OR revision.canonical_definition::JSONB#>>'{common,code}' IS DISTINCT FROM header.product_code THEN
                RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_NATIVE_PUBLICATION_UNAVAILABLE';
            END IF;
            publication_id_value:=publication.publication_id; version_id_value:=publication.product_version_id; version_number_value:=identity_row.version_number;
            public_revision:=publication.revision_id; publication_state:='WITHDRAWN'; publication_state_version:=firefly_product_definition_private.next_version(publication_control.state_version);
            rows:=rows||jsonb_build_array(firefly_product_definition_private.expected_row('product_definition_publication_control','UPDATE',to_jsonb(publication_control),
                to_jsonb(publication_control)||jsonb_build_object('state','WITHDRAWN','state_version',publication_state_version)));
            selected:=jsonb_build_object('tenantId',tenant,'rootId',root,'publicationId',publication_id_value,'productVersionId',version_id_value,
                'versionNumber',version_number_value,'publicationStateVersion',publication_state_version);
        END CASE;
        IF new_root IS NOT NULL THEN rows:=rows||jsonb_build_array(firefly_product_definition_private.expected_row('product_definition_root','UPDATE',old_root,new_root)); END IF;
    END IF;
    IF command_name=ANY(ARRAY['CREATE_DRAFT','EDIT_PUBLIC']) THEN
        rows:=rows||jsonb_build_array(firefly_product_definition_private.expected_row('product_definition_revision','INSERT',NULL,
            jsonb_build_object('revision_id',public_revision,'tenant_id',tenant,'root_id',root,'canonical_definition',pub->>'canonical','public_digest',pub->>'digest',
                'encoding_version','product-definition-canonical-v1','source_publication_id',body->'sourcePublicationId','source_publication_digest',body->'sourcePublicationDigest',
                'actor_issuer',p_issuer,'actor_id',p_actor,'created_at',p_stamp)));
    END IF;
    IF command_name=ANY(ARRAY['CREATE_DRAFT','EDIT_COST']) THEN
        rows:=rows||jsonb_build_array(firefly_product_definition_private.expected_row('product_definition_cost_revision','INSERT',NULL,
            jsonb_build_object('cost_revision_id',cost_reference,'tenant_id',tenant,'root_id',root,'canonical_cost',private_snapshot->>'canonical','cost_digest',private_snapshot->>'digest',
                'encoding_version','product-definition-canonical-v1','actor_issuer',p_issuer,'actor_id',p_actor,'created_at',p_stamp)));
    END IF;
    fingerprint:=COALESCE(fingerprint,firefly_product_definition_private.native_hash('product-definition-selection-v1',firefly_product_definition_private.fixed_json(selected)));
    visibility:=CASE WHEN command_name=ANY(ARRAY['EDIT_COST','APPROVE_SELECTION','REJECT_SELECTION']) THEN 'PRIVATE' ELSE 'PUBLIC' END;
    receipt:=jsonb_build_object('scope',p_plan->'scope','command',command_name,'actionId',action,'recordedAt',firefly_product_definition_private.instant_text(p_stamp));
    IF visibility='PUBLIC' THEN
        receipt:=receipt||jsonb_build_object('phase',phase,'publicVersion',public_version,'revisionId',public_revision,
            'publicationId',publication_id_value,'productVersionId',version_id_value,'versionNumber',version_number_value,
            'publicationState',publication_state,'publicationStateVersion',publication_state_version,'operationalAdmission','NOT_ADMITTED');
    ELSE
        receipt:=receipt||jsonb_build_object('costRevisionId',cost_reference,'costVersion',cost_version,'reviewId',review_id_value,'decision',decision_state);
    END IF;
    receipt_text:=firefly_product_definition_private.fixed_json(jsonb_build_object('encoding','product-definition-lifecycle-receipt-v1','visibility',visibility,'receipt',receipt));
    rows:=rows||jsonb_build_array(firefly_product_definition_private.expected_row('product_definition_action','INSERT',NULL,
        jsonb_build_object('action_id',action,'tenant_id',tenant,'root_id',root,'command',command_name,'visibility',visibility,'canonical_receipt',receipt_text,
            'actor_issuer',p_issuer,'actor_id',p_actor,'created_at',p_stamp)));
    rows:=rows||jsonb_build_array(firefly_product_definition_private.expected_row('product_definition_action_private','INSERT',NULL,
        jsonb_build_object('action_id',action,'tenant_id',tenant,'root_id',root,'request_fingerprint',p_digest,'selection_fingerprint',fingerprint,'canonical_instruction',p_original)));
    rows:=rows||jsonb_build_array(firefly_product_definition_private.expected_row('product_definition_audit','INSERT',NULL,
        jsonb_build_object('audit_id',ids->'auditId','tenant_id',tenant,'root_id',root,'action_id',action,'event_type',command_name,'actor_issuer',p_issuer,'actor_id',p_actor,'created_at',p_stamp)));
    IF ids->'historyId'<>'null'::JSONB THEN
        rows:=rows||jsonb_build_array(firefly_product_definition_private.expected_row('product_definition_history','INSERT',NULL,
            jsonb_build_object('history_id',ids->'historyId','tenant_id',tenant,'root_id',root,'action_id',action,'public_state_version',public_version,'event_type',command_name)));
    END IF;
    IF ids->'publicEventId'<>'null'::JSONB THEN
        rows:=rows||jsonb_build_array(firefly_product_definition_private.expected_row('product_definition_outbox','INSERT',NULL,
            jsonb_build_object('event_id',ids->'publicEventId','tenant_id',tenant,'root_id',root,'action_id',action,'event_type',command_name,'created_at',p_stamp)));
    END IF;
    IF ids->'costEventId'<>'null'::JSONB THEN
        rows:=rows||jsonb_build_array(firefly_product_definition_private.expected_row('product_definition_cost_outbox','INSERT',NULL,
            jsonb_build_object('event_id',ids->'costEventId','tenant_id',tenant,'root_id',root,'action_id',action,'cost_revision_id',cost_reference,'created_at',p_stamp)));
    END IF;
    RETURN rows;
END
$$;


CREATE FUNCTION public.begin_product_definition_command(
    p_tenant UUID,p_root UUID,p_operation UUID,p_command TEXT,p_issuer TEXT,p_actor TEXT,
    p_actor_until BIGINT,p_permit_until BIGINT,p_profile TEXT,p_original TEXT,p_digest TEXT,p_plan TEXT)
RETURNS TABLE(command_id UUID,replay BOOLEAN,command_recorded_at TIMESTAMPTZ,action_id UUID,retained_action_recorded_at TIMESTAMPTZ)
LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog SET TimeZone='UTC' AS $$
DECLARE plan JSONB; claim firefly_product_definition_private.operation_claim; original_action public.product_definition_action;
    original_private public.product_definition_action_private; has_claim BOOLEAN; path UUID[]; category UUID; stamp TIMESTAMPTZ;
    millis BIGINT; deadline BIGINT; identifier UUID:=pg_catalog.gen_random_uuid(); expected JSONB; expected_action UUID;
BEGIN
    IF NOT firefly_product_definition_private.session_allowed() OR pg_catalog.current_setting('transaction_read_only')<>'off'
       OR p_profile IS DISTINCT FROM 'product-definition-human-staff-v1'
       OR p_issuer IS NULL OR length(p_issuer)=0 OR length(p_issuer)>512
       OR p_actor IS NULL OR length(p_actor)=0 OR length(p_actor)>255
       OR p_actor_until IS NULL OR p_permit_until IS NULL OR p_actor_until>253402300799999 OR p_permit_until>253402300799999 THEN
        RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_NATIVE_RUNTIME_UNAVAILABLE';
    END IF;
    plan:=firefly_product_definition_private.validate_plan(p_tenant,p_root,p_operation,p_command,p_original,p_digest,p_plan);
    IF EXISTS (SELECT 1 FROM firefly_product_definition_private.native_command c
        WHERE c.native_transaction=pg_catalog.pg_current_xact_id() AND c.backend_pid=pg_catalog.pg_backend_pid()) THEN
        RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_ONE_COMMAND_TRANSACTION_REQUIRED';
    END IF;
    PERFORM pg_catalog.pg_advisory_xact_lock(62140,pg_catalog.hashtext(p_tenant::TEXT||':'||p_root::TEXT));
    SELECT * INTO claim FROM firefly_product_definition_private.operation_claim c
        WHERE c.tenant_id=p_tenant AND c.root_id=p_root AND c.operation_id=p_operation FOR UPDATE;
    has_claim:=FOUND;
    IF has_claim THEN
        IF claim.command IS DISTINCT FROM p_command OR claim.original_intent IS DISTINCT FROM p_original
           OR claim.original_intent_digest IS DISTINCT FROM p_digest THEN
            RAISE EXCEPTION USING ERRCODE='PD409', MESSAGE='PRODUCT_OPERATION_CONFLICT';
        END IF;
        IF claim.action_id IS NULL OR claim.prepared_plan_digest IS DISTINCT FROM firefly_product_definition_private.native_hash('product-definition-native-plan-v1',claim.prepared_plan) THEN
            RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_NATIVE_OUTCOME_UNAVAILABLE';
        END IF;
        PERFORM r.root_id FROM public.product_definition_root r WHERE r.tenant_id=p_tenant AND r.root_id=p_root FOR UPDATE;
        IF NOT FOUND THEN RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_NATIVE_OUTCOME_UNAVAILABLE'; END IF;
    ELSIF p_command='CREATE_DRAFT' THEN
        PERFORM r.root_id FROM public.product_definition_root r WHERE r.tenant_id=p_tenant AND r.root_id=p_root FOR UPDATE;
        IF FOUND THEN RAISE EXCEPTION USING ERRCODE='PD409', MESSAGE='PRODUCT_SELECTION_CONFLICT'; END IF;
        path:=firefly_product_definition_private.category_path((plan#>>'{preparedBody,categoryId}')::UUID);
        FOREACH category IN ARRAY path LOOP
            PERFORM c.product_category_id FROM public.product_category c WHERE c.product_category_id=category FOR NO KEY UPDATE;
            IF NOT FOUND THEN RAISE EXCEPTION USING ERRCODE='PD409', MESSAGE='PRODUCT_CATEGORY_SOURCE_CHANGED'; END IF;
        END LOOP;
        IF path IS DISTINCT FROM firefly_product_definition_private.category_path((plan#>>'{preparedBody,categoryId}')::UUID) THEN
            RAISE EXCEPTION USING ERRCODE='PD409', MESSAGE='PRODUCT_CATEGORY_SOURCE_CHANGED';
        END IF;
    ELSE
        PERFORM r.root_id FROM public.product_definition_root r WHERE r.tenant_id=p_tenant AND r.root_id=p_root FOR UPDATE;
        IF NOT FOUND THEN RAISE EXCEPTION USING ERRCODE='PD404', MESSAGE='PRODUCT_DEFINITION_NOT_FOUND'; END IF;
        IF p_command=ANY(ARRAY['EDIT_COST','APPROVE_SELECTION','REJECT_SELECTION','PUBLISH']) THEN
            PERFORM c.root_id FROM public.product_definition_control c WHERE c.tenant_id=p_tenant AND c.root_id=p_root FOR UPDATE;
            IF NOT FOUND THEN RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_NATIVE_COST_UNAVAILABLE'; END IF;
        ELSIF p_command='WITHDRAW_PUBLICATION' THEN
            PERFORM c.publication_id FROM public.product_definition_publication_control c
                WHERE c.tenant_id=p_tenant AND c.root_id=p_root AND c.publication_id=(plan#>>'{preparedBody,publicationId}')::UUID FOR UPDATE;
        END IF;
    END IF;
    stamp:=pg_catalog.clock_timestamp(); millis:=floor(extract(epoch FROM stamp)*1000)::BIGINT;
    IF NOT firefly_product_definition_private.session_allowed() THEN
        RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_NATIVE_RUNTIME_UNAVAILABLE';
    END IF;
    IF p_actor_until<=millis OR p_permit_until<=millis THEN
        RAISE EXCEPTION USING ERRCODE='PD403', MESSAGE='CURRENT_PRODUCT_PERMISSION_REQUIRED';
    END IF;
    deadline:=least(p_actor_until,p_permit_until,millis+30000);
    IF has_claim THEN
        SELECT * INTO original_action FROM public.product_definition_action a
            WHERE a.tenant_id=p_tenant AND a.root_id=p_root AND a.action_id=claim.action_id;
        IF NOT FOUND OR original_action.command IS DISTINCT FROM p_command OR original_action.created_at IS DISTINCT FROM claim.claimed_at THEN
            RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_NATIVE_OUTCOME_UNAVAILABLE';
        END IF;
        SELECT * INTO original_private FROM public.product_definition_action_private a
            WHERE a.tenant_id=p_tenant AND a.root_id=p_root AND a.action_id=claim.action_id;
        IF NOT FOUND OR original_private.canonical_instruction IS DISTINCT FROM p_original OR original_private.request_fingerprint IS DISTINCT FROM p_digest THEN
            RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_NATIVE_OUTCOME_UNAVAILABLE';
        END IF;
        expected:='[]'::JSONB; expected_action:=claim.action_id;
    ELSE
        expected:=firefly_product_definition_private.plan_rows(plan,p_issuer,p_actor,stamp,p_original,p_digest);
        expected_action:=(plan#>>'{resources,actionId}')::UUID;
        INSERT INTO firefly_product_definition_private.operation_claim(tenant_id,root_id,operation_id,command,original_intent,original_intent_digest,
            prepared_plan,prepared_plan_digest,claimed_at,claim_transaction,action_id)
        VALUES(p_tenant,p_root,p_operation,p_command,p_original,p_digest,p_plan,
            firefly_product_definition_private.native_hash('product-definition-native-plan-v1',p_plan),stamp,pg_catalog.pg_current_xact_id(),NULL);
    END IF;
    INSERT INTO firefly_product_definition_private.native_command(command_id,tenant_id,root_id,operation_id,command,mode,
        native_transaction,backend_pid,session_login,authority_profile,actor_issuer,actor_id,actor_until_millis,permit_until_millis,deadline_millis,
        recorded_at,action_id,retained_action_recorded_at,expected_rows,state)
    VALUES(identifier,p_tenant,p_root,p_operation,p_command,CASE WHEN has_claim THEN 'REPLAY' ELSE 'FRESH' END,
        pg_catalog.pg_current_xact_id(),pg_catalog.pg_backend_pid(),session_user,p_profile,p_issuer,p_actor,p_actor_until,p_permit_until,deadline,
        stamp,expected_action,CASE WHEN has_claim THEN original_action.created_at ELSE NULL END,expected,'OPEN');
    PERFORM pg_catalog.set_config('firefly.product_definition.command_id',identifier::TEXT,TRUE);
    PERFORM pg_catalog.set_config('firefly.product_definition.purpose',CASE WHEN NOT has_claim AND p_command='CREATE_DRAFT' THEN 'CREATE' ELSE 'COMMAND' END,TRUE);
    PERFORM pg_catalog.set_config('firefly.product_definition.tenant_id',p_tenant::TEXT,TRUE);
    PERFORM pg_catalog.set_config('firefly.product_definition.root_id',p_root::TEXT,TRUE);
    PERFORM pg_catalog.set_config('firefly.product_definition.product_id',COALESCE(plan#>>'{preparedBody,productId}',''),TRUE);
    RETURN QUERY SELECT identifier,has_claim,stamp,expected_action,CASE WHEN has_claim THEN original_action.created_at ELSE NULL::TIMESTAMPTZ END;
END
$$;

CREATE FUNCTION public.assert_product_definition_command_live(p_command UUID)
RETURNS BOOLEAN LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE context firefly_product_definition_private.native_command;
BEGIN
    context:=firefly_product_definition_private.current_command(FALSE);
    IF context.command_id IS DISTINCT FROM p_command THEN RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_NATIVE_CONTEXT_REQUIRED'; END IF;
    RETURN TRUE;
END
$$;


CREATE FUNCTION public.product_definition_command_scope_matches(p_tenant UUID,p_root UUID,p_relation OID,p_key UUID,p_access TEXT)
RETURNS BOOLEAN LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE context firefly_product_definition_private.native_command; relation_name TEXT;
BEGIN
    context:=firefly_product_definition_private.current_command(FALSE);
    IF context.tenant_id IS DISTINCT FROM p_tenant OR context.root_id IS DISTINCT FROM p_root THEN RETURN FALSE; END IF;
    SELECT c.relname INTO relation_name FROM pg_catalog.pg_class c JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace
        WHERE c.oid=p_relation AND n.nspname='public';
    IF NOT FOUND THEN RETURN FALSE; END IF;
    IF context.mode='REPLAY' THEN
        RETURN p_access='SELECT' AND relation_name=ANY(ARRAY['product_definition_action','product_definition_action_private']) AND p_key=context.action_id;
    END IF;
    IF p_access=ANY(ARRAY['INSERT','UPDATE']) THEN
        RETURN EXISTS (SELECT 1 FROM pg_catalog.jsonb_array_elements(context.expected_rows) e
            WHERE e->>'relation'=relation_name AND e->>'operation'=p_access
              AND (e->'new'->>firefly_product_definition_private.primary_key(relation_name))::UUID=p_key);
    END IF;
    IF p_access<>'SELECT' THEN RETURN FALSE; END IF;
    IF EXISTS (SELECT 1 FROM pg_catalog.jsonb_array_elements(context.expected_rows)e
        WHERE e->>'relation'=relation_name AND (e->'new'->>firefly_product_definition_private.primary_key(relation_name))::UUID=p_key) THEN RETURN TRUE; END IF;
    IF relation_name=ANY(ARRAY['product_definition_root','product_definition_revision','product_definition_publication','product_definition_publication_control','product_definition_version_identity']) THEN RETURN TRUE; END IF;
    IF relation_name=ANY(ARRAY['product_definition_control','product_definition_cost_revision']) THEN
        RETURN context.command=ANY(ARRAY['CREATE_DRAFT','EDIT_COST','APPROVE_SELECTION','REJECT_SELECTION','PUBLISH']);
    END IF;
    IF relation_name=ANY(ARRAY['product_definition_submission','product_definition_review','product_definition_review_private','product_definition_review_decision']) THEN
        RETURN context.command=ANY(ARRAY['APPROVE_SELECTION','REJECT_SELECTION','PUBLISH']);
    END IF;
    RETURN FALSE;
EXCEPTION WHEN OTHERS THEN RETURN FALSE;
END
$$;

CREATE FUNCTION public.product_definition_private_read_matches(p_tenant UUID,p_root UUID,p_relation OID,p_key UUID)
RETURNS BOOLEAN LANGUAGE plpgsql VOLATILE SECURITY INVOKER SET search_path=pg_catalog AS $$
DECLARE kind TEXT; relation_name TEXT; until_text TEXT; until_millis BIGINT; now_millis BIGINT; submission_text TEXT;
BEGIN
    IF current_user<>session_user OR NOT public.product_definition_command_runtime_allowed()
       OR COALESCE(pg_catalog.current_setting('firefly.product_definition.private_read_id',TRUE),'') !~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
       OR pg_catalog.current_setting('transaction_read_only')<>'on'
       OR pg_catalog.current_setting('firefly.product_definition.private_read_tenant',TRUE) IS DISTINCT FROM p_tenant::TEXT
       OR pg_catalog.current_setting('firefly.product_definition.private_read_root',TRUE) IS DISTINCT FROM p_root::TEXT
       OR pg_catalog.current_setting('firefly.product_definition.private_read_xid',TRUE) IS DISTINCT FROM pg_catalog.pg_current_xact_id()::TEXT THEN RETURN FALSE; END IF;
    kind:=pg_catalog.current_setting('firefly.product_definition.private_read_kind',TRUE);
    IF kind IS NULL OR kind<>ALL(ARRAY['READ_SELECTION','READ_SELECTION_CURRENT','READ_COST']) THEN RETURN FALSE; END IF;
    until_text:=pg_catalog.current_setting('firefly.product_definition.private_read_until',TRUE);
    IF until_text IS NULL OR until_text !~ '^[0-9]{1,15}$' THEN RETURN FALSE; END IF;
    until_millis:=until_text::BIGINT; now_millis:=floor(extract(epoch FROM pg_catalog.clock_timestamp())*1000)::BIGINT;
    IF until_millis>253402300799999 OR until_millis<=now_millis OR until_millis>now_millis+30000 THEN RETURN FALSE; END IF;
    SELECT c.relname INTO relation_name FROM pg_catalog.pg_class c JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace WHERE c.oid=p_relation AND n.nspname='public';
    IF NOT FOUND THEN RETURN FALSE; END IF;
    IF relation_name='product_definition_root' THEN RETURN p_key=p_root; END IF;
    IF relation_name='product_definition_revision' THEN
        RETURN EXISTS (SELECT 1 FROM public.product_definition_root r WHERE r.tenant_id=p_tenant AND r.root_id=p_root AND r.public_head=p_key);
    END IF;
    IF relation_name='product_definition_submission' THEN
        RETURN kind='READ_SELECTION_CURRENT' OR (kind='READ_SELECTION' AND pg_catalog.current_setting('firefly.product_definition.private_read_submission',TRUE)=p_key::TEXT);
    END IF;
    IF relation_name<>ALL(ARRAY['product_definition_control','product_definition_cost_revision']) THEN RETURN FALSE; END IF;
    IF kind=ANY(ARRAY['READ_SELECTION','READ_SELECTION_CURRENT']) THEN
        submission_text:=pg_catalog.current_setting('firefly.product_definition.private_read_submission',TRUE);
        IF NOT EXISTS (SELECT 1 FROM public.product_definition_submission s JOIN public.product_definition_root r
            ON r.tenant_id=s.tenant_id AND r.root_id=s.root_id AND r.public_head=s.revision_id AND r.public_state_version=s.public_version
            WHERE s.tenant_id=p_tenant AND s.root_id=p_root AND (kind='READ_SELECTION_CURRENT' OR s.submission_id::TEXT=submission_text) AND r.phase='IN_REVIEW') THEN RETURN FALSE; END IF;
    END IF;
    IF relation_name='product_definition_control' THEN RETURN p_key=p_root; END IF;
    RETURN EXISTS (SELECT 1 FROM public.product_definition_control c WHERE c.tenant_id=p_tenant AND c.root_id=p_root AND c.cost_head=p_key);
EXCEPTION WHEN OTHERS THEN RETURN FALSE;
END
$$;

CREATE FUNCTION public.open_product_definition_cost_read(p_tenant UUID,p_root UUID,p_issuer TEXT,p_actor TEXT,p_actor_until BIGINT,p_permit_until BIGINT,p_profile TEXT)
RETURNS TABLE(context_id UUID,recorded_at TIMESTAMPTZ)
LANGUAGE plpgsql VOLATILE SECURITY INVOKER SET search_path=pg_catalog AS $$
DECLARE stamp TIMESTAMPTZ; millis BIGINT; deadline BIGINT; identifier UUID:=pg_catalog.gen_random_uuid();
BEGIN
    IF current_user<>session_user OR NOT public.product_definition_command_runtime_allowed()
       OR pg_catalog.current_setting('transaction_read_only')<>'on' OR p_tenant IS NULL OR p_root IS NULL
       OR p_tenant='00000000-0000-0000-0000-000000000000' OR p_root='00000000-0000-0000-0000-000000000000'
       OR p_profile IS DISTINCT FROM 'product-definition-human-staff-v1' OR p_actor IS NULL OR length(p_actor)=0 OR length(p_actor)>255
       OR p_issuer IS NULL OR length(p_issuer)=0 OR length(p_issuer)>512
       OR p_actor_until IS NULL OR p_permit_until IS NULL OR p_actor_until>253402300799999 OR p_permit_until>253402300799999 THEN
        RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_PRIVATE_READ_UNAVAILABLE';
    END IF;
    stamp:=pg_catalog.clock_timestamp(); millis:=floor(extract(epoch FROM stamp)*1000)::BIGINT;
    IF p_actor_until<=millis OR p_permit_until<=millis THEN RAISE EXCEPTION USING ERRCODE='PD403', MESSAGE='CURRENT_PRODUCT_PERMISSION_REQUIRED'; END IF;
    deadline:=least(p_actor_until,p_permit_until,millis+30000);
    PERFORM pg_catalog.set_config('firefly.product_definition.private_read_kind','READ_COST',TRUE);
    PERFORM pg_catalog.set_config('firefly.product_definition.private_read_id',identifier::TEXT,TRUE);
    PERFORM pg_catalog.set_config('firefly.product_definition.private_read_tenant',p_tenant::TEXT,TRUE);
    PERFORM pg_catalog.set_config('firefly.product_definition.private_read_root',p_root::TEXT,TRUE);
    PERFORM pg_catalog.set_config('firefly.product_definition.private_read_submission','',TRUE);
    PERFORM pg_catalog.set_config('firefly.product_definition.private_read_xid',pg_catalog.pg_current_xact_id()::TEXT,TRUE);
    PERFORM pg_catalog.set_config('firefly.product_definition.private_read_until',deadline::TEXT,TRUE);
    RETURN QUERY SELECT identifier,stamp;
END
$$;

CREATE FUNCTION public.open_product_definition_review_read(p_tenant UUID,p_root UUID,p_submission UUID,p_issuer TEXT,p_actor TEXT,p_actor_until BIGINT,p_permit_until BIGINT,p_profile TEXT)
RETURNS TABLE(context_id UUID,recorded_at TIMESTAMPTZ)
LANGUAGE plpgsql VOLATILE SECURITY INVOKER SET search_path=pg_catalog AS $$
DECLARE opened RECORD;
BEGIN
    IF p_submission IS NULL OR p_submission='00000000-0000-0000-0000-000000000000' THEN
        RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_PRIVATE_READ_UNAVAILABLE';
    END IF;
    SELECT * INTO opened FROM public.open_product_definition_cost_read(p_tenant,p_root,p_issuer,p_actor,p_actor_until,p_permit_until,p_profile);
    PERFORM pg_catalog.set_config('firefly.product_definition.private_read_kind','READ_SELECTION',TRUE);
    PERFORM pg_catalog.set_config('firefly.product_definition.private_read_submission',p_submission::TEXT,TRUE);
    RETURN QUERY SELECT opened.context_id,opened.recorded_at;
END
$$;

CREATE FUNCTION public.assert_product_definition_cost_read_live(p_context UUID)
RETURNS BOOLEAN LANGUAGE plpgsql VOLATILE SECURITY INVOKER SET search_path=pg_catalog AS $$
DECLARE tenant UUID; root UUID;
BEGIN
    IF p_context IS NULL OR p_context='00000000-0000-0000-0000-000000000000' OR p_context::TEXT IS DISTINCT FROM pg_catalog.current_setting('firefly.product_definition.private_read_id',TRUE)
       OR pg_catalog.current_setting('firefly.product_definition.private_read_kind',TRUE) IS DISTINCT FROM 'READ_COST' THEN
        RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_PRIVATE_READ_UNAVAILABLE';
    END IF;
    tenant:=pg_catalog.current_setting('firefly.product_definition.private_read_tenant',TRUE)::UUID;
    root:=pg_catalog.current_setting('firefly.product_definition.private_read_root',TRUE)::UUID;
    IF NOT public.product_definition_private_read_matches(tenant,root,'public.product_definition_root'::REGCLASS,root) THEN
        RAISE EXCEPTION USING ERRCODE='PD403', MESSAGE='CURRENT_PRODUCT_PERMISSION_REQUIRED';
    END IF;
    RETURN TRUE;
END
$$;

CREATE FUNCTION public.assert_product_definition_review_read_live(p_context UUID)
RETURNS BOOLEAN LANGUAGE plpgsql VOLATILE SECURITY INVOKER SET search_path=pg_catalog AS $$
DECLARE tenant UUID; root UUID;
BEGIN
    IF p_context IS NULL OR p_context='00000000-0000-0000-0000-000000000000' OR p_context::TEXT IS DISTINCT FROM pg_catalog.current_setting('firefly.product_definition.private_read_id',TRUE)
       OR pg_catalog.current_setting('firefly.product_definition.private_read_kind',TRUE) IS DISTINCT FROM 'READ_SELECTION' THEN
        RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_PRIVATE_READ_UNAVAILABLE';
    END IF;
    tenant:=pg_catalog.current_setting('firefly.product_definition.private_read_tenant',TRUE)::UUID;
    root:=pg_catalog.current_setting('firefly.product_definition.private_read_root',TRUE)::UUID;
    IF NOT public.product_definition_private_read_matches(tenant,root,'public.product_definition_root'::REGCLASS,root) THEN
        RAISE EXCEPTION USING ERRCODE='PD403', MESSAGE='CURRENT_PRODUCT_PERMISSION_REQUIRED';
    END IF;
    RETURN TRUE;
END
$$;

CREATE FUNCTION public.open_product_definition_current_review_read(p_tenant UUID,p_root UUID,p_issuer TEXT,p_actor TEXT,p_actor_until BIGINT,p_permit_until BIGINT,p_profile TEXT)
RETURNS TABLE(context_id UUID,recorded_at TIMESTAMPTZ)
LANGUAGE plpgsql VOLATILE SECURITY INVOKER SET search_path=pg_catalog AS $$
DECLARE opened RECORD;
BEGIN
    SELECT * INTO opened FROM public.open_product_definition_cost_read(p_tenant,p_root,p_issuer,p_actor,p_actor_until,p_permit_until,p_profile);
    PERFORM pg_catalog.set_config('firefly.product_definition.private_read_kind','READ_SELECTION_CURRENT',TRUE);
    RETURN QUERY SELECT opened.context_id,opened.recorded_at;
END
$$;
CREATE FUNCTION public.assert_product_definition_current_review_read_live(p_context UUID)
RETURNS BOOLEAN LANGUAGE plpgsql VOLATILE SECURITY INVOKER SET search_path=pg_catalog AS $$
DECLARE tenant UUID; root UUID;
BEGIN
    IF p_context IS NULL OR p_context='00000000-0000-0000-0000-000000000000'
       OR p_context::TEXT IS DISTINCT FROM pg_catalog.current_setting('firefly.product_definition.private_read_id',TRUE)
       OR pg_catalog.current_setting('firefly.product_definition.private_read_kind',TRUE) IS DISTINCT FROM 'READ_SELECTION_CURRENT' THEN
        RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_PRIVATE_READ_UNAVAILABLE';
    END IF;
    tenant:=pg_catalog.current_setting('firefly.product_definition.private_read_tenant',TRUE)::UUID;
    root:=pg_catalog.current_setting('firefly.product_definition.private_read_root',TRUE)::UUID;
    IF NOT public.product_definition_private_read_matches(tenant,root,'public.product_definition_root'::REGCLASS,root) THEN
        RAISE EXCEPTION USING ERRCODE='PD403', MESSAGE='CURRENT_PRODUCT_PERMISSION_REQUIRED';
    END IF;
    RETURN TRUE;
END
$$;


CREATE FUNCTION public.product_definition_native_before()
RETURNS TRIGGER LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog SET TimeZone='UTC' AS $$
DECLARE context firefly_product_definition_private.native_command; expected JSONB; value JSONB; old_value JSONB;
    key_name TEXT; identity JSONB; context_text TEXT;
BEGIN
    context_text:=pg_catalog.current_setting('firefly.product_definition.command_id',TRUE);
    IF TG_TABLE_NAME=ANY(ARRAY['product','product_version']) AND NOT pg_catalog.pg_has_role(session_user,'firefly_product_definition_commands','MEMBER')
       AND COALESCE(context_text,'')='' THEN
        IF TG_OP='DELETE' THEN RETURN OLD; END IF; RETURN NEW;
    END IF;
    IF TG_OP='DELETE' THEN RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_NATIVE_APPEND_ONLY'; END IF;
    context:=firefly_product_definition_private.current_command(FALSE);
    IF context.mode<>'FRESH' THEN RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_NATIVE_WRITE_NOT_PLANNED'; END IF;
    key_name:=firefly_product_definition_private.primary_key(TG_TABLE_NAME); value:=to_jsonb(NEW);
    IF key_name IS NULL THEN RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_NATIVE_RELATION_REQUIRED'; END IF;
    identity:=jsonb_build_object(key_name,value->key_name);
    SELECT e INTO expected FROM pg_catalog.jsonb_array_elements(context.expected_rows)e
        WHERE (e->>'relationOid')::OID=TG_RELID AND e->>'operation'=TG_OP AND e->'key'=identity;
    IF NOT FOUND OR EXISTS (SELECT 1 FROM firefly_product_definition_private.row_witness w
        WHERE w.command_id=context.command_id AND w.relation_oid=TG_RELID AND w.row_identity=identity AND w.operation=TG_OP) THEN
        RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_NATIVE_WRITE_NOT_PLANNED';
    END IF;
    IF TG_OP='INSERT' THEN
        IF value ? 'created_at' THEN value:=jsonb_set(value,'{created_at}',to_jsonb(context.recorded_at)); END IF;
        IF value ? 'recorded_at' THEN value:=jsonb_set(value,'{recorded_at}',to_jsonb(context.recorded_at)); END IF;
        IF value ? 'actor_issuer' THEN value:=jsonb_set(value,'{actor_issuer}',to_jsonb(context.actor_issuer)); END IF;
        IF value ? 'actor_id' THEN value:=jsonb_set(value,'{actor_id}',to_jsonb(context.actor_id)); END IF;
        IF TG_TABLE_NAME=ANY(ARRAY['product','product_version']) THEN
            value:=value||jsonb_build_object('date_created',context.recorded_at AT TIME ZONE 'UTC','date_updated',context.recorded_at AT TIME ZONE 'UTC');
        END IF;
        NEW:=pg_catalog.jsonb_populate_record(NEW,value);
    ELSE old_value:=to_jsonb(OLD);
    END IF;
    IF to_jsonb(NEW) IS DISTINCT FROM expected->'new' OR old_value IS DISTINCT FROM NULLIF(expected->'old','null'::JSONB) THEN
        RAISE EXCEPTION USING ERRCODE='PD409', MESSAGE='PRODUCT_NATIVE_ROW_CONFLICT';
    END IF;
    RETURN NEW;
END
$$;

CREATE FUNCTION public.product_definition_native_after()
RETURNS TRIGGER LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog SET TimeZone='UTC' AS $$
DECLARE context firefly_product_definition_private.native_command; expected JSONB; identity JSONB; old_value JSONB;
BEGIN
    IF TG_TABLE_NAME=ANY(ARRAY['product','product_version']) AND NOT pg_catalog.pg_has_role(session_user,'firefly_product_definition_commands','MEMBER')
       AND COALESCE(pg_catalog.current_setting('firefly.product_definition.command_id',TRUE),'')='' THEN RETURN NEW; END IF;
    context:=firefly_product_definition_private.current_command(FALSE);
    identity:=jsonb_build_object(firefly_product_definition_private.primary_key(TG_TABLE_NAME),to_jsonb(NEW)->firefly_product_definition_private.primary_key(TG_TABLE_NAME));
    IF TG_OP='UPDATE' THEN old_value:=to_jsonb(OLD); END IF;
    SELECT e INTO expected FROM pg_catalog.jsonb_array_elements(context.expected_rows)e
        WHERE (e->>'relationOid')::OID=TG_RELID AND e->>'operation'=TG_OP AND e->'key'=identity;
    IF NOT FOUND OR context.mode<>'FRESH' OR to_jsonb(NEW) IS DISTINCT FROM expected->'new' OR old_value IS DISTINCT FROM NULLIF(expected->'old','null'::JSONB) THEN
        RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_NATIVE_WITNESS_REQUIRED';
    END IF;
    INSERT INTO firefly_product_definition_private.row_witness(command_id,tenant_id,root_id,native_transaction,backend_pid,
        relation_oid,relation_schema,relation_name,row_identity,operation,old_row,new_row)
    VALUES(context.command_id,context.tenant_id,context.root_id,pg_catalog.pg_current_xact_id(),pg_catalog.pg_backend_pid(),
        TG_RELID,TG_TABLE_SCHEMA,TG_TABLE_NAME,identity,TG_OP,old_value,to_jsonb(NEW));
    RETURN NEW;
END
$$;

CREATE FUNCTION firefly_product_definition_private.guard_claim_update()
RETURNS TRIGGER LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE context firefly_product_definition_private.native_command;
BEGIN
    IF TG_OP<>'UPDATE' THEN RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_NATIVE_CLAIM_IMMUTABLE'; END IF;
    context:=firefly_product_definition_private.current_command(FALSE);
    IF context.mode<>'FRESH' OR (to_jsonb(OLD)-'action_id') IS DISTINCT FROM (to_jsonb(NEW)-'action_id')
       OR OLD.action_id IS NOT NULL OR NEW.action_id IS DISTINCT FROM context.action_id
       OR OLD.tenant_id IS DISTINCT FROM context.tenant_id OR OLD.root_id IS DISTINCT FROM context.root_id
       OR OLD.operation_id IS DISTINCT FROM context.operation_id THEN
        RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_NATIVE_CLAIM_IMMUTABLE';
    END IF;
    RETURN NEW;
END
$$;
CREATE TRIGGER product_native_claim_immutable BEFORE UPDATE OR DELETE
    ON firefly_product_definition_private.operation_claim FOR EACH ROW
    EXECUTE FUNCTION firefly_product_definition_private.guard_claim_update();

CREATE FUNCTION firefly_product_definition_private.guard_command_update()
RETURNS TRIGGER LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
BEGIN
    IF TG_OP<>'UPDATE' OR (to_jsonb(OLD)-'state') IS DISTINCT FROM (to_jsonb(NEW)-'state')
       OR OLD.state<>'OPEN' OR NEW.state<>'FINISHED'
       OR OLD.native_transaction IS DISTINCT FROM pg_catalog.pg_current_xact_id()
       OR OLD.backend_pid IS DISTINCT FROM pg_catalog.pg_backend_pid() OR OLD.session_login IS DISTINCT FROM session_user THEN
        RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_NATIVE_COMMAND_IMMUTABLE';
    END IF;
    RETURN NEW;
END
$$;
CREATE TRIGGER product_native_command_immutable BEFORE UPDATE OR DELETE
    ON firefly_product_definition_private.native_command FOR EACH ROW
    EXECUTE FUNCTION firefly_product_definition_private.guard_command_update();
CREATE TRIGGER product_native_witness_immutable BEFORE UPDATE OR DELETE
    ON firefly_product_definition_private.row_witness FOR EACH ROW
    EXECUTE FUNCTION public.guard_product_definition_revision();


CREATE FUNCTION firefly_product_definition_private.verify_complete(p_command UUID, require_finished BOOLEAN)
RETURNS VOID LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog SET TimeZone='UTC' AS $$
DECLARE context firefly_product_definition_private.native_command; claim firefly_product_definition_private.operation_claim;
    expected JSONB; actual JSONB; key_name TEXT; expected_relation_name TEXT; matched BIGINT; total BIGINT; millis BIGINT;
    action_row public.product_definition_action; original_row public.product_definition_action_private;
    product_row JSONB; category_path UUID[];
BEGIN
    IF NOT firefly_product_definition_private.session_allowed() THEN RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_NATIVE_RUNTIME_UNAVAILABLE'; END IF;
    SELECT * INTO context FROM firefly_product_definition_private.native_command c WHERE c.command_id=p_command
        AND c.native_transaction=pg_catalog.pg_current_xact_id() AND c.backend_pid=pg_catalog.pg_backend_pid() AND c.session_login=session_user;
    IF NOT FOUND OR context.state IS DISTINCT FROM (CASE WHEN require_finished THEN 'FINISHED' ELSE 'OPEN' END)
       OR NOT firefly_product_definition_private.gate_held(context.tenant_id,context.root_id) THEN
        RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_NATIVE_COMPLETION_REQUIRED';
    END IF;
    millis:=floor(extract(epoch FROM pg_catalog.clock_timestamp())*1000)::BIGINT;
    IF millis>=context.deadline_millis OR context.deadline_millis>millis+30000
       OR context.deadline_millis>context.actor_until_millis OR context.deadline_millis>context.permit_until_millis THEN
        RAISE EXCEPTION USING ERRCODE='PD403', MESSAGE='CURRENT_PRODUCT_PERMISSION_REQUIRED';
    END IF;
    SELECT * INTO claim FROM firefly_product_definition_private.operation_claim c
        WHERE c.tenant_id=context.tenant_id AND c.root_id=context.root_id AND c.operation_id=context.operation_id;
    IF NOT FOUND OR claim.command IS DISTINCT FROM context.command
       OR claim.original_intent_digest IS DISTINCT FROM firefly_product_definition_private.native_hash('product-definition-action-v1',claim.original_intent)
       OR claim.prepared_plan_digest IS DISTINCT FROM firefly_product_definition_private.native_hash('product-definition-native-plan-v1',claim.prepared_plan)
       OR (context.mode='FRESH' AND claim.claimed_at IS DISTINCT FROM context.recorded_at)
       OR (context.mode='FRESH' AND NOT require_finished AND claim.action_id IS NOT NULL)
       OR ((context.mode='REPLAY' OR require_finished) AND claim.action_id IS DISTINCT FROM context.action_id) THEN
        RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_NATIVE_CLAIM_REQUIRED';
    END IF;
    SELECT count(*) INTO total FROM firefly_product_definition_private.row_witness w WHERE w.command_id=context.command_id;
    IF total IS DISTINCT FROM jsonb_array_length(context.expected_rows) THEN
        RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_NATIVE_WITNESS_REQUIRED';
    END IF;
    IF context.mode='REPLAY' THEN
        IF jsonb_array_length(context.expected_rows)<>0 THEN RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_NATIVE_REPLAY_READ_ONLY'; END IF;
    ELSE
        FOR expected IN SELECT * FROM pg_catalog.jsonb_array_elements(context.expected_rows) LOOP
            expected_relation_name:=expected->>'relation'; key_name:=firefly_product_definition_private.primary_key(expected_relation_name);
            IF key_name IS NULL OR pg_catalog.to_regclass(pg_catalog.format('public.%I',expected_relation_name))::OID IS DISTINCT FROM (expected->>'relationOid')::OID THEN
                RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_NATIVE_RELATION_REQUIRED';
            END IF;
            SELECT count(*) INTO matched FROM firefly_product_definition_private.row_witness w
                WHERE w.command_id=context.command_id AND w.tenant_id=context.tenant_id AND w.root_id=context.root_id
                  AND w.native_transaction=context.native_transaction AND w.backend_pid=context.backend_pid
                  AND w.relation_oid=(expected->>'relationOid')::OID AND w.relation_schema='public' AND w.relation_name=expected_relation_name
                  AND w.row_identity=expected->'key' AND w.operation=expected->>'operation'
                  AND w.old_row IS NOT DISTINCT FROM NULLIF(expected->'old','null'::JSONB) AND w.new_row=expected->'new';
            IF matched<>1 THEN RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_NATIVE_WITNESS_REQUIRED'; END IF;
            EXECUTE pg_catalog.format('SELECT to_jsonb(r) FROM public.%I r WHERE r.%I=$1',expected_relation_name,key_name)
                INTO actual USING (expected->'new'->>key_name)::UUID;
            IF actual IS DISTINCT FROM expected->'new' THEN
                RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_NATIVE_FINAL_ROW_REQUIRED';
            END IF;
        END LOOP;
        IF context.command='CREATE_DRAFT' THEN
            SELECT e->'new' INTO product_row FROM jsonb_array_elements(context.expected_rows)e WHERE e->>'relation'='product';
            SELECT s.category_path INTO category_path FROM firefly_product_definition_private.product_birth_scope s
                WHERE s.product_id=(product_row->>'product_id')::UUID AND s.tenant_id=context.tenant_id AND s.root_id=context.root_id
                  AND s.birth_transaction=context.native_transaction;
            IF category_path IS NULL OR NOT EXISTS (SELECT 1 FROM firefly_product_definition_private.product_birth b
                    WHERE b.product_id=(product_row->>'product_id')::UUID AND b.tenant_id=context.tenant_id
                      AND b.native_root_id=context.root_id AND b.birth_transaction=context.native_transaction)
               OR NOT EXISTS (SELECT 1 FROM firefly_product_definition_private.product_binding b
                    WHERE b.product_id=(product_row->>'product_id')::UUID AND b.tenant_id=context.tenant_id AND b.root_id=context.root_id)
               OR category_path IS DISTINCT FROM firefly_product_definition_private.category_path((product_row->>'product_category_id')::UUID)
               OR EXISTS (SELECT 1 FROM unnest(category_path) id WHERE NOT EXISTS (
                    SELECT 1 FROM firefly_product_definition_private.category_binding b WHERE b.category_id=id AND b.root_id=context.root_id)) THEN
                RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_NATIVE_BIRTH_PROOF_REQUIRED';
            END IF;
        END IF;
    END IF;
    SELECT * INTO action_row FROM public.product_definition_action a WHERE a.tenant_id=context.tenant_id AND a.root_id=context.root_id AND a.action_id=context.action_id;
    IF NOT FOUND OR action_row.command IS DISTINCT FROM context.command
       OR action_row.created_at IS DISTINCT FROM (CASE WHEN context.mode='REPLAY' THEN context.retained_action_recorded_at ELSE context.recorded_at END)
       OR action_row.created_at IS DISTINCT FROM claim.claimed_at THEN
        RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_NATIVE_ACTION_REQUIRED';
    END IF;
    SELECT * INTO original_row FROM public.product_definition_action_private a WHERE a.tenant_id=context.tenant_id AND a.root_id=context.root_id AND a.action_id=context.action_id;
    IF NOT FOUND OR original_row.canonical_instruction IS DISTINCT FROM claim.original_intent OR original_row.request_fingerprint IS DISTINCT FROM claim.original_intent_digest THEN
        RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_NATIVE_ORIGINAL_INTENT_REQUIRED';
    END IF;
END
$$;

CREATE FUNCTION public.finish_product_definition_command(p_command UUID)
RETURNS BOOLEAN LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE context firefly_product_definition_private.native_command;
BEGIN
    context:=firefly_product_definition_private.current_command(FALSE);
    IF context.command_id IS DISTINCT FROM p_command THEN RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_NATIVE_CONTEXT_REQUIRED'; END IF;
    PERFORM firefly_product_definition_private.verify_complete(p_command,FALSE);
    IF context.mode='FRESH' THEN
        UPDATE firefly_product_definition_private.operation_claim c SET action_id=context.action_id
            WHERE c.tenant_id=context.tenant_id AND c.root_id=context.root_id AND c.operation_id=context.operation_id AND c.action_id IS NULL;
        IF NOT FOUND THEN RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_NATIVE_CLAIM_REQUIRED'; END IF;
    END IF;
    UPDATE firefly_product_definition_private.native_command c SET state='FINISHED' WHERE c.command_id=p_command AND c.state='OPEN';
    IF NOT FOUND THEN RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_NATIVE_COMPLETION_REQUIRED'; END IF;
    RETURN TRUE;
END
$$;

CREATE FUNCTION public.product_definition_native_complete()
RETURNS TRIGGER LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE identifier UUID; key_name TEXT; identity JSONB;
BEGIN
    IF TG_TABLE_SCHEMA='firefly_product_definition_private' AND TG_TABLE_NAME='native_command' THEN identifier:=NEW.command_id;
    ELSE
        IF TG_TABLE_NAME=ANY(ARRAY['product','product_version']) AND NOT pg_has_role(session_user,'firefly_product_definition_commands','MEMBER')
           AND COALESCE(current_setting('firefly.product_definition.command_id',TRUE),'')='' THEN RETURN NEW; END IF;
        key_name:=firefly_product_definition_private.primary_key(TG_TABLE_NAME); identity:=jsonb_build_object(key_name,to_jsonb(NEW)->key_name);
        SELECT w.command_id INTO identifier FROM firefly_product_definition_private.row_witness w
            WHERE w.native_transaction=pg_current_xact_id() AND w.backend_pid=pg_backend_pid()
              AND w.relation_oid=TG_RELID AND w.row_identity=identity AND w.operation=TG_OP;
        IF NOT FOUND THEN RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_NATIVE_WITNESS_REQUIRED'; END IF;
    END IF;
    PERFORM firefly_product_definition_private.verify_complete(identifier,TRUE);
    RETURN NEW;
END
$$;


CREATE FUNCTION firefly_product_definition_private.planned_version(p_value JSONB, deferred_check BOOLEAN)
RETURNS BOOLEAN LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog SET TimeZone='UTC' AS $$
DECLARE context firefly_product_definition_private.native_command; expected JSONB;
BEGIN
    IF NOT pg_has_role(session_user,'firefly_product_definition_commands','MEMBER')
       AND COALESCE(current_setting('firefly.product_definition.command_id',TRUE),'')='' THEN RETURN FALSE; END IF;
    context:=firefly_product_definition_private.current_command(deferred_check);
    IF context.mode<>'FRESH' OR context.command<>'PUBLISH'
       OR context.state IS DISTINCT FROM (CASE WHEN deferred_check THEN 'FINISHED' ELSE 'OPEN' END) THEN RETURN FALSE; END IF;
    SELECT e INTO expected FROM jsonb_array_elements(context.expected_rows)e
        WHERE e->>'relation'='product_version' AND e->>'operation'='INSERT'
          AND e->'old'='null'::JSONB AND e->'new'=p_value;
    IF NOT FOUND THEN RETURN FALSE; END IF;
    IF deferred_check THEN
        IF NOT EXISTS (SELECT 1 FROM firefly_product_definition_private.row_witness w
            WHERE w.command_id=context.command_id AND w.native_transaction=context.native_transaction AND w.backend_pid=context.backend_pid
              AND w.relation_oid='public.product_version'::REGCLASS AND w.operation='INSERT' AND w.old_row IS NULL AND w.new_row=p_value) THEN RETURN FALSE; END IF;
        PERFORM firefly_product_definition_private.verify_complete(context.command_id,TRUE);
    END IF;
    RETURN TRUE;
END
$$;

CREATE OR REPLACE FUNCTION public.guard_product_definition_legacy_child() RETURNS TRIGGER
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog SET TimeZone='UTC' AS $$
DECLARE identities UUID[]:=ARRAY[]::UUID[];
BEGIN
    IF TG_TABLE_SCHEMA='public' AND TG_TABLE_NAME='product_version' AND TG_OP='INSERT'
       AND firefly_product_definition_private.planned_version(to_jsonb(NEW),TG_WHEN='AFTER') THEN RETURN NEW; END IF;
    IF TG_OP<>'INSERT' THEN
        identities:=identities||OLD.product_id;
        IF TG_TABLE_NAME='product_relationship' THEN identities:=identities||OLD.related_product_id; END IF;
    END IF;
    IF TG_OP<>'DELETE' THEN
        identities:=identities||NEW.product_id;
        IF TG_TABLE_NAME='product_relationship' THEN identities:=identities||NEW.related_product_id; END IF;
    END IF;
    IF EXISTS (SELECT 1 FROM firefly_product_definition_private.product_binding b WHERE b.product_id=ANY(identities)) THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='PRODUCT_DEFINITION_MANAGED_LEGACY_CHILD';
    END IF;
    IF TG_OP='DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END
$$;


GRANT USAGE ON SCHEMA public TO firefly_product_definition_commands;
GRANT INSERT (product_id,tenant_id,product_category_id,product_type,product_name,product_code,product_description,product_status,launch_date,end_date,date_created,date_updated,marketing_features) ON public.product TO firefly_product_definition_commands;
GRANT SELECT (product_id) ON public.product TO firefly_product_definition_commands;
CREATE TRIGGER a00_product_definition_native_guard BEFORE INSERT OR UPDATE OR DELETE ON public.product FOR EACH ROW EXECUTE FUNCTION public.product_definition_native_before();
CREATE TRIGGER z99_product_definition_native_witness AFTER INSERT OR UPDATE ON public.product FOR EACH ROW EXECUTE FUNCTION public.product_definition_native_after();
CREATE CONSTRAINT TRIGGER product_definition_native_final AFTER INSERT OR UPDATE ON public.product DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION public.product_definition_native_complete();
GRANT INSERT (action_id,tenant_id,root_id,command,visibility,canonical_receipt,actor_issuer,actor_id,created_at) ON public.product_definition_action TO firefly_product_definition_commands;
GRANT SELECT ON public.product_definition_action TO firefly_product_definition_commands;
CREATE POLICY product_native_select ON public.product_definition_action FOR SELECT TO firefly_product_definition_commands USING (public.product_definition_command_scope_matches(tenant_id,root_id,'public.product_definition_action'::REGCLASS,action_id,'SELECT'));
CREATE POLICY product_native_insert ON public.product_definition_action FOR INSERT TO firefly_product_definition_commands WITH CHECK (public.product_definition_command_scope_matches(tenant_id,root_id,'public.product_definition_action'::REGCLASS,action_id,'INSERT'));
CREATE TRIGGER a00_product_definition_native_guard BEFORE INSERT OR UPDATE OR DELETE ON public.product_definition_action FOR EACH ROW EXECUTE FUNCTION public.product_definition_native_before();
CREATE TRIGGER z99_product_definition_native_witness AFTER INSERT OR UPDATE ON public.product_definition_action FOR EACH ROW EXECUTE FUNCTION public.product_definition_native_after();
CREATE CONSTRAINT TRIGGER product_definition_native_final AFTER INSERT OR UPDATE ON public.product_definition_action DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION public.product_definition_native_complete();
GRANT INSERT (action_id,tenant_id,root_id,request_fingerprint,selection_fingerprint,canonical_instruction) ON public.product_definition_action_private TO firefly_product_definition_commands;
GRANT SELECT ON public.product_definition_action_private TO firefly_product_definition_commands;
CREATE POLICY product_native_select ON public.product_definition_action_private FOR SELECT TO firefly_product_definition_commands USING (public.product_definition_command_scope_matches(tenant_id,root_id,'public.product_definition_action_private'::REGCLASS,action_id,'SELECT'));
CREATE POLICY product_native_insert ON public.product_definition_action_private FOR INSERT TO firefly_product_definition_commands WITH CHECK (public.product_definition_command_scope_matches(tenant_id,root_id,'public.product_definition_action_private'::REGCLASS,action_id,'INSERT'));
CREATE TRIGGER a00_product_definition_native_guard BEFORE INSERT OR UPDATE OR DELETE ON public.product_definition_action_private FOR EACH ROW EXECUTE FUNCTION public.product_definition_native_before();
CREATE TRIGGER z99_product_definition_native_witness AFTER INSERT OR UPDATE ON public.product_definition_action_private FOR EACH ROW EXECUTE FUNCTION public.product_definition_native_after();
CREATE CONSTRAINT TRIGGER product_definition_native_final AFTER INSERT OR UPDATE ON public.product_definition_action_private DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION public.product_definition_native_complete();
GRANT INSERT (audit_id,tenant_id,root_id,action_id,event_type,actor_issuer,actor_id,created_at) ON public.product_definition_audit TO firefly_product_definition_commands;
GRANT SELECT ON public.product_definition_audit TO firefly_product_definition_commands;
CREATE POLICY product_native_select ON public.product_definition_audit FOR SELECT TO firefly_product_definition_commands USING (public.product_definition_command_scope_matches(tenant_id,root_id,'public.product_definition_audit'::REGCLASS,audit_id,'SELECT'));
CREATE POLICY product_native_insert ON public.product_definition_audit FOR INSERT TO firefly_product_definition_commands WITH CHECK (public.product_definition_command_scope_matches(tenant_id,root_id,'public.product_definition_audit'::REGCLASS,audit_id,'INSERT'));
CREATE TRIGGER a00_product_definition_native_guard BEFORE INSERT OR UPDATE OR DELETE ON public.product_definition_audit FOR EACH ROW EXECUTE FUNCTION public.product_definition_native_before();
CREATE TRIGGER z99_product_definition_native_witness AFTER INSERT OR UPDATE ON public.product_definition_audit FOR EACH ROW EXECUTE FUNCTION public.product_definition_native_after();
CREATE CONSTRAINT TRIGGER product_definition_native_final AFTER INSERT OR UPDATE ON public.product_definition_audit DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION public.product_definition_native_complete();
GRANT INSERT (root_id,tenant_id,cost_head,cost_state_version) ON public.product_definition_control TO firefly_product_definition_commands;
GRANT SELECT ON public.product_definition_control TO firefly_product_definition_commands;
GRANT UPDATE (cost_head,cost_state_version) ON public.product_definition_control TO firefly_product_definition_commands;
CREATE POLICY product_native_select ON public.product_definition_control FOR SELECT TO firefly_product_definition_commands USING (public.product_definition_command_scope_matches(tenant_id,root_id,'public.product_definition_control'::REGCLASS,root_id,'SELECT'));
CREATE POLICY product_native_insert ON public.product_definition_control FOR INSERT TO firefly_product_definition_commands WITH CHECK (public.product_definition_command_scope_matches(tenant_id,root_id,'public.product_definition_control'::REGCLASS,root_id,'INSERT'));
CREATE POLICY product_native_update ON public.product_definition_control FOR UPDATE TO firefly_product_definition_commands USING (public.product_definition_command_scope_matches(tenant_id,root_id,'public.product_definition_control'::REGCLASS,root_id,'UPDATE')) WITH CHECK (public.product_definition_command_scope_matches(tenant_id,root_id,'public.product_definition_control'::REGCLASS,root_id,'UPDATE'));
CREATE POLICY product_private_read ON public.product_definition_control FOR SELECT TO firefly_product_definition_commands USING (public.product_definition_private_read_matches(tenant_id,root_id,'public.product_definition_control'::REGCLASS,root_id));
CREATE TRIGGER a00_product_definition_native_guard BEFORE INSERT OR UPDATE OR DELETE ON public.product_definition_control FOR EACH ROW EXECUTE FUNCTION public.product_definition_native_before();
CREATE TRIGGER z99_product_definition_native_witness AFTER INSERT OR UPDATE ON public.product_definition_control FOR EACH ROW EXECUTE FUNCTION public.product_definition_native_after();
CREATE CONSTRAINT TRIGGER product_definition_native_final AFTER INSERT OR UPDATE ON public.product_definition_control DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION public.product_definition_native_complete();
GRANT INSERT (event_id,tenant_id,root_id,action_id,cost_revision_id,created_at) ON public.product_definition_cost_outbox TO firefly_product_definition_commands;
GRANT SELECT ON public.product_definition_cost_outbox TO firefly_product_definition_commands;
CREATE POLICY product_native_select ON public.product_definition_cost_outbox FOR SELECT TO firefly_product_definition_commands USING (public.product_definition_command_scope_matches(tenant_id,root_id,'public.product_definition_cost_outbox'::REGCLASS,event_id,'SELECT'));
CREATE POLICY product_native_insert ON public.product_definition_cost_outbox FOR INSERT TO firefly_product_definition_commands WITH CHECK (public.product_definition_command_scope_matches(tenant_id,root_id,'public.product_definition_cost_outbox'::REGCLASS,event_id,'INSERT'));
CREATE TRIGGER a00_product_definition_native_guard BEFORE INSERT OR UPDATE OR DELETE ON public.product_definition_cost_outbox FOR EACH ROW EXECUTE FUNCTION public.product_definition_native_before();
CREATE TRIGGER z99_product_definition_native_witness AFTER INSERT OR UPDATE ON public.product_definition_cost_outbox FOR EACH ROW EXECUTE FUNCTION public.product_definition_native_after();
CREATE CONSTRAINT TRIGGER product_definition_native_final AFTER INSERT OR UPDATE ON public.product_definition_cost_outbox DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION public.product_definition_native_complete();
GRANT INSERT (cost_revision_id,tenant_id,root_id,canonical_cost,cost_digest,encoding_version,actor_issuer,actor_id,created_at) ON public.product_definition_cost_revision TO firefly_product_definition_commands;
GRANT SELECT ON public.product_definition_cost_revision TO firefly_product_definition_commands;
CREATE POLICY product_native_select ON public.product_definition_cost_revision FOR SELECT TO firefly_product_definition_commands USING (public.product_definition_command_scope_matches(tenant_id,root_id,'public.product_definition_cost_revision'::REGCLASS,cost_revision_id,'SELECT'));
CREATE POLICY product_native_insert ON public.product_definition_cost_revision FOR INSERT TO firefly_product_definition_commands WITH CHECK (public.product_definition_command_scope_matches(tenant_id,root_id,'public.product_definition_cost_revision'::REGCLASS,cost_revision_id,'INSERT'));
CREATE POLICY product_private_read ON public.product_definition_cost_revision FOR SELECT TO firefly_product_definition_commands USING (public.product_definition_private_read_matches(tenant_id,root_id,'public.product_definition_cost_revision'::REGCLASS,cost_revision_id));
CREATE TRIGGER a00_product_definition_native_guard BEFORE INSERT OR UPDATE OR DELETE ON public.product_definition_cost_revision FOR EACH ROW EXECUTE FUNCTION public.product_definition_native_before();
CREATE TRIGGER z99_product_definition_native_witness AFTER INSERT OR UPDATE ON public.product_definition_cost_revision FOR EACH ROW EXECUTE FUNCTION public.product_definition_native_after();
CREATE CONSTRAINT TRIGGER product_definition_native_final AFTER INSERT OR UPDATE ON public.product_definition_cost_revision DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION public.product_definition_native_complete();
GRANT INSERT (history_id,tenant_id,root_id,action_id,public_state_version,event_type) ON public.product_definition_history TO firefly_product_definition_commands;
GRANT SELECT ON public.product_definition_history TO firefly_product_definition_commands;
CREATE POLICY product_native_select ON public.product_definition_history FOR SELECT TO firefly_product_definition_commands USING (public.product_definition_command_scope_matches(tenant_id,root_id,'public.product_definition_history'::REGCLASS,history_id,'SELECT'));
CREATE POLICY product_native_insert ON public.product_definition_history FOR INSERT TO firefly_product_definition_commands WITH CHECK (public.product_definition_command_scope_matches(tenant_id,root_id,'public.product_definition_history'::REGCLASS,history_id,'INSERT'));
CREATE TRIGGER a00_product_definition_native_guard BEFORE INSERT OR UPDATE OR DELETE ON public.product_definition_history FOR EACH ROW EXECUTE FUNCTION public.product_definition_native_before();
CREATE TRIGGER z99_product_definition_native_witness AFTER INSERT OR UPDATE ON public.product_definition_history FOR EACH ROW EXECUTE FUNCTION public.product_definition_native_after();
CREATE CONSTRAINT TRIGGER product_definition_native_final AFTER INSERT OR UPDATE ON public.product_definition_history DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION public.product_definition_native_complete();
GRANT INSERT (event_id,tenant_id,root_id,action_id,event_type,created_at) ON public.product_definition_outbox TO firefly_product_definition_commands;
GRANT SELECT ON public.product_definition_outbox TO firefly_product_definition_commands;
CREATE POLICY product_native_select ON public.product_definition_outbox FOR SELECT TO firefly_product_definition_commands USING (public.product_definition_command_scope_matches(tenant_id,root_id,'public.product_definition_outbox'::REGCLASS,event_id,'SELECT'));
CREATE POLICY product_native_insert ON public.product_definition_outbox FOR INSERT TO firefly_product_definition_commands WITH CHECK (public.product_definition_command_scope_matches(tenant_id,root_id,'public.product_definition_outbox'::REGCLASS,event_id,'INSERT'));
CREATE TRIGGER a00_product_definition_native_guard BEFORE INSERT OR UPDATE OR DELETE ON public.product_definition_outbox FOR EACH ROW EXECUTE FUNCTION public.product_definition_native_before();
CREATE TRIGGER z99_product_definition_native_witness AFTER INSERT OR UPDATE ON public.product_definition_outbox FOR EACH ROW EXECUTE FUNCTION public.product_definition_native_after();
CREATE CONSTRAINT TRIGGER product_definition_native_final AFTER INSERT OR UPDATE ON public.product_definition_outbox DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION public.product_definition_native_complete();
GRANT INSERT (publication_id,tenant_id,root_id,review_id,revision_id,product_version_id,canonical_definition,public_digest,encoding_version,operational_admission,created_at) ON public.product_definition_publication TO firefly_product_definition_commands;
GRANT SELECT ON public.product_definition_publication TO firefly_product_definition_commands;
CREATE POLICY product_native_select ON public.product_definition_publication FOR SELECT TO firefly_product_definition_commands USING (public.product_definition_command_scope_matches(tenant_id,root_id,'public.product_definition_publication'::REGCLASS,publication_id,'SELECT'));
CREATE POLICY product_native_insert ON public.product_definition_publication FOR INSERT TO firefly_product_definition_commands WITH CHECK (public.product_definition_command_scope_matches(tenant_id,root_id,'public.product_definition_publication'::REGCLASS,publication_id,'INSERT'));
CREATE TRIGGER a00_product_definition_native_guard BEFORE INSERT OR UPDATE OR DELETE ON public.product_definition_publication FOR EACH ROW EXECUTE FUNCTION public.product_definition_native_before();
CREATE TRIGGER z99_product_definition_native_witness AFTER INSERT OR UPDATE ON public.product_definition_publication FOR EACH ROW EXECUTE FUNCTION public.product_definition_native_after();
CREATE CONSTRAINT TRIGGER product_definition_native_final AFTER INSERT OR UPDATE ON public.product_definition_publication DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION public.product_definition_native_complete();
GRANT INSERT (publication_id,tenant_id,root_id,state,state_version) ON public.product_definition_publication_control TO firefly_product_definition_commands;
GRANT SELECT ON public.product_definition_publication_control TO firefly_product_definition_commands;
GRANT UPDATE (state,state_version) ON public.product_definition_publication_control TO firefly_product_definition_commands;
CREATE POLICY product_native_select ON public.product_definition_publication_control FOR SELECT TO firefly_product_definition_commands USING (public.product_definition_command_scope_matches(tenant_id,root_id,'public.product_definition_publication_control'::REGCLASS,publication_id,'SELECT'));
CREATE POLICY product_native_insert ON public.product_definition_publication_control FOR INSERT TO firefly_product_definition_commands WITH CHECK (public.product_definition_command_scope_matches(tenant_id,root_id,'public.product_definition_publication_control'::REGCLASS,publication_id,'INSERT'));
CREATE POLICY product_native_update ON public.product_definition_publication_control FOR UPDATE TO firefly_product_definition_commands USING (public.product_definition_command_scope_matches(tenant_id,root_id,'public.product_definition_publication_control'::REGCLASS,publication_id,'UPDATE')) WITH CHECK (public.product_definition_command_scope_matches(tenant_id,root_id,'public.product_definition_publication_control'::REGCLASS,publication_id,'UPDATE'));
CREATE TRIGGER a00_product_definition_native_guard BEFORE INSERT OR UPDATE OR DELETE ON public.product_definition_publication_control FOR EACH ROW EXECUTE FUNCTION public.product_definition_native_before();
CREATE TRIGGER z99_product_definition_native_witness AFTER INSERT OR UPDATE ON public.product_definition_publication_control FOR EACH ROW EXECUTE FUNCTION public.product_definition_native_after();
CREATE CONSTRAINT TRIGGER product_definition_native_final AFTER INSERT OR UPDATE ON public.product_definition_publication_control DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION public.product_definition_native_complete();
GRANT INSERT (review_id,tenant_id,root_id,revision_id,public_digest,actor_issuer,actor_id,created_at) ON public.product_definition_review TO firefly_product_definition_commands;
GRANT SELECT ON public.product_definition_review TO firefly_product_definition_commands;
CREATE POLICY product_native_select ON public.product_definition_review FOR SELECT TO firefly_product_definition_commands USING (public.product_definition_command_scope_matches(tenant_id,root_id,'public.product_definition_review'::REGCLASS,review_id,'SELECT'));
CREATE POLICY product_native_insert ON public.product_definition_review FOR INSERT TO firefly_product_definition_commands WITH CHECK (public.product_definition_command_scope_matches(tenant_id,root_id,'public.product_definition_review'::REGCLASS,review_id,'INSERT'));
CREATE TRIGGER a00_product_definition_native_guard BEFORE INSERT OR UPDATE OR DELETE ON public.product_definition_review FOR EACH ROW EXECUTE FUNCTION public.product_definition_native_before();
CREATE TRIGGER z99_product_definition_native_witness AFTER INSERT OR UPDATE ON public.product_definition_review FOR EACH ROW EXECUTE FUNCTION public.product_definition_native_after();
CREATE CONSTRAINT TRIGGER product_definition_native_final AFTER INSERT OR UPDATE ON public.product_definition_review DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION public.product_definition_native_complete();
GRANT INSERT (review_id,tenant_id,root_id,submission_id,public_version,cost_version,state,reason,actor_issuer,actor_id,created_at) ON public.product_definition_review_decision TO firefly_product_definition_commands;
GRANT SELECT ON public.product_definition_review_decision TO firefly_product_definition_commands;
CREATE POLICY product_native_select ON public.product_definition_review_decision FOR SELECT TO firefly_product_definition_commands USING (public.product_definition_command_scope_matches(tenant_id,root_id,'public.product_definition_review_decision'::REGCLASS,review_id,'SELECT'));
CREATE POLICY product_native_insert ON public.product_definition_review_decision FOR INSERT TO firefly_product_definition_commands WITH CHECK (public.product_definition_command_scope_matches(tenant_id,root_id,'public.product_definition_review_decision'::REGCLASS,review_id,'INSERT'));
CREATE TRIGGER a00_product_definition_native_guard BEFORE INSERT OR UPDATE OR DELETE ON public.product_definition_review_decision FOR EACH ROW EXECUTE FUNCTION public.product_definition_native_before();
CREATE TRIGGER z99_product_definition_native_witness AFTER INSERT OR UPDATE ON public.product_definition_review_decision FOR EACH ROW EXECUTE FUNCTION public.product_definition_native_after();
CREATE CONSTRAINT TRIGGER product_definition_native_final AFTER INSERT OR UPDATE ON public.product_definition_review_decision DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION public.product_definition_native_complete();
GRANT INSERT (review_id,tenant_id,root_id,cost_revision_id,selection_fingerprint) ON public.product_definition_review_private TO firefly_product_definition_commands;
GRANT SELECT ON public.product_definition_review_private TO firefly_product_definition_commands;
CREATE POLICY product_native_select ON public.product_definition_review_private FOR SELECT TO firefly_product_definition_commands USING (public.product_definition_command_scope_matches(tenant_id,root_id,'public.product_definition_review_private'::REGCLASS,review_id,'SELECT'));
CREATE POLICY product_native_insert ON public.product_definition_review_private FOR INSERT TO firefly_product_definition_commands WITH CHECK (public.product_definition_command_scope_matches(tenant_id,root_id,'public.product_definition_review_private'::REGCLASS,review_id,'INSERT'));
CREATE TRIGGER a00_product_definition_native_guard BEFORE INSERT OR UPDATE OR DELETE ON public.product_definition_review_private FOR EACH ROW EXECUTE FUNCTION public.product_definition_native_before();
CREATE TRIGGER z99_product_definition_native_witness AFTER INSERT OR UPDATE ON public.product_definition_review_private FOR EACH ROW EXECUTE FUNCTION public.product_definition_native_after();
CREATE CONSTRAINT TRIGGER product_definition_native_final AFTER INSERT OR UPDATE ON public.product_definition_review_private DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION public.product_definition_native_complete();
GRANT INSERT (revision_id,tenant_id,root_id,canonical_definition,public_digest,encoding_version,source_publication_id,source_publication_digest,actor_issuer,actor_id,created_at) ON public.product_definition_revision TO firefly_product_definition_commands;
GRANT SELECT ON public.product_definition_revision TO firefly_product_definition_commands;
CREATE POLICY product_native_select ON public.product_definition_revision FOR SELECT TO firefly_product_definition_commands USING (public.product_definition_command_scope_matches(tenant_id,root_id,'public.product_definition_revision'::REGCLASS,revision_id,'SELECT'));
CREATE POLICY product_native_insert ON public.product_definition_revision FOR INSERT TO firefly_product_definition_commands WITH CHECK (public.product_definition_command_scope_matches(tenant_id,root_id,'public.product_definition_revision'::REGCLASS,revision_id,'INSERT'));
CREATE POLICY product_private_read ON public.product_definition_revision FOR SELECT TO firefly_product_definition_commands USING (public.product_definition_private_read_matches(tenant_id,root_id,'public.product_definition_revision'::REGCLASS,revision_id));
CREATE TRIGGER a00_product_definition_native_guard BEFORE INSERT OR UPDATE OR DELETE ON public.product_definition_revision FOR EACH ROW EXECUTE FUNCTION public.product_definition_native_before();
CREATE TRIGGER z99_product_definition_native_witness AFTER INSERT OR UPDATE ON public.product_definition_revision FOR EACH ROW EXECUTE FUNCTION public.product_definition_native_after();
CREATE CONSTRAINT TRIGGER product_definition_native_final AFTER INSERT OR UPDATE ON public.product_definition_revision DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION public.product_definition_native_complete();
GRANT INSERT (root_id,tenant_id,product_id,product_code,business_area,profile_code,profile_version,phase,public_state_version,public_head,active_review_id,created_at) ON public.product_definition_root TO firefly_product_definition_commands;
GRANT SELECT ON public.product_definition_root TO firefly_product_definition_commands;
GRANT UPDATE (public_head,phase,public_state_version,active_review_id) ON public.product_definition_root TO firefly_product_definition_commands;
CREATE POLICY product_native_select ON public.product_definition_root FOR SELECT TO firefly_product_definition_commands USING (public.product_definition_command_scope_matches(tenant_id,root_id,'public.product_definition_root'::REGCLASS,root_id,'SELECT'));
CREATE POLICY product_native_insert ON public.product_definition_root FOR INSERT TO firefly_product_definition_commands WITH CHECK (public.product_definition_command_scope_matches(tenant_id,root_id,'public.product_definition_root'::REGCLASS,root_id,'INSERT'));
CREATE POLICY product_native_update ON public.product_definition_root FOR UPDATE TO firefly_product_definition_commands USING (public.product_definition_command_scope_matches(tenant_id,root_id,'public.product_definition_root'::REGCLASS,root_id,'UPDATE')) WITH CHECK (public.product_definition_command_scope_matches(tenant_id,root_id,'public.product_definition_root'::REGCLASS,root_id,'UPDATE'));
CREATE POLICY product_private_read ON public.product_definition_root FOR SELECT TO firefly_product_definition_commands USING (public.product_definition_private_read_matches(tenant_id,root_id,'public.product_definition_root'::REGCLASS,root_id) AND profile_code='ACCOUNT_TARIFF_V1' AND profile_version=1);
CREATE TRIGGER a00_product_definition_native_guard BEFORE INSERT OR UPDATE OR DELETE ON public.product_definition_root FOR EACH ROW EXECUTE FUNCTION public.product_definition_native_before();
CREATE TRIGGER z99_product_definition_native_witness AFTER INSERT OR UPDATE ON public.product_definition_root FOR EACH ROW EXECUTE FUNCTION public.product_definition_native_after();
CREATE CONSTRAINT TRIGGER product_definition_native_final AFTER INSERT OR UPDATE ON public.product_definition_root DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION public.product_definition_native_complete();
GRANT INSERT (submission_id,tenant_id,root_id,revision_id,public_digest,public_version,actor_issuer,actor_id,created_at) ON public.product_definition_submission TO firefly_product_definition_commands;
GRANT SELECT ON public.product_definition_submission TO firefly_product_definition_commands;
CREATE POLICY product_native_select ON public.product_definition_submission FOR SELECT TO firefly_product_definition_commands USING (public.product_definition_command_scope_matches(tenant_id,root_id,'public.product_definition_submission'::REGCLASS,submission_id,'SELECT'));
CREATE POLICY product_native_insert ON public.product_definition_submission FOR INSERT TO firefly_product_definition_commands WITH CHECK (public.product_definition_command_scope_matches(tenant_id,root_id,'public.product_definition_submission'::REGCLASS,submission_id,'INSERT'));
CREATE POLICY product_private_read ON public.product_definition_submission FOR SELECT TO firefly_product_definition_commands USING (public.product_definition_private_read_matches(tenant_id,root_id,'public.product_definition_submission'::REGCLASS,submission_id) AND (current_setting('firefly.product_definition.private_read_kind',TRUE)='READ_SELECTION' OR EXISTS (SELECT 1 FROM public.product_definition_root r WHERE r.tenant_id=product_definition_submission.tenant_id AND r.root_id=product_definition_submission.root_id AND r.public_head=product_definition_submission.revision_id AND r.public_state_version=product_definition_submission.public_version AND r.phase='IN_REVIEW')));
CREATE TRIGGER a00_product_definition_native_guard BEFORE INSERT OR UPDATE OR DELETE ON public.product_definition_submission FOR EACH ROW EXECUTE FUNCTION public.product_definition_native_before();
CREATE TRIGGER z99_product_definition_native_witness AFTER INSERT OR UPDATE ON public.product_definition_submission FOR EACH ROW EXECUTE FUNCTION public.product_definition_native_after();
CREATE CONSTRAINT TRIGGER product_definition_native_final AFTER INSERT OR UPDATE ON public.product_definition_submission DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION public.product_definition_native_complete();
GRANT INSERT (product_version_id,tenant_id,root_id,product_id,publication_id,version_number,recorded_at) ON public.product_definition_version_identity TO firefly_product_definition_commands;
GRANT SELECT ON public.product_definition_version_identity TO firefly_product_definition_commands;
CREATE POLICY product_native_select ON public.product_definition_version_identity FOR SELECT TO firefly_product_definition_commands USING (public.product_definition_command_scope_matches(tenant_id,root_id,'public.product_definition_version_identity'::REGCLASS,product_version_id,'SELECT'));
CREATE POLICY product_native_insert ON public.product_definition_version_identity FOR INSERT TO firefly_product_definition_commands WITH CHECK (public.product_definition_command_scope_matches(tenant_id,root_id,'public.product_definition_version_identity'::REGCLASS,product_version_id,'INSERT'));
CREATE TRIGGER a00_product_definition_native_guard BEFORE INSERT OR UPDATE OR DELETE ON public.product_definition_version_identity FOR EACH ROW EXECUTE FUNCTION public.product_definition_native_before();
CREATE TRIGGER z99_product_definition_native_witness AFTER INSERT OR UPDATE ON public.product_definition_version_identity FOR EACH ROW EXECUTE FUNCTION public.product_definition_native_after();
CREATE CONSTRAINT TRIGGER product_definition_native_final AFTER INSERT OR UPDATE ON public.product_definition_version_identity DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION public.product_definition_native_complete();
GRANT INSERT (product_version_id,product_id,version_number,version_description,effective_date,date_created,date_updated) ON public.product_version TO firefly_product_definition_commands;
GRANT SELECT (product_version_id) ON public.product_version TO firefly_product_definition_commands;
CREATE TRIGGER a00_product_definition_native_guard BEFORE INSERT OR UPDATE OR DELETE ON public.product_version FOR EACH ROW EXECUTE FUNCTION public.product_definition_native_before();
CREATE TRIGGER z99_product_definition_native_witness AFTER INSERT OR UPDATE ON public.product_version FOR EACH ROW EXECUTE FUNCTION public.product_definition_native_after();
CREATE CONSTRAINT TRIGGER product_definition_native_final AFTER INSERT OR UPDATE ON public.product_version DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION public.product_definition_native_complete();
CREATE CONSTRAINT TRIGGER product_definition_command_final AFTER INSERT OR UPDATE ON firefly_product_definition_private.native_command DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION public.product_definition_native_complete();
GRANT SELECT (product_version_id,tenant_id,root_id,product_id,publication_id) ON public.product_definition_version_identity TO firefly_product_definition_serving;
CREATE POLICY product_serving_identity ON public.product_definition_version_identity FOR SELECT TO firefly_product_definition_serving
 USING (public.product_definition_serving_context_matches(tenant_id,root_id) AND EXISTS (
  SELECT 1 FROM public.product_definition_publication p WHERE p.tenant_id=product_definition_version_identity.tenant_id
   AND p.root_id=product_definition_version_identity.root_id AND p.publication_id=product_definition_version_identity.publication_id
   AND p.product_version_id=product_definition_version_identity.product_version_id));
CREATE INDEX product_definition_revision_contributors ON public.product_definition_revision(tenant_id,root_id,actor_issuer,actor_id);
CREATE INDEX product_definition_cost_contributors ON public.product_definition_cost_revision(tenant_id,root_id,actor_issuer,actor_id);
CREATE INDEX product_definition_submission_contributors ON public.product_definition_submission(tenant_id,root_id,actor_issuer,actor_id);
DO $ownership$
DECLARE item RECORD;
BEGIN
 FOR item IN SELECT p.oid::REGPROCEDURE AS signature FROM pg_catalog.pg_proc p JOIN pg_catalog.pg_namespace n ON n.oid=p.pronamespace
  WHERE n.nspname='firefly_product_definition_private' OR (n.nspname='public' AND p.proname=ANY(ARRAY['begin_product_definition_command','assert_product_definition_command_live','finish_product_definition_command','open_product_definition_review_read','assert_product_definition_review_read_live','open_product_definition_cost_read','assert_product_definition_cost_read_live','open_product_definition_current_review_read','assert_product_definition_current_review_read_live','product_definition_command_runtime_allowed','product_definition_command_scope_matches','product_definition_private_read_matches','product_definition_native_before','product_definition_native_after','product_definition_native_complete','guard_product_definition_legacy_child'])) LOOP
  EXECUTE pg_catalog.format('ALTER FUNCTION %s OWNER TO firefly_product_definition_guard',item.signature);
  EXECUTE pg_catalog.format('REVOKE ALL ON FUNCTION %s FROM PUBLIC',item.signature);
 END LOOP;
END
$ownership$;
GRANT EXECUTE ON FUNCTION public.begin_product_definition_command(UUID,UUID,UUID,TEXT,TEXT,TEXT,BIGINT,BIGINT,TEXT,TEXT,TEXT,TEXT) TO firefly_product_definition_commands;
GRANT EXECUTE ON FUNCTION public.assert_product_definition_command_live(UUID) TO firefly_product_definition_commands;
GRANT EXECUTE ON FUNCTION public.finish_product_definition_command(UUID) TO firefly_product_definition_commands;
GRANT EXECUTE ON FUNCTION public.open_product_definition_review_read(UUID,UUID,UUID,TEXT,TEXT,BIGINT,BIGINT,TEXT) TO firefly_product_definition_commands;
GRANT EXECUTE ON FUNCTION public.assert_product_definition_review_read_live(UUID) TO firefly_product_definition_commands;
GRANT EXECUTE ON FUNCTION public.open_product_definition_cost_read(UUID,UUID,TEXT,TEXT,BIGINT,BIGINT,TEXT) TO firefly_product_definition_commands;
GRANT EXECUTE ON FUNCTION public.assert_product_definition_cost_read_live(UUID) TO firefly_product_definition_commands;
GRANT EXECUTE ON FUNCTION public.open_product_definition_current_review_read(UUID,UUID,TEXT,TEXT,BIGINT,BIGINT,TEXT) TO firefly_product_definition_commands;
GRANT EXECUTE ON FUNCTION public.assert_product_definition_current_review_read_live(UUID) TO firefly_product_definition_commands;
GRANT EXECUTE ON FUNCTION public.product_definition_command_runtime_allowed() TO firefly_product_definition_commands;
GRANT EXECUTE ON FUNCTION public.product_definition_command_scope_matches(UUID,UUID,OID,UUID,TEXT) TO firefly_product_definition_commands;
GRANT EXECUTE ON FUNCTION public.product_definition_private_read_matches(UUID,UUID,OID,UUID) TO firefly_product_definition_commands;
