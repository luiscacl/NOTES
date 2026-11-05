package com.practice.spring.core.startup;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/*
    =====================================================================
    SPRING Y SPRING BOOT: QUÉ SON, MAVEN MÍNIMO, Y QUÉ PASA AL ARRANCAR
    =====================================================================

    Ruta de spring/: guía 1  (part1_core)
    Anterior:  v14_architecture/part3_systems/MonolithVsMicroservices
    Siguiente: part1_core/.../container/IoCContainer

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo.
    - Spring no se aprende con un archivo que se corre solo: aquí cada
      guía es parte de un proyecto Maven real (v15_spring/), y sus
      comprobaciones son PRUEBAS (JUnit), una por capa, en
      src/test/java/... con el mismo nombre + "Test". Se corren así,
      desde v15_spring/:
          ./mvnw -pl part1_core test -Dtest=SpringAndBootTest
      (en PowerShell: .\mvnw.cmd -pl part1_core test -Dtest=SpringAndBootTest)
    - Versiones: Spring Boot 4.1.2 (sobre Spring Framework 7), compilando
      para Java 25. Medido en Windows 11 con JDK 26.

    Lo que ya se vio y aquí se vuelve real: la inyección de dependencias y
    el contenedor de juguete (v10_design/part3_responsibilities/
    DependencyInjection, CAPAS 7 y 8), los proxies (v11_patterns/
    part2_structural/Proxy, CAPA 6), las capas de una aplicación
    (v14_architecture/).

    Importancia: MUY ALTA. Es la base de todo lo demás de esta sección.

    Índice:
        CAPA 1)  Qué es Spring
        CAPA 2)  Qué agrega Spring Boot
        CAPA 3)  Maven: lo mínimo que tienes que entender
        CAPA 4)  La forma de un proyecto Spring Boot
        CAPA 5)  Qué pasa al arrancar, paso a paso
        CAPA 6)  Hacer algo al arrancar: runners y eventos
        CAPA 7)  El jar ejecutable
        CAPA 8)  Versiones: Boot 3 y Boot 4
        CAPA 9)  Errores comunes
        RESUMEN FINAL
*/
public class SpringAndBoot {

    /*
        =================================================================
        CAPA 1) QUÉ ES SPRING
        =================================================================

        En v10_design/part3_responsibilities/DependencyInjection escribiste
        un contenedor de juguete de 50 líneas: le decías qué clases había, y
        él creaba los objetos y les pasaba sus dependencias por el
        constructor. SPRING es eso, hecho industrial, desde 2003:

        1) EL CONTENEDOR (Spring Core, "IoC container"): crea los objetos de
           tu aplicación (los llama BEANS), los conecta entre sí, controla
           cuánto viven, y puede envolverlos en proxies para agregar
           comportamiento (transacciones, seguridad, caché) sin tocar tu
           código. Es la inversión de control: tú no haces new de tus
           servicios; el contenedor los arma (part1_core/.../IoCContainer).

        2) EL ECOSISTEMA: módulos construidos sobre ese contenedor, que
           resuelven lo que toda aplicación empresarial necesita:
               Spring MVC          APIs REST y web
               Spring Data         repositorios para bases de datos
               Spring Security     autenticación y permisos
               Spring Boot         arma y configura todo lo anterior
           y muchos más (mensajería, batch, nube...).

        Por qué domina en el backend Java: no tienes que escribir la
        infraestructura (servidor HTTP, transacciones, conexiones, JSON,
        seguridad), y todo se conecta con TU código a través de un solo
        mecanismo: beans inyectados por constructor.
    */

    /*
        =================================================================
        CAPA 2) QUÉ AGREGA SPRING BOOT
        =================================================================

        Antes de Boot (2014), una aplicación Spring necesitaba decenas de
        líneas de configuración para conectar sus piezas, elegir versiones
        de librerías que fueran compatibles, y desplegarse en un servidor
        de aplicaciones aparte. Boot elimina ese trabajo:

        - STARTERS: una dependencia que trae un conjunto probado de
          librerías ("spring-boot-starter-webmvc" trae Spring MVC, Jackson y
          un servidor Tomcat embebido) (CAPA 3).
        - AUTOCONFIGURACIÓN: si encuentra una librería en el classpath,
          configura sus beans con valores razonables; si tú defines el tuyo,
          usa el tuyo (part1_core/.../AutoConfiguration).
        - CONFIGURACIÓN EXTERNA: application.yml, variables de entorno,
          perfiles (part1_core/.../ExternalConfiguration).
        - SERVIDOR EMBEBIDO: la aplicación ES el servidor: un solo jar que
          se corre con java -jar (CAPA 7).
        - LISTO PARA PRODUCCIÓN: salud, métricas, logs (part6_production).

        Spring Boot NO reemplaza a Spring: es Spring, ya armado. Todo lo de
        Spring Core sigue ahí; Boot decide los valores por defecto ("opinado")
        y te deja cambiar cualquiera.
    */

    /*
        =================================================================
        CAPA 3) MAVEN: LO MÍNIMO QUE TIENES QUE ENTENDER
        =================================================================

        MAVEN es la herramienta que compila, prueba, empaqueta y, sobre todo,
        DESCARGA LAS LIBRERÍAS de un proyecto. (La sección 9 del ROADMAP lo
        verá completo; aquí lo que necesitas para Spring.)

        El archivo pom.xml describe el proyecto:

            <parent>spring-boot-starter-parent 4.1.2</parent>
            <dependencies>
                <dependency>spring-boot-starter</dependency>      (sin versión)
                <dependency>spring-boot-starter-test</dependency> (scope test)
            </dependencies>

        - EL PARENT de Spring Boot trae una lista enorme de librerías CON
          SUS VERSIONES, probadas juntas (un "BOM", bill of materials). Por
          eso tus dependencias no llevan versión. Cambiar la versión de Boot
          actualiza todas a la vez, compatibles. Poner versiones a mano a
          librerías que Boot ya administra es la causa número uno de errores
          raros (NoSuchMethodError, ClassNotFoundException: v12_jvm/
          part2_classes/ClassLoadingAndBytecode, CAPA 9).
        - SCOPE: compile (por defecto: al compilar y al correr), runtime
          (solo al correr: el driver de la base de datos), test (solo en las
          pruebas).
        - Las librerías se descargan de Maven Central una vez y se guardan
          en ~/.m2/repository (medido: 165 MB para todos los starters que
          usará esta sección).
        - mvnw (Maven Wrapper): un script que descarga la versión exacta de
          Maven que pide el proyecto. Todo el equipo y el servidor de
          integración usan la misma. Se usa ./mvnw en vez de mvn.

        Los comandos del día a día:
            ./mvnw compile                 compilar
            ./mvnw test                    compilar y correr las pruebas
            ./mvnw package                 todo lo anterior + el jar
            ./mvnw spring-boot:run         correr la aplicación
            ./mvnw dependency:tree         ver QUÉ librerías llegaron y por qué
                                           (la herramienta para conflictos)

        Este proyecto es MULTI-MÓDULO: v15_spring/pom.xml reúne módulos
        (part1_core, ...), cada uno con su pom y solo sus dependencias; -pl
        elige el módulo.
    */

    /*
        =================================================================
        CAPA 4) LA FORMA DE UN PROYECTO SPRING BOOT
        =================================================================

            pom.xml
            src/main/java/com/empresa/tienda/
                TiendaApplication.java       @SpringBootApplication + main
                orders/ ...                  tu código, por funcionalidad
            src/main/resources/
                application.yml              la configuración
                static/, templates/          archivos web (si hay)
                db/migration/                scripts de la base de datos
            src/test/java/com/empresa/tienda/ las pruebas, mismos paquetes

        La clase con @SpringBootApplication va en el PAQUETE RAÍZ. Esa
        anotación junta tres:
            @SpringBootConfiguration   esta clase es configuración
            @EnableAutoConfiguration   activa la autoconfiguración (CAPA 2)
            @ComponentScan             busca beans (@Component, @Service...)
                                       en ESTE paquete y sus subpaquetes

        Si la pones en un subpaquete, Spring no encuentra las clases que
        están "al lado" o "arriba", y aparecen errores de "no se encontró un
        bean". Es el error más común del primer día.

        Aquí, DemoApplication está en com.practice.spring.core.startup, y
        encuentra a StartupReport (un @Component del mismo paquete) sin que
        nadie se lo diga.
    */
    @SpringBootApplication
    public static class DemoApplication {

        public static void main(String[] args) {
            SpringApplication.run(DemoApplication.class, args);   // todo empieza aquí (CAPA 5)
        }
    }

    /*
        =================================================================
        CAPA 5) QUÉ PASA AL ARRANCAR, PASO A PASO
        =================================================================

        SpringApplication.run(DemoApplication.class, args) hace, en orden
        (y avisa de cada paso con un EVENTO; medido en la prueba):

            ApplicationStartingEvent             arrancó; aún no hay nada
            ApplicationEnvironmentPreparedEvent  ya leyó la configuración
                                                 (application.yml, variables
                                                 de entorno, argumentos)
            ApplicationContextInitializedEvent   creó el contenedor vacío
            ApplicationPreparedEvent             registró las DEFINICIONES de
                                                 beans (qué hay que crear)
            ContextRefreshedEvent                CREÓ todos los beans y los
                                                 conectó (aquí falla si falta
                                                 una dependencia)
            ApplicationStartedEvent              listo, antes de los runners
            (los ApplicationRunner corren aquí: CAPA 6)
            ApplicationReadyEvent                LISTA para atender
            ...
            ContextClosedEvent                   al apagarse

        Lo importante:
        - el contenedor se ARMA COMPLETO al arrancar: si un bean necesita
          algo que no existe, la aplicación NO arranca (y el error dice
          exactamente qué falta). Es mejor así: el error aparece al
          desplegar, no a medianoche en una petición;
        - una aplicación Boot sin nada propio ya tiene 50 beans (medido):
          los de la autoconfiguración. Con web y base de datos, cientos;
        - medido: arrancar la aplicación vacía tardó ~0.9 s. Las grandes,
          de 5 a 30 s: por eso importa no hacer trabajo pesado al arrancar.
    */

    /*
        =================================================================
        CAPA 6) HACER ALGO AL ARRANCAR: RUNNERS Y EVENTOS
        =================================================================

        Para ejecutar código cuando la aplicación arrancó (cargar datos de
        prueba, revisar una conexión, imprimir información):

        - ApplicationRunner (o CommandLineRunner): un bean con un método
          run(args). Corre después de crear todos los beans, antes de que la
          aplicación se declare lista. Recibe los argumentos de la línea de
          comandos ya interpretados (--opcion=valor).
        - @EventListener sobre un método que recibe un evento, como
          ApplicationReadyEvent: corre cuando ese evento se publica.

        Cuidado: si un runner lanza una excepción, la aplicación NO
        arranca. Y si tarda mucho, retrasa que esté lista.

        StartupReport es un @Component (CAPA 4) que hace las dos cosas y
        anota lo que vio, para que la prueba lo revise.
    */
    @Component
    public static class StartupReport implements ApplicationRunner {
        private final List<String> notes = new CopyOnWriteArrayList<>();

        @Override
        public void run(ApplicationArguments args) {
            notes.add("runner: modo=" + args.getOptionValues("modo"));   // --modo=demo
        }

        @EventListener(ApplicationReadyEvent.class)
        public void onReady() {
            notes.add("lista");
        }

        public List<String> notes() {
            return List.copyOf(notes);
        }
    }

    /*
        =================================================================
        CAPA 7) EL JAR EJECUTABLE
        =================================================================

        ./mvnw package con el plugin spring-boot-maven-plugin produce un jar
        "ejecutable" (también llamado "fat jar"):

            app.jar
                META-INF/MANIFEST.MF     Main-Class: el lanzador de Boot
                BOOT-INF/classes/        TUS clases y recursos
                BOOT-INF/lib/            TODAS las librerías, como jars
                org/springframework/boot/loader/   el lanzador

        El lanzador sabe cargar clases desde los jars de BOOT-INF/lib (un
        cargador de clases propio: ClassLoadingAndBytecode, CAPA 5). Por eso
        se despliega UN archivo:

            java -jar app.jar --server.port=9090

        Medido: un proyecto con todos los starters de esta sección produjo un
        jar de 64 MB (el código propio pesaba 3 KB: el resto son las
        librerías y el servidor). Para contenedores hay una forma mejor, en
        capas (part6_production).
    */

    /*
        =================================================================
        CAPA 8) VERSIONES: BOOT 3 Y BOOT 4
        =================================================================

        En tu trabajo puede que encuentres Spring Boot 3 o 4. Lo esencial
        (contenedor, inyección, controllers, Spring Data, transacciones,
        seguridad) es IGUAL; cambian detalles:

        - Boot 3 (2022): pasó de javax.* a jakarta.* (javax.persistence ->
          jakarta.persistence, etc.). Si ves imports javax.persistence, es
          Boot 2: muy viejo.
        - Boot 4 (noviembre 2025, sobre Spring Framework 7): starters más
          pequeños y con otros nombres (spring-boot-starter-webmvc en vez de
          -web; un starter de pruebas por módulo, como
          spring-boot-starter-webmvc-test), y librerías actualizadas.
          Verificado con el generador oficial (start.spring.io).

        Estas guías usan Boot 4.1.2. Si tu proyecto es Boot 3, todo aplica;
        cuando algo sea distinto, la guía lo dirá.
    */

    /*
        =================================================================
        CAPA 9) ERRORES COMUNES
        =================================================================

        1) Creer que Spring Boot es otra cosa que Spring: es Spring con
           configuración automática (CAPA 2).

        2) Poner versiones a mano a dependencias que el parent ya
           administra; no saber usar dependency:tree (CAPA 3).

        3) La clase @SpringBootApplication fuera del paquete raíz: beans que
           "no existen" (CAPA 4).

        4) Trabajo pesado o llamadas por red en el arranque (en
           constructores o runners): arranques lentos o que fallan (CAPAS 5
           y 6).

        5) Desplegar en un servidor de aplicaciones aparte por costumbre:
           con Boot, el jar ya es el servidor (CAPA 7).

        6) Copiar ejemplos de internet de otra versión de Boot sin revisar
           (javax vs jakarta, nombres de starters) (CAPA 8).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Spring = un contenedor que crea y conecta tus objetos (beans) + un
          ecosistema de módulos sobre él.
        - Boot = Spring ya armado: starters, autoconfiguración,
          configuración externa, servidor embebido, producción.
        - Maven: el parent fija las versiones; starters sin versión; scopes;
          ./mvnw; dependency:tree.
        - @SpringBootApplication en el paquete raíz: escanea ese paquete
          hacia abajo.
        - Arranque: configuración -> definiciones -> crear todos los beans
          -> runners -> lista. Si falta algo, no arranca.
        - Un jar ejecutable con todo adentro: java -jar.
    */
}
