-- Explicit optional activation after V20/V21 ownership preparation and staged V22/V23.
-- This admits a business-read preparation only; it cannot authorize a publication effect.
DO $prerequisites$
BEGIN
    IF pg_catalog.to_regprocedure('public.open_product_definition_deposit_enrollment_read(uuid,uuid,uuid,uuid,uuid,text,text,bigint,bigint)') IS NULL
       OR pg_catalog.to_regprocedure('public.product_definition_command_runtime_allowed()') IS NULL
       OR pg_catalog.to_regclass('firefly_product_definition_private.deposit_publication_preparation') IS NOT NULL
       OR pg_catalog.has_schema_privilege('firefly_product_definition_guard','public','CREATE') THEN
        RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_DEPOSIT_PREPARATION_PREREQUISITES_REQUIRED';
    END IF;
END
$prerequisites$;

CREATE TABLE firefly_product_definition_private.deposit_publication_preparation (
    context_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL, root_id UUID NOT NULL, operation_id UUID NOT NULL, review_id UUID NOT NULL,
    request TEXT NOT NULL, request_digest VARCHAR(64) NOT NULL CHECK (request_digest ~ '^[0-9a-f]{64}$'),
    native_transaction XID8 NOT NULL, backend_pid INTEGER NOT NULL CHECK (backend_pid>0), session_login NAME NOT NULL,
    actor_issuer VARCHAR(512) NOT NULL, actor_id VARCHAR(255) NOT NULL,
    actor_until_millis BIGINT NOT NULL, permit_until_millis BIGINT NOT NULL, request_until_millis BIGINT NOT NULL,
    observed_at_millis BIGINT NOT NULL, deadline_millis BIGINT NOT NULL,
    UNIQUE(native_transaction,backend_pid),
    CHECK (observed_at_millis>=0 AND deadline_millis>observed_at_millis
        AND deadline_millis<=observed_at_millis+30000 AND deadline_millis<=actor_until_millis
        AND deadline_millis<=permit_until_millis AND deadline_millis<=request_until_millis)
);
ALTER TABLE firefly_product_definition_private.deposit_publication_preparation OWNER TO firefly_product_definition_guard;
ALTER TABLE firefly_product_definition_private.deposit_publication_preparation ENABLE ROW LEVEL SECURITY;
ALTER TABLE firefly_product_definition_private.deposit_publication_preparation FORCE ROW LEVEL SECURITY;
REVOKE ALL ON firefly_product_definition_private.deposit_publication_preparation FROM PUBLIC;
CREATE POLICY preparation_guard ON firefly_product_definition_private.deposit_publication_preparation
    FOR ALL TO firefly_product_definition_guard USING (TRUE) WITH CHECK (TRUE);
CREATE TRIGGER preparation_immutable BEFORE UPDATE OR DELETE ON firefly_product_definition_private.deposit_publication_preparation
    FOR EACH ROW EXECUTE FUNCTION public.guard_product_definition_revision();

CREATE FUNCTION public.product_definition_deposit_publish_preparation_runtime_allowed()
RETURNS BOOLEAN LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE f RECORD; relation RECORD; count_value INTEGER:=0; columns TEXT[];
BEGIN
    IF NOT public.product_definition_command_runtime_allowed() THEN RETURN FALSE; END IF;
    SELECT c.relowner,c.relrowsecurity,c.relforcerowsecurity INTO relation FROM pg_catalog.pg_class c
        WHERE c.oid='firefly_product_definition_private.deposit_publication_preparation'::REGCLASS;
    IF NOT FOUND OR relation.relowner<>(SELECT oid FROM pg_catalog.pg_roles WHERE rolname='firefly_product_definition_guard')
       OR NOT relation.relrowsecurity OR NOT relation.relforcerowsecurity
       OR pg_catalog.has_table_privilege(session_user,'firefly_product_definition_private.deposit_publication_preparation','SELECT,INSERT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN')
       OR pg_catalog.has_any_column_privilege(session_user,'firefly_product_definition_private.deposit_publication_preparation','SELECT,INSERT,UPDATE,REFERENCES') THEN RETURN FALSE; END IF;
    SELECT c.relowner,c.relkind,c.reloptions INTO relation FROM pg_catalog.pg_class c
        WHERE c.oid='public.product_definition_deposit_preparation_cost_identity'::REGCLASS;
    SELECT array_agg(a.attname::TEXT ORDER BY a.attnum) INTO columns FROM pg_catalog.pg_attribute a
        WHERE a.attrelid='public.product_definition_deposit_preparation_cost_identity'::REGCLASS AND a.attnum>0 AND NOT a.attisdropped;
    IF NOT FOUND OR relation.relkind<>'v' OR relation.relowner<>(SELECT oid FROM pg_catalog.pg_roles WHERE rolname='firefly_product_definition_guard')
       OR NOT ('security_barrier=true'=ANY(COALESCE(relation.reloptions,ARRAY[]::TEXT[])))
       OR 'security_invoker=true'=ANY(COALESCE(relation.reloptions,ARRAY[]::TEXT[]))
       OR columns IS DISTINCT FROM ARRAY['tenant_id','root_id','cost_revision_id','cost_digest']::TEXT[]
       OR pg_catalog.has_table_privilege(session_user,'public.product_definition_deposit_preparation_cost_identity','INSERT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN') THEN RETURN FALSE; END IF;
    SELECT c.relowner,c.relkind,c.reloptions INTO relation FROM pg_catalog.pg_class c
        WHERE c.oid='public.product_definition_deposit_publication_prepared_snapshot'::REGCLASS;
    SELECT array_agg(a.attname::TEXT ORDER BY a.attnum) INTO columns FROM pg_catalog.pg_attribute a
        WHERE a.attrelid='public.product_definition_deposit_publication_prepared_snapshot'::REGCLASS AND a.attnum>0 AND NOT a.attisdropped;
    IF relation.relkind<>'v' OR relation.relowner<>(SELECT oid FROM pg_catalog.pg_roles WHERE rolname='firefly_product_definition_guard')
       OR NOT ('security_barrier=true'=ANY(COALESCE(relation.reloptions,ARRAY[]::TEXT[])))
       OR NOT ('security_invoker=true'=ANY(COALESCE(relation.reloptions,ARRAY[]::TEXT[])))
       OR columns IS DISTINCT FROM ARRAY['tenant_id','root_id','product_id','revision_id','public_digest','canonical_definition','encoding_version',
            'public_version','cost_revision_id','cost_digest','cost_version','review_id','submission_id','selection_fingerprint']::TEXT[]
       OR pg_catalog.has_table_privilege(session_user,'public.product_definition_deposit_publication_prepared_snapshot','INSERT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN') THEN RETURN FALSE; END IF;
    FOR f IN SELECT p.oid,p.proowner,p.prosecdef,p.proconfig,p.proacl,p.proname FROM pg_catalog.pg_proc p
        WHERE p.pronamespace='public'::REGNAMESPACE AND p.proname=ANY(ARRAY[
            'product_definition_deposit_publish_preparation_runtime_allowed','open_product_definition_deposit_publish_preparation',
            'product_definition_deposit_publish_preparation_matches','assert_product_definition_deposit_publish_preparation_live',
            'product_definition_deposit_publish_preparation_complete','product_definition_preparation_business_writes_allowed']) LOOP
        count_value:=count_value+1;
        IF NOT f.oid=ANY(ARRAY['public.product_definition_deposit_publish_preparation_runtime_allowed()'::REGPROCEDURE,
                'public.open_product_definition_deposit_publish_preparation(text,text,text,bigint,bigint,bigint)'::REGPROCEDURE,
                'public.product_definition_deposit_publish_preparation_matches(uuid,uuid,oid,uuid)'::REGPROCEDURE,
                'public.assert_product_definition_deposit_publish_preparation_live(uuid)'::REGPROCEDURE,
                'public.product_definition_deposit_publish_preparation_complete()'::REGPROCEDURE,
                'public.product_definition_preparation_business_writes_allowed()'::REGPROCEDURE])
           OR f.proowner<>(SELECT oid FROM pg_catalog.pg_roles WHERE rolname='firefly_product_definition_guard')
           OR NOT ('search_path=pg_catalog'=ANY(COALESCE(f.proconfig,ARRAY[]::TEXT[])))
           OR NOT f.prosecdef
           OR EXISTS (SELECT 1 FROM pg_catalog.aclexplode(COALESCE(f.proacl,pg_catalog.acldefault('f',f.proowner))) a
                WHERE a.grantee=0 AND a.privilege_type='EXECUTE') THEN RETURN FALSE; END IF;
    END LOOP;
    RETURN count_value=6;
EXCEPTION WHEN OTHERS THEN RETURN FALSE;
END
$$;

CREATE FUNCTION firefly_product_definition_private.current_deposit_publication_preparation(p_context UUID)
RETURNS firefly_product_definition_private.deposit_publication_preparation
LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE result firefly_product_definition_private.deposit_publication_preparation; millis BIGINT;
BEGIN
    IF NOT public.product_definition_deposit_publish_preparation_runtime_allowed()
       OR pg_catalog.current_setting('transaction_read_only')<>'off'
       OR pg_catalog.current_setting('transaction_isolation')<>'repeatable read'
       OR p_context IS NULL OR p_context::TEXT IS DISTINCT FROM pg_catalog.current_setting('firefly.product_definition.deposit_publish_preparation_id',TRUE) THEN
        RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_DEPOSIT_PREPARATION_UNAVAILABLE';
    END IF;
    SELECT * INTO result FROM firefly_product_definition_private.deposit_publication_preparation c
        WHERE c.context_id=p_context AND c.native_transaction=pg_catalog.pg_current_xact_id()
            AND c.backend_pid=pg_catalog.pg_backend_pid() AND c.session_login=session_user;
    IF NOT FOUND THEN RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_DEPOSIT_PREPARATION_UNAVAILABLE'; END IF;
    millis:=floor(extract(epoch FROM pg_catalog.clock_timestamp())*1000)::BIGINT;
    IF millis<result.observed_at_millis OR millis>=result.deadline_millis
       OR result.deadline_millis<>least(result.actor_until_millis,result.permit_until_millis,result.request_until_millis,result.observed_at_millis+30000)
       OR result.request_digest IS DISTINCT FROM firefly_product_definition_private.native_hash('deposit-publication-preparation-v1',result.request) THEN
        RAISE EXCEPTION USING ERRCODE='PD403', MESSAGE='CURRENT_PRODUCT_PERMISSION_REQUIRED';
    END IF;
    RETURN result;
END
$$;

CREATE FUNCTION public.open_product_definition_deposit_publish_preparation(
    p_request TEXT,p_issuer TEXT,p_actor TEXT,p_actor_until BIGINT,p_permit_until BIGINT,p_request_until BIGINT)
RETURNS TABLE(context_id UUID,observed_at_ms BIGINT,effective_until_ms BIGINT,request_digest TEXT)
LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE request_value JSONB; original JSONB; body JSONB; scope JSONB; resources JSONB; entry RECORD;
    tenant UUID; root UUID; operation UUID; review UUID; identifier UUID:=pg_catalog.gen_random_uuid(); ids UUID[]:=ARRAY[]::UUID[];
    value UUID; millis BIGINT; deadline BIGINT; digest TEXT;
BEGIN
    IF NOT public.product_definition_deposit_publish_preparation_runtime_allowed()
       OR pg_catalog.current_setting('transaction_read_only')<>'off'
       OR pg_catalog.current_setting('transaction_isolation')<>'repeatable read'
       OR COALESCE(pg_catalog.current_setting('firefly.product_definition.command_id',TRUE),'')<>''
       OR COALESCE(pg_catalog.current_setting('firefly.product_definition.private_read_id',TRUE),'')<>''
       OR COALESCE(pg_catalog.current_setting('firefly.product_definition.publication_read_id',TRUE),'')<>''
       OR COALESCE(pg_catalog.current_setting('firefly.product_definition.deposit_publish_preparation_id',TRUE),'')<>''
       OR p_request IS NULL OR pg_catalog.octet_length(p_request)>262144
       OR p_issuer IS NULL OR p_issuer !~ '^https?://' OR length(p_issuer)>512
       OR p_actor IS NULL OR p_actor ~ '^[[:space:]]*$' OR length(p_actor)>255
       OR p_actor_until IS NULL OR p_permit_until IS NULL OR p_request_until IS NULL
       OR p_permit_until>p_actor_until OR p_actor_until>253402300799999 OR p_request_until>253402300799999 THEN
        RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_DEPOSIT_PREPARATION_UNAVAILABLE';
    END IF;
    request_value:=p_request::JSONB;
    IF NOT firefly_product_definition_private.closed(request_value,ARRAY['encoding','canonicalIntent','intentDigest','scope','reviewId','expectedPublicVersion','expectedCostVersion','versionNumber','resources'])
       OR request_value->>'encoding' IS DISTINCT FROM 'deposit-publication-preparation-v1'
       OR p_request IS DISTINCT FROM firefly_product_definition_private.fixed_json(request_value)
       OR NOT firefly_product_definition_private.closed(request_value->'scope',ARRAY['tenantId','rootId','operationId'])
       OR NOT firefly_product_definition_private.closed(request_value->'resources',ARRAY['actionId','publicationId','productVersionId','historyId','auditId','publicEventId'])
       OR pg_catalog.jsonb_typeof(request_value->'canonicalIntent') IS DISTINCT FROM 'string' THEN
        RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_DEPOSIT_PREPARATION_REQUEST_REQUIRED';
    END IF;
    scope:=request_value->'scope';resources:=request_value->'resources';
    tenant:=firefly_product_definition_private.uuid_value(scope->'tenantId');root:=firefly_product_definition_private.uuid_value(scope->'rootId');
    operation:=firefly_product_definition_private.uuid_value(scope->'operationId');review:=firefly_product_definition_private.uuid_value(request_value->'reviewId');
    IF firefly_product_definition_private.integer_value(request_value->'expectedPublicVersion')>2147483647
       OR firefly_product_definition_private.integer_value(request_value->'expectedCostVersion')>2147483647 THEN
        RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_NATIVE_VERSION_REQUIRED';
    END IF;
    PERFORM firefly_product_definition_private.integer_value(request_value->'versionNumber',TRUE);
    FOR entry IN SELECT * FROM pg_catalog.jsonb_each(resources) LOOP
        value:=firefly_product_definition_private.uuid_value(entry.value);
        IF value=ANY(ids) THEN RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_NATIVE_RESOURCES_REQUIRED'; END IF;
        ids:=ids||value;
    END LOOP;
    original:=(request_value->>'canonicalIntent')::JSONB;body:=original->'request';
    IF NOT firefly_product_definition_private.closed(original,ARRAY['encoding','command','scope','request'])
       OR original->>'encoding' IS DISTINCT FROM 'product-definition-lifecycle-instruction-v1'
       OR original->>'command' IS DISTINCT FROM 'PUBLISH' OR original->'scope' IS DISTINCT FROM scope
       OR request_value->>'canonicalIntent' IS DISTINCT FROM firefly_product_definition_private.fixed_json(original)
       OR request_value->>'intentDigest' IS DISTINCT FROM firefly_product_definition_private.native_hash('product-definition-action-v1',request_value->>'canonicalIntent')
       OR NOT firefly_product_definition_private.closed(body,ARRAY['operationId','reviewId','expectedPublicVersion','expectedCostVersion','versionNumber'])
       OR body IS DISTINCT FROM pg_catalog.jsonb_build_object('operationId',operation,'reviewId',review,
            'expectedPublicVersion',request_value->'expectedPublicVersion','expectedCostVersion',request_value->'expectedCostVersion','versionNumber',request_value->'versionNumber') THEN
        RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_DEPOSIT_PREPARATION_REQUEST_REQUIRED';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM public.product_definition_root r JOIN public.product_definition_control c
            ON c.tenant_id=r.tenant_id AND c.root_id=r.root_id WHERE r.tenant_id=tenant AND r.root_id=root
            AND r.business_area='BANKING' AND r.profile_code='DEPOSIT_ACCOUNT_V1' AND r.profile_version=1
            AND r.phase='IN_REVIEW' AND r.active_review_id=review
            AND r.public_state_version=(request_value->>'expectedPublicVersion')::BIGINT
            AND c.cost_state_version=(request_value->>'expectedCostVersion')::BIGINT) THEN
        RAISE EXCEPTION USING ERRCODE='PD409', MESSAGE='PRODUCT_SELECTION_CONFLICT';
    END IF;
    millis:=floor(extract(epoch FROM pg_catalog.clock_timestamp())*1000)::BIGINT;
    deadline:=least(p_actor_until,p_permit_until,p_request_until,millis+30000);
    IF deadline<=millis THEN RAISE EXCEPTION USING ERRCODE='PD403', MESSAGE='CURRENT_PRODUCT_PERMISSION_REQUIRED'; END IF;
    digest:=firefly_product_definition_private.native_hash('deposit-publication-preparation-v1',p_request);
    INSERT INTO firefly_product_definition_private.deposit_publication_preparation(context_id,tenant_id,root_id,operation_id,review_id,
        request,request_digest,native_transaction,backend_pid,session_login,actor_issuer,actor_id,actor_until_millis,permit_until_millis,
        request_until_millis,observed_at_millis,deadline_millis)
    VALUES(identifier,tenant,root,operation,review,p_request,digest,pg_catalog.pg_current_xact_id(),pg_catalog.pg_backend_pid(),session_user,
        p_issuer,p_actor,p_actor_until,p_permit_until,p_request_until,millis,deadline);
    PERFORM pg_catalog.set_config('firefly.product_definition.deposit_publish_preparation_id',identifier::TEXT,TRUE);
    RETURN QUERY SELECT identifier,millis,deadline,digest;
END
$$;

CREATE FUNCTION public.product_definition_deposit_publish_preparation_matches(p_tenant UUID,p_root UUID,p_relation OID,p_key UUID)
RETURNS BOOLEAN LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE context firefly_product_definition_private.deposit_publication_preparation; relation_name TEXT;
BEGIN
    context:=firefly_product_definition_private.current_deposit_publication_preparation(
        pg_catalog.current_setting('firefly.product_definition.deposit_publish_preparation_id',TRUE)::UUID);
    IF context.tenant_id IS DISTINCT FROM p_tenant OR context.root_id IS DISTINCT FROM p_root THEN RETURN FALSE; END IF;
    SELECT c.relname INTO relation_name FROM pg_catalog.pg_class c
        WHERE c.oid=p_relation AND c.relnamespace='public'::REGNAMESPACE;
    IF relation_name=ANY(ARRAY['product_definition_root','product_definition_control']) THEN RETURN p_key=p_root; END IF;
    IF relation_name=ANY(ARRAY['product_definition_review','product_definition_review_private','product_definition_review_decision']) THEN RETURN p_key=context.review_id; END IF;
    IF relation_name='product_definition_revision' THEN RETURN EXISTS (SELECT 1 FROM public.product_definition_root r
        WHERE r.tenant_id=p_tenant AND r.root_id=p_root AND r.public_head=p_key); END IF;
    IF relation_name='product_definition_cost_revision' THEN RETURN EXISTS (SELECT 1 FROM public.product_definition_control c
        WHERE c.tenant_id=p_tenant AND c.root_id=p_root AND c.cost_head=p_key); END IF;
    IF relation_name='product_definition_submission' THEN RETURN EXISTS (SELECT 1 FROM public.product_definition_review_decision d
        WHERE d.tenant_id=p_tenant AND d.root_id=p_root AND d.review_id=context.review_id AND d.submission_id=p_key); END IF;
    RETURN FALSE;
EXCEPTION WHEN OTHERS THEN RETURN FALSE;
END
$$;

CREATE FUNCTION public.assert_product_definition_deposit_publish_preparation_live(p_context UUID)
RETURNS BOOLEAN LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE context firefly_product_definition_private.deposit_publication_preparation;
BEGIN
    context:=firefly_product_definition_private.current_deposit_publication_preparation(p_context);
    RETURN TRUE;
END
$$;

CREATE FUNCTION public.product_definition_deposit_publish_preparation_complete()
RETURNS TRIGGER LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
BEGIN
    PERFORM firefly_product_definition_private.current_deposit_publication_preparation(NEW.context_id);
    RETURN NEW;
END
$$;
-- This deferred check follows the owned client flow. A session can force it early with
-- SET CONSTRAINTS ... IMMEDIATE; adversarial raw-session commit expiry remains unqualified.
CREATE CONSTRAINT TRIGGER preparation_finite_commit AFTER INSERT ON firefly_product_definition_private.deposit_publication_preparation
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION public.product_definition_deposit_publish_preparation_complete();

-- The physical transaction writes native metadata only. A caller cannot start an ordinary
-- command in that transaction to turn this preparation context into business-write authority.
CREATE FUNCTION public.product_definition_preparation_business_writes_allowed()
RETURNS BOOLEAN LANGUAGE SQL VOLATILE SECURITY DEFINER SET search_path=pg_catalog AS $$
    SELECT NOT EXISTS (SELECT 1 FROM firefly_product_definition_private.deposit_publication_preparation c
        WHERE c.native_transaction=pg_catalog.pg_current_xact_id() AND c.backend_pid=pg_catalog.pg_backend_pid()
            AND c.session_login=session_user)
$$;

-- The caller already has legacy cost column privileges. Do not add a preparation policy
-- on the raw cost relation: this view exposes identity/digest without canonical_cost.
CREATE VIEW public.product_definition_deposit_preparation_cost_identity WITH (security_barrier=true) AS
    SELECT c.tenant_id,c.root_id,c.cost_revision_id,c.cost_digest FROM public.product_definition_cost_revision c
    WHERE public.product_definition_deposit_publish_preparation_matches(
        c.tenant_id,c.root_id,'public.product_definition_cost_revision'::REGCLASS,c.cost_revision_id);

CREATE VIEW public.product_definition_deposit_publication_prepared_snapshot WITH (security_barrier=true,security_invoker=true) AS
            SELECT r.tenant_id,r.root_id,r.product_id,v.revision_id,v.public_digest,v.canonical_definition,v.encoding_version,
                   r.public_state_version AS public_version,c.cost_head AS cost_revision_id,k.cost_digest,c.cost_state_version AS cost_version,
                   a.review_id,s.submission_id,p.selection_fingerprint
              FROM public.product_definition_root r
              JOIN public.product_definition_revision v ON v.tenant_id=r.tenant_id AND v.root_id=r.root_id AND v.revision_id=r.public_head
              JOIN public.product_definition_control c ON c.tenant_id=r.tenant_id AND c.root_id=r.root_id
              JOIN public.product_definition_deposit_preparation_cost_identity k ON k.tenant_id=c.tenant_id AND k.root_id=c.root_id AND k.cost_revision_id=c.cost_head
              JOIN public.product_definition_review a ON a.tenant_id=r.tenant_id AND a.root_id=r.root_id AND a.review_id=r.active_review_id
                   AND a.revision_id=v.revision_id AND a.public_digest=v.public_digest
              JOIN public.product_definition_review_private p ON p.tenant_id=a.tenant_id AND p.root_id=a.root_id AND p.review_id=a.review_id AND p.cost_revision_id=c.cost_head
              JOIN public.product_definition_review_decision d ON d.tenant_id=a.tenant_id AND d.root_id=a.root_id AND d.review_id=a.review_id
                   AND d.state='APPROVED' AND d.public_version=r.public_state_version AND d.cost_version=c.cost_state_version
                   AND d.actor_issuer=a.actor_issuer AND d.actor_id=a.actor_id AND d.created_at=a.created_at
              JOIN public.product_definition_submission s ON s.tenant_id=d.tenant_id AND s.root_id=d.root_id AND s.submission_id=d.submission_id
                   AND s.revision_id=v.revision_id AND s.public_digest=v.public_digest AND s.public_version=r.public_state_version
             WHERE r.phase='IN_REVIEW' AND r.business_area='BANKING' AND r.profile_code='DEPOSIT_ACCOUNT_V1' AND r.profile_version=1;

DO $ownership$
DECLARE signature REGPROCEDURE;
BEGIN
    GRANT CREATE ON SCHEMA public TO firefly_product_definition_guard;
    FOREACH signature IN ARRAY ARRAY['public.product_definition_deposit_publish_preparation_runtime_allowed()'::REGPROCEDURE,
        'public.open_product_definition_deposit_publish_preparation(text,text,text,bigint,bigint,bigint)'::REGPROCEDURE,
        'public.product_definition_deposit_publish_preparation_matches(uuid,uuid,oid,uuid)'::REGPROCEDURE,
        'public.assert_product_definition_deposit_publish_preparation_live(uuid)'::REGPROCEDURE,
        'public.product_definition_deposit_publish_preparation_complete()'::REGPROCEDURE,
        'public.product_definition_preparation_business_writes_allowed()'::REGPROCEDURE] LOOP
        EXECUTE pg_catalog.format('ALTER FUNCTION %s OWNER TO firefly_product_definition_guard',signature);
        EXECUTE pg_catalog.format('REVOKE ALL ON FUNCTION %s FROM PUBLIC',signature);
    END LOOP;
    REVOKE CREATE ON SCHEMA public FROM firefly_product_definition_guard;
    ALTER FUNCTION firefly_product_definition_private.current_deposit_publication_preparation(UUID) OWNER TO firefly_product_definition_guard;
    REVOKE ALL ON FUNCTION firefly_product_definition_private.current_deposit_publication_preparation(UUID) FROM PUBLIC;
END
$ownership$;
DO $view_ownership$
BEGIN
    GRANT CREATE ON SCHEMA public TO firefly_product_definition_guard;
    ALTER VIEW public.product_definition_deposit_preparation_cost_identity OWNER TO firefly_product_definition_guard;
    ALTER VIEW public.product_definition_deposit_publication_prepared_snapshot OWNER TO firefly_product_definition_guard;
    REVOKE CREATE ON SCHEMA public FROM firefly_product_definition_guard;
END
$view_ownership$;
REVOKE ALL ON public.product_definition_deposit_preparation_cost_identity FROM PUBLIC;
GRANT SELECT ON public.product_definition_deposit_preparation_cost_identity TO firefly_product_definition_commands;
REVOKE ALL ON public.product_definition_deposit_publication_prepared_snapshot FROM PUBLIC;
GRANT SELECT ON public.product_definition_deposit_publication_prepared_snapshot TO firefly_product_definition_commands;
GRANT EXECUTE ON FUNCTION public.product_definition_deposit_publish_preparation_runtime_allowed() TO firefly_product_definition_commands;
GRANT EXECUTE ON FUNCTION public.open_product_definition_deposit_publish_preparation(TEXT,TEXT,TEXT,BIGINT,BIGINT,BIGINT) TO firefly_product_definition_commands;
GRANT EXECUTE ON FUNCTION public.product_definition_deposit_publish_preparation_matches(UUID,UUID,OID,UUID) TO firefly_product_definition_commands;
GRANT EXECUTE ON FUNCTION public.assert_product_definition_deposit_publish_preparation_live(UUID) TO firefly_product_definition_commands;
GRANT EXECUTE ON FUNCTION public.product_definition_preparation_business_writes_allowed() TO firefly_product_definition_commands;

DO $business_read_only$
DECLARE relation RECORD;
BEGIN
    FOR relation IN SELECT relation_name FROM firefly_product_definition_private.relation_contract LOOP
        EXECUTE pg_catalog.format('CREATE POLICY deposit_preparation_no_insert ON public.%I AS RESTRICTIVE FOR INSERT TO firefly_product_definition_commands WITH CHECK (public.product_definition_preparation_business_writes_allowed())',relation.relation_name);
        EXECUTE pg_catalog.format('CREATE POLICY deposit_preparation_no_update ON public.%I AS RESTRICTIVE FOR UPDATE TO firefly_product_definition_commands USING (public.product_definition_preparation_business_writes_allowed()) WITH CHECK (public.product_definition_preparation_business_writes_allowed())',relation.relation_name);
        EXECUTE pg_catalog.format('CREATE POLICY deposit_preparation_no_delete ON public.%I AS RESTRICTIVE FOR DELETE TO firefly_product_definition_commands USING (public.product_definition_preparation_business_writes_allowed())',relation.relation_name);
    END LOOP;
END
$business_read_only$;

CREATE POLICY deposit_publish_preparation_select ON public.product_definition_root FOR SELECT TO firefly_product_definition_commands
    USING (public.product_definition_deposit_publish_preparation_matches(tenant_id,root_id,'public.product_definition_root'::REGCLASS,root_id)
        AND business_area='BANKING' AND profile_code='DEPOSIT_ACCOUNT_V1' AND profile_version=1 AND phase='IN_REVIEW');
CREATE POLICY deposit_publish_preparation_select ON public.product_definition_control FOR SELECT TO firefly_product_definition_commands
    USING (public.product_definition_deposit_publish_preparation_matches(tenant_id,root_id,'public.product_definition_control'::REGCLASS,root_id));
CREATE POLICY deposit_publish_preparation_select ON public.product_definition_revision FOR SELECT TO firefly_product_definition_commands
    USING (public.product_definition_deposit_publish_preparation_matches(tenant_id,root_id,'public.product_definition_revision'::REGCLASS,revision_id));
CREATE POLICY deposit_publish_preparation_select ON public.product_definition_review FOR SELECT TO firefly_product_definition_commands
    USING (public.product_definition_deposit_publish_preparation_matches(tenant_id,root_id,'public.product_definition_review'::REGCLASS,review_id));
CREATE POLICY deposit_publish_preparation_select ON public.product_definition_review_private FOR SELECT TO firefly_product_definition_commands
    USING (public.product_definition_deposit_publish_preparation_matches(tenant_id,root_id,'public.product_definition_review_private'::REGCLASS,review_id));
CREATE POLICY deposit_publish_preparation_select ON public.product_definition_review_decision FOR SELECT TO firefly_product_definition_commands
    USING (public.product_definition_deposit_publish_preparation_matches(tenant_id,root_id,'public.product_definition_review_decision'::REGCLASS,review_id));
CREATE POLICY deposit_publish_preparation_select ON public.product_definition_submission FOR SELECT TO firefly_product_definition_commands
    USING (public.product_definition_deposit_publish_preparation_matches(tenant_id,root_id,'public.product_definition_submission'::REGCLASS,submission_id));
