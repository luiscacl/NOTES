package com.practice.tools.build.maven;

/*
    =====================================================================
    MAVEN A FONDO: CÓMO SE CONSTRUYE UN PROYECTO Y DE DÓNDE SALE CADA JAR
    =====================================================================

    Ruta de tools/: guía 1  (build)
    Anterior:  v8_apis/part2_io_and_network/Serialization
    Siguiente: libraries/jackson/Jackson  (Gradle se omitió: ver ROADMAP, sección 9)

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo. No tiene código propio:
      lo que se estudia son archivos pom.xml, que aparecen en el texto y en
      las pruebas.
    - Las comprobaciones son MavenTest: cada prueba crea un proyecto de
      muestra en una carpeta temporal y corre Maven DE VERDAD sobre él
      (con su propio wrapper). Desde v9_tools/:
          ./mvnw test -Dtest=MavenTest          (PowerShell: .\mvnw.cmd)
      Tarda unos 2 minutos (medido: 110 s): son 13 corridas de Maven.
    - El pom.xml de v9_tools/ es en sí mismo un ejemplo: un proyecto SIN el
      parent de Spring Boot, con todo escrito a mano.

    Lo que ya se vio y NO se repite aquí (part1_core/.../startup/
    SpringAndBoot, CAPA 3): qué es Maven, el pom con el parent de Spring
    como BOM, los scopes compile/runtime/test, ~/.m2/repository, el wrapper
    mvnw, los comandos del día a día y -pl en multi-módulo. También:
    NoSuchMethodError y NoClassDefFoundError (v12_jvm/part2_classes/
    ClassLoadingAndBytecode), el jar ejecutable y el build reproducible
    (v15_spring/part6_production/.../packaging/PackagingAndDocker),
    Surefire vs Failsafe (v15_spring/part4_testing/IntegrationTests).

    Importancia: ALTA. En el trabajo casi nunca escribes un pom desde cero,
    pero SÍ te toca agregar dependencias, entender por qué el build falla en
    el servidor y no en tu máquina, y resolver conflictos de versiones. Eso
    es esta guía.

    Índice:
        CAPA 1)  Lo que el parent de Spring escondía: un pom desde cero
        CAPA 2)  El ciclo de vida: fases, plugins y goals
        CAPA 3)  Dependencias transitivas: cómo elige Maven una versión
        CAPA 4)  Las defensas: BOM, exclusiones y enforcer
        CAPA 5)  Los scopes que faltaban: provided, optional e import
        CAPA 6)  Plugins por dentro: pluginManagement y executions
        CAPA 7)  Multi-módulo a fondo: el reactor
        CAPA 8)  Maven en una empresa: SNAPSHOT, settings.xml y repositorios
        CAPA 9)  Errores comunes
        RESUMEN FINAL
*/
public final class Maven {

    private Maven() {
    }

    /*
        =================================================================
        CAPA 1) LO QUE EL PARENT DE SPRING ESCONDÍA: UN POM DESDE CERO
        =================================================================

        El pom MÁS CHICO que funciona (medido: compila y empaqueta):

            <project xmlns="http://maven.apache.org/POM/4.0.0">
              <modelVersion>4.0.0</modelVersion>
              <groupId>com.example</groupId>       quién: tu empresa al revés
              <artifactId>hello</artifactId>       qué: el nombre del proyecto
              <version>1.0.0</version>             cuál
              <properties>
                <maven.compiler.release>25</maven.compiler.release>
              </properties>
            </project>

        groupId:artifactId:version son las COORDENADAS: así se identifica
        cualquier librería en el mundo (com.fasterxml.jackson.core:
        jackson-databind:2.18.2). maven.compiler.release es el --release de
        javac (ClassLoadingAndBytecode, CAPA 9): para qué Java se compila.

        Lo que Maven da por hecho, SIN configurarlo ("convención sobre
        configuración"):

            src/main/java         tu código           -> target/classes
            src/main/resources    archivos que van en el jar
            src/test/java         pruebas             -> target/test-classes
            target/               todo lo generado (se borra con clean)
            <packaging>jar        si no dices otra cosa

        Si pones tu código en otra carpeta, Maven simplemente no lo ve.

        Medido con ese pom: ./mvnw package dejó target/hello-1.0.0.jar, y

            java -jar target/hello-1.0.0.jar
                -> no main manifest attribute, in target\hello-1.0.0.jar
            java -cp target/hello-1.0.0.jar demo.Hello
                -> hola desde Maven

        Un jar normal NO es ejecutable: es una caja de clases. Para
        "java -jar" el manifiesto debe decir Main-Class (lo configura el
        plugin jar, CAPA 6), y si hay dependencias, hace falta además
        meterlas o ponerlas en el classpath. Eso es lo que hacía por ti el
        plugin de Spring Boot (PackagingAndDocker, CAPA 1).

        Lo que también hacía el parent de Spring, y aquí está a mano en el
        pom.xml de v9_tools/: las versiones de las dependencias (CAPA 4), las
        versiones de los plugins (CAPA 2), UTF-8 para el código fuente, y la
        fecha fija para el build reproducible.
    */

    /*
        =================================================================
        CAPA 2) EL CICLO DE VIDA: FASES, PLUGINS Y GOALS
        =================================================================

        Maven no sabe compilar. Sabe ejecutar PLUGINS, y cada plugin tiene
        GOALS (tareas): compiler:compile, surefire:test, jar:jar. Lo que sí
        sabe es el CICLO DE VIDA: una lista fija de FASES, y qué goal corre en
        cada una. Las principales, en orden:

            validate   el pom está bien
            compile    src/main/java -> target/classes
            test       compila y corre las pruebas
            package    el jar
            verify     revisiones finales (pruebas de integración, Failsafe)
            install    copia el jar a TU ~/.m2 (para otros proyectos tuyos)
            deploy     lo sube al repositorio de la empresa (CAPA 8)

        Pedir una fase corre TODAS las anteriores: "package" es validate +
        compile + test + package. Medido, el pom de la CAPA 1 ejecutó, sin
        pedirlo:

            resources:3.5.0:resources     copiar src/main/resources
            compiler:3.16.0:compile
            resources:3.5.0:testResources
            compiler:3.16.0:testCompile
            surefire:3.6.0:test
            jar:3.5.1:jar

        "clean" es OTRO ciclo de vida (solo borra target/), por eso se
        escribe aparte: ./mvnw clean package.

        Fíjate en las VERSIONES: el pom no las pidió y Maven 3.10.0 eligió
        compiler 3.16.0 y surefire 3.6.0. El parent de Spring Boot fija 3.15.0
        y 3.5.6. Con otra versión de Maven, otros plugins y quizá otro
        resultado. Por eso un pom serio fija las versiones de sus plugins
        (pluginManagement en el pom de v9_tools/) y por eso existe el wrapper:
        mismo Maven, mismos plugins, en tu máquina y en el servidor.
        ./mvnw help:effective-pom muestra el pom COMPLETO, con todo lo que
        Maven agregó (medido: 8 plugins).

        Si una prueba falla, el ciclo se DETIENE ahí. Medido: con una prueba
        que esperaba 11600 y obtuvo 11601, "package" terminó con

            Tests run: 1, Failures: 1, Errors: 0, Skipped: 0 ... FAILURE!
            expected: <11600> but was: <11601>

        y NO hubo jar. Las dos formas de saltarlas no son iguales (medido):

            -DskipTests              compila las pruebas, no las corre
                                     ("Tests are skipped.")
            -Dmaven.test.skip=true   ni las compila
                                     ("Not compiling test sources")

        La segunda esconde que las pruebas ya ni compilan. Úsala solo si
        sabes por qué; y ninguna de las dos para "hacer que pase" un build.

        También se puede pedir un goal directo, sin ciclo:
        ./mvnw dependency:tree corre solo ese goal.
    */

    /*
        =================================================================
        CAPA 3) DEPENDENCIAS TRANSITIVAS: CÓMO ELIGE MAVEN UNA VERSIÓN
        =================================================================

        Pides jackson-databind; jackson-databind pide jackson-core y
        jackson-annotations. Esas llegan solas: son TRANSITIVAS. Un proyecto
        Spring real tiene decenas así (el proyecto final: 92 jars).

        El problema: dos caminos piden la MISMA librería en versiones
        distintas, y en el classpath solo puede haber una. Maven elige con
        una regla simple:

            1) gana la MÁS CERCANA a tu proyecto (menos saltos en el árbol);
            2) a la misma distancia, gana la que se DECLARÓ PRIMERO.

        No gana la más nueva. Medido, con este pom:

            jackson-databind 2.18.2             (pide jackson-core 2.18.2)
            jackson-core     2.14.0             (puesto a mano, "por si acaso")

        ./mvnw dependency:tree -Dverbose dijo:

            com.example:conflict:jar:1.0.0
            +- com.fasterxml.jackson.core:jackson-databind:jar:2.18.2:compile
            |  +- com.fasterxml.jackson.core:jackson-annotations:jar:2.18.2:compile
            |  \- (com.fasterxml.jackson.core:jackson-core:jar:2.18.2:compile
            |       - omitted for conflict with 2.14.0)
            \- com.fasterxml.jackson.core:jackson-core:jar:2.14.0:compile

        La 2.14.0 está a un salto; la 2.18.2, a dos. Ganó la vieja. El
        proyecto COMPILÓ sin quejarse, y al correr:

            java.lang.NoSuchMethodError: 'void com.fasterxml.jackson.core.
                util.BufferRecycler.releaseToPool()'
                at com.fasterxml.jackson.databind.ObjectMapper.writeValueAsString

        databind 2.18 llama a un método que core 2.14 todavía no tenía. Es el
        error más traicionero de Maven: aparece en PRODUCCIÓN, en la línea
        que por primera vez usa esa parte de la librería
        (ClassLoadingAndBytecode, CAPA 9).

        Cuando veas NoSuchMethodError, NoClassDefFoundError o
        ClassNotFoundException con una librería de por medio, lo primero es
        dependency:tree -Dverbose y buscar "omitted for conflict".
    */

    /*
        =================================================================
        CAPA 4) LAS DEFENSAS: BOM, EXCLUSIONES Y ENFORCER
        =================================================================

        DEPENDENCYMANAGEMENT. Una sección del pom que NO agrega
        dependencias: solo dice "si esta librería aparece, en cualquier
        nivel del árbol, usa ESTA versión". Gana sobre la regla de la CAPA 3,
        también para las transitivas.

        BOM ("bill of materials"): un pom que solo trae un dependencyManagement
        con las versiones de una familia de librerías probadas juntas. Se
        IMPORTA (scope import, CAPA 5):

            <dependencyManagement>
              <dependencies>
                <dependency>
                  <groupId>com.fasterxml.jackson</groupId>
                  <artifactId>jackson-bom</artifactId>
                  <version>2.18.2</version>
                  <type>pom</type>
                  <scope>import</scope>
                </dependency>
              </dependencies>
            </dependencyManagement>

        y las dependencias ya NO llevan versión. Medido: el mismo proyecto de
        la CAPA 3 con el BOM quedó todo en 2.18.2 y corrió:
            {"sku":"TEC-01"}
        Es lo que hace el parent de Spring Boot (SpringAndBoot, CAPA 3), y
        JUnit, Jackson, Testcontainers y la nube publican el suyo. El pom de
        v9_tools/ importa el de JUnit.

        EXCLUSIONES. Si una librería trae una transitiva que no quieres (una
        versión vieja, o una que ya da otra), se corta:

            <dependency>
              ...
              <exclusions>
                <exclusion>
                  <groupId>commons-logging</groupId>
                  <artifactId>commons-logging</artifactId>
                </exclusion>
              </exclusions>
            </dependency>

        Úsalas con cuidado: si la librería sí la necesitaba, vuelves al
        NoClassDefFoundError.

        ENFORCER. El plugin maven-enforcer-plugin revisa reglas ANTES de
        compilar. Con la regla dependencyConvergence, el proyecto de la CAPA 3
        falló en "validate" (medido):

            Dependency convergence error for com.fasterxml.jackson.core:
            jackson-core:jar:2.18.2. Paths to dependency are:
            +-com.example:conflict:jar:1.0.0
              +-com.fasterxml.jackson.core:jackson-databind:jar:2.18.2:compile
                +-com.fasterxml.jackson.core:jackson-core:jar:2.18.2:compile
            and
            +-com.example:conflict:jar:1.0.0
              +-com.fasterxml.jackson.core:jackson-core:jar:2.14.0:compile

        El error pasó de "producción, a las 3 de la mañana" a "el build, en
        tu máquina, antes de compilar". Muchas empresas lo tienen en su pom
        padre; si ves este mensaje, la solución es alinear con un BOM o un
        dependencyManagement, no quitar la regla.
    */

    /*
        =================================================================
        CAPA 5) LOS SCOPES QUE FALTABAN: PROVIDED, OPTIONAL E IMPORT
        =================================================================

        compile, runtime y test ya se vieron (SpringAndBoot). Uno más
        comprobado: lo que es test NO existe para src/main. Medido, una clase
        en src/main que importaba JUnit:

            Oops.java:[2,29] package org.junit.jupiter.api does not exist

        Los que faltaban:

            provided   se compila con ella, pero NO viaja: el que corre el
                       programa ya la tiene. Era lo normal con servidores de
                       aplicaciones (la API de servlets la daba el servidor);
                       hoy se ve con procesadores de anotaciones (Lombok) y
                       en librerías que corren dentro de otro programa.
            import     solo en dependencyManagement y con type pom: trae las
                       versiones de un BOM (CAPA 4).
            <optional>true</optional>
                       no es un scope, pero se parece: la dependencia es
                       tuya y NO se pasa a quien dependa de ti. El proyecto
                       final la usa para spring-boot-docker-compose (solo
                       desarrollo).

        system (un jar con ruta de tu disco) existe, pero está desaconsejado:
        rompe en cualquier otra máquina.
    */

    /*
        =================================================================
        CAPA 6) PLUGINS POR DENTRO: PLUGINMANAGEMENT Y EXECUTIONS
        =================================================================

        Igual que con las dependencias, hay dos secciones:

            <build><plugins>           ESTE proyecto usa el plugin
            <build><pluginManagement>  SI alguien lo usa, con esta versión y
                                       esta configuración (lo que va en un
                                       parent, para todos los módulos)

        Un plugin se configura con <configuration> (en el pom de v9_tools/:
        -Xlint:all para el compilador) y se ENGANCHA a una fase con
        <executions>:

            <plugin>
              <groupId>org.springframework.boot</groupId>
              <artifactId>spring-boot-maven-plugin</artifactId>
              <executions>
                <execution>
                  <goals><goal>build-info</goal></goals>
                </execution>
              </executions>
            </plugin>

        Ese goal ya trae su fase por defecto (generate-resources), así que
        corre solo en cada build: es como PackagingAndDocker generó el
        build-info. Si no la trae, se agrega <phase>. Así funcionan también
        Failsafe en verify y el enforcer de la CAPA 4 en validate.

        Las propiedades se usan con ${...}: ${project.version} (la del pom),
        las tuyas en <properties>, las que pases con -D, y las del sistema.
        Cuidado con ${java.version}: en el parent de Spring es una propiedad
        suya (para qué Java se compila); sin ese parent es la versión del
        JDK que está corriendo Maven, que no es lo mismo.
    */

    /*
        =================================================================
        CAPA 7) MULTI-MÓDULO A FONDO: EL REACTOR
        =================================================================

        Un pom con <packaging>pom</packaging> y <modules> es un AGREGADOR:
        construye varios proyectos juntos. Cada módulo suele declarar ese pom
        como <parent> para heredar su configuración. Son dos cosas distintas
        (agregar y heredar) que casi siempre van en el mismo pom, como en
        v15_spring/pom.xml.

        El REACTOR es la parte de Maven que ordena los módulos. Medido con un
        padre que declaraba <module>app</module> ANTES que
        <module>pricing</module>, y app dependiendo de pricing:

            Reactor Build Order:
              shop-parent    [pom]
              pricing        [jar]
              app            [jar]

        El orden sale de las DEPENDENCIAS, no de cómo los escribiste.

        Y lo que pasa al construir UN módulo (medido, después de clean):

            ./mvnw -pl app package
                -> The POM for com.example:pricing:jar:1.0.0-SNAPSHOT is
                   missing ... Could not resolve dependencies
            ./mvnw -pl app -am package
                -> construye pricing y luego app: BUILD SUCCESS

        Con -pl solo, Maven busca pricing en ~/.m2 (y en internet), no en la
        carpeta de al lado. Las opciones:
            -am  (also make)            también lo que app NECESITA
            -amd (also make dependents) también lo que DEPENDE de app
            ./mvnw install              deja pricing en ~/.m2 para siempre
                                        (útil entre proyectos distintos, pero
                                        cuidado: luego se usa esa copia vieja
                                        aunque cambies el código)
    */

    /*
        =================================================================
        CAPA 8) MAVEN EN UNA EMPRESA: SNAPSHOT, SETTINGS.XML Y REPOSITORIOS
        =================================================================

        SNAPSHOT vs RELEASE. 1.0.0-SNAPSHOT significa "en desarrollo, puede
        cambiar": Maven busca una copia nueva periódicamente (una vez al día
        por defecto; -U lo fuerza). 1.0.0 es una versión liberada y NUNCA
        cambia: si se publica, es para siempre. Por eso nada que vaya a
        producción debería depender de un SNAPSHOT: lo que pruebas hoy no es
        lo que corre mañana.

        EL REPOSITORIO DE LA EMPRESA. En el trabajo, Maven casi nunca baja
        directo de Maven Central. Hay un servidor interno (Nexus o
        Artifactory son los usuales) que:
        - hace de espejo de Central (más rápido, y sigue funcionando si
          internet falla);
        - guarda las librerías internas de la empresa (lo que sube "deploy");
        - y a veces bloquea librerías con vulnerabilidades conocidas.

        Eso se configura en ~/.m2/settings.xml, NO en el pom:

            <settings>
              <mirrors>
                <mirror>
                  <id>empresa</id>
                  <mirrorOf>*</mirrorOf>
                  <url>https://repo.empresa.example/maven</url>
                </mirror>
              </mirrors>
              <servers>
                <server>
                  <id>empresa</id>
                  <username>${env.REPO_USER}</username>
                  <password>${env.REPO_TOKEN}</password>
                </server>
              </servers>
            </settings>

        Las CREDENCIALES van en settings.xml (o en variables de entorno), nunca
        en el pom: el pom va al repositorio de código y lo lee todo el
        equipo. Si tu primer build en el trabajo falla con "Could not
        transfer artifact" o un 401, casi siempre falta este archivo: pídelo
        a tu equipo.

        Otras banderas útiles:
            -o                  sin red: solo lo que ya está en ~/.m2
            -P nombre           activar un <profile> del pom (configuración
                                alterna, p. ej. para el servidor de CI)
            -X                  todo el detalle, para depurar el build

        ACTUALIZAR. ./mvnw versions:display-dependency-updates lista lo que
        tiene versiones más nuevas. Medido en el proyecto de la CAPA 4:

            jackson-annotations ..... 2.18.2 -> 3.0-rc5
            jackson-core ............ 2.18.2 -> 2.22.4
            jackson-databind ........ 2.18.2 -> 2.22.4

        Ojo con la primera: 3.0-rc5 es una versión candidata (rc) que nunca
        se liberó: Jackson 3 se quedó con jackson-annotations 2.x (su BOM
        3.1.7 trae la 2.21) y la última real es 2.22. La herramienta solo
        compara números: dice qué existe, no qué conviene. Se actualiza el BOM (una línea), se corren las pruebas, y
        se revisan las notas de cambios cuando cambia el primer número.
    */

    /*
        =================================================================
        CAPA 9) ERRORES COMUNES
        =================================================================

        1) Código fuera de src/main/java, o pruebas fuera de src/test/java:
           Maven no las ve (CAPA 1).

        2) Esperar que "java -jar" funcione con el jar normal (CAPA 1).

        3) No fijar versiones de plugins; saltarse las pruebas con
           -Dmaven.test.skip para que "pase" (CAPA 2).

        4) Poner a mano la versión de una transitiva "por si acaso": gana
           la cercana, aunque sea vieja (CAPA 3).

        5) Arreglar un conflicto quitando la regla del enforcer en vez de
           alinear versiones con un BOM (CAPA 4).

        6) Usar en src/main algo con scope test; scope system (CAPA 5).

        7) Confundir plugins con pluginManagement (el plugin "no corre") y
           dependencies con dependencyManagement (la librería "no llega")
           (CAPAS 4 y 6).

        8) Construir un módulo con -pl sin -am y usar sin saberlo una copia
           vieja de ~/.m2 (CAPA 7).

        9) Credenciales en el pom; depender de SNAPSHOTs en producción;
           actualizar a una versión rc porque la herramienta la sugirió
           (CAPA 8).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Coordenadas groupId:artifactId:version; estructura src/main,
          src/test, target por convención.
        - Fases en orden (validate ... deploy); pedir una corre las
          anteriores; los goals de plugins hacen el trabajo. Fija las
          versiones de los plugins.
        - Transitivas: gana la más cercana, luego la primera declarada; NO la
          más nueva. dependency:tree -Dverbose y "omitted for conflict".
        - BOM con scope import + dependencyManagement alinean versiones;
          exclusions cortan; enforcer detecta antes de compilar.
        - provided (lo da quien corre), optional (no se hereda), import (BOM).
        - plugins vs pluginManagement; executions enganchan goals a fases.
        - El reactor ordena por dependencias; -pl -am.
        - SNAPSHOT cambia, release no; settings.xml con el espejo y las
          credenciales de la empresa, nunca en el pom.
    */
}
