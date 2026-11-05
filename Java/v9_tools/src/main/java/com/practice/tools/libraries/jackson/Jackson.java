package com.practice.tools.libraries.jackson;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.json.JsonMapper;

/*
    =====================================================================
    JACKSON A FONDO: JACKSON 2 VS 3, Y LO QUE TE VA A PASAR CON JSON REAL
    =====================================================================

    Ruta de tools/: guía 4  (libraries; las 2 y 3 se omitieron: ver ROADMAP)
    Anterior:  build/maven/Maven
    Siguiente: quality/git/Git

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo.
    - Las comprobaciones son JacksonTest. Desde v9_tools/:
          ./mvnw test -Dtest=JacksonTest       (PowerShell: .\mvnw.cmd)
    - Aquí están las DOS versiones a la vez (ver el pom.xml): Jackson 2
      (com.fasterxml.jackson, la de Spring Boot 3) y Jackson 3
      (tools.jackson, la de Spring Boot 4). Cada valor citado sale de una
      corrida con Jackson 2.21.7 y 3.1.7.

    Lo que ya se vio y NO se repite aquí:
    - qué es Jackson, ObjectMapper compartido, TypeReference, por qué
      NUNCA dejar que el JSON elija la clase ("default typing"):
      v8_apis/part2_io_and_network/Serialization, CAPA 13 (era código de
      referencia; aquí corre);
    - DTOs en vez de clases internas: v11_patterns/part4_modern/DTO;
    - @JsonProperty, @JsonIgnore, @JsonInclude, y lo que hace Spring con el
      JSON de un controller (fechas ISO, campos desconocidos ignorados,
      400 con JSON roto): v15_spring/part2_web/.../rest/RestControllers,
      CAPA 5;
    - entidades JPA convertidas a JSON: v15_spring/part3_data/.../jpa/
      JpaEntities, CAPA 9;
    - por qué un long no cabe en el double de JavaScript:
      v1_fundamentals/part1_first_steps/ConversionsAndCasting, CAPA 3.

    Importancia: ALTA. Cada petición y respuesta de tu API pasa por Jackson,
    y cada API ajena que llames también. Y si tu trabajo usa Spring Boot 3
    mientras estudias con Boot 4, las diferencias de la CAPA 3 te van a
    morder.

    Índice:
        CAPA 1)  Jackson 2 y Jackson 3: cuál usa tu proyecto
        CAPA 2)  Los defaults que cambiaron (medidos)
        CAPA 3)  En Spring: usa SU mapper, no uno tuyo
        CAPA 4)  JsonNode: leer una API ajena sin crear clases
        CAPA 5)  Contratos que cambian: alias, snake_case y enums nuevos
        CAPA 6)  Polimorfismo seguro: el JSON dice "qué es", no "qué clase"
        CAPA 7)  Números que JavaScript no puede leer
        CAPA 8)  Errores comunes
        RESUMEN FINAL
*/
public final class Jackson {

    private Jackson() {
    }

    /*
        =================================================================
        CAPA 1) JACKSON 2 Y JACKSON 3: CUÁL USA TU PROYECTO
        =================================================================

        Mira los imports de tu proyecto:

            com.fasterxml.jackson.databind.ObjectMapper   Jackson 2 (Boot 3)
            tools.jackson.databind.json.JsonMapper        Jackson 3 (Boot 4)

        Lo que cambia al escribir código:

                                Jackson 2                  Jackson 3
            crear el mapper     new ObjectMapper()         JsonMapper.builder()
                                y luego configurarlo         ...build()
                                (MUTABLE)                  (INMUTABLE: se configura
                                                           al construirlo)
            excepciones         JsonProcessingException,   JacksonException,
                                CHECKED (try o throws      UNCHECKED
                                en todos lados)
            fechas java.time    módulo aparte              incluidas
                                (jackson-datatype-jsr310)
            JsonNode            asText()                   asString() (asText()
                                                           existe, pero DEPRECADO:
                                                           -Xlint lo avisa)

        Las ANOTACIONES BÁSICAS (@JsonProperty, @JsonIgnore, @JsonFormat,
        @JsonTypeInfo...) siguen en com.fasterxml.jackson.annotation en las
        dos: el mismo jar sirve para ambas (v9_tools/pom.xml las alinea en
        2.21). Pero las anotaciones que viven en DATABIND sí cambiaron de
        paquete: @JsonNaming, @JsonSerialize, @JsonDeserialize están en
        com.fasterxml.jackson.databind.annotation (2) y en
        tools.jackson.databind.annotation (3). Al copiar código entre
        proyectos, esos imports son los que rompen.

        Abajo, los dos mappers "de fábrica" que usa la CAPA 2, y uno de
        Jackson 2 configurado como lo hace Spring Boot 3 (CAPA 3).
    */
    public static ObjectMapper plainJackson2() {
        return new ObjectMapper();
    }

    public static JsonMapper plainJackson3() {
        return JsonMapper.builder().build();
    }

    /** Lo que Spring Boot 3 hace por ti con Jackson 2: fechas ISO y campos desconocidos ignorados. */
    public static ObjectMapper jackson2LikeSpringBoot3() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    /*
        =================================================================
        CAPA 2) LOS DEFAULTS QUE CAMBIARON (MEDIDOS)
        =================================================================

        Con new ObjectMapper() (Jackson 2) contra JsonMapper.builder().build()
        (Jackson 3), sin configurar nada. Product es un record (String sku,
        String name, long priceCents); Event lleva un Instant.

        | Caso                                  | Jackson 2                       | Jackson 3                        |
        |---------------------------------------|---------------------------------|----------------------------------|
        | escribir un Instant                   | FALLA: "Java 8 date/time type   | "at":"2026-11-05T10:00:00Z"      |
        |                                       | `java.time.Instant` not         |                                  |
        |                                       | supported by default"           |                                  |
        | ... con el módulo de fechas           | "at":1793872800.000000000       |                                  |
        |                                       | (¡un NÚMERO!)                   |                                  |
        | campo que el record no tiene          | FALLA: Unrecognized field       | lo IGNORA                        |
        | ("color":"rojo")                      | "color" ... not marked as       |                                  |
        |                                       | ignorable                       |                                  |
        | "priceCents": null en un long         | pone 0, sin avisar              | FALLA: Cannot map `null` into    |
        |                                       |                                 | type `long`                      |
        | falta "priceCents" (y "name")         | priceCents=0, name=null         | FALLA (el mismo mensaje)         |
        | basura después del JSON               | la acepta                       | FALLA: Unrecognized token        |
        | ({...} basura)                        |                                 | 'basura'                         |
        | valor de enum desconocido             | FALLA                           | FALLA                            |
        | orden de los campos al escribir       | el del record                   | el del record                    |

        Lo que significa:
        - Jackson 3 es más ESTRICTO con lo que importa (un precio que falta
          ya no se vuelve 0 en silencio: un pedido de $0 era un error real
          esperando a pasar) y más TOLERANTE con lo que no (campos nuevos de
          una API ajena ya no rompen tu lectura).
        - Con Jackson 2, un record con long/int/boolean que viene incompleto
          se llena con 0/false. Si "no vino" debe ser un error, usa Long (y
          valida que no sea null) o activa FAIL_ON_NULL_FOR_PRIMITIVES.
        - El Instant como número de Jackson 2 + módulo es la razón de las
          APIs que mandan fechas como 1793872800: falta desactivar
          WRITE_DATES_AS_TIMESTAMPS (jackson2LikeSpringBoot3, arriba).
    */
    public record Product(String sku, String name, long priceCents) {
    }

    public record Event(String id, Instant at) {
    }

    /*
        =================================================================
        CAPA 3) EN SPRING: USA SU MAPPER, NO UNO TUYO
        =================================================================

        Spring Boot crea UN mapper ya configurado (en Boot 3: módulo de
        fechas, fechas ISO, campos desconocidos ignorados; en Boot 4, medido
        en RestControllers: lo mismo) y lo usa para tus controllers y tus
        clientes HTTP. El error típico:

            String json = new ObjectMapper().writeValueAsString(event);

        en un servicio: ese mapper NO tiene la configuración de Spring. Con
        Jackson 2 truena con el Instant (CAPA 2), o escribe la fecha como
        número, y el JSON que guardas en la base o mandas a una cola sale
        DISTINTO al de tu API. Se inyecta el de Spring:

            Boot 3:  private final ObjectMapper mapper;   (constructor)
            Boot 4:  private final JsonMapper mapper;

        Para cambiar su configuración, sin crear otro:
        - propiedades: spring.jackson.default-property-inclusion=non_null,
          spring.jackson.deserialization.fail-on-unknown-properties=true...
        - en código, un bean que ajusta el builder de Spring:
              Boot 4: JsonMapperBuilderCustomizer
              Boot 3: Jackson2ObjectMapperBuilderCustomizer
          (verificado: JsonMapperBuilderCustomizer está en
          spring-boot-jackson 4.1.2, paquete org.springframework.boot.
          jackson.autoconfigure);
        - un serializador propio para un tipo: @JacksonComponent (Boot 4) o
          @JsonComponent (Boot 3).

        Si declaras tu PROPIO bean ObjectMapper/JsonMapper, Spring deja de
        crear el suyo: pierdes toda su configuración. Ajusta, no reemplaces.
    */

    /*
        =================================================================
        CAPA 4) JSONNODE: LEER UNA API AJENA SIN CREAR CLASES
        =================================================================

        Una API de pagos responde 40 campos y tú necesitas 1. Crear records
        para todo el árbol es mucho; readTree da un ÁRBOL navegable:

            {"data":{"items":[{"sku":"A1","price":{"amount":1999}}]},"meta":{"page":1}}

            mapper.readTree(json).path("data").path("items").get(0)
                  .path("price").path("amount").asLong()          -> 1999

        get vs path (medido):
            .get("nope")                       -> null (y el siguiente
                                                  .get revienta con NPE)
            .path("nope").path("x").asLong()   -> 0
            .path("nope").asString()           -> "" (vacío)

        path nunca devuelve null: devuelve un nodo "faltante". Cómodo, pero
        cuidado: un 0 o un "" pueden esconder que la API cambió y el campo
        ya no viene. Si el dato es obligatorio, revisa .isMissingNode() o
        usa .required("amount"), que lanza un error con el nombre del campo.

        Úsalo para LEER partes de algo ajeno. Para tu propia API, records:
        el compilador te avisa si te equivocas de nombre; un String en
        path() no.
    */
    public static long firstItemAmount(JsonMapper mapper, String paymentApiResponse) {
        JsonNode amount = mapper.readTree(paymentApiResponse)
                .path("data").path("items").path(0).path("price").path("amount");
        if (amount.isMissingNode()) {
            throw new IllegalArgumentException("La respuesta ya no trae data.items[0].price.amount");
        }
        return amount.asLong();
    }

    /*
        =================================================================
        CAPA 5) CONTRATOS QUE CAMBIAN: ALIAS, SNAKE_CASE Y ENUMS NUEVOS
        =================================================================

        Las APIs cambian. Tres herramientas para no romperte:

        @JsonAlias: aceptar el nombre VIEJO y el nuevo mientras los clientes
        migran. Al escribir sale solo el nuevo.

        @JsonNaming(SnakeCaseStrategy): la API ajena usa unit_price_cents y tu
        record unitPriceCents. En vez de un @JsonProperty por campo, uno para
        toda la clase. (Recuerda: @JsonNaming es de databind; en Jackson 3 se
        importa de tools.jackson.databind.annotation, CAPA 1.)

        Enums que crecen: tu enum tiene ACTIVE y PAUSED; un día la otra API
        manda "DELETED". En LAS DOS versiones eso FALLA (medido): "Cannot
        deserialize value of type ... Status from String "DELETED": not one
        of the values accepted". Una petición entera rota por un valor
        nuevo. La defensa: un valor de respaldo marcado @JsonEnumDefaultValue
        y la opción que lo activa:
            Jackson 2: DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE
            Jackson 3: EnumFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE
        Con eso "DELETED" se lee como UNKNOWN y tu código decide qué hacer.
        Solo para lo que LEES de otros; en lo que TÚ recibes de clientes, un
        valor inválido debe ser un 400.
    */
    public enum SubscriptionStatus {
        ACTIVE, PAUSED,
        @JsonEnumDefaultValue UNKNOWN
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Subscription(
            @JsonAlias("plan_name") String plan,              // la API vieja decía plan_name
            long unitPriceCents,                              // en el JSON: unit_price_cents
            SubscriptionStatus status) {
    }

    /** Jackson 3 tolerante con enums nuevos de una API ajena. */
    public static JsonMapper readerForPartnerApi() {
        return JsonMapper.builder()
                .enable(EnumFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE)
                .build();
    }

    /*
        =================================================================
        CAPA 6) POLIMORFISMO SEGURO: EL JSON DICE "QUÉ ES", NO "QUÉ CLASE"
        =================================================================

        Un pago puede ser con tarjeta o en efectivo, con campos distintos.
        El JSON necesita decir CUÁL es. Lo peligroso es que diga la CLASE
        Java (Serialization, CAPA 13). Lo correcto: un NOMBRE estable que
        tú mapeas a una clase de una lista cerrada:

            @JsonTypeInfo(use = Id.NAME, property = "type")
            @JsonSubTypes({@Type(value = Card.class, name = "card"),
                           @Type(value = Cash.class, name = "cash")})
            sealed interface PaymentMethod permits Card, Cash

        Medido:
            new Card("4242")  ->  {"type":"card","last4":"4242"}
            {"type":"cash","cents":500}    ->  Cash[cents=500]
            {"type":"crypto",...}          ->  InvalidTypeIdException: Could
                not resolve type id 'crypto' ... known type ids = [card, cash]

        ¿Y sin @JsonSubTypes? Con una interface sealed, Jackson 3 encuentra
        las clases solo; Jackson 2 no (medido: "known type ids = []"). Pero
        el nombre que inventa Jackson 3 es el de la clase. Medido en
        JacksonTest, con la interface dentro de la clase de prueba:
            {"type":"JacksonTest$Coupon","code":"BUEN-FIN"}
        es decir, tu estructura interna (clase externa incluida) se vuelve
        parte del contrato: renombrar una clase rompe a los clientes. Escribe
        @JsonSubTypes con nombres tuyos.

        Con el switch de SealedAndPatternMatching (v2_oop/part4_advanced_
        types/) el compilador te obliga a tratar cada tipo de pago.
    */
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = Card.class, name = "card"),
            @JsonSubTypes.Type(value = Cash.class, name = "cash")})
    public sealed interface PaymentMethod permits Card, Cash {
    }

    public record Card(String last4) implements PaymentMethod {
    }

    public record Cash(long cents) implements PaymentMethod {
    }

    public static String describe(PaymentMethod method) {
        return switch (method) {
            case Card card -> "tarjeta terminación " + card.last4();
            case Cash cash -> "efectivo: " + cash.cents() + " centavos";
        };
    }

    /*
        =================================================================
        CAPA 7) NÚMEROS QUE JAVASCRIPT NO PUEDE LEER
        =================================================================

        Un id long como 9007199254740993 sale en el JSON como número, y el
        frontend en JavaScript lo lee como 9007199254740992: OTRO pedido
        (ConversionsAndCasting, CAPA 3: JavaScript solo tiene double).
        Pasa con ids grandes generados al azar o por fecha, no con un
        autoincremento pequeño.

        La salida: mandarlo como TEXTO.

            record OrderRef(@JsonFormat(shape = Shape.STRING) long id)
            -> {"id":"9007199254740993"}          (medido)

        Jackson lo vuelve a leer como long sin problema. Lo mismo aplica al
        dinero: centavos en long o BigDecimal, nunca double
        (v8_apis/.../Serialization, CAPA 11).
    */
    public record OrderRef(@JsonFormat(shape = JsonFormat.Shape.STRING) long id) {
    }

    /*
        =================================================================
        CAPA 8) ERRORES COMUNES
        =================================================================

        1) Copiar código entre Boot 3 y Boot 4 sin cambiar imports; olvidar
           que @JsonNaming/@JsonSerialize cambiaron de paquete (CAPA 1).

        2) Asumir que Jackson 2 avisa cuando falta un número: pone 0
           (CAPA 2).

        3) Fechas como 1793872800 por registrar el módulo sin desactivar
           WRITE_DATES_AS_TIMESTAMPS (CAPA 2).

        4) new ObjectMapper() dentro de una app Spring, o declarar tu propio
           bean y perder la configuración de Spring (CAPA 3).

        5) .get() encadenado sobre JSON ajeno (NPE); path() sin revisar
           isMissingNode cuando el dato es obligatorio (CAPA 4).

        6) Un enum de una API ajena sin valor de respaldo: un valor nuevo
           rompe todas las lecturas (CAPA 5).

        7) Dejar que Jackson 3 invente los nombres de tipo (nombres de
           clase en el contrato) o, peor, que el JSON diga la clase
           (CAPA 6).

        8) ids long grandes como número hacia un frontend (CAPA 7).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Jackson 2 = com.fasterxml.jackson (Boot 3); Jackson 3 =
          tools.jackson (Boot 4): builder inmutable, excepciones unchecked,
          fechas incluidas. Anotaciones básicas compartidas; las de databind
          cambiaron de paquete.
        - Defaults: Jackson 3 ignora campos desconocidos, rechaza null y
          faltantes en primitivos y la basura al final; Jackson 2 al revés.
        - En Spring, inyecta su mapper y ajústalo (spring.jackson.*,
          JsonMapperBuilderCustomizer / Jackson2ObjectMapperBuilderCustomizer).
        - JsonNode con path() para leer partes de APIs ajenas; isMissingNode
          para lo obligatorio.
        - @JsonAlias para migrar nombres, @JsonNaming para snake_case,
          @JsonEnumDefaultValue para enums que crecen.
        - Polimorfismo con @JsonTypeInfo(NAME) + @JsonSubTypes con nombres
          tuyos sobre una interface sealed.
        - ids long grandes como texto (@JsonFormat(shape = STRING)).
    */
}
