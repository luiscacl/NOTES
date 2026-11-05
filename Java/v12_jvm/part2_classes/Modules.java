package v12_jvm.part2_classes;

import java.io.IOException;
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.ServiceLoader;
import java.util.Set;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/*
    =====================================================================
    MÓDULOS (JPMS): QUÉ PAQUETES VE CADA QUIEN
    =====================================================================

    Ruta de jvm/: guía 5 de 6  (part2_classes)
    Anterior:  part2_classes/ReflectionAndAnnotations
    Siguiente: part3_performance/Performance

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.
    - Este proyecto NO puede tener un module-info.java propio: todas las
      guías se compilan juntas sin módulo (CAPA 7). Por eso la guía arma
      dos módulos DE VERDAD mientras corre: escribe su código en una
      carpeta temporal, lo compila con el compilador del JDK (javax.tools,
      como en ClassLoadingAndBytecode, CAPA 6) y los carga. Los mensajes
      de error citados salieron de ahí (Windows 11, JDK 26).

    Lo que ya se vio:
    - el modificador public y los paquetes (v2_oop/part1_basics/
      AccessModifiers);
    - el InaccessibleObjectException al abrir String con reflexión
      (part2_classes/ReflectionAndAnnotations, CAPA 4).

    Importancia: MEDIA. Pocas aplicaciones se escriben como módulos, pero
    los módulos te afectan siempre: el JDK está hecho de ellos, por eso
    hay cosas internas que ya no puedes tocar, y jlink los usa para crear
    runtimes pequeños.

    Índice:
        CAPA 1)  El problema: con el classpath, public es para todos
        CAPA 2)  Qué es un módulo: module-info.java
        CAPA 3)  exports: lo no exportado no compila
        CAPA 4)  ... y tampoco se puede usar al ejecutar
        CAPA 5)  opens: dejar entrar a la reflexión
        CAPA 6)  Servicios: provides, uses y ServiceLoader
        CAPA 7)  El módulo sin nombre y los módulos automáticos
        CAPA 8)  El JDK modular y jlink
        CAPA 9)  Errores comunes
        RESUMEN FINAL
*/
public class Modules {

    public static void main(String[] args) throws Exception {
        check(jdkModulesExample(), "CAPA 1");
        Path workspace = Files.createTempDirectory("modules-");
        try {
            check(notExportedDoesNotCompileExample(workspace), "CAPA 3");
            ModuleLayer layer = buildAndLoad(workspace);
            check(notExportedAtRuntimeExample(layer), "CAPA 4");
            check(opensExample(layer), "CAPA 5");
            check(servicesExample(layer), "CAPA 6");
        } finally {
            deleteRecursively(workspace);
        }
        System.out.println("Modules: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) EL PROBLEMA: CON EL CLASSPATH, PUBLIC ES PARA TODOS
        =================================================================

        Hasta Java 8, un programa era una lista de .jar en el CLASSPATH, y
        todo se mezclaba en un solo espacio:

        - Una librería tiene paquetes públicos (su API) y paquetes internos
          (su implementación). Pero una clase public de un paquete interno
          es tan visible como la API. Alguien la usa, y la librería ya no
          puede cambiarla sin romperle el código. El caso más famoso:
          sun.misc.Unsafe, interna del JDK, usada por media industria.
        - Nada dice qué necesita cada .jar. Si falta uno, te enteras al
          EJECUTAR, con NoClassDefFoundError (ClassLoadingAndBytecode,
          CAPA 9), quizá días después, cuando se usa esa parte.
        - Si dos .jar traen la misma clase (dos versiones de una librería),
          gana la que aparezca primero en la lista. "JAR hell".

        Java 9 (2017) agregó el SISTEMA DE MÓDULOS (JPMS, Java Platform
        Module System) para resolver eso, y lo primero que modularizó fue
        el propio JDK: hoy son 69 módulos (medido con java --list-modules).
        String vive en el módulo java.base; tu código, sin module-info, en
        el "módulo sin nombre" (CAPA 7).
    */
    static boolean jdkModulesExample() {
        return String.class.getModule().getName().equals("java.base")
                && java.sql.Connection.class.getModule().getName().equals("java.sql")
                && !Modules.class.getModule().isNamed()                    // sin module-info
                && ModuleFinder.ofSystem().findAll().size() > 50;
    }

    /*
        =================================================================
        CAPA 2) QUÉ ES UN MÓDULO: MODULE-INFO.JAVA
        =================================================================

        Un MÓDULO es un grupo de paquetes con un nombre y una DESCRIPCIÓN
        de qué ofrece y qué necesita. La descripción es el archivo
        module-info.java, en la raíz de las fuentes del módulo.

        Los dos módulos de esta guía (los arma el código de abajo):

            module com.shop.pricing {
                exports com.shop.pricing.api;            // la API: otros pueden usarla
                opens com.shop.pricing.model;            // solo para reflexión (CAPA 5)
                provides com.shop.pricing.api.PriceRule  // ofrece una implementación
                    with com.shop.pricing.internal.VipRule;   // (CAPA 6)
                // com.shop.pricing.internal: ni exportado ni abierto
            }

            module com.shop.app {
                requires com.shop.pricing;               // depende de pricing
                uses com.shop.pricing.api.PriceRule;     // busca implementaciones
            }

        Las palabras:
            requires X             necesito al módulo X (se revisa al
                                   compilar Y al arrancar: si falta, no
                                   arranca, en vez de fallar días después)
            requires transitive X  y quien me use también ve X (cuando X
                                   aparece en MI API)
            requires static X      solo al compilar; al ejecutar es opcional
            exports p              el paquete p es API pública
            exports p to M         ... solo para el módulo M
            opens p                p acepta reflexión profunda (privados)
            provides I with C      C implementa el servicio I
            uses I                 busco implementaciones de I

        Y la regla central, ENCAPSULACIÓN FUERTE: dentro de un módulo,
        "public" ya no significa "para todos". Significa "para todos los
        que puedan ver este paquete", y solo ven los paquetes EXPORTADOS.
    */

    /*
        =================================================================
        CAPA 3) EXPORTS: LO NO EXPORTADO NO COMPILA
        =================================================================

        com.shop.app intenta usar VipRule, una clase PUBLIC de un paquete
        que pricing NO exporta. Medido, el compilador lo rechaza:

            error: package com.shop.pricing.internal is not visible
              (package com.shop.pricing.internal is declared in module
              com.shop.pricing, which does not export it)

        Con el classpath, eso habría compilado sin problema. Ahora la
        librería puede cambiar todo lo de internal sin romper a nadie,
        porque nadie puede depender de ello.
    */
    static boolean notExportedDoesNotCompileExample(Path workspace) throws IOException {
        Path source = workspace.resolve("bad-src");
        writePricingModule(source);
        writeAppModule(source);
        write(source, "com.shop.app/com/shop/app/Checkout.java", """
                package com.shop.app;
                public class Checkout {
                    Object rule = new com.shop.pricing.internal.VipRule();   // paquete no exportado
                }
                """);
        List<String> errors = compileModules(source, workspace.resolve("bad-out"));
        return errors.size() == 1
                && errors.getFirst().startsWith("package com.shop.pricing.internal is not visible")
                && errors.getFirst().contains("which does not export it");
    }

    /*
        =================================================================
        CAPA 4) ... Y TAMPOCO SE PUEDE USAR AL EJECUTAR
        =================================================================

        La encapsulación no es solo del compilador: la JVM la aplica al
        ejecutar. Aunque se cargue la clase por nombre (Class.forName o un
        cargador), CREARLA o llamarla desde fuera del módulo falla. Medido:

            IllegalAccessException: class ... cannot access class
            com.shop.pricing.internal.VipRule (in module com.shop.pricing)
            because module com.shop.pricing does not export
            com.shop.pricing.internal to unnamed module @2805c96b

        Lo que sí se puede: usar el objeto A TRAVÉS de lo exportado. Un
        VipRule recibido como PriceRule (la interface exportada) se llama
        sin problema (CAPA 6). Es la idea completa: se expone la interface,
        la implementación queda escondida.

        Para cargar módulos al ejecutar se usa un ModuleLayer ("capa de
        módulos"): un ModuleFinder busca los módulos compilados, una
        Configuration resuelve sus requires (falla si falta alguno), y la
        capa los carga. Es lo que hace el comando java con --module-path,
        y lo que usan las aplicaciones con plugins.
    */
    static boolean notExportedAtRuntimeExample(ModuleLayer layer) throws ClassNotFoundException {
        Module pricing = layer.findModule("com.shop.pricing").orElseThrow();
        Class<?> vipRule = layer.findLoader("com.shop.pricing").loadClass("com.shop.pricing.internal.VipRule");

        String error;
        try {
            vipRule.getDeclaredConstructor().newInstance();
            error = "";
        } catch (ReflectiveOperationException e) {
            error = e.getMessage();
        }
        return pricing.isExported("com.shop.pricing.api")
                && !pricing.isExported("com.shop.pricing.internal")
                && error.contains("does not export com.shop.pricing.internal");
    }

    /*
        =================================================================
        CAPA 5) OPENS: DEJAR ENTRAR A LA REFLEXIÓN
        =================================================================

        exports da acceso a lo PUBLIC, al compilar y al ejecutar. Pero los
        frameworks necesitan más: Jackson llena campos PRIVADOS de tus
        clases, Hibernate también (ReflectionAndAnnotations, CAPA 4). Eso
        es "reflexión profunda", y en un módulo está prohibida salvo que el
        paquete esté ABIERTO:

            opens com.shop.pricing.model;            abierto para todos
            opens com.shop.pricing.model to X;       solo para el módulo X
            open module com.shop.pricing { ... }     todo el módulo abierto

        Un paquete puede estar abierto SIN estar exportado: nadie compila
        contra él, pero la reflexión entra. Es justo lo que necesita un
        modelo que se convierte a JSON.

        El ejemplo lee el campo privado de Price (paquete abierto) y
        falla con el de VipRule (paquete cerrado):

            InaccessibleObjectException: Unable to make field private int
            com.shop.pricing.internal.VipRule.percent accessible: module
            com.shop.pricing does not "opens com.shop.pricing.internal" to
            unnamed module @2805c96b

        Es EXACTAMENTE el mismo error que da String en
        ReflectionAndAnnotations, CAPA 4: el JDK no abre sus paquetes.
    */
    static boolean opensExample(ModuleLayer layer) throws ReflectiveOperationException {
        ClassLoader loader = layer.findLoader("com.shop.pricing");

        Field cents = loader.loadClass("com.shop.pricing.model.Price").getDeclaredField("cents");
        cents.setAccessible(true);                          // paquete abierto: permitido
        Object price = cents.getDeclaringClass().getMethod("of", long.class).invoke(null, 4_500L);
        boolean openWorks = cents.getLong(price) == 4_500;

        boolean closedRejected;
        try {
            loader.loadClass("com.shop.pricing.internal.VipRule").getDeclaredField("percent").setAccessible(true);
            closedRejected = false;
        } catch (InaccessibleObjectException e) {
            closedRejected = e.getMessage().contains("does not \"opens com.shop.pricing.internal\"");
        }
        return openWorks && closedRejected;
    }

    /*
        =================================================================
        CAPA 6) SERVICIOS: PROVIDES, USES Y SERVICELOADER
        =================================================================

        ¿Cómo usa la app una implementación que no puede ver? Con
        SERVICIOS, el mecanismo de plugins del JDK:
        - pricing declara "provides PriceRule with VipRule";
        - la app declara "uses PriceRule" y pide
          ServiceLoader.load(PriceRule.class): recibe objetos que
          implementan PriceRule, sin saber de qué clase son.

        Es inversión de dependencias (v10_design/part2_solid/
        DependencyInversion) resuelta por la plataforma: agregar otra
        regla es agregar otro módulo que la "provea", sin tocar la app.

        ServiceLoader existía antes de los módulos y sigue funcionando en
        el classpath: ahí la implementación se anuncia con un archivo de
        texto META-INF/services/<nombre de la interface> que tiene el
        nombre de la clase. El JDK lo usa para encontrar, por ejemplo, los
        drivers de bases de datos (JDBC).

        En el ejemplo, la llamada se hace por el método de la INTERFACE
        (exportada): funciona y aplica el 10% de descuento. La misma llamada
        por el método de la clase VipRule fallaría (CAPA 4).
    */
    static boolean servicesExample(ModuleLayer layer) throws ReflectiveOperationException {
        Class<?> priceRule = layer.findLoader("com.shop.pricing").loadClass("com.shop.pricing.api.PriceRule");
        List<Long> results = new ArrayList<>();
        List<String> providers = new ArrayList<>();
        for (Object rule : ServiceLoader.load(layer, priceRule)) {
            providers.add(rule.getClass().getName());
            results.add((Long) priceRule.getMethod("apply", long.class).invoke(rule, 1_000L));   // por la interface
        }
        return providers.equals(List.of("com.shop.pricing.internal.VipRule"))
                && results.equals(List.of(900L));
    }

    /*
        =================================================================
        CAPA 7) EL MÓDULO SIN NOMBRE Y LOS MÓDULOS AUTOMÁTICOS
        =================================================================

        Para no romper a todo el mundo en 2017, los módulos son OPCIONALES:

        - MÓDULO SIN NOMBRE: todo lo que está en el CLASSPATH. Ve todos los
          módulos y exporta todo, como antes. Este proyecto vive ahí: por
          eso funciona sin module-info y por eso no puede tener uno (con un
          module-info, todas las guías tendrían que ser un módulo, y el
          lanzador "java Archivo.java" las trataría distinto).
        - MÓDULO AUTOMÁTICO: un .jar SIN module-info puesto en el MODULE
          PATH. Toma un nombre (del manifiesto, Automatic-Module-Name, o del
          nombre del archivo), exporta todo y lee todo. Es el puente para
          usar librerías viejas desde código modular.

        La realidad hoy:
        - las LIBRERÍAS publican module-info (o al menos un
          Automatic-Module-Name) para que se puedan usar desde módulos;
        - la mayoría de las APLICACIONES (las de Spring Boot, por ejemplo)
          corren en el classpath, sin module-info;
        - lo que SÍ afecta a todos: el JDK está encapsulado. Una librería
          que pide --add-opens o --add-exports al arrancar está usando
          partes internas del JDK.
    */

    /*
        =================================================================
        CAPA 8) EL JDK MODULAR Y JLINK
        =================================================================

        Como el JDK está partido en módulos, se puede armar un runtime de
        Java con SOLO los que usa tu aplicación. Eso hace jlink (en la
        carpeta bin del JDK):

            jlink --add-modules java.base,java.net.http,java.sql
                  --strip-debug --no-header-files --no-man-pages
                  --output mi-runtime

        Medido en esta máquina:

            JDK 26 completo                      373 MB
            runtime con java.base                 45 MB
            con java.base + java.net.http + java.sql   57 MB

        El resultado es una carpeta con su propio bin/java, que se copia
        tal cual: imágenes de contenedor más chicas, menos que descargar,
        y menos código que pueda tener fallas de seguridad. Para saber qué
        módulos usa un .jar, la herramienta jdeps (también en bin) los
        lista: jdeps --print-module-deps app.jar.
    */

    /*
        =================================================================
        CAPA 9) ERRORES COMUNES
        =================================================================

        1) Creer que public basta: en un módulo, solo lo exportado se ve
           (CAPAS 2 y 3).

        2) Exportar paquetes internos "para que compile": se pierde todo lo
           que el módulo protegía (CAPA 3).

        3) Llamar a la implementación en vez de a la interface exportada
           (CAPAS 4 y 6).

        4) Olvidar opens para los paquetes que lee un framework por
           reflexión (el modelo que va a JSON); o lo contrario, open module
           completo "por si acaso" (CAPA 5).

        5) Llenar el arranque de --add-opens en vez de actualizar la
           librería que los necesita (CAPA 7).

        6) Agregar module-info a una aplicación sin necesidad: si no vas a
           usar jlink ni encapsulación entre partes, el classpath sigue
           siendo válido (CAPA 7).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Un módulo: paquetes + module-info.java con requires, exports,
          opens, provides y uses.
        - Encapsulación fuerte: public solo es visible en paquetes
          exportados, al compilar Y al ejecutar.
        - exports: API pública. opens: solo reflexión profunda (frameworks).
        - Servicios: provides/uses + ServiceLoader, plugins sin conocer la
          clase.
        - Classpath = módulo sin nombre (todo visible); .jar sin
          module-info en el module path = módulo automático.
        - El JDK son ~70 módulos: por eso sus internos están cerrados y
          jlink arma runtimes de 45 MB.
    */

    // -----------------------------------------------------------------
    // Utilidades internas de la guía: escriben, compilan y cargan los
    // dos módulos de ejemplo.
    // -----------------------------------------------------------------

    private static void writePricingModule(Path source) throws IOException {
        write(source, "com.shop.pricing/module-info.java", """
                module com.shop.pricing {
                    exports com.shop.pricing.api;
                    opens com.shop.pricing.model;
                    provides com.shop.pricing.api.PriceRule with com.shop.pricing.internal.VipRule;
                }
                """);
        write(source, "com.shop.pricing/com/shop/pricing/api/PriceRule.java", """
                package com.shop.pricing.api;
                public interface PriceRule {
                    long apply(long cents);
                }
                """);
        write(source, "com.shop.pricing/com/shop/pricing/internal/VipRule.java", """
                package com.shop.pricing.internal;
                public class VipRule implements com.shop.pricing.api.PriceRule {
                    private int percent = 10;
                    public long apply(long cents) {
                        return cents - cents * percent / 100;
                    }
                }
                """);
        write(source, "com.shop.pricing/com/shop/pricing/model/Price.java", """
                package com.shop.pricing.model;
                public class Price {
                    private long cents;
                    public static Price of(long cents) {
                        Price price = new Price();
                        price.cents = cents;
                        return price;
                    }
                }
                """);
    }

    private static void writeAppModule(Path source) throws IOException {
        write(source, "com.shop.app/module-info.java", """
                module com.shop.app {
                    requires com.shop.pricing;
                    uses com.shop.pricing.api.PriceRule;
                }
                """);
        write(source, "com.shop.app/com/shop/app/App.java", """
                package com.shop.app;
                public class App {
                }
                """);
    }

    /** Compila los dos módulos y los carga en una capa nueva. */
    private static ModuleLayer buildAndLoad(Path workspace) throws IOException {
        Path source = workspace.resolve("src");
        Path output = workspace.resolve("out");
        writePricingModule(source);
        writeAppModule(source);
        List<String> errors = compileModules(source, output);
        if (!errors.isEmpty()) {
            throw new IllegalStateException("No compilaron los módulos: " + errors);
        }
        ModuleLayer boot = ModuleLayer.boot();
        Configuration configuration = boot.configuration()
                .resolveAndBind(ModuleFinder.of(output), ModuleFinder.of(), Set.of("com.shop.app"));   // revisa los requires
        return boot.defineModulesWithOneLoader(configuration, ClassLoader.getSystemClassLoader());
    }

    /** Como "javac --module-source-path src -d out --module ..."; devuelve los mensajes de error. */
    private static List<String> compileModules(Path source, Path output) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, Locale.ROOT, null)) {
            List<String> options = List.of("-d", output.toString(), "--module-source-path", source.toString(),
                    "--module", "com.shop.pricing,com.shop.app");
            compiler.getTask(null, files, diagnostics, options, null, null).call();
        }
        return diagnostics.getDiagnostics().stream()
                .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR)
                .map(diagnostic -> diagnostic.getMessage(Locale.ROOT))
                .toList();
    }

    private static void write(Path root, String relative, String content) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private static void deleteRecursively(Path root) throws IOException {
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    private static void check(boolean condition, String layer) {
        if (!condition) {
            throw new IllegalStateException("El ejemplo de la " + layer + " no hace lo que la guía explica");
        }
    }
}
