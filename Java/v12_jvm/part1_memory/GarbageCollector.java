package v12_jvm.part1_memory;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.ref.Cleaner;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/*
    =====================================================================
    EL RECOLECTOR DE BASURA: QUIÉN LIBERA LA MEMORIA Y CÓMO
    =====================================================================

    Ruta de jvm/: guía 2 de 6  (part1_memory)
    Anterior:  part1_memory/StackAndHeapMemory
    Siguiente: part2_classes/ClassLoadingAndBytecode

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.
    - Los números citados se MIDIERON en Windows 11, JDK 26 (el de Oracle),
      16 GB de RAM, con un programa que crea 4 GB de basura de vida corta
      mientras mantiene vivos 50 MB (o 300 MB). Sirven para comparar, no
      como promesa.
    - Para saber si un objeto ya fue recolectado, las checks usan
      WeakReference (CAPA 7): una referencia que no lo mantiene vivo y que
      el recolector vacía al liberarlo.

    Lo que ya se vio y aquí se explica por dentro:
    - un objeto sin referencias se libera (v1_fundamentals/
      part3_text_and_references/ReferencesAndNull, CAPA 5);
    - la fuga del Observer que no se da de baja (v11_patterns/
      part3_behavioral/Observer, CAPA 4);
    - la clase interna que retiene a la externa (v2_oop/
      part3_interfaces_and_relationships/NestedClasses, CAPA 4).

    Importancia: ALTA. Las pausas del recolector y las fugas de memoria
    son de los problemas más comunes en servidores Java.

    Índice:
        CAPA 1)  Qué es "basura": alcanzable o no
        CAPA 2)  La mayoría de los objetos muere joven: las generaciones
        CAPA 3)  Las pausas: detener el mundo
        CAPA 4)  Los recolectores de HotSpot y cuál elegir
        CAPA 5)  Fugas de memoria: basura que no es basura
        CAPA 6)  Encontrar una fuga
        CAPA 7)  Referencias débiles: WeakReference y WeakHashMap
        CAPA 8)  Liberar recursos nativos: close primero, Cleaner de respaldo
        CAPA 9)  Ajustes: qué tocar y qué no
        CAPA 10) Errores comunes
        RESUMEN FINAL
*/
public class GarbageCollector {

    public static void main(String[] args) throws InterruptedException {
        check(reachabilityExample(), "CAPA 1");
        check(generationsExample(), "CAPA 2");
        check(leakExample(), "CAPA 5");
        check(weakReferencesExample(), "CAPA 7");
        check(cleanerExample(), "CAPA 8");
        System.out.println("GarbageCollector: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) QUÉ ES "BASURA": ALCANZABLE O NO
        =================================================================

        En C, el programador pide memoria (malloc) y la devuelve (free). Si
        la devuelve dos veces o la usa después de devolverla, el programa se
        corrompe; si no la devuelve, se fuga. Java quita ese trabajo: no hay
        free. Un RECOLECTOR DE BASURA (GC, garbage collector) encuentra los
        objetos que ya nadie puede usar y reutiliza su memoria.

        ¿Cómo sabe cuáles? No cuenta referencias: RASTREA desde las RAÍCES
        (GC roots), los lugares de donde el programa puede empezar a leer:
        - las variables locales de TODAS las pilas de hilos vivos;
        - los campos static de las clases cargadas;
        - algunas referencias internas de la JVM y del código nativo.

        Desde ahí sigue cada referencia, marcando todo lo que alcanza. Lo
        que quedó SIN MARCAR es basura, aunque tenga referencias:

            raíz (local) --> pedido --> línea 1
                                   \--> línea 2
                             A <--> B      <- se apuntan entre sí, pero
                                              nadie llega a ellos: basura

        Por eso los CICLOS no son problema en Java (con un simple contador
        de referencias, A y B se mantendrían vivos para siempre).

        Un detalle que sorprende: el JIT sabe en qué punto un método deja
        de usar una local, y desde ahí esa local ya no cuenta como raíz,
        aunque siga "en el bloque". Si necesitas que un objeto siga vivo
        hasta un punto exacto (raro, solo con recursos nativos), se dice
        con Reference.reachabilityFence(objeto).
    */
    static final class Node {
        Node other;
        final byte[] payload = new byte[1024];
    }

    static boolean reachabilityExample() {
        Node a = new Node();
        Node b = new Node();
        a.other = b;
        b.other = a;                                        // un ciclo
        WeakReference<Node> watchA = new WeakReference<>(a);
        a = null;
        b = null;                                           // nadie llega al ciclo
        boolean cycleCollected = isCollected(watchA);

        Node kept = new Node();
        WeakReference<Node> watchKept = new WeakReference<>(kept);
        boolean keptAlive = !isCollected(watchKept);
        Reference.reachabilityFence(kept);                  // "kept" cuenta como viva hasta aquí
        return cycleCollected && keptAlive;
    }

    /*
        =================================================================
        CAPA 2) LA MAYORÍA DE LOS OBJETOS MUERE JOVEN: LAS GENERACIONES
        =================================================================

        Observación de décadas (la "hipótesis generacional"): casi todos los
        objetos mueren muy pronto. El String armado para un log, el DTO de
        una petición, el iterador de un for: viven microsegundos. Los que
        sobreviven un rato (cachés, configuración) suelen vivir mucho.

        Por eso el heap se divide en GENERACIONES:

            JOVEN                                    VIEJA
            +--------------+-----------+             +----------------------+
            | Eden         | Survivor  |  --------->  | objetos que          |
            | (aquí nacen  | (sobre-   |  sobrevivió  | sobrevivieron varias |
            |  todos)      |  vivientes)|  varias      | recolecciones        |
            +--------------+-----------+  veces       +----------------------+

        - Todo objeto nace en Eden (en el TLAB de su hilo; StackAndHeapMemory,
          CAPA 4).
        - Cuando Eden se llena, hay una RECOLECCIÓN JOVEN: se COPIAN los
          pocos objetos vivos a Survivor, y Eden se vacía completo de un
          golpe. La "edad" de cada objeto (cuántas recolecciones sobrevivió)
          va en su cabecera; al pasar de cierta edad, se mueve a la vieja.
        - La recolección vieja (más cara) es mucho menos frecuente.

        La clave: el costo de una recolección joven depende de los objetos
        VIVOS (los que se copian), no de la basura. Crear muchos objetos
        temporales sale barato, porque morir no cuesta nada.

        Medido: 4 GB de basura de vida corta, con 50 MB vivos -> G1 hizo
        29 recolecciones jóvenes (110 ms en total) y CERO viejas.

        El ejemplo crea ~512 MB de arreglos temporales: hay recolecciones
        jóvenes y la generación vieja no se toca.
    */
    static volatile byte[] lastTemporary;                   // hace que cada arreglo "escape" (StackAndHeapMemory, CAPA 7)

    static boolean generationsExample() {
        GarbageCollectorMXBean young = collector("G1 Young Generation");
        GarbageCollectorMXBean old = collector("G1 Old Generation");
        if (young == null || old == null) {
            return true;                                    // se corrió con otro recolector: la idea no se mide aquí
        }
        long youngBefore = young.getCollectionCount();
        long oldBefore = old.getCollectionCount();
        for (int i = 0; i < 500_000; i++) {
            lastTemporary = new byte[1024];                 // muere en la siguiente vuelta
        }
        return young.getCollectionCount() > youngBefore
                && old.getCollectionCount() == oldBefore;
    }

    /*
        =================================================================
        CAPA 3) LAS PAUSAS: DETENER EL MUNDO
        =================================================================

        Mover un objeto a otra dirección (como hace la recolección joven)
        obliga a actualizar TODAS las referencias que apuntan a él. Si un
        hilo de tu programa estuviera usando esa referencia en ese
        instante, leería memoria vieja. La solución más simple: DETENER
        todos los hilos de Java mientras se mueve ("stop-the-world", una
        PAUSA).

        Los hilos no se detienen en cualquier instrucción: la JVM les pide
        parar y cada uno se detiene en el siguiente SAFEPOINT, un punto del
        código (al volver de un método, en el salto de un ciclo) donde la
        JVM sabe exactamente qué hay en cada registro y en la pila.

        Una pausa de 5 ms en un servidor significa: TODAS las peticiones
        que se estaban atendiendo se congelan 5 ms. Para un sistema de
        pagos o un juego, una pausa de 300 ms es un problema visible.

        Por eso los recolectores modernos hacen la mayor parte del trabajo
        CONCURRENTEMENTE (en sus propios hilos, mientras tu programa sigue
        corriendo) y solo pausan para pasos cortos. El precio: más CPU y
        más complejidad (el programa cambia referencias mientras el
        recolector las recorre; se resuelve con "barreras": unas
        instrucciones extra que el JIT agrega en cada lectura o escritura de
        referencias).
    */

    /*
        =================================================================
        CAPA 4) LOS RECOLECTORES DE HOTSPOT Y CUÁL ELEGIR
        =================================================================

            Recolector   Opción               Idea                      Pausas
            ----------   ------------------   -----------------------   ----------
            Serial       -XX:+UseSerialGC     un solo hilo, todo con    cortas en
                                              pausa                     heaps chicos
            Parallel     -XX:+UseParallelGC   varios hilos, todo con    crecen con
                                              pausa; máximo throughput  el heap
            G1           (por defecto)        heap en REGIONES (2 MB    meta: 200 ms;
                                              aquí); recoge primero las típicas de
                                              regiones con más basura;  pocos ms
                                              marcado concurrente
            ZGC          -XX:+UseZGC          casi todo concurrente,    < 1 ms,
                                              también mover objetos     sin importar
                                              (generacional)            el heap

        Medido (4 GB de basura, -Xmx512m o -Xmx1g):

                              50 MB vivos                 300 MB vivos
            G1        29 pausas, mediana 4.7 ms    37 pausas, mediana 3.7 ms,
                      máxima 7.3 ms                máxima 12.7 ms
            Parallel  50 pausas, mediana 4.6 ms
            ZGC       120 pausas, mediana 0.010 ms 99 pausas, mediana 0.015 ms,
                      máxima 0.026 ms              máxima 0.030 ms

        ZGC pausa cientos de veces menos, y no crece con lo que está vivo. A
        cambio usa más CPU y algo más de memoria: en un programa por lotes
        (que solo quiere terminar rápido) no gana nada.

        Cómo elegir:
        - Empieza con el de por defecto (G1). Está bien para casi todo.
        - Servicios donde importan las respuestas lentas (el percentil 99),
          o heaps de decenas de GB: ZGC.
        - Procesos por lotes donde solo importa el tiempo total: Parallel.
        - La JVM elige Serial SOLA si la máquina tiene menos de 2 CPUs o
          menos de ~1.8 GB: pasa en contenedores pequeños. Si tu
          contenedor tiene 1 CPU, revisa qué recolector está usando.

        Shenandoah es otro recolector de pausas cortas, incluido en varias
        distribuciones de OpenJDK pero NO en el JDK de Oracle (medido: "Option
        -XX:+UseShenandoahGC not supported").
    */

    /*
        =================================================================
        CAPA 5) FUGAS DE MEMORIA: BASURA QUE NO ES BASURA
        =================================================================

        El GC libera lo INALCANZABLE, no lo que ya no usas. Si un objeto
        que ya no sirve sigue alcanzable desde una raíz, vive para siempre.
        Eso es una fuga de memoria en Java. Las más comunes:

        1) Colecciones static que solo crecen: un "caché" sin límite, un
           registro de sesiones que nadie limpia.
        2) Suscriptores que nunca se dan de baja (Observer, CAPA 4): el
           objeto que avisa vive mucho y retiene a todos sus suscriptores.
        3) Clases internas (no static) y lambdas que usan this: guardan una
           referencia oculta al objeto externo (NestedClasses, CAPA 4).
           Guardar UNA en un registro de larga vida retiene al objeto
           externo COMPLETO, con todo lo que tenga.
        4) ThreadLocal en hilos de un pool: el hilo no muere nunca, y su
           valor tampoco, si no se llama remove() (v13_concurrency/).

        El ejemplo junta 2 y 3: una Playlist registra un listener en un
        registro static. Si el listener es una clase interna, la Playlist
        (con sus canciones) no se libera aunque ya nadie la use. Con una
        clase static que solo guarda lo que necesita, se libera.
    */
    static final List<Runnable> GLOBAL_LISTENERS = new ArrayList<>();   // vive lo que dure el programa

    static final class Playlist {
        final byte[] songs = new byte[1_000_000];           // lo pesado
        int plays;

        final class InnerListener implements Runnable {     // interna: retiene a su Playlist
            @Override
            public void run() {
                plays++;
            }
        }

        static final class CounterListener implements Runnable {   // static: no retiene nada externo
            final AtomicInteger plays = new AtomicInteger();

            @Override
            public void run() {
                plays.incrementAndGet();
            }
        }
    }

    static boolean leakExample() {
        Playlist leaking = new Playlist();
        GLOBAL_LISTENERS.add(leaking.new InnerListener());
        WeakReference<Playlist> watchLeaking = new WeakReference<>(leaking);
        leaking = null;
        boolean leaked = !isCollected(watchLeaking);        // el registro la sigue alcanzando

        Playlist clean = new Playlist();
        GLOBAL_LISTENERS.add(new Playlist.CounterListener());
        WeakReference<Playlist> watchClean = new WeakReference<>(clean);
        clean = null;
        boolean released = isCollected(watchClean);

        GLOBAL_LISTENERS.clear();                           // darse de baja: ahora sí se libera
        boolean releasedAfterUnregister = isCollected(watchLeaking);
        return leaked && released && releasedAfterUnregister;
    }

    /*
        =================================================================
        CAPA 6) ENCONTRAR UNA FUGA
        =================================================================

        EL SÍNTOMA. El uso del heap sube y baja como sierra (crece, el GC
        limpia, baja). Normal. Una fuga se ve en el PISO de la sierra: lo
        que queda DESPUÉS de cada recolección sube y sube, hasta el
        OutOfMemoryError (StackAndHeapMemory, CAPA 8). Se ve en el log del
        GC:

            java -Xlog:gc:file=gc.log ...
            GC(12) Pause Young (Normal) (G1 Evacuation Pause) 119M->104M(270M) 10.0ms
                                                              antes  después (comprometido)

        EL SOSPECHOSO. jcmd (viene con el JDK, en su carpeta bin) se conecta
        a una JVM corriendo por su número de proceso (pid). Un histograma de
        qué clases ocupan el heap, medido en un programa que retiene 100,000
        int[64] en una lista static:

            jcmd 17148 GC.class_histogram
             num   #instances     #bytes  class name
               1:      101392   27244328  [I            <- int[]: 26 MB
               2:        1434     581504  [Ljava.lang.Object;

        Tomado dos veces con minutos de diferencia, la clase que crece es la
        sospechosa.

        EL CULPABLE. Saber QUIÉN la retiene requiere un volcado del heap
        (heap dump): un archivo con todos los objetos y sus referencias.
            jcmd <pid> GC.heap_dump /ruta/heap.hprof
        o automático al fallar con -XX:+HeapDumpOnOutOfMemoryError. Se abre
        con un analizador de heap dumps (el más usado es Eclipse MAT, una
        aplicación gratuita) que muestra la cadena de referencias desde la
        raíz hasta los objetos retenidos: "este static de esta clase
        retiene esta lista que retiene 2 GB".

        Cuidado: un heap dump pesa lo que el heap y pausa el proceso
        mientras se escribe. Y contiene TODOS los datos en memoria
        (contraseñas, datos de clientes): se trata como información
        confidencial.
    */

    /*
        =================================================================
        CAPA 7) REFERENCIAS DÉBILES: WEAKREFERENCE Y WEAKHASHMAP
        =================================================================

        Una referencia normal (fuerte) mantiene vivo al objeto. Java tiene
        referencias que NO lo hacen:

        - WeakReference<T>: no cuenta para el rastreo. Si solo quedan
          referencias débiles, el GC libera el objeto y la referencia
          devuelve null. Sirve para "quiero saber de este objeto mientras
          exista, pero no ser yo quien lo mantenga vivo".

        - WeakHashMap<K, V>: un mapa cuyas CLAVES son débiles. Cuando la
          clave ya no se usa en ningún otro lado, la entrada desaparece
          sola. Útil para asociar datos extra a objetos que no son tuyos
          (como "metadatos por cada objeto de esta librería").
          Dos trampas:
          * si el VALOR referencia a la clave, la clave siempre es
            alcanzable (desde el mapa, a través del valor): nunca se
            borra;
          * con claves que nunca se liberan (literales "texto", Integer
            pequeños de la caché) es un HashMap normal.

        - SoftReference<T>: se libera solo cuando falta memoria. Parece
          ideal para cachés, pero NO se recomienda: el GC las vacía todas a
          la vez justo cuando el sistema está bajo presión, y no controlas
          cuántas hay. Un caché se hace con TAMAÑO MÁXIMO (el LRU con
          LinkedHashMap de v5_collections/part2_sets_and_maps/Maps, CAPA
          11).

        - PhantomReference: la usa Cleaner por dentro (CAPA 8); no se usa
          directamente.
    */
    record Metadata(String note, Object owner) {
    }

    static boolean weakReferencesExample() {
        Map<Object, String> notes = new WeakHashMap<>();
        Object key = new Object();
        notes.put(key, "nota");
        key = null;
        boolean entryRemoved = waitUntil(notes::isEmpty);   // la clave se liberó: la entrada se fue

        Map<Object, Metadata> trap = new WeakHashMap<>();
        Object owner = new Object();
        trap.put(owner, new Metadata("nota", owner));       // el valor apunta a la clave
        owner = null;
        boolean neverRemoved = !waitUntil(trap::isEmpty);   // la clave sigue alcanzable

        return entryRemoved && neverRemoved;
    }

    /*
        =================================================================
        CAPA 8) LIBERAR RECURSOS NATIVOS: CLOSE PRIMERO, CLEANER DE RESPALDO
        =================================================================

        El GC libera MEMORIA DEL HEAP. Un archivo abierto, un socket o
        memoria nativa son recursos del SISTEMA OPERATIVO (un descriptor de
        archivo en Linux, un handle en Windows). El GC no sabe nada de
        ellos, y además no hay garantía de CUÁNDO correrá (quizá nunca, si
        sobra heap). Mientras tanto, el proceso se queda sin descriptores
        ("Too many open files"; v8_apis/part2_io_and_network/FilesAndIO).

        Por eso:
        1) La forma correcta es close() con try-with-resources (v3_exceptions/
           part2_in_practice/TryWithResources). Determinista: se libera al
           salir del bloque.
        2) finalize() era el viejo intento de "limpiar cuando el GC lo
           libere": lento, impredecible, y está marcado para eliminarse
           (v2_oop/part4_advanced_types/ObjectContract, CAPA 11).
        3) Cleaner (java.lang.ref) es su reemplazo, solo como RED DE
           SEGURIDAD: si alguien olvida close(), la limpieza corre algún
           tiempo después de que el objeto se vuelve inalcanzable.

        Cómo se usa bien:
        - la acción de limpieza va en un objeto SEPARADO (una clase static o
          un record) que NO referencia al recurso: si la referenciara, el
          recurso siempre sería alcanzable desde el Cleaner (la trampa de la
          CAPA 7) y nunca se limpiaría;
        - close() llama a cleanable.clean(): la acción corre una sola vez,
          sea por close o por el GC.
    */
    private static final Cleaner CLEANER = Cleaner.create();

    static final class NativeBuffer implements AutoCloseable {
        private record Release(AtomicInteger releases) implements Runnable {   // no conoce al NativeBuffer
            @Override
            public void run() {
                releases.incrementAndGet();                 // aquí se liberaría el recurso del SO
            }
        }

        private final Cleaner.Cleanable cleanable;

        NativeBuffer(AtomicInteger releases) {
            this.cleanable = CLEANER.register(this, new Release(releases));
        }

        @Override
        public void close() {
            cleanable.clean();                              // corre la acción ahora, y nunca más
        }
    }

    static boolean cleanerExample() {
        AtomicInteger closedReleases = new AtomicInteger();
        try (NativeBuffer _ = new NativeBuffer(closedReleases)) {
            // se usa el buffer
        }
        boolean closedOnce = waitUntil(() -> closedReleases.get() == 1) && closedReleases.get() == 1;

        AtomicInteger forgottenReleases = new AtomicInteger();
        new NativeBuffer(forgottenReleases);                // alguien olvidó close()
        boolean safetyNet = waitUntil(() -> forgottenReleases.get() == 1);
        return closedOnce && safetyNet;
    }

    /*
        =================================================================
        CAPA 9) AJUSTES: QUÉ TOCAR Y QUÉ NO
        =================================================================

        Lo que SÍ se configura en un servidor:
        - el tamaño: -Xmx (o -XX:MaxRAMPercentage en contenedores;
          StackAndHeapMemory, CAPA 4). En servidores, -Xms igual a -Xmx
          evita que el heap crezca y se encoja durante el día;
        - el recolector, si los defaults no bastan (CAPA 4);
        - el log del GC, SIEMPRE: cuesta casi nada y es lo primero que se
          necesita cuando algo falla:
              -Xlog:gc*:file=gc.log:time,uptime:filecount=5,filesize=20m
        - -XX:+HeapDumpOnOutOfMemoryError (CAPA 6).

        Lo que NO:
        - System.gc(): es una sugerencia; con G1 provoca una recolección
          completa con pausa. Las checks de esta guía lo usan para poder
          demostrar cosas; el código de producción no.
        - Decenas de opciones copiadas de internet: cada versión del JDK
          cambia los defaults, y opciones viejas pueden empeorar las cosas.
          Primero se MIDE (log del GC, part3_performance/Performance), luego
          se cambia UNA cosa y se vuelve a medir.
        - Una meta de pausa irreal (-XX:MaxGCPauseMillis=1 con G1): el
          recolector hará recolecciones más pequeñas y más seguidas, y el
          programa irá más lento. Si necesitas pausas de 1 ms, usa ZGC.

        Y la mejor optimización: crear menos basura de vida LARGA. La basura
        joven es barata (CAPA 2); lo caro es lo que llega a la generación
        vieja.
    */

    /*
        =================================================================
        CAPA 10) ERRORES COMUNES
        =================================================================

        1) Creer que un objeto vive mientras su variable está "en el bloque",
           o que un ciclo de referencias nunca se libera (CAPA 1).

        2) Evitar objetos temporales "para no cargar al GC": la basura
           joven es casi gratis (CAPA 2).

        3) Cambiar de recolector sin medir; no revisar qué recolector usa un
           contenedor de 1 CPU (CAPA 4).

        4) Cachés static sin límite, listeners que no se dan de baja, clases
           internas registradas en objetos de larga vida (CAPA 5).

        5) Compartir un heap dump como si fuera un archivo cualquiera: tiene
           todos los datos en memoria (CAPA 6).

        6) SoftReference como caché; WeakHashMap con valores que apuntan a
           su clave (CAPA 7).

        7) Confiar en el GC (finalize, Cleaner) para cerrar archivos y
           conexiones, en vez de close() (CAPA 8).

        8) Llamar a System.gc() en producción; opciones copiadas sin medir
           (CAPA 9).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - El GC rastrea desde las raíces (pilas, statics); lo inalcanzable
          es basura, con ciclos incluidos.
        - Generaciones: casi todo muere joven; la recolección joven copia
          solo lo vivo, así que la basura temporal es barata.
        - Las pausas detienen todos los hilos en safepoints; los
          recolectores modernos trabajan en paralelo con tu programa.
        - G1 por defecto (pausas de pocos ms); ZGC para pausas < 1 ms;
          Parallel para lotes; Serial en máquinas pequeñas.
        - Fuga = objetos inútiles pero alcanzables. Síntoma: el piso del
          heap sube. Herramientas: log del GC, jcmd GC.class_histogram, heap
          dump.
        - WeakReference y WeakHashMap no mantienen vivo; los cachés, con
          límite.
        - Recursos del SO: close() con try-with-resources; Cleaner solo de
          respaldo.
        - Configura tamaño, log del GC y heap dump al fallar; mide antes de
          ajustar.
    */

    // -----------------------------------------------------------------
    // Utilidades internas de la guía.
    // -----------------------------------------------------------------

    /** ¿El GC ya liberó el objeto? Pide recolecciones (solo para demostrar) hasta un límite. */
    private static boolean isCollected(WeakReference<?> reference) {
        return waitUntil(() -> reference.get() == null);
    }

    private static boolean waitUntil(Supplier<Boolean> condition) {
        for (int attempt = 0; attempt < 20; attempt++) {
            if (condition.get()) {
                return true;
            }
            System.gc();
            try {
                Thread.sleep(10);                           // da tiempo a los hilos del GC y del Cleaner
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return condition.get();
    }

    private static GarbageCollectorMXBean collector(String name) {
        return ManagementFactory.getGarbageCollectorMXBeans().stream()
                .filter(bean -> bean.getName().equals(name))
                .findFirst()
                .orElse(null);
    }

    private static void check(boolean condition, String layer) {
        if (!condition) {
            throw new IllegalStateException("El ejemplo de la " + layer + " no hace lo que la guía explica");
        }
    }
}
