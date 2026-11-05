package com.practice.spring.core.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

/*
    =====================================================================
    CONFIGURACIÓN EXTERNA: EL MISMO JAR EN TODOS LOS AMBIENTES
    =====================================================================

    Ruta de spring/: guía 4  (part1_core)
    Anterior:  part1_core/.../autoconfig/AutoConfigurationUsage
    Siguiente: part2_web/.../rest/RestControllers

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo.
    - Las comprobaciones son pruebas: ExternalConfigurationTest, una por
      capa. Desde v15_spring/:
          ./mvnw -pl part1_core test -Dtest=ExternalConfigurationTest
    - La configuración del ejemplo está en src/main/resources/shop.yml y
      shop-prod.yml. Se llaman "shop" y no "application" para que solo las
      lea esta guía: la aplicación arranca con --spring.config.name=shop
      (CAPA 7).
    - Los mensajes citados salieron de corridas reales.

    Lo que ya se vio: las propiedades que ajustan la autoconfiguración
    (autoconfig/AutoConfigurationUsage, CAPA 5), los records y su
    constructor compacto (v2_oop/part4_advanced_types/Records), Duration
    (v8_apis/part1_text_and_time/DatesAndTimes).

    Importancia: MUY ALTA. Toda aplicación se configura así; los errores de
    configuración (y los secretos filtrados) son de los incidentes más
    comunes.

    Índice:
        CAPA 1)  Por qué la configuración va afuera del código
        CAPA 2)  application.yml y @ConfigurationProperties
        CAPA 3)  Quién gana: el orden de precedencia
        CAPA 4)  Validar la configuración al arrancar
        CAPA 5)  Perfiles
        CAPA 6)  @Value, y por qué preferir @ConfigurationProperties
        CAPA 7)  Dónde busca Boot los archivos
        CAPA 8)  Secretos
        CAPA 9)  Errores comunes
        RESUMEN FINAL
*/
public class ExternalConfiguration {

    /*
        =================================================================
        CAPA 1) POR QUÉ LA CONFIGURACIÓN VA AFUERA DEL CÓDIGO
        =================================================================

        La URL del proveedor de pagos es una en tu máquina, otra en pruebas
        y otra en producción. Lo mismo la base de datos, los tiempos límite,
        las llaves de API.

        Si esos valores están en el código, hay un jar distinto por
        ambiente, y lo que se probó NO es lo que se despliega. La práctica
        profesional ("The Twelve-Factor App", una guía muy citada para
        aplicaciones en la nube): UN solo artefacto (el jar o la imagen de
        Docker) que se despliega igual en todos lados, y lo que cambia
        entre ambientes se le da DESDE AFUERA al arrancar: archivos,
        variables de entorno, argumentos.

        Spring Boot junta todas esas fuentes en un solo lugar, el
        Environment, y tu código lee de ahí sin saber de dónde vino cada
        valor.
    */

    /*
        =================================================================
        CAPA 2) APPLICATION.YML Y @CONFIGURATIONPROPERTIES
        =================================================================

        La configuración se escribe en application.yml (o
        application.properties; mismo contenido, otra sintaxis):

            shop:                                 shop.payments.timeout=5s
              payments:                           (lo mismo en .properties)
                timeout: 5s
                max-retries: 3

        Y se lee con un record anotado con @ConfigurationProperties: Boot
        crea el objeto, LLENA cada componente con la propiedad del mismo
        nombre y CONVIERTE el texto al tipo:

            "5s", "500ms", "2m"         -> Duration
            "10MB", "512KB"             -> DataSize
            "https://..."               -> URI
            una lista en YAML           -> List<String>
            otra sección                -> un record anidado

        Los nombres son "relajados" (relaxed binding): max-retries,
        maxRetries, max_retries y la variable de entorno
        SHOP_PAYMENTS_MAX_RETRIES llegan TODOS al componente maxRetries. En
        los archivos se escribe con guiones (kebab-case), que es la forma
        recomendada.

        Para que Boot cree el bean: @EnableConfigurationProperties(La.class)
        en una configuración, o @ConfigurationPropertiesScan. Después se
        inyecta como cualquier bean (container/IoCContainer, CAPA 3): el
        servicio recibe ShopProperties, ya validado y con sus tipos, en vez
        de leer textos sueltos.
    */
    @Validated                                              // CAPA 4: validar al arrancar
    @ConfigurationProperties("shop")
    public record ShopProperties(
            @NotBlank String name,
            @Valid @NotNull Payments payments,              // @Valid: validar también lo de adentro
            @Valid @NotNull Upload upload,
            @NotEmpty List<String> allowedCountries) {

        public record Payments(
                @NotNull URI providerUrl,
                @NotNull Duration timeout,
                @Min(0) @Max(10) int maxRetries,
                String apiKey) {                            // CAPA 8: secreto

            @Override
            public String toString() {                      // nunca imprimir el secreto (CAPA 8)
                return "Payments[providerUrl=" + providerUrl + ", timeout=" + timeout
                        + ", maxRetries=" + maxRetries + ", apiKey=***]";
            }
        }

        public record Upload(@NotNull DataSize maxSize) {
        }
    }

    @SpringBootApplication
    @EnableConfigurationProperties(ShopProperties.class)
    public static class ShopApplication {
    }

    @Component
    public static class PaymentClient {
        private final ShopProperties.Payments settings;

        public PaymentClient(ShopProperties properties) {   // inyectado, ya convertido y validado
            this.settings = properties.payments();
        }

        public String describe() {
            return "POST " + settings.providerUrl() + " (timeout " + settings.timeout().toMillis() + " ms, "
                    + settings.maxRetries() + " reintentos)";
        }
    }

    /*
        =================================================================
        CAPA 3) QUIÉN GANA: EL ORDEN DE PRECEDENCIA
        =================================================================

        La misma propiedad puede venir de varios lugares. Gana el de MÁS
        ARRIBA (lista simplificada de la documentación de Spring Boot):

            1) argumentos de la línea de comandos    --shop.payments.timeout=9s
            2) propiedades del sistema de Java       -Dshop.payments.timeout=8s
            3) VARIABLES DE ENTORNO del SO           SHOP_PAYMENTS_TIMEOUT=7s
            4) application-{perfil}.yml              (CAPA 5)
            5) application.yml
            6) valores por defecto en el código

        (Los archivos que están FUERA del jar ganan a los que están dentro:
        CAPA 7.)

        La lógica: lo más general va en el archivo dentro del jar; lo de
        cada ambiente llega por variables de entorno (lo que usan Docker y
        Kubernetes); y un argumento sirve para cambiar algo puntual a mano.

        Las variables de entorno son como el sistema operativo le pasa datos
        a un proceso al crearlo (en Linux, el arreglo envp de execve: el
        proceso hijo recibe una COPIA y no puede cambiar las del padre). Por
        su formato, solo letras, números y guion bajo: shop.payments.timeout
        se escribe SHOP_PAYMENTS_TIMEOUT (puntos y guiones -> _, todo en
        mayúsculas).

        La prueba lo comprueba: el archivo dice 5s; la variable de entorno,
        7s (gana a el archivo); el argumento, 9s (gana a todos).
    */

    /*
        =================================================================
        CAPA 4) VALIDAR LA CONFIGURACIÓN AL ARRANCAR
        =================================================================

        Una configuración mala (un tiempo límite negativo, una URL vacía,
        max-retries: 50) que se descubre en la primera petición de pago, a
        las 3 de la mañana, es un incidente. Que se descubra AL ARRANCAR es
        un despliegue que no avanzó.

        @Validated en el record + anotaciones de Bean Validation
        (@NotBlank, @Min, @Max, @NotNull; part2_web/ las verá a fondo) hacen
        que Boot revise cada valor al crear el bean. Si algo no cumple, la
        aplicación NO arranca. Medido, con --shop.payments.max-retries=50:

            ***************************
            APPLICATION FAILED TO START
            ***************************
            Description:
            Binding to target com.practice.spring.core.config.
            ExternalConfiguration$ShopProperties failed:
                Property: shop.payments.maxRetries
                Value: "50"
                Reason: debe ser menor que o igual a 10

        Dice qué propiedad, qué valor llegó y por qué no sirve. El "Reason"
        sale en el idioma del sistema (esta máquina está en español; en un
        servidor en inglés diría "must be less than or equal to 10"). No
        dice de QUÉ FUENTE vino el valor (lo comprobé con un argumento y con
        un archivo): si no sabes cuál ganó, revisa el orden de la CAPA 3 o
        /actuator/env (CAPA 7).

        @Valid en un componente que es otro record hace que también se
        valide por dentro (Payments, Upload).
    */

    /*
        =================================================================
        CAPA 5) PERFILES
        =================================================================

        Un PERFIL es un nombre ("dev", "test", "prod") que se activa al
        arrancar:
            --spring.profiles.active=prod
            o la variable de entorno SPRING_PROFILES_ACTIVE=prod

        Con un perfil activo, Boot:
        - lee ADEMÁS application-{perfil}.yml (aquí, shop-prod.yml), que
          SOBRESCRIBE solo lo que define; lo demás se hereda;
        - crea los beans marcados con @Profile("prod"), y omite los de otros
          perfiles.

        Úsalos para diferencias de COMPORTAMIENTO: en dev, un servicio de
        correo falso que solo escribe en el log; en prod, el real.

        No los uses para tener "un archivo por servidor" con todas las URLs
        y contraseñas: los valores de cada ambiente van por variables de
        entorno (CAPA 3), y los secretos nunca en archivos del repositorio
        (CAPA 8). Muchos equipos tienen solo dos perfiles: el de por
        defecto (local) y uno para pruebas.
    */
    public interface MailSender {
        String send(String to);
    }

    @Component
    @Profile("!prod")                                       // en todo MENOS prod
    public static class LoggingMailSender implements MailSender {
        @Override
        public String send(String to) {
            return "[falso] correo a " + to + " escrito en el log";
        }
    }

    @Component
    @Profile("prod")
    public static class SmtpMailSender implements MailSender {
        @Override
        public String send(String to) {
            return "[smtp] correo a " + to + " enviado";
        }
    }

    /*
        =================================================================
        CAPA 6) @VALUE, Y POR QUÉ PREFERIR @CONFIGURATIONPROPERTIES
        =================================================================

        La otra forma de leer una propiedad, muy común en código existente:

            @Value("${shop.name}") String name
            @Value("${shop.featured-limit:5}") int limit     <- 5 si no existe

        Sirve para un valor suelto, pero con muchos se vuelve frágil:
        - el nombre de la propiedad está escrito como texto en cada lugar
          que la usa (un error de dedo se descubre al arrancar, o nunca si
          tiene valor por defecto);
        - no hay validación ni agrupación;
        - no funciona el binding relajado completo.

        @ConfigurationProperties pone toda la configuración de un tema en
        UN tipo, validado, documentable y fácil de probar (se construye con
        new en una prueba unitaria). Regla práctica: @Value para uno o dos
        valores aislados; @ConfigurationProperties para todo lo demás.
    */
    @Component
    public static class Storefront {
        private final String title;
        private final int featuredLimit;

        public Storefront(@Value("${shop.name}") String title,
                          @Value("${shop.featured-limit:5}") int featuredLimit) {   // 5 si no está definida
            this.title = title;
            this.featuredLimit = featuredLimit;
        }

        public String header() {
            return title + " (" + featuredLimit + " destacados)";
        }
    }

    /*
        =================================================================
        CAPA 7) DÓNDE BUSCA BOOT LOS ARCHIVOS
        =================================================================

        Boot busca application.yml (y application-{perfil}.yml) en, de menor
        a mayor prioridad:

            classpath:/            dentro del jar (src/main/resources)
            classpath:/config/
            ./                     la carpeta desde donde se arranca
            ./config/

        Así se puede poner un application.yml JUNTO al jar en un servidor y
        sobrescribir lo de adentro sin reconstruir.

        Para cambiarlo:
            spring.config.name=shop              otro nombre (lo que usa esta
                                                 guía: shop.yml, shop-prod.yml)
            spring.config.location=...           otros lugares
            spring.config.import=optional:file:.env[.properties]
                                                 importar otro archivo (aquí,
                                                 un .env local, si existe)

        Para ver qué valor quedó y de dónde vino: en una aplicación con
        Actuator, /actuator/env y /actuator/configprops (part6_production).
        Y para que el editor autocomplete tus propiedades en application.yml,
        se agrega spring-boot-configuration-processor, que genera la
        descripción de cada propiedad al compilar.
    */

    /*
        =================================================================
        CAPA 8) SECRETOS
        =================================================================

        Contraseñas de bases de datos, llaves de API, secretos para firmar
        tokens. Las reglas:

        1) NUNCA en el repositorio: ni en application.yml, ni en un perfil,
           ni "solo para dev". Lo que entra a Git queda en el historial para
           siempre, aunque se borre después, y lo ve cualquiera con acceso.
        2) Llegan DESDE AFUERA al arrancar: variables de entorno, o un gestor
           de secretos (los de cada nube, o HashiCorp Vault), que Boot puede
           importar con spring.config.import. En el archivo solo queda la
           REFERENCIA:
               api-key: ${PAYMENTS_API_KEY:}     (vacío si no está)
        3) NUNCA en logs ni en mensajes de error: el toString de lo que
           contiene un secreto lo oculta (Payments, arriba), igual que los
           DTOs con contraseñas (v11_patterns/part4_modern/DTO, CAPA 5).
           Actuator oculta por defecto los valores en /actuator/env.
        4) Si un secreto se filtró (se subió al repositorio, salió en un
           log), se ROTA: se genera uno nuevo y el viejo se invalida. Borrar
           el commit no basta.

        Y una regla de tu organización que coincide con esto: no compartir
        contraseñas, tokens ni llaves en ningún chat o documento.
    */

    /*
        =================================================================
        CAPA 9) ERRORES COMUNES
        =================================================================

        1) Valores de cada ambiente dentro del código o del jar (CAPA 1).

        2) Leer propiedades como texto suelto y convertirlas a mano (CAPA 2).

        3) No saber qué fuente ganó; editar el archivo cuando una variable de
           entorno lo está sobrescribiendo (CAPA 3).

        4) Configuración sin validar que falla en la primera petición
           (CAPA 4).

        5) Un perfil por servidor con todas sus URLs y contraseñas (CAPA 5).

        6) @Value con el mismo texto repetido en 15 clases (CAPA 6).

        7) Secretos en el repositorio o en los logs; "borrar el commit" en
           vez de rotar (CAPA 8).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Un jar para todos los ambientes; lo que cambia llega desde afuera.
        - application.yml + @ConfigurationProperties en records: binding
          relajado y conversión a Duration, DataSize, URI, listas.
        - Precedencia: argumentos > propiedades de Java > variables de
          entorno > archivo del perfil > archivo general.
        - @Validated: la configuración mala detiene el arranque, con la
          propiedad, el valor y la razón (no la fuente).
        - Perfiles para comportamiento (@Profile), no para guardar cada
          ambiente.
        - @Value para un valor suelto; @ConfigurationProperties para lo demás.
        - Secretos: fuera del repositorio, por variables de entorno o un
          gestor; nunca en logs; si se filtran, se rotan.
    */
}
