package io.fireflysoftware.core.product.web.config.definition.lifecycle;

import io.fireflysoftware.core.product.core.services.definition.InsuranceQuotePurposeService;
import io.r2dbc.pool.ConnectionPool;
import java.util.Objects;

public final class InsuranceQuotePurposeRuntime implements AutoCloseable {
    private final ConnectionPool pool;private final InsuranceQuotePurposeService service;
    public InsuranceQuotePurposeRuntime(ConnectionPool pool,InsuranceQuotePurposeService service){this.pool=Objects.requireNonNull(pool);this.service=Objects.requireNonNull(service);}
    public InsuranceQuotePurposeService service(){return service;}
    @Override public void close(){pool.dispose();}
    @Override public String toString(){return "InsuranceQuotePurposeRuntime";}
}
