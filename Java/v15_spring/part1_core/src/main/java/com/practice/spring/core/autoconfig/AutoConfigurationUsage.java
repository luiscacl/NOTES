package com.practice.spring.core.autoconfig;

import java.net.http.HttpClient;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/*
    =====================================================================
    AUTOCONFIGURACIÓN: CÓMO DECIDE SPRING BOOT QUÉ CONFIGURAR
    =====================================================================

    Ruta de spring/: guía 3  (part1_core)
    Anterior:  part1_core/.../container/IoCContainer
    Siguiente: part1_core/.../config/ExternalConfiguration

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo.
    - Las comprobaciones son pruebas: AutoConfigurationUsageTest, una por
      capa. Desde v15_spring/:
          ./mvnw -pl part1_core test -Dtest=AutoConfigurationUsageTest
    - Se llama AutoConfigurationUsage para no tapar a la anotación
      @AutoConfiguration de Spring Boot (como v13_concurrency/part3_tasks/
      ExecutorsUsage con la clase Executors del JDK).
    - Incluye una autoconfiguración PROPIA y real (AuditAutoConfiguration,
      CAPA 7), registrada en
      src/main/resources/META-INF/spring/
          org.springframework.boot.autoconfigure.AutoConfiguration.imports
      igual que las de Spring Boot, y una aplicación que la usa sin
      mencionarla (shop/ShopApplication).
    - Los números y el reporte citados salieron de corridas reales.

    Lo que ya se vio: beans, @Configuration y @Bean (container/
    IoCContainer), anotaciones leídas al ejecutar (v12_jvm/part2_classes/
    ReflectionAndAnnotations).

    Importancia: ALTA. Es la "magia" de Spring Boot. Saber leerla es lo que
    permite resolver "¿por qué no se configuró X?" y "¿cómo cambio lo que
    Boot configuró?" sin adivinar.

    Índice:
        CAPA 1)  La idea: configurar según lo que hay
        CAPA 2)  Dónde están las autoconfiguraciones
        CAPA 3)  Las condiciones
        CAPA 4)  Tu bean gana: cómo "se hace a un lado" Boot
        CAPA 5)  Las propiedades ajustan lo autoconfigurado
        CAPA 6)  El reporte de condiciones: por qué sí y por qué no
        CAPA 7)  Escribir una autoconfiguración propia
        CAPA 8)  Excluir una autoconfiguración
        CAPA 9)  Errores comunes
        RESUMEN FINAL
*/
public class AutoConfigurationUsage {

    /*
        =================================================================
        CAPA 1) LA IDEA: CONFIGURAR SEGÚN LO QUE HAY
        =================================================================

        Agregas spring-boot-starter-data-jpa y el driver de PostgreSQL al
        pom, pones la URL de la base de datos en application.yml, y la
        aplicación ya tiene: un pool de conexiones, un EntityManagerFactory,
        un manejador de transacciones y los repositorios. No escribiste
        ninguna configuración. ¿Quién la escribió?

        Una AUTOCONFIGURACIÓN: una clase @Configuration normal, escrita por
        el equipo de Spring Boot, con CONDICIONES:
            "si en el classpath está la clase DataSource,
             y hay una URL configurada,
             y el usuario NO definió su propio DataSource,
             entonces crea uno con estos valores por defecto".

        Tres fuentes de información deciden todo:
        1) el CLASSPATH: qué librerías agregaste (los starters);
        2) las PROPIEDADES: application.yml, variables de entorno
           (config/ExternalConfiguration);
        3) los BEANS que TÚ ya definiste.

        No hay magia: es @Configuration + @Bean (container/IoCContainer)
        con @Conditional. Lo que sigue es cómo se encuentran y cómo se leen.
    */

    /*
        =================================================================
        CAPA 2) DÓNDE ESTÁN LAS AUTOCONFIGURACIONES
        =================================================================

        @SpringBootApplication incluye @EnableAutoConfiguration. Al arrancar,
        esa anotación busca en TODOS los jars del classpath un archivo:

            META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports

        Cada línea es el nombre de una clase de autoconfiguración. Cada
        librería que quiere autoconfigurarse trae el suyo.

        Medido:
        - el starter base trae 12 autoconfiguraciones candidatas (este
          módulo suma la de validation y la propia de esta guía: 14);
        - un proyecto con todos los starters de esta sección (web, datos,
          seguridad, actuator...): 129, repartidas en 96 jars.

        En Spring Boot 4 cada tecnología es un MÓDULO aparte con sus propias
        autoconfiguraciones: si no agregas el starter, ni siquiera se
        evalúan. (En Boot 3 había un solo jar con casi todas, y las
        condiciones descartaban las que no aplicaban.)

        Las autoconfiguraciones se procesan DESPUÉS de tu configuración: así
        pueden preguntar "¿el usuario ya definió este bean?" (CAPA 4).
    */

    /*
        =================================================================
        CAPA 3) LAS CONDICIONES
        =================================================================

        Las anotaciones @ConditionalOn... deciden si una clase de
        configuración o un método @Bean se aplica:

            @ConditionalOnClass(X.class)          la clase X existe en el
                                                  classpath (la librería
                                                  está)
            @ConditionalOnMissingClass("...")     no existe
            @ConditionalOnMissingBean             el usuario NO definió un
                                                  bean de ese tipo (CAPA 4)
            @ConditionalOnBean(X.class)           ya existe un bean X
            @ConditionalOnProperty(...)           una propiedad tiene cierto
                                                  valor
            @ConditionalOnBooleanProperty(...)    lo mismo, para true/false
            @ConditionalOnWebApplication          es una aplicación web
            @ConditionalOnResource(...)           existe un archivo

        Todas se construyen sobre @Conditional(MiCondicion.class) de Spring,
        que acepta cualquier lógica propia.

        @ConditionalOnClass es especial: se revisa LEYENDO el .class con
        ASM (una librería que lee bytecode, v12_jvm/part2_classes/
        ClassLoadingAndBytecode) SIN cargar la clase. Por eso puede decir
        "si la librería X está" sin que falle cuando X no está.

        Ejemplos en la autoconfiguración de la CAPA 7:
        - toda la autoconfiguración: solo si practice.audit.enabled=true;
        - el reenvío por HTTP: solo si java.net.http.HttpClient existe.
    */

    /*
        =================================================================
        CAPA 4) TU BEAN GANA: CÓMO "SE HACE A UN LADO" BOOT
        =================================================================

        Casi todo bean autoconfigurado lleva @ConditionalOnMissingBean: "créalo
        solo si el usuario no definió uno". Como las autoconfiguraciones se
        evalúan DESPUÉS de tu configuración, si tú defines el bean, Boot ve
        que ya existe y no crea el suyo ("backs off").

        Esa es LA forma de cambiar algo que Boot configuró: no se edita nada
        de Boot; se define tu propio bean del mismo tipo:

            @Bean
            AuditTrail auditTrail() { return new MiAuditoriaEnBaseDeDatos(); }

        y el de Boot desaparece. La prueba lo comprueba con AuditTrail.

        Ojo con el TIPO: @ConditionalOnMissingBean revisa el tipo que dice
        el método @Bean. Si tu bean es de un tipo distinto (otra interface),
        Boot no lo reconoce como "el mismo" y crea el suyo también: terminas
        con dos, y el error de "required a single bean, but 2 were found"
        (container/IoCContainer, CAPA 4).
    */

    /*
        =================================================================
        CAPA 5) LAS PROPIEDADES AJUSTAN LO AUTOCONFIGURADO
        =================================================================

        Antes de reemplazar un bean, casi siempre basta con una PROPIEDAD.
        Cada autoconfiguración lee una clase @ConfigurationProperties con
        valores por defecto, y tú los cambias en application.yml:

            spring.datasource.hikari.maximum-pool-size: 20
            server.port: 9090
            practice.audit.max-entries: 500        <- la de la CAPA 7

        El orden de preferencia para cambiar algo de Boot:
            1) una propiedad (lo más simple; la lista completa de cada
               tecnología está en la documentación de Spring Boot, sección
               "Common Application Properties");
            2) un bean "customizer" que Boot llama mientras arma el suyo (por
               ejemplo, para agregar algo al ObjectMapper sin reemplazarlo);
            3) tu propio bean (CAPA 4), cuando lo quieres completamente
               distinto.
        Reemplazar el bean pierde TODO lo que Boot configuraba en él; una
        propiedad o un customizer cambian solo lo que necesitas.

        Cómo funcionan las propiedades por dentro: config/ExternalConfiguration.
    */

    /*
        =================================================================
        CAPA 6) EL REPORTE DE CONDICIONES: POR QUÉ SÍ Y POR QUÉ NO
        =================================================================

        "Agregué la librería y no se configuró." "¿Por qué hay un bean que no
        pedí?" No se adivina: Boot registra CADA condición que evaluó y su
        resultado. Se ve:
        - arrancando con --debug (o debug=true en application.yml): imprime
          el CONDITIONS EVALUATION REPORT;
        - en una aplicación con Actuator, en /actuator/conditions
          (part6_production);
        - desde código, con ConditionEvaluationReport (lo que usa la prueba).

        Un extracto real, de este módulo:

            Positive matches:
            -----------------
               AopAutoConfiguration matched:
                  - @ConditionalOnBooleanProperty (spring.aop.auto=true)
                    matched (OnPropertyCondition)

            Negative matches:
            -----------------
               AopAutoConfiguration.AspectJAutoProxyingConfiguration:
                  Did not match:
                     - @ConditionalOnClass did not find required class
                       'org.aspectj.weaver.Advice' (OnClassCondition)

               JmxAutoConfiguration:
                  Did not match:
                     - @ConditionalOnBooleanProperty (spring.jmx.enabled=true)
                       did not find property 'spring.jmx.enabled'
                  Matched:
                     - @ConditionalOnClass found required class
                       'org.springframework.jmx.export.MBeanExporter'

        Se lee así: JMX no se configuró NO porque falte la librería (esa
        condición sí se cumplió), sino porque la propiedad
        spring.jmx.enabled no está en true. Medido: 28 fuentes evaluadas y
        15 que se aplicaron completas, en una aplicación vacía.
    */

    /*
        =================================================================
        CAPA 7) ESCRIBIR UNA AUTOCONFIGURACIÓN PROPIA
        =================================================================

        En empresas con varios servicios es común un "starter interno": una
        librería que todos agregan y que configura sola lo común (auditoría,
        clientes de servicios internos, formato de logs, seguridad). Así se
        escribe, y así es exactamente como están hechas las de Boot:

        1) una clase con @org.springframework.boot.autoconfigure.AutoConfiguration
           (una @Configuration con proxyBeanMethods = false: container/
           IoCContainer, CAPA 7);
        2) condiciones en la clase y en cada @Bean; @ConditionalOnMissingBean
           en TODO lo que el usuario podría querer reemplazar (CAPA 4);
        3) sus propiedades en un @ConfigurationProperties con valores por
           defecto (aquí, un record);
        4) registrarla en el archivo .imports (CAPA 2). Sin eso, no se
           descubre.

        Y una regla que se aprende a golpes: las autoconfiguraciones NO
        deben ser escaneadas como componentes. Si se registraran como
        configuración normal, perderían su orden ("después de la
        configuración del usuario") y sus condiciones.
        @SpringBootApplication excluye del escaneo a las clases con
        @AutoConfiguration o listadas en un .imports
        (AutoConfigurationExcludeFilter)... pero no a una @Configuration
        normal anidada dentro de ellas.
        Medido al escribir esta guía: con la aplicación en el MISMO paquete
        que la autoconfiguración, el escaneo encontró la configuración
        ANIDADA (ForwardingConfiguration, que no está en el .imports), la
        registró sin la condición de la clase de afuera, y la aplicación no
        arrancó:

            Error creating bean with name 'auditForwarder' ...: No
            qualifying bean of type '...AuditProperties' available

        Por eso las autoconfiguraciones viven en paquetes que ninguna
        aplicación escanea (las de Boot, en org.springframework.boot...), y
        aquí la aplicación está en su propio subpaquete, shop/.

        La de aquí: AuditAutoConfiguration. Si practice.audit.enabled=true,
        registra un AuditTrail en memoria (con máximo de entradas
        configurable) y, si existe el cliente HTTP del JDK, un AuditForwarder.
    */
    public interface AuditTrail {
        void record(String event);

        List<String> entries();
    }

    public static class InMemoryAuditTrail implements AuditTrail {
        private final int maxEntries;
        private final Deque<String> entries = new ArrayDeque<>();

        public InMemoryAuditTrail(int maxEntries) {
            this.maxEntries = maxEntries;
        }

        @Override
        public synchronized void record(String event) {
            if (entries.size() == maxEntries) {
                entries.removeFirst();                      // conserva los más recientes
            }
            entries.addLast(event);
        }

        @Override
        public synchronized List<String> entries() {
            return List.copyOf(entries);
        }

        public int maxEntries() {
            return maxEntries;
        }
    }

    public record AuditForwarder(String endpoint) {
    }

    @ConfigurationProperties("practice.audit")
    public record AuditProperties(
            boolean enabled,
            @DefaultValue("100") int maxEntries,            // practice.audit.max-entries
            @DefaultValue("http://localhost:8081/audit") String forwardTo) {
    }

    @org.springframework.boot.autoconfigure.AutoConfiguration
    @ConditionalOnProperty(prefix = "practice.audit", name = "enabled", havingValue = "true")
    @EnableConfigurationProperties(AuditProperties.class)
    public static class AuditAutoConfiguration {

        @Bean
        @ConditionalOnMissingBean                           // si el usuario trae el suyo, este no se crea
        public AuditTrail auditTrail(AuditProperties properties) {   // dependencias como parámetros
            return new InMemoryAuditTrail(properties.maxEntries());
        }

        @Configuration(proxyBeanMethods = false)
        @ConditionalOnClass(HttpClient.class)               // solo si la clase existe en el classpath
        public static class ForwardingConfiguration {
            @Bean
            @ConditionalOnMissingBean
            public AuditForwarder auditForwarder(AuditProperties properties) {
                return new AuditForwarder(properties.forwardTo());
            }
        }
    }

    /*
        =================================================================
        CAPA 8) EXCLUIR UNA AUTOCONFIGURACIÓN
        =================================================================

        Si una autoconfiguración estorba (y no basta con una propiedad ni con
        tu propio bean), se apaga completa:

            @SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
        o en application.yml:
            spring.autoconfigure.exclude: com.ejemplo.AlgunaAutoConfiguration

        Con la propiedad se puede excluir por perfil (por ejemplo, solo en
        pruebas). El reporte de condiciones lista lo excluido en
        "Exclusions". Es el último recurso: normalmente significa que sobra
        una dependencia en el pom.
    */

    /*
        =================================================================
        CAPA 9) ERRORES COMUNES
        =================================================================

        1) Creer que Boot hace "magia": son @Configuration con condiciones,
           y todo se puede leer (CAPAS 1 y 6).

        2) Agregar un starter "por si acaso": trae beans, conexiones y
           puertos que no pediste (CAPA 2).

        3) Reemplazar un bean de Boot cuando bastaba una propiedad o un
           customizer; o definirlo con OTRO tipo y terminar con dos
           (CAPAS 4 y 5).

        4) Adivinar por qué algo no se configuró en vez de leer el reporte
           de condiciones (CAPA 6).

        5) En una autoconfiguración propia: olvidar @ConditionalOnMissingBean,
           no registrarla en el .imports, o dejar que se escanee como
           componente (CAPA 7).

        6) Excluir autoconfiguraciones en vez de quitar la dependencia que
           sobra (CAPA 8).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Autoconfiguración = @Configuration con condiciones, decidida por
          classpath, propiedades y tus beans.
        - Se descubren en META-INF/spring/...AutoConfiguration.imports de
          cada jar (12 con el starter base, 129 con todos, medido) y se
          evalúan después de tu configuración.
        - @ConditionalOnClass, OnMissingBean, OnProperty, OnBean...
        - Tu bean del mismo tipo reemplaza al de Boot; antes, prueba una
          propiedad o un customizer.
        - --debug o /actuator/conditions explican por qué sí y por qué no.
        - Autoconfiguración propia: @AutoConfiguration, condiciones,
          @ConfigurationProperties, y el archivo .imports.
    */
}
