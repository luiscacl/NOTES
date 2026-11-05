package v13_concurrency.part2_coordination;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/*
    =====================================================================
    SINCRONIZACIÓN: BLOQUEOS Y SEÑALES ENTRE HILOS
    =====================================================================

    Ruta de concurrency/: guía 4 de 9  (part2_coordination)
    Anterior:  part1_threads/MemoryModel
    Siguiente: part2_coordination/Atomics

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.
    - Costos medidos en Windows 11, JDK 26, 14 núcleos lógicos.

    Lo que ya se vio: los problemas que esto resuelve (part1_threads/
    ConcurrencyProblems) y las reglas de visibilidad (part1_threads/
    MemoryModel). volatile ya se explicó completo en MemoryModel, CAPA 8.

    Importancia: MUY ALTA. synchronized y ReentrantLock están en todo
    código concurrente; Semaphore y CountDownLatch, en muchos.

    Índice:
        CAPA 1)  synchronized: exclusión mutua y visibilidad
        CAPA 2)  Sobre qué objeto bloquear
        CAPA 3)  Proteger un invariante, no una variable
        CAPA 4)  Cómo funciona por dentro, y lo que cuesta
        CAPA 5)  ReentrantLock: lo que synchronized no puede
        CAPA 6)  Evitar el deadlock: un orden global
        CAPA 7)  ReadWriteLock: muchos lectores o un escritor
        CAPA 8)  Esperar una condición: wait/notify y Condition
        CAPA 9)  Sincronizadores: CountDownLatch, Semaphore y compañía
        CAPA 10) Qué herramienta usar
        CAPA 11) Errores comunes
        RESUMEN FINAL
*/
public class Synchronization {

    public static void main(String[] args) throws InterruptedException {
        check(synchronizedCounterExample(), "CAPA 1");
        check(privateLockExample(), "CAPA 2");
        check(invariantExample(), "CAPA 3");
        check(tryLockExample(), "CAPA 5");
        check(lockOrderingExample(), "CAPA 6");
        check(readWriteLockExample(), "CAPA 7");
        check(conditionExample(), "CAPA 8");
        check(semaphoreExample(), "CAPA 9");
        System.out.println("Synchronization: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) SYNCHRONIZED: EXCLUSIÓN MUTUA Y VISIBILIDAD
        =================================================================

        Todo objeto de Java tiene un BLOQUEO (también llamado "monitor").
        synchronized lo toma al entrar y lo suelta al salir, aunque se
        salga con una excepción:

            synchronized (lock) { ... }          un bloque, sobre un objeto
            synchronized void m() { ... }        todo el método, sobre this
            static synchronized void m() { ... } sobre el objeto Class

        Da DOS cosas:
        1) EXCLUSIÓN MUTUA: un solo hilo a la vez dentro de los bloques
           que usan EL MISMO objeto. Los demás esperan en estado BLOCKED
           (part1_threads/ThreadBasics, CAPA 4). Así, leer-sumar-escribir
           se vuelve un solo paso: no se pierden actualizaciones.
        2) VISIBILIDAD: lo que un hilo escribió antes de soltar el bloqueo
           es visible para el siguiente que lo toma (happens-before;
           MemoryModel, CAPA 3). Por eso también hay que sincronizar las
           LECTURAS: un get sin synchronized puede ver valores viejos.

        Es REENTRANTE: un hilo que ya tiene el bloqueo puede volver a
        entrar (un método synchronized que llama a otro del mismo objeto)
        sin bloquearse a sí mismo.
    */
    static final class Counter {
        private long value;

        synchronized void increment() {
            value++;                                        // un solo hilo a la vez
        }

        synchronized void incrementTwice() {
            increment();                                    // reentrante: ya tiene el bloqueo, entra
            increment();
        }

        synchronized long value() {                         // también la lectura: visibilidad
            return value;
        }
    }

    static boolean synchronizedCounterExample() throws InterruptedException {
        Counter counter = new Counter();
        runInThreads(4, () -> {
            for (int i = 0; i < 250_000; i++) {
                counter.incrementTwice();
            }
        });
        return counter.value() == 2_000_000;                // exacto, a diferencia de ConcurrencyProblems, CAPA 2
    }

    /*
        =================================================================
        CAPA 2) SOBRE QUÉ OBJETO BLOQUEAR
        =================================================================

        Un método synchronized bloquea sobre this. El problema: this es
        PÚBLICO. Cualquier código que tenga tu objeto puede hacer
        synchronized (tuObjeto) { ... } y, sin querer, detener todos tus
        métodos. El ejemplo lo demuestra: código "de afuera" toma el
        bloqueo de un objeto y su método queda BLOCKED.

        La práctica profesional en clases que otros usan: un objeto
        privado, solo para bloquear:

            private final Object lock = new Object();
            void m() { synchronized (lock) { ... } }

        Nadie más lo puede tomar. Y que sea FINAL: si el campo cambiara de
        objeto, dos hilos podrían bloquear sobre objetos distintos.

        Nunca bloquees sobre:
        - Strings literales o Integer pequeños: están compartidos en toda
          la JVM (el pool de Strings, la caché de Integer); otro código sin
          relación podría estar usando "el mismo" objeto;
        - objetos que cambian (un campo que se reasigna);
        - records u objetos "valor" pensados para compararse por contenido.

        Y el bloque, lo más CORTO posible: solo lo que toca el estado
        compartido. Nunca una llamada por red, a disco, o a código ajeno
        (un listener) mientras se tiene el bloqueo: todos los demás hilos
        esperan esa llamada, y si el código ajeno toma otro bloqueo, es el
        camino directo a un deadlock.
    */
    static final class PublicLockInventory {
        private int stock = 10;

        synchronized int reserve() {                        // bloquea sobre this: cualquiera puede interferir
            return --stock;
        }
    }

    static final class PrivateLockInventory {
        private final Object lock = new Object();           // solo esta clase lo conoce
        private int stock = 10;

        int reserve() {
            synchronized (lock) {
                return --stock;
            }
        }
    }

    static boolean privateLockExample() throws InterruptedException {
        PublicLockInventory exposed = new PublicLockInventory();
        PrivateLockInventory hidden = new PrivateLockInventory();
        boolean exposedBlocked;
        boolean hiddenFree;
        synchronized (exposed) {                            // código "de afuera" toma el bloqueo del objeto
            synchronized (hidden) {                         // ...y lo intenta con el otro también
                Thread first = Thread.ofPlatform().start(exposed::reserve);
                Thread second = Thread.ofPlatform().start(hidden::reserve);
                exposedBlocked = reachesState(first, Thread.State.BLOCKED);
                second.join(2_000);
                hiddenFree = !second.isAlive();             // no le afectó: usa su propio bloqueo
            }
        }
        return exposedBlocked && hiddenFree;
    }

    /*
        =================================================================
        CAPA 3) PROTEGER UN INVARIANTE, NO UNA VARIABLE
        =================================================================

        Un INVARIANTE es una regla que el objeto cumple siempre: "min <=
        max", "el total es la suma de las líneas", "el dinero entre dos
        cuentas no cambia en una transferencia".

        Si el invariante involucra VARIAS variables, todas se cambian bajo
        el MISMO bloqueo, en el mismo bloque. Hacer volatile o atómica cada
        una por separado NO basta: otro hilo puede ver min ya cambiado y max
        todavía viejo.

        El ejemplo: un rango de precios que siempre cumple min <= max. Dos
        hilos lo cambian a la vez, mientras un tercero lee los dos valores
        juntos, y nunca ve min > max.
    */
    static final class PriceRange {
        private final Object lock = new Object();
        private int min = 0;
        private int max = 100;

        void set(int newMin, int newMax) {
            if (newMin > newMax) {
                throw new IllegalArgumentException("min > max");
            }
            synchronized (lock) {                           // las dos juntas: nadie ve una sin la otra
                min = newMin;
                max = newMax;
            }
        }

        int[] snapshot() {
            synchronized (lock) {                           // leer las dos juntas también
                return new int[] {min, max};
            }
        }
    }

    static boolean invariantExample() throws InterruptedException {
        PriceRange range = new PriceRange();
        AtomicInteger violations = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(2);
        Thread low = Thread.ofPlatform().start(() -> {
            for (int i = 0; i < 100_000; i++) {
                range.set(0, 10);
            }
            done.countDown();
        });
        Thread high = Thread.ofPlatform().start(() -> {
            for (int i = 0; i < 100_000; i++) {
                range.set(50, 100);
            }
            done.countDown();
        });
        while (done.getCount() > 0) {
            int[] seen = range.snapshot();
            if (seen[0] > seen[1]) {
                violations.incrementAndGet();               // nunca: min y max se cambian juntos
            }
        }
        low.join();
        high.join();
        return violations.get() == 0;
    }

    /*
        =================================================================
        CAPA 4) CÓMO FUNCIONA POR DENTRO, Y LO QUE CUESTA
        =================================================================

        El estado del bloqueo de un objeto vive en su CABECERA (la "marca"
        de 8 bytes; v12_jvm/part1_memory/StackAndHeapMemory, CAPA 6):

        - SIN COMPETENCIA (un solo hilo lo usa): tomarlo es una operación
          atómica del procesador sobre la cabecera (un CAS; part2_coordination/
          Atomics). Unos nanosegundos, sin el sistema operativo.
        - CON COMPETENCIA: el hilo que no consigue el bloqueo gira un poco
          (por si se libera pronto) y si no, se DUERME. Dormir y despertar
          es trabajo del sistema operativo: en Linux, la llamada al sistema
          futex ("fast userspace mutex"); en Windows, un objeto de espera
          del kernel. Eso cuesta microsegundos: cambiar de contexto, el
          planificador, las cachés que se enfrían.

        Medido, incrementar un contador bajo bloqueo (ns por operación):

            sin bloqueo, un hilo                  ~1
            synchronized, un hilo                 ~13 a 17
            synchronized, 4 hilos compitiendo     ~97
            ReentrantLock, un hilo                ~14 a 16
            ReentrantLock, 4 hilos compitiendo    ~19 a 22
            ReentrantLock JUSTO, 4 hilos          ~3,100

        Lo que enseña: un bloqueo SIN competencia es barato; lo caro es la
        COMPETENCIA. Y el bloqueo justo (por orden de llegada, CAPA 5) es
        150 veces más lento, porque cada turno obliga a dormir a uno y
        despertar a otro. Los no justos dejan que el hilo que ya está
        corriendo lo vuelva a tomar, sin pasar por el SO.

        Por eso: bloques cortos, menos estado compartido (part1_threads/
        ConcurrencyProblems, CAPA 9), y medir antes de cambiar synchronized
        por otra cosa "porque es más rápida".

        Nota: desde Java 24, synchronized ya no "fija" un hilo virtual a su
        hilo del SO (part3_tasks/VirtualThreads). Antes era una razón para
        preferir ReentrantLock; hoy ya no.
    */

    /*
        =================================================================
        CAPA 5) REENTRANTLOCK: LO QUE SYNCHRONIZED NO PUEDE
        =================================================================

        java.util.concurrent.locks.ReentrantLock hace lo mismo que
        synchronized (exclusión, visibilidad, reentrante) y además:

            tryLock()                 intentar SIN esperar: true o false
            tryLock(100, MILLISECONDS) esperar como máximo un tiempo
            lockInterruptibly()       esperar, pero salir si interrumpen
                                      (un hilo en synchronized no se puede
                                      interrumpir mientras espera)
            new ReentrantLock(true)   justo: por orden de llegada (CAPA 4)
            newCondition()            varias condiciones de espera (CAPA 8)

        El precio: soltarlo es TU responsabilidad. Siempre así:

            lock.lock();
            try {
                ...
            } finally {
                lock.unlock();      // si se olvida, nadie vuelve a entrar
            }

        Regla: synchronized por defecto (más simple, imposible olvidar
        soltarlo). ReentrantLock cuando necesitas tryLock, tiempo límite,
        interrupción o justicia.
    */
    static boolean tryLockExample() throws InterruptedException {
        ReentrantLock lock = new ReentrantLock();
        CountDownLatch holding = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread holder = Thread.ofPlatform().start(() -> {
            lock.lock();
            try {
                holding.countDown();
                awaitQuietly(release);                      // lo tiene mientras main prueba
            } finally {
                lock.unlock();
            }
        });
        holding.await();

        boolean immediate = lock.tryLock();                 // ocupado: false, sin esperar
        long start = System.nanoTime();
        boolean withTimeout = lock.tryLock(100, TimeUnit.MILLISECONDS);
        long waitedMs = (System.nanoTime() - start) / 1_000_000;

        release.countDown();
        holder.join();
        boolean afterRelease = lock.tryLock();
        if (afterRelease) {
            lock.unlock();
        }
        return !immediate && !withTimeout && waitedMs >= 90 && afterRelease;
    }

    /*
        =================================================================
        CAPA 6) EVITAR EL DEADLOCK: UN ORDEN GLOBAL
        =================================================================

        El deadlock de part1_threads/ConcurrencyProblems, CAPA 5: dos
        transferencias en sentidos contrarios, cada una con una cuenta
        bloqueada esperando la otra.

        La solución estándar: todos toman los bloqueos en el MISMO orden
        global, sin importar el sentido de la transferencia. Por ejemplo,
        primero la cuenta de id menor. Así no puede haber espera circular.

        El ejemplo hace 20,000 transferencias A->B y B->A a la vez, desde
        dos hilos: sin el orden se trabaría en segundos; con él termina, y
        el dinero total se conserva (el invariante de la CAPA 3).
    */
    static final class Account {
        final long id;
        private final ReentrantLock lock = new ReentrantLock();
        private long balance;

        Account(long id, long balance) {
            this.id = id;
            this.balance = balance;
        }
    }

    static void transfer(Account from, Account to, long amount) {
        Account first = from.id < to.id ? from : to;        // el orden NO depende del sentido
        Account second = first == from ? to : from;
        first.lock.lock();
        try {
            second.lock.lock();
            try {
                from.balance -= amount;
                to.balance += amount;
            } finally {
                second.lock.unlock();
            }
        } finally {
            first.lock.unlock();
        }
    }

    static boolean lockOrderingExample() throws InterruptedException {
        Account a = new Account(1, 1_000);
        Account b = new Account(2, 1_000);
        Thread aToB = Thread.ofPlatform().start(() -> {
            for (int i = 0; i < 10_000; i++) {
                transfer(a, b, 1);
            }
        });
        Thread bToA = Thread.ofPlatform().start(() -> {
            for (int i = 0; i < 10_000; i++) {
                transfer(b, a, 1);
            }
        });
        aToB.join(10_000);
        bToA.join(10_000);
        boolean finished = !aToB.isAlive() && !bToA.isAlive();
        a.lock.lock();
        b.lock.lock();
        try {
            return finished && a.balance + b.balance == 2_000;   // el total se conserva
        } finally {
            b.lock.unlock();
            a.lock.unlock();
        }
    }

    /*
        =================================================================
        CAPA 7) READWRITELOCK: MUCHOS LECTORES O UN ESCRITOR
        =================================================================

        Leer no cambia nada: varios hilos pueden leer a la vez sin
        problema. Lo que hay que impedir es leer MIENTRAS alguien escribe,
        y escribir a la vez.

        ReentrantReadWriteLock tiene dos bloqueos enlazados:
        - readLock(): lo pueden tener MUCHOS hilos a la vez;
        - writeLock(): EXCLUSIVO: nadie más, ni lectores ni escritores.

        Cuándo conviene: lecturas MUCHO más frecuentes que escrituras, y
        que tarden (recorrer una tabla de configuración, un catálogo en
        memoria). Para lecturas de nanosegundos, su contabilidad interna
        cuesta más de lo que ahorra: un synchronized simple, o mejor, un
        objeto inmutable que se REEMPLAZA completo en un volatile o un
        AtomicReference (copy-on-write; part2_coordination/
        ConcurrentCollections).

        (StampedLock, Java 8, agrega lecturas "optimistas" sin bloquear,
        que luego se validan. Es más rápida y más difícil de usar bien; solo
        para casos medidos.)
    */
    static boolean readWriteLockExample() throws InterruptedException {
        ReadWriteLock catalogLock = new ReentrantReadWriteLock();
        CountDownLatch readerHolding = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread reader = Thread.ofPlatform().start(() -> {
            catalogLock.readLock().lock();
            try {
                readerHolding.countDown();
                awaitQuietly(release);
            } finally {
                catalogLock.readLock().unlock();
            }
        });
        readerHolding.await();

        boolean[] results = new boolean[2];
        Thread others = Thread.ofPlatform().start(() -> {
            results[0] = catalogLock.readLock().tryLock();  // otro lector: sí, a la vez
            if (results[0]) {
                catalogLock.readLock().unlock();
            }
            results[1] = catalogLock.writeLock().tryLock(); // un escritor: no, hay un lector
            if (results[1]) {
                catalogLock.writeLock().unlock();
            }
        });
        others.join();
        release.countDown();
        reader.join();
        return results[0] && !results[1];
    }

    /*
        =================================================================
        CAPA 8) ESPERAR UNA CONDICIÓN: WAIT/NOTIFY Y CONDITION
        =================================================================

        A veces un hilo debe ESPERAR a que algo pase ("hasta que haya un
        pedido en la cola", "hasta que haya lugar"). Girar preguntando gasta
        CPU; dormir con sleep es adivinar.

        El mecanismo de bajo nivel, en todo objeto: wait() suelta el
        bloqueo y duerme hasta que otro hilo llame a notify() o
        notifyAll() sobre el MISMO objeto. Con ReentrantLock, lo mismo es
        Condition: await() y signal()/signalAll(), con la ventaja de tener
        VARIAS condiciones por bloqueo ("no vacía" y "no llena").

        La forma correcta, siempre:

            lock.lock();
            try {
                while (!condición) {        // WHILE, nunca if
                    condition.await();
                }
                ... actuar ...
            } finally { lock.unlock(); }

        ¿Por qué while? Al despertar, la condición puede ya no cumplirse:
        otro hilo llegó primero, o fue un despertar "espurio" (el sistema
        operativo puede despertar un hilo sin que nadie haya avisado; la
        especificación lo permite).

        El ejemplo es una "puerta" que se abre una vez: tres hilos esperan
        y un cuarto la abre con signalAll.

        En la práctica, casi nunca escribirás esto: CountDownLatch (CAPA 9)
        hace exactamente esta puerta, y las colas bloqueantes
        (part2_coordination/ConcurrentCollections) resuelven el "esperar a
        que haya algo". wait/notify aparece en código viejo; Condition, en
        el código de las propias librerías.
    */
    static final class Gate {
        private final ReentrantLock lock = new ReentrantLock();
        private final Condition opened = lock.newCondition();
        private boolean open;

        void await() throws InterruptedException {
            lock.lock();
            try {
                while (!open) {                             // while: revisar otra vez al despertar
                    opened.await();                         // suelta el bloqueo y duerme
                }
            } finally {
                lock.unlock();
            }
        }

        void open() {
            lock.lock();
            try {
                open = true;
                opened.signalAll();                         // despertar a TODOS los que esperan
            } finally {
                lock.unlock();
            }
        }
    }

    static boolean conditionExample() throws InterruptedException {
        Gate gate = new Gate();
        AtomicInteger passed = new AtomicInteger();
        List<Thread> waiters = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            waiters.add(Thread.ofPlatform().start(() -> {
                try {
                    gate.await();
                    passed.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }
        boolean allWaiting = true;
        for (Thread waiter : waiters) {
            allWaiting &= reachesState(waiter, Thread.State.WAITING);
        }
        boolean nobodyPassedYet = passed.get() == 0;
        gate.open();
        for (Thread waiter : waiters) {
            waiter.join();
        }
        return allWaiting && nobodyPassedYet && passed.get() == 3;
    }

    /*
        =================================================================
        CAPA 9) SINCRONIZADORES: COUNTDOWNLATCH, SEMAPHORE Y COMPAÑÍA
        =================================================================

        java.util.concurrent trae coordinadores ya hechos y probados. Usa
        estos antes de escribir wait/notify:

            CountDownLatch(n)   una puerta que se abre cuando countDown()
                                se llamó n veces. De un solo uso. "Esperar
                                a que 3 servicios estén listos", "que los
                                hilos arranquen juntos" (lo usan casi todos
                                los ejemplos de estas guías).
            Semaphore(n)        n PERMISOS. acquire() toma uno (espera si no
                                hay), release() lo devuelve. Limita cuántos
                                hilos hacen algo A LA VEZ: "máximo 5
                                llamadas simultáneas al proveedor de pagos".
            CyclicBarrier(n)    n hilos se esperan entre sí en un punto, y
                                siguen juntos; reutilizable (por fases).
            Phaser              como CyclicBarrier, con participantes que
                                entran y salen.

        El ejemplo: 20 hilos quieren llamar a un servicio que acepta 3
        llamadas a la vez. Con un Semaphore(3), nunca hay más de 3 dentro.
        Igual que con los bloqueos: release() en finally.
    */
    static boolean semaphoreExample() throws InterruptedException {
        Semaphore permits = new Semaphore(3);
        AtomicInteger inside = new AtomicInteger();
        AtomicInteger maxInside = new AtomicInteger();
        runInThreads(20, () -> {
            try {
                permits.acquire();
                try {
                    int now = inside.incrementAndGet();
                    maxInside.accumulateAndGet(now, Math::max);
                    pause(5);                               // "la llamada al servicio"
                    inside.decrementAndGet();
                } finally {
                    permits.release();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        return maxInside.get() == 3;
    }

    /*
        =================================================================
        CAPA 10) QUÉ HERRAMIENTA USAR
        =================================================================

            Necesitas                                   Usa
            ------------------------------------------  ---------------------------
            no compartir, o compartir inmutable         nada (la mejor opción)
            una bandera que otro hilo debe ver          volatile (MemoryModel)
            un contador, un acumulador, un id           AtomicLong, LongAdder
                                                        (part2_coordination/Atomics)
            un mapa, una cola, una lista compartida     colecciones concurrentes
                                                        (ConcurrentCollections)
            varias variables que cambian juntas         synchronized
            lo mismo, con tiempo límite o interrumpible ReentrantLock
            muchas lecturas largas, pocas escrituras    ReadWriteLock, o un
                                                        inmutable reemplazable
            esperar a que algo pase                     CountDownLatch, cola
                                                        bloqueante
            limitar cuántos a la vez                    Semaphore
            tareas en paralelo con resultado            Executors, CompletableFuture
                                                        (part3_tasks/)
    */

    /*
        =================================================================
        CAPA 11) ERRORES COMUNES
        =================================================================

        1) Sincronizar la escritura y no la lectura (CAPA 1).

        2) synchronized sobre this en clases públicas; sobre Strings o
           Integer; sobre un campo que no es final (CAPA 2).

        3) Llamadas lentas o código ajeno dentro de un bloque (CAPA 2).

        4) Proteger cada variable por separado cuando forman un invariante
           (CAPA 3).

        5) Cambiar a otro tipo de bloqueo "porque es más rápido" sin medir;
           pedir bloqueos justos sin necesitarlos (CAPA 4).

        6) lock() sin unlock() en finally (CAPA 5).

        7) Tomar varios bloqueos en órdenes distintos (CAPA 6).

        8) if en vez de while al esperar una condición; wait/notify a mano
           cuando había un sincronizador hecho (CAPAS 8 y 9).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - synchronized: exclusión mutua + visibilidad; reentrante; sobre un
          objeto privado y final; bloques cortos; lecturas también.
        - Un invariante de varias variables, bajo un mismo bloqueo.
        - Sin competencia, un bloqueo cuesta nanosegundos; con competencia,
          interviene el SO (futex en Linux) y cuesta mucho más. Los justos,
          150 veces más.
        - ReentrantLock: tryLock, tiempo límite, interrumpible, justo,
          condiciones; unlock en finally.
        - Deadlock: orden global de bloqueos.
        - ReadWriteLock: muchos lectores o un escritor, para lecturas
          largas.
        - Esperar condiciones: while + await/signalAll; mejor
          CountDownLatch, Semaphore, colas bloqueantes.
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
                awaitQuietly(start);
                task.run();
            }));
        }
        start.countDown();
        for (Thread thread : threads) {
            thread.join();
        }
    }

    /** Espera (hasta 5 s) a que el hilo llegue al estado. */
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
            Thread.currentThread().interrupt();
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
