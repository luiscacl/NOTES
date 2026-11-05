package v12_jvm.part3_performance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;
import jdk.jfr.Configuration;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordingFile;

/*
    =====================================================================
    RENDIMIENTO: MEDIR BIEN, ENCONTRAR DÓNDE SE VA EL TIEMPO
    =====================================================================

    Ruta de jvm/: guía 6 de 6  (part3_performance)
    Anterior:  part2_classes/Modules
    Siguiente: v13_concurrency/part1_threads/ThreadBasics  (última de jvm/)

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.
    - Tiempos medidos en Windows 11, JDK 26. Las checks comparan con
      márgenes amplios, no números exactos.
    - Todo lo de esta guía viene con el JDK. JMH, la herramienta estándar
      para micro-benchmarks, es una librería que se descarga con Maven: se
      explica aquí (CAPA 4) y se practica en v9_tools/.

    Lo que ya se vio y aquí se usa:
    - el calentamiento del JIT y la eliminación de código (part2_classes/
      ClassLoadingAndBytecode, CAPA 7);
    - el log del GC, jcmd GC.class_histogram y heap dumps (part1_memory/
      GarbageCollector, CAPAS 6 y 9).

    Importancia: ALTA. "Está lento" es de los problemas más comunes en
    producción, y la diferencia entre resolverlo en una hora o en una
    semana es saber medir.

    Índice:
        CAPA 1)  La regla: medir antes de cambiar
        CAPA 2)  Qué medir: el promedio miente, los percentiles no
        CAPA 3)  Por qué un benchmark hecho a mano miente
        CAPA 4)  JMH: el benchmark hecho bien
        CAPA 5)  Lo que más pesa: el algoritmo, no el truco
        CAPA 6)  Dónde se va el tiempo: Java Flight Recorder
        CAPA 7)  Eventos propios en JFR
        CAPA 8)  La caja de herramientas: jcmd y compañía
        CAPA 9)  Las causas reales de "está lento"
        CAPA 10) Errores comunes
        RESUMEN FINAL
*/
public class Performance {

    public static void main(String[] args) throws IOException {
        check(percentilesExample(), "CAPA 2");
        check(deadCodeExample(), "CAPA 3");
        check(algorithmExample(), "CAPA 5");
        Path recording = Files.createTempFile("performance-", ".jfr");
        try {
            recordWork(recording);
            check(profilerFindsHotMethodExample(recording), "CAPA 6");
            check(customEventExample(recording), "CAPA 7");
        } finally {
            Files.deleteIfExists(recording);
        }
        System.out.println("Performance: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) LA REGLA: MEDIR ANTES DE CAMBIAR
        =================================================================

        "La optimización prematura es la raíz de todos los males" (Donald
        Knuth). La frase completa dice que en el 97% del código la
        eficiencia pequeña no importa, y en el 3% sí. El trabajo es
        encontrar ESE 3%, y la intuición es pésima para eso: casi siempre
        el tiempo se va en un lugar que nadie sospechaba.

        El ciclo profesional:
            1) definir la meta con números ("el checkout responde en menos
               de 300 ms en el 99% de los casos");
            2) MEDIR dónde se va el tiempo (CAPA 6), no suponer;
            3) cambiar UNA cosa;
            4) medir otra vez y comparar. Si no mejoró, revertir.

        Y antes de optimizar, el código tiene que estar claro y probado:
        optimizar código confuso lo vuelve más confuso (v10_design/
        part4_clean_code/Refactoring).
    */

    /*
        =================================================================
        CAPA 2) QUÉ MEDIR: EL PROMEDIO MIENTE, LOS PERCENTILES NO
        =================================================================

        Las medidas que importan:
        - LATENCIA: cuánto tarda UNA operación (una petición, una consulta).
        - THROUGHPUT: cuántas operaciones por segundo.
        - RECURSOS: CPU, memoria, conexiones.

        Para la latencia, el PROMEDIO engaña. Si 98 peticiones tardan 20 ms
        y 2 tardan 3 segundos (una pausa larga, una consulta mala), el
        promedio es ~80 ms: "todo bien". Pero 2 de cada 100 usuarios
        esperaron 3 segundos, y un usuario que hace 50 peticiones en su
        visita casi seguro se topó con una.

        Por eso se usan PERCENTILES: el p50 (mediana) es lo que tarda la
        petición típica; el p99, lo que tarda la del 1% más lento. Las metas
        serias se ponen en p99 ("p99 < 300 ms"). Y el máximo también se
        mira: es donde viven los problemas raros.
    */
    static long percentile(List<Long> values, double percent) {
        List<Long> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int index = (int) Math.ceil(percent / 100 * sorted.size()) - 1;   // método "nearest rank"
        return sorted.get(Math.max(0, index));
    }

    static boolean percentilesExample() {
        List<Long> latenciesMs = new ArrayList<>(Collections.nCopies(98, 20L));
        latenciesMs.add(3_000L);
        latenciesMs.add(3_000L);

        double average = latenciesMs.stream().mapToLong(Long::longValue).average().orElse(0);
        return average == 79.6                                   // "se ve bien"
                && percentile(latenciesMs, 50) == 20
                && percentile(latenciesMs, 99) == 3_000;         // la verdad del 1% más lento
    }

    /*
        =================================================================
        CAPA 3) POR QUÉ UN BENCHMARK HECHO A MANO MIENTE
        =================================================================

        La forma ingenua: System.nanoTime() antes y después de un ciclo. En
        la JVM, eso falla de varias maneras:

        1) SIN CALENTAR: las primeras vueltas se interpretan; mides el
           intérprete, no el código que correrá en producción
           (ClassLoadingAndBytecode, CAPA 7).

        2) CÓDIGO ELIMINADO: si el resultado del cálculo no se usa, el JIT
           puede BORRAR el cálculo completo. Medido, 100 llamadas de un
           ciclo de un millón de vueltas con aritmética:

               resultado descartado       ~0.002 ms   (¡100 millones de vueltas!)
               resultado usado            ~64 ms

           El primero no es rápido: no existe. La conclusión "mi versión es
           30,000 veces más rápida" sería falsa. (No siempre pasa: con
           Math.log, por ejemplo, el JIT no lo borró. Por eso no se puede
           confiar en ninguna de las dos cosas.)

        3) VALORES CONSTANTES: si la entrada es una constante, el JIT puede
           calcular el resultado al COMPILAR y medirías una lectura.

        4) RUIDO: una recolección de basura a la mitad, el procesador que
           cambia de frecuencia, otro programa usando la CPU, el antivirus.
           Una sola medición no dice nada; hay que repetir y ver la
           dispersión.

        5) UNA SOLA JVM: el JIT toma decisiones según lo que vio primero
           (CAPA 7 de ClassLoadingAndBytecode: optimizaciones
           especulativas). Medir dos versiones en la misma JVM, una después
           de la otra, puede favorecer a una sin que sea mejor.

        El ejemplo demuestra el punto 2: el ciclo con el resultado
        descartado "tarda" menos de la centésima parte que el que lo usa.
    */
    static void sumDiscarded(int n) {
        long sum = 0;
        for (int i = 0; i < n; i++) {
            sum += (long) i * i % 7;
        }
        // sum no se usa: el JIT puede eliminar todo el ciclo
    }

    static long sumUsed(int n) {
        long sum = 0;
        for (int i = 0; i < n; i++) {
            sum += (long) i * i % 7;
        }
        return sum;
    }

    static boolean deadCodeExample() {
        long discarded = bestOf(8, () -> {
            for (int call = 0; call < 100; call++) {
                sumDiscarded(1_000_000);
            }
            return 0;
        });
        long used = bestOf(8, () -> {
            long total = 0;
            for (int call = 0; call < 100; call++) {
                total += sumUsed(1_000_000);
            }
            return total;
        });
        return discarded * 100 < used;                      // "100 veces más rápido": una mentira del benchmark
    }

    /*
        =================================================================
        CAPA 4) JMH: EL BENCHMARK HECHO BIEN
        =================================================================

        JMH (Java Microbenchmark Harness) lo escribió el mismo equipo que
        hace el JIT, justo para resolver la CAPA 3:
        - corre cada benchmark en una JVM NUEVA (varias, "forks");
        - hace iteraciones de CALENTAMIENTO que no cuentan;
        - entrega los resultados a un Blackhole, un objeto que "usa" el
          valor de forma que el JIT no puede eliminar el cálculo;
        - repite y reporta el promedio con su margen de error.

        Así se ve (se ejecuta con Maven, en v9_tools/):

            @BenchmarkMode(Mode.AverageTime)
            @OutputTimeUnit(TimeUnit.MICROSECONDS)
            @Warmup(iterations = 5) @Measurement(iterations = 5) @Fork(2)
            @State(Scope.Benchmark)
            public class ConcatBenchmark {
                @Param({"100", "10000"}) int size;

                @Benchmark
                public String withBuilder() {
                    StringBuilder builder = new StringBuilder();
                    for (int i = 0; i < size; i++) builder.append('x');
                    return builder.toString();      // lo devuelto va al Blackhole
                }
            }

        Úsalo cuando necesites comparar dos formas de hacer algo PEQUEÑO
        (una estructura de datos, un algoritmo). Para "por qué está lenta
        mi aplicación", no sirve: ahí se usa un perfilador (CAPA 6).
    */

    /*
        =================================================================
        CAPA 5) LO QUE MÁS PESA: EL ALGORITMO, NO EL TRUCO
        =================================================================

        Las mejoras de verdad casi nunca son trucos de bajo nivel. Son
        cambios de ALGORITMO o de estructura de datos: pasar de O(n²) a
        O(n), de buscar en una lista a buscar en un HashMap, de 101
        consultas a 1 (el N+1 de v11_patterns/part4_modern/Repository,
        CAPA 8).

        Ejemplo clásico: armar un texto de 20,000 caracteres con += dentro
        de un ciclo. Cada += crea un String NUEVO copiando todo lo anterior
        (los String son inmutables; v1_fundamentals/
        part3_text_and_references/Strings, CAPA 9): 1 + 2 + ... + 20,000
        copias = unos 200 millones de caracteres copiados. StringBuilder
        agrega al final de un arreglo que crece: 20,000 operaciones.

            medido:  += en un ciclo   ~35 a 57 ms
                     StringBuilder    ~0.5 ms

        Ninguna configuración de la JVM compensa un algoritmo cuadrático.
        Aquí no hace falta JMH: la diferencia es de 70 veces, no de 3%.
    */
    static String concatenateWithPlus(int size) {
        String text = "";
        for (int i = 0; i < size; i++) {
            text += "x";                                    // copia todo el texto en cada vuelta
        }
        return text;
    }

    static String concatenateWithBuilder(int size) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < size; i++) {
            builder.append("x");
        }
        return builder.toString();
    }

    static boolean algorithmExample() {
        long plus = bestOf(3, () -> concatenateWithPlus(20_000).length());
        long builder = bestOf(3, () -> concatenateWithBuilder(20_000).length());
        return builder * 5 < plus;
    }

    /*
        =================================================================
        CAPA 6) DÓNDE SE VA EL TIEMPO: JAVA FLIGHT RECORDER
        =================================================================

        Un PERFILADOR responde "¿en qué métodos se va el tiempo?". Los
        buenos funcionan por MUESTREO: cada pocos milisegundos miran qué
        está ejecutando cada hilo (la cima de su pila; part1_memory/
        StackAndHeapMemory, CAPA 2) y lo anotan. El método que aparece en
        más muestras es donde se va el tiempo. No hay que modificar el
        código.

        JAVA FLIGHT RECORDER (JFR) viene dentro de la JVM. Registra
        muestras de CPU, recolecciones, bloqueos entre hilos, lecturas de
        archivos y red, excepciones... con un costo bajo (Oracle lo
        documenta en menos de 1% con la configuración por defecto), así que
        se puede dejar encendido EN PRODUCCIÓN. Como la "caja negra" de un
        avión: cuando algo pasa, la información ya está grabada.

        Cómo se usa:
            java -XX:StartFlightRecording=duration=60s,filename=app.jfr ...
            jcmd <pid> JFR.start duration=60s filename=app.jfr   (en caliente)
            jfr summary app.jfr                          (resumen en texto)
            jfr print --events jdk.ExecutionSample app.jfr
        Para verlo con gráficas está JDK Mission Control (JMC), una
        aplicación gratuita que se descarga aparte.

        El ejemplo graba DESDE el programa (la API jdk.jfr), ejecuta un
        método caliente, y lee la grabación: el método que más aparece en
        la cima de las muestras es el caliente. Medido en dos corridas: 21 de
        21 y 21 de 24 muestras, en ~230 ms de trabajo; la grabación pesó
        ~250 KB.
    */
    static double hotLoop(int n) {
        double sum = 0;
        for (int i = 1; i < n; i++) {
            sum += Math.sqrt(i);
        }
        return sum;
    }

    private static double hotSink;

    private static void recordWork(Path file) throws IOException {
        try (Recording recording = new Recording(configuration("profile"))) {   // "profile": más detalle que "default"
            recording.start();
            CheckoutEvent checkout = new CheckoutEvent();   // CAPA 7
            checkout.begin();
            checkout.items = 3;
            hotSink += hotLoop(150_000_000);                // ~300 ms de CPU
            checkout.commit();
            recording.stop();
            recording.dump(file);
        }
    }

    static boolean profilerFindsHotMethodExample(Path file) throws IOException {
        Map<String, Integer> topFrames = new HashMap<>();
        for (RecordedEvent event : RecordingFile.readAllEvents(file)) {
            if (event.getEventType().getName().equals("jdk.ExecutionSample") && event.getStackTrace() != null) {
                List<RecordedFrame> frames = event.getStackTrace().getFrames();
                if (!frames.isEmpty()) {
                    topFrames.merge(frames.getFirst().getMethod().getName(), 1, Integer::sum);   // la cima de la pila
                }
            }
        }
        String hottest = topFrames.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("");
        return hottest.equals("hotLoop");
    }

    /*
        =================================================================
        CAPA 7) EVENTOS PROPIOS EN JFR
        =================================================================

        Además de los eventos de la JVM, puedes definir eventos DEL
        NEGOCIO: una clase que hereda de jdk.jfr.Event, con campos. Se
        crean, begin() marca el inicio, commit() los registra con su
        duración. Si no hay grabación activa, commit() casi no cuesta nada.

        Así, en la misma línea de tiempo de JFR ves "este checkout tardó
        800 ms" y, debajo, que en ese momento hubo una pausa del GC o un
        hilo esperando un bloqueo. Es la diferencia entre "fue lento" y "fue
        lento POR ESTO".
    */
    @Name("shop.Checkout")
    @Label("Checkout")
    static final class CheckoutEvent extends Event {
        @Label("Productos")
        int items;
    }

    static boolean customEventExample(Path file) throws IOException {
        return RecordingFile.readAllEvents(file).stream()
                .filter(event -> event.getEventType().getName().equals("shop.Checkout"))
                .anyMatch(event -> event.getInt("items") == 3
                        && event.getDuration().toMillis() > 0);   // midió lo que tardó el trabajo
    }

    /*
        =================================================================
        CAPA 8) LA CAJA DE HERRAMIENTAS: JCMD Y COMPAÑÍA
        =================================================================

        Todo en la carpeta bin del JDK. jcmd se conecta a una JVM corriendo
        por su pid (jcmd sin argumentos lista las JVM de la máquina):

            jcmd <pid> Thread.print       volcado de hilos: qué está haciendo
                                          CADA hilo ahora (para "se quedó
                                          congelado": se ven los bloqueos
                                          entre hilos, v13_concurrency/)
            jcmd <pid> GC.heap_info       cómo está el heap
            jcmd <pid> GC.class_histogram qué clases ocupan el heap
            jcmd <pid> GC.heap_dump f     volcado completo (GarbageCollector,
                                          CAPA 6)
            jcmd <pid> VM.flags           con qué opciones arrancó
            jcmd <pid> JFR.start / JFR.dump   grabar con JFR en caliente

        Y fuera de Java, el sistema operativo: en Linux, top (CPU y
        memoria por proceso; con -H, por hilo), vmstat e iostat (si la
        máquina espera al disco); en Windows, el Administrador de tareas y
        el Monitor de recursos. Si la CPU está al 5% y todo va lento, el
        problema NO es el código Java: algo está esperando (base de datos,
        red, disco, un bloqueo).
    */

    /*
        =================================================================
        CAPA 9) LAS CAUSAS REALES DE "ESTÁ LENTO"
        =================================================================

        En aplicaciones de negocio, en orden aproximado de frecuencia:

            causa                                       dónde se vio
            -----------------------------------------   ------------------------
            consultas a la base de datos: N+1,          Repository, CAPA 8
            sin índice, traer de más
            llamadas por red en serie (y sin timeout)   v8_apis/.../HttpClientUsage
            algoritmo o estructura equivocada           CAPA 5; v5_collections/
            (buscar en List, O(n²))
            hilos esperando un bloqueo                  v13_concurrency/
            mucha memoria de vida larga: GC frecuente   GarbageCollector, CAPAS 2 y 5
            logs que arman textos enormes, o            (se ve en el perfilador)
            excepciones usadas para control de flujo
            regex o formateadores creados en cada       v8_apis/part1_text_and_time/
            llamada en vez de una vez                   RegularExpressions, CAPA 8

        Casi nunca es "Java es lento" ni "falta una opción de la JVM". El
        perfilador lo dice en minutos.
    */

    /*
        =================================================================
        CAPA 10) ERRORES COMUNES
        =================================================================

        1) Optimizar sin medir, por intuición (CAPA 1).

        2) Reportar el promedio en vez de p50/p99 (CAPA 2).

        3) Micro-benchmarks con nanoTime sin calentar, con resultados que
           no se usan, en una sola JVM (CAPA 3).

        4) Usar JMH para buscar por qué la aplicación está lenta: eso es
           trabajo de un perfilador (CAPAS 4 y 6).

        5) Pulir trucos de bajo nivel mientras hay un algoritmo cuadrático
           o un N+1 (CAPAS 5 y 9).

        6) No tener JFR ni logs del GC en producción cuando pasa el
           problema (CAPA 6).

        7) Culpar al código Java cuando la CPU está ociosa: algo está
           esperando (CAPA 8).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Meta con números, medir, cambiar una cosa, medir otra vez.
        - Latencia en percentiles (p50, p99, máximo), no en promedio.
        - nanoTime a mano miente: calentamiento, código eliminado,
          constantes, ruido. Para micro-benchmarks, JMH.
        - Las grandes mejoras vienen del algoritmo y de evitar trabajo
          (N+1, O(n²)), no de trucos.
        - JFR: perfilador por muestreo dentro de la JVM, apto para
          producción, con eventos propios del negocio.
        - jcmd para hilos, heap, opciones y JFR en caliente; las
          herramientas del SO dicen si se espera CPU, disco o red.
    */

    // -----------------------------------------------------------------
    // Utilidades internas de la guía.
    // -----------------------------------------------------------------

    private static long benchmarkSink;

    /** El mejor tiempo (en nanos) de varias rondas; la primera sirve de calentamiento. */
    private static long bestOf(int rounds, LongSupplier work) {
        long best = Long.MAX_VALUE;
        for (int round = 0; round < rounds; round++) {
            long start = System.nanoTime();
            benchmarkSink += work.getAsLong();              // usar el resultado (CAPA 3)
            best = Math.min(best, System.nanoTime() - start);
        }
        return best;
    }

    private static Configuration configuration(String name) throws IOException {
        try {
            return Configuration.getConfiguration(name);
        } catch (java.text.ParseException e) {
            throw new IOException("Configuración de JFR inválida: " + name, e);
        }
    }

    private static void check(boolean condition, String layer) {
        if (!condition) {
            throw new IllegalStateException("El ejemplo de la " + layer + " no hace lo que la guía explica");
        }
    }
}
