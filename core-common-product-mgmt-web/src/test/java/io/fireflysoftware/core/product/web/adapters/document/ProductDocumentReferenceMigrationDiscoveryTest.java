package io.fireflysoftware.core.product.web.adapters.document;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.boot.env.YamlPropertySourceLoader;

/** Actual Spring classpath discovery only; no Flyway installation or native catalogue is implied. */
class ProductDocumentReferenceMigrationDiscoveryTest {
    private static final Map<String,String> PUBLISHED=Map.ofEntries(
        Map.entry("V10__Create_product_configuration_table.sql","faa5d34930345c37a83def5d67b3eb86009b9c87d1ac0d82deb1d7154c3988ad"),
        Map.entry("V11__Add_tenant_id_to_product.sql","529b3ade75f4cff8425d62aa15c9cd878daf65d872f8985c7060f5031ada5e58"),
        Map.entry("V12__Seed_Demo_Lending_Products_Configuration.sql","2fc88cbed86601728da493d2723209d371f16e971bba87d49aed35b788fb0cc2"),
        Map.entry("V13__Add_Marketing_Features_To_Product.sql","b6a3be198f0ed960cb8d75c7d455f45b31aa320d88cb7a93126735e8d5b124b4"),
        Map.entry("V14__Add_jsonb_implicit_cast.sql","6e1dc0be4b578973170d4f93481c373951891a5cfd07f0bb38f7f28c995869ed"),
        Map.entry("V15__Add_draft_product_status.sql","d90271392c09b40d4b7e1a1e658da29a180eb63c97e4f0fba975c222ae101b6b"),
        Map.entry("V16__Cascade_product_children.sql","605906eb4b554602bc8a2cd2604aca4ae34fe2c24bed6c64a8d2e4a7e8119967"),
        Map.entry("V17__Create_Casts_Product_Config_Type.sql","8a25491693c6085d513ebf834e3e5e5b2556a0f730627a652acd90a0ebfa181e"),
        Map.entry("V18__Create_governed_product_definitions.sql","a2a4f0c90fa06be935ffcb1ac1d9c5fc076d5fae42c1613b7f85322aa2ee4076"),
        Map.entry("V19__Scoped_account_tariff_serving_reads.sql","89cecedce7d6b7e849d387939d54f7089d9cb59cc87f4919d0dba253492bce75"),
        Map.entry("V1__Create_enums.sql","8779cbe3d07d2838974306608c2ef916b81bf35bddde5eaed9179e46a84380e9"),
        Map.entry("V20__Admit_governed_account_tariff_lifecycle.sql","3f9f51b61d3e7ab036f67a658d9ffe4dd27c30ef7b9a867840c53a6b916b9639"),
        Map.entry("V21__Scoped_product_publication_metadata.sql","a98036ebcbc60d78c3c96608f744adbd9e566b788acbd8cb24e47bcd42cbd2b9"),
        Map.entry("V2__Create_tables.sql","5e56e83eb04eafdbd40e5aa0fa14ce183e94765f707b2d89cdc1500d79de6260"),
        Map.entry("V3__Create_casts.sql","3fed50d14d8b8ccf1c590a8a9764e0405453f22c5c3b2e885f4d7d543cab997e"),
        Map.entry("V4__Add_contracting_doc_type.sql","070c2a150d563515e1c49ca8cada5376a31137bc2897e01ca0533948dc693aa7"),
        Map.entry("V5__Create_Casts_Contracting_Doc_Type.sql","4a0c1c88c29ba5f91325d91d21b0cabfadbee66f478af3b9a7849de1d4fe1bd7"),
        Map.entry("V6__Change_version_number_to_long.sql","7a2ec5d3bfc2a9e570bc1d21011bb769fb739df4837fc3e17f22c3c07d8d5bd0"),
        Map.entry("V7__Remove_lifecycle_fees_features_pricing_limit.sql","c927a7c0e2383afd005c9ca35eaddf964d1838cf055e90066621ed75802b7bfd"),
        Map.entry("V8__Refactor_category_hierarchy.sql","718ce0980d4514d3649bc3467266ef10bd7d3a2752d7696de4202ad5ca657c12"),
        Map.entry("V9__Remove_product_bundle.sql","e9375f9d474bb588e38a8be42c5702da222ac92e1f2c9a41ec11b0ef481b6ec2"));
    private static final Map<String,String> STAGED=Map.ofEntries(
        Map.entry("V22__Governed_term_pc_purpose_read.sql","73dc0e5b49aebb3ce0dbb6d4a52a6fb8b7cdfaa382284d98566d424b5f79ccf7"),
        Map.entry("V23__Governed_deposit_enrollment_purpose.sql","9d86ed9e72378bb86fb0a47abac8195a7ad0656105026fba5d067ba437f919ce"));
    private static String digest(byte[] value)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));}
    @Test void defaultOwnerClasspathDiscoversTheExactPublishedV1ThroughV21Bytes()throws Exception {
        var source=new YamlPropertySourceLoader().load("actual-owner",new ClassPathResource("application.yaml")).getFirst();
        assertEquals("classpath:db/migration",source.getProperty("spring.flyway.locations"));
        var resources=new PathMatchingResourcePatternResolver().getResources("classpath*:db/migration/V*__*.sql");
        var actual=new TreeMap<String,String>();for(var resource:resources){var previous=actual.put(resource.getFilename(),digest(resource.getContentAsByteArray()));assertNull(previous,"Duplicate default migration identity");}
        assertEquals(PUBLISHED,actual,"Unpublished purpose extensions must not become default application migrations");
    }
    @Test void unpublishedPurposeSqlIsBytePreservedOutsideRuntimeMigrationDiscovery()throws Exception {
        var root=Path.of(System.getProperty("product.reference.source-root",
            System.getProperty("maven.multiModuleProjectDirectory",System.getProperty("user.dir")))).toAbsolutePath();
        if(!Files.isDirectory(root.resolve("ops/database/purpose-activation"))
                && Files.isDirectory(root.resolve("../ops/database/purpose-activation")))root=root.getParent();
        for(var entry:STAGED.entrySet()){
            var file=root.resolve("ops/database/purpose-activation").resolve(entry.getKey());
            assertTrue(Files.isRegularFile(file),"The staged source migration must remain available outside runtime discovery");
            assertEquals(entry.getValue(),digest(Files.readAllBytes(file)));
            assertEquals(0,new PathMatchingResourcePatternResolver().getResources("classpath*:db/migration/"+entry.getKey()).length);
        }
    }
}
