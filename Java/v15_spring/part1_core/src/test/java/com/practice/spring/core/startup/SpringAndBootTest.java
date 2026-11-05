package com.practice.spring.core.startup;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Las comprobaciones de SpringAndBoot: una prueba por capa que se puede demostrar.
 * Correr desde v15_spring/:  ./mvnw -pl part1_core test -Dtest=SpringAndBootTest
 */
class SpringAndBootTest {

    /** Arranca DemoApplication como lo haría main, sin servidor web, y anota cada evento. */
    private static ConfigurableApplicationContext start(List<String> events, String... args) {
        SpringApplication application = new SpringApplication(SpringAndBoot.DemoApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.addListeners(event -> events.add(event.getClass().getSimpleName()));
        return application.run(args);
    }

    @Test
    void capa4_elEscaneoEncuentraLosComponentesDelPaquete() {
        try (ConfigurableApplicationContext context = start(new ArrayList<>())) {
            assertThat(context.getBean(SpringAndBoot.StartupReport.class)).isNotNull();   // nadie lo registró a mano
        }
    }

    @Test
    void capa5_elArranqueSigueEsteOrden() {
        List<String> events = new ArrayList<>();
        try (ConfigurableApplicationContext context = start(events)) {
            assertThat(context.getBeanDefinitionCount()).isGreaterThan(40);   // la autoconfiguración ya trae decenas
        }
        assertThat(events).containsSubsequence(
                "ApplicationStartingEvent",
                "ApplicationEnvironmentPreparedEvent",
                "ApplicationContextInitializedEvent",
                "ApplicationPreparedEvent",
                "ContextRefreshedEvent",
                "ApplicationStartedEvent",
                "ApplicationReadyEvent",
                "ContextClosedEvent");
    }

    @Test
    void capa6_elRunnerRecibeLosArgumentosYCorreAntesDeEstarLista() {
        try (ConfigurableApplicationContext context = start(new ArrayList<>(), "--modo=demo")) {
            assertThat(context.getBean(SpringAndBoot.StartupReport.class).notes())
                    .containsExactly("runner: modo=[demo]", "lista");
        }
    }
}
