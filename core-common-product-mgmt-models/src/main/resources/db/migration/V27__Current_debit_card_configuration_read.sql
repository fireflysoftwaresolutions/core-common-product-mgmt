-- Additive current debit configuration read; V1-V22 and V26 plus explicit administrator role preparation are required.
-- This read context does not establish customer eligibility or issuer execution.

DO $preflight$
DECLARE role_row RECORD; installer_row RECORD; metadata_owner_row RECORD;
BEGIN
    SELECT * INTO role_row FROM pg_catalog.pg_roles WHERE rolname='firefly_product_definition_debit_card_read';
    IF NOT FOUND OR role_row.rolcanlogin OR role_row.rolsuper OR role_row.rolbypassrls
       OR role_row.rolinherit OR role_row.rolcreatedb OR role_row.rolcreaterole OR role_row.rolreplication
       OR EXISTS (SELECT 1 FROM pg_catalog.pg_auth_members WHERE member=role_row.oid) THEN
        RAISE EXCEPTION USING ERRCODE='42501', MESSAGE='EXPLICIT_PRODUCT_DEBIT_CARD_READ_ROLE_REQUIRED';
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
        RAISE EXCEPTION USING ERRCODE='42501', MESSAGE='SEPARATE_RESTRICTED_DEBIT_CARD_INSTALLER_REQUIRED';
    END IF;
END
$preflight$;

CREATE FUNCTION public.product_definition_debit_card_runtime_allowed()
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
       OR NOT pg_catalog.pg_has_role(login,'firefly_product_definition_debit_card_read','USAGE')
       OR pg_catalog.has_database_privilege(login,pg_catalog.current_database(),'CREATE,TEMPORARY')
       OR pg_catalog.has_schema_privilege(login,'public','CREATE')
       OR pg_catalog.has_schema_privilege(login,'firefly_product_definition_private','USAGE,CREATE') THEN RETURN FALSE; END IF;
    SELECT * INTO role_row FROM pg_catalog.pg_roles WHERE rolname='firefly_product_definition_debit_card_read';
    IF NOT FOUND OR role_row.rolcanlogin OR role_row.rolsuper OR role_row.rolbypassrls
       OR role_row.rolinherit OR role_row.rolcreatedb OR role_row.rolcreaterole OR role_row.rolreplication
       OR EXISTS (SELECT 1 FROM pg_catalog.pg_auth_members WHERE member=role_row.oid)
       OR EXISTS (SELECT 1 FROM pg_catalog.pg_roles r
                  WHERE r.rolname NOT IN (login,'firefly_product_definition_debit_card_read')
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
        WHERE n.nspname='public' AND p.proname=ANY(ARRAY['product_definition_debit_card_runtime_allowed','product_definition_debit_card_read_matches','open_product_definition_debit_card_read','assert_product_definition_debit_card_read_live']::TEXT[])
    LOOP
        function_count := function_count+1;
        expected_signature := function_row.oid;
        IF NOT expected_signature=ANY(ARRAY['public.product_definition_debit_card_runtime_allowed()'::REGPROCEDURE,'public.product_definition_debit_card_read_matches(uuid,uuid,oid,uuid)'::REGPROCEDURE,'public.open_product_definition_debit_card_read(uuid,uuid,uuid,uuid,uuid,text,text,bigint,bigint)'::REGPROCEDURE,'public.assert_product_definition_debit_card_read_live(uuid)'::REGPROCEDURE]) OR function_row.prosecdef
           OR function_row.proowner<>(SELECT oid FROM pg_catalog.pg_roles WHERE rolname='firefly_product_definition_guard')
           OR function_row.proconfig IS DISTINCT FROM ARRAY['search_path=pg_catalog']::TEXT[]
           OR NOT pg_catalog.has_function_privilege(login,function_row.oid,'EXECUTE')
           OR EXISTS (SELECT 1 FROM pg_catalog.aclexplode(COALESCE(function_row.proacl,pg_catalog.acldefault('f',function_row.proowner))) a
                      WHERE a.privilege_type='EXECUTE' AND a.grantee NOT IN ((SELECT oid FROM pg_catalog.pg_roles WHERE rolname='firefly_product_definition_guard'),(SELECT oid FROM pg_catalog.pg_roles WHERE rolname='firefly_product_definition_debit_card_read'))) THEN RETURN FALSE; END IF;
    END LOOP;
    IF EXISTS (SELECT 1 FROM pg_catalog.pg_proc p JOIN pg_catalog.pg_roles owner_role ON owner_role.oid=p.proowner
        WHERE owner_role.rolname IN ('firefly_product_definition_guard','firefly_product_definition_metadata_owner')
          AND p.prorettype<>'trigger'::REGTYPE AND NOT p.oid=ANY(ARRAY['public.product_definition_debit_card_runtime_allowed()'::REGPROCEDURE,'public.product_definition_debit_card_read_matches(uuid,uuid,oid,uuid)'::REGPROCEDURE,'public.open_product_definition_debit_card_read(uuid,uuid,uuid,uuid,uuid,text,text,bigint,bigint)'::REGPROCEDURE,'public.assert_product_definition_debit_card_read_live(uuid)'::REGPROCEDURE])
          AND pg_catalog.has_function_privilege(login,p.oid,'EXECUTE')) THEN RETURN FALSE; END IF;
    RETURN function_count=4 AND pg_catalog.current_setting('server_encoding')='UTF8';
EXCEPTION WHEN OTHERS THEN RETURN FALSE;
END
$body$;

CREATE FUNCTION public.product_definition_debit_card_read_matches(p_tenant UUID,p_root UUID,p_relation OID,p_key UUID)
RETURNS BOOLEAN LANGUAGE plpgsql VOLATILE SECURITY INVOKER SET search_path=pg_catalog AS $body$
DECLARE
    kind TEXT; until_text TEXT; recorded_text TEXT; actor_text TEXT; permit_text TEXT;
    deadline BIGINT; recorded BIGINT; actor_until BIGINT; permit_until BIGINT; now_millis BIGINT;
BEGIN
    IF NOT public.product_definition_debit_card_runtime_allowed()
       OR pg_catalog.current_setting('transaction_read_only')<>'on'
       OR pg_catalog.current_setting('transaction_isolation')<>'repeatable read'
       OR COALESCE(pg_catalog.current_setting('firefly.product_definition.debit_card_read_id',TRUE),'') !~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
       OR pg_catalog.current_setting('firefly.product_definition.debit_card_read_tenant',TRUE) IS DISTINCT FROM p_tenant::TEXT
       OR pg_catalog.current_setting('firefly.product_definition.debit_card_read_root',TRUE) IS DISTINCT FROM p_root::TEXT
       OR pg_catalog.current_setting('firefly.product_definition.debit_card_read_login',TRUE) IS DISTINCT FROM session_user::TEXT
       OR pg_catalog.current_setting('firefly.product_definition.debit_card_read_backend',TRUE) IS DISTINCT FROM pg_catalog.pg_backend_pid()::TEXT
       OR pg_catalog.current_setting('firefly.product_definition.debit_card_read_xid',TRUE) IS DISTINCT FROM pg_catalog.pg_current_xact_id()::TEXT
       OR COALESCE(pg_catalog.current_setting('firefly.product_definition.debit_card_read_actor',TRUE),'')=''
       OR COALESCE(pg_catalog.current_setting('firefly.product_definition.debit_card_read_issuer',TRUE),'') !~ '^https?://' THEN RETURN FALSE; END IF;
    until_text := pg_catalog.current_setting('firefly.product_definition.debit_card_read_until',TRUE);
    recorded_text := pg_catalog.current_setting('firefly.product_definition.debit_card_read_recorded',TRUE);
    actor_text := pg_catalog.current_setting('firefly.product_definition.debit_card_read_actor_until',TRUE);
    permit_text := pg_catalog.current_setting('firefly.product_definition.debit_card_read_permit_until',TRUE);
    IF until_text IS NULL OR until_text !~ '^[0-9]{1,15}$' OR recorded_text IS NULL OR recorded_text !~ '^[0-9]{1,15}$'
       OR actor_text IS NULL OR actor_text !~ '^[0-9]{1,15}$' OR permit_text IS NULL OR permit_text !~ '^[0-9]{1,15}$' THEN RETURN FALSE; END IF;
    deadline := until_text::BIGINT; recorded := recorded_text::BIGINT;
    actor_until := actor_text::BIGINT; permit_until := permit_text::BIGINT;
    now_millis := floor(extract(epoch FROM pg_catalog.clock_timestamp())*1000)::BIGINT;
    IF recorded>now_millis OR deadline<=now_millis
       OR deadline<>least(actor_until,permit_until,recorded+30000)
       OR actor_until>253402300799999 OR permit_until>253402300799999 THEN RETURN FALSE; END IF;
    IF COALESCE(pg_catalog.current_setting('firefly.product_definition.debit_card_read_product',TRUE),'') !~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' THEN RETURN FALSE; END IF;
    SELECT c.relname INTO kind FROM pg_catalog.pg_class c WHERE c.oid=p_relation AND c.relnamespace='public'::REGNAMESPACE;
    CASE kind
        WHEN 'product_definition_root' THEN RETURN p_key=p_root;
        WHEN 'product_definition_revision' THEN RETURN p_key::TEXT=pg_catalog.current_setting('firefly.product_definition.debit_card_read_revision',TRUE);
        WHEN 'product_definition_publication' THEN RETURN p_key::TEXT=pg_catalog.current_setting('firefly.product_definition.debit_card_read_publication',TRUE);
        WHEN 'product_definition_publication_control' THEN RETURN p_key::TEXT=pg_catalog.current_setting('firefly.product_definition.debit_card_read_publication',TRUE);
        WHEN 'product_definition_version_identity' THEN RETURN TRUE;
        ELSE RETURN FALSE;
    END CASE;
EXCEPTION WHEN OTHERS THEN RETURN FALSE;
END
$body$;

CREATE FUNCTION public.assert_product_definition_debit_card_read_live(p_context UUID)
RETURNS BOOLEAN LANGUAGE plpgsql VOLATILE SECURITY INVOKER SET search_path=pg_catalog AS $body$
DECLARE tenant UUID; root UUID;
BEGIN
    IF p_context IS NULL OR p_context='00000000-0000-0000-0000-000000000000'::UUID
       OR p_context::TEXT IS DISTINCT FROM pg_catalog.current_setting('firefly.product_definition.debit_card_read_id',TRUE) THEN
        RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='DEBIT_CARD_PURPOSE_READ_UNAVAILABLE';
    END IF;
    tenant:=pg_catalog.current_setting('firefly.product_definition.debit_card_read_tenant',TRUE)::UUID;
    root:=pg_catalog.current_setting('firefly.product_definition.debit_card_read_root',TRUE)::UUID;
    IF NOT public.product_definition_debit_card_read_matches(tenant,root,'public.product_definition_root'::REGCLASS,root) THEN
        RAISE EXCEPTION USING ERRCODE='PD403', MESSAGE='CURRENT_PRODUCT_PERMISSION_REQUIRED';
    END IF;
    RETURN TRUE;
END
$body$;

CREATE FUNCTION public.open_product_definition_debit_card_read(
    p_tenant UUID,p_root UUID,p_product UUID,p_revision UUID,p_publication UUID,
    p_issuer TEXT,p_actor TEXT,p_actor_until BIGINT,p_permit_until BIGINT)
RETURNS TABLE(context_id UUID,recorded_at TIMESTAMPTZ,db_now_ms BIGINT,admission_until_ms BIGINT)
LANGUAGE plpgsql VOLATILE SECURITY INVOKER SET search_path=pg_catalog AS $body$
DECLARE stamp TIMESTAMPTZ; millis BIGINT; deadline BIGINT; identifier UUID:=pg_catalog.gen_random_uuid();
BEGIN
    IF NOT public.product_definition_debit_card_runtime_allowed()
       OR pg_catalog.current_setting('transaction_read_only')<>'on'
       OR pg_catalog.current_setting('transaction_isolation')<>'repeatable read'
       OR p_tenant IS NULL OR p_root IS NULL OR p_product IS NULL OR p_revision IS NULL OR p_publication IS NULL
       OR '00000000-0000-0000-0000-000000000000'::UUID=ANY(ARRAY[p_tenant,p_root,p_product,p_revision,p_publication])
       OR p_issuer IS NULL OR p_issuer !~ '^https?://' OR length(p_issuer)>512
       OR p_actor IS NULL OR p_actor ~ '^[[:space:]]*$' OR length(p_actor)>255
       OR p_actor_until IS NULL OR p_permit_until IS NULL
       OR p_actor_until>253402300799999 OR p_permit_until>253402300799999
       OR COALESCE(pg_catalog.current_setting('firefly.product_definition.debit_card_read_id',TRUE),'')<>'' THEN
        RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='DEBIT_CARD_PURPOSE_READ_UNAVAILABLE';
    END IF;
    stamp:=pg_catalog.clock_timestamp(); millis:=floor(extract(epoch FROM stamp)*1000)::BIGINT;
    IF p_actor_until<=millis OR p_permit_until<=millis THEN
        RAISE EXCEPTION USING ERRCODE='PD403', MESSAGE='CURRENT_PRODUCT_PERMISSION_REQUIRED';
    END IF;
    deadline:=least(p_actor_until,p_permit_until,millis+30000);
    PERFORM pg_catalog.set_config('firefly.product_definition.debit_card_read_id',identifier::TEXT,TRUE);
    PERFORM pg_catalog.set_config('firefly.product_definition.debit_card_read_tenant',p_tenant::TEXT,TRUE);
    PERFORM pg_catalog.set_config('firefly.product_definition.debit_card_read_root',p_root::TEXT,TRUE);
    PERFORM pg_catalog.set_config('firefly.product_definition.debit_card_read_product',p_product::TEXT,TRUE);
    PERFORM pg_catalog.set_config('firefly.product_definition.debit_card_read_revision',p_revision::TEXT,TRUE);
    PERFORM pg_catalog.set_config('firefly.product_definition.debit_card_read_publication',p_publication::TEXT,TRUE);
    PERFORM pg_catalog.set_config('firefly.product_definition.debit_card_read_login',session_user::TEXT,TRUE);
    PERFORM pg_catalog.set_config('firefly.product_definition.debit_card_read_backend',pg_catalog.pg_backend_pid()::TEXT,TRUE);
    PERFORM pg_catalog.set_config('firefly.product_definition.debit_card_read_xid',pg_catalog.pg_current_xact_id()::TEXT,TRUE);
    PERFORM pg_catalog.set_config('firefly.product_definition.debit_card_read_issuer',p_issuer,TRUE);
    PERFORM pg_catalog.set_config('firefly.product_definition.debit_card_read_actor',p_actor,TRUE);
    PERFORM pg_catalog.set_config('firefly.product_definition.debit_card_read_recorded',millis::TEXT,TRUE);
    PERFORM pg_catalog.set_config('firefly.product_definition.debit_card_read_actor_until',p_actor_until::TEXT,TRUE);
    PERFORM pg_catalog.set_config('firefly.product_definition.debit_card_read_permit_until',p_permit_until::TEXT,TRUE);
    PERFORM pg_catalog.set_config('firefly.product_definition.debit_card_read_until',deadline::TEXT,TRUE);
    IF NOT EXISTS (SELECT 1 FROM public.product_definition_root r
        JOIN public.product_definition_publication p ON p.tenant_id=r.tenant_id AND p.root_id=r.root_id
        JOIN public.product_definition_publication_control c ON c.tenant_id=p.tenant_id AND c.root_id=p.root_id AND c.publication_id=p.publication_id
        JOIN public.product_definition_version_identity v ON v.tenant_id=p.tenant_id AND v.root_id=p.root_id AND v.product_version_id=p.product_version_id AND v.publication_id=p.publication_id
        WHERE r.tenant_id=p_tenant AND r.root_id=p_root AND r.product_id=p_product
          AND r.business_area='BANKING' AND r.profile_code='DEBIT_CARD_V1' AND r.profile_version=1
          AND p.revision_id=p_revision AND p.publication_id=p_publication AND p.operational_admission='NOT_ADMITTED'
          AND c.state='ACTIVE' AND v.product_id=p_product
          AND NOT EXISTS (SELECT 1 FROM public.product_definition_version_identity newer
                WHERE newer.tenant_id=r.tenant_id AND newer.root_id=r.root_id AND newer.version_number>v.version_number)) THEN
        RAISE EXCEPTION USING ERRCODE='PD503', MESSAGE='DEBIT_CARD_PURPOSE_READ_UNAVAILABLE';
    END IF;
    IF NOT public.assert_product_definition_debit_card_read_live(identifier) THEN
        RAISE EXCEPTION USING ERRCODE='PD403', MESSAGE='CURRENT_PRODUCT_PERMISSION_REQUIRED';
    END IF;
    RETURN QUERY SELECT identifier,stamp,millis,deadline;
END
$body$;

DO $ownership$
DECLARE signature REGPROCEDURE;
BEGIN
    IF pg_catalog.has_schema_privilege('firefly_product_definition_guard','public','CREATE') THEN
        RAISE EXCEPTION USING ERRCODE='42501', MESSAGE='PREEXISTING_PRODUCT_GUARD_SCHEMA_CREATE';
    END IF;
    GRANT CREATE ON SCHEMA public TO firefly_product_definition_guard;
    FOREACH signature IN ARRAY ARRAY['public.product_definition_debit_card_runtime_allowed()'::REGPROCEDURE,'public.product_definition_debit_card_read_matches(uuid,uuid,oid,uuid)'::REGPROCEDURE,'public.open_product_definition_debit_card_read(uuid,uuid,uuid,uuid,uuid,text,text,bigint,bigint)'::REGPROCEDURE,'public.assert_product_definition_debit_card_read_live(uuid)'::REGPROCEDURE] LOOP
        EXECUTE pg_catalog.format('ALTER FUNCTION %s OWNER TO firefly_product_definition_guard',signature);
        EXECUTE pg_catalog.format('REVOKE ALL ON FUNCTION %s FROM PUBLIC',signature);
    END LOOP;
    REVOKE CREATE ON SCHEMA public FROM firefly_product_definition_guard;
    IF pg_catalog.has_schema_privilege('firefly_product_definition_guard','public','CREATE') THEN
        RAISE EXCEPTION USING ERRCODE='42501', MESSAGE='PRODUCT_GUARD_SCHEMA_CREATE_NOT_REMOVED';
    END IF;
END
$ownership$;

GRANT EXECUTE ON FUNCTION public.product_definition_debit_card_runtime_allowed() TO firefly_product_definition_debit_card_read;
GRANT EXECUTE ON FUNCTION public.product_definition_debit_card_read_matches(uuid,uuid,oid,uuid) TO firefly_product_definition_debit_card_read;
GRANT EXECUTE ON FUNCTION public.open_product_definition_debit_card_read(uuid,uuid,uuid,uuid,uuid,text,text,bigint,bigint) TO firefly_product_definition_debit_card_read;
GRANT EXECUTE ON FUNCTION public.assert_product_definition_debit_card_read_live(uuid) TO firefly_product_definition_debit_card_read;

GRANT SELECT (root_id,tenant_id,product_id,product_code,business_area,profile_code,profile_version) ON public.product_definition_root TO firefly_product_definition_debit_card_read;
GRANT SELECT (revision_id,tenant_id,root_id,canonical_definition,public_digest,encoding_version) ON public.product_definition_revision TO firefly_product_definition_debit_card_read;
GRANT SELECT (publication_id,tenant_id,root_id,revision_id,product_version_id,canonical_definition,public_digest,encoding_version,operational_admission,created_at) ON public.product_definition_publication TO firefly_product_definition_debit_card_read;
GRANT SELECT (publication_id,tenant_id,root_id,state,state_version) ON public.product_definition_publication_control TO firefly_product_definition_debit_card_read;
GRANT SELECT (product_version_id,tenant_id,root_id,product_id,publication_id,version_number,recorded_at) ON public.product_definition_version_identity TO firefly_product_definition_debit_card_read;

CREATE POLICY debit_card_purpose_select ON public.product_definition_root FOR SELECT TO firefly_product_definition_debit_card_read
 USING (public.product_definition_debit_card_read_matches(tenant_id,root_id,'public.product_definition_root'::REGCLASS,root_id) AND business_area='BANKING' AND profile_code='DEBIT_CARD_V1' AND profile_version=1 AND product_id::TEXT=pg_catalog.current_setting('firefly.product_definition.debit_card_read_product',TRUE));

CREATE POLICY debit_card_purpose_select ON public.product_definition_revision FOR SELECT TO firefly_product_definition_debit_card_read
 USING (public.product_definition_debit_card_read_matches(tenant_id,root_id,'public.product_definition_revision'::REGCLASS,revision_id));

CREATE POLICY debit_card_purpose_select ON public.product_definition_publication FOR SELECT TO firefly_product_definition_debit_card_read
 USING (public.product_definition_debit_card_read_matches(tenant_id,root_id,'public.product_definition_publication'::REGCLASS,publication_id));

CREATE POLICY debit_card_purpose_select ON public.product_definition_publication_control FOR SELECT TO firefly_product_definition_debit_card_read
 USING (public.product_definition_debit_card_read_matches(tenant_id,root_id,'public.product_definition_publication_control'::REGCLASS,publication_id));

CREATE POLICY debit_card_purpose_select ON public.product_definition_version_identity FOR SELECT TO firefly_product_definition_debit_card_read
 USING (public.product_definition_debit_card_read_matches(tenant_id,root_id,'public.product_definition_version_identity'::REGCLASS,product_version_id) AND product_id::TEXT=pg_catalog.current_setting('firefly.product_definition.debit_card_read_product',TRUE));
