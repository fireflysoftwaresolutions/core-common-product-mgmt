-- Staged contract only. V1-V24 and default Flyway discovery are unchanged.
-- Activation is deliberately refused pending actual native/finality qualification.
-- These checks protect the running command, not historical committed publication visibility.
-- Deferred checks do not guarantee after-all-waits physical rollback; raw SAVEPOINT
-- rollback can erase transactional metadata. Neither criterion is closed by this script.
DO $prerequisites$
BEGIN
    IF pg_catalog.to_regclass('firefly_product_definition_private.deposit_publication_preparation') IS NULL
       OR pg_catalog.to_regclass('firefly_product_definition_private.deposit_publication_admission') IS NOT NULL
       OR pg_catalog.to_regprocedure('public.product_definition_command_admission(uuid)') IS NULL
       OR pg_catalog.has_schema_privilege('firefly_product_definition_guard','public','CREATE') THEN
        RAISE EXCEPTION USING ERRCODE='PD503',MESSAGE='PRODUCT_DEPOSIT_PUBLICATION_PREREQUISITES_REQUIRED';
    END IF;
END
$prerequisites$;

CREATE TABLE firefly_product_definition_private.deposit_publication_admission (
    admission_id UUID PRIMARY KEY,
    preparation_context_id UUID NOT NULL UNIQUE REFERENCES firefly_product_definition_private.deposit_publication_preparation(context_id),
    tenant_id UUID NOT NULL,root_id UUID NOT NULL,operation_id UUID NOT NULL,
    native_transaction XID8 NOT NULL,backend_pid INTEGER NOT NULL CHECK(backend_pid>0),session_login NAME NOT NULL,
    actor_issuer TEXT NOT NULL,actor_id TEXT NOT NULL,actor_until_millis BIGINT NOT NULL,permit_until_millis BIGINT NOT NULL,
    original_actor_until_millis BIGINT NOT NULL,original_permit_until_millis BIGINT NOT NULL,request_until_millis BIGINT NOT NULL,
    preparation_until_millis BIGINT NOT NULL,owner_effective_until_millis BIGINT NOT NULL,
    evidence TEXT NOT NULL,evidence_digest VARCHAR(64) NOT NULL,original_intent TEXT NOT NULL,intent_digest VARCHAR(64) NOT NULL,
    prepared_plan TEXT NOT NULL,plan_digest VARCHAR(64) NOT NULL,
    UNIQUE(native_transaction,backend_pid),
    CHECK(owner_effective_until_millis<=preparation_until_millis
        AND owner_effective_until_millis<=request_until_millis
        AND owner_effective_until_millis<=original_actor_until_millis
        AND owner_effective_until_millis<=original_permit_until_millis
        AND owner_effective_until_millis<=actor_until_millis
        AND owner_effective_until_millis<=permit_until_millis)
);
CREATE TABLE firefly_product_definition_private.deposit_publication_command_binding (
    command_id UUID PRIMARY KEY REFERENCES firefly_product_definition_private.native_command(command_id),
    admission_id UUID NOT NULL UNIQUE REFERENCES firefly_product_definition_private.deposit_publication_admission(admission_id)
);
ALTER TABLE firefly_product_definition_private.deposit_publication_admission OWNER TO firefly_product_definition_guard;
ALTER TABLE firefly_product_definition_private.deposit_publication_command_binding OWNER TO firefly_product_definition_guard;
ALTER TABLE firefly_product_definition_private.deposit_publication_admission ENABLE ROW LEVEL SECURITY;
ALTER TABLE firefly_product_definition_private.deposit_publication_admission FORCE ROW LEVEL SECURITY;
ALTER TABLE firefly_product_definition_private.deposit_publication_command_binding ENABLE ROW LEVEL SECURITY;
ALTER TABLE firefly_product_definition_private.deposit_publication_command_binding FORCE ROW LEVEL SECURITY;
REVOKE ALL ON firefly_product_definition_private.deposit_publication_admission,firefly_product_definition_private.deposit_publication_command_binding FROM PUBLIC;
CREATE POLICY publication_admission_guard ON firefly_product_definition_private.deposit_publication_admission
    FOR ALL TO firefly_product_definition_guard USING(TRUE) WITH CHECK(TRUE);
CREATE POLICY publication_binding_guard ON firefly_product_definition_private.deposit_publication_command_binding
    FOR ALL TO firefly_product_definition_guard USING(TRUE) WITH CHECK(TRUE);
CREATE TRIGGER publication_admission_immutable BEFORE UPDATE OR DELETE ON firefly_product_definition_private.deposit_publication_admission
    FOR EACH ROW EXECUTE FUNCTION public.guard_product_definition_revision();
CREATE TRIGGER publication_binding_immutable BEFORE UPDATE OR DELETE ON firefly_product_definition_private.deposit_publication_command_binding
    FOR EACH ROW EXECUTE FUNCTION public.guard_product_definition_revision();

CREATE FUNCTION firefly_product_definition_private.deposit_publication_activation_allowed()
RETURNS BOOLEAN LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path=pg_catalog AS $$
BEGIN
    -- Replacing this fence is a separate source/deployment/native/finality review, never a fixture overlay.
    RETURN FALSE;
END
$$;

CREATE FUNCTION firefly_product_definition_private.pending_deposit_publication(
    p_tenant UUID,p_root UUID,p_operation UUID,p_issuer TEXT,p_actor TEXT,p_actor_until BIGINT,p_permit_until BIGINT,
    p_original TEXT,p_digest TEXT,p_plan TEXT)
RETURNS firefly_product_definition_private.deposit_publication_admission
LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE result firefly_product_definition_private.deposit_publication_admission; millis BIGINT;
BEGIN
    IF NOT firefly_product_definition_private.deposit_publication_activation_allowed()
       OR NOT public.product_definition_deposit_publish_preparation_runtime_allowed()
       OR pg_catalog.current_setting('transaction_read_only')<>'off'
       OR pg_catalog.current_setting('transaction_isolation')<>'repeatable read' THEN
        RAISE EXCEPTION USING ERRCODE='PD503',MESSAGE='PRODUCT_DEPOSIT_PUBLICATION_UNAVAILABLE';
    END IF;
    SELECT * INTO result FROM firefly_product_definition_private.deposit_publication_admission a
        WHERE a.native_transaction=pg_catalog.pg_current_xact_id() AND a.backend_pid=pg_catalog.pg_backend_pid() AND a.session_login=session_user;
    IF NOT FOUND OR result.tenant_id IS DISTINCT FROM p_tenant OR result.root_id IS DISTINCT FROM p_root
       OR result.operation_id IS DISTINCT FROM p_operation OR result.actor_issuer IS DISTINCT FROM p_issuer OR result.actor_id IS DISTINCT FROM p_actor
       OR result.actor_until_millis IS DISTINCT FROM p_actor_until OR result.permit_until_millis IS DISTINCT FROM p_permit_until
       OR result.original_intent IS DISTINCT FROM p_original OR result.intent_digest IS DISTINCT FROM p_digest
       OR result.prepared_plan IS DISTINCT FROM p_plan
       OR result.evidence_digest IS DISTINCT FROM firefly_product_definition_private.native_hash('deposit-publication-admission-v1',result.evidence)
       OR result.intent_digest IS DISTINCT FROM firefly_product_definition_private.native_hash('product-definition-action-v1',result.original_intent)
       OR result.plan_digest IS DISTINCT FROM firefly_product_definition_private.native_hash('product-definition-native-plan-v1',result.prepared_plan) THEN
        RAISE EXCEPTION USING ERRCODE='PD503',MESSAGE='PRODUCT_DEPOSIT_PUBLICATION_BINDING_REQUIRED';
    END IF;
    millis:=floor(extract(epoch FROM pg_catalog.clock_timestamp())*1000)::BIGINT;
    IF millis>=result.owner_effective_until_millis OR result.owner_effective_until_millis>millis+5000 THEN
        RAISE EXCEPTION USING ERRCODE='PD403',MESSAGE='CURRENT_PRODUCT_PERMISSION_REQUIRED';
    END IF;
    RETURN result;
END
$$;

CREATE FUNCTION firefly_product_definition_private.deposit_publication_command_until(p_command firefly_product_definition_private.native_command)
RETURNS BIGINT LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE admission firefly_product_definition_private.deposit_publication_admission; binding UUID;
BEGIN
    IF p_command.command<>'PUBLISH' OR p_command.authority_profile<>'product-definition-deposit-staff-v1' THEN RETURN p_command.deadline_millis; END IF;
    SELECT b.admission_id INTO binding FROM firefly_product_definition_private.deposit_publication_command_binding b WHERE b.command_id=p_command.command_id;
    IF NOT FOUND OR p_command.mode<>'FRESH' THEN
        RAISE EXCEPTION USING ERRCODE='PD503',MESSAGE='PRODUCT_DEPOSIT_PUBLICATION_BINDING_REQUIRED';
    END IF;
    SELECT * INTO admission FROM firefly_product_definition_private.deposit_publication_admission a WHERE a.admission_id=binding;
    IF NOT FOUND THEN RAISE EXCEPTION USING ERRCODE='PD503',MESSAGE='PRODUCT_DEPOSIT_PUBLICATION_BINDING_REQUIRED'; END IF;
    admission:=firefly_product_definition_private.pending_deposit_publication(p_command.tenant_id,p_command.root_id,p_command.operation_id,
        p_command.actor_issuer,p_command.actor_id,p_command.actor_until_millis,p_command.permit_until_millis,
        admission.original_intent,admission.intent_digest,admission.prepared_plan);
    RETURN least(p_command.deadline_millis,admission.owner_effective_until_millis);
END
$$;

CREATE FUNCTION public.begin_product_definition_deposit_publication(
    p_tenant UUID,p_root UUID,p_operation UUID,p_preparation UUID,p_evidence TEXT,p_issuer TEXT,p_actor TEXT,
    p_actor_until BIGINT,p_permit_until BIGINT,p_original TEXT,p_digest TEXT,p_plan TEXT)
RETURNS TABLE(command_id UUID,replay BOOLEAN,command_recorded_at TIMESTAMPTZ,action_id UUID,retained_action_recorded_at TIMESTAMPTZ)
LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog SET TimeZone='UTC' AS $$
DECLARE preparation firefly_product_definition_private.deposit_publication_preparation;
    evidence JSONB; request_value JSONB; plan JSONB; selected JSONB; canonical TEXT; product UUID; review UUID; fingerprint TEXT;
    document JSONB; target JSONB; window_value JSONB; document_index INTEGER:=0; observed BIGINT; until_value BIGINT; native_until BIGINT;
    cap BIGINT; millis BIGINT; identifier UUID:=pg_catalog.gen_random_uuid(); result RECORD;
BEGIN
    IF NOT firefly_product_definition_private.deposit_publication_activation_allowed()
       OR NOT public.product_definition_deposit_publish_preparation_runtime_allowed()
       OR pg_catalog.current_setting('transaction_read_only')<>'off'
       OR pg_catalog.current_setting('transaction_isolation')<>'repeatable read'
       OR p_evidence IS NULL OR pg_catalog.octet_length(p_evidence)>524288
       OR p_actor_until IS NULL OR p_permit_until IS NULL OR p_permit_until>p_actor_until OR p_actor_until>253402300799999
       OR EXISTS(SELECT 1 FROM firefly_product_definition_private.native_command c WHERE c.native_transaction=pg_catalog.pg_current_xact_id() AND c.backend_pid=pg_catalog.pg_backend_pid()) THEN
        RAISE EXCEPTION USING ERRCODE='PD503',MESSAGE='PRODUCT_DEPOSIT_PUBLICATION_UNAVAILABLE';
    END IF;
    plan:=firefly_product_definition_private.validate_plan(p_tenant,p_root,p_operation,'PUBLISH',p_original,p_digest,p_plan);
    SELECT * INTO preparation FROM firefly_product_definition_private.deposit_publication_preparation p WHERE p.context_id=p_preparation;
    IF NOT FOUND OR preparation.tenant_id IS DISTINCT FROM p_tenant OR preparation.root_id IS DISTINCT FROM p_root
       OR preparation.operation_id IS DISTINCT FROM p_operation OR preparation.session_login IS DISTINCT FROM session_user
       OR preparation.native_transaction=pg_catalog.pg_current_xact_id()
       OR preparation.actor_issuer IS DISTINCT FROM p_issuer OR preparation.actor_id IS DISTINCT FROM p_actor
       OR preparation.request_digest IS DISTINCT FROM firefly_product_definition_private.native_hash('deposit-publication-preparation-v1',preparation.request) THEN
        RAISE EXCEPTION USING ERRCODE='PD503',MESSAGE='PRODUCT_DEPOSIT_PREPARATION_REQUIRED';
    END IF;
    request_value:=preparation.request::JSONB;evidence:=p_evidence::JSONB;
    IF p_evidence IS DISTINCT FROM firefly_product_definition_private.fixed_json(evidence)
       OR NOT firefly_product_definition_private.closed(evidence,ARRAY['encoding','scope','productId','selection','selectionFingerprint','reviewId','intentDigest','resources','nativePreparation','preparedObservedAtMillis','preparedEffectiveUntilMillis','effectiveUntilMillis','documents'])
       OR evidence->>'encoding' IS DISTINCT FROM 'deposit-publication-admission-v1'
       OR evidence->'scope' IS DISTINCT FROM request_value->'scope'
       OR evidence->'resources' IS DISTINCT FROM request_value->'resources'
       OR evidence->'reviewId' IS DISTINCT FROM request_value->'reviewId'
       OR evidence->>'intentDigest' IS DISTINCT FROM p_digest OR request_value->>'intentDigest' IS DISTINCT FROM p_digest
       OR request_value->>'canonicalIntent' IS DISTINCT FROM p_original
       OR plan->'preparedBody' IS DISTINCT FROM (p_original::JSONB)->'request'
       OR evidence->'nativePreparation' IS DISTINCT FROM pg_catalog.jsonb_build_object('contextId',preparation.context_id,'requestDigest',preparation.request_digest,
            'actorUntilMillis',preparation.actor_until_millis,'permitUntilMillis',preparation.permit_until_millis,'requestUntilMillis',preparation.request_until_millis)
       OR evidence->'resources' IS DISTINCT FROM (plan->'resources')-ARRAY['revisionId','costRevisionId','submissionId','reviewId','costEventId']
       OR firefly_product_definition_private.integer_value(evidence->'preparedObservedAtMillis',TRUE) IS DISTINCT FROM preparation.observed_at_millis
       OR firefly_product_definition_private.integer_value(evidence->'preparedEffectiveUntilMillis',TRUE)>preparation.deadline_millis
       OR firefly_product_definition_private.integer_value(evidence->'preparedEffectiveUntilMillis',TRUE)<=preparation.observed_at_millis THEN
        RAISE EXCEPTION USING ERRCODE='PD503',MESSAGE='PRODUCT_DEPOSIT_PUBLICATION_BINDING_REQUIRED';
    END IF;
    -- Keep the existing advisory/root/control lock order. Re-read the complete approved tuple
    -- after waits; plan_rows below retains its independent maker/checker/native row contract.
    PERFORM pg_catalog.pg_advisory_xact_lock(62140,pg_catalog.hashtext(p_tenant::TEXT||':'||p_root::TEXT));
    PERFORM r.root_id FROM public.product_definition_root r WHERE r.tenant_id=p_tenant AND r.root_id=p_root FOR UPDATE;
    PERFORM c.root_id FROM public.product_definition_control c WHERE c.tenant_id=p_tenant AND c.root_id=p_root FOR UPDATE;
    SELECT r.product_id,r.active_review_id,v.canonical_definition,p.selection_fingerprint,
        pg_catalog.jsonb_build_object('tenantId',r.tenant_id,'rootId',r.root_id,'submissionId',s.submission_id,'revisionId',v.revision_id,
            'publicDigest',v.public_digest,'publicVersion',r.public_state_version,'costRevisionId',k.cost_revision_id,'costDigest',k.cost_digest,'costVersion',c.cost_state_version)
        INTO product,review,canonical,fingerprint,selected
        FROM public.product_definition_root r
        JOIN public.product_definition_revision v ON v.tenant_id=r.tenant_id AND v.root_id=r.root_id AND v.revision_id=r.public_head
        JOIN public.product_definition_control c ON c.tenant_id=r.tenant_id AND c.root_id=r.root_id
        JOIN public.product_definition_cost_revision k ON k.tenant_id=c.tenant_id AND k.root_id=c.root_id AND k.cost_revision_id=c.cost_head
        JOIN public.product_definition_review a ON a.tenant_id=r.tenant_id AND a.root_id=r.root_id AND a.review_id=r.active_review_id AND a.revision_id=v.revision_id AND a.public_digest=v.public_digest
        JOIN public.product_definition_review_private p ON p.tenant_id=a.tenant_id AND p.root_id=a.root_id AND p.review_id=a.review_id AND p.cost_revision_id=k.cost_revision_id
        JOIN public.product_definition_review_decision d ON d.tenant_id=a.tenant_id AND d.root_id=a.root_id AND d.review_id=a.review_id AND d.state='APPROVED'
            AND d.public_version=r.public_state_version AND d.cost_version=c.cost_state_version AND d.actor_issuer=a.actor_issuer AND d.actor_id=a.actor_id AND d.created_at=a.created_at
        JOIN public.product_definition_submission s ON s.tenant_id=d.tenant_id AND s.root_id=d.root_id AND s.submission_id=d.submission_id
            AND s.revision_id=v.revision_id AND s.public_digest=v.public_digest AND s.public_version=r.public_state_version
        WHERE r.tenant_id=p_tenant AND r.root_id=p_root AND r.phase='IN_REVIEW' AND r.business_area='BANKING' AND r.profile_code='DEPOSIT_ACCOUNT_V1' AND r.profile_version=1;
    IF NOT FOUND OR evidence->'productId' IS DISTINCT FROM pg_catalog.to_jsonb(product)
       OR evidence->'reviewId' IS DISTINCT FROM pg_catalog.to_jsonb(review)
       OR evidence->'selection' IS DISTINCT FROM selected
       OR selected->'publicVersion' IS DISTINCT FROM request_value->'expectedPublicVersion'
       OR selected->'costVersion' IS DISTINCT FROM request_value->'expectedCostVersion'
       OR evidence->>'selectionFingerprint' IS DISTINCT FROM fingerprint
       OR fingerprint IS DISTINCT FROM firefly_product_definition_private.native_hash('product-definition-selection-v1',firefly_product_definition_private.fixed_json(selected)) THEN
        RAISE EXCEPTION USING ERRCODE='PD409',MESSAGE='PRODUCT_SELECTION_CONFLICT';
    END IF;
    IF pg_catalog.jsonb_typeof(evidence->'documents') IS DISTINCT FROM 'array'
       OR pg_catalog.jsonb_array_length(evidence->'documents')<>pg_catalog.jsonb_array_length(canonical::JSONB#>'{common,requirements,documents}')
       OR pg_catalog.jsonb_array_length(evidence->'documents') NOT BETWEEN 1 AND 50 THEN
        RAISE EXCEPTION USING ERRCODE='PD503',MESSAGE='PRODUCT_CURRENT_DOCUMENTS_REQUIRED';
    END IF;
    millis:=floor(extract(epoch FROM pg_catalog.clock_timestamp())*1000)::BIGINT;
    IF millis<preparation.observed_at_millis OR millis>=preparation.deadline_millis
       OR preparation.deadline_millis<>least(preparation.actor_until_millis,preparation.permit_until_millis,preparation.request_until_millis,preparation.observed_at_millis+30000) THEN
        RAISE EXCEPTION USING ERRCODE='PD403',MESSAGE='CURRENT_PRODUCT_PERMISSION_REQUIRED';
    END IF;
    cap:=least(preparation.deadline_millis,preparation.request_until_millis,preparation.actor_until_millis,preparation.permit_until_millis,
        p_actor_until,p_permit_until,(evidence->>'preparedEffectiveUntilMillis')::BIGINT);
    FOR window_value IN SELECT value FROM pg_catalog.jsonb_array_elements(evidence->'documents') LOOP
        document:=(canonical::JSONB#>'{common,requirements,documents}')->document_index;target:=window_value->'target';
        IF NOT firefly_product_definition_private.closed(window_value,ARRAY['target','observedAtMillis','nativeUntilMillis','effectiveUntilMillis','windowEvidence'])
           OR NOT firefly_product_definition_private.closed(target,ARRAY['scope','purpose','document'])
           OR target->>'purpose' IS DISTINCT FROM 'DEPOSIT_ACCOUNT_ENROLLMENT'
           OR target->'document' IS DISTINCT FROM document
           OR target->'scope' IS DISTINCT FROM pg_catalog.jsonb_build_object('tenantId',p_tenant,'rootId',p_root,'productId',product,
                'revisionId',selected->'revisionId','publicationId',request_value#>'{resources,publicationId}','definitionDigest',selected->'publicDigest') THEN
            RAISE EXCEPTION USING ERRCODE='PD503',MESSAGE='PRODUCT_CURRENT_DOCUMENTS_REQUIRED';
        END IF;
        observed:=firefly_product_definition_private.integer_value(window_value->'observedAtMillis',TRUE);
        until_value:=firefly_product_definition_private.integer_value(window_value->'effectiveUntilMillis',TRUE);
        IF observed>millis OR until_value<=millis OR until_value<=observed OR until_value>observed+5000 THEN
            RAISE EXCEPTION USING ERRCODE='PD403',MESSAGE='CURRENT_PRODUCT_DOCUMENTS_REQUIRED';
        END IF;
        IF window_value->>'windowEvidence'='EFFECTIVE_ONLY' THEN
            IF window_value->'nativeUntilMillis' IS DISTINCT FROM 'null'::JSONB THEN
                RAISE EXCEPTION USING ERRCODE='PD503',MESSAGE='PRODUCT_OWNER_WINDOW_PROVENANCE_REQUIRED';
            END IF;
        ELSIF window_value->>'windowEvidence'='NATIVE_AND_EFFECTIVE' THEN
            native_until:=firefly_product_definition_private.integer_value(window_value->'nativeUntilMillis',TRUE);
            IF native_until<until_value OR native_until>observed+5000 THEN
                RAISE EXCEPTION USING ERRCODE='PD503',MESSAGE='PRODUCT_OWNER_WINDOW_PROVENANCE_REQUIRED';
            END IF;
        ELSE RAISE EXCEPTION USING ERRCODE='PD503',MESSAGE='PRODUCT_OWNER_WINDOW_PROVENANCE_REQUIRED';
        END IF;
        cap:=least(cap,until_value);document_index:=document_index+1;
    END LOOP;
    until_value:=firefly_product_definition_private.integer_value(evidence->'effectiveUntilMillis',TRUE);
    IF until_value>cap OR until_value<=millis THEN RAISE EXCEPTION USING ERRCODE='PD403',MESSAGE='CURRENT_PRODUCT_PERMISSION_REQUIRED'; END IF;
    INSERT INTO firefly_product_definition_private.deposit_publication_admission(admission_id,preparation_context_id,tenant_id,root_id,operation_id,
        native_transaction,backend_pid,session_login,actor_issuer,actor_id,actor_until_millis,permit_until_millis,
        original_actor_until_millis,original_permit_until_millis,request_until_millis,preparation_until_millis,owner_effective_until_millis,
        evidence,evidence_digest,original_intent,intent_digest,prepared_plan,plan_digest)
    VALUES(identifier,p_preparation,p_tenant,p_root,p_operation,pg_catalog.pg_current_xact_id(),pg_catalog.pg_backend_pid(),session_user,
        p_issuer,p_actor,p_actor_until,p_permit_until,preparation.actor_until_millis,preparation.permit_until_millis,preparation.request_until_millis,
        preparation.deadline_millis,until_value,p_evidence,firefly_product_definition_private.native_hash('deposit-publication-admission-v1',p_evidence),
        p_original,p_digest,p_plan,firefly_product_definition_private.native_hash('product-definition-native-plan-v1',p_plan));
    SELECT * INTO result FROM public.begin_product_definition_command(p_tenant,p_root,p_operation,'PUBLISH',p_issuer,p_actor,p_actor_until,p_permit_until,
        'product-definition-deposit-staff-v1',p_original,p_digest,p_plan);
    IF NOT FOUND OR result.replay THEN RAISE EXCEPTION USING ERRCODE='PD503',MESSAGE='PRODUCT_DEPOSIT_HISTORICAL_RECOVERY_UNQUALIFIED'; END IF;
    RETURN QUERY SELECT result.command_id,result.replay,result.command_recorded_at,result.action_id,result.retained_action_recorded_at;
END
$$;

CREATE OR REPLACE FUNCTION public.begin_product_definition_command(
    p_tenant UUID,p_root UUID,p_operation UUID,p_command TEXT,p_issuer TEXT,p_actor TEXT,
    p_actor_until BIGINT,p_permit_until BIGINT,p_profile TEXT,p_original TEXT,p_digest TEXT,p_plan TEXT)
RETURNS TABLE(command_id UUID,replay BOOLEAN,command_recorded_at TIMESTAMPTZ,action_id UUID,retained_action_recorded_at TIMESTAMPTZ)
LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog SET TimeZone='UTC' AS $$
DECLARE bound_area TEXT; bound_profile TEXT; bound_version INTEGER; plan JSONB; claim firefly_product_definition_private.operation_claim; original_action public.product_definition_action;
    original_private public.product_definition_action_private; has_claim BOOLEAN; path UUID[]; category UUID; stamp TIMESTAMPTZ;
    millis BIGINT; deadline BIGINT; identifier UUID:=pg_catalog.gen_random_uuid(); expected JSONB; expected_action UUID;
BEGIN
    IF NOT firefly_product_definition_private.session_allowed() OR pg_catalog.current_setting('transaction_read_only')<>'off'
       OR p_profile IS NULL OR p_profile NOT IN ('product-definition-human-staff-v1','product-definition-term-pc-staff-v1','product-definition-deposit-staff-v1')
       OR p_issuer IS NULL OR length(p_issuer)=0 OR length(p_issuer)>512
       OR p_actor IS NULL OR length(p_actor)=0 OR length(p_actor)>255
       OR p_actor_until IS NULL OR p_permit_until IS NULL OR p_actor_until>253402300799999 OR p_permit_until>253402300799999 THEN
        RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_NATIVE_RUNTIME_UNAVAILABLE';
    END IF;
    IF p_command='PUBLISH' AND p_profile='product-definition-deposit-staff-v1' THEN
        PERFORM firefly_product_definition_private.pending_deposit_publication(p_tenant,p_root,p_operation,p_issuer,p_actor,p_actor_until,p_permit_until,p_original,p_digest,p_plan);
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
    IF NOT has_claim AND p_command='CREATE_DRAFT' THEN
        bound_area:=(plan#>>'{publicSnapshot,canonical}')::JSONB#>>'{common,businessArea}';
        bound_profile:=(plan#>>'{publicSnapshot,canonical}')::JSONB#>>'{common,profileCode}';
        bound_version:=((plan#>>'{publicSnapshot,canonical}')::JSONB#>>'{common,profileVersion}')::INTEGER;
    ELSE
        SELECT r.business_area,r.profile_code,r.profile_version INTO bound_area,bound_profile,bound_version
            FROM public.product_definition_root r WHERE r.tenant_id=p_tenant AND r.root_id=p_root;
    END IF;
    IF bound_version IS DISTINCT FROM 1 OR NOT COALESCE((p_profile='product-definition-human-staff-v1' AND bound_area='BANKING' AND bound_profile='ACCOUNT_TARIFF_V1')
        OR (p_profile='product-definition-term-pc-staff-v1' AND bound_area='INSURANCE' AND bound_profile='TERM_PC_INSURANCE_V1')
        OR (p_profile='product-definition-deposit-staff-v1' AND bound_area='BANKING' AND bound_profile='DEPOSIT_ACCOUNT_V1'),FALSE) THEN
        RAISE EXCEPTION USING ERRCODE='PD403', MESSAGE='PRODUCT_NATIVE_AUTHORITY_PROFILE_REQUIRED';
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
    IF p_command='PUBLISH' AND p_profile='product-definition-deposit-staff-v1' THEN
        IF has_claim THEN RAISE EXCEPTION USING ERRCODE='PD503',MESSAGE='PRODUCT_DEPOSIT_HISTORICAL_RECOVERY_UNQUALIFIED'; END IF;
        PERFORM firefly_product_definition_private.pending_deposit_publication(p_tenant,p_root,p_operation,p_issuer,p_actor,p_actor_until,p_permit_until,p_original,p_digest,p_plan);
    END IF;
    INSERT INTO firefly_product_definition_private.native_command(command_id,tenant_id,root_id,operation_id,command,mode,
        native_transaction,backend_pid,session_login,authority_profile,actor_issuer,actor_id,actor_until_millis,permit_until_millis,deadline_millis,
        recorded_at,action_id,retained_action_recorded_at,expected_rows,state)
    VALUES(identifier,p_tenant,p_root,p_operation,p_command,CASE WHEN has_claim THEN 'REPLAY' ELSE 'FRESH' END,
        pg_catalog.pg_current_xact_id(),pg_catalog.pg_backend_pid(),session_user,p_profile,p_issuer,p_actor,p_actor_until,p_permit_until,deadline,
        stamp,expected_action,CASE WHEN has_claim THEN original_action.created_at ELSE NULL END,expected,'OPEN');
    IF p_command='PUBLISH' AND p_profile='product-definition-deposit-staff-v1' THEN
        INSERT INTO firefly_product_definition_private.deposit_publication_command_binding(command_id,admission_id)
            SELECT identifier,a.admission_id FROM firefly_product_definition_private.deposit_publication_admission a
            WHERE a.native_transaction=pg_catalog.pg_current_xact_id() AND a.backend_pid=pg_catalog.pg_backend_pid() AND a.session_login=session_user;
        IF NOT FOUND THEN RAISE EXCEPTION USING ERRCODE='PD503',MESSAGE='PRODUCT_DEPOSIT_PUBLICATION_BINDING_REQUIRED'; END IF;
    END IF;
    PERFORM pg_catalog.set_config('firefly.product_definition.command_id',identifier::TEXT,TRUE);
    PERFORM pg_catalog.set_config('firefly.product_definition.purpose',CASE WHEN NOT has_claim AND p_command='CREATE_DRAFT' THEN 'CREATE' ELSE 'COMMAND' END,TRUE);
    PERFORM pg_catalog.set_config('firefly.product_definition.tenant_id',p_tenant::TEXT,TRUE);
    PERFORM pg_catalog.set_config('firefly.product_definition.root_id',p_root::TEXT,TRUE);
    PERFORM pg_catalog.set_config('firefly.product_definition.product_id',COALESCE(plan#>>'{preparedBody,productId}',''),TRUE);
    RETURN QUERY SELECT identifier,has_claim,stamp,expected_action,CASE WHEN has_claim THEN original_action.created_at ELSE NULL::TIMESTAMPTZ END;
END
$$;

CREATE OR REPLACE FUNCTION firefly_product_definition_private.current_command(allow_finished BOOLEAN DEFAULT FALSE)
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
    PERFORM firefly_product_definition_private.deposit_publication_command_until(result);
    RETURN result;
END
$$;

CREATE OR REPLACE FUNCTION firefly_product_definition_private.verify_complete(p_command UUID, require_finished BOOLEAN)
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
    PERFORM firefly_product_definition_private.deposit_publication_command_until(context);
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

CREATE OR REPLACE FUNCTION public.product_definition_command_admission(p_context UUID)
RETURNS TABLE(recorded_at TIMESTAMPTZ,db_now_ms BIGINT,admission_until_ms BIGINT)
LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path=pg_catalog AS $body$
DECLARE command firefly_product_definition_private.native_command; millis BIGINT;
BEGIN
    command:=firefly_product_definition_private.current_command(FALSE);
    millis:=floor(extract(epoch FROM command.recorded_at)*1000)::BIGINT;
    IF p_context IS NULL OR p_context IS DISTINCT FROM command.command_id
       OR command.deadline_millis<>least(command.actor_until_millis,command.permit_until_millis,millis+30000) THEN
        RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_NATIVE_CONTEXT_REQUIRED';
    END IF;
    RETURN QUERY SELECT command.recorded_at,millis,firefly_product_definition_private.deposit_publication_command_until(command);
END
$body$;
DO $ownership$
DECLARE signature REGPROCEDURE;
BEGIN
    GRANT CREATE ON SCHEMA public TO firefly_product_definition_guard;
    FOREACH signature IN ARRAY ARRAY[
        'firefly_product_definition_private.deposit_publication_activation_allowed()'::REGPROCEDURE,
        'firefly_product_definition_private.pending_deposit_publication(uuid,uuid,uuid,text,text,bigint,bigint,text,text,text)'::REGPROCEDURE,
        'firefly_product_definition_private.deposit_publication_command_until(firefly_product_definition_private.native_command)'::REGPROCEDURE,
        'public.begin_product_definition_deposit_publication(uuid,uuid,uuid,uuid,text,text,text,bigint,bigint,text,text,text)'::REGPROCEDURE
    ] LOOP
        EXECUTE pg_catalog.format('ALTER FUNCTION %s OWNER TO firefly_product_definition_guard',signature);
        EXECUTE pg_catalog.format('REVOKE ALL ON FUNCTION %s FROM PUBLIC',signature);
    END LOOP;
    REVOKE CREATE ON SCHEMA public FROM firefly_product_definition_guard;
END
$ownership$;
GRANT EXECUTE ON FUNCTION public.begin_product_definition_deposit_publication(UUID,UUID,UUID,UUID,TEXT,TEXT,TEXT,BIGINT,BIGINT,TEXT,TEXT,TEXT)
    TO firefly_product_definition_commands;
-- Existing ownership/ACLs for replaced functions remain exact; no serving/publication-read policy is changed.
