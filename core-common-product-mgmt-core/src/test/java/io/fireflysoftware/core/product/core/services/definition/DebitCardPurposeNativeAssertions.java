package io.fireflysoftware.core.product.core.services.definition;

import static org.assertj.core.api.Assertions.*;
import io.fireflysoftware.core.product.core.services.definition.impl.DebitCardPurposeReadStoreImpl;
import io.fireflysoftware.core.product.interfaces.dtos.definition.DebitCardPurposeContracts.Scope;
import io.fireflysoftware.core.product.models.repositories.definition.purpose.DebitCardPurposeReadStore.Admission;
import io.fireflysoftware.core.product.models.repositories.definition.purpose.impl.DebitCardPurposeTransactionImpl;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactoryOptions;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.data.r2dbc.convert.*;
import org.springframework.data.r2dbc.dialect.PostgresDialect;
import org.springframework.data.r2dbc.mapping.R2dbcMappingContext;

/** Real restricted PostgreSQL reads; caller authorization and remote reference observation are separate. */
final class DebitCardPurposeNativeAssertions {
    private DebitCardPurposeNativeAssertions() {}
    static void read(ProductDefinitionCommandDatabaseFixture.Settings settings, Connection owner, Scope scope) throws Exception {
        String login="debit_read_"+UUID.randomUUID().toString().replace("-","");String password=UUID.randomUUID().toString();
        try(var statement=owner.createStatement()) {
            statement.execute("CREATE ROLE "+login+" LOGIN NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE NOREPLICATION PASSWORD '"+password+"'");
            statement.execute("GRANT firefly_product_definition_debit_card_read TO "+login+" WITH INHERIT TRUE, SET FALSE, ADMIN FALSE");
            statement.execute("GRANT CONNECT ON DATABASE "+settings.database()+" TO "+login);
            statement.execute("GRANT USAGE ON SCHEMA public TO "+login);
        }
        var before=snapshot(owner);var options=ConnectionFactoryOptions.builder().option(ConnectionFactoryOptions.DRIVER,"postgresql")
                .option(ConnectionFactoryOptions.HOST,settings.host()).option(ConnectionFactoryOptions.PORT,settings.port())
                .option(ConnectionFactoryOptions.DATABASE,settings.database()).option(ConnectionFactoryOptions.USER,login).option(ConnectionFactoryOptions.PASSWORD,password).build();
        var conversions=R2dbcCustomConversions.of(PostgresDialect.INSTANCE);var mapping=new R2dbcMappingContext();mapping.setSimpleTypeHolder(conversions.getSimpleTypeHolder());mapping.afterPropertiesSet();
        var converter=new MappingR2dbcConverter(mapping,conversions);
        try(var application=new GenericApplicationContext()) {
            application.refresh();var transaction=new DebitCardPurposeTransactionImpl(ConnectionFactories.get(options),converter,application,Clock.systemUTC(),System::nanoTime);
            var store=new DebitCardPurposeReadStoreImpl(transaction,application,Clock.systemUTC(),System::nanoTime);
            var read=store.read(admission(scope)).block(Duration.ofSeconds(10));assertThat(read).isNotNull();
            assertThat(read.configuration().scope()).isEqualTo(scope);assertThat(read.configuration().definition().terms().cardKind()).isEqualTo("DEBIT");
            assertThat(read.window().until()).isAfter(read.window().recordedAt());assertThat(snapshot(owner)).isEqualTo(before);
            var wrong=new Scope(UUID.randomUUID(),scope.rootId(),scope.productId(),scope.revisionId(),scope.publicationId());
            assertThatThrownBy(()->store.read(admission(wrong)).block(Duration.ofSeconds(10))).isInstanceOf(IllegalStateException.class);
            try(var connection=DriverManager.getConnection("jdbc:postgresql://"+settings.host()+":"+settings.port()+"/"+settings.database(),login,password);var statement=connection.createStatement()) {
                try(var rows=statement.executeQuery("SELECT root_id FROM public.product_definition_root")){assertThat(rows.next()).isFalse();}
                assertThatThrownBy(()->statement.executeQuery("SELECT canonical_cost FROM public.product_definition_cost_revision")).isInstanceOf(SQLException.class);
                assertThatThrownBy(()->statement.executeQuery("SELECT * FROM public.product_definition_action")).isInstanceOf(SQLException.class);
            }
            try(var statement=owner.createStatement()){statement.execute("GRANT SELECT ON public.product_definition_cost_revision TO "+login);}
            assertThatThrownBy(()->store.read(admission(scope)).block(Duration.ofSeconds(10))).isInstanceOf(IllegalStateException.class);
            assertThat(snapshot(owner)).isEqualTo(before);
        }
    }
    private static Admission admission(Scope scope){var now=Instant.now();return new Admission(scope,"https://owned-debit-reader.example.test","cards-owner",now.plusSeconds(30),now.plusSeconds(5),now,System.nanoTime());}
    private static Map<String,String> snapshot(Connection owner)throws Exception {
        var result=new TreeMap<String,String>();
        for(String table:ProductDefinitionDatabaseTest.NATIVE_TABLES){try(var statement=owner.createStatement();var rows=statement.executeQuery("SELECT coalesce(jsonb_agg(to_jsonb(t) ORDER BY to_jsonb(t)::text),'[]'::jsonb)::text FROM public."+table+" t")){assertThat(rows.next()).isTrue();result.put(table,rows.getString(1));}}
        return Map.copyOf(result);
    }
}
