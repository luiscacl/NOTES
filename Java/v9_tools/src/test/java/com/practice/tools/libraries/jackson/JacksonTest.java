package com.practice.tools.libraries.jackson;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.practice.tools.libraries.jackson.Jackson.Card;
import com.practice.tools.libraries.jackson.Jackson.Cash;
import com.practice.tools.libraries.jackson.Jackson.Event;
import com.practice.tools.libraries.jackson.Jackson.OrderRef;
import com.practice.tools.libraries.jackson.Jackson.PaymentMethod;
import com.practice.tools.libraries.jackson.Jackson.Product;
import com.practice.tools.libraries.jackson.Jackson.Subscription;
import com.practice.tools.libraries.jackson.Jackson.SubscriptionStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Las comprobaciones de Jackson, con Jackson 2.21.7 y 3.1.7 a la vez.
 * Correr desde v9_tools/:  ./mvnw test -Dtest=JacksonTest
 */
class JacksonTest {

    private static final com.fasterxml.jackson.databind.ObjectMapper JACKSON2 = Jackson.plainJackson2();
    private static final JsonMapper JACKSON3 = Jackson.plainJackson3();

    private static final Event EVENT = new Event("e1", Instant.parse("2026-11-05T10:00:00Z"));
    private static final String WITH_UNKNOWN = "{\"sku\":\"A1\",\"name\":\"Teclado\",\"priceCents\":100,\"color\":\"rojo\"}";
    private static final String NULL_PRICE = "{\"sku\":\"A1\",\"name\":\"Teclado\",\"priceCents\":null}";
    private static final String TRAILING = "{\"sku\":\"A1\",\"name\":\"T\",\"priceCents\":1} basura";

    @Test
    void capa1_jackson2LanzaCheckedYJackson3Unchecked() {
        assertThat(RuntimeException.class).isAssignableFrom(JacksonException.class);
        assertThat(RuntimeException.class.isAssignableFrom(JsonProcessingException.class)).isFalse();
        assertThat(JACKSON3.writeValueAsString(new Product("A1", "Teclado", 100)))   // sin try ni throws
                .isEqualTo("{\"sku\":\"A1\",\"name\":\"Teclado\",\"priceCents\":100}");
    }

    @Test
    void capa2_losDefaultsQueCambiaron() throws Exception {
        assertThatThrownBy(() -> JACKSON2.writeValueAsString(EVENT))
                .hasMessageContaining("Java 8 date/time type `java.time.Instant` not supported by default");
        var jackson2WithModule = new com.fasterxml.jackson.databind.ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        assertThat(jackson2WithModule.writeValueAsString(EVENT)).isEqualTo("{\"id\":\"e1\",\"at\":1793872800.000000000}");
        assertThat(JACKSON3.writeValueAsString(EVENT)).isEqualTo("{\"id\":\"e1\",\"at\":\"2026-11-05T10:00:00Z\"}");

        assertThatThrownBy(() -> JACKSON2.readValue(WITH_UNKNOWN, Product.class))
                .hasMessageContaining("Unrecognized field \"color\"");
        assertThat(JACKSON3.readValue(WITH_UNKNOWN, Product.class)).isEqualTo(new Product("A1", "Teclado", 100));

        assertThat(JACKSON2.readValue(NULL_PRICE, Product.class).priceCents()).isZero();   // en silencio
        assertThatThrownBy(() -> JACKSON3.readValue(NULL_PRICE, Product.class))
                .hasMessageContaining("Cannot map `null` into type `long`");
        assertThat(JACKSON2.readValue("{\"sku\":\"A1\"}", Product.class)).isEqualTo(new Product("A1", null, 0));
        assertThatThrownBy(() -> JACKSON3.readValue("{\"sku\":\"A1\"}", Product.class))
                .hasMessageContaining("Cannot map `null` into type `long`");

        assertThat(JACKSON2.readValue(TRAILING, Product.class).sku()).isEqualTo("A1");      // la basura pasa
        assertThatThrownBy(() -> JACKSON3.readValue(TRAILING, Product.class))
                .hasMessageContaining("Unrecognized token 'basura'");
    }

    @Test
    void capa3_jackson2ConfiguradoComoSpringBoot3() throws Exception {
        var springLike = Jackson.jackson2LikeSpringBoot3();
        assertThat(springLike.writeValueAsString(EVENT)).isEqualTo("{\"id\":\"e1\",\"at\":\"2026-11-05T10:00:00Z\"}");
        assertThat(springLike.readValue(WITH_UNKNOWN, Product.class).sku()).isEqualTo("A1");
    }

    @Test
    void capa4_jsonNodeConPathYGet() {
        String api = "{\"data\":{\"items\":[{\"sku\":\"A1\",\"price\":{\"amount\":1999}}]},\"meta\":{\"page\":1}}";
        assertThat(Jackson.firstItemAmount(JACKSON3, api)).isEqualTo(1999);

        var root = JACKSON3.readTree(api);
        assertThat(root.get("nope")).isNull();                                  // el siguiente .get: NPE
        assertThat(root.path("nope").path("x").asLong()).isZero();              // path nunca es null...
        assertThat(root.path("nope").asString()).isEmpty();
        assertThatThrownBy(() -> Jackson.firstItemAmount(JACKSON3, "{\"data\":{\"items\":[]}}"))   // ...por eso se revisa
                .hasMessage("La respuesta ya no trae data.items[0].price.amount");
    }

    @Test
    void capa5_aliasSnakeCaseYEnumsQueCrecen() {
        assertThat(JACKSON3.writeValueAsString(new Subscription("pro", 19_900, SubscriptionStatus.ACTIVE)))
                .isEqualTo("{\"plan\":\"pro\",\"unit_price_cents\":19900,\"status\":\"ACTIVE\"}");
        assertThat(JACKSON3.readValue("{\"plan_name\":\"pro\",\"unit_price_cents\":19900,\"status\":\"PAUSED\"}",
                Subscription.class).plan()).isEqualTo("pro");                  // el nombre viejo también entra

        String newValue = "{\"plan\":\"pro\",\"unit_price_cents\":19900,\"status\":\"DELETED\"}";
        assertThatThrownBy(() -> JACKSON3.readValue(newValue, Subscription.class))
                .hasMessageContaining("from String \"DELETED\": not one of the values accepted");
        assertThat(Jackson.readerForPartnerApi().readValue(newValue, Subscription.class).status())
                .isEqualTo(SubscriptionStatus.UNKNOWN);
    }

    /** Una interface sealed SIN @JsonSubTypes: Jackson 3 inventa los nombres de tipo. */
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    sealed interface Undeclared permits Coupon, GiftCard {
    }

    record Coupon(String code) implements Undeclared {
    }

    record GiftCard(long cents) implements Undeclared {
    }

    @Test
    void capa6_polimorfismoConNombresTuyos() throws Exception {
        assertThat(JACKSON3.writeValueAsString(new Card("4242"))).isEqualTo("{\"type\":\"card\",\"last4\":\"4242\"}");
        PaymentMethod cash = JACKSON3.readValue("{\"type\":\"cash\",\"cents\":500}", PaymentMethod.class);
        assertThat(cash).isEqualTo(new Cash(500));
        assertThat(Jackson.describe(cash)).isEqualTo("efectivo: 500 centavos");
        assertThatThrownBy(() -> JACKSON3.readValue("{\"type\":\"crypto\",\"cents\":500}", PaymentMethod.class))
                .hasMessageContaining("Could not resolve type id 'crypto'")
                .hasMessageContaining("known type ids = [card, cash]");

        assertThat(JACKSON3.writeValueAsString(new Coupon("BUEN-FIN")))
                .isEqualTo("{\"type\":\"JacksonTest$Coupon\",\"code\":\"BUEN-FIN\"}");   // la clase en el contrato
        assertThatThrownBy(() -> JACKSON2.readValue("{\"type\":\"Coupon\",\"code\":\"X\"}", Undeclared.class))
                .hasMessageContaining("known type ids = []");                       // Jackson 2 no las busca
    }

    @Test
    void capa7_idsGrandesComoTexto() {
        String json = JACKSON3.writeValueAsString(new OrderRef(9_007_199_254_740_993L));
        assertThat(json).isEqualTo("{\"id\":\"9007199254740993\"}");
        assertThat(JACKSON3.readValue(json, OrderRef.class).id()).isEqualTo(9_007_199_254_740_993L);
        assertThat((long) (double) 9_007_199_254_740_993L).isEqualTo(9_007_199_254_740_992L);   // lo que haría JS
    }
}
