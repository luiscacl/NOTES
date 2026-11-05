package v13_concurrency.part1_threads;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicIntegerArray;

/*
    =====================================================================
    EL MODELO DE MEMORIA: QUÉ VE CADA HILO, Y CUÁNDO
    =====================================================================

    Ruta de concurrency/: guía 3 de 9  (part1_threads)
    Anterior:  part1_threads/ConcurrencyProblems
    Siguiente: part2_coordination/Synchronization

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.
    - Medido en Windows 11, JDK 26, procesador x86-64 de 14 núcleos
      lógicos. En procesadores ARM (los Mac recientes, muchos servidores
      en la nube) el hardware reordena todavía más.

    La guía anterior mostró que un hilo puede no ver nunca una bandera
    sin volatile (ConcurrencyProblems, CAPA 8). Esta explica las REGLAS:
    qué garantiza Java y qué no.

    Importancia: ALTA para entender, BAJA para usar directamente: casi
    nunca escribirás código que dependa de estas reglas a mano. Pero son
    las que explican por qué synchronized, volatile, join y las
    colecciones concurrentes funcionan, y por qué "a mí me funciona" no
    prueba nada.

    Índice:
        CAPA 1)  Tu código no se ejecuta en el orden en que lo escribiste
        CAPA 2)  El reordenamiento, demostrado
        CAPA 3)  El modelo de memoria de Java: happens-before
        CAPA 4)  Publicar datos con una bandera volatile
        CAPA 5)  La garantía de oro: sin carreras de datos, todo es simple
        CAPA 6)  Publicación segura y los campos final
        CAPA 7)  Double-checked locking: el ejemplo clásico
        CAPA 8)  volatile en detalle (y lo que cuesta)
        CAPA 9)  Errores comunes
        RESUMEN FINAL
*/
public class MemoryModel {

    public static void main(String[] args) throws InterruptedException {
        check(reorderingExample(), "CAPA 2");
        check(volatilePublicationExample(), "CAPA 4");
        check(doubleCheckedLockingExample(), "CAPA 7");
        System.out.println("MemoryModel: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) TU CÓDIGO NO SE EJECUTA EN EL ORDEN EN QUE LO ESCRIBISTE
        =================================================================

        Dentro de UN hilo, todo parece ocurrir en orden. Pero entre
        hilos, tres capas pueden cambiar el orden en que las escrituras y
        lecturas se ven:

        1) EL COMPILADOR JIT reordena instrucciones, guarda valores en
           registros y deja de releerlos (la bandera de ConcurrencyProblems,
           CAPA 8), elimina escrituras que "nadie" lee. Todo legal si el
           resultado es el mismo PARA ESE HILO.

        2) EL PROCESADOR ejecuta fuera de orden. Y cada núcleo tiene un
           BUFFER DE ESCRITURAS: cuando escribe, el valor va primero a ese
           buffer y llega a la memoria compartida (a la caché que ven los
           demás núcleos) un poco después. Mientras tanto, el núcleo ya
           ejecutó las instrucciones siguientes, incluidas lecturas.

        3) LAS CACHÉS: cada núcleo tiene las suyas (L1, L2) y comparten una
           más grande (L3). Un protocolo de coherencia las mantiene de
           acuerdo, pero no al instante.

        En un procesador x86 (Intel, AMD) el reordenamiento visible es
        casi solo el del punto 2: "una escritura seguida de una lectura de
        otra variable" puede verse al revés. En ARM puede pasar casi
        cualquier combinación. Java no puede depender del procesador:
        define SUS reglas (CAPA 3), y la JVM pone las barreras necesarias
        en cada procesador para cumplirlas.
    */

    /*
        =================================================================
        CAPA 2) EL REORDENAMIENTO, DEMOSTRADO
        =================================================================

        El experimento clásico ("store buffering"). Todo empieza en 0:

            hilo 1:  x = 1;  r1 = y;
            hilo 2:  y = 1;  r2 = x;

        Si cada hilo ejecutara en orden, alguna escritura va primero, así
        que al menos uno de los dos debe leer 1. Los resultados posibles
        "en orden" son (r1, r2) = (0,1), (1,0) o (1,1). NUNCA (0,0).

        Medido, 2 millones de rondas (los dos hilos recorriendo arreglos a
        la vez, una casilla por ronda):

            variables normales     (0,0) en 0, 7,952, 621,088, 9,872 rondas
                                   (según la corrida; 0 la primera, antes de
                                   que el JIT compilara)
            con volatile           (0,0) en 0 rondas, siempre

        (0,0) significa que CADA hilo leyó ANTES de que su propia escritura
        fuera visible para el otro: las escrituras estaban en los buffers
        (o el JIT movió las lecturas). Con volatile, el modelo de memoria
        PROHÍBE ese resultado, y la JVM pone las barreras para impedirlo.

        No importa si lo causó el procesador o el JIT: el modelo de memoria
        los trata igual. Lo que importa es que el código sin sincronizar
        PUEDE ver cosas imposibles "en orden".

        AtomicIntegerArray da a cada casilla las garantías de un volatile
        (part2_coordination/Atomics).
    */
    static int countBothZero(int rounds, boolean withVolatile) throws InterruptedException {
        int[] r1 = new int[rounds];
        int[] r2 = new int[rounds];
        CountDownLatch go = new CountDownLatch(1);
        Thread first;
        Thread second;
        if (withVolatile) {
            AtomicIntegerArray x = new AtomicIntegerArray(rounds);
            AtomicIntegerArray y = new AtomicIntegerArray(rounds);
            first = Thread.ofPlatform().start(() -> {
                awaitQuietly(go);
                for (int i = 0; i < rounds; i++) {
                    x.set(i, 1);
                    r1[i] = y.get(i);
                }
            });
            second = Thread.ofPlatform().start(() -> {
                awaitQuietly(go);
                for (int i = 0; i < rounds; i++) {
                    y.set(i, 1);
                    r2[i] = x.get(i);
                }
            });
        } else {
            int[] x = new int[rounds];
            int[] y = new int[rounds];
            first = Thread.ofPlatform().start(() -> {
                awaitQuietly(go);
                for (int i = 0; i < rounds; i++) {
                    x[i] = 1;                               // escribe...
                    r1[i] = y[i];                           // ...y lee la otra
                }
            });
            second = Thread.ofPlatform().start(() -> {
                awaitQuietly(go);
                for (int i = 0; i < rounds; i++) {
                    y[i] = 1;
                    r2[i] = x[i];
                }
            });
        }
        go.countDown();
        first.join();                                       // join: ya se pueden leer r1 y r2 (CAPA 3)
        second.join();
        int bothZero = 0;
        for (int i = 0; i < rounds; i++) {
            if (r1[i] == 0 && r2[i] == 0) {
                bothZero++;
            }
        }
        return bothZero;
    }

    static boolean reorderingExample() throws InterruptedException {
        boolean impossibleSeen = false;
        for (int attempt = 0; attempt < 15 && !impossibleSeen; attempt++) {
            impossibleSeen = countBothZero(1_000_000, false) > 0;
        }
        boolean volatileNever = true;
        for (int attempt = 0; attempt < 3; attempt++) {
            volatileNever &= countBothZero(1_000_000, true) == 0;   // prohibido por el modelo de memoria
        }
        return impossibleSeen && volatileNever;
    }

    /*
        =================================================================
        CAPA 3) EL MODELO DE MEMORIA DE JAVA: HAPPENS-BEFORE
        =================================================================

        El MODELO DE MEMORIA de Java (JMM: Java Memory Model, capítulo 17
        de la especificación del lenguaje, desde Java 5) responde una sola
        pregunta: "cuando un hilo LEE una variable, ¿qué valores puede
        ver?".

        Su herramienta es la relación HAPPENS-BEFORE ("sucede antes"). Si
        la acción A happens-before la acción B, entonces todo lo que A (y
        lo anterior a A) escribió es VISIBLE para B. Sin esa relación, no
        hay ninguna garantía: B puede ver el valor nuevo, el viejo, o
        algunos sí y otros no.

        Las reglas que crean happens-before:

            ORDEN DEL PROGRAMA   dentro de un hilo, cada acción hb la
                                 siguiente
            MONITOR              soltar un bloqueo (salir de synchronized)
                                 hb el siguiente que lo toma
            VOLATILE             escribir un volatile hb cada lectura
                                 posterior de ESE volatile
            START                thread.start() hb todo lo que hace el
                                 hilo nuevo
            JOIN                 todo lo que hace un hilo hb que join()
                                 regrese (ThreadBasics, CAPA 3)
            INTERRUPCIÓN         interrupt() hb que el hilo lo detecte
            TRANSITIVIDAD        si A hb B y B hb C, entonces A hb C

        Aparte, con su propia regla (JLS 17.5; no es happens-before): los
        campos FINAL se ven con el valor que les dio el constructor aunque
        el objeto se publique sin sincronizar (CAPA 6). Solo los final: los
        demás campos no quedan garantizados.

        Y las del paquete java.util.concurrent, documentadas en cada clase:
        poner algo en una colección concurrente hb sacarlo; mandar una tarea
        a un executor hb que la tarea corra; que la tarea termine hb que
        future.get() regrese; countDown() hb que await() regrese.

        La lectura práctica: dos hilos se "comunican" de forma confiable
        SOLO a través de una de estas reglas. Si no puedes señalar cuál
        conecta la escritura de un hilo con la lectura del otro, el código
        tiene un error.
    */

    /*
        =================================================================
        CAPA 4) PUBLICAR DATOS CON UNA BANDERA VOLATILE
        =================================================================

        La TRANSITIVIDAD permite algo muy útil: con UN volatile se publican
        muchos datos normales.

            hilo productor                    hilo consumidor
            config = cargar();      (1)
            ready = true;  (volatile) (2)  ->  if (ready) {  (volatile) (3)
                                                   usar(config);       (4)
                                               }

        (1) hb (2) por orden del programa; (2) hb (3) por la regla de
        volatile; (3) hb (4) por orden del programa. Por transitividad, (1)
        hb (4): si el consumidor ve ready == true, VE el config completo,
        aunque config no sea volatile.

        Por eso el ORDEN importa: los datos se escriben ANTES de la bandera.
        Al revés (bandera primero, datos después) no hay garantía.

        Es exactamente lo que hacen por dentro las colecciones concurrentes
        y los executors: por eso puedes pasar objetos normales entre hilos
        a través de ellos sin pensar en esto.
    */
    static final class ConfigHolder {
        private String endpoint;                            // datos normales
        private int timeoutSeconds;
        private volatile boolean ready;                     // la bandera que publica

        void publish(String newEndpoint, int newTimeout) {
            endpoint = newEndpoint;                         // (1) primero los datos
            timeoutSeconds = newTimeout;
            ready = true;                                   // (2) al final, la bandera
        }

        String describeIfReady() {
            if (ready) {                                    // (3) leer la bandera primero
                return endpoint + " " + timeoutSeconds;     // (4) visibles por transitividad
            }
            return null;
        }
    }

    static boolean volatilePublicationExample() throws InterruptedException {
        ConfigHolder holder = new ConfigHolder();
        String[] seen = new String[1];
        Thread consumer = Thread.ofPlatform().start(() -> {
            String description;
            while ((description = holder.describeIfReady()) == null) {
                Thread.onSpinWait();                        // pista al procesador: "estoy esperando"
            }
            seen[0] = description;
        });
        holder.publish("https://pagos.interno", 30);
        consumer.join();
        return "https://pagos.interno 30".equals(seen[0]); // nunca "null 0" ni "https://... 0"
    }

    /*
        =================================================================
        CAPA 5) LA GARANTÍA DE ORO: SIN CARRERAS DE DATOS, TODO ES SIMPLE
        =================================================================

        Una CARRERA DE DATOS (data race) es: dos hilos acceden a la misma
        variable, al menos uno escribe, y NO hay happens-before entre esos
        accesos.

        El modelo de memoria promete: si tu programa NO tiene carreras de
        datos, se comporta como si todo ocurriera en UN orden global, uno
        tras otro, respetando el orden de cada hilo ("consistencia
        secuencial"). Todo lo raro de las CAPAS 1 y 2 desaparece.

        Esa es la regla práctica: no razones sobre buffers ni cachés.
        Asegúrate de que todo dato compartido y mutable se lea y se escriba
        bajo alguna regla de happens-before (un bloqueo, un volatile, una
        colección concurrente). Si lo logras, puedes pensar "en orden".

        Ojo: sin carreras de DATOS no significa sin ERRORES de concurrencia.
        El retiro doble de ConcurrencyProblems, CAPA 3, se puede escribir
        con cada lectura y escritura bajo su propio bloqueo y seguir mal: el
        problema ahí es que revisar y actuar no son UN paso.
    */

    /*
        =================================================================
        CAPA 6) PUBLICACIÓN SEGURA Y LOS CAMPOS FINAL
        =================================================================

        PUBLICAR un objeto es hacerlo alcanzable para otro hilo (guardarlo
        en un campo compartido, en una colección...). Si se publica SIN
        happens-before, el otro hilo puede ver la referencia al objeto pero
        sus campos todavía en 0 o null: un objeto "a medio construir".
        Raro en x86, posible en ARM, y legal según el modelo.

        Formas SEGURAS de publicar:
        - inicializarlo en un static (la inicialización de clases tiene su
          propio bloqueo; v12_jvm/part2_classes/ClassLoadingAndBytecode,
          CAPA 3);
        - guardarlo en un campo volatile o en un AtomicReference;
        - guardarlo bajo un bloqueo que el lector también use;
        - pasarlo por una colección concurrente, un executor o una cola;
        - y los CAMPOS FINAL.

        La regla especial de final: cuando el constructor termina, los
        campos final quedan "congelados", y CUALQUIER hilo que vea el
        objeto ve esos campos con su valor correcto, aunque el objeto se
        haya publicado sin sincronizar. Es lo que hace a los objetos
        INMUTABLES (todos sus campos final, como los records y String)
        seguros de compartir siempre.

        Con una condición: que this no se ESCAPE durante el constructor (no
        registrarse como listener ni arrancar un hilo que lo use desde el
        constructor). Si otro hilo lo ve antes de que el constructor
        termine, la garantía no aplica.
    */

    /*
        =================================================================
        CAPA 7) DOUBLE-CHECKED LOCKING: EL EJEMPLO CLÁSICO
        =================================================================

        Querer crear un objeto caro solo la primera vez que se pide, sin
        pagar un bloqueo en cada llamada:

            private static Config instance;           // SIN volatile: ROTO
            static Config get() {
                if (instance == null) {                // 1a revisión, sin bloqueo
                    synchronized (Config.class) {
                        if (instance == null) {        // 2a revisión, con bloqueo
                            instance = new Config();
                        }
                    }
                }
                return instance;
            }

        Se usó durante años y estaba ROTO: un hilo que pasa por la primera
        revisión SIN bloqueo puede ver instance != null (la referencia ya
        se escribió) pero los campos del Config sin inicializar (CAPA 6).
        No hay happens-before entre quien escribió y quien leyó sin
        bloqueo.

        Con volatile en instance, funciona (desde Java 5): la escritura
        volatile publica el objeto completo. Pero hoy hay formas más
        simples:
        - el modismo holder (ClassLoadingAndBytecode, CAPA 3): perezoso,
          seguro y sin escribir ningún bloqueo;
        - o mejor: crear el objeto al arrancar e inyectarlo (v11_patterns/
          part1_creational/Singleton, CAPA 4).
    */
    static final class ExpensiveConfig {
        private final String source;

        ExpensiveConfig(String source) {
            this.source = source;
        }

        String source() {
            return source;
        }
    }

    private static volatile ExpensiveConfig instance;       // volatile: obligatorio aquí
    private static int creations;

    static ExpensiveConfig config() {
        ExpensiveConfig local = instance;                   // una sola lectura volatile en el camino rápido
        if (local == null) {
            synchronized (MemoryModel.class) {
                local = instance;
                if (local == null) {
                    creations++;                            // protegido por el bloqueo
                    local = new ExpensiveConfig("config.properties");
                    instance = local;
                }
            }
        }
        return local;
    }

    static boolean doubleCheckedLockingExample() throws InterruptedException {
        ExpensiveConfig[] seen = new ExpensiveConfig[8];
        CountDownLatch go = new CountDownLatch(1);
        Thread[] threads = new Thread[seen.length];
        for (int i = 0; i < threads.length; i++) {
            int index = i;
            threads[i] = Thread.ofPlatform().start(() -> {
                awaitQuietly(go);
                seen[index] = config();
            });
        }
        go.countDown();
        for (Thread thread : threads) {
            thread.join();
        }
        boolean sameInstance = true;
        for (ExpensiveConfig config : seen) {
            sameInstance &= config == seen[0] && config.source().equals("config.properties");
        }
        synchronized (MemoryModel.class) {
            return sameInstance && creations == 1;          // 8 hilos a la vez, una sola creación
        }
    }

    /*
        =================================================================
        CAPA 8) VOLATILE EN DETALLE (Y LO QUE CUESTA)
        =================================================================

        Lo que volatile SÍ da:
        - VISIBILIDAD: cada lectura ve la última escritura (CAPA 3);
        - ORDEN: lo escrito antes de una escritura volatile no se mueve
          después de ella, y lo leído después de una lectura volatile no se
          mueve antes (lo que hace funcionar la CAPA 4);
        - ATOMICIDAD de lecturas y escrituras SIMPLES de long y double. Sin
          volatile, la especificación permite que un long se escriba en dos
          mitades de 32 bits (otro hilo vería una mezcla). En las JVM de 64
          bits no pasa en la práctica, pero el lenguaje no lo promete. Las
          referencias y los int siempre son atómicos.

        Lo que NO da:
        - atomicidad de operaciones COMPUESTAS: count++, if-then-act
          (ConcurrencyProblems, CAPAS 2 y 3). Para eso, atómicos o bloqueos.

        Cuánto cuesta: una lectura volatile en x86 cuesta casi lo mismo que
        una normal (lo que se pierde es que el JIT no puede guardarla en un
        registro). Una ESCRITURA volatile necesita una barrera que vacía el
        buffer de escrituras (CAPA 1): decenas de ciclos. Es barato para una
        bandera; no para una variable que se escribe millones de veces por
        segundo desde varios hilos.

        Para quien escribe librerías hay controles más finos (VarHandle con
        modos acquire/release y opaque; v12_jvm/part2_classes/
        ReflectionAndAnnotations, CAPA 9). En código de aplicación: volatile,
        atómicos, bloqueos y colecciones concurrentes cubren todo.
    */

    /*
        =================================================================
        CAPA 9) ERRORES COMUNES
        =================================================================

        1) Pensar que los demás hilos ven las operaciones en el orden del
           código (CAPAS 1 y 2).

        2) Comunicar hilos sin poder señalar qué regla de happens-before
           los conecta (CAPA 3).

        3) Escribir la bandera volatile ANTES que los datos que publica
           (CAPA 4).

        4) Creer que sin carreras de datos no hay errores de concurrencia
           (CAPA 5).

        5) Publicar objetos mutables en un campo normal compartido; dejar
           escapar this desde el constructor (CAPA 6).

        6) Double-checked locking sin volatile; o escribirlo cuando el
           modismo holder o la inyección bastaban (CAPA 7).

        7) Usar volatile para contadores (CAPA 8).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - El JIT, el procesador (buffers de escritura) y las cachés pueden
          hacer que otros hilos vean las operaciones en otro orden.
          Demostrado: el resultado "imposible" (0,0) aparece sin volatile y
          nunca con volatile.
        - El JMM define qué puede ver una lectura con happens-before:
          orden del programa, bloqueos, volatile, start, join,
          java.util.concurrent, y transitividad; los campos final tienen su
          propia garantía.
        - Un volatile escrito al final publica todo lo escrito antes.
        - Sin carreras de datos, el programa se comporta "en orden".
        - Publicar con seguridad: static, volatile/atómico, bloqueo,
          colección concurrente, o campos final (objetos inmutables).
        - Double-checked locking necesita volatile; mejor holder o
          inyección.
        - volatile: visibilidad y orden, no atomicidad de operaciones
          compuestas.
    */

    // -----------------------------------------------------------------
    // Utilidades internas de la guía.
    // -----------------------------------------------------------------

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
