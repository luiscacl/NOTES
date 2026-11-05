package com.practice.spring.core.autoconfig;

import static org.assertj.core.api.Assertions.assertThat;

import com.practice.spring.core.autoconfig.AutoConfigurationUsage.AuditAutoConfiguration;
import com.practice.spring.core.autoconfig.AutoConfigurationUsage.AuditForwarder;
import com.practice.spring.core.autoconfig.AutoConfigurationUsage.AuditTrail;
import com.practice.spring.core.autoconfig.AutoConfigurationUsage.InMemoryAuditTrail;
import com.practice.spring.core.autoconfig.shop.ShopApplication;
import java.net.http.HttpClient;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.condition.ConditionEvaluationReport;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Las comprobaciones de AutoConfigurationUsage, una por capa.
 * ApplicationContextRunner (de spring-boot-test) arranca un contenedor pequeño con
 * exactamente lo que se le dice: la herramienta estándar para probar autoconfiguraciones.
 * Correr desde v15_spring/:  ./mvnw -pl part1_core test -Dtest=AutoConfigurationUsageTest
 */
class AutoConfigurationUsageTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AuditAutoConfiguration.class));

    @Test
    void capa2_lasAutoconfiguracionesSeDescubrenPorElArchivoImports() {
        List<String> candidates = ImportCandidates
                .load(org.springframework.boot.autoconfigure.AutoConfiguration.class, getClass().getClassLoader())
                .getCandidates();
        assertThat(candidates)
                .contains("com.practice.spring.core.autoconfig.AutoConfigurationUsage$AuditAutoConfiguration")   // la nuestra
                .hasSizeGreaterThan(10);                                                                     // y las de Boot
    }

    @Test
    void capa3_sinLaPropiedadNoSeAplica() {
        runner.run(context -> assertThat(context).doesNotHaveBean(AuditTrail.class));
    }

    @Test
    void capa3_conLaPropiedadSeAplicaYConElClienteHttpTambienElReenvio() {
        runner.withPropertyValues("practice.audit.enabled=true").run(context -> {
            assertThat(context).hasSingleBean(AuditTrail.class).hasSingleBean(AuditForwarder.class);
            assertThat(context.getBean(AuditTrail.class)).isInstanceOf(InMemoryAuditTrail.class);
        });
    }

    @Test
    void capa3_siFaltaLaClaseNoSeCreaElReenvio() {
        runner.withPropertyValues("practice.audit.enabled=true")
                .withClassLoader(new FilteredClassLoader(HttpClient.class))   // como si la librería no estuviera
                .run(context -> assertThat(context)
                        .hasSingleBean(AuditTrail.class)
                        .doesNotHaveBean(AuditForwarder.class));
    }

    @Configuration(proxyBeanMethods = false)
    static class UserAuditConfiguration {
        @Bean
        AuditTrail databaseAuditTrail() {
            return new InMemoryAuditTrail(1);               // "el del usuario"
        }
    }

    @Test
    void capa4_elBeanDelUsuarioReemplazaAlDeBoot() {
        runner.withPropertyValues("practice.audit.enabled=true")
                .withUserConfiguration(UserAuditConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(AuditTrail.class);              // uno solo: el nuestro
                    assertThat(context).hasBean("databaseAuditTrail").doesNotHaveBean("auditTrail");
                });
    }

    @Test
    void capa5_unaPropiedadAjustaLoAutoconfigurado() {
        runner.withPropertyValues("practice.audit.enabled=true", "practice.audit.max-entries=2").run(context -> {
            InMemoryAuditTrail trail = (InMemoryAuditTrail) context.getBean(AuditTrail.class);
            trail.record("login");
            trail.record("pago");
            trail.record("logout");
            assertThat(trail.maxEntries()).isEqualTo(2);
            assertThat(trail.entries()).containsExactly("pago", "logout");   // solo los 2 más recientes
        });
    }

    @Test
    void capa6y7_unaAplicacionLaDescubreSolaYElReporteDiceElPorQue() {
        SpringApplication application = new SpringApplication(ShopApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);

        try (ConfigurableApplicationContext disabled = application.run()) {
            assertThat(disabled.getBeansOfType(AuditTrail.class)).isEmpty();
            String why = ConditionEvaluationReport.get(disabled.getBeanFactory())
                    .getConditionAndOutcomesBySource()
                    .get(AuditAutoConfiguration.class.getName())
                    .iterator().next().getOutcome().getMessage();
            assertThat(why).contains("did not find property 'enabled'");
        }
        try (ConfigurableApplicationContext enabled = application.run("--practice.audit.enabled=true")) {
            assertThat(enabled.getBean(AuditTrail.class)).isInstanceOf(InMemoryAuditTrail.class);
        }
    }

    @Test
    void capa8_unaAutoconfiguracionSePuedeExcluir() {
        SpringApplication application = new SpringApplication(ShopApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        try (ConfigurableApplicationContext context = application.run("--practice.audit.enabled=true",
                "--spring.autoconfigure.exclude=" + AuditAutoConfiguration.class.getName())) {
            assertThat(context.getBeansOfType(AuditTrail.class)).isEmpty();
            assertThat(ConditionEvaluationReport.get(context.getBeanFactory()).getExclusions())
                    .contains(AuditAutoConfiguration.class.getName());
        }
    }
}
