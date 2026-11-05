package com.practice.spring.production.packaging;

import java.util.Map;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/*
    =====================================================================
    EMPAQUETAR Y CORRER EN DOCKER: DEL CÓDIGO A UN CONTENEDOR EN PRODUCCIÓN
    =====================================================================

    Ruta de spring/: guía 19  (part6_production)
    Anterior:  part6_production/.../async/AsyncSchedulingCaching
    Siguiente: final_project (la tienda completa)

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo. El código de la
      aplicación es mínimo a propósito: lo importante está AFUERA, en
      part6_production/Dockerfile, .dockerignore, compose.yaml y el
      pom.xml (spring-boot-maven-plugin).
    - La comprobación es PackagedImageIT: toma el jar YA construido,
      construye la imagen con el Dockerfile y la arranca en Docker. Por eso
      es una prueba "IT" (de integración) que corre después de empaquetar.
      Desde v15_spring/:
          ./mvnw -pl part6_production verify -Dit.test=PackagedImageIT
    - Los tiempos y tamaños se midieron a mano con docker (Windows 11,
      Docker Desktop 29, Java 25 en la imagen).

    Lo que ya se vio: configuración externa y variables de entorno
    (part1_core/.../config/ExternalConfiguration), liveness y readiness
    (observability/Observability, CAPA 3), hilos y señales del SO
    (v13_concurrency/part1_threads/ThreadBasics), memoria de la JVM y el
    GC (v12_jvm/part1_memory/), monolito vs microservicios
    (v14_architecture/part3_systems/MonolithVsMicroservices).

    Importancia: ALTA. Hoy casi toda aplicación Spring Boot se entrega como
    imagen de Docker. No necesitas ser experto en infraestructura, pero sí
    entender qué hay en la imagen y por qué.

    Índice:
        CAPA 1)  El jar ejecutable: todo en un archivo
        CAPA 2)  El mismo jar en cada ambiente
        CAPA 3)  Docker y las capas: no subir 25 MB por un cambio de 30 KB
        CAPA 4)  El Dockerfile, línea por línea
        CAPA 5)  Un contenedor bien portado: usuario, señales y memoria
        CAPA 6)  Arrancar más rápido: la caché AOT de Java
        CAPA 7)  Sin Dockerfile: buildpacks
        CAPA 8)  Docker Compose: varios contenedores juntos
        CAPA 9)  Errores comunes
        RESUMEN FINAL
*/
public class PackagingAndDocker {

    /*
        =================================================================
        CAPA 1) EL JAR EJECUTABLE: TODO EN UN ARCHIVO
        =================================================================

        "./mvnw package" compila, corre las pruebas y empaqueta. Un jar
        normal de Java solo trae TUS clases: para correrlo necesitas también
        las de Spring, Jackson, Tomcat... (el "classpath"). El
        spring-boot-maven-plugin (ver el pom.xml) lo REEMPAQUETA en un jar
        ejecutable (le dicen "fat jar") que trae todo adentro. Medido:

            part6_production-1.0.0-SNAPSHOT.jar.original      19.7 KB
            part6_production-1.0.0-SNAPSHOT.jar           24.8 MB
                (51 jars de dependencias en BOOT-INF/lib/)

        Por dentro:
            BOOT-INF/classes/     tus clases y application.properties
            BOOT-INF/lib/         las dependencias, cada una como jar
            org/springframework/boot/loader/   el "lanzador"
            META-INF/MANIFEST.MF  qué arrancar

        Java no sabe leer jars dentro de un jar. Por eso el manifiesto dice
        (medido):
            Main-Class:  org.springframework.boot.loader.launch.JarLauncher
            Start-Class: com.practice.spring.production.packaging.
                         PackagingAndDocker$PackagedShopApplication
        Java arranca el JarLauncher de Spring Boot, que sabe leer
        BOOT-INF/lib, y él llama al main de TU clase (Start-Class). Este
        módulo tiene tres aplicaciones, así que el pom.xml dice cuál con
        <mainClass>. (El paquete del lanzador cambió en Boot 3.2: antes era
        org.springframework.boot.loader.JarLauncher.)

        Se corre con:
            java -jar target/part6_production-1.0.0-SNAPSHOT.jar

        El plugin también hace build-info: /actuator/info mostró, medido,
            {"build":{"artifact":"part6_production","version":"1.0.0-SNAPSHOT",
             "time":"1980-02-01T00:00:00Z",...}}
        La fecha de 1980 no es un error: el pom padre de Spring Boot fija
        la fecha de los archivos para que DOS compilaciones del mismo código
        den un jar IDÉNTICO byte por byte ("build reproducible"). Así Docker
        reconoce que nada cambió (CAPA 3).
    */
    @SpringBootApplication
    public static class PackagedShopApplication {
        public static void main(String[] args) {
            new SpringApplicationBuilder(PackagedShopApplication.class)
                    .properties("spring.config.name=packaged-shop")    // lee packaged-shop.properties
                    .run(args);
        }
    }

    /*
        =================================================================
        CAPA 2) EL MISMO JAR EN CADA AMBIENTE
        =================================================================

        La regla: se construye UNA vez, y ese mismo jar (esa misma imagen)
        pasa por pruebas, preproducción y producción. Lo que cambia entre
        ambientes (URL de la base, contraseñas, niveles de log) entra desde
        AFUERA, casi siempre como variables de entorno
        (ExternalConfiguration): MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE es
        management.endpoints.web.exposure.include.

        Medido: el jar expone health e info (packaged-shop.properties); con
            docker run -e MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,info,env tienda
        /actuator/env respondió 200, sin recompilar nada.

        Las contraseñas NUNCA van dentro de la imagen (ni en el
        application.properties, ni en un ENV del Dockerfile): cualquiera que
        pueda descargar la imagen puede leerlas con "docker history". Las
        pone el orquestador al arrancar (los secretos de Kubernetes o de tu
        nube).

        StatusController existe solo para tener algo que llamar.
    */
    @RestController
    public static class StatusController {
        @GetMapping("/api/status")
        public Map<String, String> status() {
            return Map.of("status", "OK", "java", Runtime.version().toString());
        }
    }

    /*
        =================================================================
        CAPA 3) DOCKER Y LAS CAPAS: NO SUBIR 25 MB POR UN CAMBIO DE 30 KB
        =================================================================

        Docker en dos frases: una IMAGEN es un sistema de archivos empacado
        (un Linux mínimo + Java + tu aplicación); un CONTENEDOR es un
        proceso que corre con esa imagen, aislado del resto de la máquina
        por el kernel de Linux (namespaces para que no vea otros procesos ni
        archivos, cgroups para limitar su memoria y CPU). No es una máquina
        virtual: comparte el kernel. En Windows, Docker Desktop corre ese
        kernel de Linux en una máquina virtual ligera (WSL 2).

        Una imagen se forma por CAPAS: cada instrucción del Dockerfile agrega
        una. Al reconstruir, Docker reusa (caché) las capas que no cambiaron,
        y al subir la imagen al registro solo sube las capas nuevas.

        Si copias el jar como UN archivo, cada cambio en tu código cambia los
        24.8 MB completos. Pero las dependencias cambian rara vez y tu código
        cambia diario. Por eso Spring Boot sabe partir el jar en capas
        (medido con java -Djarmode=tools -jar app.jar list-layers):

            dependencies            las librerías con versión fija   24.7 MB
            spring-boot-loader      el lanzador
            snapshot-dependencies   librerías -SNAPSHOT (cambian seguido)
            application             tus clases y recursos            32.8 KB

        Medido, reconstruyendo la imagen:
        - después de cambiar un TEXTO en StatusController: Docker reusó la
          capa de dependencias (24.7 MB) y rehízo solo la de la aplicación
          (32.8 KB) y la caché AOT (CAPA 6);
        - después de cambiar solo un COMENTARIO: todo salió de la caché, en
          3 s. El bytecode no cambió y, como el build es reproducible (CAPA
          1), el jar fue idéntico byte por byte.

        Boot 3 vs 4: hasta Boot 3.2 se usaba -Djarmode=layertools; desde
        3.3 es -Djarmode=tools, y en Boot 4 el viejo ya no existe (medido:
        "Error: Unsupported jarmode 'layertools'"). Si ves layertools en un
        Dockerfile de tu trabajo, es de Boot 3.
    */

    /*
        =================================================================
        CAPA 4) EL DOCKERFILE, LÍNEA POR LÍNEA
        =================================================================

        Está en part6_production/Dockerfile. Las ideas:

        - DOS ETAPAS ("multi-stage"): la primera (builder) parte el jar en
          capas con jarmode=tools extract; la segunda copia solo el
          resultado. Lo que la primera usó no llega a la imagen final.
        - La base es eclipse-temurin:25-jre: un Ubuntu con el JRE (para
          CORRER Java) y no el JDK (para compilar): menos peso y menos
          herramientas para un atacante. Medido: la base pesa 480 MB y la
          imagen final 594 MB.
        - Las capas se copian de la que MENOS cambia a la que MÁS: Docker
          invalida la caché de una capa y de todas las que siguen. Por eso
          el useradd va ARRIBA: medido, cuando estaba al final se rehacía en
          cada cambio de código. La caché AOT sí va después de la aplicación
          (depende de ella): cada cambio la regenera y la sube otra vez
          (57 MB). Es el precio de arrancar más rápido.
        - .dockerignore deja pasar solo target/*.jar. Sin él, "docker build"
          manda TODA la carpeta al motor de Docker (el "build context"),
          incluidos archivos que no deberían terminar en una imagen.
        - La etiqueta de la base es fija (25-jre, no "latest"): "latest"
          cambia sin avisar y dos compilaciones del mismo código darían
          imágenes distintas. Los equipos más estrictos fijan hasta el
          digest (eclipse-temurin:25-jre@sha256:...).

        Lo que se construye en el Dockerfile es la IMAGEN, no el jar: el jar
        sale de Maven antes (en el pipeline de CI: compilar y probar, luego
        construir y subir la imagen).
    */

    /*
        =================================================================
        CAPA 5) UN CONTENEDOR BIEN PORTADO: USUARIO, SEÑALES Y MEMORIA
        =================================================================

        USUARIO. Medido: la imagen base corre como uid=0 (root). Si alguien
        logra ejecutar código en tu aplicación, sería root dentro del
        contenedor, a un paso del host. El Dockerfile crea un usuario de
        sistema y cambia a él (USER spring); medido: uid=1001(spring).

        SEÑALES. "docker stop" (y Kubernetes al reemplazar un pod) manda
        SIGTERM al proceso 1 del contenedor, espera un tiempo, y si sigue
        vivo manda SIGKILL (no se puede atrapar: muerte inmediata). Java
        atrapa SIGTERM y corre sus "shutdown hooks"; Spring Boot cierra en
        orden: deja de aceptar peticiones, espera las que están en curso
        (apagado ordenado, el valor por defecto desde Boot 3.4), cierra el
        pool de conexiones. Medido con la forma "exec" del ENTRYPOINT
        (["java", ...]): java fue el PID 1, el log dijo
            GracefulShutdown : Commencing graceful shutdown. Waiting for
                               active requests to complete
            GracefulShutdown : Graceful shutdown complete
        y salió con código 143 (128 + 15, la señal SIGTERM) en 1.7 s.

        Con la forma "shell" (ENTRYPOINT java -jar app.jar, sin corchetes,
        o sh -c "..."), el PID 1 es sh y java es un hijo (medido: PID 1 sh,
        PID 7 java). sh no le pasa la señal: Docker terminó matándolo con
        SIGKILL, código 137 (128 + 9), y el log no tuvo ni una línea de
        apagado. Las peticiones en curso se cortan.

        Cuánto espera Spring cada fase del apagado:
            spring.lifecycle.timeout-per-shutdown-phase=30s   (el default)
        y el orquestador debe esperar MÁS que eso (stop_grace_period en
        compose.yaml, terminationGracePeriodSeconds en Kubernetes).

        MEMORIA. La JVM sabe que está en un contenedor: lee el límite del
        cgroup, no la memoria de la máquina. Medido con -m 512m:
            heap máximo por defecto          123.75 MB   (el 25 %)
            con -XX:MaxRAMPercentage=75      371.25 MB
        El 25 % desperdicia el contenedor; el 100 % lo revienta, porque la
        JVM usa memoria FUERA del heap (metaspace, pilas de hilos, buffers,
        el código compilado: v12_jvm/part1_memory/StackAndHeapMemory). Si el
        proceso pasa el límite, el kernel lo mata (OOMKilled, código 137) sin
        OutOfMemoryError ni log. 75 % es un punto de partida común; se
        ajusta midiendo.

        SALUD. La imagen no trae curl ni wget (medido). No hace falta: en
        Kubernetes el chequeo lo hace el propio orquestador, llamando por
        HTTP a /actuator/health/liveness y /readiness (Observability,
        CAPA 3).
    */

    /*
        =================================================================
        CAPA 6) ARRANCAR MÁS RÁPIDO: LA CACHÉ AOT DE JAVA
        =================================================================

        Al arrancar, la JVM lee, verifica y enlaza miles de clases, y Spring
        examina anotaciones y crea beans. Se repite IGUAL en cada arranque.
        Java 25 permite guardar ese trabajo en un archivo (la caché AOT,
        "ahead of time"):

            1) un arranque de ENTRENAMIENTO, al construir la imagen:
                   java -XX:AOTCacheOutput=app.aot
                        -Dspring.context.exit=onRefresh -jar app.jar
               spring.context.exit=onRefresh hace que Spring se detenga en
               cuanto termina de crear el contexto: no se queda esperando
               peticiones;
            2) cada arranque real la usa:
                   java -XX:AOTCache=app.aot -jar app.jar

        Medido, tres arranques de cada uno en Docker:
            sin caché    5.08 s, 4.99 s, 5.12 s
            con caché    2.30 s, 2.35 s, 2.25 s      (menos de la mitad)
        a cambio de 57.7 MB más en la imagen.

        Condiciones: la caché solo sirve con la MISMA versión de Java y el
        MISMO classpath. Por eso se crea dentro del Dockerfile, con el JRE de
        la imagen, y por eso conviene correr la app desde el jar extraído
        (como aquí) y no desde el fat jar. Con Java 21 existe lo mismo en
        versión anterior (CDS: -XX:SharedArchiveFile), soportado desde Boot
        3.3.

        Importa cuando se arrancan y detienen instancias seguido: escalar
        con la carga, despliegues frecuentes. Para arranques de
        milisegundos existe la imagen NATIVA (GraalVM, spring-boot:build-
        image con -Pnative): compila todo a un ejecutable sin JVM, pero la
        compilación tarda minutos y la reflexión necesita configuración. Se
        usa en casos específicos (funciones serverless); para empezar, no.
    */

    /*
        =================================================================
        CAPA 7) SIN DOCKERFILE: BUILDPACKS
        =================================================================

            ./mvnw -pl part6_production spring-boot:build-image

        construye una imagen SIN Dockerfile: los "buildpacks" (un estándar,
        Cloud Native Buildpacks) detectan que es una aplicación Java y hacen
        lo mismo que nuestro Dockerfile: capas, un JRE, usuario sin root,
        memoria calculada para el contenedor.

        Medido (dos lecciones que no esperaba):
        - la primera vez tardó 122 s: bajó un "builder" de 1.25 GB. Después
          ya está en tu máquina (la segunda, 94 s);
        - sin configurar la versión, dijo "Using Java version 27 extracted
          from MANIFEST.MF" e instaló el JRE 27.0.1, aunque el proyecto es
          para Java 25: tomó la versión del JDK que COMPILÓ (en el manifiesto,
          Build-Jdk-Spec: 26) y la redondeó a una que tenía. Se fija con
          BP_JVM_VERSION=25 en el pom.xml; con eso, "Using Java version 25
          from BP_JVM_VERSION" y Liberica JRE 25.0.5;
        - corre como un usuario sin root (1002:1001) y calcula la memoria
          sola, suponiendo 250 hilos y 240 MB para el código compilado. Con
          -m 512m NO ARRANCÓ: "fixed memory regions require 593940K which is
          greater than 512M available". Con 1 GB arrancó en 5.6 s (sin la
          caché AOT de la CAPA 6).

        Cuál usar: los buildpacks dan una buena imagen sin que nadie del
        equipo mantenga un Dockerfile; el Dockerfile da control total y es
        lo que más vas a encontrar en los proyectos. Lo importante es
        entender qué debe tener la imagen (CAPAS 3 a 6), la haga quien la
        haga.
    */

    /*
        =================================================================
        CAPA 8) DOCKER COMPOSE: VARIOS CONTENEDORES JUNTOS
        =================================================================

        Una aplicación real necesita su base de datos, quizá Redis o una cola
        de mensajes. Docker Compose describe todos en un archivo
        (part6_production/compose.yaml) y los levanta juntos:

            docker compose up -d --build
            docker compose down

        Medido con el compose.yaml de este módulo: el contenedor
        part6_production-tienda-1 quedó "Up" con el puerto 8080 publicado, el
        log en JSON (LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs, Observability)
        dijo "Started ... in 2.247 seconds", health respondió UP, y usaba
        139.9 MiB de sus 512 MiB. "docker compose down" lo apagó (en orden:
        SIGTERM, CAPA 5) y borró su red en 2.4 s.

        Con una base de datos, el archivo crece así (lo verás funcionando en
        el proyecto final):

            services:
              db:
                image: postgres:17
                environment:
                  POSTGRES_PASSWORD: ${DB_PASSWORD}    # de un archivo .env
                healthcheck:                         # que no se cuente como lista
                  test: ["CMD", "pg_isready", "-U", "postgres"]  # hasta aceptar
                  interval: 2s                                    # conexiones
              tienda:
                build: .
                environment:
                  SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/postgres
                depends_on:
                  db:
                    condition: service_healthy

        Los contenedores se encuentran por el NOMBRE del servicio (db), no por
        localhost: cada uno tiene su propia red, y localhost dentro del
        contenedor de la tienda es la tienda misma.

        Para DESARROLLAR, Spring Boot (3.1+) trae spring-boot-docker-compose:
        al correr la aplicación en tu máquina, levanta el compose.yaml solo y
        configura la conexión sin escribir la URL (como @ServiceConnection en
        las pruebas: part4_testing/.../IntegrationTests). En producción no se
        usa Compose: se usa Kubernetes o el servicio de contenedores de tu
        nube.
    */

    /*
        =================================================================
        CAPA 9) ERRORES COMUNES
        =================================================================

        1) Construir un jar distinto por ambiente, o meter contraseñas en la
           imagen (CAPA 2).

        2) Copiar el fat jar como una sola capa: cada cambio sube 25 MB
           (CAPA 3). layertools en Boot 4 (CAPA 3).

        3) Base con el JDK completo o con "latest"; sin .dockerignore
           (CAPA 4).

        4) Correr como root; ENTRYPOINT en forma shell (sin apagado
           ordenado); el orquestador esperando menos que Spring; heap del
           100 % o el 25 % por defecto (CAPA 5).

        5) Crear la caché AOT con un Java distinto al de la imagen (CAPA 6).

        6) Usar localhost para hablar con otro contenedor; arrancar la app
           antes de que la base esté lista (CAPA 8).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - ./mvnw package: fat jar con BOOT-INF/lib; JarLauncher arranca tu
          Start-Class. build-info para /actuator/info.
        - Un jar (una imagen) para todos los ambientes; configuración y
          secretos desde afuera.
        - Dockerfile en dos etapas con jarmode=tools extract --layers: las
          dependencias quedan en una capa que casi nunca cambia.
        - JRE fijo, .dockerignore, USER sin root, ENTRYPOINT ["java", ...]
          para recibir SIGTERM (apagado ordenado, 143 vs 137).
        - -XX:MaxRAMPercentage=75: la JVM respeta el límite del contenedor.
        - Caché AOT de Java 25: arranque de 5 s a 2.3 s (medido).
        - spring-boot:build-image: imagen sin Dockerfile (buildpacks).
        - Docker Compose para levantar la app con sus servicios; en
          desarrollo, spring-boot-docker-compose.
    */
}
