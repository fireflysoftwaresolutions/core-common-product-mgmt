/*
 * Copyright 2026 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.fireflysoftware.core.product.core.services.definition;
import static org.junit.jupiter.api.Assertions.*;
import io.fireflysoftware.core.product.interfaces.dtos.definition.InsuranceQuotePurposeContracts.*;
import io.fireflysoftware.core.product.models.repositories.definition.purpose.InsuranceQuotePurposeRows.*;
import java.lang.reflect.*;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Supplied mapped rows only; protected owner/native/publication admission is qualified separately. */
class InsuranceQuotePurposeProjectionTest {
    private final UUID tenant=new UUID(0,1),root=new UUID(0,2),product=new UUID(0,3),revision=new UUID(0,4),publication=new UUID(0,5),version=new UUID(0,6);
    private final Scope scope=new Scope(tenant,root,product,revision,publication);
    private final OffsetDateTime time=OffsetDateTime.parse("2026-10-26T12:00:00.123456Z");
    private String canonical(){return DefinitionProfiles.canonical(ProfileFixtures.load("insurance").toString()).json();}
    private String digest(){return DefinitionProfiles.canonical(canonical()).digest();}
    private Root header(){return new Root(root,tenant,product,"TERM_PC_INSURANCE_V1_DEMO","INSURANCE","TERM_PC_INSURANCE_V1",1);}
    private Revision revision(){return new Revision(revision,tenant,root,canonical(),digest(),DefinitionCanonical.ENCODING);}
    private Publication publication(){return new Publication(publication,tenant,root,revision,version,canonical(),digest(),DefinitionCanonical.ENCODING,"NOT_ADMITTED",time);}
    private Control control(){return new Control(publication,tenant,root,"ACTIVE",0);}
    private Version latest(){return new Version(version,tenant,root,product,publication,1L,time);}
    private Configuration project(Root r,Revision v,Publication p,Control c,Version i){try{return (Configuration)Class.forName("io.fireflysoftware.core.product.core.services.definition.impl.InsuranceQuotePurposeProjection").getMethod("project",Scope.class,Root.class,Revision.class,Publication.class,Control.class,Version.class).invoke(null,scope,r,v,p,c,i);}catch(ClassNotFoundException missing){throw new AssertionError("Mapped insurance-purpose graph projection missing",missing);}catch(InvocationTargetException e){if(e.getCause() instanceof RuntimeException x)throw x;throw new AssertionError(e.getCause());}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    @Test void completePublicGraphRetainsExactCanonicalFixedTerms(){var c=project(header(),revision(),publication(),control(),latest());assertEquals(scope,c.scope());assertEquals(digest(),c.publicDigest());assertEquals("FIXED_PER_POLICY",c.definition().terms().premiumBasis());assertEquals("11000",c.definition().terms().totalPremium().minorUnits());}
    @Test void wrongTenantOrProductCannotEnterTheGraph(){var r=header();assertThrows(IllegalStateException.class,()->project(new Root(r.rootId(),new UUID(0,99),r.productId(),r.productCode(),r.businessArea(),r.profileCode(),r.profileVersion()),revision(),publication(),control(),latest()));var i=latest();assertThrows(IllegalStateException.class,()->project(header(),revision(),publication(),control(),new Version(i.productVersionId(),i.tenantId(),i.rootId(),new UUID(0,99),i.publicationId(),i.versionNumber(),i.recordedAt())));}
    @Test void retainedOlderPublicationIsNotTheSelectedLatestIdentity(){var i=latest();assertThrows(IllegalStateException.class,()->project(header(),revision(),publication(),control(),new Version(i.productVersionId(),i.tenantId(),i.rootId(),i.productId(),new UUID(0,99),2L,i.recordedAt())));}
    @Test void withdrawnOrUnobservedControlCannotBecomeCurrent(){assertThrows(IllegalStateException.class,()->project(header(),revision(),publication(),new Control(publication,tenant,root,"WITHDRAWN",1),latest()));assertThrows(IllegalStateException.class,()->project(header(),revision(),publication(),null,latest()));}
    @Test void publicationAndRevisionMustHaveIdenticalCanonicalDefinition(){var p=publication();assertThrows(IllegalStateException.class,()->project(header(),revision(),new Publication(p.publicationId(),p.tenantId(),p.rootId(),p.revisionId(),p.productVersionId(),p.canonicalDefinition()+" ",p.publicDigest(),p.encodingVersion(),p.operationalAdmission(),p.createdAt()),control(),latest()));}
    @Test void unsupportedEncodingOrFinancialAdmissionClaimRefuses(){var p=publication();assertThrows(IllegalStateException.class,()->project(header(),revision(),new Publication(p.publicationId(),p.tenantId(),p.rootId(),p.revisionId(),p.productVersionId(),p.canonicalDefinition(),p.publicDigest(),"other",p.operationalAdmission(),p.createdAt()),control(),latest()));assertThrows(IllegalStateException.class,()->project(header(),revision(),new Publication(p.publicationId(),p.tenantId(),p.rootId(),p.revisionId(),p.productVersionId(),p.canonicalDefinition(),p.publicDigest(),p.encodingVersion(),"ADMITTED",p.createdAt()),control(),latest()));}
    @Test void exactVersionIdentityAndPublicationTimeMustAgree(){var i=latest();assertThrows(IllegalStateException.class,()->project(header(),revision(),publication(),control(),new Version(i.productVersionId(),i.tenantId(),i.rootId(),i.productId(),i.publicationId(),i.versionNumber(),i.recordedAt().plusNanos(1000))));}
    @Test void publicDigestCannotBeSuppliedIndependentlyOfCanonicalBytes(){var v=revision();assertThrows(IllegalStateException.class,()->project(header(),new Revision(v.revisionId(),v.tenantId(),v.rootId(),v.canonicalDefinition(),"0".repeat(64),v.encodingVersion()),publication(),control(),latest()));}
    @Test void bankingHeaderCannotMasqueradeAsInsurancePurpose(){var r=header();assertThrows(IllegalStateException.class,()->project(new Root(r.rootId(),r.tenantId(),r.productId(),r.productCode(),"BANKING","ACCOUNT_TARIFF_V1",1),revision(),publication(),control(),latest()));}
}
