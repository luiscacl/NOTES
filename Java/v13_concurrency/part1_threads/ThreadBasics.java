package v13_concurrency.part1_threads;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.LongStream;

/*
    =====================================================================
    HILOS: VARIAS COSAS A LA VEZ DENTRO DE UN PROGRAMA
    =====================================================================

    Ruta de concurrency/: guía 1 de 9  (part1_threads)
    Anterior:  v12_jvm/part3_performance/Performance
    Siguiente: part1_threads/ConcurrencyProblems

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.
    - Números medidos en Windows 11, JDK 26, procesador de 14 núcleos
      lógicos. El sistema operativo de referencia es Linux; las
      diferencias de Windows se dicen.
    - Esta guía enseña el MECANISMO. En código real casi nunca se crea un
      Thread a mano: se usan pools y tareas (part3_tasks/ExecutorsUsage) o
      hilos virtuales (part3_tasks/VirtualThreads). Pero todo eso está
      construido sobre lo de aquí.

    Importancia: MUY ALTA. Un servidor atiende miles de peticiones a la
    vez con hilos; los errores de concurrencia son de los más difíciles
    de encontrar.

    Índice:
        CAPA 1)  Qué es un hilo, visto desde el sistema operativo
        CAPA 2)  Crear y arrancar: start, no run
        CAPA 3)  join: esperar a que termine (y ver sus resultados)
        CAPA 4)  El ciclo de vida: los seis estados
        CAPA 5)  Detener un hilo: la interrupción
        CAPA 6)  Hilos daemon: cuándo termina la JVM
        CAPA 7)  Excepciones que nadie atrapa
        CAPA 8)  ThreadLocal: un valor por hilo
        CAPA 9)  Lo que cuesta un hilo, y sleep y prioridades
        CAPA 10) Errores comunes
        RESUMEN FINAL
*/
public class ThreadBasics {

    public static void main(String[] args) throws InterruptedException {
        check(processorsExample(), "CAPA 1");
        check(startVersusRunExample(), "CAPA 2");
        check(joinExample(), "CAPA 3");
        check(lifecycleExample(), "CAPA 4");
        check(interruptionExample(), "CAPA 5");
        check(daemonExample(), "CAPA 6");
        check(uncaughtExceptionExample(), "CAPA 7");
        check(threadLocalExample(), "CAPA 8");
        System.out.println("ThreadBasics: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) QUÉ ES UN HILO, VISTO DESDE EL SISTEMA OPERATIVO
        =================================================================

        Un PROCESO es un programa corriendo, con su propio espacio de
        memoria (part1_memory/StackAndHeapMemory, CAPA 1, en v12_jvm/). Un
        HILO es una línea de ejecución DENTRO de un proceso. Un proceso
        tiene uno o muchos hilos, y todos:
        - COMPARTEN la memoria del proceso: el heap, los static, los
          archivos abiertos;
        - tienen cada uno SU pila (sus variables locales) y sus registros
          del procesador (en qué instrucción va).

        Compartir el heap es lo útil (dos hilos trabajan sobre los mismos
        datos sin copiarlos) y lo peligroso (dos hilos cambian el mismo
        objeto a la vez: part1_threads/ConcurrencyProblems).

        En Java, un Thread "normal" (de PLATAFORMA) es exactamente un hilo
        del sistema operativo, 1 a 1:
        - en Linux, Thread.start() termina en la llamada al sistema clone(),
          que crea una "tarea" del kernel que comparte la memoria con su
          proceso (lo que la librería pthreads llama un hilo);
        - en Windows, en CreateThread.
        Por eso un volcado de hilos (jcmd <pid> Thread.print; v12_jvm/
        part3_performance/Performance, CAPA 8) muestra el número que el SO
        le dio a cada hilo. Medido:

            "main" #3 [23952] ... nid=23952 waiting on condition
               java.lang.Thread.State: TIMED_WAITING (sleeping)

        Y un programa Java "de un solo hilo" tiene muchos: medido, 47 en un
        programa que solo dormía (el recolector de basura, los compiladores
        JIT, el de los Cleaner, el que atiende a jcmd...).

        ¿Quién decide qué hilo corre? El PLANIFICADOR del sistema operativo.
        Si hay más hilos listos que núcleos, les da turnos de pocos
        milisegundos y los interrumpe cuando se les acaba el turno
        (planificación "preventiva"). Dos ideas que se confunden:
        - CONCURRENCIA: varias tareas avanzan en el mismo periodo,
          turnándose (posible con un solo núcleo);
        - PARALELISMO: varias tareas corren en el MISMO instante, en
          núcleos distintos.
        Java ve cuántos núcleos lógicos hay con availableProcessors() (14
        aquí; en un contenedor, los que el contenedor permite).
    */
    static boolean processorsExample() {
        return Runtime.getRuntime().availableProcessors() >= 1
                && Thread.currentThread().getName().equals("main");   // el hilo que ejecuta main
    }

    /*
        =================================================================
        CAPA 2) CREAR Y ARRANCAR: START, NO RUN
        =================================================================

        Un hilo recibe su trabajo como un Runnable (una interface funcional:
        una lambda basta) y empieza con start():

            Thread worker = Thread.ofPlatform().name("worker").start(tarea);

        Thread.ofPlatform() (Java 21) es la forma moderna, con nombre,
        daemon, etc. La forma clásica sigue funcionando:

            Thread worker = new Thread(tarea, "worker");
            worker.start();

        PONLES NOMBRE: en un volcado de hilos o en un log, "pago-worker-3"
        dice algo; "Thread-27" no.

        El error clásico: llamar a run() en vez de start(). run() es un
        método normal: ejecuta la tarea EN EL HILO QUE LO LLAMA, sin crear
        ninguno. El programa "funciona", pero todo es secuencial. El
        ejemplo lo demuestra preguntando, desde la tarea, en qué hilo
        corre.

        Heredar de Thread y sobrescribir run() también funciona, pero mezcla
        "la tarea" con "el mecanismo que la ejecuta". Con un Runnable, la
        misma tarea se puede pasar después a un pool (part3_tasks/
        Executors) sin cambiarla.
    */
    static boolean startVersusRunExample() throws InterruptedException {
        AtomicReference<String> ranOn = new AtomicReference<>();
        Runnable task = () -> ranOn.set(Thread.currentThread().getName());

        Thread wrong = Thread.ofPlatform().name("worker").unstarted(task);
        wrong.run();                                        // NO crea un hilo
        String withRun = ranOn.get();

        Thread right = Thread.ofPlatform().name("worker").start(task);
        right.join();
        return withRun.equals("main") && ranOn.get().equals("worker");
    }

    /*
        =================================================================
        CAPA 3) JOIN: ESPERAR A QUE TERMINE (Y VER SUS RESULTADOS)
        =================================================================

        start() regresa de inmediato: el hilo nuevo corre "por su lado".
        Para esperar a que TERMINE se usa join(). Con tiempo límite,
        join(Duration) o join(millis): regresa al cumplirse aunque el hilo
        siga (hay que preguntar isAlive() después).

        join hace algo más importante que esperar: GARANTIZA que todo lo
        que el hilo escribió en memoria es visible para quien hizo join. Sin
        esa garantía, el hilo principal podría leer valores viejos (se
        explica en part1_threads/MemoryModel: join crea una relación
        "happens-before").

        El ejemplo reparte una suma grande entre 4 hilos, cada uno escribe
        su resultado parcial en SU casilla de un arreglo (nadie escribe la
        casilla de otro), y después de join se suman los parciales.
    */
    static boolean joinExample() throws InterruptedException {
        int parts = 4;
        long n = 1_000_000;
        long[] partial = new long[parts];
        List<Thread> workers = new ArrayList<>();
        for (int part = 0; part < parts; part++) {
            int index = part;
            long from = index * n / parts + 1;
            long to = (index + 1) * n / parts;
            workers.add(Thread.ofPlatform().name("sum-" + index)
                    .start(() -> partial[index] = LongStream.rangeClosed(from, to).sum()));   // su casilla
        }
        for (Thread worker : workers) {
            worker.join();                                  // esperar Y ver lo que escribió
        }
        return LongStream.of(partial).sum() == n * (n + 1) / 2;
    }

    /*
        =================================================================
        CAPA 4) EL CICLO DE VIDA: LOS SEIS ESTADOS
        =================================================================

            NEW            creado, sin start() todavía
            RUNNABLE       corriendo, O listo esperando turno, O esperando
                           al SO (leyendo de la red o del disco): para Java
                           todo eso es "runnable"
            BLOCKED        esperando entrar a un bloque synchronized que
                           otro hilo tiene (part2_coordination/
                           Synchronization)
            WAITING        esperando sin límite: join(), un latch, una cola
            TIMED_WAITING  esperando con límite: sleep(ms), join(ms)
            TERMINATED     run() terminó (bien o con excepción). No se puede
                           volver a arrancar: start() otra vez lanza
                           IllegalThreadStateException

        Estos estados son los que muestra el volcado de hilos (CAPA 1), y
        leerlos es la herramienta principal cuando "la aplicación se
        congeló": muchos hilos en BLOCKED sobre el mismo bloqueo, o todos en
        WAITING esperando algo que nunca llega.

        Ojo con RUNNABLE: un hilo esperando una respuesta de red aparece
        como RUNNABLE aunque no use CPU. Para el SO está dormido; para Java,
        "está en una llamada nativa".

        El ejemplo pone hilos en cada estado y lo comprueba.
    */
    static boolean lifecycleExample() throws InterruptedException {
        CountDownLatch release = new CountDownLatch(1);
        Object lock = new Object();

        Thread waiting = Thread.ofPlatform().name("waiting").unstarted(() -> awaitQuietly(release));
        boolean isNew = waiting.getState() == Thread.State.NEW;
        waiting.start();

        Thread sleeping = Thread.ofPlatform().name("sleeping").start(() -> sleepQuietly(10_000));

        Thread blocked;
        boolean blockedSeen;
        synchronized (lock) {                               // main tiene el bloqueo...
            blocked = Thread.ofPlatform().name("blocked").start(() -> {
                synchronized (lock) {                       // ...así que este espera para entrar
                    // entra cuando main suelte el bloqueo
                }
            });
            blockedSeen = reachesState(blocked, Thread.State.BLOCKED);
        }

        boolean waitingSeen = reachesState(waiting, Thread.State.WAITING);
        boolean timedSeen = reachesState(sleeping, Thread.State.TIMED_WAITING);

        release.countDown();
        sleeping.interrupt();                               // despertarlo (CAPA 5)
        waiting.join();
        sleeping.join();
        blocked.join();
        return isNew && blockedSeen && waitingSeen && timedSeen
                && waiting.getState() == Thread.State.TERMINATED;
    }

    /*
        =================================================================
        CAPA 5) DETENER UN HILO: LA INTERRUPCIÓN
        =================================================================

        Java NO tiene una forma segura de MATAR un hilo desde fuera
        (Thread.stop existió y era peligrosísimo: cortaba el hilo a la mitad
        de cualquier cosa, dejando objetos a medio modificar. En Java 26 el
        método ya no existe).

        Lo que hay es la INTERRUPCIÓN, que es COOPERATIVA: thread.interrupt()
        solo PIDE que se detenga, encendiendo una bandera. El hilo decide
        cuándo y cómo parar:

        1) Si está haciendo trabajo de CPU, revisa la bandera de vez en
           cuando:
               while (!Thread.currentThread().isInterrupted()) { ... }

        2) Si está BLOQUEADO esperando (sleep, join, wait, una cola
           bloqueante), esa espera termina de inmediato con
           InterruptedException. Al lanzarla, la JVM APAGA la bandera.

        Qué hacer con InterruptedException (la regla más ignorada de Java):
        - si puedes, déjala salir (declara throws InterruptedException);
        - si no puedes (estás en un Runnable), RESTAURA la bandera con
          Thread.currentThread().interrupt() y termina, para que quien te
          llamó también se entere;
        - NUNCA catch vacío: el pedido de detenerse se pierde, y el
          programa no se puede apagar bien (un pool que nunca termina de
          cerrarse; part3_tasks/ExecutorsUsage).

        Límite: las lecturas clásicas de sockets y archivos (java.io) NO se
        interrumpen. Para cortarlas se cierra el recurso o se usan tiempos
        límite (v8_apis/part2_io_and_network/HttpClientUsage).
    */
    static boolean interruptionExample() throws InterruptedException {
        AtomicBoolean cpuWorkerStopped = new AtomicBoolean();
        Thread cpuWorker = Thread.ofPlatform().name("cpu-worker").start(() -> {
            long counter = 0;
            while (!Thread.currentThread().isInterrupted()) {   // 1) revisa la bandera
                counter++;
            }
            cpuWorkerStopped.set(counter > 0);
        });

        AtomicBoolean flagClearedByException = new AtomicBoolean();
        AtomicBoolean flagRestored = new AtomicBoolean();
        Thread sleeper = Thread.ofPlatform().name("sleeper").start(() -> {
            try {
                Thread.sleep(60_000);                       // 2) espera bloqueada
            } catch (InterruptedException e) {
                flagClearedByException.set(!Thread.currentThread().isInterrupted());   // la JVM la apagó
                Thread.currentThread().interrupt();         // restaurarla: la forma correcta
                flagRestored.set(Thread.currentThread().isInterrupted());
            }
        });

        cpuWorker.interrupt();
        sleeper.interrupt();
        cpuWorker.join(5_000);
        sleeper.join(5_000);
        return cpuWorkerStopped.get() && flagClearedByException.get() && flagRestored.get()
                && !cpuWorker.isAlive() && !sleeper.isAlive();
    }

    /*
        =================================================================
        CAPA 6) HILOS DAEMON: CUÁNDO TERMINA LA JVM
        =================================================================

        La JVM termina cuando ya no queda ningún hilo NO-daemon vivo (o
        cuando alguien llama a System.exit). Los hilos DAEMON no la
        mantienen viva: son para tareas "de fondo" (los del GC y del JIT son
        daemon).

        Por defecto, un hilo hereda lo que sea quien lo creó: los creados
        desde main NO son daemon. Consecuencia: si olvidas detener un hilo
        normal, el programa termina main... y no se cierra nunca.

        El peligro de daemon: cuando la JVM se apaga, los daemon se cortan
        DE GOLPE, sin terminar sus finally. Nunca le des a un daemon un
        trabajo que deje cosas a medias (escribir un archivo, mandar un
        pago). Para esos, un hilo normal que se detiene bien (CAPA 5).
    */
    static boolean daemonExample() throws InterruptedException {
        AtomicBoolean childIsDaemon = new AtomicBoolean();
        Thread daemon = Thread.ofPlatform().name("background").daemon(true).start(() -> {
            Thread child = new Thread(() -> { });           // creado desde un daemon...
            childIsDaemon.set(child.isDaemon());            // ...hereda daemon
        });
        daemon.join();
        Thread normal = Thread.ofPlatform().unstarted(() -> { });
        return daemon.isDaemon() && childIsDaemon.get() && !normal.isDaemon();
    }

    /*
        =================================================================
        CAPA 7) EXCEPCIONES QUE NADIE ATRAPA
        =================================================================

        Si run() lanza una excepción, muere SOLO ese hilo. main y los demás
        siguen como si nada; nadie se entera. Por defecto la JVM imprime el
        stack trace en la salida de error ("Exception in thread "worker"
        ...") y ya. En un servidor, eso puede ser un trabajo de fondo que
        dejó de correr hace días sin que nadie lo note.

        Se atrapa con un UncaughtExceptionHandler, por hilo o global
        (Thread.setDefaultUncaughtExceptionHandler), que normalmente manda
        el error al log y a las alertas.

        Con pools y tareas es distinto: la excepción se guarda en el
        resultado de la tarea (un Future) y aparece cuando lo pides
        (part3_tasks/ExecutorsUsage). Otra razón para no usar Thread a mano.
    */
    static boolean uncaughtExceptionExample() throws InterruptedException {
        AtomicReference<String> reported = new AtomicReference<>();
        Thread worker = Thread.ofPlatform().name("report-job")
                .uncaughtExceptionHandler((thread, error) ->
                        reported.set(thread.getName() + ": " + error.getMessage()))
                .start(() -> {
                    throw new IllegalStateException("no hay conexión con la base de datos");
                });
        worker.join();
        return "report-job: no hay conexión con la base de datos".equals(reported.get());
    }

    /*
        =================================================================
        CAPA 8) THREADLOCAL: UN VALOR POR HILO
        =================================================================

        Un ThreadLocal<T> es una variable con UN VALOR DISTINTO POR HILO.
        Cada hilo ve y cambia solo el suyo. Usos reales:
        - datos de la petición actual (quién es el usuario, un id para
          seguir la petición en los logs) sin pasarlos por cada método;
        - objetos caros que NO son seguros entre hilos, uno por hilo (fue el
          truco clásico para SimpleDateFormat; hoy DateTimeFormatter es
          inmutable y no lo necesita: v8_apis/part1_text_and_time/
          DatesAndTimes).

        Por dentro, cada objeto Thread tiene un mapa con sus valores: el
        valor vive lo que viva el HILO. Y ahí está la trampa (v12_jvm/
        part1_memory/GarbageCollector, CAPA 5): en un POOL, los hilos no
        mueren nunca. Si una petición pone "usuario = Ana" y no lo quita, la
        siguiente petición que use ese hilo ve a Ana. Fuga de memoria y
        fuga de datos. Por eso siempre:

            CURRENT_USER.set(user);
            try { ... } finally { CURRENT_USER.remove(); }

        La alternativa moderna (Java 25) es ScopedValue: un valor que existe
        solo mientras corre un bloque, sin remove que olvidar
        (part3_tasks/VirtualThreads).
    */
    static final ThreadLocal<String> CURRENT_USER = ThreadLocal.withInitial(() -> "anónimo");

    static boolean threadLocalExample() throws InterruptedException {
        AtomicReference<String> seenByAna = new AtomicReference<>();
        AtomicReference<String> seenByLuis = new AtomicReference<>();
        Thread ana = Thread.ofPlatform().start(() -> handleRequest("ana", seenByAna));
        Thread luis = Thread.ofPlatform().start(() -> handleRequest("luis", seenByLuis));
        ana.join();
        luis.join();
        return seenByAna.get().equals("ana") && seenByLuis.get().equals("luis")
                && CURRENT_USER.get().equals("anónimo");    // main nunca puso nada: el valor inicial
    }

    private static void handleRequest(String user, AtomicReference<String> seen) {
        CURRENT_USER.set(user);
        try {
            sleepQuietly(20);                               // los dos hilos corren a la vez
            seen.set(CURRENT_USER.get());                   // cada uno ve el suyo
        } finally {
            CURRENT_USER.remove();                          // siempre: el hilo podría ser de un pool
        }
    }

    /*
        =================================================================
        CAPA 9) LO QUE CUESTA UN HILO, Y SLEEP Y PRIORIDADES
        =================================================================

        Un hilo de plataforma es un hilo del SO, y eso cuesta:
        - MEMORIA: cada uno reserva su pila (1 MB por defecto; se
          compromete solo lo que usa: StackAndHeapMemory, CAPAS 1 y 2) más
          estructuras del kernel;
        - TIEMPO: crearlo es una llamada al sistema. Medido, crear,
          arrancar y esperar 10,000 hilos vacíos: ~100 a 130 microsegundos
          por hilo. Con hilos virtuales (part3_tasks/VirtualThreads): ~1.5
          microsegundos;
        - LÍMITES: el SO limita cuántos hay (en Linux, ulimit -u y
          /proc/sys/kernel/threads-max). Pasarse da "OutOfMemoryError:
          unable to create native thread" (StackAndHeapMemory, CAPA 8);
        - CAMBIOS DE CONTEXTO: con miles de hilos activos, el SO gasta
          tiempo pasando de uno a otro (guardar y cargar registros, cachés
          del procesador que se enfrían).

        Por eso "un hilo nuevo por petición" no escala: se REUTILIZAN hilos
        en un pool (Executors), o se usan hilos virtuales, que son baratos.

        SLEEP no es preciso: Thread.sleep(ms) pide al SO "despiértame en
        al menos ms", y el SO despierta según su temporizador y su
        planificador. Medido en Windows, sleep(1) durmió 1.96 ms en promedio
        y hasta 3 ms. Nunca se usa sleep para sincronizar hilos ("espero
        100 ms y seguro ya terminó"): para eso están join, latches y colas.

        PRIORIDADES (setPriority 1 a 10): el SO las ignora casi siempre (en
        Linux, sin permisos especiales, la JVM no las aplica; el volcado
        muestra os_prio=0). No arreglan nada; no se usan.
    */

    /*
        =================================================================
        CAPA 10) ERRORES COMUNES
        =================================================================

        1) Llamar a run() en lugar de start() (CAPA 2).

        2) Hilos sin nombre: volcados y logs ilegibles (CAPA 2).

        3) Leer resultados de otro hilo sin join ni otra sincronización
           (CAPA 3).

        4) Usar sleep para "esperar a que otro hilo termine" (CAPAS 3 y 9).

        5) catch (InterruptedException e) { } vacío (CAPA 5).

        6) Trabajo importante en hilos daemon; hilos normales que nadie
           detiene y no dejan cerrar la JVM (CAPA 6).

        7) Hilos que mueren por una excepción sin que nadie se entere
           (CAPA 7).

        8) ThreadLocal sin remove() en hilos de un pool (CAPA 8).

        9) Un hilo nuevo por cada tarea en un servidor (CAPA 9).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Hilo = línea de ejecución dentro del proceso: comparte el heap,
          tiene su pila. Un Thread de plataforma es un hilo del SO (clone en
          Linux), planificado por el SO.
        - Thread.ofPlatform().name(...).start(tarea); start crea el hilo,
          run no.
        - join espera y hace visibles los resultados.
        - Estados: NEW, RUNNABLE, BLOCKED, WAITING, TIMED_WAITING,
          TERMINATED; se leen en el volcado de hilos.
        - Detener = interrumpir (cooperativo). InterruptedException: dejar
          salir o restaurar la bandera, nunca tragar.
        - Daemon no mantiene viva la JVM y se corta de golpe.
        - Excepciones no atrapadas matan solo ese hilo: usar un handler.
        - ThreadLocal: un valor por hilo; remove en finally.
        - Hilos de plataforma caros (~100 us, 1 MB de pila reservada):
          reutilizar con pools o usar hilos virtuales.
    */

    // -----------------------------------------------------------------
    // Utilidades internas de la guía.
    // -----------------------------------------------------------------

    /** Espera (hasta 5 s) a que el hilo llegue al estado; los estados cambian en otro hilo. */
    private static boolean reachesState(Thread thread, Thread.State expected) throws InterruptedException {
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (System.nanoTime() < deadline) {
            if (thread.getState() == expected) {
                return true;
            }
            Thread.sleep(1);
        }
        return false;
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();             // restaurar la bandera (CAPA 5)
        }
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();             // restaurar la bandera (CAPA 5)
        }
    }

    private static void check(boolean condition, String layer) {
        if (!condition) {
            throw new IllegalStateException("El ejemplo de la " + layer + " no hace lo que la guía explica");
        }
    }
}
