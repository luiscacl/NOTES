package v12_jvm.part1_memory;

import com.sun.management.HotSpotDiagnosticMXBean;
import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntFunction;

/*
    =====================================================================
    PILA Y HEAP: CÓMO USA LA MEMORIA UN PROGRAMA JAVA
    =====================================================================

    Ruta de jvm/: guía 1 de 6  (part1_memory)
    Anterior:  v11_patterns/part4_modern/Specification
    Siguiente: part1_memory/GarbageCollector

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.
    - Los números citados se MIDIERON en Windows 11, JDK 26, con 16 GB de
      RAM. En tu máquina pueden variar; las checks comprueban la idea, no
      el número exacto.
    - El sistema operativo de referencia es Linux; cuando Windows hace algo
      distinto, se dice.

    Lo que ya se vio y aquí se profundiza:
    - la pila de llamadas y el stack trace (v1_fundamentals/
      part2_control_flow/Methods, CAPA 9);
    - locales en la pila y campos en el heap (v1_fundamentals/
      part1_first_steps/Variables, CAPA 4);
    - copiar una referencia no copia el objeto (v1_fundamentals/
      part3_text_and_references/ReferencesAndNull, CAPA 2).

    Importancia: ALTA. Explica StackOverflowError, OutOfMemoryError, por
    qué un List<Integer> pesa 5 veces más que un int[], y por qué la JVM
    "usa" gigas que en realidad no usa.

    Índice:
        CAPA 1)  La JVM es un proceso: su memoria vista desde el SO
        CAPA 2)  La pila: una por hilo, un marco por llamada
        CAPA 3)  StackOverflowError por dentro
        CAPA 4)  El heap: compartido, y por qué new es barato
        CAPA 5)  Qué vive dónde
        CAPA 6)  Cuánto pesa de verdad un objeto
        CAPA 7)  new no siempre llega al heap: análisis de escape
        CAPA 8)  Los OutOfMemoryError y el que mata el sistema operativo
        CAPA 9)  Errores comunes
        RESUMEN FINAL
*/
public class StackAndHeapMemory {

    public static void main(String[] args) throws InterruptedException {
        check(reservedVsCommittedExample(), "CAPA 1");
        check(stackSizeExample(), "CAPA 3");
        check(heapLimitExample(), "CAPA 4");
        check(memoryAreasExample(), "CAPA 5");
        check(objectSizeExample(), "CAPA 6");
        check(escapeAnalysisExample(), "CAPA 7");
        System.out.println("StackAndHeapMemory: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) LA JVM ES UN PROCESO: SU MEMORIA VISTA DESDE EL SO
        =================================================================

        "java Programa" arranca un PROCESO del sistema operativo, como
        cualquier otro programa. El SO le da un ESPACIO DE DIRECCIONES
        VIRTUAL: un rango enorme de direcciones (en 64 bits, 128 TB en
        Linux) que el proceso cree que son suyas. Esas direcciones no son
        RAM: el SO las va conectando a páginas de RAM físicas (de 4 KB)
        solo cuando el proceso las TOCA por primera vez. Eso se llama
        paginación bajo demanda.

        Dentro de ese espacio, la JVM organiza varias zonas:

            espacio virtual del proceso java
            +--------------------------------------------------+
            | HEAP              los objetos (new). Compartido   |  CAPA 4
            |                   por todos los hilos. Lo limpia  |
            |                   el recolector de basura.        |
            +--------------------------------------------------+
            | PILAS DE HILOS    una por hilo (main, el JIT, el  |  CAPA 2
            |                   recolector, los tuyos...)       |
            +--------------------------------------------------+
            | METASPACE         la descripción de cada clase    |  CAPA 5
            |                   cargada (métodos, campos)       |
            +--------------------------------------------------+
            | CODE CACHE        el código máquina que genera el |  CAPA 7
            |                   JIT                             |
            +--------------------------------------------------+
            | lo propio de la JVM y del SO: el recolector, el  |
            | compilador, librerías nativas, buffers directos   |
            +--------------------------------------------------+

        RESERVAR NO ES USAR. La JVM pide al SO dos cosas distintas:
        - RESERVAR: "apártame este rango de direcciones". No gasta RAM.
          (Linux: mmap con PROT_NONE. Windows: VirtualAlloc con
          MEM_RESERVE.)
        - COMPROMETER (commit): "esta parte la voy a usar". Recién ahí
          cuenta como memoria del proceso, y la RAM llega al tocarla.
          (Linux: mprotect/mmap con permisos. Windows: MEM_COMMIT.)

        Medido con el rastreo de memoria nativa de la JVM
        (-XX:NativeMemoryTracking=summary) en un programa que no hace casi
        nada:

            zona          reservado     comprometido
            Java Heap      3,958 MB          10 MB
            Class          1,024 MB         0.2 MB
            Thread            15 MB         0.4 MB
            Code             242 MB         7.5 MB
            TOTAL         ~5,470 MB         ~97 MB

        Por eso el "VIRT" de un proceso java en top (Linux) asusta y no
        importa. Lo que importa es lo que está en RAM: "RES" en top, VmRSS
        en /proc/<pid>/status; en Windows, el "conjunto de trabajo" del
        Administrador de tareas.

        Desde Java se ve con los MXBeans (objetos de monitoreo que la JVM
        expone): el heap tiene un máximo reservado y una parte
        comprometida, mucho menor al arrancar.
    */
    static boolean reservedVsCommittedExample() {
        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        return heap.getCommitted() < heap.getMax()          // comprometido: lo que de verdad se pidió
                && heap.getUsed() <= heap.getCommitted();   // usado: lo que ocupan los objetos
    }

    /*
        =================================================================
        CAPA 2) LA PILA: UNA POR HILO, UN MARCO POR LLAMADA
        =================================================================

        Cada HILO tiene su propia pila (para el SO, un hilo de Java es un
        hilo del sistema; en Linux, una "tarea" del kernel, y su pila es una
        zona de memoria reservada al crearlo). En cada llamada a un método,
        la JVM pone encima un MARCO (frame) con:

            +-------------------------------+
            | variables locales y parámetros|  primitivos: el valor
            |                               |  referencias: la dirección del objeto
            +-------------------------------+
            | pila de operandos             |  los valores intermedios de
            |                               |  "a + b * c" mientras se calcula
            +-------------------------------+
            | a dónde volver al terminar    |
            +-------------------------------+

        El tamaño de cada marco lo calcula el COMPILADOR (javac anota en el
        .class cuántas locales y cuánto espacio de operandos necesita cada
        método; se verá con javap en part2_classes/ClassLoadingAndBytecode).
        Por eso:
        - CREAR un marco es mover un puntero hacia arriba, y DESTRUIRLO es
          moverlo de regreso. No hay recolector de basura en la pila: al
          terminar el método, sus locales desaparecen solas.
        - La pila es rapidísima, pero pequeña (1 MB por hilo por defecto en
          Linux y Windows de 64 bits).

        Una consecuencia importante: como cada hilo tiene SU pila, las
        variables locales NUNCA se comparten entre hilos. Una local es
        segura aunque diez hilos ejecuten el mismo método a la vez (cada
        uno tiene su copia en su marco). Los problemas de concurrencia
        empiezan cuando varios hilos tocan el mismo OBJETO del heap
        (v13_concurrency/).
    */

    /*
        =================================================================
        CAPA 3) STACKOVERFLOWERROR POR DENTRO
        =================================================================

        La pila de un hilo tiene un tamaño fijo, decidido al crearlo. Al
        final de esa zona la JVM deja unas PÁGINAS DE GUARDA: páginas sin
        permiso de acceso. Cuando la recursión llega ahí, el procesador
        intenta escribir en una página prohibida y el SO avisa al proceso
        (en Linux, la señal SIGSEGV; en Windows, una excepción de "guard
        page"). La JVM atrapa ese aviso, ve que fue en la zona de guarda, y
        lo convierte en un StackOverflowError normal de Java. Sin esas
        páginas, la pila invadiría la memoria vecina.

        ¿Cuántas llamadas caben? NO es un número fijo. Depende del tamaño de
        cada marco, y el mismo método tiene marcos más grandes mientras se
        INTERPRETA que después de que el JIT lo compila. Medido con un
        método recursivo sin locales:

            pila           profundidad
            256 KB           ~5,200
            1 MB (default)   ~11,000 (main, recién arrancado) a ~25,000
            4 MB             ~259,000 (ya compilado por el JIT)

        El tamaño se cambia:
        - para todos los hilos: java -Xss4m Programa;
        - para un hilo: new Thread(grupo, tarea, nombre, tamañoDePila). El
          JDK dice que la JVM PUEDE ignorar este valor; HotSpot (la JVM
          normal) lo respeta en Linux y Windows.

        Subir la pila es un parche. Si una recursión puede ser profunda
        (recorrer un árbol que viene de afuera, una lista enlazada), la
        solución real es un ciclo con una pila propia en el heap (como en
        v11_patterns/part3_behavioral/Iterator, CAPA 2).
    */
    private static int depth;

    private static void recurse() {
        depth++;
        recurse();
    }

    static int maxDepthWithStack(long stackBytes) throws InterruptedException {
        int[] result = new int[1];
        Thread thread = new Thread(null, () -> {
            depth = 0;
            try {
                recurse();
            } catch (StackOverflowError e) {
                result[0] = depth;                          // hasta dónde llegó antes de la página de guarda
            }
        }, "deep-recursion", stackBytes);
        thread.start();
        thread.join();                                      // join: lo que escribió el hilo ya es visible aquí
        return result[0];
    }

    static boolean stackSizeExample() throws InterruptedException {
        int small = maxDepthWithStack(256 * 1024);
        int large = maxDepthWithStack(4 * 1024 * 1024);
        return small > 1_000 && large > small * 4;          // 16 veces la pila: muchas más llamadas
    }

    /*
        =================================================================
        CAPA 4) EL HEAP: COMPARTIDO, Y POR QUÉ NEW ES BARATO
        =================================================================

        Todo lo que se crea con new (objetos y arreglos) vive en el HEAP.
        Hay UN heap por proceso, compartido por todos los hilos: por eso un
        objeto creado en un hilo se puede pasar a otro.

        Su tamaño:
        - -Xmx: el máximo. Si no lo pones, la JVM usa 1/4 de la RAM física
          (medido: 3,958 MB máximo con 15,828 MB de RAM).
        - -Xms: lo que compromete al arrancar.
        - En contenedores (Docker, Kubernetes), la JVM lee el límite del
          contenedor (de los cgroups de Linux) en vez de la RAM de la
          máquina, y se ajusta con -XX:MaxRAMPercentage=75 (un porcentaje
          en vez de un número fijo).

        ¿Por qué new es barato? Cada hilo recibe un pedazo del heap para él
        solo, un TLAB (thread-local allocation buffer). Crear un objeto ahí
        es: "el siguiente objeto empieza donde termina el anterior", mover
        un puntero y escribir la cabecera. Sin pedir nada al SO y sin
        bloquear a otros hilos. Mucho más rápido que el malloc de C.

        El costo real no está en crear, sino en LIMPIAR: el recolector de
        basura tiene que encontrar qué objetos siguen vivos. Eso es la
        siguiente guía (part1_memory/GarbageCollector).
    */
    static boolean heapLimitExample() {
        long maxHeap = Runtime.getRuntime().maxMemory();
        long physicalRam = ((com.sun.management.OperatingSystemMXBean)
                ManagementFactory.getOperatingSystemMXBean()).getTotalMemorySize();
        return maxHeap > 0 && maxHeap < physicalRam;        // por defecto, una fracción de la RAM
    }

    /*
        =================================================================
        CAPA 5) QUÉ VIVE DÓNDE
        =================================================================

            Cosa                                    Dónde
            --------------------------------------  -------------------------
            local o parámetro primitivo (int x)     pila, el valor
            local o parámetro referencia (Order o)  pila, la dirección;
                                                    el objeto, en el heap
            campo de un objeto (this.total)         dentro del objeto, heap
            elementos de un arreglo                 dentro del arreglo, heap
            campo static                            heap (dentro del objeto
                                                    Class de su clase)
            literal "hola"                          heap (en la tabla de
                                                    Strings internados)
            la descripción de la clase: métodos,    METASPACE (memoria
            campos, constantes                      nativa, fuera del heap)
            código máquina del JIT                  CODE CACHE (nativa)
            locales capturadas por una lambda       se COPIAN dentro del
                                                    objeto lambda, en el heap

        Dos notas:
        - Los static NO están en una "zona estática" aparte: desde Java 7
          viven en el heap, dentro del objeto Class. Por eso un static que
          guarda una lista que nunca se vacía es la fuga de memoria más
          común (GarbageCollector).
        - Metaspace está FUERA del heap. -Xmx no lo limita: crece con cada
          clase cargada (frameworks que generan clases al ejecutar, como
          los proxies de v11_patterns/part2_structural/Proxy, CAPA 5). Se
          limita con -XX:MaxMetaspaceSize.

        La JVM nombra estas zonas en sus "memory pools". Con el recolector
        por defecto (G1), el heap aparece como "G1 Eden Space", "G1
        Survivor Space" y "G1 Old Gen" (las generaciones, en la siguiente
        guía), y fuera del heap están "Metaspace" y los "CodeHeap".
    */
    static boolean memoryAreasExample() {
        List<MemoryPoolMXBean> pools = ManagementFactory.getMemoryPoolMXBeans();
        boolean heapPools = pools.stream().anyMatch(pool -> pool.getType() == MemoryType.HEAP);
        boolean metaspaceOutsideHeap = pools.stream().anyMatch(pool -> pool.getName().equals("Metaspace")
                && pool.getType() == MemoryType.NON_HEAP);
        return heapPools && metaspaceOutsideHeap;
    }

    /*
        =================================================================
        CAPA 6) CUÁNTO PESA DE VERDAD UN OBJETO
        =================================================================

        Un objeto en el heap no es solo sus campos. Lleva:

            +---------------------+
            | marca (8 bytes)     |  hash, estado de bloqueo, edad para el GC
            | clase (4 bytes)     |  a qué clase pertenece (comprimido)
            +---------------------+  = cabecera de 12 bytes
            | campos              |
            | relleno             |  todo objeto mide un múltiplo de 8
            +---------------------+

        Los arreglos agregan 4 bytes con su longitud.

        Las REFERENCIAS miden 4 bytes, no 8, aunque la máquina sea de 64
        bits: con un heap menor a 32 GB, la JVM las guarda comprimidas
        ("compressed oops"). Por eso pasar de -Xmx31g a -Xmx33g puede DAR
        MENOS espacio útil: todas las referencias pasan a medir 8.

        Medido (bytes reservados por objeto, con la API de la JVM que cuenta
        lo que reserva cada hilo):

            objeto                      normal   con cabecera compacta
            new Object()                  16          8
            Integer                       16         16
            record Point(int x, int y)    24         16
            int[0]                        16         16
            int[10]                       56         56

        La "cabecera compacta" (-XX:+UseCompactObjectHeaders, oficial desde
        Java 25, aún no viene activa por defecto) junta marca y clase en 8
        bytes. Ahorra memoria en programas con muchos objetos pequeños.

        La consecuencia práctica: un millón de enteros
        - int[1_000_000]: 4 MB;
        - List<Integer> con un millón: 4 MB del arreglo de referencias + 16
          MB de objetos Integer = ~20 MB, CINCO veces más, y además dispersos
          por el heap (peor para la caché del procesador).
        Por eso existen IntStream y los arreglos primitivos
        (v5_collections/, v7_functional/).

        La check calcula el tamaño esperado con la fórmula (cabecera +
        campos, redondeado a 8) según cómo arrancó la JVM, y lo compara con
        lo medido.
    */
    record Point(int x, int y) {
    }

    private static final ThreadMXBean THREADS = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    private static final Object[] SINK = new Object[100_000];   // mantiene vivos los objetos medidos

    static long bytesPerObject(IntFunction<Object> factory) {
        for (int i = 0; i < SINK.length; i++) {
            SINK[i] = factory.apply(i);                     // calentar: que el código ya esté cargado
        }
        long before = THREADS.getCurrentThreadAllocatedBytes();
        for (int i = 0; i < SINK.length; i++) {
            SINK[i] = factory.apply(i);
        }
        long after = THREADS.getCurrentThreadAllocatedBytes();
        return Math.round((after - before) / (double) SINK.length);
    }

    static long alignTo8(long bytes) {
        return (bytes + 7) / 8 * 8;
    }

    static boolean objectSizeExample() {
        boolean compact = Boolean.parseBoolean(ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class)
                .getVMOption("UseCompactObjectHeaders").getValue());
        long header = compact ? 8 : 12;

        boolean pointMatches = bytesPerObject(i -> new Point(i, i)) == alignTo8(header + 4 + 4);
        boolean integerMatches = bytesPerObject(i -> Integer.valueOf(i + 1_000)) == alignTo8(header + 4);   // > 127: sin caché
        boolean arrayMatches = bytesPerObject(i -> new int[10]) == alignTo8(header + 4 + 10 * 4);
        Arrays.fill(SINK, null);
        return pointMatches && integerMatches && arrayMatches;
    }

    /*
        =================================================================
        CAPA 7) NEW NO SIEMPRE LLEGA AL HEAP: ANÁLISIS DE ESCAPE
        =================================================================

        "Los objetos van al heap" es la regla del LENGUAJE. La JVM puede
        hacer algo mejor si nadie se da cuenta. Cuando el JIT compila un
        método caliente, revisa cada new: si el objeto NO ESCAPA (no se
        guarda en un campo, no se devuelve, no se pasa a un método que no se
        pudo copiar adentro), no hace falta que exista como objeto. El JIT
        guarda sus campos directo en registros del procesador o en la pila:
        se llama "reemplazo escalar".

        Medido: un método que crea un millón de Point temporales para sumar
        sus campos (24 MB si todos fueran al heap):

            vuelta 1 (interpretado / recién compilado)   ~10 a 16 MB
            vuelta 3 en adelante (compilado por el JIT)  ~0.2 MB

        Es decir: casi cero objetos reales.

        Qué significa para ti: los objetos pequeños y temporales (un record
        para devolver dos valores, un Optional, un iterador) suelen ser más
        baratos de lo que parecen. NO significa escribir código raro para
        "ayudar" al JIT: depende de que el método se compile, de que la
        llamada se pueda copiar adentro, y cambia entre versiones. Se
        escribe claro y se mide (part3_performance/Performance).
    */
    static long sumOfTemporaryPoints(int count) {
        long sum = 0;
        for (int i = 0; i < count; i++) {
            Point point = new Point(i, i + 1);              // no escapa: solo se leen sus campos
            sum += point.x() + point.y();
        }
        return sum;
    }

    static boolean escapeAnalysisExample() {
        long naiveBytes = 1_000_000L * bytesPerObject(i -> new Point(i, i));
        Arrays.fill(SINK, null);
        for (int round = 0; round < 30; round++) {
            long before = THREADS.getCurrentThreadAllocatedBytes();
            sumOfTemporaryPoints(1_000_000);
            long allocated = THREADS.getCurrentThreadAllocatedBytes() - before;
            if (allocated < naiveBytes / 10) {
                return true;                                // el JIT ya eliminó (casi) todos los new
            }
        }
        return false;
    }

    /*
        =================================================================
        CAPA 8) LOS OUTOFMEMORYERROR Y EL QUE MATA EL SISTEMA OPERATIVO
        =================================================================

        OutOfMemoryError no es UN error: el mensaje dice qué zona se acabó.

            mensaje                                 qué pasó
            -------------------------------------   ---------------------------
            Java heap space                         el heap llegó a -Xmx con
                                                    objetos vivos
            GC overhead limit exceeded              (recolector Parallel) el GC
                                                    se pasa casi todo el tiempo
                                                    limpiando y libera casi nada
            Metaspace                               demasiadas clases (CAPA 5)
            unable to create native thread          el SO no dio otro hilo
                                                    (límite de procesos o de
                                                    memoria; Linux: ulimit -u)
            Direct buffer memory                    buffers fuera del heap
                                                    (los usa la red y NIO)
            Requested array size exceeds VM limit   un arreglo de casi
                                                    Integer.MAX_VALUE elementos

        Qué hacer:
        - NO atraparlo para seguir como si nada: el programa quedó en un
          estado incierto (cualquier new pudo fallar a la mitad de algo).
        - Arrancar los servidores con -XX:+HeapDumpOnOutOfMemoryError: al
          fallar, la JVM escribe un archivo con TODO el heap, que se abre
          con una herramienta de análisis para ver qué lo llenó.

        Y el caso más confuso: en un contenedor con 1 GB de límite, si el
        TOTAL del proceso (heap + pilas + metaspace + code cache + nativa)
        pasa de 1 GB, NO hay OutOfMemoryError. El kernel de Linux mata el
        proceso de golpe (el "OOM killer"): sin stack trace, sin log de
        Java, solo "Killed" y el código de salida 137. Por eso -Xmx nunca
        se pone igual al límite del contenedor: se deja espacio para todo lo
        que no es heap (por eso MaxRAMPercentage=75 y no 100).
    */

    /*
        =================================================================
        CAPA 9) ERRORES COMUNES
        =================================================================

        1) Alarmarse por la memoria virtual (VIRT) del proceso: casi toda es
           reservada, no usada (CAPA 1).

        2) Compartir objetos entre hilos creyendo que "son locales": lo
           local es la referencia, el objeto está en el heap compartido
           (CAPA 2).

        3) Subir -Xss para tapar una recursión que depende del tamaño de
           los datos, en vez de convertirla en un ciclo (CAPA 3).

        4) Creer que -Xmx es toda la memoria del proceso (CAPAS 5 y 8).

        5) Colecciones de wrappers (List<Integer>) para millones de números
           (CAPA 6).

        6) Escribir código rebuscado para "evitar new" sin medir (CAPA 7).

        7) Atrapar OutOfMemoryError y seguir; poner -Xmx igual al límite
           del contenedor (CAPA 8).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - La JVM es un proceso: reserva mucho espacio virtual y compromete
          poco; lo que importa es la memoria residente (RSS).
        - Pila: una por hilo, un marco por llamada, se libera sola; las
          locales nunca se comparten entre hilos.
        - StackOverflowError: la recursión llegó a las páginas de guarda; la
          profundidad depende del marco (interpretado o compilado) y de
          -Xss.
        - Heap: uno, compartido; por defecto 1/4 de la RAM; new es barato
          (TLAB), lo caro es limpiar.
        - Statics y literales en el heap; clases en Metaspace (fuera de
          -Xmx).
        - Objeto = cabecera 12 bytes + campos, múltiplo de 8; referencias de
          4 bytes bajo 32 GB.
        - El JIT puede eliminar objetos que no escapan.
        - Cada OutOfMemoryError dice qué zona se acabó; en contenedores, el
          kernel puede matar el proceso antes.
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
