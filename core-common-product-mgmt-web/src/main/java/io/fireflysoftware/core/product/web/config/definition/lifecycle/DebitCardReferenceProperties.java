/* Copyright 2026 Firefly Software Foundation. Licensed under the Apache License, Version 2.0. */
package io.fireflysoftware.core.product.web.config.definition.lifecycle;

import io.fireflysoftware.core.product.interfaces.dtos.definition.DefinitionContracts.*;
import java.net.URI;
import java.util.*;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Server-owned candidate routes; they are not current owner observations or permissions. */
@ConfigurationProperties("product.definitions.debit-card-read.references")
public record DebitCardReferenceProperties(boolean enabled,URI configurationEndpoint,String configurationRegistrationId,
        URI mastersEndpoint,String mastersRegistrationId,List<CurrencySelection> currencies){
    public DebitCardReferenceProperties{currencies=currencies==null?List.of():List.copyOf(currencies);}
    public record CurrencySelection(CurrencyPolicyReference reference,Integer postedScale){}
    @Override public String toString(){return "DebitCardReferenceProperties";}
}
