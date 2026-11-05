package com.practice.spring.core.container;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.practice.spring.core.container.IoCContainer.CallCounter;
import com.practice.spring.core.container.IoCContainer.CardPayment;
import com.practice.spring.core.container.IoCContainer.CashPayment;
import com.practice.spring.core.container.IoCContainer.ChickenService;
import com.practice.spring.core.container.IoCContainer.Checkout;
import com.practice.spring.core.container.IoCContainer.ClockConfig;
import com.practice.spring.core.container.IoCContainer.ConnectionPoolLike;
import com.practice.spring.core.container.IoCContainer.CountedPostProcessor;
import com.practice.spring.core.container.IoCContainer.EggService;
import com.practice.spring.core.container.IoCContainer.FriendlyGreeter;
import com.practice.spring.core.container.IoCContainer.FullConfig;
import com.practice.spring.core.container.IoCContainer.Greeter;
import com.practice.spring.core.container.IoCContainer.HttpSettings;
import com.practice.spring.core.container.IoCContainer.InMemoryOrderRepository;
import com.practice.spring.core.container.IoCContainer.LifecycleLog;
import com.practice.spring.core.container.IoCContainer.LiteConfig;
import com.practice.spring.core.container.IoCContainer.NeedsSomethingMissing;
import com.practice.spring.core.container.IoCContainer.OrderService;
import com.practice.spring.core.container.IoCContainer.ReportBuilder;
import com.practice.spring.core.container.IoCContainer.ReportService;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCurrentlyInCreationException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.UnsatisfiedDependencyException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.NestedExceptionUtils;

/**
 * Las comprobaciones de IoCContainer, una por capa.
 * Correr desde v15_spring/:  ./mvnw -pl part1_core test -Dtest=IoCContainerTest
 */
class IoCContainerTest {

    @Test
    void capa1_unSingletonEsElMismoObjetoCadaVez() {
        try (var context = new AnnotationConfigApplicationContext(InMemoryOrderRepository.class)) {
            assertThat(context.getBean(InMemoryOrderRepository.class))
                    .isSameAs(context.getBean(InMemoryOrderRepository.class));
        }
    }

    @Test
    void capa2y3_estereotiposYBeanSeInyectanPorConstructor() {
        try (var context = new AnnotationConfigApplicationContext(
                InMemoryOrderRepository.class, ClockConfig.class, OrderService.class)) {
            String order = context.getBean(OrderService.class).placeOrder("café");
            assertThat(order).isEqualTo("café @ 2026-11-04T10:00:00Z");     // el Clock de @Bean
            assertThat(context.getBean(InMemoryOrderRepository.class).count()).isEqualTo(1);
            assertThat(context.getBean(Clock.class)).isNotNull();
        }
    }

    @Test
    void capa3_siFaltaUnaDependenciaNoArranca() {
        assertThatThrownBy(() -> new AnnotationConfigApplicationContext(NeedsSomethingMissing.class).close())
                .isInstanceOf(UnsatisfiedDependencyException.class)
                .satisfies(error -> assertThat(NestedExceptionUtils.getMostSpecificCause(error))
                        .isInstanceOf(NoSuchBeanDefinitionException.class)
                        .hasMessageContaining("No qualifying bean of type 'java.lang.Runnable' available"));
    }

    @Test
    void capa4_primaryQualifierYTodasComoMapa() {
        try (var context = new AnnotationConfigApplicationContext(
                CardPayment.class, CashPayment.class, Checkout.class)) {
            Checkout checkout = context.getBean(Checkout.class);
            assertThat(checkout.defaultCode()).isEqualTo("CARD");      // @Primary
            assertThat(checkout.cashCode()).isEqualTo("CASH");         // @Qualifier("cash")
            assertThat(checkout.pay("cash")).isEqualTo("CASH");        // Map<nombre, bean>
            assertThatThrownBy(() -> checkout.pay("bitcoin")).hasMessage("Forma de pago no soportada: bitcoin");
        }
    }

    @Test
    void capa5_prototypeInyectadoUnaVezVsObjectProvider() {
        try (var context = new AnnotationConfigApplicationContext(ReportBuilder.class, ReportService.class)) {
            ReportService service = context.getBean(ReportService.class);
            assertThat(service.trapped()).isSameAs(service.trapped());           // la trampa: siempre el mismo
            assertThat(service.fresh().id()).isNotEqualTo(service.fresh().id()); // uno nuevo cada vez
        }
    }

    @Test
    void capa6_cicloDeVidaCompleto() {
        List<String> events;
        try (var context = new AnnotationConfigApplicationContext(LifecycleLog.class, ConnectionPoolLike.class)) {
            events = context.getBean(LifecycleLog.class).events();
            assertThat(events).containsExactly("constructor", "@PostConstruct: abrir conexiones");
        }                                                                       // close(): @PreDestroy
        assertThat(events).containsExactly(
                "constructor", "@PostConstruct: abrir conexiones", "@PreDestroy: cerrar conexiones");
    }

    @Test
    void capa7_configurationCompletaEsUnaSubclaseCglibYLiteNo() {
        try (var full = new AnnotationConfigApplicationContext(FullConfig.class);
             var lite = new AnnotationConfigApplicationContext(LiteConfig.class)) {
            @SuppressWarnings("unchecked")
            List<HttpSettings> fullCalls = full.getBean("twoCalls", List.class);
            @SuppressWarnings("unchecked")
            List<HttpSettings> liteCalls = lite.getBean("twoCalls", List.class);

            assertThat(full.getBean(FullConfig.class).getClass().getName()).contains("$$SpringCGLIB$$");
            assertThat(fullCalls.get(0)).isSameAs(fullCalls.get(1)).isSameAs(full.getBean(HttpSettings.class));
            assertThat(lite.getBean(LiteConfig.class).getClass()).isEqualTo(LiteConfig.class);
            assertThat(liteCalls.get(0)).isNotSameAs(liteCalls.get(1));
        }
    }

    @Test
    void capa8_elBeanInyectadoEsUnProxyYLaAutoInvocacionNoPasaPorEl() {
        try (var context = new AnnotationConfigApplicationContext(
                CallCounter.class, CountedPostProcessor.class, FriendlyGreeter.class)) {
            Greeter greeter = context.getBean(Greeter.class);
            CallCounter counter = context.getBean(CallCounter.class);

            assertThat(Proxy.isProxyClass(greeter.getClass())).isTrue();      // no es FriendlyGreeter
            assertThat(greeter.greet("Ana")).isEqualTo("Hola, Ana");
            assertThat(counter.calls()).isEqualTo(1);

            greeter.greetTwice("Luis");                                         // adentro llama dos veces a greet...
            assertThat(counter.calls()).isEqualTo(1);                           // ...sin pasar por el proxy
        }
    }

    @Test
    void capa9_unCicloNoSePuedeCrear() {
        assertThatThrownBy(() -> new AnnotationConfigApplicationContext(ChickenService.class, EggService.class).close())
                .satisfies(error -> assertThat(NestedExceptionUtils.getMostSpecificCause(error))
                        .isInstanceOf(BeanCurrentlyInCreationException.class)
                        .hasMessageContaining("Is there an unresolvable circular reference"));
    }
}
