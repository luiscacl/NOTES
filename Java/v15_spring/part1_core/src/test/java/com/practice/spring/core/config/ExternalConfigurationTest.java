package com.practice.spring.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.practice.spring.core.config.ExternalConfiguration.MailSender;
import com.practice.spring.core.config.ExternalConfiguration.PaymentClient;
import com.practice.spring.core.config.ExternalConfiguration.ShopApplication;
import com.practice.spring.core.config.ExternalConfiguration.ShopProperties;
import com.practice.spring.core.config.ExternalConfiguration.Storefront;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.util.unit.DataSize;

/**
 * Las comprobaciones de ExternalConfiguration, una por capa.
 * Correr desde v15_spring/:  ./mvnw -pl part1_core test -Dtest=ExternalConfigurationTest
 */
class ExternalConfigurationTest {

    /**
     * Arranca la aplicación con shop.yml y, si se pasan, unas "variables de entorno" falsas:
     * se reemplaza la fuente del sistema por un mapa, para no depender de las de esta máquina.
     */
    private static ConfigurableApplicationContext start(Map<String, Object> environmentVariables, String... args) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().replace(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                new SystemEnvironmentPropertySource(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                        environmentVariables));
        SpringApplication application = new SpringApplication(ShopApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setEnvironment(environment);
        List<String> allArgs = new ArrayList<>(List.of("--spring.config.name=shop"));
        allArgs.addAll(List.of(args));
        return application.run(allArgs.toArray(String[]::new));
    }

    @Test
    void capa2_elYamlLlegaConvertidoAlRecord() {
        try (var context = start(Map.of())) {
            ShopProperties shop = context.getBean(ShopProperties.class);
            assertThat(shop.name()).isEqualTo("Tienda Demo");
            assertThat(shop.payments().timeout()).isEqualTo(Duration.ofSeconds(5));          // "5s"
            assertThat(shop.payments().providerUrl()).isEqualTo(URI.create("https://sandbox.pagos.example"));
            assertThat(shop.upload().maxSize()).isEqualTo(DataSize.ofMegabytes(10));           // "10MB"
            assertThat(shop.allowedCountries()).containsExactly("MX", "US", "CA");
            assertThat(context.getBean(PaymentClient.class).describe())
                    .isEqualTo("POST https://sandbox.pagos.example (timeout 5000 ms, 3 reintentos)");
        }
    }

    @Test
    void capa3_variableDeEntornoGanaAlArchivoYElArgumentoGanaATodos() {
        Map<String, Object> env = Map.of("SHOP_PAYMENTS_TIMEOUT", "7s");                    // relaxed binding
        try (var fromEnv = start(env);
             var fromArg = start(env, "--shop.payments.timeout=9s")) {
            assertThat(fromEnv.getBean(ShopProperties.class).payments().timeout()).isEqualTo(Duration.ofSeconds(7));
            assertThat(fromArg.getBean(ShopProperties.class).payments().timeout()).isEqualTo(Duration.ofSeconds(9));
        }
    }

    @Test
    void capa4_unaConfiguracionInvalidaDetieneElArranque() {
        assertThatThrownBy(() -> start(Map.of(), "--shop.payments.max-retries=50").close())
                .satisfies(error -> {
                    BindValidationException cause = (BindValidationException) NestedExceptionUtils.getMostSpecificCause(error);
                    assertThat(cause.getValidationErrors().getAllErrors()).hasSize(1);
                    assertThat(cause.getValidationErrors().getAllErrors().getFirst().getArguments())
                            .isNotEmpty();
                    assertThat(cause.getMessage()).contains("maxRetries");
                });
    }

    @Test
    void capa5_elPerfilSobrescribeSoloLoQueDefineYEligeBeans() {
        try (var dev = start(Map.of());
             var prod = start(Map.of(), "--spring.profiles.active=prod")) {
            ShopProperties prodShop = prod.getBean(ShopProperties.class);
            assertThat(prodShop.payments().providerUrl()).isEqualTo(URI.create("https://api.pagos.example"));
            assertThat(prodShop.payments().timeout()).isEqualTo(Duration.ofSeconds(2));
            assertThat(prodShop.payments().maxRetries()).isEqualTo(3);                        // heredado de shop.yml

            assertThat(dev.getBean(MailSender.class).send("ana@correo.com")).startsWith("[falso]");
            assertThat(prod.getBean(MailSender.class).send("ana@correo.com")).startsWith("[smtp]");
        }
    }

    @Test
    void capa6_valueConValorPorDefecto() {
        try (var context = start(Map.of());
             var withLimit = start(Map.of(), "--shop.featured-limit=12")) {
            assertThat(context.getBean(Storefront.class).header()).isEqualTo("Tienda Demo (5 destacados)");
            assertThat(withLimit.getBean(Storefront.class).header()).isEqualTo("Tienda Demo (12 destacados)");
        }
    }

    @Test
    void capa8_elSecretoLlegaDeAfueraYNoSeImprime() {
        String fakeKey = "sk_test_1234";                                                       // un valor de prueba, no real
        try (var context = start(Map.of("PAYMENTS_API_KEY", fakeKey));
             var without = start(Map.of())) {
            ShopProperties.Payments payments = context.getBean(ShopProperties.class).payments();
            assertThat(payments.apiKey()).isEqualTo(fakeKey);
            assertThat(payments.toString()).doesNotContain(fakeKey).contains("apiKey=***");
            assertThat(without.getBean(ShopProperties.class).payments().apiKey()).isEmpty();   // ${...:} vacío
        }
    }
}
