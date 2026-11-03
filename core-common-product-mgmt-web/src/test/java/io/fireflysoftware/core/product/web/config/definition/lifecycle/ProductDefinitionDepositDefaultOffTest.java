package io.fireflysoftware.core.product.web.config.definition.lifecycle;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.*;
import java.util.*;
import org.fireflyframework.security.rs.ResourceServerProperties;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

/** Real DI/credential boundary only; no database, live policy, or reference-owner acceptance. */
class ProductDefinitionDepositDefaultOffTest {
    Class<?> type(String suffix){try{return Class.forName("io.fireflysoftware.core.product.web.config.definition.lifecycle.DepositAccount"+suffix);}catch(ClassNotFoundException missing){throw new AssertionError("Default-off deposit owner wiring missing",missing);}}
    @Test void absentFlagDoesNotCreatePoolOrRuntime(){try(var context=new AnnotationConfigApplicationContext()){context.register(type("Configuration"));context.refresh();assertEquals(0,context.getBeanNamesForType(type("Runtime")).length);}}
    @Test void explicitFalseNeedsNoCredentials(){try(var context=new AnnotationConfigApplicationContext()){context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("controlled",Map.of("product.definitions.deposit.enabled","false")));context.register(type("Configuration"));context.refresh();assertEquals(0,context.getBeanNamesForType(type("Runtime")).length);}}
    @Test void propertiesDefaultDisabledAndRuntimeDoesNotShadowLegacyServices()throws Exception{var properties=type("Properties").getConstructor().newInstance();assertEquals(false,type("Properties").getMethod("isEnabled").invoke(properties));assertFalse(io.fireflysoftware.core.product.core.services.definition.ProductDefinitionLifecycleService.class.isAssignableFrom(type("Runtime")));}
    @Test void newRoutesAreExplicitlyOptInWithExistingBearer()throws Exception{for(String name:List.of("DepositAccountLifecycleController","DepositAccountReadController")){var c=Class.forName("io.fireflysoftware.core.product.web.controllers."+name);var flag=c.getAnnotation(org.springframework.boot.autoconfigure.condition.ConditionalOnProperty.class);assertNotNull(flag);assertFalse(flag.matchIfMissing());assertEquals("product.definitions.deposit",flag.prefix());assertEquals("true",flag.havingValue());assertEquals("productDefinitionBearer",c.getAnnotation(io.swagger.v3.oas.annotations.security.SecurityRequirement.class).name());}}
    @Test void missingCredentialsFailBeforeOwnerOptionsOrPoolAreTouched()throws Exception{
        var config=type("Configuration").getConstructor().newInstance();var factory=Arrays.stream(config.getClass().getDeclaredMethods()).filter(m->m.isAnnotationPresent(org.springframework.context.annotation.Bean.class)).findFirst().orElseThrow();
        var commands=new ProductDefinitionCommandProperties();commands.setEnabled(true);var props=type("Properties").getConstructor().newInstance();
        var error=assertThrows(InvocationTargetException.class,()->factory.invoke(config,Arrays.stream(factory.getParameterTypes()).map(t->t==ProductDefinitionCommandProperties.class?commands:t==type("Properties")?props:null).toArray()));
        assertInstanceOf(IllegalStateException.class,error.getCause());assertEquals("PRODUCT_DEPOSIT_CREDENTIALS_REQUIRED",error.getCause().getMessage());assertNull(error.getCause().getCause());
    }
}
