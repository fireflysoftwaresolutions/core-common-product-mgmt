package io.fireflysoftware.core.product.web.openapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("openapi-gen")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SpringBootTest(classes=OpenApiGenApplication.class,webEnvironment=SpringBootTest.WebEnvironment.MOCK,properties={"spring.main.web-application-type=reactive","springdoc.api-docs.enabled=true","springdoc.api-docs.version=OPENAPI_3_0","firefly.openapi.instant-scalar.enabled=true","product.definitions.commands.enabled=true","product.definitions.publication-read.enabled=true","product.definitions.term-pc.enabled=true","product.definitions.deposit.enabled=true","product.definitions.insurance-quote-read.enabled=true"})
class InsuranceQuoteCanonicalProducerTest {
    @Autowired ApplicationContext context;
    record Operation(String path,String verb,String id) {}
    Set<Operation> operations(JsonNode doc){var result=new HashSet<Operation>();doc.path("paths").fields().forEachRemaining(path->path.getValue().fields().forEachRemaining(method->{if(Set.of("get","post","put","patch","delete","head","options","trace").contains(method.getKey()))assertTrue(result.add(new Operation(path.getKey(),method.getKey(),method.getValue().path("operationId").asText())));}));return result;}
    void schema(JsonNode doc){
        var operation=doc.path("paths").path("/api/v1/tenants/{tenantId}/product-definitions/{rootId}/insurance-quote-configurations/{productId}/revisions/{revisionId}/publications/{publicationId}").path("get");assertEquals("observe",operation.path("operationId").asText());assertTrue(operation.path("security").toString().contains("productDefinitionBearer"));
        var content=operation.path("responses").path("200").path("content");var mediaTypes=new TreeSet<String>();content.fieldNames().forEachRemaining(mediaTypes::add);assertEquals(Set.of("*/*"),mediaTypes);var ref=content.path("*/*").path("schema").path("$ref").asText();assertTrue(ref.startsWith("#/components/schemas/"));var response=doc.at(ref.substring(1));var property=response.path("properties").path("canonicalConfiguration");assertEquals("string",property.path("type").asText());assertEquals(131072,property.path("maxLength").asInt());response.path("required").forEach(value->assertNotEquals("canonicalConfiguration",value.asText()));
        assertEquals(13,doc.path("components").path("schemas").path("TermPcInsuranceTerms").path("properties").size());
    }
    @Test @Order(1) void actualFullOwnerProducerPreservesEveryOriginalOperationAndAddsOnlyTheRepresentation()throws Exception{
        var client=WebTestClient.bindToApplicationContext(context).configureClient().responseTimeout(Duration.ofSeconds(3)).build();byte[] json=client.get().uri("/v3/api-docs").exchange().expectStatus().isOk().expectBody().returnResult().getResponseBody();byte[] yaml=client.get().uri("/v3/api-docs.yaml").exchange().expectStatus().isOk().expectBody().returnResult().getResponseBody();assertNotNull(json);assertNotNull(yaml);assertTrue(json.length>0 && json.length<=4*1024*1024 && yaml.length>0 && yaml.length<=4*1024*1024);var actual=new ObjectMapper().readTree(json);assertEquals(actual,new ObjectMapper(new YAMLFactory()).readTree(yaml));Files.createDirectories(Path.of("target"));Files.write(Path.of("target/insurance-quote-owner-openapi.json"),json);Files.write(Path.of("target/insurance-quote-owner-openapi.yml"),yaml);var original=new ObjectMapper(new YAMLFactory()).readTree(Files.readAllBytes(Path.of("../core-common-product-mgmt-sdk/src/main/resources/openapi.yml")));assertFalse(operations(original).isEmpty());assertEquals(operations(original),operations(actual));schema(actual);
    }
    @Test @Order(2) void ownedWebToSdkBoundaryCopiesOnlyTheCompleteSameProducer()throws Exception{
        if(!Boolean.getBoolean("owned.insurance.product-sdk-boundary.enabled"))return;
        assertEquals("validated-hosted-manual-main-v1",System.getProperty("owned.insurance.product-sdk-boundary.hosted"));var root=Path.of(System.getProperty("owned.insurance.product-sdk-boundary.root")).normalize();assertTrue(root.isAbsolute());assertEquals(root,root.toRealPath());assertEquals(Path.of("..").toRealPath(),root);assertEquals(Path.of(System.getProperty("owned.insurance.product-sdk-boundary.workspace"),"qualification","insurance-product-source-chain","core-common-product-mgmt").toRealPath(),root);
        var canonical=root.resolve("core-common-product-mgmt-sdk/src/main/resources/openapi.yml");assertTrue(Files.isRegularFile(canonical,LinkOption.NOFOLLOW_LINKS));assertEquals(canonical,canonical.toRealPath());assertTrue(Files.size(canonical)<=4*1024*1024);var original=Files.readAllBytes(canonical);String expected=System.getProperty("owned.insurance.product-sdk-boundary.preimage-sha256");assertNotNull(expected);assertTrue(expected.matches("[0-9a-f]{64}"));assertEquals(expected,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(original)));
        var json=Files.readAllBytes(Path.of("target/insurance-quote-owner-openapi.json"));var yaml=Files.readAllBytes(Path.of("target/insurance-quote-owner-openapi.yml"));assertTrue(json.length>0 && json.length<=4*1024*1024 && yaml.length>0 && yaml.length<=4*1024*1024);var actual=new ObjectMapper().readTree(json);assertEquals(actual,new ObjectMapper(new YAMLFactory()).readTree(yaml));assertEquals(operations(new ObjectMapper(new YAMLFactory()).readTree(original)),operations(actual));schema(actual);Files.write(Path.of("target/insurance-quote-owner-sdk-preimage.yml"),original,StandardOpenOption.CREATE_NEW);Files.write(canonical,yaml,StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING);assertArrayEquals(yaml,Files.readAllBytes(canonical));
    }
}
