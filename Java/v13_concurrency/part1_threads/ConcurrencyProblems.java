package v13_concurrency.part1_threads;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/*
    =====================================================================
    LOS PROBLEMAS DE LA CONCURRENCIA, PROVOCADOS DE VERDAD
    =====================================================================

    Ruta de concurrency/: guía 2 de 9  (part1_threads)
    Anterior:  part1_threads/ThreadBasics
    Siguiente: part1_threads/MemoryModel

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.
    - Aquí cada problema SE PROVOCA de verdad y se comprueba que pasó. Los
      números se midieron en Windows 11, JDK 26, 14 núcleos lógicos. Los
      problemas de concurrencia dependen del momento exacto en que corre
      cada hilo: las checks repiten el intento cuando hace falta.
    - Las soluciones solo se presentan aquí; cada una tiene su guía en
      part2_coordination/.

    Importancia: MUY ALTA. Estos errores no aparecen en las pruebas, sí en
    producción, y casi nunca se pueden reproducir a voluntad.

    Índice:
        CAPA 1)  Por qué compartir datos mutables es el problema
        CAPA 2)  Condición de carrera: actualizaciones perdidas
        CAPA 3)  Revisar y luego actuar
        CAPA 4)  Colecciones normales compartidas
        CAPA 5)  Deadlock: esperarse mutuamente para siempre
        CAPA 6)  Livelock: muy ocupados sin avanzar
        CAPA 7)  Starvation: el que nunca recibe su turno
        CAPA 8)  Visibilidad: el cambio que otro hilo nunca ve
        CAPA 9)  La mejor defensa: no compartir lo mutable
        CAPA 10) Errores comunes
        RESUMEN FINAL
*/
public class ConcurrencyProblems {

    public static void main(String[] args) throws Exception {
        check(lostUpdatesExample(), "CAPA 2");
        check(checkThenActExample(), "CAPA 3");
        check(sharedCollectionExample(), "CAPA 4");
        check(deadlockExample(), "CAPA 5");
        check(livelockExample(), "CAPA 6");
        check(poolStarvationExample(), "CAPA 7");
        check(confinementExample(), "CAPA 9");
        check(visibilityExample(), "CAPA 8");              // al final: deja un hilo girando hasta que la JVM termina
        System.out.println("ConcurrencyProblems: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) POR QUÉ COMPARTIR DATOS MUTABLES ES EL PROBLEMA
        =================================================================

        Todos los problemas de esta guía necesitan TRES ingredientes a la
        vez:
            1) un dato COMPARTIDO entre hilos (un objeto del heap, un static);
            2) que sea MUTABLE (alguien lo cambia);
            3) acceso SIN coordinación.

        Quita cualquiera y el problema desaparece:
        - no compartir: cada hilo con sus propios datos (CAPA 9);
        - no mutar: objetos inmutables, como records y List.of
          (v2_oop/part4_advanced_types/Records), se comparten sin riesgo;
        - coordinar: bloqueos, atómicos, colecciones concurrentes
          (part2_coordination/).

        Por qué es tan difícil: el planificador del SO puede pausar un hilo
        ENTRE CUALQUIER par de instrucciones (ThreadBasics, CAPA 1), y en
        varios núcleos los hilos corren literalmente al mismo tiempo. El
        resultado depende de un orden que cambia en cada ejecución. Un
        error así puede pasar una vez en un millón: nunca en las pruebas,
        varias veces al día en producción.
    */

    /*
        =================================================================
        CAPA 2) CONDICIÓN DE CARRERA: ACTUALIZACIONES PERDIDAS
        =================================================================

        counter++ parece una sola operación. No lo es: en bytecode
        (v12_jvm/part2_classes/ClassLoadingAndBytecode, CAPA 2) son tres:

            getstatic counter     LEER el valor (digamos 41)
            iconst_1, iadd        SUMAR en el registro (42)
            putstatic counter     ESCRIBIR 42

        Con dos hilos:

            hilo A                      hilo B
            lee 41
                                        lee 41
            escribe 42
                                        escribe 42      <- un incremento perdido

        Medido: 4 hilos sumando 1,000,000 cada uno (esperado 4,000,000)
        dieron 1,993,984 y 3,799,365. Se perdió hasta la MITAD.

        Y una trampa más: en una tercera ronda dio EXACTO 4,000,000. El JIT
        compiló el ciclo y sumó el millón en un registro antes de escribir
        una sola vez, y esa vez los hilos no se encimaron. Que el resultado
        salga bien una vez no significa que el código esté bien: el error
        sigue ahí, esperando otro momento.

        Se llama CONDICIÓN DE CARRERA (race condition): el resultado
        depende de quién "gana la carrera". La solución es que leer-sumar-
        escribir sea ATÓMICO (indivisible): AtomicInteger
        (part2_coordination/Atomics) o synchronized
        (part2_coordination/Synchronization). El ejemplo intenta varias
        veces hasta ver una pérdida, y comprueba que con AtomicInteger el
        total siempre es exacto.
    */
    static int unsafeCounter;

    static boolean lostUpdatesExample() throws InterruptedException {
        boolean lostSeen = false;
        for (int attempt = 0; attempt < 20 && !lostSeen; attempt++) {
            unsafeCounter = 0;
            runInThreads(4, () -> {
                for (int i = 0; i < 1_000_000; i++) {
                    unsafeCounter++;                        // leer, sumar, escribir: tres pasos
                }
            });
            lostSeen = unsafeCounter < 4_000_000;
        }

        AtomicInteger safeCounter = new AtomicInteger();
        runInThreads(4, () -> {
            for (int i = 0; i < 1_000_000; i++) {
                safeCounter.incrementAndGet();              // un solo paso indivisible
            }
        });
        return lostSeen && safeCounter.get() == 4_000_000;
    }

    /*
        =================================================================
        CAPA 3) REVISAR Y LUEGO ACTUAR
        =================================================================

        La otra forma típica de carrera: REVISAR una condición y ACTUAR
        según ella, cuando otro hilo puede cambiar las cosas en medio.

            if (balance >= amount) {        // revisar
                // <- otro hilo retira aquí
                balance -= amount;          // actuar con información vieja
            }

        Dos retiros de 100 sobre una cuenta con 100: los dos ven "hay
        100", los dos retiran, la cuenta queda en -100. Cada operación es
        correcta por sí sola; juntas no.

        Aparece por todos lados:
        - "si no existe en el mapa, créalo y guárdalo" (dos hilos crean dos);
        - "si el archivo no existe, créalo" (v8_apis/part2_io_and_network/
          FilesAndIO: por eso CREATE_NEW);
        - "si no hay usuario con ese correo, regístralo" (dos registros:
          por eso además la base de datos tiene un índice único).

        La solución: revisar y actuar dentro de la MISMA operación atómica
        (un bloque synchronized, o métodos que ya lo son, como
        ConcurrentHashMap.computeIfAbsent: part2_coordination/
        ConcurrentCollections).

        En el ejemplo, una pausa entre revisar y actuar agranda la ventana
        para que el problema se vea siempre; sin la pausa pasa igual, solo
        que menos seguido. Con synchronized, uno de los dos retiros se
        rechaza.
    */
    static final class UnsafeAccount {
        private int balance = 100;

        boolean withdraw(int amount) {
            if (balance >= amount) {
                pause(50);                                  // la ventana entre revisar y actuar
                balance -= amount;
                return true;
            }
            return false;
        }
    }

    static final class SafeAccount {
        private int balance = 100;

        synchronized boolean withdraw(int amount) {         // revisar y actuar: un solo paso
            if (balance >= amount) {
                pause(50);
                balance -= amount;
                return true;
            }
            return false;
        }

        synchronized int balance() {
            return balance;
        }
    }

    static boolean checkThenActExample() throws InterruptedException {
        UnsafeAccount unsafe = new UnsafeAccount();
        AtomicInteger unsafeApproved = new AtomicInteger();
        runInThreads(2, () -> {
            if (unsafe.withdraw(100)) {
                unsafeApproved.incrementAndGet();
            }
        });

        SafeAccount safe = new SafeAccount();
        AtomicInteger safeApproved = new AtomicInteger();
        runInThreads(2, () -> {
            if (safe.withdraw(100)) {
                safeApproved.incrementAndGet();
            }
        });
        return unsafeApproved.get() == 2                    // los dos retiraron: la cuenta quedó en -100
                && safeApproved.get() == 1 && safe.balance() == 0;
    }

    /*
        =================================================================
        CAPA 4) COLECCIONES NORMALES COMPARTIDAS
        =================================================================

        ArrayList, HashMap, HashSet... NO son seguras entre hilos. Por
        dentro, add hace "poner en la posición size y luego size++": la
        misma carrera de la CAPA 2, más el crecimiento del arreglo
        interno. Medido, 4 hilos agregando 100,000 elementos cada uno a la
        MISMA ArrayList: quedaron entre 136,298 y 158,699 de 400,000. Según
        el momento, también puede lanzar ArrayIndexOutOfBoundsException o
        dejar nulls donde no había.

        Peor: un HashMap modificado por dos hilos puede quedar con su
        estructura interna dañada (entradas perdidas, búsquedas que no
        encuentran lo que sí está).

        Las opciones (part2_coordination/ConcurrentCollections):
        - ConcurrentHashMap, CopyOnWriteArrayList, colas bloqueantes;
        - o mejor: que cada hilo llene SU lista y juntarlas al final (CAPA
          9), o un stream paralelo que lo hace por ti (v7_functional/
          part2_streams/AdvancedStreams).
    */
    static boolean sharedCollectionExample() throws InterruptedException {
        boolean broken = false;
        for (int attempt = 0; attempt < 10 && !broken; attempt++) {
            List<Integer> shared = new ArrayList<>();
            AtomicInteger failures = new AtomicInteger();
            runInThreads(4, () -> {
                try {
                    for (int i = 0; i < 100_000; i++) {
                        shared.add(i);
                    }
                } catch (RuntimeException e) {
                    failures.incrementAndGet();             // también puede romperse así
                }
            });
            broken = shared.size() != 400_000 || failures.get() > 0;
        }
        return broken;
    }

    /*
        =================================================================
        CAPA 5) DEADLOCK: ESPERARSE MUTUAMENTE PARA SIEMPRE
        =================================================================

        Una transferencia bloquea la cuenta de origen y luego la de
        destino. Dos transferencias al mismo tiempo, en sentidos contrarios:

            hilo 1: bloquea A ... quiere B (la tiene el hilo 2)
            hilo 2: bloquea B ... quiere A (la tiene el hilo 1)

        Ninguno suelta lo que tiene; los dos esperan para siempre. No hay
        error, no hay excepción, no usa CPU: la aplicación simplemente deja
        de responder en esa parte.

        Pasa cuando se cumplen las cuatro condiciones de Coffman (1971): los
        recursos son exclusivos, se toma uno y se espera otro sin soltar el
        primero, nadie los puede quitar a la fuerza, y hay una espera
        CIRCULAR. Se rompe atacando una:
        - ORDEN GLOBAL: todos bloquean las cuentas en el mismo orden (por
          ejemplo, el id menor primero). Así no puede haber círculo. Es la
          solución más usada;
        - tryLock con tiempo límite (ReentrantLock): si no consigues el
          segundo en 100 ms, sueltas el primero y reintentas
          (part2_coordination/Synchronization);
        - no tener dos bloqueos a la vez (rediseñar).

        Cómo se ve, medido con jcmd <pid> Thread.print en un deadlock con
        synchronized:

            Found one Java-level deadlock:
            =============================
            "transfer-A-to-B":
              waiting to lock monitor 0x...aaaa0 (object 0x...c860, a java.lang.Object),
              which is held by "transfer-B-to-A"
            "transfer-B-to-A":
              waiting to lock monitor 0x...ab360 (object 0x...c850, a java.lang.Object),
              which is held by "transfer-A-to-B"
            ...
            Found 1 deadlock.

        La JVM los detecta sola; desde el código, con ThreadMXBean.
        findDeadlockedThreads(), que es lo que usa el ejemplo. (El ejemplo
        usa ReentrantLock con lockInterruptibly para poder destrabarlos al
        final interrumpiéndolos; un hilo trabado en synchronized no se puede
        destrabar.)
    */
    static boolean deadlockExample() throws InterruptedException {
        ReentrantLock accountA = new ReentrantLock();
        ReentrantLock accountB = new ReentrantLock();
        CountDownLatch bothHoldFirst = new CountDownLatch(2);

        Thread aToB = Thread.ofPlatform().name("transfer-A-to-B")
                .start(() -> transfer(accountA, accountB, bothHoldFirst));
        Thread bToA = Thread.ofPlatform().name("transfer-B-to-A")
                .start(() -> transfer(accountB, accountA, bothHoldFirst));

        long[] deadlocked = null;
        for (int attempt = 0; attempt < 200 && deadlocked == null; attempt++) {
            Thread.sleep(10);
            deadlocked = ManagementFactory.getThreadMXBean().findDeadlockedThreads();   // null si no hay
        }
        aToB.interrupt();                                   // destrabar (solo posible con lockInterruptibly)
        bToA.interrupt();
        aToB.join();
        bToA.join();
        return deadlocked != null && deadlocked.length == 2;
    }

    private static void transfer(ReentrantLock from, ReentrantLock to, CountDownLatch bothHoldFirst) {
        try {
            from.lockInterruptibly();
            try {
                bothHoldFirst.countDown();
                bothHoldFirst.await();                      // los dos ya tienen su primera cuenta
                to.lockInterruptibly();                     // y ahora cada uno quiere la del otro
                to.unlock();
            } finally {
                from.unlock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /*
        =================================================================
        CAPA 6) LIVELOCK: MUY OCUPADOS SIN AVANZAR
        =================================================================

        Dos personas en un pasillo: las dos se hacen a la izquierda, luego
        las dos a la derecha, luego a la izquierda... Nadie está bloqueado,
        las dos se mueven todo el tiempo, y ninguna pasa. Eso es un
        LIVELOCK: los hilos no esperan (a diferencia del deadlock), usan
        CPU, y no avanzan.

        En código pasa con reintentos "educados" y simétricos: dos hilos que
        al chocar sueltan lo que tienen y reintentan DESPUÉS DEL MISMO
        TIEMPO, chocan otra vez, y así para siempre. Lo mismo con
        reintentos de red entre servicios que fallan juntos y reintentan
        juntos.

        La solución: romper la simetría con una espera ALEATORIA ("jitter"),
        normalmente creciente (esperar 10 ms + algo al azar, luego 20 + algo,
        luego 40...: "backoff exponencial"). Es lo que hacen los protocolos
        de red y los buenos clientes HTTP.

        El ejemplo es una simulación paso a paso (sin hilos, para que sea
        exacta): con esperas iguales, en 1,000 rondas nunca se cruzan; con
        esperas aleatorias, se cruzan en pocas rondas.
    */
    /** Rondas hasta que se cruzan, o -1 si en 1,000 rondas nunca lo logran (livelock). */
    static int roundsUntilTheyPass(boolean randomBackoff, Random random) {
        for (int round = 1; round <= 1_000; round++) {
            int waitLeft = randomBackoff ? random.nextInt(3) : 1;    // cuánto espera cada uno
            int waitRight = randomBackoff ? random.nextInt(3) : 1;   // antes de hacerse a un lado
            if (waitLeft != waitRight) {
                return round;                               // uno se movió antes que el otro: pasaron
            }
            // se movieron al mismo tiempo, al mismo lado: siguen de frente
        }
        return -1;
    }

    static boolean livelockExample() {
        Random random = new Random(42);                     // semilla fija: el ejemplo siempre igual
        int symmetric = roundsUntilTheyPass(false, random);
        int withJitter = roundsUntilTheyPass(true, random);
        return symmetric == -1 && withJitter > 0 && withJitter < 10;
    }

    /*
        =================================================================
        CAPA 7) STARVATION: EL QUE NUNCA RECIBE SU TURNO
        =================================================================

        STARVATION (inanición): un hilo PODRÍA avanzar, pero nunca le toca.
        Causas:
        - bloqueos no justos: un ReentrantLock normal no garantiza orden;
          un hilo que suelta y vuelve a pedir el bloqueo al instante suele
          volver a ganarlo, y otro puede esperar mucho. new
          ReentrantLock(true) es "justo" (por orden de llegada), a cambio de
          menos rendimiento;
        - un hilo que tiene un bloqueo mucho tiempo (una llamada por red
          DENTRO de un synchronized);
        - y la más común en servidores: AGOTAR UN POOL de hilos.

        El caso del pool, que es un deadlock disfrazado: un pool de N hilos
        (part3_tasks/ExecutorsUsage) ejecuta tareas que, a su vez, mandan otra
        tarea AL MISMO POOL y esperan su resultado. Si las N tareas de
        afuera ocupan los N hilos, las de adentro nunca consiguen hilo, y
        las de afuera las esperan para siempre.

        El ejemplo lo provoca con un pool de 1 hilo: la tarea de afuera
        espera a la de adentro, que está en la cola detrás de ella. Sin el
        tiempo límite del get, esperaría para siempre.

        La solución: nunca esperar, desde una tarea, a otra tarea del mismo
        pool (usar otro pool, o componer sin esperar con
        part3_tasks/CompletableFutureUsage).
    */
    static boolean poolStarvationExample() throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(1);
        try {
            Future<String> outer = pool.submit(() -> {
                Future<String> inner = pool.submit(() -> "resultado");   // a la cola, detrás de mí
                return inner.get(300, TimeUnit.MILLISECONDS);            // espera un hilo que no hay
            });
            try {
                outer.get();
                return false;
            } catch (ExecutionException e) {
                return e.getCause() instanceof TimeoutException;         // la de adentro nunca corrió
            }
        } finally {
            pool.shutdownNow();
        }
    }

    /*
        =================================================================
        CAPA 8) VISIBILIDAD: EL CAMBIO QUE OTRO HILO NUNCA VE
        =================================================================

        El problema más raro, porque no hay dos escrituras chocando. Un
        hilo gira esperando una bandera; otro la pone en true:

            static boolean stop;                      // sin volatile
            hilo 1:  while (!stop) { n++; }
            main:    stop = true;

        Medido: en 5 de 5 rondas, el hilo 1 SIGUIÓ girando para siempre
        después de stop = true.

        ¿Por qué? Ni el JIT ni el procesador están obligados a que un hilo
        vea lo que escribe otro, salvo que el código lo pida. El JIT ve que
        dentro del ciclo nadie cambia stop, así que lee stop UNA vez antes
        del ciclo y lo convierte en "while (true)". Es una optimización
        legal: el código no dijo que stop lo comparten varios hilos.
        Además, cada núcleo tiene sus cachés y sus buffers de escritura: una
        escritura puede tardar en ser visible a otros núcleos.

        La solución mínima es volatile: le dice a la JVM "esta variable se
        comparte; cada lectura va a la memoria de verdad y cada escritura
        se publica". Las reglas exactas de qué ve cada hilo son el MODELO
        DE MEMORIA de Java (la siguiente guía, part1_threads/MemoryModel).

        Y ojo: volatile resuelve VISIBILIDAD, no ATOMICIDAD. Un volatile
        int con count++ sigue perdiendo incrementos (CAPA 2).
    */
    static boolean plainStop;
    static volatile boolean volatileStop;

    static boolean visibilityExample() throws InterruptedException {
        Thread plainSpinner = Thread.ofPlatform().name("plain-spinner").daemon(true).start(() -> {
            long spins = 0;
            while (!plainStop) {                            // el JIT puede leer plainStop una sola vez
                spins++;
            }
        });
        Thread volatileSpinner = Thread.ofPlatform().name("volatile-spinner").daemon(true).start(() -> {
            long spins = 0;
            while (!volatileStop) {                         // cada vuelta lee la memoria de verdad
                spins++;
            }
        });
        Thread.sleep(300);                                  // que el JIT compile los ciclos
        plainStop = true;
        volatileStop = true;
        plainSpinner.join(1_000);
        volatileSpinner.join(1_000);
        return !volatileSpinner.isAlive()                   // vio el cambio y terminó
                && plainSpinner.isAlive();                  // nunca lo vio (es daemon: muere con la JVM)
    }

    /*
        =================================================================
        CAPA 9) LA MEJOR DEFENSA: NO COMPARTIR LO MUTABLE
        =================================================================

        Antes de pensar en bloqueos (CAPA 1), la pregunta es: ¿hace falta
        compartir algo mutable? Casi siempre se puede evitar:

        - CONFINAMIENTO: cada hilo trabaja con SUS datos (sus locales, sus
          objetos) y entrega un RESULTADO al final. Es lo que hizo join en
          ThreadBasics, CAPA 3, y lo que hace un stream paralelo.
        - INMUTABILIDAD: records, List.of, String, LocalDate. Un objeto que
          no cambia se comparte entre mil hilos sin ningún cuidado.
        - MENSAJES en vez de memoria compartida: un hilo produce, otro
          consume, a través de una cola segura (part2_coordination/
          ConcurrentCollections).

        El mismo conteo de la CAPA 2, sin compartir: cada hilo cuenta en
        SU variable local y devuelve su total. Exacto, sin bloqueos, y más
        rápido (no hay hilos peleando por la misma línea de caché).
    */
    static boolean confinementExample() throws InterruptedException {
        long[] perThread = new long[4];
        List<Thread> workers = new ArrayList<>();
        for (int t = 0; t < 4; t++) {
            int index = t;
            workers.add(Thread.ofPlatform().start(() -> {
                long mine = 0;                              // local: solo de este hilo
                for (int i = 0; i < 1_000_000; i++) {
                    mine++;
                }
                perThread[index] = mine;                    // una escritura, en su casilla
            }));
        }
        for (Thread worker : workers) {
            worker.join();                                  // join: ver lo que escribió
        }
        long total = 0;
        for (long count : perThread) {
            total += count;
        }
        return total == 4_000_000;
    }

    /*
        =================================================================
        CAPA 10) ERRORES COMUNES
        =================================================================

        1) "En mis pruebas funciona": los errores de concurrencia dependen
           del momento; que salga bien no prueba nada (CAPAS 1 y 2).

        2) Creer que x++ o una suma son atómicas (CAPA 2).

        3) Revisar y actuar en dos pasos sobre algo compartido (CAPA 3).

        4) Compartir ArrayList, HashMap o HashSet entre hilos (CAPA 4).

        5) Tomar dos bloqueos en órdenes distintos (CAPA 5).

        6) Reintentos con esperas fijas e iguales (CAPA 6).

        7) Esperar, dentro de una tarea, otra tarea del mismo pool (CAPA 7).

        8) Banderas compartidas sin volatile; creer que volatile hace
           atómico un ++ (CAPA 8).

        9) Ir directo a los bloqueos sin preguntarse si se puede no
           compartir (CAPA 9).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - El origen de todo: datos compartidos + mutables + sin
          coordinación.
        - Carrera: leer-modificar-escribir intercalado pierde
          actualizaciones; revisar-y-actuar decide con datos viejos.
        - Las colecciones normales se corrompen con varios hilos.
        - Deadlock: espera circular de bloqueos; se evita con orden global
          o tryLock; jcmd y ThreadMXBean lo detectan.
        - Livelock: reintentos simétricos; se rompe con espera aleatoria.
        - Starvation: alguien nunca recibe turno; el caso típico es agotar
          un pool esperando tareas del mismo pool.
        - Visibilidad: sin volatile (u otra sincronización), un hilo puede
          no ver nunca lo que escribe otro.
        - Primera defensa: confinar, hacer inmutable, pasar mensajes.
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
                    start.await();                          // todos esperan la señal...
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                task.run();
            }));
        }
        start.countDown();                                  // ...y arrancan a la vez
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
