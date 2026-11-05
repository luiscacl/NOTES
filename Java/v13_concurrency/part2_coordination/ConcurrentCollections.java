package v13_concurrency.part2_coordination;

import java.util.ArrayList;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/*
    =====================================================================
    COLECCIONES CONCURRENTES: MAPAS, LISTAS Y COLAS PARA VARIOS HILOS
    =====================================================================

    Ruta de concurrency/: guía 6 de 9  (part2_coordination)
    Anterior:  part2_coordination/Atomics
    Siguiente: part3_tasks/ExecutorsUsage

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.
    - Costos medidos en Windows 11, JDK 26.

    Lo que ya se vio: las colecciones normales se rompen con varios hilos
    (part1_threads/ConcurrencyProblems, CAPA 4), y cómo funcionan HashMap
    y las colas por dentro (v5_collections/part2_sets_and_maps/Maps,
    v5_collections/part3_queues/QueuesAndStacks).

    Importancia: MUY ALTA. ConcurrentHashMap y las colas bloqueantes están
    en casi todo servidor: cachés, contadores por clave, trabajo entre
    hilos.

    Índice:
        CAPA 1)  Por qué no basta con "sincronizar" una colección
        CAPA 2)  ConcurrentHashMap: cómo funciona
        CAPA 3)  Operaciones atómicas por clave: merge y computeIfAbsent
        CAPA 4)  Las reglas de ConcurrentHashMap
        CAPA 5)  CopyOnWriteArrayList: muchas lecturas, casi ninguna escritura
        CAPA 6)  Colas bloqueantes: productor y consumidor
        CAPA 7)  Detener a los consumidores
        CAPA 8)  Las demás colecciones concurrentes
        CAPA 9)  Errores comunes
        RESUMEN FINAL
*/
public class ConcurrentCollections {

    public static void main(String[] args) throws InterruptedException {
        check(synchronizedWrapperExample(), "CAPA 1");
        check(mergeExample(), "CAPA 3");
        check(computeIfAbsentExample(), "CAPA 3");
        check(rulesExample(), "CAPA 4");
        check(copyOnWriteExample(), "CAPA 5");
        check(producerConsumerExample(), "CAPA 6");
        check(poisonPillExample(), "CAPA 7");
        System.out.println("ConcurrentCollections: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) POR QUÉ NO BASTA CON "SINCRONIZAR" UNA COLECCIÓN
        =================================================================

        Collections.synchronizedList(lista) (y synchronizedMap, y las
        viejas Vector y Hashtable) envuelven cada método en un synchronized
        sobre un solo bloqueo. Cada operación SUELTA queda segura. Pero:

        1) Las operaciones COMPUESTAS siguen sin serlo: "si no contiene,
           agrega" son dos llamadas, y otro hilo se mete en medio
           (part1_threads/ConcurrencyProblems, CAPA 3).

        2) RECORRERLA no está protegido: el iterador hace muchas llamadas,
           y si otro hilo agrega mientras tanto, el iterador lanza
           ConcurrentModificationException. La documentación pide envolver
           TODO el recorrido en synchronized (lista) { ... }, deteniendo a
           todos los demás mientras tanto.

        3) UN bloqueo para toda la colección: con muchos hilos, todos hacen
           fila (Synchronization, CAPA 4).

        El ejemplo recorre una lista sincronizada mientras otro hilo agrega,
        y obtiene la excepción.

        Las colecciones de java.util.concurrent resuelven los tres puntos:
        operaciones compuestas atómicas (CAPA 3), iteradores que no fallan
        (CAPA 4) y bloqueos mucho más finos (CAPA 2).
    */
    static boolean synchronizedWrapperExample() throws InterruptedException {
        for (int attempt = 0; attempt < 20; attempt++) {
            List<Integer> list = Collections.synchronizedList(new ArrayList<>());
            for (int i = 0; i < 1_000; i++) {
                list.add(i);
            }
            AtomicBoolean stop = new AtomicBoolean();
            Thread writer = Thread.ofPlatform().start(() -> {
                while (!stop.get()) {
                    list.add(0);                            // cada add, seguro por sí solo
                }
            });
            boolean failed = false;
            try {
                long sum = 0;
                for (int round = 0; round < 1_000 && !failed; round++) {
                    for (int value : list) {                // el recorrido NO está protegido
                        sum += value;
                    }
                }
            } catch (ConcurrentModificationException e) {
                failed = true;
            } finally {
                stop.set(true);
                writer.join();
            }
            if (failed) {
                return true;
            }
        }
        return false;
    }

    /*
        =================================================================
        CAPA 2) CONCURRENTHASHMAP: CÓMO FUNCIONA
        =================================================================

        Por dentro es una tabla de cajones como HashMap (v5_collections/
        part2_sets_and_maps/Maps, CAPA 4), pero:

        - LEER (get) no toma ningún bloqueo: lee los cajones con las
          garantías de un volatile (part1_threads/MemoryModel). Muchos
          lectores a la vez, sin esperar.
        - ESCRIBIR en un cajón VACÍO es un CAS (part2_coordination/Atomics,
          CAPA 2): sin bloqueo.
        - ESCRIBIR en un cajón OCUPADO bloquea SOLO ese cajón (synchronized
          sobre su primer nodo). Dos hilos que escriben claves de cajones
          distintos no se estorban.
        - Al crecer, varios hilos ayudan a mover los cajones a la tabla
          nueva, sin detener a los lectores.

        Resultado: escala con muchos hilos, a diferencia de un mapa con un
        solo bloqueo. Es la colección concurrente que más vas a usar.
    */

    /*
        =================================================================
        CAPA 3) OPERACIONES ATÓMICAS POR CLAVE: MERGE Y COMPUTEIFABSENT
        =================================================================

        El error típico al pasar de HashMap a ConcurrentHashMap es seguir
        escribiendo:

            Integer count = map.get(word);                  // leer
            map.put(word, count == null ? 1 : count + 1);   // escribir: carrera

        Cada llamada es segura; las dos juntas no. Para eso están los
        métodos que hacen leer-calcular-escribir de una clave en UN paso
        (con el cajón bloqueado mientras corre la función):

            merge(clave, 1, Integer::sum)       contar
            computeIfAbsent(clave, k -> crear)  crear solo si falta (cachés)
            compute(clave, (k, v) -> ...)       cualquier cambio
            putIfAbsent(clave, valor)           poner solo si falta

        Dos ejemplos:
        - contar palabras desde 4 hilos con merge: exacto;
        - una caché: 8 hilos piden las mismas 3 claves al mismo tiempo, y la
          función que "carga" cada valor (cara: una consulta, un cálculo)
          corre UNA vez por clave. La documentación lo garantiza: la
          función se aplica como mucho una vez.
    */
    static boolean mergeExample() throws InterruptedException {
        List<String> words = List.of("pago", "envío", "pago", "pedido", "pago", "envío");
        Map<String, Integer> counts = new ConcurrentHashMap<>();
        runInThreads(4, () -> {
            for (int i = 0; i < 10_000; i++) {
                for (String word : words) {
                    counts.merge(word, 1, Integer::sum);    // leer-sumar-escribir: un paso
                }
            }
        });
        return counts.equals(Map.of("pago", 120_000, "envío", 80_000, "pedido", 40_000));
    }

    static boolean computeIfAbsentExample() throws InterruptedException {
        Map<String, String> cache = new ConcurrentHashMap<>();
        Map<String, AtomicInteger> loads = new ConcurrentHashMap<>();
        runInThreads(8, () -> {
            for (String key : List.of("MX", "US", "CA")) {
                cache.computeIfAbsent(key, k -> {
                    loads.computeIfAbsent(k, x -> new AtomicInteger()).incrementAndGet();   // otro mapa: permitido
                    pause(20);                              // "consulta cara"
                    return "tasas de " + k;
                });
            }
        });
        return cache.size() == 3
                && loads.values().stream().allMatch(count -> count.get() == 1);   // una carga por clave
    }

    /*
        =================================================================
        CAPA 4) LAS REGLAS DE CONCURRENTHASHMAP
        =================================================================

        1) NO ACEPTA null, ni como clave ni como valor (NullPointerException).
           En un HashMap, get() == null significa "no está" O "está con
           null" (v5_collections/part2_sets_and_maps/Maps, CAPA 3); con
           varios hilos no habría forma de preguntar cuál sin una carrera.

        2) La función de compute / computeIfAbsent / merge debe ser CORTA
           y NO DEBE MODIFICAR EL MISMO MAPA. Corre con el cajón bloqueado:
           si tarda (una llamada por red), todos los que necesitan ese cajón
           esperan. Y si modifica el mapa, medido:
           - sobre la MISMA clave: IllegalStateException: Recursive update;
           - sobre OTRA clave: a veces funciona, a veces se traba, según en
             qué cajón caiga. Que funcione en tus pruebas no significa nada.

        3) Los iteradores son "débilmente consistentes": NUNCA lanzan
           ConcurrentModificationException, recorren lo que había al
           empezar, y pueden (o no) ver cambios hechos durante el
           recorrido. Ningún elemento se ve dos veces.

        4) size() con hilos escribiendo es un APROXIMADO (cuenta en celdas
           repartidas, como LongAdder; Atomics, CAPA 6). Para decisiones,
           no sirve; para métricas, sí.

        Para un Set concurrente: ConcurrentHashMap.newKeySet().
    */
    static boolean rulesExample() {
        Map<String, Integer> map = new ConcurrentHashMap<>();
        boolean nullRejected;
        try {
            map.put("x", null);
            nullRejected = false;
        } catch (NullPointerException e) {
            nullRejected = true;
        }

        String recursiveError;
        try {
            map.computeIfAbsent("a", k -> map.computeIfAbsent("a", k2 -> 1));
            recursiveError = "";
        } catch (IllegalStateException e) {
            recursiveError = e.getMessage();
        }

        Map<String, Integer> prices = new ConcurrentHashMap<>(Map.of("a", 1, "b", 2, "c", 3, "d", 4));
        int visited = 0;
        for (String key : prices.keySet()) {
            prices.remove("d");                             // en un HashMap: ConcurrentModificationException
            visited += key.isEmpty() ? 0 : 1;
        }

        Set<String> concurrentSet = ConcurrentHashMap.newKeySet();
        concurrentSet.add("ana");
        return nullRejected && recursiveError.equals("Recursive update")
                && prices.size() == 3 && (visited == 3 || visited == 4)   // pudo o no ver la "d"
                && concurrentSet.contains("ana");
    }

    /*
        =================================================================
        CAPA 5) COPYONWRITEARRAYLIST: MUCHAS LECTURAS, CASI NINGUNA ESCRITURA
        =================================================================

        CopyOnWriteArrayList no bloquea al leer ni al recorrer: cada
        ESCRITURA copia el arreglo completo, cambia la copia y la publica
        (con un volatile). Quien estaba recorriendo sigue con su versión
        vieja, intacta: una "foto". Nunca hay ConcurrentModificationException.

        El caso ideal son las listas de suscriptores (v11_patterns/
        part3_behavioral/Observer, CAPA 6): se recorren en cada evento, y
        alguien se suscribe o se da de baja de vez en cuando, incluso
        DURANTE un aviso.

        El precio: cada add copia todo. Medido, 100,000 add uno por uno:

            ArrayList                 ~0.4 a 1.9 ms
            CopyOnWriteArrayList      ~2,700 a 3,750 ms

        Unas 2,000 veces más lento (es O(n²): copiar 1, 2, 3... 100,000
        elementos). Nunca para listas que cambian seguido; para eso,
        una cola concurrente o un bloqueo.

        El ejemplo recorre una lista mientras se le agregan elementos: el
        recorrido ve exactamente los que había al empezar.
    */
    static boolean copyOnWriteExample() {
        List<String> listeners = new CopyOnWriteArrayList<>(List.of("log", "correo", "métricas"));
        List<String> notified = new ArrayList<>();
        for (String listener : listeners) {                 // recorre la foto de 3
            notified.add(listener);
            listeners.add(listener + "-2");                 // se suscriben más durante el aviso
        }
        Iterator<String> snapshot = listeners.iterator();   // una foto de este momento
        listeners.clear();
        int inSnapshot = 0;
        while (snapshot.hasNext()) {
            snapshot.next();
            inSnapshot++;
        }
        return notified.equals(List.of("log", "correo", "métricas"))   // la foto, sin los nuevos
                && inSnapshot == 6;                                     // la foto no vio el clear
    }

    /*
        =================================================================
        CAPA 6) COLAS BLOQUEANTES: PRODUCTOR Y CONSUMIDOR
        =================================================================

        El patrón más útil para repartir trabajo entre hilos: unos hilos
        PRODUCEN tareas (pedidos que llegan) y otros las CONSUMEN
        (procesarlos), comunicándose por una COLA. Ninguno conoce al otro
        ni comparte nada más (part1_threads/ConcurrencyProblems, CAPA 9:
        mensajes en vez de memoria compartida).

        BlockingQueue agrega operaciones que ESPERAN:

            operación         si no se puede        espera     con tiempo límite
            meter             (cola llena)          put(x)     offer(x, t, unidad)
            sacar             (cola vacía)          take()     poll(t, unidad)
            sin esperar:      offer(x) -> false, poll() -> null

        Y su propiedad clave: TAMAÑO MÁXIMO. Una ArrayBlockingQueue(10)
        no pasa de 10: si el productor va más rápido, put() lo DETIENE hasta
        que haya lugar. Eso se llama CONTRAPRESIÓN (backpressure): el lento
        frena al rápido, en vez de que la cola crezca hasta agotar la
        memoria.

            ArrayBlockingQueue(n)    con límite, un arreglo fijo. La opción
                                     por defecto.
            LinkedBlockingQueue()    SIN límite si no le das uno: si el
                                     productor es más rápido, crece hasta el
                                     OutOfMemoryError. Siempre con capacidad:
                                     new LinkedBlockingQueue<>(1_000).

        Todo lo que se mete en la cola es visible para quien lo saca
        (happens-before; part1_threads/MemoryModel, CAPA 3): se pueden
        pasar objetos normales.

        El ejemplo: un productor rápido, dos consumidores lentos, cola de
        10. Se procesan los 200 pedidos, ninguno dos veces, y la cola nunca
        pasa de 10.
    */
    static boolean producerConsumerExample() throws InterruptedException {
        BlockingQueue<Integer> orders = new ArrayBlockingQueue<>(10);
        AtomicInteger processed = new AtomicInteger();
        AtomicInteger maxSize = new AtomicInteger();
        Set<Integer> seen = ConcurrentHashMap.newKeySet();
        AtomicBoolean duplicated = new AtomicBoolean();

        Runnable consumer = () -> {
            try {
                while (true) {
                    Integer order = orders.poll(500, TimeUnit.MILLISECONDS);   // espera, con límite
                    if (order == null) {
                        return;                             // nada en medio segundo: terminó (CAPA 7 lo hace mejor)
                    }
                    if (!seen.add(order)) {
                        duplicated.set(true);
                    }
                    pause(1);                               // procesar es lento
                    processed.incrementAndGet();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
        Thread consumer1 = Thread.ofPlatform().name("consumer-1").start(consumer);
        Thread consumer2 = Thread.ofPlatform().name("consumer-2").start(consumer);

        for (int order = 1; order <= 200; order++) {
            orders.put(order);                              // se detiene si la cola está llena
            maxSize.accumulateAndGet(orders.size(), Math::max);
        }
        consumer1.join();
        consumer2.join();
        return processed.get() == 200 && !duplicated.get() && maxSize.get() <= 10;
    }

    /*
        =================================================================
        CAPA 7) DETENER A LOS CONSUMIDORES
        =================================================================

        ¿Cómo sabe un consumidor que ya no vendrá nada más? Esperar "un
        rato" (como la CAPA 6) es adivinar. Las formas correctas:

        - PÍLDORA VENENOSA (poison pill): el productor, al terminar, mete un
          valor especial por cada consumidor. Quien lo saca, termina. Como
          la cola es FIFO, la píldora llega después de todo el trabajo real.
          Funciona perfecto con un productor; con varios, hay que coordinar
          quién manda las píldoras.
        - INTERRUPCIÓN: take() lanza InterruptedException al interrumpir el
          hilo (part1_threads/ThreadBasics, CAPA 5). Es lo que hace un pool
          al apagarse con shutdownNow (part3_tasks/ExecutorsUsage). Ojo: lo que
          quedaba en la cola no se procesa.

        En código real, el productor-consumidor casi siempre se escribe con
        un ExecutorService, que trae la cola, los hilos y el apagado
        (part3_tasks/ExecutorsUsage). Pero por dentro es exactamente esto.
    */
    private static final String POISON = new String("FIN");   // objeto propio: se compara por referencia

    static boolean poisonPillExample() throws InterruptedException {
        BlockingQueue<String> queue = new ArrayBlockingQueue<>(5);
        List<String> handled = Collections.synchronizedList(new ArrayList<>());
        int consumers = 3;
        CountDownLatch finished = new CountDownLatch(consumers);
        for (int i = 0; i < consumers; i++) {
            Thread.ofPlatform().start(() -> {
                try {
                    while (true) {
                        String item = queue.take();         // espera sin límite
                        if (item == POISON) {
                            break;                          // la píldora: terminar
                        }
                        handled.add(item);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    finished.countDown();
                }
            });
        }
        for (int i = 1; i <= 30; i++) {
            queue.put("pedido-" + i);
        }
        for (int i = 0; i < consumers; i++) {
            queue.put(POISON);                              // una píldora por consumidor
        }
        boolean allStopped = finished.await(5, TimeUnit.SECONDS);
        return allStopped && handled.size() == 30;
    }

    /*
        =================================================================
        CAPA 8) LAS DEMÁS COLECCIONES CONCURRENTES
        =================================================================

            ConcurrentLinkedQueue / Deque   cola sin límite y SIN esperas
                                            (CAS); para cuando nadie necesita
                                            bloquearse esperando
            PriorityBlockingQueue           cola bloqueante por prioridad
                                            (v5_collections/part3_queues/
                                            PriorityQueues); sin límite
            DelayQueue                      cada elemento sale cuando vence su
                                            espera (reintentos programados)
            SynchronousQueue                capacidad CERO: put espera a que
                                            alguien haga take (entrega en
                                            mano); la usa newCachedThreadPool
            LinkedTransferQueue             mezcla de las anteriores
            ConcurrentSkipListMap / Set     mapa y conjunto ORDENADOS
                                            concurrentes (el TreeMap de varios
                                            hilos)

        Y la opción que a menudo es mejor que todas: un objeto INMUTABLE
        (List.of, Map.copyOf; v5_collections/part4_tools/
        ImmutableCollections) que se reemplaza completo en un volatile o un
        AtomicReference cuando cambia (Atomics, CAPA 4). Para configuración
        o catálogos que cambian poco, es lo más simple y rápido.
    */

    /*
        =================================================================
        CAPA 9) ERRORES COMUNES
        =================================================================

        1) synchronizedList/Map creyendo que recorrerlas o "revisar y
           agregar" es seguro (CAPA 1).

        2) get + put en un ConcurrentHashMap en vez de merge o compute
           (CAPA 3).

        3) Funciones lentas dentro de compute, o que modifican el mismo
           mapa; esperar null en un ConcurrentHashMap; decidir con size()
           (CAPA 4).

        4) CopyOnWriteArrayList para listas que cambian seguido (CAPA 5).

        5) LinkedBlockingQueue sin capacidad: memoria sin freno (CAPA 6).

        6) Consumidores que no saben cuándo parar, o que esperan "un rato"
           (CAPA 7).

        7) Usar Hashtable o Vector en código nuevo: son las versiones viejas
           con un solo bloqueo (CAPA 1).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Las colecciones "sincronizadas" protegen cada llamada, no las
          operaciones compuestas ni los recorridos.
        - ConcurrentHashMap: lecturas sin bloqueo, CAS en cajones vacíos,
          bloqueo por cajón. merge/compute/computeIfAbsent son atómicos por
          clave (la función corre una vez, corta y sin tocar el mapa).
          Sin nulls; iteradores sin CME; size aproximado.
        - CopyOnWriteArrayList: recorridos sobre una foto; escrituras
          carísimas (2,000 veces, medido). Para suscriptores.
        - BlockingQueue con límite: productor-consumidor con contrapresión;
          detener con píldora o interrupción.
        - Para datos que cambian poco: inmutable + reemplazo atómico.
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

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void check(boolean condition, String layer) {
        if (!condition) {
            throw new IllegalStateException("El ejemplo de la " + layer + " no hace lo que la guía explica");
        }
    }
}
