package io.fireflysoftware.core.product.core.services.definition;

import static org.junit.jupiter.api.Assertions.*;
import io.fireflysoftware.core.product.core.services.definition.impl.ProductDefinitionCommandAuthorizationImpl;
import io.fireflysoftware.core.product.interfaces.dtos.definition.*;
import io.fireflysoftware.core.product.interfaces.dtos.definition.ProductDefinitionLifecycleMessages.Parsed;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.fireflyframework.security.api.domain.Decision;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.fireflyframework.security.spi.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/** Supplied source fixtures and controlled PDP, not native/catalog/financial acceptance. */
class TermPcLifecycleProfileTest {
    static final UUID TENANT=new UUID(0,1),ROOT=new UUID(0,2);static final Instant NOW=Instant.parse("2026-10-26T12:00:00Z");
    ObjectNode body(ProductDefinitionCommand command,String family){
        var node=new ProductDefinitionInstructionCodecTest().body(command);var definition=ProfileFixtures.load(family);var common=definition.get("common");
        if(command==ProductDefinitionCommand.CREATE_DRAFT || command==ProductDefinitionCommand.EDIT_PUBLIC)node.set("definition",definition);
        if(command==ProductDefinitionCommand.EDIT_COST){node.set("publicDefinition",definition);node.put("publicDigest",DefinitionProfiles.canonical(definition.toString()).digest());}
        if(command==ProductDefinitionCommand.CREATE_DRAFT || command==ProductDefinitionCommand.EDIT_COST){var cost=(ObjectNode)node.get("cost");cost.put("businessArea",common.get("businessArea").asText());cost.put("profileCode",common.get("profileCode").asText());}
        return node;
    }
    Parsed parse(ProductDefinitionCommand command,String body,GovernedDefinitionProfile backend){try{return (Parsed)ProductDefinitionInstructionCodec.class.getMethod("parse",UUID.class,UUID.class,ProductDefinitionCommand.class,String.class,GovernedDefinitionProfile.class).invoke(null,TENANT,ROOT,command,body,backend);}catch(InvocationTargetException e){if(e.getCause() instanceof RuntimeException x)throw x;throw new AssertionError(e.getCause());}catch(ReflectiveOperationException e){throw new AssertionError("Explicit backend profile parser missing",e);}}
    @Test void termPcBackendParsesAllEightClosedCommandsAndRetainsOriginalIntent(){for(var c:ProductDefinitionCommand.values()){var request=body(c,"insurance");var p=parse(c,request.toString(),GovernedDefinitionProfile.TERM_PC_INSURANCE);assertEquals(c,p.instruction().command());assertEquals(request,DefinitionCanonical.parse(p.canonicalIntent()).get("request"));assertEquals(p.intentDigest(),DefinitionCanonical.intentDigest(DefinitionCanonical.parse(p.canonicalIntent())));}}
    @Test void originalBankingEntryPointStillRejectsInsuranceBeforeAuthority(){assertThrows(IllegalArgumentException.class,()->ProductDefinitionInstructionCodec.parse(TENANT,ROOT,ProductDefinitionCommand.CREATE_DRAFT,body(ProductDefinitionCommand.CREATE_DRAFT,"insurance").toString()));}
    @Test void fixedTermPcBackendRejectsBankingSnapshot(){assertThrows(IllegalArgumentException.class,()->parse(ProductDefinitionCommand.CREATE_DRAFT,body(ProductDefinitionCommand.CREATE_DRAFT,"account").toString(),GovernedDefinitionProfile.TERM_PC_INSURANCE));}
    @Test void callerCannotSupplyBackendAuthorityProfile(){var n=body(ProductDefinitionCommand.SUBMIT_PUBLIC,"insurance");n.put("authorityProfile",GovernedDefinitionProfile.TERM_PC_INSURANCE.policyProfile());assertThrows(IllegalArgumentException.class,()->parse(ProductDefinitionCommand.SUBMIT_PUBLIC,n.toString(),GovernedDefinitionProfile.TERM_PC_INSURANCE));}
    @Test void missingBackendCannotSelectProfileFromPayload(){assertThrows(IllegalArgumentException.class,()->parse(ProductDefinitionCommand.CREATE_DRAFT,body(ProductDefinitionCommand.CREATE_DRAFT,"insurance").toString(),null));}
    SecurityPrincipal principal(){return SecurityPrincipal.builder().subject("staff").issuer("https://issuer.example").tenantId(TENANT.toString()).scopes(Set.of("product:definitions:write","product:definitions:cost:write","product:definitions:cost:read","product:definitions:review","product:definitions:publish","product:definitions:withdraw")).claims(Map.of("exp",NOW.plusSeconds(60))).build();}
    ProductDefinitionCommandAuthorizationImpl authority(String policy,GovernedDefinitionProfile backend,PolicyDecisionPort pdp){try{return ProductDefinitionCommandAuthorizationImpl.class.getConstructor(SecurityContextPort.class,PolicyDecisionPort.class,Clock.class,Set.class,String.class,GovernedDefinitionProfile.class).newInstance((SecurityContextPort)()->Mono.just(principal()),pdp,Clock.fixed(NOW,ZoneOffset.UTC),Set.of("https://issuer.example"),policy,backend);}catch(InvocationTargetException e){if(e.getCause() instanceof RuntimeException x)throw x;throw new AssertionError(e.getCause());}catch(ReflectiveOperationException e){throw new AssertionError("Explicit backend authorization binding missing",e);}}
    ProductDefinitionCommandAuthorization.Scope scope(ProductDefinitionCommand c){return new ProductDefinitionCommandAuthorization.Scope(TENANT,ROOT,new UUID(0,3),c,c.target()==null?null:new UUID(0,4));}
    @Test void currentTermPcPdpReceivesBackendScopeForEveryCommandAndReauthorization(){var calls=new AtomicInteger();var auth=authority(GovernedDefinitionProfile.TERM_PC_INSURANCE.policyProfile(),GovernedDefinitionProfile.TERM_PC_INSURANCE,(p,a,r,c)->{assertEquals("INSURANCE",c.get("businessArea"));assertEquals("TERM_PC_INSURANCE_V1",c.get("profileCode"));assertEquals(GovernedDefinitionProfile.TERM_PC_INSURANCE.policyProfile(),c.get("authorityProfile"));assertFalse(c.containsKey("principal_kind"));calls.incrementAndGet();return Mono.just(Decision.permit());});for(var c:ProductDefinitionCommand.values()){var admitted=auth.authorize(scope(c)).block();assertNotNull(admitted);auth.reauthorize(admitted).block();}assertEquals(16,calls.get());}
    @Test void configuredProfileMustMatchFixedBackendBeforePdp(){var calls=new AtomicInteger();var auth=authority(ProductDefinitionCommandAuthorization.POLICY_PROFILE,GovernedDefinitionProfile.TERM_PC_INSURANCE,(p,a,r,c)->{calls.incrementAndGet();return Mono.just(Decision.permit());});assertThrows(ResponseStatusException.class,()->auth.authorize(scope(ProductDefinitionCommand.SUBMIT_PUBLIC)).block());assertEquals(0,calls.get());}
    @Test void oldConstructorCannotActivateTermPcFromConfigurationString(){var calls=new AtomicInteger();var auth=new ProductDefinitionCommandAuthorizationImpl(()->Mono.just(principal()),(p,a,r,c)->{calls.incrementAndGet();return Mono.just(Decision.permit());},Clock.fixed(NOW,ZoneOffset.UTC),Set.of("https://issuer.example"),GovernedDefinitionProfile.TERM_PC_INSURANCE.policyProfile());assertThrows(ResponseStatusException.class,()->auth.authorize(scope(ProductDefinitionCommand.SUBMIT_PUBLIC)).block());assertEquals(0,calls.get());}
    @Test void termPcCurrentPolicyDenialNeverReturnsAdmission(){var auth=authority(GovernedDefinitionProfile.TERM_PC_INSURANCE.policyProfile(),GovernedDefinitionProfile.TERM_PC_INSURANCE,(p,a,r,c)->Mono.just(Decision.deny("CURRENT_DENIED")));assertThrows(ResponseStatusException.class,()->auth.authorize(scope(ProductDefinitionCommand.PUBLISH)).block());}
    @Test void termPcPrivateAndPublicationReadPurposesRetainExactCurrentPdpBinding(){
        var calls=new AtomicInteger();var auth=authority(GovernedDefinitionProfile.TERM_PC_INSURANCE.policyProfile(),GovernedDefinitionProfile.TERM_PC_INSURANCE,(p,a,r,c)->{
            assertEquals("INSURANCE",c.get("businessArea"));assertEquals("TERM_PC_INSURANCE_V1",c.get("profileCode"));assertEquals(GovernedDefinitionProfile.TERM_PC_INSURANCE.policyProfile(),c.get("authorityProfile"));calls.incrementAndGet();return Mono.just(Decision.permit());});
        var exact=auth.authorize(new ProductDefinitionReviewAuthorization.Scope(TENANT,ROOT,new UUID(0,9))).block();assertNotNull(exact);auth.reauthorize(exact).block();
        var current=auth.authorize(ProductDefinitionReviewAuthorization.Scope.current(TENANT,ROOT)).block();assertNotNull(current);auth.reauthorize(current).block();
        var cost=auth.authorize(new ProductDefinitionCostReadAuthorization.Scope(TENANT,ROOT)).block();assertNotNull(cost);auth.reauthorize(cost).block();
        for(var t:ProductDefinitionPublicationReadAuthorization.Target.values()){var view=auth.authorize(new ProductDefinitionPublicationReadAuthorization.Scope(TENANT,ROOT,t)).block();assertNotNull(view);auth.reauthorize(view).block();}assertEquals(10,calls.get());
    }
    @Test void termPcRetainedAdmissionCannotBypassASecondCurrentPolicyDenial(){var calls=new AtomicInteger();var auth=authority(GovernedDefinitionProfile.TERM_PC_INSURANCE.policyProfile(),GovernedDefinitionProfile.TERM_PC_INSURANCE,(p,a,r,c)->Mono.just(calls.incrementAndGet()==1?Decision.permit():Decision.deny("CURRENT_DENIED")));var admitted=auth.authorize(scope(ProductDefinitionCommand.PUBLISH)).block();assertNotNull(admitted);assertThrows(ResponseStatusException.class,()->auth.reauthorize(admitted).block());assertEquals(2,calls.get());}

}
