package io.fireflysoftware.core.product.web.config.definition.lifecycle;

import io.fireflysoftware.core.product.core.services.definition.*;
import io.fireflysoftware.core.product.core.services.definition.impl.*;
import io.fireflysoftware.core.product.models.repositories.definition.lifecycle.ProductDefinitionCommandPersistence;
import java.time.Clock;
import java.util.Objects;
import java.util.function.LongSupplier;

/** Owns the explicit bounded catalog services without adding ambiguous legacy service beans. */
public final class DepositAccountRuntime implements AutoCloseable {
    private final ProductDefinitionCommandPersistence persistence;
    private final ProductDefinitionLifecycleService commands;
    private final ProductDefinitionCostReadService costs;
    private final ProductDefinitionReviewReadService reviews;
    public DepositAccountRuntime(ProductDefinitionCommandPersistence persistence,ProductDefinitionCommandAuthorizationImpl authority,Clock clock,LongSupplier nanos){
        this.persistence=Objects.requireNonNull(persistence);var backend=GovernedDefinitionProfile.DEPOSIT_ACCOUNT;
        commands=new ProductDefinitionLifecycleServiceImpl(authority,persistence,backend,clock,nanos);
        costs=new ProductDefinitionCostReadServiceImpl(authority,persistence,backend,clock,nanos);
        reviews=new ProductDefinitionReviewReadServiceImpl(authority,persistence,backend,clock,nanos);
    }
    public ProductDefinitionLifecycleService commands(){return commands;}
    public ProductDefinitionCostReadService costs(){return costs;}
    public ProductDefinitionReviewReadService reviews(){return reviews;}
    @Override public void close(){persistence.close();}
    @Override public String toString(){return "DepositAccountRuntime";}
}
