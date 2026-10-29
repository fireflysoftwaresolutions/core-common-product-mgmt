-- Additive source-only successor. Separate administrator role bootstrap and original V1-V21 are required.
-- Trusted backend context, not database JWT verification or applicant/financial admission.
DO $preflight$
DECLARE role_row RECORD; installer_row RECORD; metadata_owner_row RECORD;
BEGIN
    SELECT * INTO role_row FROM pg_catalog.pg_roles WHERE rolname='firefly_product_definition_insurance_quote_read';
    IF NOT FOUND OR role_row.rolcanlogin OR role_row.rolsuper OR role_row.rolbypassrls
       OR role_row.rolinherit OR role_row.rolcreatedb OR role_row.rolcreaterole OR role_row.rolreplication
       OR EXISTS (SELECT 1 FROM pg_catalog.pg_auth_members WHERE member=role_row.oid) THEN
        RAISE EXCEPTION USING ERRCODE='42501', MESSAGE='EXPLICIT_PRODUCT_INSURANCE_QUOTE_READ_ROLE_REQUIRED';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_catalog.pg_roles r WHERE r.rolname='firefly_product_definition_guard'
                   AND NOT r.rolcanlogin AND NOT r.rolsuper AND NOT r.rolbypassrls
                   AND NOT r.rolcreatedb AND NOT r.rolcreaterole AND NOT r.rolreplication
                   AND NOT EXISTS (SELECT 1 FROM pg_catalog.pg_auth_members WHERE member=r.oid)) THEN
        RAISE EXCEPTION USING ERRCODE='42501', MESSAGE='EXISTING_PRODUCT_GUARD_REQUIRED';
    END IF;
    SELECT * INTO metadata_owner_row FROM pg_catalog.pg_roles WHERE rolname='firefly_product_definition_metadata_owner';
    IF NOT FOUND OR metadata_owner_row.rolcanlogin OR metadata_owner_row.rolinherit OR metadata_owner_row.rolsuper
       OR metadata_owner_row.rolbypassrls OR metadata_owner_row.rolcreatedb OR metadata_owner_row.rolcreaterole OR metadata_owner_row.rolreplication
       OR EXISTS (SELECT 1 FROM pg_catalog.pg_auth_members WHERE member=metadata_owner_row.oid)
       OR pg_catalog.has_schema_privilege(metadata_owner_row.oid,'public','CREATE')
       OR pg_catalog.has_schema_privilege(metadata_owner_row.oid,'firefly_product_definition_private','USAGE,CREATE')
       OR pg_catalog.has_database_privilege(metadata_owner_row.oid,current_database(),'CREATE,TEMPORARY') THEN
        RAISE EXCEPTION USING ERRCODE='42501', MESSAGE='PREPARED_PROTECTED_METADATA_OWNER_REQUIRED';
    END IF;
    IF EXISTS (SELECT 1 FROM (VALUES
            ('product_definition_root','firefly_product_definition_metadata_owner'),
            ('product_definition_control','firefly_product_definition_metadata_owner'),
            ('product_definition_revision','firefly_product_definition_metadata_owner'),
            ('product_definition_review','firefly_product_definition_metadata_owner'),
            ('product_definition_review_private','firefly_product_definition_metadata_owner'),
            ('product_definition_review_decision','firefly_product_definition_guard'),
            ('product_definition_submission','firefly_product_definition_guard'),
            ('product_definition_history','firefly_product_definition_metadata_owner'),
            ('product_definition_action','firefly_product_definition_metadata_owner'),
            ('product_definition_publication','firefly_product_definition_metadata_owner'),
            ('product_definition_publication_control','firefly_product_definition_metadata_owner'),
            ('product_definition_version_identity','firefly_product_definition_guard')) expected(table_name,owner_name)
        LEFT JOIN pg_catalog.pg_class c ON c.relnamespace='public'::REGNAMESPACE AND c.relname=expected.table_name
        LEFT JOIN pg_catalog.pg_roles r ON r.rolname=expected.owner_name
        WHERE c.oid IS NULL OR r.oid IS NULL OR c.relkind<>'r' OR c.relowner<>r.oid OR NOT c.relrowsecurity OR NOT c.relforcerowsecurity) THEN
        RAISE EXCEPTION USING ERRCODE='42501', MESSAGE='EXACT_PREPARED_METADATA_OWNER_MAP_REQUIRED';
    END IF;
    SELECT * INTO installer_row FROM pg_catalog.pg_roles WHERE rolname=current_user;
    IF NOT FOUND OR current_user<>session_user OR (pg_catalog.current_setting('role',TRUE) IS DISTINCT FROM 'none'
       AND pg_catalog.current_setting('role',TRUE) IS DISTINCT FROM session_user::TEXT)
       OR NOT installer_row.rolcanlogin OR installer_row.rolsuper OR installer_row.rolbypassrls
       OR installer_row.rolcreatedb OR installer_row.rolcreaterole OR installer_row.rolreplication
       OR NOT pg_catalog.pg_has_role(current_user,'firefly_product_definition_guard','USAGE')
       OR NOT pg_catalog.pg_has_role(current_user,'firefly_product_definition_guard','SET')
       OR NOT pg_catalog.pg_has_role(current_user,'firefly_product_definition_metadata_owner','USAGE')
       OR pg_catalog.pg_has_role(current_user,'firefly_product_definition_metadata_owner','SET')
       OR NOT pg_catalog.has_schema_privilege(current_user,'public','CREATE WITH GRANT OPTION')
       OR pg_catalog.has_database_privilege(current_user,pg_catalog.current_database(),'CREATE,TEMPORARY')
       OR (SELECT count(*) FROM pg_catalog.pg_auth_members a WHERE a.member=installer_row.oid)<>2
       OR EXISTS (SELECT 1 FROM pg_catalog.pg_auth_members a WHERE a.member=installer_row.oid
                  AND (a.admin_option OR NOT a.inherit_option OR a.set_option IS DISTINCT FROM
                       (a.roleid=(SELECT oid FROM pg_catalog.pg_roles WHERE rolname='firefly_product_definition_guard'))))
       OR EXISTS (SELECT 1 FROM pg_catalog.pg_roles r
                  WHERE r.rolname NOT IN (current_user,'firefly_product_definition_guard','firefly_product_definition_metadata_owner')
                    AND pg_catalog.pg_has_role(current_user,r.oid,'MEMBER')) THEN
        RAISE EXCEPTION USING ERRCODE='42501', MESSAGE='SEPARATE_RESTRICTED_INSURANCE_QUOTE_INSTALLER_REQUIRED';
    END IF;
END
$preflight$;



DO $profile_constraint$
DECLARE item RECORD;
BEGIN
    SELECT c.* INTO item FROM pg_catalog.pg_constraint c
      WHERE c.conrelid='firefly_product_definition_private.native_command'::REGCLASS
        AND c.conname='native_command_authority_profile_check' AND c.contype='c' AND c.convalidated;
    IF NOT FOUND OR pg_catalog.regexp_replace(pg_catalog.pg_get_expr(item.conbin,item.conrelid),'[[:space:]()]','','g')
          IS DISTINCT FROM 'authority_profile::text=''product-definition-human-staff-v1''::text' THEN
        RAISE EXCEPTION USING ERRCODE='42501', MESSAGE='EXACT_ORIGINAL_PRODUCT_PROFILE_CONSTRAINT_REQUIRED';
    END IF;
    ALTER TABLE firefly_product_definition_private.native_command DROP CONSTRAINT native_command_authority_profile_check;
    ALTER TABLE firefly_product_definition_private.native_command ADD CONSTRAINT native_command_authority_profile_check
        CHECK (authority_profile IN ('product-definition-human-staff-v1','product-definition-term-pc-staff-v1'));
END
$profile_constraint$;

CREATE OR REPLACE FUNCTION firefly_product_definition_private.validate_plan(
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
            IF key_name='publicSnapshot' AND (canonical::JSONB#>>'{common,profileVersion}' IS DISTINCT FROM '1'
                OR NOT COALESCE(((canonical::JSONB#>>'{common,businessArea}'='BANKING' AND canonical::JSONB#>>'{common,profileCode}'='ACCOUNT_TARIFF_V1')
                    OR (canonical::JSONB#>>'{common,businessArea}'='INSURANCE' AND canonical::JSONB#>>'{common,profileCode}'='TERM_PC_INSURANCE_V1')),FALSE)) THEN
                RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_NATIVE_PROFILE_REQUIRED';
            END IF;
        ELSIF snapshot IS DISTINCT FROM 'null'::JSONB THEN
            RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_NATIVE_SNAPSHOT_REQUIRED';
        END IF;
    END LOOP;
    RETURN plan;
END
$$;

CREATE OR REPLACE FUNCTION firefly_product_definition_private.plan_rows(
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
            'product_code',(pub->>'canonical')::JSONB#>>'{common,code}','business_area',(pub->>'canonical')::JSONB#>>'{common,businessArea}','profile_code',(pub->>'canonical')::JSONB#>>'{common,profileCode}',
            'profile_version',1,'phase','DRAFT','public_state_version',0,'public_head',public_revision,'active_review_id',NULL,'created_at',p_stamp)));
        rows:=rows||jsonb_build_array(firefly_product_definition_private.expected_row('product_definition_control','INSERT',NULL,
            jsonb_build_object('root_id',root,'tenant_id',tenant,'cost_head',cost_reference,'cost_state_version',0)));
        selected:=jsonb_build_object('tenantId',tenant,'rootId',root,'revisionId',public_revision,'publicDigest',pub->>'digest','publicVersion',0,
            'costRevisionId',cost_reference,'costDigest',private_snapshot->>'digest','costVersion',0);
    ELSE
        SELECT * INTO header FROM public.product_definition_root r WHERE r.tenant_id=tenant AND r.root_id=root;
        IF NOT FOUND THEN RAISE EXCEPTION USING ERRCODE='PD404', MESSAGE='PRODUCT_DEFINITION_NOT_FOUND'; END IF;
        IF NOT ((header.business_area='BANKING' AND header.profile_code='ACCOUNT_TARIFF_V1')
                OR (header.business_area='INSURANCE' AND header.profile_code='TERM_PC_INSURANCE_V1')) OR header.profile_version<>1
           OR header.phase<>ALL(ARRAY['DRAFT','IN_REVIEW','PUBLISHED']) OR header.public_state_version<0
           OR NOT EXISTS (SELECT 1 FROM firefly_product_definition_private.product_binding b
                WHERE b.product_id=header.product_id AND b.tenant_id=tenant AND b.root_id=root) THEN
            RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_NATIVE_ROOT_UNAVAILABLE';
        END IF;
        IF pub IS DISTINCT FROM 'null'::JSONB AND ((pub->>'canonical')::JSONB#>>'{common,businessArea}' IS DISTINCT FROM header.business_area
                OR (pub->>'canonical')::JSONB#>>'{common,profileCode}' IS DISTINCT FROM header.profile_code
                OR (pub->>'canonical')::JSONB#>>'{common,profileVersion}' IS DISTINCT FROM header.profile_version::TEXT) THEN
            RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_NATIVE_PROFILE_MISMATCH';
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
       OR p_profile IS NULL OR p_profile NOT IN ('product-definition-human-staff-v1','product-definition-term-pc-staff-v1')
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
    IF NOT has_claim AND p_command='CREATE_DRAFT' THEN
        bound_area:=(plan#>>'{publicSnapshot,canonical}')::JSONB#>>'{common,businessArea}';
        bound_profile:=(plan#>>'{publicSnapshot,canonical}')::JSONB#>>'{common,profileCode}';
        bound_version:=((plan#>>'{publicSnapshot,canonical}')::JSONB#>>'{common,profileVersion}')::INTEGER;
    ELSE
        SELECT r.business_area,r.profile_code,r.profile_version INTO bound_area,bound_profile,bound_version
            FROM public.product_definition_root r WHERE r.tenant_id=p_tenant AND r.root_id=p_root;
    END IF;
    IF bound_version IS DISTINCT FROM 1 OR NOT COALESCE((p_profile='product-definition-human-staff-v1' AND bound_area='BANKING' AND bound_profile='ACCOUNT_TARIFF_V1')
        OR (p_profile='product-definition-term-pc-staff-v1' AND bound_area='INSURANCE' AND bound_profile='TERM_PC_INSURANCE_V1'),FALSE) THEN
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

CREATE OR REPLACE FUNCTION public.product_definition_private_read_matches(p_tenant UUID,p_root UUID,p_relation OID,p_key UUID)
RETURNS BOOLEAN LANGUAGE plpgsql VOLATILE SECURITY INVOKER SET search_path=pg_catalog AS $$
DECLARE kind TEXT; relation_name TEXT; until_text TEXT; until_millis BIGINT; now_millis BIGINT; submission_text TEXT;
BEGIN
    IF current_user<>session_user OR NOT public.product_definition_command_runtime_allowed()
       OR COALESCE(pg_catalog.current_setting('firefly.product_definition.private_read_id',TRUE),'') !~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
       OR pg_catalog.current_setting('transaction_read_only')<>'on'
       OR pg_catalog.current_setting('firefly.product_definition.private_read_tenant',TRUE) IS DISTINCT FROM p_tenant::TEXT
       OR pg_catalog.current_setting('firefly.product_definition.private_read_root',TRUE) IS DISTINCT FROM p_root::TEXT
       OR pg_catalog.current_setting('firefly.product_definition.private_read_xid',TRUE) IS DISTINCT FROM pg_catalog.pg_current_xact_id()::TEXT THEN RETURN FALSE; END IF;
    IF COALESCE(pg_catalog.current_setting('firefly.product_definition.private_read_profile',TRUE),'') NOT IN ('product-definition-human-staff-v1','product-definition-term-pc-staff-v1') THEN RETURN FALSE; END IF;
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

CREATE OR REPLACE FUNCTION public.open_product_definition_cost_read(p_tenant UUID,p_root UUID,p_issuer TEXT,p_actor TEXT,p_actor_until BIGINT,p_permit_until BIGINT,p_profile TEXT)
RETURNS TABLE(context_id UUID,recorded_at TIMESTAMPTZ)
LANGUAGE plpgsql VOLATILE SECURITY INVOKER SET search_path=pg_catalog AS $$
DECLARE stamp TIMESTAMPTZ; millis BIGINT; deadline BIGINT; identifier UUID:=pg_catalog.gen_random_uuid();
BEGIN
    IF current_user<>session_user OR NOT public.product_definition_command_runtime_allowed()
       OR pg_catalog.current_setting('transaction_read_only')<>'on' OR p_tenant IS NULL OR p_root IS NULL
       OR p_tenant='00000000-0000-0000-0000-000000000000' OR p_root='00000000-0000-0000-0000-000000000000'
       OR p_profile IS NULL OR p_profile NOT IN ('product-definition-human-staff-v1','product-definition-term-pc-staff-v1') OR p_actor IS NULL OR length(p_actor)=0 OR length(p_actor)>255
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
    PERFORM pg_catalog.set_config('firefly.product_definition.private_read_profile',p_profile,TRUE);
    PERFORM pg_catalog.set_config('firefly.product_definition.private_read_recorded_at',stamp::TEXT,TRUE);
    PERFORM pg_catalog.set_config('firefly.product_definition.private_read_recorded_ms',millis::TEXT,TRUE);
    PERFORM pg_catalog.set_config('firefly.product_definition.private_read_actor_until',p_actor_until::TEXT,TRUE);
    PERFORM pg_catalog.set_config('firefly.product_definition.private_read_permit_until',p_permit_until::TEXT,TRUE);
    IF NOT EXISTS (SELECT 1 FROM public.product_definition_root r WHERE r.tenant_id=p_tenant AND r.root_id=p_root) THEN
        RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_PRIVATE_READ_UNAVAILABLE';
    END IF;
    RETURN QUERY SELECT identifier,stamp;
END
$$;

CREATE OR REPLACE FUNCTION public.open_product_definition_publication_metadata(
    p_kind TEXT,p_tenant UUID,p_root UUID,p_issuer TEXT,p_actor TEXT,p_actor_until BIGINT,p_permit_until BIGINT,p_profile TEXT)
RETURNS TABLE(context_id UUID,recorded_at TIMESTAMPTZ,admission_valid_until TIMESTAMPTZ)
LANGUAGE plpgsql VOLATILE SECURITY INVOKER SET search_path=pg_catalog AS $body$
DECLARE stamp TIMESTAMPTZ; millis BIGINT; deadline BIGINT; identifier UUID := pg_catalog.gen_random_uuid();
BEGIN
    IF NOT public.product_definition_publication_read_runtime_allowed()
       OR pg_catalog.current_setting('transaction_read_only')<>'on'
       OR pg_catalog.current_setting('transaction_isolation')<>'repeatable read'
       OR p_kind IS NULL OR p_kind NOT IN ('PUBLICATION_READINESS','PUBLICATION_DISCOVERY')
       OR p_tenant IS NULL OR p_root IS NULL
       OR p_tenant='00000000-0000-0000-0000-000000000000'::UUID OR p_root='00000000-0000-0000-0000-000000000000'::UUID
       OR p_profile IS NULL OR p_profile NOT IN ('product-definition-human-staff-v1','product-definition-term-pc-staff-v1')
       OR p_issuer IS NULL OR p_issuer !~ '^https?://' OR length(p_issuer)>512
       OR p_actor IS NULL OR p_actor ~ '^[[:space:]]*$' OR length(p_actor)>255
       OR p_actor_until IS NULL OR p_permit_until IS NULL
       OR p_actor_until>253402300799999 OR p_permit_until>253402300799999
       OR COALESCE(pg_catalog.current_setting('firefly.product_definition.publication_read_id',TRUE),'')<>'' THEN
        RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_PUBLICATION_READ_UNAVAILABLE';
    END IF;
    stamp := pg_catalog.clock_timestamp();
    millis := floor(extract(epoch FROM stamp)*1000)::BIGINT;
    IF p_actor_until<=millis OR p_permit_until<=millis THEN
        RAISE EXCEPTION USING ERRCODE='PD403', MESSAGE='CURRENT_PRODUCT_PERMISSION_REQUIRED';
    END IF;
    deadline := least(p_actor_until,p_permit_until,millis+30000);
    PERFORM pg_catalog.set_config('firefly.product_definition.publication_read_kind',p_kind,TRUE);
    PERFORM pg_catalog.set_config('firefly.product_definition.publication_read_id',identifier::TEXT,TRUE);
    PERFORM pg_catalog.set_config('firefly.product_definition.publication_read_tenant',p_tenant::TEXT,TRUE);
    PERFORM pg_catalog.set_config('firefly.product_definition.publication_read_root',p_root::TEXT,TRUE);
    PERFORM pg_catalog.set_config('firefly.product_definition.publication_read_login',session_user::TEXT,TRUE);
    PERFORM pg_catalog.set_config('firefly.product_definition.publication_read_backend',pg_catalog.pg_backend_pid()::TEXT,TRUE);
    PERFORM pg_catalog.set_config('firefly.product_definition.publication_read_xid',pg_catalog.pg_current_xact_id()::TEXT,TRUE);
    PERFORM pg_catalog.set_config('firefly.product_definition.publication_read_issuer',p_issuer,TRUE);
    PERFORM pg_catalog.set_config('firefly.product_definition.publication_read_actor',p_actor,TRUE);
    PERFORM pg_catalog.set_config('firefly.product_definition.publication_read_profile',p_profile,TRUE);
    PERFORM pg_catalog.set_config('firefly.product_definition.publication_read_recorded',millis::TEXT,TRUE);
    PERFORM pg_catalog.set_config('firefly.product_definition.publication_read_actor_until',p_actor_until::TEXT,TRUE);
    PERFORM pg_catalog.set_config('firefly.product_definition.publication_read_permit_until',p_permit_until::TEXT,TRUE);
    PERFORM pg_catalog.set_config('firefly.product_definition.publication_read_until',deadline::TEXT,TRUE);
    RETURN QUERY SELECT identifier,stamp,pg_catalog.to_timestamp(deadline::NUMERIC / 1000);
END
$body$;

CREATE OR REPLACE FUNCTION public.product_definition_publication_read_matches(p_tenant UUID,p_root UUID,p_relation OID)
RETURNS BOOLEAN LANGUAGE plpgsql VOLATILE SECURITY INVOKER SET search_path=pg_catalog AS $body$
DECLARE
    kind TEXT; until_text TEXT; recorded_text TEXT; actor_text TEXT; permit_text TEXT;
    deadline BIGINT; recorded BIGINT; actor_until BIGINT; permit_until BIGINT; now_millis BIGINT;
BEGIN
    IF NOT public.product_definition_publication_read_runtime_allowed()
       OR pg_catalog.current_setting('transaction_read_only')<>'on'
       OR pg_catalog.current_setting('transaction_isolation')<>'repeatable read'
       OR COALESCE(pg_catalog.current_setting('firefly.product_definition.publication_read_id',TRUE),'') !~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
       OR pg_catalog.current_setting('firefly.product_definition.publication_read_tenant',TRUE) IS DISTINCT FROM p_tenant::TEXT
       OR pg_catalog.current_setting('firefly.product_definition.publication_read_root',TRUE) IS DISTINCT FROM p_root::TEXT
       OR pg_catalog.current_setting('firefly.product_definition.publication_read_login',TRUE) IS DISTINCT FROM session_user::TEXT
       OR pg_catalog.current_setting('firefly.product_definition.publication_read_backend',TRUE) IS DISTINCT FROM pg_catalog.pg_backend_pid()::TEXT
       OR pg_catalog.current_setting('firefly.product_definition.publication_read_xid',TRUE) IS DISTINCT FROM pg_catalog.pg_current_xact_id()::TEXT
       OR COALESCE(pg_catalog.current_setting('firefly.product_definition.publication_read_profile',TRUE),'') NOT IN ('product-definition-human-staff-v1','product-definition-term-pc-staff-v1')
       OR COALESCE(pg_catalog.current_setting('firefly.product_definition.publication_read_actor',TRUE),'')=''
       OR COALESCE(pg_catalog.current_setting('firefly.product_definition.publication_read_issuer',TRUE),'') !~ '^https?://' THEN RETURN FALSE; END IF;
    until_text := pg_catalog.current_setting('firefly.product_definition.publication_read_until',TRUE);
    recorded_text := pg_catalog.current_setting('firefly.product_definition.publication_read_recorded',TRUE);
    actor_text := pg_catalog.current_setting('firefly.product_definition.publication_read_actor_until',TRUE);
    permit_text := pg_catalog.current_setting('firefly.product_definition.publication_read_permit_until',TRUE);
    IF until_text IS NULL OR until_text !~ '^[0-9]{1,15}$' OR recorded_text IS NULL OR recorded_text !~ '^[0-9]{1,15}$'
       OR actor_text IS NULL OR actor_text !~ '^[0-9]{1,15}$' OR permit_text IS NULL OR permit_text !~ '^[0-9]{1,15}$' THEN RETURN FALSE; END IF;
    deadline := until_text::BIGINT; recorded := recorded_text::BIGINT;
    actor_until := actor_text::BIGINT; permit_until := permit_text::BIGINT;
    now_millis := floor(extract(epoch FROM pg_catalog.clock_timestamp())*1000)::BIGINT;
    IF recorded>now_millis OR deadline<=now_millis
       OR deadline<>least(actor_until,permit_until,recorded+30000)
       OR actor_until>253402300799999 OR permit_until>253402300799999 THEN RETURN FALSE; END IF;
    kind := pg_catalog.current_setting('firefly.product_definition.publication_read_kind',TRUE);
    IF kind='PUBLICATION_READINESS' THEN
        RETURN p_relation=ANY(ARRAY[
            'public.product_definition_root'::REGCLASS,'public.product_definition_control'::REGCLASS,
            'public.product_definition_revision'::REGCLASS,'public.product_definition_review'::REGCLASS,
            'public.product_definition_review_private'::REGCLASS,'public.product_definition_review_decision'::REGCLASS,
            'public.product_definition_submission'::REGCLASS]);
    ELSIF kind='PUBLICATION_DISCOVERY' THEN
        RETURN p_relation=ANY(ARRAY[
            'public.product_definition_root'::REGCLASS,'public.product_definition_review'::REGCLASS,
            'public.product_definition_review_private'::REGCLASS,'public.product_definition_review_decision'::REGCLASS,
            'public.product_definition_submission'::REGCLASS,'public.product_definition_history'::REGCLASS,
            'public.product_definition_action'::REGCLASS,'public.product_definition_publication'::REGCLASS,
            'public.product_definition_publication_control'::REGCLASS,'public.product_definition_version_identity'::REGCLASS]);
    END IF;
    RETURN FALSE;
EXCEPTION WHEN OTHERS THEN RETURN FALSE;
END
$body$;

CREATE FUNCTION public.product_definition_private_read_admission(p_context UUID)
RETURNS TABLE(recorded_at TIMESTAMPTZ,db_now_ms BIGINT,admission_until_millis BIGINT)
LANGUAGE plpgsql VOLATILE SECURITY INVOKER SET search_path=pg_catalog AS $body$
DECLARE kind TEXT; stamp TIMESTAMPTZ; millis BIGINT; deadline BIGINT; actor_until BIGINT; permit_until BIGINT;
BEGIN
    kind:=pg_catalog.current_setting('firefly.product_definition.private_read_kind',TRUE);
    IF kind='READ_COST' THEN PERFORM public.assert_product_definition_cost_read_live(p_context);
    ELSIF kind='READ_SELECTION' THEN PERFORM public.assert_product_definition_review_read_live(p_context);
    ELSIF kind='READ_SELECTION_CURRENT' THEN PERFORM public.assert_product_definition_current_review_read_live(p_context);
    ELSE RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_PRIVATE_READ_UNAVAILABLE'; END IF;
    stamp:=pg_catalog.current_setting('firefly.product_definition.private_read_recorded_at',TRUE)::TIMESTAMPTZ;
    millis:=pg_catalog.current_setting('firefly.product_definition.private_read_recorded_ms',TRUE)::BIGINT;
    deadline:=pg_catalog.current_setting('firefly.product_definition.private_read_until',TRUE)::BIGINT;
    actor_until:=pg_catalog.current_setting('firefly.product_definition.private_read_actor_until',TRUE)::BIGINT;
    permit_until:=pg_catalog.current_setting('firefly.product_definition.private_read_permit_until',TRUE)::BIGINT;
    IF stamp IS NULL OR millis IS NULL OR deadline IS NULL OR actor_until IS NULL OR permit_until IS NULL
       OR floor(extract(epoch FROM stamp)*1000)::BIGINT<>millis
       OR deadline<>least(actor_until,permit_until,millis+30000) THEN
        RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='PRODUCT_PRIVATE_READ_UNAVAILABLE';
    END IF;
    RETURN QUERY SELECT stamp,millis,deadline;
END
$body$;

CREATE FUNCTION public.product_definition_insurance_quote_runtime_allowed()
RETURNS BOOLEAN LANGUAGE plpgsql VOLATILE SECURITY INVOKER SET search_path=pg_catalog AS $body$
DECLARE
    login NAME := session_user;
    login_row RECORD;
    role_row RECORD;
    relation_row RECORD;
    attribute_row RECORD;
    function_row RECORD;
    allowed TEXT[];
    present TEXT[];
    relation_count INTEGER := 0; owner_count INTEGER := 0; expected_signature OID;
    function_count INTEGER := 0;
    expected_owner NAME;
    metadata_owner OID;
BEGIN
    SELECT * INTO login_row FROM pg_catalog.pg_roles WHERE rolname=login;
    IF current_user<>session_user OR pg_catalog.current_setting('role',TRUE) IS DISTINCT FROM 'none'
       OR NOT FOUND OR NOT login_row.rolcanlogin OR login_row.rolsuper OR login_row.rolbypassrls
       OR login_row.rolcreaterole OR login_row.rolcreatedb OR login_row.rolreplication
       OR NOT pg_catalog.pg_has_role(login,'firefly_product_definition_insurance_quote_read','USAGE')
       OR pg_catalog.has_database_privilege(login,pg_catalog.current_database(),'CREATE,TEMPORARY')
       OR pg_catalog.has_schema_privilege(login,'public','CREATE')
       OR pg_catalog.has_schema_privilege(login,'firefly_product_definition_private','USAGE,CREATE') THEN RETURN FALSE; END IF;
    SELECT * INTO role_row FROM pg_catalog.pg_roles WHERE rolname='firefly_product_definition_insurance_quote_read';
    IF NOT FOUND OR role_row.rolcanlogin OR role_row.rolsuper OR role_row.rolbypassrls
       OR role_row.rolinherit OR role_row.rolcreatedb OR role_row.rolcreaterole OR role_row.rolreplication
       OR EXISTS (SELECT 1 FROM pg_catalog.pg_auth_members WHERE member=role_row.oid)
       OR EXISTS (SELECT 1 FROM pg_catalog.pg_roles r
                  WHERE r.rolname NOT IN (login,'firefly_product_definition_insurance_quote_read')
                    AND pg_catalog.pg_has_role(login,r.oid,'MEMBER')) THEN RETURN FALSE; END IF;
    IF (SELECT count(*) FROM pg_catalog.pg_auth_members a WHERE a.member=login_row.oid)<>1
       OR EXISTS (SELECT 1 FROM pg_catalog.pg_auth_members a WHERE a.member=login_row.oid
            AND (a.roleid<>role_row.oid OR a.admin_option OR a.set_option OR NOT a.inherit_option)) THEN RETURN FALSE; END IF;
    IF EXISTS (SELECT 1 FROM pg_catalog.pg_namespace n WHERE n.nspowner IN (login_row.oid,role_row.oid) OR pg_catalog.has_schema_privilege(login,n.oid,'CREATE'))
       OR EXISTS (SELECT 1 FROM pg_catalog.pg_class c WHERE c.relowner IN (login_row.oid,role_row.oid))
       OR EXISTS (SELECT 1 FROM pg_catalog.pg_proc p WHERE p.proowner IN (login_row.oid,role_row.oid))
       OR EXISTS (SELECT 1 FROM pg_catalog.pg_type t WHERE t.typowner IN (login_row.oid,role_row.oid))
       OR EXISTS (SELECT 1 FROM pg_catalog.pg_database d WHERE d.datdba IN (login_row.oid,role_row.oid)) THEN RETURN FALSE; END IF;
    SELECT oid INTO metadata_owner FROM pg_catalog.pg_roles WHERE rolname='firefly_product_definition_metadata_owner'
        AND NOT rolcanlogin AND NOT rolinherit AND NOT rolsuper AND NOT rolbypassrls
        AND NOT rolcreatedb AND NOT rolcreaterole AND NOT rolreplication;
    IF metadata_owner IS NULL OR EXISTS (SELECT 1 FROM pg_catalog.pg_auth_members WHERE member=metadata_owner)
       OR pg_catalog.has_schema_privilege(metadata_owner,'public','CREATE')
       OR pg_catalog.has_schema_privilege(metadata_owner,'firefly_product_definition_private','USAGE,CREATE')
       OR pg_catalog.has_database_privilege(metadata_owner,current_database(),'CREATE,TEMPORARY')
       OR NOT EXISTS (SELECT 1 FROM pg_catalog.pg_roles r WHERE r.rolname='firefly_product_definition_guard'
            AND NOT r.rolcanlogin AND NOT r.rolsuper AND NOT r.rolbypassrls AND NOT r.rolcreatedb AND NOT r.rolcreaterole AND NOT r.rolreplication
            AND NOT EXISTS (SELECT 1 FROM pg_catalog.pg_auth_members WHERE member=r.oid))
       OR EXISTS (SELECT 1 FROM pg_catalog.pg_namespace WHERE nspowner=metadata_owner)
       OR EXISTS (SELECT 1 FROM pg_catalog.pg_proc WHERE proowner=metadata_owner)
       OR EXISTS (SELECT 1 FROM pg_catalog.pg_database WHERE datdba=metadata_owner)
       OR EXISTS (SELECT 1 FROM pg_catalog.pg_type t WHERE t.typowner=metadata_owner AND t.oid NOT IN (
            SELECT c.reltype FROM pg_catalog.pg_class c WHERE c.relnamespace='public'::REGNAMESPACE AND c.relname=ANY(ARRAY['product_definition_root','product_definition_control','product_definition_revision','product_definition_review','product_definition_review_private','product_definition_history','product_definition_action','product_definition_publication','product_definition_publication_control']::TEXT[])
            UNION SELECT row_type.typarray FROM pg_catalog.pg_class c JOIN pg_catalog.pg_type row_type ON row_type.oid=c.reltype
                WHERE c.relnamespace='public'::REGNAMESPACE AND c.relname=ANY(ARRAY['product_definition_root','product_definition_control','product_definition_revision','product_definition_review','product_definition_review_private','product_definition_history','product_definition_action','product_definition_publication','product_definition_publication_control']::TEXT[]))) THEN RETURN FALSE; END IF;
    FOR relation_row IN
        SELECT c.oid,c.relname,c.relowner,c.relrowsecurity,c.relforcerowsecurity,n.nspname
        FROM pg_catalog.pg_class c JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace
        WHERE c.relkind IN ('r','p','v','m','f')
          AND n.nspname NOT IN ('pg_catalog','information_schema') AND n.nspname NOT LIKE 'pg_toast%'
    LOOP
        IF pg_catalog.pg_has_role(login,relation_row.relowner,'MEMBER') THEN RETURN FALSE; END IF;
        IF relation_row.relowner<>metadata_owner AND
           (pg_catalog.has_table_privilege(metadata_owner,relation_row.oid,'SELECT,INSERT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN')
             OR pg_catalog.has_any_column_privilege(metadata_owner,relation_row.oid,'SELECT,INSERT,UPDATE,REFERENCES')) THEN RETURN FALSE; END IF;
        allowed := CASE WHEN relation_row.nspname='public' THEN CASE relation_row.relname
            WHEN 'product_definition_root' THEN ARRAY['root_id','tenant_id','product_id','product_code','business_area','profile_code','profile_version']::TEXT[]
            WHEN 'product_definition_revision' THEN ARRAY['revision_id','tenant_id','root_id','canonical_definition','public_digest','encoding_version']::TEXT[]
            WHEN 'product_definition_publication' THEN ARRAY['publication_id','tenant_id','root_id','revision_id','product_version_id','canonical_definition','public_digest','encoding_version','operational_admission','created_at']::TEXT[]
            WHEN 'product_definition_publication_control' THEN ARRAY['publication_id','tenant_id','root_id','state','state_version']::TEXT[]
            WHEN 'product_definition_version_identity' THEN ARRAY['product_version_id','tenant_id','root_id','product_id','publication_id','version_number','recorded_at']::TEXT[]
            ELSE NULL END ELSE NULL END;
        IF pg_catalog.has_table_privilege(login,relation_row.oid,'SELECT,INSERT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN') THEN RETURN FALSE; END IF;
        expected_owner := CASE relation_row.relname
            WHEN 'product_definition_root' THEN 'firefly_product_definition_metadata_owner'
            WHEN 'product_definition_control' THEN 'firefly_product_definition_metadata_owner'
            WHEN 'product_definition_revision' THEN 'firefly_product_definition_metadata_owner'
            WHEN 'product_definition_review' THEN 'firefly_product_definition_metadata_owner'
            WHEN 'product_definition_review_private' THEN 'firefly_product_definition_metadata_owner'
            WHEN 'product_definition_review_decision' THEN 'firefly_product_definition_guard'
            WHEN 'product_definition_submission' THEN 'firefly_product_definition_guard'
            WHEN 'product_definition_history' THEN 'firefly_product_definition_metadata_owner'
            WHEN 'product_definition_action' THEN 'firefly_product_definition_metadata_owner'
            WHEN 'product_definition_publication' THEN 'firefly_product_definition_metadata_owner'
            WHEN 'product_definition_publication_control' THEN 'firefly_product_definition_metadata_owner'
            WHEN 'product_definition_version_identity' THEN 'firefly_product_definition_guard'
            ELSE NULL END;
        IF expected_owner IS NOT NULL THEN
            owner_count := owner_count+1;
            IF relation_row.nspname<>'public' OR NOT relation_row.relrowsecurity OR NOT relation_row.relforcerowsecurity
           OR relation_row.relowner<>(SELECT oid FROM pg_catalog.pg_roles WHERE rolname=expected_owner) THEN RETURN FALSE; END IF;
        ELSIF relation_row.relowner=metadata_owner THEN RETURN FALSE; END IF;
        IF allowed IS NULL THEN
            IF pg_catalog.has_any_column_privilege(login,relation_row.oid,'SELECT,INSERT,UPDATE,REFERENCES') THEN RETURN FALSE; END IF;
            CONTINUE;
        END IF;
        relation_count := relation_count+1;
        SELECT array_agg(a.attname::TEXT) INTO present FROM pg_catalog.pg_attribute a
            WHERE a.attrelid=relation_row.oid AND a.attnum>0 AND NOT a.attisdropped;
        IF NOT COALESCE(allowed <@ present,FALSE) THEN RETURN FALSE; END IF;
        FOR attribute_row IN SELECT a.attnum,a.attname FROM pg_catalog.pg_attribute a
            WHERE a.attrelid=relation_row.oid AND a.attnum>0 AND NOT a.attisdropped
        LOOP
            IF pg_catalog.has_column_privilege(login,relation_row.oid,attribute_row.attnum,'SELECT')
                   IS DISTINCT FROM (attribute_row.attname=ANY(allowed))
               OR pg_catalog.has_column_privilege(login,relation_row.oid,attribute_row.attnum,'INSERT,UPDATE,REFERENCES') THEN RETURN FALSE; END IF;
        END LOOP;
    END LOOP;
    IF relation_count<>5 OR owner_count<>12 THEN RETURN FALSE; END IF;
    FOR function_row IN
        SELECT p.oid,p.proname,p.proowner,p.prosecdef,p.proconfig,p.proacl
        FROM pg_catalog.pg_proc p JOIN pg_catalog.pg_namespace n ON n.oid=p.pronamespace
        WHERE n.nspname='public' AND p.proname=ANY(ARRAY['product_definition_insurance_quote_runtime_allowed','product_definition_insurance_quote_read_matches','open_product_definition_insurance_quote_read','assert_product_definition_insurance_quote_read_live']::TEXT[])
    LOOP
        function_count := function_count+1;
        expected_signature := function_row.oid;
        IF NOT expected_signature=ANY(ARRAY['public.product_definition_insurance_quote_runtime_allowed()'::REGPROCEDURE,'public.product_definition_insurance_quote_read_matches(uuid,uuid,oid,uuid)'::REGPROCEDURE,'public.open_product_definition_insurance_quote_read(uuid,uuid,uuid,uuid,uuid,text,text,bigint,bigint)'::REGPROCEDURE,'public.assert_product_definition_insurance_quote_read_live(uuid)'::REGPROCEDURE]) OR function_row.prosecdef
           OR function_row.proowner<>(SELECT oid FROM pg_catalog.pg_roles WHERE rolname='firefly_product_definition_guard')
           OR function_row.proconfig IS DISTINCT FROM ARRAY['search_path=pg_catalog']::TEXT[]
           OR NOT pg_catalog.has_function_privilege(login,function_row.oid,'EXECUTE')
           OR EXISTS (SELECT 1 FROM pg_catalog.aclexplode(COALESCE(function_row.proacl,pg_catalog.acldefault('f',function_row.proowner))) a
                      WHERE a.privilege_type='EXECUTE' AND a.grantee NOT IN ((SELECT oid FROM pg_catalog.pg_roles WHERE rolname='firefly_product_definition_guard'),(SELECT oid FROM pg_catalog.pg_roles WHERE rolname='firefly_product_definition_insurance_quote_read'))) THEN RETURN FALSE; END IF;
    END LOOP;
    IF EXISTS (SELECT 1 FROM pg_catalog.pg_proc p JOIN pg_catalog.pg_roles owner_role ON owner_role.oid=p.proowner
        WHERE owner_role.rolname IN ('firefly_product_definition_guard','firefly_product_definition_metadata_owner')
          AND p.prorettype<>'trigger'::REGTYPE AND NOT p.oid=ANY(ARRAY['public.product_definition_insurance_quote_runtime_allowed()'::REGPROCEDURE,'public.product_definition_insurance_quote_read_matches(uuid,uuid,oid,uuid)'::REGPROCEDURE,'public.open_product_definition_insurance_quote_read(uuid,uuid,uuid,uuid,uuid,text,text,bigint,bigint)'::REGPROCEDURE,'public.assert_product_definition_insurance_quote_read_live(uuid)'::REGPROCEDURE])
          AND pg_catalog.has_function_privilege(login,p.oid,'EXECUTE')) THEN RETURN FALSE; END IF;
    RETURN function_count=4 AND pg_catalog.current_setting('server_encoding')='UTF8';
EXCEPTION WHEN OTHERS THEN RETURN FALSE;
END
$body$;

CREATE FUNCTION public.product_definition_insurance_quote_read_matches(p_tenant UUID,p_root UUID,p_relation OID,p_key UUID)
RETURNS BOOLEAN LANGUAGE plpgsql VOLATILE SECURITY INVOKER SET search_path=pg_catalog AS $body$
DECLARE
    kind TEXT; until_text TEXT; recorded_text TEXT; actor_text TEXT; permit_text TEXT;
    deadline BIGINT; recorded BIGINT; actor_until BIGINT; permit_until BIGINT; now_millis BIGINT;
BEGIN
    IF NOT public.product_definition_insurance_quote_runtime_allowed()
       OR pg_catalog.current_setting('transaction_read_only')<>'on'
       OR pg_catalog.current_setting('transaction_isolation')<>'repeatable read'
       OR COALESCE(pg_catalog.current_setting('firefly.product_definition.insurance_quote_read_id',TRUE),'') !~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
       OR pg_catalog.current_setting('firefly.product_definition.insurance_quote_read_tenant',TRUE) IS DISTINCT FROM p_tenant::TEXT
       OR pg_catalog.current_setting('firefly.product_definition.insurance_quote_read_root',TRUE) IS DISTINCT FROM p_root::TEXT
       OR pg_catalog.current_setting('firefly.product_definition.insurance_quote_read_login',TRUE) IS DISTINCT FROM session_user::TEXT
       OR pg_catalog.current_setting('firefly.product_definition.insurance_quote_read_backend',TRUE) IS DISTINCT FROM pg_catalog.pg_backend_pid()::TEXT
       OR pg_catalog.current_setting('firefly.product_definition.insurance_quote_read_xid',TRUE) IS DISTINCT FROM pg_catalog.pg_current_xact_id()::TEXT
       OR COALESCE(pg_catalog.current_setting('firefly.product_definition.insurance_quote_read_actor',TRUE),'')=''
       OR COALESCE(pg_catalog.current_setting('firefly.product_definition.insurance_quote_read_issuer',TRUE),'') !~ '^https?://' THEN RETURN FALSE; END IF;
    until_text := pg_catalog.current_setting('firefly.product_definition.insurance_quote_read_until',TRUE);
    recorded_text := pg_catalog.current_setting('firefly.product_definition.insurance_quote_read_recorded',TRUE);
    actor_text := pg_catalog.current_setting('firefly.product_definition.insurance_quote_read_actor_until',TRUE);
    permit_text := pg_catalog.current_setting('firefly.product_definition.insurance_quote_read_permit_until',TRUE);
    IF until_text IS NULL OR until_text !~ '^[0-9]{1,15}$' OR recorded_text IS NULL OR recorded_text !~ '^[0-9]{1,15}$'
       OR actor_text IS NULL OR actor_text !~ '^[0-9]{1,15}$' OR permit_text IS NULL OR permit_text !~ '^[0-9]{1,15}$' THEN RETURN FALSE; END IF;
    deadline := until_text::BIGINT; recorded := recorded_text::BIGINT;
    actor_until := actor_text::BIGINT; permit_until := permit_text::BIGINT;
    now_millis := floor(extract(epoch FROM pg_catalog.clock_timestamp())*1000)::BIGINT;
    IF recorded>now_millis OR deadline<=now_millis
       OR deadline<>least(actor_until,permit_until,recorded+30000)
       OR actor_until>253402300799999 OR permit_until>253402300799999 THEN RETURN FALSE; END IF;
    IF COALESCE(pg_catalog.current_setting('firefly.product_definition.insurance_quote_read_product',TRUE),'') !~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' THEN RETURN FALSE; END IF;
    SELECT c.relname INTO kind FROM pg_catalog.pg_class c WHERE c.oid=p_relation AND c.relnamespace='public'::REGNAMESPACE;
    CASE kind
        WHEN 'product_definition_root' THEN RETURN p_key=p_root;
        WHEN 'product_definition_revision' THEN RETURN p_key::TEXT=pg_catalog.current_setting('firefly.product_definition.insurance_quote_read_revision',TRUE);
        WHEN 'product_definition_publication' THEN RETURN p_key::TEXT=pg_catalog.current_setting('firefly.product_definition.insurance_quote_read_publication',TRUE);
        WHEN 'product_definition_publication_control' THEN RETURN p_key::TEXT=pg_catalog.current_setting('firefly.product_definition.insurance_quote_read_publication',TRUE);
        WHEN 'product_definition_version_identity' THEN RETURN TRUE;
        ELSE RETURN FALSE;
    END CASE;
EXCEPTION WHEN OTHERS THEN RETURN FALSE;
END
$body$;

CREATE FUNCTION public.assert_product_definition_insurance_quote_read_live(p_context UUID)
RETURNS BOOLEAN LANGUAGE plpgsql VOLATILE SECURITY INVOKER SET search_path=pg_catalog AS $body$
DECLARE tenant UUID; root UUID;
BEGIN
    IF p_context IS NULL OR p_context='00000000-0000-0000-0000-000000000000'::UUID
       OR p_context::TEXT IS DISTINCT FROM pg_catalog.current_setting('firefly.product_definition.insurance_quote_read_id',TRUE) THEN
        RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='INSURANCE_QUOTE_PURPOSE_READ_UNAVAILABLE';
    END IF;
    tenant:=pg_catalog.current_setting('firefly.product_definition.insurance_quote_read_tenant',TRUE)::UUID;
    root:=pg_catalog.current_setting('firefly.product_definition.insurance_quote_read_root',TRUE)::UUID;
    IF NOT public.product_definition_insurance_quote_read_matches(tenant,root,'public.product_definition_root'::REGCLASS,root) THEN
        RAISE EXCEPTION USING ERRCODE='PD403', MESSAGE='CURRENT_PRODUCT_PERMISSION_REQUIRED';
    END IF;
    RETURN TRUE;
END
$body$;

CREATE FUNCTION public.open_product_definition_insurance_quote_read(
    p_tenant UUID,p_root UUID,p_product UUID,p_revision UUID,p_publication UUID,
    p_issuer TEXT,p_actor TEXT,p_actor_until BIGINT,p_permit_until BIGINT)
RETURNS TABLE(context_id UUID,recorded_at TIMESTAMPTZ,db_now_ms BIGINT,admission_until_ms BIGINT)
LANGUAGE plpgsql VOLATILE SECURITY INVOKER SET search_path=pg_catalog AS $body$
DECLARE stamp TIMESTAMPTZ; millis BIGINT; deadline BIGINT; identifier UUID:=pg_catalog.gen_random_uuid();
BEGIN
    IF NOT public.product_definition_insurance_quote_runtime_allowed()
       OR pg_catalog.current_setting('transaction_read_only')<>'on'
       OR pg_catalog.current_setting('transaction_isolation')<>'repeatable read'
       OR p_tenant IS NULL OR p_root IS NULL OR p_product IS NULL OR p_revision IS NULL OR p_publication IS NULL
       OR '00000000-0000-0000-0000-000000000000'::UUID=ANY(ARRAY[p_tenant,p_root,p_product,p_revision,p_publication])
       OR p_issuer IS NULL OR p_issuer !~ '^https?://' OR length(p_issuer)>512
       OR p_actor IS NULL OR p_actor ~ '^[[:space:]]*$' OR length(p_actor)>255
       OR p_actor_until IS NULL OR p_permit_until IS NULL
       OR p_actor_until>253402300799999 OR p_permit_until>253402300799999
       OR COALESCE(pg_catalog.current_setting('firefly.product_definition.insurance_quote_read_id',TRUE),'')<>'' THEN
        RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='INSURANCE_QUOTE_PURPOSE_READ_UNAVAILABLE';
    END IF;
    stamp:=pg_catalog.clock_timestamp(); millis:=floor(extract(epoch FROM stamp)*1000)::BIGINT;
    IF p_actor_until<=millis OR p_permit_until<=millis THEN
        RAISE EXCEPTION USING ERRCODE='PD403', MESSAGE='CURRENT_PRODUCT_PERMISSION_REQUIRED';
    END IF;
    deadline:=least(p_actor_until,p_permit_until,millis+30000);
    PERFORM pg_catalog.set_config('firefly.product_definition.insurance_quote_read_id',identifier::TEXT,TRUE);
    PERFORM pg_catalog.set_config('firefly.product_definition.insurance_quote_read_tenant',p_tenant::TEXT,TRUE);
    PERFORM pg_catalog.set_config('firefly.product_definition.insurance_quote_read_root',p_root::TEXT,TRUE);
    PERFORM pg_catalog.set_config('firefly.product_definition.insurance_quote_read_product',p_product::TEXT,TRUE);
    PERFORM pg_catalog.set_config('firefly.product_definition.insurance_quote_read_revision',p_revision::TEXT,TRUE);
    PERFORM pg_catalog.set_config('firefly.product_definition.insurance_quote_read_publication',p_publication::TEXT,TRUE);
    PERFORM pg_catalog.set_config('firefly.product_definition.insurance_quote_read_login',session_user::TEXT,TRUE);
    PERFORM pg_catalog.set_config('firefly.product_definition.insurance_quote_read_backend',pg_catalog.pg_backend_pid()::TEXT,TRUE);
    PERFORM pg_catalog.set_config('firefly.product_definition.insurance_quote_read_xid',pg_catalog.pg_current_xact_id()::TEXT,TRUE);
    PERFORM pg_catalog.set_config('firefly.product_definition.insurance_quote_read_issuer',p_issuer,TRUE);
    PERFORM pg_catalog.set_config('firefly.product_definition.insurance_quote_read_actor',p_actor,TRUE);
    PERFORM pg_catalog.set_config('firefly.product_definition.insurance_quote_read_recorded',millis::TEXT,TRUE);
    PERFORM pg_catalog.set_config('firefly.product_definition.insurance_quote_read_actor_until',p_actor_until::TEXT,TRUE);
    PERFORM pg_catalog.set_config('firefly.product_definition.insurance_quote_read_permit_until',p_permit_until::TEXT,TRUE);
    PERFORM pg_catalog.set_config('firefly.product_definition.insurance_quote_read_until',deadline::TEXT,TRUE);
    IF NOT EXISTS (SELECT 1 FROM public.product_definition_root r
        JOIN public.product_definition_publication p ON p.tenant_id=r.tenant_id AND p.root_id=r.root_id
        JOIN public.product_definition_publication_control c ON c.tenant_id=p.tenant_id AND c.root_id=p.root_id AND c.publication_id=p.publication_id
        JOIN public.product_definition_version_identity v ON v.tenant_id=p.tenant_id AND v.root_id=p.root_id AND v.product_version_id=p.product_version_id AND v.publication_id=p.publication_id
        WHERE r.tenant_id=p_tenant AND r.root_id=p_root AND r.product_id=p_product
          AND r.business_area='INSURANCE' AND r.profile_code='TERM_PC_INSURANCE_V1' AND r.profile_version=1
          AND p.revision_id=p_revision AND p.publication_id=p_publication AND p.operational_admission='NOT_ADMITTED'
          AND c.state='ACTIVE' AND v.product_id=p_product
          AND NOT EXISTS (SELECT 1 FROM public.product_definition_version_identity newer
                WHERE newer.tenant_id=r.tenant_id AND newer.root_id=r.root_id AND newer.version_number>v.version_number)) THEN
        RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='INSURANCE_QUOTE_PURPOSE_READ_UNAVAILABLE';
    END IF;
    IF NOT public.assert_product_definition_insurance_quote_read_live(identifier) THEN
        RAISE EXCEPTION USING ERRCODE='PD403', MESSAGE='CURRENT_PRODUCT_PERMISSION_REQUIRED';
    END IF;
    RETURN QUERY SELECT identifier,stamp,millis,deadline;
END
$body$;

CREATE FUNCTION public.product_definition_command_admission(p_context UUID)
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
    RETURN QUERY SELECT command.recorded_at,millis,command.deadline_millis;
END
$body$;

CREATE OR REPLACE FUNCTION firefly_product_definition_private.session_allowed()
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
            'product_definition_command_runtime_allowed','product_definition_command_scope_matches','product_definition_private_read_matches','product_definition_private_read_admission','product_definition_command_admission',
            'product_definition_native_before','product_definition_native_after','product_definition_native_complete',
            'guard_product_definition_legacy_child'])) LOOP
        IF function_row.proowner<>(SELECT oid FROM pg_catalog.pg_roles WHERE rolname='firefly_product_definition_guard')
           OR NOT ('search_path=pg_catalog'=ANY(COALESCE(function_row.proconfig,ARRAY[]::TEXT[])))
           OR (function_row.nspname='public' AND function_row.prosecdef IS DISTINCT FROM
               (function_row.proname<>ALL(ARRAY['open_product_definition_review_read','assert_product_definition_review_read_live',
                   'open_product_definition_cost_read','assert_product_definition_cost_read_live','open_product_definition_current_review_read','assert_product_definition_current_review_read_live','product_definition_private_read_matches','product_definition_private_read_admission'])))
           OR EXISTS (SELECT 1 FROM pg_catalog.aclexplode(COALESCE(function_row.proacl,pg_catalog.acldefault('f',function_row.proowner))) a
               WHERE a.grantee=0 AND a.privilege_type='EXECUTE')
           OR (function_row.nspname='firefly_product_definition_private' AND pg_catalog.has_function_privilege(login,function_row.oid,'EXECUTE')) THEN RETURN FALSE; END IF;
    END LOOP;
    IF (SELECT count(*) FROM pg_catalog.pg_proc p JOIN pg_catalog.pg_namespace n ON n.oid=p.pronamespace
        WHERE n.nspname='public' AND p.proname=ANY(ARRAY['begin_product_definition_command','assert_product_definition_command_live','finish_product_definition_command',
            'open_product_definition_review_read','assert_product_definition_review_read_live','open_product_definition_cost_read','assert_product_definition_cost_read_live','open_product_definition_current_review_read','assert_product_definition_current_review_read_live',
            'product_definition_command_runtime_allowed','product_definition_command_scope_matches','product_definition_private_read_matches','product_definition_private_read_admission','product_definition_command_admission']))<>14 THEN RETURN FALSE; END IF;
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

DO $ownership$
DECLARE signature REGPROCEDURE;
BEGIN
    IF pg_catalog.has_schema_privilege('firefly_product_definition_guard','public','CREATE') THEN
        RAISE EXCEPTION USING ERRCODE='42501', MESSAGE='PREEXISTING_PRODUCT_GUARD_SCHEMA_CREATE';
    END IF;
    GRANT CREATE ON SCHEMA public TO firefly_product_definition_guard;
    FOREACH signature IN ARRAY ARRAY['public.product_definition_insurance_quote_runtime_allowed()'::REGPROCEDURE,'public.product_definition_insurance_quote_read_matches(uuid,uuid,oid,uuid)'::REGPROCEDURE,'public.open_product_definition_insurance_quote_read(uuid,uuid,uuid,uuid,uuid,text,text,bigint,bigint)'::REGPROCEDURE,'public.assert_product_definition_insurance_quote_read_live(uuid)'::REGPROCEDURE,'public.product_definition_private_read_admission(uuid)'::REGPROCEDURE,'public.product_definition_command_admission(uuid)'::REGPROCEDURE] LOOP
        EXECUTE pg_catalog.format('ALTER FUNCTION %s OWNER TO firefly_product_definition_guard',signature);
        EXECUTE pg_catalog.format('REVOKE ALL ON FUNCTION %s FROM PUBLIC',signature);
    END LOOP;
    REVOKE CREATE ON SCHEMA public FROM firefly_product_definition_guard;
    IF pg_catalog.has_schema_privilege('firefly_product_definition_guard','public','CREATE') THEN
        RAISE EXCEPTION USING ERRCODE='42501', MESSAGE='PRODUCT_GUARD_SCHEMA_CREATE_NOT_REMOVED';
    END IF;
END
$ownership$;
GRANT EXECUTE ON FUNCTION public.product_definition_insurance_quote_runtime_allowed() TO firefly_product_definition_insurance_quote_read;
GRANT EXECUTE ON FUNCTION public.product_definition_insurance_quote_read_matches(uuid,uuid,oid,uuid) TO firefly_product_definition_insurance_quote_read;
GRANT EXECUTE ON FUNCTION public.open_product_definition_insurance_quote_read(uuid,uuid,uuid,uuid,uuid,text,text,bigint,bigint) TO firefly_product_definition_insurance_quote_read;
GRANT EXECUTE ON FUNCTION public.assert_product_definition_insurance_quote_read_live(uuid) TO firefly_product_definition_insurance_quote_read;
GRANT EXECUTE ON FUNCTION public.product_definition_private_read_admission(uuid) TO firefly_product_definition_commands;
GRANT EXECUTE ON FUNCTION public.product_definition_command_admission(uuid) TO firefly_product_definition_commands;

GRANT SELECT (root_id,tenant_id,product_id,product_code,business_area,profile_code,profile_version) ON public.product_definition_root TO firefly_product_definition_insurance_quote_read;
GRANT SELECT (revision_id,tenant_id,root_id,canonical_definition,public_digest,encoding_version) ON public.product_definition_revision TO firefly_product_definition_insurance_quote_read;
GRANT SELECT (publication_id,tenant_id,root_id,revision_id,product_version_id,canonical_definition,public_digest,encoding_version,operational_admission,created_at) ON public.product_definition_publication TO firefly_product_definition_insurance_quote_read;
GRANT SELECT (publication_id,tenant_id,root_id,state,state_version) ON public.product_definition_publication_control TO firefly_product_definition_insurance_quote_read;
GRANT SELECT (product_version_id,tenant_id,root_id,product_id,publication_id,version_number,recorded_at) ON public.product_definition_version_identity TO firefly_product_definition_insurance_quote_read;

CREATE POLICY insurance_quote_purpose_select ON public.product_definition_root FOR SELECT TO firefly_product_definition_insurance_quote_read
 USING (public.product_definition_insurance_quote_read_matches(tenant_id,root_id,'public.product_definition_root'::REGCLASS,root_id) AND business_area='INSURANCE' AND profile_code='TERM_PC_INSURANCE_V1' AND profile_version=1 AND product_id::TEXT=pg_catalog.current_setting('firefly.product_definition.insurance_quote_read_product',TRUE));

CREATE POLICY insurance_quote_purpose_select ON public.product_definition_revision FOR SELECT TO firefly_product_definition_insurance_quote_read
 USING (public.product_definition_insurance_quote_read_matches(tenant_id,root_id,'public.product_definition_revision'::REGCLASS,revision_id));

CREATE POLICY insurance_quote_purpose_select ON public.product_definition_publication FOR SELECT TO firefly_product_definition_insurance_quote_read
 USING (public.product_definition_insurance_quote_read_matches(tenant_id,root_id,'public.product_definition_publication'::REGCLASS,publication_id));

CREATE POLICY insurance_quote_purpose_select ON public.product_definition_publication_control FOR SELECT TO firefly_product_definition_insurance_quote_read
 USING (public.product_definition_insurance_quote_read_matches(tenant_id,root_id,'public.product_definition_publication_control'::REGCLASS,publication_id));

CREATE POLICY insurance_quote_purpose_select ON public.product_definition_version_identity FOR SELECT TO firefly_product_definition_insurance_quote_read
 USING (public.product_definition_insurance_quote_read_matches(tenant_id,root_id,'public.product_definition_version_identity'::REGCLASS,product_version_id) AND product_id::TEXT=pg_catalog.current_setting('firefly.product_definition.insurance_quote_read_product',TRUE));

ALTER POLICY product_private_read ON public.product_definition_root TO firefly_product_definition_commands
 USING (public.product_definition_private_read_matches(tenant_id,root_id,'public.product_definition_root'::REGCLASS,root_id) AND ((pg_catalog.current_setting('firefly.product_definition.private_read_profile',TRUE)='product-definition-human-staff-v1' AND business_area='BANKING' AND profile_code='ACCOUNT_TARIFF_V1' AND profile_version=1)
 OR (pg_catalog.current_setting('firefly.product_definition.private_read_profile',TRUE)='product-definition-term-pc-staff-v1' AND business_area='INSURANCE' AND profile_code='TERM_PC_INSURANCE_V1' AND profile_version=1)));

ALTER POLICY publication_metadata_select ON public.product_definition_root TO firefly_product_definition_publication_read
 USING (public.product_definition_publication_read_matches(tenant_id,root_id,'public.product_definition_root'::REGCLASS) AND ((pg_catalog.current_setting('firefly.product_definition.publication_read_profile',TRUE)='product-definition-human-staff-v1' AND business_area='BANKING' AND profile_code='ACCOUNT_TARIFF_V1' AND profile_version=1)
 OR (pg_catalog.current_setting('firefly.product_definition.publication_read_profile',TRUE)='product-definition-term-pc-staff-v1' AND business_area='INSURANCE' AND profile_code='TERM_PC_INSURANCE_V1' AND profile_version=1)));
