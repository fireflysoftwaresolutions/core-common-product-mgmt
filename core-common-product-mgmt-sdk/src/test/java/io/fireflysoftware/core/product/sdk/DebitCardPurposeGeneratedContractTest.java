package io.fireflysoftware.core.product.sdk;

import static org.junit.jupiter.api.Assertions.*;
import io.fireflysoftware.core.product.core.services.definition.DefinitionProfiles;
import io.fireflysoftware.core.product.core.services.definition.DebitCardPurposeCodec;
import io.fireflysoftware.core.product.sdk.invoker.ApiClient;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import reactor.core.publisher.Mono;

/** Original generated SDK against controlled transport; no signed Main or owner admission. */
class DebitCardPurposeGeneratedContractTest {
    private final com.fasterxml.jackson.databind.ObjectMapper mapper=ApiClient.createDefaultMapper(null);
    private final List<UUID> ids=List.of(new UUID(0,1),new UUID(0,2),new UUID(0,3),new UUID(0,4),new UUID(0,5));
    private final Instant observed=Instant.parse("2026-11-01T12:00:00.123456Z");
    private Class<?> observation()throws Exception{return Class.forName("io.fireflysoftware.core.product.sdk.model.DebitCardPurposeObservation");}
    private String body()throws Exception{
        var definition=mapper.readTree(Files.readString(Path.of("..","core-common-product-mgmt-core","src","test","resources","definition","v1","debit-card.json")));
        var scope=mapper.createObjectNode();var names=List.of("tenantId","rootId","productId","revisionId","publicationId");
        for(int i=0;i<names.size();i++)scope.put(names.get(i),ids.get(i).toString());
        var configuration=mapper.createObjectNode().set("scope",scope);
        ((com.fasterxml.jackson.databind.node.ObjectNode)configuration).put("publicDigest",DefinitionProfiles.canonical(definition.toString()).digest()).set("definition",definition);
        var canonical=((com.fasterxml.jackson.databind.node.ObjectNode)configuration).deepCopy().put("encodingVersion","product-debit-card-configuration-1");
        String exact=DebitCardPurposeCodec.encode(DebitCardPurposeCodec.decode(canonical.toString()));
        return mapper.writeValueAsString(Map.of("configuration",configuration,"canonicalConfiguration",exact,"observedAt",observed.toString(),"effectiveUntil",observed.plusSeconds(4).toString()));
    }
    @Test void generatedObservationPreservesExactCanonicalConfigurationAndInstantTypes()throws Exception{
        var type=observation();assertEquals(Instant.class,type.getMethod("getObservedAt").getReturnType());assertEquals(Instant.class,type.getMethod("getEffectiveUntil").getReturnType());
        var raw=body();var value=mapper.readValue(raw,type);assertEquals(observed,type.getMethod("getObservedAt").invoke(value));assertEquals(observed.plusSeconds(4),type.getMethod("getEffectiveUntil").invoke(value));
        var roundTrip=mapper.readTree(mapper.writeValueAsString(value));assertEquals(mapper.readTree(raw),roundTrip);
        var configuration=DebitCardPurposeCodec.decode(roundTrip.get("canonicalConfiguration").asText());assertEquals(ids.getFirst(),configuration.scope().tenantId());assertEquals("DEBIT_CARD_V1",configuration.definition().common().profileCode());
    }
    @Test void generatedObservationNeverInventsOffsetForInstant()throws Exception{
        var type=observation();var raw=body().replace("2026-11-01T12:00:00.123456Z","2026-11-01T12:00:00.123456");assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class,()->mapper.readValue(raw,type));
    }
    @Test void actualGeneratedOperationKeepsExactFiveOwnerIdsAndHasNoCommandBody()throws Exception{
        var provider=Path.of(ApiClient.class.getProtectionDomain().getCodeSource().getLocation().toURI());var apiNames=new ArrayList<String>();String prefix="io/fireflysoftware/core/product/sdk/api/";
        if(Files.isDirectory(provider)){try(var files=Files.list(provider.resolve(prefix))){files.filter(p->p.getFileName().toString().endsWith("Api.class")).forEach(p->apiNames.add(prefix+p.getFileName()));}}
        else try(var jar=new java.util.jar.JarFile(provider.toFile())){jar.stream().map(java.util.jar.JarEntry::getName).filter(n->n.startsWith(prefix)&&n.endsWith("Api.class")).forEach(apiNames::add);}
        var methods=new ArrayList<Method>();for(var name:apiNames)for(var method:Class.forName(name.replace('/', '.').replace(".class","")).getDeclaredMethods())if(method.getName().equals("observeCurrentDebitCardDefinition"))methods.add(method);
        assertEquals(1,methods.size());var method=methods.getFirst();assertArrayEquals(new Class<?>[]{UUID.class,UUID.class,UUID.class,UUID.class,UUID.class},method.getParameterTypes());
        var requests=new AtomicInteger();var raw=body();var format=ApiClient.createDefaultDateFormat();var exactMapper=ApiClient.createDefaultMapper(format);
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var failure=new AtomicReference<Throwable>();
        server.createContext("/",exchange->{
            try{
                requests.incrementAndGet();assertEquals("GET",exchange.getRequestMethod());
                assertEquals("/api/v1/tenants/"+ids.get(0)+"/product-definitions/"+ids.get(1)+"/debit-card-configurations/"+ids.get(2)+"/revisions/"+ids.get(3)+"/publications/"+ids.get(4),exchange.getRequestURI().getRawPath());
                assertNull(exchange.getRequestURI().getRawQuery());assertEquals("Bearer synthetic-card-purpose",exchange.getRequestHeaders().getFirst(HttpHeaders.AUTHORIZATION));
                assertFalse(exchange.getRequestHeaders().containsKey("Idempotency-Key"));assertEquals(0,exchange.getRequestBody().readAllBytes().length);
                byte[] response=raw.getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set(HttpHeaders.CONTENT_TYPE,MediaType.APPLICATION_JSON_VALUE);
                exchange.sendResponseHeaders(200,response.length);exchange.getResponseBody().write(response);
            }catch(Throwable error){failure.set(error);}finally{exchange.close();}
        });
        server.start();
        try{
            var web=ApiClient.buildWebClientBuilder(exactMapper).build();
            var client=new ApiClient(web,exactMapper,format).setBasePath("http://127.0.0.1:"+server.getAddress().getPort());
            var auth=client.getAuthentication("productDefinitionBearer");assertNotNull(auth);auth.getClass().getMethod("setBearerToken",String.class).invoke(auth,"synthetic-card-purpose");
            var api=method.getDeclaringClass().getConstructor(ApiClient.class).newInstance(client);
            var value=((Mono<?>)method.invoke(api,ids.toArray())).block(Duration.ofSeconds(5));
            assertNull(failure.get());assertNotNull(value);assertEquals(observation(),value.getClass());
            assertEquals(observed,value.getClass().getMethod("getObservedAt").invoke(value));
            assertEquals(observed.plusSeconds(4),value.getClass().getMethod("getEffectiveUntil").invoke(value));
            assertEquals(mapper.readTree(raw),mapper.readTree(mapper.writeValueAsString(value)));assertEquals(1,requests.get());
        }finally{server.stop(0);}
    }
}
