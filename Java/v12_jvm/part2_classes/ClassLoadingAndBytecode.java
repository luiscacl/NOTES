package v12_jvm.part2_classes;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntUnaryOperator;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

/*
    =====================================================================
    CARGA DE CLASES, BYTECODE Y JIT: DEL .CLASS AL CÓDIGO MÁQUINA
    =====================================================================

    Ruta de jvm/: guía 3 de 6  (part2_classes)
    Anterior:  part1_memory/GarbageCollector
    Siguiente: part2_classes/ReflectionAndAnnotations

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.
    - Las salidas de javap, los mensajes de error y los tiempos se
      obtuvieron en Windows 11, JDK 26. javap está en la carpeta bin del
      JDK (aquí: "C:\Program Files\Java\jdk-26\bin\javap").

    Lo que ya se vio y aquí se abre por dentro:
    - el recorrido .java -> javac -> .class -> JVM, y la lista de lo que
      hace la JVM al correr (v1_fundamentals/part1_first_steps/
      HowJavaWorks, CAPAS 1 y 10);
    - los marcos de la pila (part1_memory/StackAndHeapMemory, CAPA 2);
    - el borrado de genéricos (v4_generics/AdvancedGenerics, CAPA 8).

    Importancia: MEDIA-ALTA. No se usa a diario, pero explica errores
    reales (NoClassDefFoundError, "X cannot be cast to X"), por qué Java
    tarda en "calentar", y cómo funcionan los frameworks por dentro.

    Índice:
        CAPA 1)  El archivo .class
        CAPA 2)  Leer bytecode con javap
        CAPA 3)  Cuándo se carga y se inicializa una clase
        CAPA 4)  Cuando el inicializador falla
        CAPA 5)  Los cargadores de clases
        CAPA 6)  Una clase es su nombre MÁS su cargador
        CAPA 7)  El JIT: de interpretado a código máquina
        CAPA 8)  El arranque: compartir clases ya preparadas
        CAPA 9)  Errores comunes
        RESUMEN FINAL
*/
public class ClassLoadingAndBytecode {

    public static void main(String[] args) throws Exception {
        check(classFileExample(), "CAPA 1");
        check(lambdaBytecodeExample(), "CAPA 2");
        check(lazyInitializationExample(), "CAPA 3");
        check(failedInitializationExample(), "CAPA 4");
        check(classLoadersExample(), "CAPA 5");
        check(classIdentityExample(), "CAPA 6");
        check(jitWarmupExample(), "CAPA 7");
        System.out.println("ClassLoadingAndBytecode: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) EL ARCHIVO .CLASS
        =================================================================

        javac produce un .class por cada clase (también por cada clase
        anidada: Externa$Anidada.class). Es un archivo binario con un
        formato fijo, definido en la especificación de la JVM:

            CA FE BA BE         "número mágico": así empieza todo .class
            versión             menor y MAYOR (Java 26 = 70)
            constant pool       la tabla de constantes: textos, números,
                                nombres de clases, métodos y campos que el
                                código usa. El bytecode no dice
                                "String.length", dice "la entrada #13"
            accesos             public, final, interface, record...
            esta clase, su padre, sus interfaces
            campos
            métodos             cada uno con su BYTECODE y cuántas locales
                                y cuánto espacio de operandos necesita
            atributos           nombre del archivo fuente, números de línea
                                (para los stack traces), genéricos...

        La versión MAYOR es 44 + la versión de Java: Java 8 = 52, Java 17 =
        61, Java 21 = 65, Java 26 = 70. Una JVM no ejecuta un .class más
        nuevo que ella: lanza UnsupportedClassVersionError (CAPA 9).

        Los .class del JDK también son archivos .class (empacados dentro de
        la imagen del JDK, el archivo lib/modules). El ejemplo lee el de
        Object y revisa su cabecera.
    */
    static boolean classFileExample() throws IOException {
        try (InputStream in = Object.class.getResourceAsStream("Object.class")) {
            byte[] header = in.readNBytes(8);
            int magic = ((header[0] & 0xFF) << 24) | ((header[1] & 0xFF) << 16)
                    | ((header[2] & 0xFF) << 8) | (header[3] & 0xFF);
            int major = ((header[6] & 0xFF) << 8) | (header[7] & 0xFF);
            return magic == 0xCAFEBABE
                    && major == 44 + Runtime.version().feature();    // 70 en Java 26
        }
    }

    /*
        =================================================================
        CAPA 2) LEER BYTECODE CON JAVAP
        =================================================================

        javap -c -p Clase.class muestra el bytecode de cada método (-p
        incluye los privados). La JVM es una MÁQUINA DE PILA: las
        instrucciones toman sus datos de la pila de operandos del marco
        (StackAndHeapMemory, CAPA 2) y dejan ahí el resultado.

            static int add(int a, int b) { return a + b; }

                0: iload_0        pone la local 0 (a) en la pila
                1: iload_1        pone la local 1 (b)
                2: iadd           saca dos, suma, pone el resultado
                3: ireturn        devuelve lo que está arriba
            (javap -v agrega: stack=2, locals=2: lo que javac calculó para
            el marco)

        Leer bytecode no es para todos los días, pero muestra lo que el
        compilador hace POR TI, sin que se vea en el código:

        AUTOBOXING (v1_fundamentals/part3_text_and_references/Wrappers):
            static Integer boxed(int x) { Integer y = x; return y + 1; }
                1: invokestatic  Integer.valueOf    <- int -> Integer
                6: invokevirtual Integer.intValue   <- Integer -> int
               11: invokestatic  Integer.valueOf    <- y de vuelta
            Tres llamadas que nadie escribió.

        FOR-EACH SOBRE UNA LISTA Y BORRADO DE GENÉRICOS:
            for (int v : list) t += v;
                3: invokeinterface List.iterator
               10: invokeinterface Iterator.hasNext
               19: invokeinterface Iterator.next     <- devuelve Object
               24: checkcast     Integer             <- el genérico se borró:
               27: invokevirtual Integer.intValue        el cast lo pone javac

        CONCATENAR TEXTO:
            "Hola " + name + ", " + age
                2: invokedynamic makeConcatWithConstants
            Desde Java 9 no se generan StringBuilder: una sola instrucción
            invokedynamic, que la JVM resuelve la PRIMERA vez que se ejecuta
            con una receta optimizada. Si la JVM mejora la receta, tu código
            ya compilado mejora sin recompilar.

        LAMBDAS:
            static IntUnaryOperator twice() { return x -> x * 2; }
                0: invokedynamic applyAsInt
            El cuerpo de la lambda se compila a un método PRIVADO y static
            de tu clase (con un nombre como lambda$twice$0), y invokedynamic
            crea, la primera vez, el objeto que implementa la interface
            llamando a ese método. Por eso una lambda NO genera un archivo
            .class, a diferencia de una clase anónima (Externa$1.class).
            El ejemplo encuentra ese método escondido con reflexión.
    */
    static IntUnaryOperator twice() {
        return x -> x * 2;
    }

    static boolean lambdaBytecodeExample() {
        boolean hiddenMethod = Arrays.stream(ClassLoadingAndBytecode.class.getDeclaredMethods())
                .map(Method::getName)
                .anyMatch(name -> name.startsWith("lambda$twice$"));   // el cuerpo de la lambda
        return hiddenMethod && twice().applyAsInt(21) == 42;
    }

    /*
        =================================================================
        CAPA 3) CUÁNDO SE CARGA Y SE INICIALIZA UNA CLASE
        =================================================================

        Una clase pasa por tres fases, y cada una ocurre lo más TARDE
        posible:

            1) CARGA: un cargador (CAPA 5) busca los bytes del .class y crea
               el objeto Class.
            2) ENLACE:
               - VERIFICAR: que el bytecode sea seguro (no saltar fuera del
                 método, tipos correctos en la pila). Un .class manipulado
                 se rechaza con VerifyError.
               - PREPARAR: reservar los campos static con 0/null/false.
               - RESOLVER: convertir "la entrada #13" en la dirección real
                 del método (también perezoso: al primer uso).
            3) INICIALIZAR: ejecutar los inicializadores static (los
               "static { }" y los "static x = ...") en orden de aparición.

        ¿Qué dispara la inicialización? El PRIMER uso real: crear un
        objeto (new), llamar un método static, leer o escribir un campo
        static, inicializar una subclase, o Class.forName("..."). Por eso
        un programa con miles de clases arranca sin inicializarlas todas.

        Una excepción famosa: leer una CONSTANTE de compilación (un static
        final de tipo primitivo o String con un valor literal) NO inicializa
        la clase. javac COPIA el valor dentro de la clase que lo usa. La
        consecuencia: si una librería cambia una constante pública, el
        código que la usaba sigue con el valor VIEJO hasta que se
        recompile.

        Y una garantía útil: la JVM inicializa cada clase UNA vez, aunque
        varios hilos la usen a la vez (los demás esperan). Por eso el
        modismo "holder" da un objeto creado al primer uso y seguro entre
        hilos, sin escribir ningún bloqueo:

            final class Config {
                private static final class Holder {
                    static final Config INSTANCE = load();   // corre la 1a vez que se pide
                }
                static Config get() { return Holder.INSTANCE; }
            }
    */
    static final AtomicBoolean LAZY_INITIALIZED = new AtomicBoolean();

    static final class Lazy {
        static final int MAX_ITEMS = 10;                    // constante de compilación: javac la copia
        static final String CREATED_AT;

        static {
            LAZY_INITIALIZED.set(true);                     // efecto visible de la inicialización
            CREATED_AT = String.valueOf(System.nanoTime());
        }

        static String createdAt() {
            return CREATED_AT;
        }
    }

    static boolean lazyInitializationExample() {
        boolean notYet = !LAZY_INITIALIZED.get();
        int max = Lazy.MAX_ITEMS;                           // NO inicializa: el 10 ya estaba copiado aquí
        boolean stillNot = !LAZY_INITIALIZED.get();
        String created = Lazy.createdAt();                  // método static: AHORA sí
        return notYet && max == 10 && stillNot && LAZY_INITIALIZED.get() && created != null;
    }

    /*
        =================================================================
        CAPA 4) CUANDO EL INICIALIZADOR FALLA
        =================================================================

        Si un inicializador static lanza una excepción, la clase queda
        marcada como ERRÓNEA para siempre (en ese cargador). Medido:

            1er uso:  java.lang.ExceptionInInitializerError
                      causa: NumberFormatException: For input string: "x"
            2o uso:   java.lang.NoClassDefFoundError: Could not initialize
                      class ...$BrokenConfig

        La trampa está en el SEGUNDO error: en un servidor, el primero
        quedó en el log hace horas, y el que ves ahora es "NoClassDefFound",
        que suena a "falta un .jar". No falta nada: la clase existe, pero su
        inicialización falló antes. En el Java actual, el NoClassDefFoundError
        trae como causa el error original: hay que leer el "Caused by".

        Lección: en los inicializadores static no hagas nada que pueda
        fallar (leer archivos, conectarse, interpretar configuración). Eso
        va en un método normal, llamado en un momento controlado, donde el
        error se puede manejar.
    */
    static final class BrokenConfig {
        static final int PORT = Integer.parseInt("x");      // falla al inicializar

        static int port() {
            return PORT;
        }
    }

    static boolean failedInitializationExample() {
        Throwable first = errorOf(BrokenConfig::port);
        Throwable second = errorOf(BrokenConfig::port);
        return first instanceof ExceptionInInitializerError
                && first.getCause() instanceof NumberFormatException
                && second instanceof NoClassDefFoundError
                && second.getMessage().startsWith("Could not initialize class")
                && second.getCause() != null;               // el error original, como causa
    }

    /*
        =================================================================
        CAPA 5) LOS CARGADORES DE CLASES
        =================================================================

        Un CARGADOR DE CLASES (ClassLoader) es un objeto que, dado un nombre
        de clase, encuentra sus bytes y crea la clase. Hay tres
        principales, en jerarquía:

            BOOTSTRAP     las clases centrales (java.lang, java.util).
                          Está escrito dentro de la JVM: desde Java se ve
                          como null.
               ^
            PLATFORM      el resto de los módulos del JDK (java.sql,
                          java.net.http...).
               ^
            APP           TU código y las librerías del classpath (-cp).
                          Con "java Archivo.java" es otro: el que compila
                          tu archivo en memoria (MemoryClassLoader).

        DELEGACIÓN: cuando a un cargador le piden una clase, PRIMERO le
        pregunta a su padre, y solo si el padre no la tiene, la busca él.
        Por eso nadie puede "reemplazar" java.lang.String poniendo un
        String.class propio en el classpath: el bootstrap siempre contesta
        primero.

        Los frameworks crean sus propios cargadores para:
        - aislar aplicaciones o plugins (cada uno con sus versiones de
          librerías);
        - recargar clases sin reiniciar (las herramientas de desarrollo
          que aplican cambios en caliente);
        - generar clases al ejecutar (los proxies; v11_patterns/
          part2_structural/Proxy, CAPA 5).
    */
    static boolean classLoadersExample() {
        ClassLoader platform = ClassLoader.getPlatformClassLoader();
        ClassLoader mine = ClassLoadingAndBytecode.class.getClassLoader();
        return String.class.getClassLoader() == null                       // bootstrap
                && java.sql.Connection.class.getClassLoader() == platform  // platform
                && mine != null && mine != platform;                       // app (o el del launcher)
    }

    /*
        =================================================================
        CAPA 6) UNA CLASE ES SU NOMBRE MÁS SU CARGADOR
        =================================================================

        Para la JVM, dos clases son la MISMA solo si tienen el mismo nombre
        Y las cargó el mismo cargador. Si dos cargadores cargan el mismo
        Greeter.class, hay DOS clases Greeter distintas, con sus propios
        static, y un objeto de una no se puede usar como la otra.

        El mensaje, medido con un cast normal:

            java.lang.ClassCastException: class Greeter cannot be cast to
            class Greeter (Greeter is in unnamed module of loader
            java.net.URLClassLoader @8bcc55f; Greeter is in unnamed module
            of loader 'app')

        "Greeter no se puede convertir a Greeter" parece absurdo, hasta que
        se lee la parte de los cargadores. Pasa en la vida real cuando una
        librería está DOS veces (dentro de la aplicación y en el servidor
        donde corre), o con las herramientas de recarga en caliente.

        El ejemplo hace eso de verdad: compila un Greeter con el compilador
        del JDK (javax.tools) en una carpeta temporal, lo carga con dos
        cargadores independientes, y comprueba que son dos clases.
    */
    static boolean classIdentityExample() throws Exception {
        Path dir = Files.createTempDirectory("loaders-");
        try {
            Path source = dir.resolve("Greeter.java");
            Files.writeString(source, "public class Greeter { public String greet() { return \"hola\"; } }");
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler.run(null, null, null, "-d", dir.toString(), source.toString()) != 0) {
                return false;
            }
            URL[] path = {dir.toUri().toURL()};
            try (URLClassLoader first = new URLClassLoader(path, null);    // padre null: no delega en la app
                 URLClassLoader second = new URLClassLoader(path, null)) {
                Class<?> greeterA = first.loadClass("Greeter");
                Class<?> greeterB = second.loadClass("Greeter");
                Object fromA = greeterA.getDeclaredConstructor().newInstance();

                Throwable castError = errorOf(() -> greeterB.cast(fromA));
                return greeterA.getName().equals(greeterB.getName())        // mismo nombre...
                        && greeterA != greeterB                             // ...distinta clase
                        && castError instanceof ClassCastException
                        && castError.getMessage().equals("Cannot cast Greeter to Greeter");
            }
        } finally {
            deleteRecursively(dir);
        }
    }

    /*
        =================================================================
        CAPA 7) EL JIT: DE INTERPRETADO A CÓDIGO MÁQUINA
        =================================================================

        HotSpot tiene un intérprete y DOS compiladores JIT, y un método
        sube de nivel según cuánto se usa ("compilación por niveles"):

            nivel 0   INTÉRPRETE: ejecuta el bytecode instrucción por
                      instrucción y CUENTA llamadas y vueltas de ciclos.
            nivel 3   C1: compila rápido, poco optimizado, y sigue midiendo
                      (qué tipos llegan, qué ramas se toman).
            nivel 4   C2: compila lento, MUY optimizado, usando lo medido.

        Visto con java -XX:+PrintCompilation (un método "work" con un
        ciclo, llamado 200 veces por ronda):

            78   14 %   3   Warm::work @ 4    C1, sustituyendo el ciclo que
                                              ya corría ("%": OSR)
            93   71     4   Warm::work        C2: la versión final
            95   15     3   Warm::work   made not entrant: not used
                                              la versión C1 se descarta

        Tiempo por ronda, medido: ~2,800 us la primera, ~460 us desde la
        cuarta. Forzando solo el intérprete (java -Xint), ~2,600 us
        siempre: el intérprete es unas 6 veces más lento aquí (en otros
        códigos, 10 a 50 veces).

        Lo que C2 hace con lo medido:
        - INLINING: copia el cuerpo de métodos pequeños donde se llaman. Es
          la optimización madre: sin ella no habría análisis de escape
          (StackAndHeapMemory, CAPA 7). Los getters no cuestan nada.
        - OPTIMIZACIÓN ESPECULATIVA: si en una llamada a una interface
          siempre llegó la MISMA clase, la trata como llamada directa. Si un
          día llega otra clase, la JVM DESOPTIMIZA: descarta ese código
          ("made not entrant"), vuelve a interpretar y recompila después.
        - Eliminar código cuyo resultado no se usa: por eso medir con un
          ciclo y System.nanoTime() suele mentir (part3_performance/
          Performance).

        Consecuencia práctica: un servidor recién arrancado es más LENTO
        durante sus primeros segundos o minutos ("calentamiento"). Las
        primeras peticiones tardan más; las mediciones de rendimiento se
        hacen después de calentar.
    */
    static double work(int n) {
        double sum = 0;
        for (int i = 1; i <= n; i++) {
            sum += Math.sqrt(i) / i;
        }
        return sum;
    }

    static boolean jitWarmupExample() {
        long cold = timeRound();                            // la primera: interpretado
        long best = Long.MAX_VALUE;
        for (int round = 0; round < 20; round++) {
            best = Math.min(best, timeRound());             // después: compilado por C1 y C2
        }
        return best * 2 < cold;
    }

    private static double roundSink;

    private static long timeRound() {
        long start = System.nanoTime();
        double sum = 0;
        for (int call = 0; call < 200; call++) {
            sum += work(1_000);
        }
        roundSink += sum;                                   // usar el resultado: que el JIT no lo elimine
        return System.nanoTime() - start;
    }

    /*
        =================================================================
        CAPA 8) EL ARRANQUE: COMPARTIR CLASES YA PREPARADAS
        =================================================================

        Cargar, verificar y preparar miles de clases en cada arranque es
        trabajo repetido. Medido con -Xlog:class+load en un programa
        pequeño que usa streams, fechas, regex y el cliente HTTP: cargó
        2,235 clases, y 1,255 vinieron del "shared objects file".

        Eso es CDS (Class Data Sharing): el JDK trae un archivo con las
        clases más comunes ya cargadas y verificadas, que la JVM mapea en
        memoria al arrancar (con mmap; si corren varias JVM, el SO
        COMPARTE esas páginas entre los procesos). Viene activo por
        defecto: es el "sharing" que aparece en java -version.

        Desde Java 24 hay un paso más, la caché AOT (ahead-of-time; con un
        solo comando, como abajo, desde Java 25): se
        hace una corrida de entrenamiento que guarda las clases de TU
        aplicación ya cargadas y enlazadas, y las siguientes corridas la
        usan:

            java -XX:AOTCacheOutput=app.aot -cp app.jar Main   (entrenar)
            java -XX:AOTCache=app.aot -cp app.jar Main         (usar)

        En un programa pequeño medí casi lo mismo con y sin ella (~930 ms
        contra ~970 ms en esta máquina); la diferencia grande aparece en
        aplicaciones con miles de clases propias, como las de Spring
        (v15_spring/).
    */

    /*
        =================================================================
        CAPA 9) ERRORES COMUNES
        =================================================================

        1) Confundir ClassNotFoundException con NoClassDefFoundError:
           - ClassNotFoundException: se pidió una clase POR NOMBRE
             (Class.forName("no.Existe"), un cargador) y no está. Checked.
           - NoClassDefFoundError: el código se COMPILÓ con la clase, pero
             al ejecutar falta (un .jar que no se incluyó), o su
             inicialización ya falló (CAPA 4).

        2) UnsupportedClassVersionError: el .class se compiló para un Java
           más nuevo que el que lo ejecuta (su mensaje dice "class file
           version 70.0" y hasta qué versión entiende la JVM). Se arregla
           actualizando el Java que ejecuta, o compilando para el viejo con
           javac --release 21 (que además impide usar APIs que el 21 no
           tiene).

        3) Cambiar una constante pública de una librería y no recompilar a
           quien la usa (CAPA 3).

        4) Lógica que puede fallar en inicializadores static; ignorar el
           "Caused by" de un NoClassDefFoundError (CAPA 4).

        5) La misma librería dos veces en distintos cargadores: "X cannot be
           cast to X" (CAPA 6).

        6) Medir el rendimiento sin calentar, o con un ciclo cuyo
           resultado no se usa (CAPA 7).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Un .class empieza con CAFEBABE y su versión (44 + Java); tiene
          constant pool, campos y métodos con bytecode.
        - javap -c muestra lo que javac agrega por ti: boxing, iteradores,
          casts del borrado, invokedynamic para concatenar y para lambdas.
        - Carga -> enlace (verificar, preparar, resolver) -> inicialización,
          lo más tarde posible; las constantes de compilación se copian.
        - Un inicializador static que falla deja la clase rota:
          ExceptionInInitializerError y luego NoClassDefFoundError.
        - Cargadores: bootstrap, platform, app, con delegación al padre.
          Una clase = nombre + cargador.
        - JIT por niveles: intérprete -> C1 -> C2, con inlining y
          optimizaciones especulativas que se pueden deshacer. Calentar
          antes de medir.
        - CDS (y la caché AOT) aceleran el arranque compartiendo clases ya
          preparadas.
    */

    // -----------------------------------------------------------------
    // Utilidades internas de la guía.
    // -----------------------------------------------------------------

    private interface Action {
        void run() throws Exception;
    }

    private static Throwable errorOf(Action action) {
        try {
            action.run();
            return null;
        } catch (Throwable t) {
            return t;
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    private static void check(boolean condition, String layer) {
        if (!condition) {
            throw new IllegalStateException("El ejemplo de la " + layer + " no hace lo que la guía explica");
        }
    }
}
