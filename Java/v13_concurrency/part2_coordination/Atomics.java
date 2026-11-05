package v13_concurrency.part2_coordination;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicStampedReference;
import java.util.concurrent.atomic.LongAdder;

/*
    =====================================================================
    ATÓMICOS: ACTUALIZAR SIN BLOQUEOS, CON UNA INSTRUCCIÓN DEL PROCESADOR
    =====================================================================

    Ruta de concurrency/: guía 5 de 9  (part2_coordination)
    Anterior:  part2_coordination/Synchronization
    Siguiente: part2_coordination/ConcurrentCollections

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.
    - Costos medidos en Windows 11, JDK 26, procesador x86-64 de 14
      núcleos lógicos.

    Importancia: ALTA. Contadores, generadores de ids, estadísticas y
    estado compartido simple se hacen con atómicos; y son la base de casi
    todo java.util.concurrent.

    Índice:
        CAPA 1)  Las clases atómicas
        CAPA 2)  CAS: comparar e intercambiar
        CAPA 3)  updateAndGet: la función puede correr varias veces
        CAPA 4)  AtomicReference: estado inmutable que se reemplaza completo
        CAPA 5)  El problema ABA
        CAPA 6)  Cuando muchos hilos pelean: LongAdder
        CAPA 7)  False sharing: la línea de caché compartida
        CAPA 8)  Sin bloqueos no significa más rápido
        CAPA 9)  Errores comunes
        RESUMEN FINAL
*/
public class Atomics {

    public static void main(String[] args) throws InterruptedException {
        check(atomicClassesExample(), "CAPA 1");
        check(casLoopExample(), "CAPA 2");
        check(retriesExample(), "CAPA 3");
        check(immutableStateExample(), "CAPA 4");
        check(abaExample(), "CAPA 5");
        check(longAdderExample(), "CAPA 6");
        System.out.println("Atomics: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) LAS CLASES ATÓMICAS
        =================================================================

        java.util.concurrent.atomic tiene variables cuyas operaciones son
        INDIVISIBLES y además visibles entre hilos (como un volatile;
        part1_threads/MemoryModel, CAPA 8):

            AtomicInteger, AtomicLong     números
            AtomicBoolean                 una bandera que se cambia UNA vez
            AtomicReference<V>            una referencia a un objeto
            AtomicIntegerArray, ...       arreglos, cada casilla atómica
            LongAdder, LongAccumulator    contadores bajo mucha competencia
                                          (CAPA 6)

        Las operaciones más usadas:

            incrementAndGet() / getAndIncrement()   ++x / x++, atómicos
            addAndGet(delta)
            compareAndSet(esperado, nuevo)           CAS (CAPA 2)
            updateAndGet(función)                    x = f(x), atómico (CAPA 3)
            getAndSet(nuevo)                         cambiar y obtener el viejo

        Usos típicos: un contador de peticiones, un generador de ids, una
        bandera "ya se inició" que solo un hilo debe ganar:

            if (started.compareAndSet(false, true)) { iniciar(); }

        Aunque 10 hilos lleguen a la vez, uno solo ve true.
    */
    static boolean atomicClassesExample() throws InterruptedException {
        AtomicLong nextId = new AtomicLong(1000);
        OneTimeStarter starter = new OneTimeStarter();
        List<Long> ids = Collections.synchronizedList(new ArrayList<>());
        runInThreads(10, () -> {
            ids.add(nextId.getAndIncrement());              // ids únicos, sin bloqueos
            starter.startOnce();
        });
        return ids.stream().distinct().count() == 10 && starter.starts.get() == 1;
    }

    static final class OneTimeStarter {
        private final AtomicBoolean started = new AtomicBoolean();
        final AtomicInteger starts = new AtomicInteger();

        void startOnce() {
            if (started.compareAndSet(false, true)) {       // solo un hilo gana
                starts.incrementAndGet();
            }
        }
    }

    /*
        =================================================================
        CAPA 2) CAS: COMPARAR E INTERCAMBIAR
        =================================================================

        Todo se basa en UNA operación que el procesador hace de forma
        indivisible: CAS (compare-and-swap):

            compareAndSet(esperado, nuevo):
                "si el valor ACTUAL es 'esperado', cámbialo a 'nuevo' y di
                 true; si no, no toques nada y di false"

        En x86 es una sola instrucción, LOCK CMPXCHG: el prefijo LOCK hace
        que el núcleo tenga la línea de caché en exclusiva mientras compara
        y escribe, y ningún otro núcleo puede meterse en medio. En ARM se
        hace con instrucciones equivalentes. No hay sistema operativo, no
        hay dormir hilos: todo pasa en el procesador.

        Con CAS se escribe cualquier actualización atómica como un CICLO
        DE REINTENTO:

            do {
                actual = valor.get();              // leer
                nuevo = calcular(actual);          // calcular
            } while (!valor.compareAndSet(actual, nuevo));   // ¿nadie cambió? escribir

        Si otro hilo cambió el valor entre leer y escribir, el CAS falla y
        se repite con el valor nuevo. Nadie espera dormido: el que pierde
        reintenta. A esto se le llama algoritmo "sin bloqueos" (lock-free).

        incrementAndGet hace exactamente esto por dentro (en x86 usa una
        instrucción aún más directa, LOCK XADD). El ejemplo escribe el ciclo
        a mano y comprueba que, con 4 hilos, no se pierde nada.
    */
    static long incrementWithCas(AtomicLong value) {
        long current;
        long next;
        do {
            current = value.get();
            next = current + 1;
        } while (!value.compareAndSet(current, next));      // si alguien se metió, otra vez
        return next;
    }

    static boolean casLoopExample() throws InterruptedException {
        AtomicLong counter = new AtomicLong();
        runInThreads(4, () -> {
            for (int i = 0; i < 250_000; i++) {
                incrementWithCas(counter);
            }
        });
        return counter.get() == 1_000_000;
    }

    /*
        =================================================================
        CAPA 3) UPDATEANDGET: LA FUNCIÓN PUEDE CORRER VARIAS VECES
        =================================================================

        updateAndGet(f) y accumulateAndGet(x, f) escriben el ciclo de la
        CAPA 2 por ti. Pero por eso mismo: si el CAS falla, LA FUNCIÓN SE
        VUELVE A LLAMAR.

        Medido: 8 hilos haciendo 100,000 updateAndGet cada uno (800,000
        actualizaciones) llamaron a la función 2,503,659 veces. Tres veces
        más, por los reintentos.

        Por eso la función tiene que ser PURA: calcular el valor nuevo a
        partir del viejo, sin efectos. Si dentro mandas un correo, escribes
        un log o sumas a otro contador, se hará varias veces
        (v7_functional/part1_lambdas/FunctionalInterfaces).
    */
    static boolean retriesExample() throws InterruptedException {
        boolean retriesSeen = false;
        for (int attempt = 0; attempt < 10 && !retriesSeen; attempt++) {
            AtomicInteger value = new AtomicInteger();
            AtomicInteger functionCalls = new AtomicInteger();
            runInThreads(8, () -> {
                for (int i = 0; i < 100_000; i++) {
                    value.updateAndGet(v -> {
                        functionCalls.incrementAndGet();    // un efecto: justo lo que NO se debe hacer
                        return v + 1;
                    });
                }
            });
            retriesSeen = value.get() == 800_000 && functionCalls.get() > 800_000;
        }
        return retriesSeen;
    }

    /*
        =================================================================
        CAPA 4) ATOMICREFERENCE: ESTADO INMUTABLE QUE SE REEMPLAZA COMPLETO
        =================================================================

        Un atómico protege UNA variable. ¿Y si el estado son varias que
        deben cambiar juntas (el invariante de Synchronization, CAPA 3)?

        Truco muy usado: juntar el estado en un objeto INMUTABLE (un record)
        y guardarlo en un AtomicReference. Para cambiarlo, se crea un record
        NUEVO a partir del actual y se reemplaza con CAS (updateAndGet). Los
        lectores leen la referencia y ven un estado completo y coherente,
        sin bloqueo: el record no puede cambiar a medias.

        Ejemplo: estadísticas de tiempos de respuesta (cuántas, el total y
        el máximo). Varios hilos registran mediciones; nunca se ve un total
        de una medición con el conteo de otra.

        Es "copy-on-write": cada cambio crea un objeto. Va bien con pocos
        cambios o estados pequeños; con mucha competencia, los reintentos
        tiran objetos (CAPA 3), y un bloqueo puede ser mejor.
    */
    record Stats(long count, long totalMs, long maxMs) {
        Stats record(long ms) {
            return new Stats(count + 1, totalMs + ms, Math.max(maxMs, ms));   // nuevo, sin tocar el viejo
        }
    }

    static boolean immutableStateExample() throws InterruptedException {
        AtomicReference<Stats> stats = new AtomicReference<>(new Stats(0, 0, 0));
        runInThreads(4, () -> {
            for (int ms = 1; ms <= 1_000; ms++) {
                long measured = ms;
                stats.updateAndGet(current -> current.record(measured));   // función pura (CAPA 3)
            }
        });
        Stats finalStats = stats.get();
        return finalStats.count() == 4_000
                && finalStats.totalMs() == 4L * 500_500                     // 4 veces 1 + 2 + ... + 1000
                && finalStats.maxMs() == 1_000;
    }

    /*
        =================================================================
        CAPA 5) EL PROBLEMA ABA
        =================================================================

        CAS pregunta "¿el valor SIGUE siendo A?". Pero no sabe si en medio
        cambió a B y regresó a A. Casi siempre da igual (un número que
        volvió a su valor, volvió a su valor). Importa en estructuras con
        nodos que se REUTILIZAN: "el primer nodo sigue siendo el nodo A"...
        pero ese nodo ya se sacó, se volvió a meter, y su "siguiente" es
        otro.

        La solución: comparar el valor Y una versión que siempre crece.
        AtomicStampedReference guarda la referencia y un número ("sello"):
        el CAS falla si cambió cualquiera de los dos.

        En código de aplicación casi nunca te pasa (el recolector de basura
        evita la reutilización de objetos que causa ABA en C). Es
        importante saber que existe cuando lees o escribes estructuras
        sin bloqueos.
    */
    static boolean abaExample() {
        String a = "A";
        String b = "B";
        AtomicReference<String> plain = new AtomicReference<>(a);
        AtomicStampedReference<String> stamped = new AtomicStampedReference<>(a, 0);

        int stampSeen = stamped.getStamp();                 // un hilo lee: "es A, versión 0"

        plain.set(b);                                       // otro hilo: A -> B -> A
        plain.set(a);
        stamped.set(b, stamped.getStamp() + 1);
        stamped.set(a, stamped.getStamp() + 1);

        boolean plainFooled = plain.compareAndSet(a, "nuevo");                        // "sigue siendo A": acepta
        boolean stampedDetects = !stamped.compareAndSet(a, "nuevo", stampSeen, stampSeen + 1);   // la versión cambió
        return plainFooled && stampedDetects;
    }

    /*
        =================================================================
        CAPA 6) CUANDO MUCHOS HILOS PELEAN: LONGADDER
        =================================================================

        Un AtomicLong es UNA variable en UNA línea de caché. Cuando muchos
        núcleos la incrementan, la línea viaja de un núcleo a otro en cada
        operación (cada CAS necesita tenerla en exclusiva), y los que pierden
        reintentan. Medido, 20 millones de incrementos (ns por operación):

                           1 hilo    4 hilos    14 hilos
            AtomicLong       5.1      20.5       20.0
            LongAdder        8.5       2.4        1.0

        LongAdder reparte: cuando detecta competencia, crea varias celdas y
        cada hilo suma en una distinta. sum() las junta al leer. Con 14
        hilos fue 20 veces más rápido; con uno solo, algo más lento.

        El precio: sum() no es una foto exacta de un instante (mientras
        suma, otros siguen incrementando) y no hay "incrementar y obtener".
        Por eso:
        - LongAdder para CONTAR y ESTADÍSTICAS (peticiones atendidas,
          errores), donde se escribe muchísimo y se lee de vez en cuando;
        - AtomicLong cuando necesitas el valor exacto en cada operación (un
          generador de ids: getAndIncrement).
        (LongAccumulator es lo mismo con otra operación: el máximo, por
        ejemplo.)
    */
    static boolean longAdderExample() throws InterruptedException {
        LongAdder requests = new LongAdder();
        runInThreads(8, () -> {
            for (int i = 0; i < 100_000; i++) {
                requests.increment();
            }
        });
        return requests.sum() == 800_000;                   // exacto cuando ya nadie escribe
    }

    /*
        =================================================================
        CAPA 7) FALSE SHARING: LA LÍNEA DE CACHÉ COMPARTIDA
        =================================================================

        Las cachés del procesador no mueven bytes sueltos: mueven LÍNEAS de
        64 bytes. Si dos variables DISTINTAS quedan en la misma línea, y
        dos núcleos escriben cada uno en la suya, la línea va y viene entre
        los núcleos igual que si fuera una sola variable. Ninguno comparte
        DATOS, pero comparten la LÍNEA: "false sharing".

        Medido, 4 hilos incrementando 20 millones de veces cada uno SU
        casilla de un AtomicLongArray:

            casillas vecinas (misma línea de 64 bytes)     1,468 a 2,651 ms
            casillas separadas 64 bytes                      345 a 461 ms

        El mismo trabajo, sin compartir nada, entre 4 y 6 veces más lento,
        solo por dónde quedaron las variables en memoria.

        Es exactamente lo que LongAdder evita por dentro: sus celdas se
        separan con relleno (la anotación interna @Contended). En código de
        aplicación casi nunca hay que preocuparse; aparece en contadores
        por hilo muy calientes, y la solución es separarlos o usar
        LongAdder.
    */

    /*
        =================================================================
        CAPA 8) SIN BLOQUEOS NO SIGNIFICA MÁS RÁPIDO
        =================================================================

        "Lock-free" es una garantía de PROGRESO: si un hilo se detiene (lo
        pausa el SO, muere), los demás siguen avanzando; con un bloqueo, un
        hilo detenido mientras lo tiene detiene a todos. No es una
        garantía de velocidad.

        - Con poca competencia, un atómico es más barato que un bloqueo
          (no hay que tomar ni soltar nada).
        - Con mucha competencia, todos reintentan y la línea de caché va y
          viene (CAPA 6). A veces un bloqueo, que duerme a los que esperan,
          gasta menos CPU.
        - Un atómico protege UNA variable. Para varias, AtomicReference con
          un inmutable (CAPA 4) o un bloqueo.

        Regla práctica: atómicos para contadores, ids, banderas y estado
        pequeño; bloqueos para operaciones que tocan varias cosas;
        colecciones concurrentes para todo lo que sea una colección
        (part2_coordination/ConcurrentCollections). Y medir (v12_jvm/
        part3_performance/Performance).

        Para autores de librerías: VarHandle (v12_jvm/part2_classes/
        ReflectionAndAnnotations, CAPA 9) hace CAS sobre un campo normal de
        un objeto, sin crear un objeto atómico por cada uno.
    */

    /*
        =================================================================
        CAPA 9) ERRORES COMUNES
        =================================================================

        1) Combinar dos operaciones atómicas creyendo que el conjunto es
           atómico: if (x.get() == 0) x.set(1) es revisar-y-actuar; se usa
           compareAndSet(0, 1) (CAPAS 1 y 2).

        2) Efectos dentro de updateAndGet o accumulateAndGet: se repiten
           (CAPA 3).

        3) Varios atómicos para un estado que debe cambiar junto (CAPA 4).

        4) AtomicLong como contador de estadísticas con mucha competencia:
           LongAdder (CAPA 6).

        5) Leer LongAdder.sum() como si fuera exacto mientras otros escriben
           (CAPA 6).

        6) Suponer que lock-free siempre gana (CAPA 8).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Atómicos: operaciones indivisibles y visibles sin bloqueos.
        - Todo se basa en CAS (LOCK CMPXCHG en x86): si sigue igual, cambia;
          si no, reintenta.
        - updateAndGet puede llamar la función varias veces: debe ser pura.
        - Varias variables juntas: un record inmutable en un
          AtomicReference.
        - ABA: el valor volvió pero algo cambió; AtomicStampedReference.
        - Mucha competencia: LongAdder (20 veces más rápido con 14 hilos,
          medido); AtomicLong si necesitas el valor exacto en cada paso.
        - False sharing: variables vecinas en la misma línea de 64 bytes se
          estorban (4 a 6 veces más lento, medido).
        - Lock-free garantiza progreso, no velocidad.
    */

    // -----------------------------------------------------------------
    // Utilidades internas de la guía.
    // -----------------------------------------------------------------

    /** Corre la misma tarea en varios hilos que arrancan juntos, y los espera. */
    private static void runInThreads(int count, Runnable task) throws InterruptedException {
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            threads.add(Thread.ofPlatform().start(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                task.run();
            }));
        }
        start.countDown();
        for (Thread thread : threads) {
            thread.join();
        }
    }

    private static void check(boolean condition, String layer) {
        if (!condition) {
            throw new IllegalStateException("El ejemplo de la " + layer + " no hace lo que la guía explica");
        }
    }
}
