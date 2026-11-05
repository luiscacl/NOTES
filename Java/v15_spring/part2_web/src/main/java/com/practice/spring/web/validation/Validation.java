package com.practice.spring.web.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.LocalDate;
import java.util.List;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/*
    =====================================================================
    VALIDACIÓN: NO DEJAR ENTRAR DATOS MAL FORMADOS
    =====================================================================

    Ruta de spring/: guía 6  (part2_web)
    Anterior:  part2_web/.../rest/RestControllers
    Siguiente: part2_web/.../errors/ErrorHandling

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo.
    - Las comprobaciones son pruebas: ValidationTest, una por capa. Desde
      v15_spring/:  ./mvnw -pl part2_web test -Dtest=ValidationTest
    - Las pruebas usan MockMvcTester sobre la aplicación completa
      (@SpringBootTest + @AutoConfigureMockMvc) para poder revisar la
      excepción que produjo cada error, con todos sus detalles.
    - Mensajes y códigos citados: corridas reales con Spring Boot 4.1.2.

    Lo que ya se vio: validar la forma en el constructor compacto de un DTO
    (v11_patterns/part4_modern/DTO, CAPA 5), validar la configuración con
    estas mismas anotaciones (part1_core/.../config/ExternalConfiguration,
    CAPA 4), y los proxies (part1_core/.../container/IoCContainer, CAPA 8).

    Importancia: MUY ALTA. Todo lo que llega a una API es sospechoso.

    Índice:
        CAPA 1)  Qué se valida aquí y qué no
        CAPA 2)  Bean Validation: las anotaciones
        CAPA 3)  @Valid en el cuerpo, y lo que pasa cuando falla
        CAPA 4)  Objetos anidados y listas
        CAPA 5)  Parámetros de la URL
        CAPA 6)  Restricciones propias
        CAPA 7)  El idioma de los mensajes
        CAPA 8)  Validar en los servicios (y el 500 que sorprende)
        CAPA 9)  Errores comunes
        RESUMEN FINAL
*/
public class Validation {

    /*
        =================================================================
        CAPA 1) QUÉ SE VALIDA AQUÍ Y QUÉ NO
        =================================================================

        Dos tipos de validación, en dos lugares distintos
        (v11_patterns/part4_modern/DTO, CAPA 5):

        - de FORMA, en la frontera (esta guía): que el correo tenga forma de
          correo, que la cantidad sea positiva, que la lista no venga vacía,
          que "desde" sea antes que "hasta". No necesitan consultar nada:
          se revisan con los datos que llegaron.
        - de NEGOCIO, en el servicio o el dominio: "ese correo ya está
          registrado", "no hay existencias", "el cupón ya venció". Necesitan
          consultar el estado (v14_architecture/part1_layers/DDDBasics).

        Validar la forma al entrar significa que el resto del código recibe
        datos que ya cumplen lo básico, y que el cliente recibe un 400
        claro en vez de un 500 de algo que falló adentro.
    */

    @SpringBootApplication
    public static class OrdersApplication {
    }

    /*
        =================================================================
        CAPA 2) BEAN VALIDATION: LAS ANOTACIONES
        =================================================================

        Bean Validation (Jakarta Validation; la implementación que trae Boot
        es Hibernate Validator) define restricciones como anotaciones sobre
        los componentes del record:

            @NotNull           no null
            @NotBlank          texto no null y con algo además de espacios
            @NotEmpty          texto o colección no vacía
            @Size(min, max)    largo de un texto o tamaño de una colección
            @Min / @Max        números (también @Positive, @PositiveOrZero)
            @Email             forma de correo
            @Pattern(regexp)   una expresión regular (v8_apis/
                               part1_text_and_time/RegularExpressions)
            @Past / @Future    fechas
            @Digits            cantidad de dígitos enteros y decimales

        Ojo con los primitivos: un int nunca es null. Si el campo falta o
        viene null en el JSON:
        - Boot 4 (Jackson 3): la conversión FALLA y responde 400 antes de
          validar, con un mensaje de Jackson ("Cannot map `null` into type
          `int`"), no con el campo y tu mensaje;
        - Boot 3 (Jackson 2): queda en 0, en silencio.
        Si "no llegó" debe ser un error claro, usa Integer con @NotNull:
        sale como error de validación, con el campo y el mensaje
        (v9_tools/.../libraries/jackson/Jackson, CAPA 2).
    */
    public record LineRequest(
            @NotBlank @Sku String sku,                      // CAPA 6: restricción propia
            @Positive int quantity) {
    }

    @ValidDeliveryWindow                                    // CAPA 6: compara dos campos
    public record DeliveryWindow(@NotNull LocalDate from, @NotNull LocalDate to) {
    }

    public record CreateOrderRequest(
            @NotBlank @Email String email,
            @NotNull @Size(min = 1, max = 20) List<@Valid LineRequest> lines,   // CAPA 4
            @Valid DeliveryWindow delivery) {               // CAPA 4: opcional, pero si viene, válido
    }

    /*
        =================================================================
        CAPA 3) @VALID EN EL CUERPO, Y LO QUE PASA CUANDO FALLA
        =================================================================

        Las anotaciones no hacen nada solas: alguien tiene que pedir la
        validación. En un controller, @Valid junto a @RequestBody:

            public OrderCreated create(@Valid @RequestBody CreateOrderRequest request)

        Spring convierte el JSON, lo valida, y si algo no cumple NO llama a
        tu método: lanza MethodArgumentNotValidException con TODOS los
        errores juntos (no solo el primero). Medido, para un correo "ana",
        una línea con sku en blanco y cantidad 0 (petición en inglés):

            email: must be a well-formed email address
            lines[0].quantity: must be greater than 0
            lines[0].sku: debe tener la forma ABC-123
            lines[0].sku: must not be blank

        Cada error trae el CAMPO (con su ruta: lines[0].sku), el VALOR que
        llegó ([ana], [0], [ ]) y el MENSAJE. El sku en blanco rompe DOS
        reglas (@NotBlank y la propia @Sku, CAPA 6), y salen las dos. El
        mensaje de @Sku sale en español aunque la petición sea en inglés:
        es un texto fijo, no una clave traducible (CAPA 7).

        La respuesta por defecto: 400 Bad Request... SIN esos detalles en el
        cuerpo (el genérico {"status":400,"error":"Bad Request",...} de
        rest/RestControllers, CAPA 6). El cliente sabe que algo está mal,
        pero no qué. Devolver la lista de campos en un formato estándar es
        la siguiente guía (errors/ErrorHandling).
    */
    public record OrderCreated(String email, int lines) {
    }

    @RestController
    @RequestMapping("/api/orders")
    public static class OrderController {
        private final InventoryService inventory;

        public OrderController(InventoryService inventory) {
            this.inventory = inventory;
        }

        @PostMapping
        @ResponseStatus(HttpStatus.CREATED)
        public OrderCreated create(@Valid @RequestBody CreateOrderRequest request) {   // sin @Valid: no se valida
            return new OrderCreated(request.email(), request.lines().size());
        }

        /*
            =============================================================
            CAPA 5) PARÁMETROS DE LA URL
            =============================================================

            Desde Spring Framework 6.1, las restricciones sobre parámetros
            sueltos (@RequestParam, @PathVariable) se revisan SOLAS en los
            controllers, sin poner nada en la clase:

                @GetMapping
                List<...> page(@RequestParam @Min(1) @Max(100) int page)

            Si fallan: HandlerMethodValidationException y 400 (medido: con
            ?page=0, "400 BAD_REQUEST "Validation failure"").

            (En código de Spring Boot 3.0/3.1 verás @Validated en la clase
            del controller: era necesario antes de 6.1; ahora sobra.)
        */
        @GetMapping
        public String page(@RequestParam @Min(1) @Max(100) int page,
                           @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
            return "página " + page + " de " + size;
        }

        @PostMapping("/reserve")
        public String reserve(@RequestParam String sku, @RequestParam int quantity) {
            return inventory.reserve(sku, quantity);        // CAPA 8: aquí NO se valida; en el servicio sí
        }
    }

    /*
        =================================================================
        CAPA 4) OBJETOS ANIDADOS Y LISTAS
        =================================================================

        La validación NO entra sola a los objetos de adentro. Hay que
        pedirlo con @Valid:
        - DeliveryWindow dentro del pedido: @Valid DeliveryWindow delivery;
        - cada elemento de la lista: List<@Valid LineRequest> lines.

        Sin ese @Valid, una línea con cantidad -5 pasaría sin error aunque
        LineRequest tenga @Positive. Es el error de validación más común.

        Y las restricciones sobre la colección misma (@Size(min = 1, max =
        20), @NotNull) van sobre el campo, como cualquier otra. Un límite de
        tamaño en las listas no es un detalle: sin él, un cliente puede
        mandar un pedido de un millón de líneas.
    */

    /*
        =================================================================
        CAPA 6) RESTRICCIONES PROPIAS
        =================================================================

        Cuando las anotaciones estándar no alcanzan, se escribe una propia:
        una anotación (@Constraint) + una clase que la revisa
        (ConstraintValidator). Dos casos:

        1) Una regla de FORMA del negocio que se repite: un código de
           producto es "tres letras, guion, tres dígitos" (ABC-123). Con
           @Sku en vez de repetir el @Pattern en cada DTO, el nombre dice qué
           es, y si la regla cambia se cambia en un lugar.

        2) Una regla que compara VARIOS campos: "from" debe ser antes o igual
           a "to". Una anotación sobre un campo no ve a los demás; se pone
           sobre el TIPO (@ValidDeliveryWindow sobre el record) y el
           validador recibe el objeto completo. Convención: si un campo es
           null, el validador de varios campos dice "válido" y deja ese caso
           a @NotNull, para no reportar el mismo problema dos veces.

        Las anotaciones de restricción necesitan tres elementos (message,
        groups, payload) aunque no los uses: es el contrato de Bean
        Validation. (Los "groups" sirven para validar distinto según el
        caso; casi siempre es más claro tener un DTO por caso de uso:
        v11_patterns/part4_modern/DTO, CAPA 6.)
    */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.TYPE_USE})
    @Constraint(validatedBy = SkuValidator.class)
    public @interface Sku {
        String message() default "debe tener la forma ABC-123";

        Class<?>[] groups() default {};

        Class<? extends Payload>[] payload() default {};
    }

    public static class SkuValidator implements ConstraintValidator<Sku, String> {
        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || value.matches("[A-Z]{3}-\\d{3}");   // null: lo revisa @NotBlank
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @Constraint(validatedBy = DeliveryWindowValidator.class)
    public @interface ValidDeliveryWindow {
        String message() default "la fecha inicial debe ser anterior o igual a la final";

        Class<?>[] groups() default {};

        Class<? extends Payload>[] payload() default {};
    }

    public static class DeliveryWindowValidator implements ConstraintValidator<ValidDeliveryWindow, DeliveryWindow> {
        @Override
        public boolean isValid(DeliveryWindow window, ConstraintValidatorContext context) {
            if (window == null || window.from() == null || window.to() == null) {
                return true;                                // los null los reporta @NotNull
            }
            return !window.from().isAfter(window.to());
        }
    }

    /*
        =================================================================
        CAPA 7) EL IDIOMA DE LOS MENSAJES
        =================================================================

        Los mensajes por defecto vienen traducidos en Hibernate Validator.
        ¿En qué idioma salen? Medido:
        - al validar la configuración al arrancar (config/
          ExternalConfiguration, CAPA 4) salieron en ESPAÑOL: el idioma de
          esta máquina;
        - al validar una PETICIÓN, Spring usa el idioma de la petición (el
          encabezado Accept-Language que manda el navegador o el cliente):
              Accept-Language: es  -> "debe ser mayor que 0"
              Accept-Language: en  -> "must be greater than 0"
          (y sin el encabezado, el idioma por defecto del servidor; las
          pruebas con MockMvc simulan una petición en inglés).

        Para mensajes propios:
        - en la anotación: @Size(max = 20, message = "máximo 20 productos");
        - o en un archivo ValidationMessages.properties (y _en, _es...) con
          claves, para traducirlos.

        Una decisión de diseño: muchas APIs devuelven además un CÓDIGO
        estable por error (el nombre de la restricción: "Positive",
        "NotBlank") para que el cliente decida qué mostrar, en vez de depender
        del texto (errors/ErrorHandling).
    */

    /*
        =================================================================
        CAPA 8) VALIDAR EN LOS SERVICIOS (Y EL 500 QUE SORPRENDE)
        =================================================================

        También se pueden validar los parámetros de un SERVICIO: @Validated
        en la clase y restricciones en los parámetros del método. Spring
        envuelve el bean en un PROXY que valida antes de llamar al método
        (part1_core/.../container/IoCContainer, CAPA 8). Útil cuando el
        servicio lo llaman varias puertas (web, mensajes, tareas), y no
        todas validan.

        Dos cosas que hay que saber:
        1) Si falla, lanza jakarta.validation.ConstraintViolationException.
           Spring MVC NO la convierte en 400: medido, la petición terminó en
           un error 500 ("reserve.sku: must not be blank, reserve.quantity:
           must be greater than or equal to 1"). Para el cliente parece un
           fallo del servidor, cuando el error fue suyo. Se traduce a 400
           con un manejador de errores (errors/ErrorHandling).
        2) Como es un proxy, la AUTO-INVOCACIÓN no valida: un método del
           servicio que llama a otro método del mismo servicio con datos
           malos no pasa por la validación.
    */
    @Service
    @Validated                                              // el proxy valida los parámetros
    public static class InventoryService {
        public String reserve(@NotBlank String sku, @Min(1) int quantity) {
            return "reservadas " + quantity + " de " + sku;
        }
    }

    /*
        =================================================================
        CAPA 9) ERRORES COMUNES
        =================================================================

        1) Reglas de negocio con anotaciones (consultar la base de datos
           dentro de un validador), o validar la forma en el servicio en
           vez de en la frontera (CAPA 1).

        2) int en vez de Integer cuando "no llegó" debe ser un error con
           mensaje claro (CAPA 2).

        3) Olvidar @Valid en el @RequestBody: las anotaciones están, pero
           nadie las revisa (CAPA 3).

        4) Olvidar @Valid en objetos anidados y en los elementos de las
           listas; listas sin límite de tamaño (CAPA 4).

        5) Repetir el mismo @Pattern en 10 DTOs en vez de una restricción con
           nombre (CAPA 6).

        6) Depender del texto de los mensajes (cambia con el idioma) en vez
           de un código (CAPA 7).

        7) Dejar que las validaciones de un servicio respondan 500 (CAPA 8).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - En la frontera se valida la FORMA; las reglas de negocio, en el
          servicio o el dominio.
        - Bean Validation: @NotBlank, @Size, @Min, @Positive, @Email,
          @Pattern... sobre los componentes del record.
        - @Valid @RequestBody: si falla, 400 y
          MethodArgumentNotValidException con TODOS los errores (campo,
          valor, mensaje); el cuerpo por defecto no los muestra.
        - @Valid en anidados y en List<@Valid T>; límites de tamaño.
        - Parámetros sueltos: restricciones directas (Spring 6.1+), 400.
        - Restricciones propias: @Constraint + ConstraintValidator; sobre el
          tipo para comparar campos.
        - Mensajes en el idioma de la petición (Accept-Language).
        - @Validated en servicios: proxy; ConstraintViolationException da 500
          si no se maneja.
    */
}
