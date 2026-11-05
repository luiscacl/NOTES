package v13_concurrency.part3_tasks;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/*
    =====================================================================
    HILOS VIRTUALES: MILLONES DE TAREAS QUE ESPERAN, CÓDIGO SIMPLE
    =====================================================================

    Ruta de concurrency/: guía 9 de 9  (part3_tasks)
    Anterior:  part3_tasks/CompletableFutureUsage
    Siguiente: v14_architecture/part1_layers/LayeredArchitecture  (última de concurrency/)

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.
    - Medido en Windows 11, JDK 26, 14 núcleos lógicos.
    - Hilos virtuales: definitivos desde Java 21. ScopedValue: definitivo
      desde Java 25. Concurrencia estructurada (StructuredTaskScope): en
      Java 26 sigue en PREVIEW (CAPA 8): se explica, pero no se compila
      aquí.

    Importancia: MUY ALTA. Es el cambio más grande de la concurrencia en
    Java en años: los servidores pueden atender cada petición en su propio
    hilo, con código bloqueante normal, sin quedarse sin hilos.

    Índice:
        CAPA 1)  El problema: los hilos del SO son pocos y caros
        CAPA 2)  Qué es un hilo virtual
        CAPA 3)  Por dentro: montarse y desmontarse de un hilo portador
        CAPA 4)  Para qué SÍ y para qué NO
        CAPA 5)  No se reutilizan: se limita lo escaso
        CAPA 6)  Cuando un hilo virtual no se puede desmontar
        CAPA 7)  ScopedValue: datos de contexto sin ThreadLocal
        CAPA 8)  Concurrencia estructurada
        CAPA 9)  Cómo adoptarlos
        CAPA 10) Errores comunes
        RESUMEN FINAL
*/
public class VirtualThreads {

    public static void main(String[] args) throws Exception {
        check(basicsExample(), "CAPA 2");
        check(carriersExample(), "CAPA 3");
        check(scaleExample(), "CAPA 4");
        check(limitExample(), "CAPA 5");
        check(scopedValueExample(), "CAPA 7");
        check(structuredExample(), "CAPA 8");
        System.out.println("VirtualThreads: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) EL PROBLEMA: LOS HILOS DEL SO SON POCOS Y CAROS
        =================================================================

        El modelo más simple para un servidor es "un hilo por petición":
        cada petición corre de principio a fin en su hilo, con código
        normal que BLOQUEA mientras espera a la base de datos o a otro
        servicio. Fácil de escribir, de leer y de depurar.

        El problema: un hilo de plataforma es un hilo del SO
        (part1_threads/ThreadBasics, CAPAS 1 y 9): ~100 microsegundos para
        crearlo, una pila de 1 MB reservada, y el planificador del SO
        sufriendo con miles. Un servidor típico tiene un pool de unos 200
        hilos. Si cada petición pasa 95% del tiempo ESPERANDO la red, esos
        200 hilos están casi siempre dormidos... y la petición 201 hace
        fila, con la CPU ociosa.

        Medido, 2,000 tareas que esperan 1 segundo:

            pool de 200 hilos de plataforma           ~10,000 ms
            hilos virtuales (100,000 tareas)           ~1,500 ms

        La solución "vieja" era la programación asíncrona: no bloquear
        nunca, y encadenar callbacks (part3_tasks/CompletableFutureUsage).
        Funciona, pero el código se vuelve difícil de leer, los stack traces
        no dicen nada y los errores se pierden. Los hilos virtuales hacen
        BARATO bloquear, y el código vuelve a ser simple.
    */

    /*
        =================================================================
        CAPA 2) QUÉ ES UN HILO VIRTUAL
        =================================================================

        Un hilo virtual es un java.lang.Thread completo (mismo API: join,
        interrupt, ThreadLocal, stack traces), pero NO es un hilo del SO.
        Lo administra la JVM:

            Thread.ofVirtual().name("pedido-", 0).start(tarea)
            Thread.startVirtualThread(tarea)
            Executors.newVirtualThreadPerTaskExecutor()      un hilo virtual
                                                             NUEVO por tarea

        Sus diferencias:
        - cuesta ~1.5 microsegundos crearlo (contra ~100), y unos cientos de
          bytes al empezar: su pila vive en el HEAP y crece y se encoge según
          la necesita;
        - siempre es daemon (no mantiene viva la JVM) y sin nombre por
          defecto;
        - no tiene prioridad (setPriority no hace nada);
        - se pueden tener MILLONES.

        La forma normal de usarlos es el executor, en try-with-resources
        (part3_tasks/ExecutorsUsage, CAPA 7): cada submit crea un hilo
        virtual, y el bloque no termina hasta que terminan todos.
    */
    static boolean basicsExample() throws InterruptedException {
        Thread virtual = Thread.ofVirtual().name("pedido-", 1).unstarted(() -> { });
        Thread unnamed = Thread.ofVirtual().unstarted(() -> { });
        AtomicBoolean ranVirtual = new AtomicBoolean();
        Thread started = Thread.startVirtualThread(() -> ranVirtual.set(Thread.currentThread().isVirtual()));
        started.join();
        return virtual.isVirtual() && virtual.getName().equals("pedido-1")
                && unnamed.getName().isEmpty() && unnamed.isDaemon()
                && ranVirtual.get();
    }

    /*
        =================================================================
        CAPA 3) POR DENTRO: MONTARSE Y DESMONTARSE DE UN HILO PORTADOR
        =================================================================

        Para ejecutar código, un hilo virtual se MONTA en un hilo de
        plataforma, su PORTADOR (carrier). Los portadores son un
        ForkJoinPool con tantos hilos como núcleos (14 aquí).

        La clave está en las esperas. Cuando un hilo virtual va a
        bloquearse (sleep, leer un socket, esperar un lock, una cola, un
        future):
        1) la JVM guarda su pila (sus marcos) en el heap;
        2) lo DESMONTA: el portador queda libre y toma otro hilo virtual;
        3) cuando la espera termina (llegaron los datos), el hilo virtual
           vuelve a la cola, y algún portador lo monta y sigue donde iba.

        ¿Y cómo sabe la JVM que llegaron los datos sin un hilo esperando?
        Para la red usa los mecanismos de E/S sin bloqueo del SO: en Linux,
        epoll ("avísame cuando alguno de estos miles de sockets tenga
        datos"); en Windows, wepoll (construido sobre IOCP). Unos pocos
        hilos "poller" de la JVM (uno para lecturas y otro para escrituras)
        vigilan todos los sockets y despiertan al hilo virtual
        correspondiente.

        Es lo mismo que hace un sistema operativo con sus procesos, o
        JavaScript con su event loop, pero con hilos de Java: tu código
        sigue siendo bloqueante y secuencial, y la JVM hace el trabajo
        asíncrono por debajo.

        Medido: 100,000 hilos virtuales durmiendo corrieron sobre solo 14
        portadores. El ejemplo lo comprueba con 2,000. (El toString de un
        hilo virtual muestra su portador después de '@'; es un detalle de
        la implementación, útil para ver esto, no para programar con él.)
    */
    static boolean carriersExample() {
        Set<String> carriers = ConcurrentHashMap.newKeySet();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 2_000; i++) {
                executor.submit(() -> {
                    String description = Thread.currentThread().toString();   // VirtualThread[#..]/runnable@ForkJoinPool-1-worker-3
                    carriers.add(description.substring(description.indexOf('@') + 1));
                    Thread.sleep(50);                       // se desmonta: el portador queda libre
                    return null;
                });
            }
        }
        return !carriers.isEmpty() && carriers.size() <= Runtime.getRuntime().availableProcessors();
    }

    /*
        =================================================================
        CAPA 4) PARA QUÉ SÍ Y PARA QUÉ NO
        =================================================================

        SÍ: tareas que pasan la mayor parte del tiempo ESPERANDO (llamadas
        HTTP, consultas a la base de datos, archivos, colas). Es el caso de
        casi todo backend. La mejora es en CANTIDAD de tareas a la vez
        (throughput), no en la velocidad de cada una.

        NO: tareas de CPU (cálculos, compresión, ordenar). Un hilo virtual
        que calcula ocupa su portador todo el tiempo, igual que uno de
        plataforma: con 14 núcleos, 14 cálculos a la vez, como siempre.
        Para eso, un pool del tamaño de los núcleos o un stream paralelo
        (part3_tasks/ExecutorsUsage, CAPA 6).

        Un hilo virtual no es más rápido. Es más BARATO, así que puedes
        tener uno por tarea en vez de compartir pocos.

        El ejemplo: 10,000 tareas que esperan 200 ms terminan en ~200 ms
        (más el arranque), porque esperan todas a la vez.
    */
    static boolean scaleExample() {
        AtomicInteger done = new AtomicInteger();
        long start = System.nanoTime();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 10_000; i++) {
                executor.submit(() -> {
                    Thread.sleep(200);                      // "la consulta a la base de datos"
                    done.incrementAndGet();
                    return null;
                });
            }
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        return done.get() == 10_000 && elapsedMs < 3_000;   // en serie serían 2,000 segundos
    }

    /*
        =================================================================
        CAPA 5) NO SE REUTILIZAN: SE LIMITA LO ESCASO
        =================================================================

        Con hilos de plataforma, el tamaño del pool hacía DOS trabajos:
        reutilizar hilos caros, y de paso LIMITAR cuántas cosas se hacían a
        la vez (si la base de datos acepta 20 conexiones, un pool de 20).

        Con hilos virtuales:
        - NO se hace pool de hilos virtuales. Son baratos: uno nuevo por
          tarea, y se tira al terminar. Un "pool de 100 hilos virtuales" es
          un error de concepto;
        - el LÍMITE se pone donde está el recurso escaso, explícitamente:
          un Semaphore (part2_coordination/Synchronization, CAPA 9), o el
          pool de conexiones de la base de datos.

        Sin límite, 100,000 hilos virtuales lanzando 100,000 consultas a la
        vez tumbarían a la base de datos. Los hilos dejaron de ser el
        cuello de botella; ahora hay que proteger lo que sí lo es.

        El ejemplo: 1,000 tareas virtuales, un Semaphore(10): nunca más de
        10 "consultas" a la vez.
    */
    static boolean limitExample() {
        Semaphore databaseConnections = new Semaphore(10);
        AtomicInteger inside = new AtomicInteger();
        AtomicInteger maxInside = new AtomicInteger();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 1_000; i++) {
                executor.submit(() -> {
                    databaseConnections.acquire();          // espera barata: el hilo virtual se desmonta
                    try {
                        maxInside.accumulateAndGet(inside.incrementAndGet(), Math::max);
                        Thread.sleep(5);
                        inside.decrementAndGet();
                    } finally {
                        databaseConnections.release();
                    }
                    return null;
                });
            }
        }
        return maxInside.get() == 10;
    }

    /*
        =================================================================
        CAPA 6) CUANDO UN HILO VIRTUAL NO SE PUEDE DESMONTAR
        =================================================================

        A veces un hilo virtual NO puede soltar su portador mientras
        espera: queda "FIJADO" (pinned). Si muchos quedan fijados a la vez,
        los 14 portadores se ocupan y todo se detiene, como un pool lleno.

        Causas:
        - código NATIVO en la pila (métodos native, llamadas a C): la JVM no
          puede mover esos marcos al heap;
        - synchronized: lo fijaba HASTA Java 23. Desde Java 24 ya no
          (part2_coordination/Synchronization, CAPA 4). Por eso hay mucho
          material de internet que dice "cambia synchronized por
          ReentrantLock para hilos virtuales": ya no aplica.

        Cómo detectarlo: JFR (v12_jvm/part3_performance/Performance, CAPA
        6) registra el evento jdk.VirtualThreadPinned con el stack trace
        de quien se quedó fijado.

        Otra fuente de problemas: código que guarda en un ThreadLocal
        objetos grandes "para reutilizarlos por hilo" (un buffer, un
        formateador). Con 200 hilos de plataforma eran 200 copias; con un
        millón de hilos virtuales son un millón (part1_threads/ThreadBasics,
        CAPA 8). Ese truco ya no sirve.
    */

    /*
        =================================================================
        CAPA 7) SCOPEDVALUE: DATOS DE CONTEXTO SIN THREADLOCAL
        =================================================================

        El uso legítimo de ThreadLocal era pasar CONTEXTO (el usuario de la
        petición, un id para los logs) sin agregarlo como parámetro a cada
        método. Sus problemas: se puede cambiar desde cualquier lado,
        alguien tiene que acordarse de remove(), y cada hilo hijo copia el
        mapa entero si se usa InheritableThreadLocal.

        ScopedValue (definitivo en Java 25) es la alternativa:

            static final ScopedValue<String> USER = ScopedValue.newInstance();

            ScopedValue.where(USER, "ana").run(() -> {
                ... cualquier método llamado aquí lee USER.get() == "ana"
            });
            // fuera del bloque, USER no tiene valor

        - El valor existe SOLO mientras corre el bloque: no hay remove que
          olvidar, ni valores que se quedan en un hilo reutilizado.
        - Es INMUTABLE dentro del bloque: nadie más abajo lo cambia. Se
          puede volver a "atar" con otro valor en un bloque más interno,
          que solo vale ahí.
        - Leerlo es muy rápido, y lo HEREDAN las subtareas de la
          concurrencia estructurada (CAPA 8). Un hilo arrancado por otros
          medios (un executor normal) NO lo ve: eso lo muestra el ejemplo.
    */
    static final ScopedValue<String> CURRENT_USER = ScopedValue.newInstance();

    static String auditLine(String action) {
        return CURRENT_USER.get() + " " + action;           // lo lee sin recibirlo como parámetro
    }

    static boolean scopedValueExample() throws Exception {
        AtomicReference<String> inner = new AtomicReference<>();
        AtomicBoolean seenByOtherThread = new AtomicBoolean(true);
        String line = ScopedValue.where(CURRENT_USER, "ana").call(() -> {
            ScopedValue.where(CURRENT_USER, "sistema").run(() -> inner.set(auditLine("respaldo")));   // solo aquí
            Thread other = Thread.ofVirtual().start(() -> seenByOtherThread.set(CURRENT_USER.isBound()));
            other.join();
            return auditLine("pagó el pedido 7");           // otra vez "ana"
        });
        return line.equals("ana pagó el pedido 7")
                && "sistema respaldo".equals(inner.get())
                && !seenByOtherThread.get()                 // un hilo normal no lo hereda
                && !CURRENT_USER.isBound();                 // fuera del bloque, no existe
    }

    /*
        =================================================================
        CAPA 8) CONCURRENCIA ESTRUCTURADA
        =================================================================

        Con un executor, una tarea lanzada puede seguir viva aunque quien
        la lanzó ya terminó o falló: tareas "huérfanas" que siguen gastando
        recursos, y errores que nadie ve. La CONCURRENCIA ESTRUCTURADA
        aplica a las tareas la misma regla que a los bloques de código:
        las subtareas que nacen en un bloque TERMINAN dentro de ese bloque.

        En Java 26 la API (StructuredTaskScope) sigue en PREVIEW: compilarla
        sin la opción da, medido:

            error: StructuredTaskScope is a preview API and is disabled by default.
              (use --enable-preview to enable preview APIs)

        Así se ve (con --enable-preview, y puede cambiar antes de ser
        definitiva):

            try (var scope = StructuredTaskScope.open()) {
                Subtask<User> user = scope.fork(() -> fetchUser(id));
                Subtask<Cart> cart = scope.fork(() -> fetchCart(id));
                scope.join();                     // espera a las dos; si una
                                                  // falla, CANCELA a la otra y
                                                  // lanza el error aquí
                return new Page(user.get(), cart.get());
            }

        Lo que agrega sobre un executor: si una subtarea falla, las demás
        se cancelan solas; las subtareas heredan los ScopedValue (CAPA 7);
        y en un volcado de hilos se ven como un árbol (quién lanzó a quién).

        Lo que YA se puede hacer sin preview, y da la garantía principal
        (ninguna subtarea sobrevive al bloque), es un executor de hilos
        virtuales en try-with-resources: close() espera a todas. El ejemplo
        lo hace así, y al fallar una consulta cancela la otra a mano (lo que
        StructuredTaskScope haría solo).
    */
    record Page(String user, String cart) {
    }

    static boolean structuredExample() throws Exception {
        AtomicBoolean slowTaskInterrupted = new AtomicBoolean();
        String error;
        try (ExecutorService scope = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<String> user = scope.submit(() -> {
                Thread.sleep(50);
                throw new IllegalStateException("servicio de usuarios caído");
            });
            Future<String> cart = scope.submit(() -> {
                try {
                    Thread.sleep(5_000);                    // lenta: no tiene caso esperarla
                    return "carrito";
                } catch (InterruptedException e) {
                    slowTaskInterrupted.set(true);          // cancelada porque la otra falló
                    throw e;
                }
            });
            try {
                new Page(user.get(), cart.get());
                error = "";
            } catch (ExecutionException e) {
                cart.cancel(true);                          // lo que StructuredTaskScope haría solo
                error = e.getCause().getMessage();
            }
        }                                                   // close(): ninguna sigue viva después de aquí
        return error.equals("servicio de usuarios caído") && slowTaskInterrupted.get();
    }

    /*
        =================================================================
        CAPA 9) CÓMO ADOPTARLOS
        =================================================================

        - En un servidor con Spring Boot (3.2 o más): una línea de
          configuración, spring.threads.virtual.enabled=true, y cada
          petición corre en un hilo virtual (v15_spring/).
        - En tu código: Executors.newVirtualThreadPerTaskExecutor() donde
          antes había un pool para tareas que esperan.
        - Revisar:
            * qué recurso escaso protegía el tamaño del pool, y ponerle un
              límite explícito (CAPA 5);
            * ThreadLocal usados como caché por hilo (CAPA 6);
            * librerías con código nativo en el camino de las esperas
              (JFR, CAPA 6).
        - El código de negocio NO cambia: sigue siendo bloqueante y
          secuencial. Ese es el punto.

        Y medir antes y después (v12_jvm/part3_performance/Performance): la
        mejora aparece cuando el límite eran los hilos. Si el límite es la
        base de datos, los hilos virtuales solo la saturan más rápido.
    */

    /*
        =================================================================
        CAPA 10) ERRORES COMUNES
        =================================================================

        1) Creer que hacen más rápida cada tarea: dan más tareas a la vez
           (CAPAS 1 y 4).

        2) Usarlos para trabajo de CPU (CAPA 4).

        3) Hacer un pool de hilos virtuales (CAPA 5).

        4) Quitar el pool sin poner un límite al recurso que protegía
           (CAPA 5).

        5) ThreadLocal como caché de objetos caros por hilo (CAPA 6).

        6) Seguir el consejo viejo de quitar todo synchronized: desde Java
           24 ya no fija (CAPA 6).

        7) Pasar contexto con ThreadLocal sin remove, cuando ScopedValue lo
           resuelve (CAPA 7).

        8) Lanzar subtareas que pueden sobrevivir a quien las lanzó
           (CAPA 8).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Hilo virtual: un Thread que administra la JVM, con la pila en el
          heap; cuesta ~1.5 us y se pueden tener millones.
        - Se monta en un portador (uno por núcleo) y se desmonta al
          esperar; la red se vigila con epoll (Linux) o wepoll/IOCP
          (Windows).
        - Para tareas que esperan, no para CPU; dan más tareas a la vez, no
          tareas más rápidas.
        - No se hace pool: se limita el recurso escaso (Semaphore, pool de
          conexiones).
        - Se fijan al portador con código nativo (synchronized ya no, desde
          Java 24); JFR lo detecta.
        - ScopedValue: contexto inmutable que solo existe dentro de un
          bloque.
        - Concurrencia estructurada (preview en Java 26): las subtareas
          terminan dentro de su bloque; hoy, executor virtual en
          try-with-resources.
    */

    // -----------------------------------------------------------------
    // Utilidades internas de la guía.
    // -----------------------------------------------------------------

    private static void check(boolean condition, String layer) {
        if (!condition) {
            throw new IllegalStateException("El ejemplo de la " + layer + " no hace lo que la guía explica");
        }
    }
}
