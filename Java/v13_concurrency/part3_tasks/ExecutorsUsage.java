package v13_concurrency.part3_tasks;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/*
    =====================================================================
    EXECUTORS: TAREAS EN UN POOL DE HILOS, Y APAGARLO BIEN
    =====================================================================

    Ruta de concurrency/: guía 7 de 9  (part3_tasks)
    Anterior:  part2_coordination/ConcurrentCollections
    Siguiente: part3_tasks/CompletableFutureUsage

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.
    - La guía se llama ExecutorsUsage para no tapar a la clase del JDK
      java.util.concurrent.Executors (igual que v7_functional/
      part2_streams/OptionalUsage). Aun así, aquí la del JDK se escribe
      completa, para que se vea de dónde viene cada pool.

    Lo que ya se vio: lo que cuesta crear un hilo (part1_threads/
    ThreadBasics, CAPA 9), el pool que se traba esperando sus propias
    tareas (part1_threads/ConcurrencyProblems, CAPA 7) y las colas
    bloqueantes (part2_coordination/ConcurrentCollections, CAPA 6).

    Importancia: MUY ALTA. Es la forma normal de ejecutar trabajo en
    paralelo en Java: casi nunca se crea un Thread a mano.

    Índice:
        CAPA 1)  Separar la tarea de quién la ejecuta
        CAPA 2)  Callable y Future: tareas con resultado
        CAPA 3)  Varias tareas: invokeAll e invokeAny
        CAPA 4)  Los pools prefabricados y sus trampas
        CAPA 5)  ThreadPoolExecutor a la medida: cola, límites y rechazo
        CAPA 6)  ¿Cuántos hilos?
        CAPA 7)  Apagar el pool correctamente
        CAPA 8)  Las excepciones que se pierden
        CAPA 9)  Tareas programadas
        CAPA 10) Errores comunes
        RESUMEN FINAL
*/
public class ExecutorsUsage {

    public static void main(String[] args) throws Exception {
        check(reuseExample(), "CAPA 1");
        check(futureExample(), "CAPA 2");
        check(invokeAllAnyExample(), "CAPA 3");
        check(customPoolExample(), "CAPA 5");
        check(shutdownExample(), "CAPA 7");
        check(lostExceptionExample(), "CAPA 8");
        check(scheduledExample(), "CAPA 9");
        System.out.println("ExecutorsUsage: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) SEPARAR LA TAREA DE QUIÉN LA EJECUTA
        =================================================================

        Un hilo por tarea tiene dos problemas: crear hilos de plataforma
        cuesta (~100 microsegundos y 1 MB de pila reservada cada uno;
        ThreadBasics, CAPA 9), y no hay límite: 10,000 peticiones a la vez
        serían 10,000 hilos del sistema operativo.

        Un POOL DE HILOS crea unos cuantos hilos y los REUTILIZA: las tareas
        esperan en una cola, y cada hilo libre toma la siguiente. Es el
        productor-consumidor de ConcurrentCollections, CAPA 6, ya hecho.

        En Java, la interface es ExecutorService:

            ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(4);
            pool.submit(tarea);          // la tarea va a la cola; un hilo la tomará

        Tu código describe QUÉ hacer (un Runnable o un Callable); el pool
        decide CUÁNDO y EN QUÉ HILO. Cambiar de un pool de 4 a uno de hilos
        virtuales (part3_tasks/VirtualThreads) es cambiar UNA línea.

        El ejemplo manda 20 tareas a un pool de 3: las ejecutan solo 3
        hilos, reutilizados.
    */
    static boolean reuseExample() throws InterruptedException {
        Set<String> threadsUsed = ConcurrentHashMap.newKeySet();
        try (ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(3)) {
            for (int i = 0; i < 20; i++) {
                pool.submit(() -> threadsUsed.add(Thread.currentThread().getName()));
            }
        }                                                   // close(): espera a que terminen (CAPA 7)
        return threadsUsed.size() <= 3 && !threadsUsed.isEmpty();
    }

    /*
        =================================================================
        CAPA 2) CALLABLE Y FUTURE: TAREAS CON RESULTADO
        =================================================================

        Runnable no devuelve nada ni puede lanzar excepciones checked.
        Callable<V> sí:

            Callable<Price> task = () -> priceService.fetch("MX");

        submit(callable) devuelve un Future<V> de inmediato: una "promesa"
        del resultado, que todavía no existe.

            future.get()                    espera y devuelve el resultado
            future.get(2, SECONDS)          espera como máximo eso, o
                                            TimeoutException
            future.isDone()                 ¿ya terminó?
            future.cancel(true)             cancelar (true: interrumpir el
                                            hilo si ya empezó)
            future.state() / resultNow()    (Java 19) el estado sin esperar

        Si la tarea lanzó una excepción, get() lanza ExecutionException, y
        la excepción real está en getCause() (como InvocationTargetException
        en v12_jvm/part2_classes/ReflectionAndAnnotations, CAPA 3).

        Y lo que hace que funcione entre hilos: todo lo que la tarea hizo es
        visible para quien recibe el resultado con get() (happens-before;
        part1_threads/MemoryModel, CAPA 3).

        SIEMPRE con tiempo límite en servidores: un get() sin límite sobre
        una tarea que llama a un servicio caído deja ese hilo esperando para
        siempre.
    */
    static boolean futureExample() throws Exception {
        try (ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            Future<Integer> price = pool.submit(() -> 4_500);
            Future<Integer> failing = pool.submit(() -> {
                throw new IllegalStateException("el proveedor no responde");
            });
            Future<Integer> slow = pool.submit(() -> {
                Thread.sleep(10_000);
                return 0;
            });

            boolean resultOk = price.get(2, TimeUnit.SECONDS) == 4_500;

            String cause;
            try {
                failing.get(2, TimeUnit.SECONDS);
                cause = "";
            } catch (ExecutionException e) {
                cause = e.getCause().getMessage();          // la real, adentro
            }

            boolean timedOut;
            try {
                slow.get(100, TimeUnit.MILLISECONDS);
                timedOut = false;
            } catch (TimeoutException e) {
                timedOut = true;
                slow.cancel(true);                          // y cancelarla: interrumpe su sleep
            }
            return resultOk && cause.equals("el proveedor no responde") && timedOut && slow.isCancelled();
        }
    }

    /*
        =================================================================
        CAPA 3) VARIAS TAREAS: INVOKEALL E INVOKEANY
        =================================================================

        - invokeAll(tareas): ejecuta todas y espera a que TODAS terminen.
          Devuelve los Future en el MISMO orden en que se pasaron las
          tareas, no en el orden en que terminaron. Con tiempo límite, las
          que no alcanzaron se cancelan.
        - invokeAny(tareas): devuelve el resultado de la PRIMERA que
          termina bien y cancela las demás. Útil para preguntar lo mismo a
          varias réplicas y quedarse con la más rápida.

        Para combinar resultados sin bloquear un hilo esperando (cuando
        esto termine, haz aquello), part3_tasks/CompletableFutureUsage.
    */
    static boolean invokeAllAnyExample() throws Exception {
        try (ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(3)) {
            List<Callable<String>> lookups = List.of(
                    () -> slowly(150, "MX"),
                    () -> slowly(10, "US"),
                    () -> slowly(80, "CA"));
            List<String> inOrder = pool.invokeAll(lookups).stream()
                    .map(ExecutorsUsage::resultOf)
                    .toList();
            String fastest = pool.invokeAny(lookups);
            return inOrder.equals(List.of("MX", "US", "CA"))    // orden de las tareas, no de llegada
                    && fastest.equals("US");
        }
    }

    /*
        =================================================================
        CAPA 4) LOS POOLS PREFABRICADOS Y SUS TRAMPAS
        =================================================================

        La clase java.util.concurrent.Executors fabrica pools comunes:

            newFixedThreadPool(n)        n hilos fijos. Su cola NO tiene
                                         límite (LinkedBlockingQueue): si
                                         llegan tareas más rápido de lo que
                                         se procesan, la cola crece hasta el
                                         OutOfMemoryError (ConcurrentCollections,
                                         CAPA 6).
            newCachedThreadPool()        crea un hilo nuevo si no hay uno
                                         libre, SIN límite. Con una ráfaga de
                                         10,000 tareas lentas, 10,000 hilos
                                         del SO.
            newSingleThreadExecutor()    un hilo: las tareas corren en orden,
                                         una a la vez (útil para serializar
                                         acceso a algo).
            newScheduledThreadPool(n)    tareas con retraso o periódicas
                                         (CAPA 9).
            newVirtualThreadPerTaskExecutor()   un hilo virtual por tarea
                                         (part3_tasks/VirtualThreads). Lo
                                         normal hoy para tareas que esperan
                                         red o base de datos.
            newWorkStealingPool()        un ForkJoinPool: cada hilo con su
                                         cola, y los libres "roban" trabajo
                                         a los ocupados. Para muchas tareas
                                         pequeñas de CPU.

        Y uno que usas sin crearlo: ForkJoinPool.commonPool(), compartido
        por TODA la JVM. Lo usan los streams paralelos (v7_functional/
        part2_streams/AdvancedStreams) y CompletableFuture cuando no le das
        un pool. Bloquearlo con tareas lentas (llamadas por red) frena a
        todos los demás que lo usan.

        Para servidores, los prefabricados con cola o hilos sin límite son
        un riesgo: mejor un ThreadPoolExecutor configurado (CAPA 5), o hilos
        virtuales con un límite explícito (un Semaphore; Synchronization,
        CAPA 9).
    */

    /*
        =================================================================
        CAPA 5) THREADPOOLEXECUTOR A LA MEDIDA: COLA, LÍMITES Y RECHAZO
        =================================================================

        Todos los pools de la CAPA 4 (menos los de ForkJoin y virtuales) son
        un ThreadPoolExecutor con distintos parámetros:

            new ThreadPoolExecutor(
                core,          hilos que se mantienen siempre
                max,           hilos máximos
                keepAlive,     cuánto vive un hilo extra sin trabajo
                cola,          dónde esperan las tareas (¡con límite!)
                threadFactory, cómo se crean los hilos (NOMBRES)
                rechazo)       qué hacer si todo está lleno

        El orden en que crece SORPRENDE a todo el mundo:
            1) hasta core hilos: crea un hilo por tarea;
            2) después: METE A LA COLA;
            3) solo si la cola está LLENA: crea hilos hasta max;
            4) si ya hay max hilos y la cola está llena: RECHAZA.
        Con una cola sin límite, el paso 3 nunca llega: max no sirve de
        nada.

        Las políticas de rechazo:
            AbortPolicy (defecto)   lanza RejectedExecutionException
            CallerRunsPolicy        la ejecuta EL HILO QUE LA MANDÓ: lo
                                    frena de forma natural (contrapresión)
            DiscardPolicy / DiscardOldestPolicy   tirar la tarea (casi
                                    nunca: se pierde trabajo sin avisar)

        Y la ThreadFactory: para que los hilos se llamen "payments-1" en vez
        de "pool-7-thread-3" en los volcados y logs (ThreadBasics, CAPA 2).

        El ejemplo: core 1, max 2, cola de 1. Caben 3 tareas (1 en el hilo
        core, 1 en la cola, 1 en un hilo extra); la cuarta se rechaza. Con
        CallerRunsPolicy, la cuarta la ejecuta main.
    */
    static ThreadFactory named(String prefix) {
        AtomicInteger number = new AtomicInteger();
        return task -> Thread.ofPlatform().name(prefix + "-" + number.incrementAndGet()).unstarted(task);
    }

    static boolean customPoolExample() throws InterruptedException {
        CountDownLatch release = new CountDownLatch(1);
        Runnable blocking = () -> awaitQuietly(release);

        boolean rejected;
        ThreadPoolExecutor abort = new ThreadPoolExecutor(1, 2, 30, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(1), named("orders"), new ThreadPoolExecutor.AbortPolicy());
        try (abort) {
            abort.execute(blocking);                        // 1) hilo core
            abort.execute(blocking);                        // 2) a la cola
            abort.execute(blocking);                        // 3) cola llena: hilo extra
            try {
                abort.execute(blocking);                    // 4) todo lleno
                rejected = false;
            } catch (RejectedExecutionException e) {
                rejected = true;
            }
            release.countDown();
        }

        CountDownLatch release2 = new CountDownLatch(1);
        AtomicReference<String> fourthRanOn = new AtomicReference<>();
        ThreadPoolExecutor callerRuns = new ThreadPoolExecutor(1, 2, 30, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(1), named("reports"), new ThreadPoolExecutor.CallerRunsPolicy());
        try (callerRuns) {
            for (int i = 0; i < 3; i++) {
                callerRuns.execute(() -> awaitQuietly(release2));
            }
            callerRuns.execute(() -> fourthRanOn.set(Thread.currentThread().getName()));   // la corre main
            release2.countDown();
        }
        return rejected && "main".equals(fourthRanOn.get());
    }

    /*
        =================================================================
        CAPA 6) ¿CUÁNTOS HILOS?
        =================================================================

        Depende de qué hacen las tareas:

        - USO DE CPU (calcular, comprimir, ordenar): tantos hilos como
          núcleos (Runtime.getRuntime().availableProcessors()). Más hilos
          no hacen más trabajo: solo se turnan, con el costo de cambiar de
          contexto (ThreadBasics, CAPA 9).

        - ESPERAS (red, base de datos, disco): mientras esperan no usan
          CPU, así que caben más. Una estimación clásica:

              hilos = núcleos * (1 + tiempo esperando / tiempo calculando)

          Con 8 núcleos y tareas que esperan 90 ms y calculan 10 ms: 8 * 10
          = 80 hilos. Es un punto de partida; lo real se MIDE (v12_jvm/
          part3_performance/Performance).

        Hoy, para tareas que esperan, la respuesta suele ser: hilos
        virtuales (part3_tasks/VirtualThreads), y limitar lo que de verdad
        es escaso (las conexiones a la base de datos) con un Semaphore o un
        pool de conexiones.

        Y separar: un pool por cada dependencia lenta (pagos, correo,
        reportes). Si el proveedor de correo se cae y sus tareas se
        acumulan, solo se llena SU pool; los pagos siguen funcionando. Se le
        llama "bulkhead" (los compartimentos de un barco).
    */

    /*
        =================================================================
        CAPA 7) APAGAR EL POOL CORRECTAMENTE
        =================================================================

        Los hilos de un pool NO son daemon: un pool que nadie apaga deja la
        JVM viva cuando main termina (ThreadBasics, CAPA 6).

            shutdown()             no acepta tareas nuevas; las de la cola y
                                   las que corren TERMINAN
            awaitTermination(t)    esperar a que termine todo (con límite)
            shutdownNow()          no acepta nuevas, DEVUELVE las que no
                                   empezaron e INTERRUMPE a las que corren
                                   (solo paran si respetan la interrupción:
                                   ThreadBasics, CAPA 5)
            close()  (Java 19)     shutdown + esperar a que terminen. Hace
                                   al pool AutoCloseable: try-with-resources

        El apagado ordenado de un servidor:

            pool.shutdown();
            if (!pool.awaitTermination(30, SECONDS)) {   // margen para terminar
                pool.shutdownNow();                       // y si no, cortar
            }

        Para pools de vida corta (dentro de un método), try-with-resources
        como en todos los ejemplos de esta guía.

        El ejemplo: shutdownNow sobre un pool de 1 hilo con una tarea
        corriendo y 5 en cola. Devuelve las 5 que no empezaron e interrumpe
        la que corría.
    */
    static boolean shutdownExample() throws InterruptedException {
        ExecutorService pool = java.util.concurrent.Executors.newSingleThreadExecutor(named("shutdown-demo"));
        AtomicBoolean runningWasInterrupted = new AtomicBoolean();
        CountDownLatch started = new CountDownLatch(1);
        pool.submit(() -> {
            started.countDown();
            try {
                Thread.sleep(60_000);
            } catch (InterruptedException e) {
                runningWasInterrupted.set(true);            // respeta la interrupción y termina
                Thread.currentThread().interrupt();
            }
        });
        for (int i = 0; i < 5; i++) {
            pool.submit(() -> { });
        }
        started.await();
        List<Runnable> neverStarted = pool.shutdownNow();
        boolean terminated = pool.awaitTermination(5, TimeUnit.SECONDS);

        boolean rejectsNew;
        try {
            pool.submit(() -> { });
            rejectsNew = false;
        } catch (RejectedExecutionException e) {
            rejectsNew = true;                              // apagado: ya no acepta nada
        }
        return neverStarted.size() == 5 && terminated && runningWasInterrupted.get() && rejectsNew;
    }

    /*
        =================================================================
        CAPA 8) LAS EXCEPCIONES QUE SE PIERDEN
        =================================================================

        La diferencia entre execute y submit importa:

        - execute(runnable): si la tarea lanza una excepción, mata ese hilo
          del pool (el pool crea otro) y la excepción va al
          UncaughtExceptionHandler del hilo (ThreadBasics, CAPA 7): se ve en
          el log.
        - submit(tarea): la excepción se GUARDA en el Future. Si nadie llama
          a get(), NADIE SE ENTERA. Ni log, ni nada. Un trabajo de fondo que
          falla todas las noches en silencio.

        Reglas:
        - si usas submit, alguien tiene que llamar a get() (o usar
          CompletableFuture y manejar el error: part3_tasks/CompletableFutureUsage);
        - si no te importa el resultado, execute, con un handler que vaya al
          log;
        - o atrapa y registra DENTRO de la tarea.

        El ejemplo: la misma tarea que falla, con submit y con execute, en
        un pool cuyos hilos reportan las excepciones. Solo execute llega al
        handler.
    */
    static boolean lostExceptionExample() throws InterruptedException {
        AtomicInteger reported = new AtomicInteger();
        CountDownLatch handlerRan = new CountDownLatch(1);
        AtomicInteger number = new AtomicInteger();
        ThreadFactory reporting = task -> Thread.ofPlatform()
                .name("jobs-" + number.incrementAndGet())
                .uncaughtExceptionHandler((thread, error) -> {
                    reported.incrementAndGet();             // aquí iría log.error(...)
                    handlerRan.countDown();
                })
                .unstarted(task);
        Runnable failing = () -> {
            throw new IllegalStateException("el reporte nocturno falló");
        };
        try (ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(1, reporting)) {
            pool.submit(failing);                           // guardada en un Future que nadie lee
        }
        boolean submitSilent = !handlerRan.await(200, TimeUnit.MILLISECONDS);   // nadie se enteró
        try (ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(1, reporting)) {
            pool.execute(failing);                          // al handler: queda registrada
        }
        // El pool se da por terminado un instante ANTES de que el hilo que murió llame al
        // handler: close() puede regresar primero. Por eso se espera al handler, con límite.
        boolean executeReported = handlerRan.await(5, TimeUnit.SECONDS);
        return submitSilent && executeReported && reported.get() == 1;
    }

    /*
        =================================================================
        CAPA 9) TAREAS PROGRAMADAS
        =================================================================

        ScheduledExecutorService (newScheduledThreadPool) ejecuta tareas
        más tarde o periódicamente:

            schedule(tarea, 5, SECONDS)                   una vez, en 5 s
            scheduleAtFixedRate(tarea, 0, 1, MINUTES)     cada minuto, contando
                                                          desde el INICIO de cada
                                                          ejecución (si una tarda
                                                          más, la siguiente sale
                                                          de inmediato)
            scheduleWithFixedDelay(tarea, 0, 1, MINUTES)  un minuto ENTRE el fin
                                                          de una y el inicio de
                                                          la siguiente

        La trampa: si UNA ejecución de una tarea periódica lanza una
        excepción, las siguientes se CANCELAN EN SILENCIO. La excepción
        queda en el ScheduledFuture que nadie mira (CAPA 8). Un trabajo
        "cada 5 minutos" que dejó de correr el martes y nadie lo supo.

        Por eso las tareas periódicas atrapan TODO adentro y lo registran:

            () -> {
                try { trabajo(); }
                catch (RuntimeException e) { log.error("falló, sigo", e); }
            }

        El ejemplo: una tarea cada 10 ms que falla en su tercera ejecución.
        Después de eso no vuelve a correr nunca. La versión que atrapa la
        excepción sigue corriendo.

        (Para tareas programadas de verdad en un servidor, "todos los días a
        las 2 am", Spring tiene @Scheduled con expresiones cron: v15_spring/.)
    */
    static boolean scheduledExample() throws Exception {
        AtomicInteger fragileRuns = new AtomicInteger();
        AtomicInteger robustRuns = new AtomicInteger();
        try (ScheduledExecutorService scheduler = java.util.concurrent.Executors.newScheduledThreadPool(2)) {
            ScheduledFuture<?> fragile = scheduler.scheduleAtFixedRate(() -> {
                if (fragileRuns.incrementAndGet() == 3) {
                    throw new IllegalStateException("falló la tercera vez");
                }
            }, 0, 10, TimeUnit.MILLISECONDS);
            ScheduledFuture<?> robust = scheduler.scheduleAtFixedRate(() -> {
                try {
                    if (robustRuns.incrementAndGet() == 3) {
                        throw new IllegalStateException("falló la tercera vez");
                    }
                } catch (RuntimeException e) {
                    // aquí iría log.error(...): registrar y seguir
                }
            }, 0, 10, TimeUnit.MILLISECONDS);

            Thread.sleep(200);
            boolean fragileStopped = fragile.isDone() && fragileRuns.get() == 3;   // nunca más
            boolean robustContinued = robustRuns.get() > 5;
            robust.cancel(false);
            String cause;
            try {
                fragile.get();
                cause = "";
            } catch (ExecutionException e) {
                cause = e.getCause().getMessage();          // la excepción estaba guardada ahí
            }
            return fragileStopped && robustContinued && cause.equals("falló la tercera vez");
        }
    }

    /*
        =================================================================
        CAPA 10) ERRORES COMUNES
        =================================================================

        1) Crear un Thread por tarea en vez de usar un pool (CAPA 1).

        2) future.get() sin tiempo límite; no desenvolver ExecutionException
           (CAPA 2).

        3) newFixedThreadPool o newCachedThreadPool en servidores sin pensar
           en su cola o sus hilos sin límite; tareas lentas en el commonPool
           (CAPA 4).

        4) Creer que max se alcanza antes de llenar la cola; colas sin
           límite; hilos sin nombre (CAPA 5).

        5) Pools de 200 hilos para trabajo de CPU, o un solo pool compartido
           por todas las dependencias (CAPA 6).

        6) No apagar los pools: la JVM no termina; o shutdownNow con tareas
           que ignoran la interrupción (CAPA 7).

        7) submit sin que nadie lea el Future: excepciones perdidas
           (CAPA 8).

        8) Tareas periódicas sin try/catch: dejan de correr en silencio
           (CAPA 9).

        9) Esperar, desde una tarea, otra del mismo pool (ConcurrencyProblems,
           CAPA 7).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Un pool reutiliza hilos y limita cuántos hay; tú das tareas, él
          decide dónde corren.
        - Callable devuelve un Future: get con tiempo límite; la excepción
          llega en ExecutionException.
        - invokeAll: todas, en el orden pedido; invokeAny: la primera que
          termina bien.
        - Los prefabricados tienen colas o hilos sin límite; en servidores,
          ThreadPoolExecutor con cola limitada, nombres y CallerRunsPolicy,
          o hilos virtuales.
        - Orden de crecimiento: core -> cola -> max -> rechazo.
        - CPU: hilos = núcleos; esperas: más, o hilos virtuales; un pool por
          dependencia.
        - Apagar: shutdown + awaitTermination + shutdownNow, o close().
        - submit guarda las excepciones; tareas periódicas que fallan se
          cancelan en silencio.
    */

    // -----------------------------------------------------------------
    // Utilidades internas de la guía.
    // -----------------------------------------------------------------

    private static String slowly(long millis, String value) throws InterruptedException {
        Thread.sleep(millis);
        return value;
    }

    private static String resultOf(Future<String> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException e) {
            return null;
        }
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
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
