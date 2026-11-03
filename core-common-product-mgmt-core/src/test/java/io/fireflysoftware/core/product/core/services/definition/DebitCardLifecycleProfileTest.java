/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.product.core.services.definition;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.fireflysoftware.core.product.interfaces.dtos.definition.ProductDefinitionCommand;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.fireflyframework.security.api.domain.Decision;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

class DebitCardLifecycleProfileTest {
    static final UUID TENANT=new UUID(0,1),ROOT=new UUID(0,2);
    static ObjectNode body(ProductDefinitionCommand command) {
        var body=new TermPcLifecycleProfileTest().body(command,"account");var definition=DebitCardProfileTest.debit();
        if(command==ProductDefinitionCommand.CREATE_DRAFT||command==ProductDefinitionCommand.EDIT_PUBLIC)body.set("definition",definition);
        if(command==ProductDefinitionCommand.EDIT_COST){body.set("publicDefinition",definition);body.put("publicDigest",DefinitionProfiles.canonical(definition.toString()).digest());}
        if(command==ProductDefinitionCommand.CREATE_DRAFT||command==ProductDefinitionCommand.EDIT_COST){var cost=(ObjectNode)body.get("cost");cost.put("businessArea","BANKING");cost.put("profileCode","DEBIT_CARD_V1");}
        return body;
    }
    @Test void allEightDebitCommandsRetainClosedOriginalIntentAndExactProfile() {
        for(var command:ProductDefinitionCommand.values()){
            var request=body(command);var parsed=ProductDefinitionInstructionCodec.parse(TENANT,ROOT,command,request.toString(),GovernedDefinitionProfile.DEBIT_CARD);
            assertEquals(command,parsed.instruction().command());assertEquals(request,DefinitionCanonical.parse(parsed.canonicalIntent()).get("request"));
            assertEquals(parsed.intentDigest(),DefinitionCanonical.intentDigest(DefinitionCanonical.parse(parsed.canonicalIntent())));
        }
    }
    @Test void privateCostsCannotSilentlyRetainAnotherProductProfile() {
        var request=body(ProductDefinitionCommand.CREATE_DRAFT);assertNotNull(ProductDefinitionInstructionCodec.parse(TENANT,ROOT,ProductDefinitionCommand.CREATE_DRAFT,request.toString(),GovernedDefinitionProfile.DEBIT_CARD));
        ((ObjectNode)request.get("cost")).put("businessArea","INSURANCE").put("profileCode","TERM_PC_INSURANCE_V1");
        assertThrows(IllegalArgumentException.class,()->ProductDefinitionInstructionCodec.parse(TENANT,ROOT,ProductDefinitionCommand.CREATE_DRAFT,request.toString(),GovernedDefinitionProfile.DEBIT_CARD));
    }
    @Test void currentDebitPolicyReceivesExactBackendForEveryCommandAndResponse() {
        var fixture=new TermPcLifecycleProfileTest();var calls=new AtomicInteger();
        var authority=fixture.authority(GovernedDefinitionProfile.DEBIT_CARD.policyProfile(),GovernedDefinitionProfile.DEBIT_CARD,(p,a,r,context)->{
            assertEquals("BANKING",context.get("businessArea"));assertEquals("DEBIT_CARD_V1",context.get("profileCode"));assertEquals("product-definition-debit-card-staff-v1",context.get("authorityProfile"));calls.incrementAndGet();return Mono.just(Decision.permit());});
        for(var command:ProductDefinitionCommand.values()){var admitted=authority.authorize(fixture.scope(command)).block();assertNotNull(admitted);authority.reauthorize(admitted).block();}assertEquals(16,calls.get());
    }
    @Test void callerProfileInjectionAndCurrentPolicyWithdrawalCannotAdmitDebit() {
        var request=body(ProductDefinitionCommand.CREATE_DRAFT);request.put("authorityProfile",GovernedDefinitionProfile.DEBIT_CARD.policyProfile());
        assertThrows(IllegalArgumentException.class,()->ProductDefinitionInstructionCodec.parse(TENANT,ROOT,ProductDefinitionCommand.CREATE_DRAFT,request.toString(),GovernedDefinitionProfile.DEBIT_CARD));
        var fixture=new TermPcLifecycleProfileTest();var calls=new AtomicInteger();
        var authority=fixture.authority(GovernedDefinitionProfile.DEBIT_CARD.policyProfile(),GovernedDefinitionProfile.DEBIT_CARD,(p,a,r,c)->Mono.just(calls.incrementAndGet()==1?Decision.permit():Decision.deny("CURRENT_DENIED")));
        var admission=authority.authorize(fixture.scope(ProductDefinitionCommand.PUBLISH)).block();assertNotNull(admission);assertThrows(ResponseStatusException.class,()->authority.reauthorize(admission).block());assertEquals(2,calls.get());
    }
}
