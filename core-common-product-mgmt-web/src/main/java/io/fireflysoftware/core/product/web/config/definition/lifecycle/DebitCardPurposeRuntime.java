package io.fireflysoftware.core.product.web.config.definition.lifecycle;

import io.fireflysoftware.core.product.core.services.definition.DebitCardPurposeService;
import io.r2dbc.pool.ConnectionPool;
import java.util.Objects;

public final class DebitCardPurposeRuntime implements AutoCloseable {
    private final ConnectionPool pool;private final DebitCardPurposeService service;
    public DebitCardPurposeRuntime(ConnectionPool pool,DebitCardPurposeService service){this.pool=Objects.requireNonNull(pool);this.service=Objects.requireNonNull(service);}
    public DebitCardPurposeService service(){return service;}
    @Override public void close(){pool.dispose();}
    @Override public String toString(){return "DebitCardPurposeRuntime";}
}
