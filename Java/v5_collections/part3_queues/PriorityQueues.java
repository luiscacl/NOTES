package v5_collections.part3_queues;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.PriorityQueue;
import java.util.Random;

/*
    =====================================================================
    COLAS DE PRIORIDAD: PRIORITYQUEUE Y EL HEAP — DE CERO A NIVEL TÉCNICO
    =====================================================================

    Ruta de collections/: guía 7 de 10  (part3_queues)
    Anterior:  part3_queues/QueuesAndStacks
    Siguiente: part4_tools/Sorting

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.

    Índice:
        CAPA 1)  Qué es una cola de prioridad
        CAPA 2)  PriorityQueue básica: el menor sale primero
        CAPA 3)  El heap: un árbol guardado en un arreglo
        CAPA 4)  Cómo entra y cómo sale un elemento (O(log n))
        CAPA 5)  Un heap hecho a mano
        CAPA 6)  La trampa: recorrerla NO da el orden
        CAPA 7)  Max-heap y prioridades propias con Comparator
        CAPA 8)  Empates: PriorityQueue no respeta el orden de llegada
        CAPA 9)  Top K: el heap de tamaño fijo
        CAPA 10) Mezclar listas ordenadas
        CAPA 11) No cambies un elemento que ya está dentro
        CAPA 12) Cómo elegir
        CAPA 13) Errores comunes
        RESUMEN FINAL
*/
public class PriorityQueues {

    public static void main(String[] args) {
        check(basicExample(), "CAPA 2");
        check(heapLayoutExample(), "CAPA 3");
        check(siftExample(), "CAPA 4");
        check(handMadeHeapExample(), "CAPA 5");
        check(iterationTrapExample(), "CAPA 6");
        check(comparatorExample(), "CAPA 7");
        check(tieBreakExample(), "CAPA 8");
        check(topKExample(), "CAPA 9");
        check(mergeExample(), "CAPA 10");
        check(mutationExample(), "CAPA 11");
        System.out.println("PriorityQueues: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) QUÉ ES UNA COLA DE PRIORIDAD
        =================================================================

        En una cola normal sale el que llegó primero (QueuesAndStacks,
        CAPA 1). En una COLA DE PRIORIDAD sale el MÁS IMPORTANTE, sin
        importar cuándo llegó.

        Como la sala de urgencias de un hospital: no se atiende por orden
        de llegada, sino por gravedad. Un paciente grave que acaba de
        entrar pasa antes que uno leve que lleva una hora esperando.

        Lo que la hace especial: NO mantiene todo ordenado. Solo garantiza
        que el más prioritario esté siempre a la mano. Ordenar todo cuesta
        más, y para "dame el siguiente más importante" no hace falta (lo
        verás en la CAPA 3).

        Para qué se usa (de tu antigua JavaDataStructuresGuide):
        - planificar tareas por urgencia o por hora (scheduling);
        - "los K mayores" o "el K-ésimo mayor" (CAPA 9);
        - mezclar varias secuencias ya ordenadas (CAPA 10);
        - ruta más corta en grafos con pesos, algoritmo de Dijkstra
          (algorithms/TreesAndGraphs);
        - algoritmos "greedy": tomar siempre la mejor opción disponible.
    */

    /*
        =================================================================
        CAPA 2) PRIORITYQUEUE BÁSICA: EL MENOR SALE PRIMERO
        =================================================================

        PriorityQueue implementa Queue, así que usa los métodos de siempre
        (QueuesAndStacks, CAPA 2):
            offer(x)   agrega                       O(log n)
            peek()     mira el más prioritario      O(1)
            poll()     saca el más prioritario      O(log n)
        (y null si está vacía).

        "Más prioritario" significa, por defecto, el MENOR según el orden
        natural (Comparable; v2_oop/part4_advanced_types/ObjectContract,
        CAPA 9): el número más chico, el texto alfabéticamente primero.
        A esto se le llama MIN-HEAP. Para el mayor, CAPA 7.

        Igual que TreeSet (Sets, CAPA 5):
        - los elementos deben poder compararse, o lanza ClassCastException;
        - no acepta null.
        A diferencia de TreeSet, SÍ acepta repetidos.
    */
    static boolean basicExample() {
        PriorityQueue<Integer> minHeap = new PriorityQueue<>();
        minHeap.offer(30);
        minHeap.offer(10);
        minHeap.offer(20);
        minHeap.offer(5);
        minHeap.offer(10);                          // repetidos permitidos

        boolean peekIsMin = minHeap.peek() == 5 && minHeap.size() == 5;

        List<Integer> pollOrder = new ArrayList<>();
        while (!minHeap.isEmpty()) {
            pollOrder.add(minHeap.poll());          // cada poll saca el menor que quede
        }

        return peekIsMin && pollOrder.equals(List.of(5, 10, 10, 20, 30));
    }

    /*
        =================================================================
        CAPA 3) EL HEAP: UN ÁRBOL GUARDADO EN UN ARREGLO
        =================================================================

        Por dentro, PriorityQueue es un HEAP BINARIO ("montículo"): un
        árbol donde cada nodo tiene hasta dos hijos y cumple UNA sola
        regla:

            cada padre es MENOR O IGUAL que sus hijos

        Consecuencia: el menor de todos está siempre en la RAÍZ. Por eso
        peek() es O(1). Pero fíjate en lo que la regla NO dice: nada sobre
        hermanos ni primos. Un heap está "medio ordenado", y por eso
        mantenerlo es más barato que mantener todo ordenado.

        Insertando 9, 4, 7, 1, 3, 10, 8 queda así:

                    1
                  /   \
                 3     7
                / \   / \
               9   4 10  8

        Revisa la regla: 1 <= 3 y 7; 3 <= 9 y 4; 7 <= 10 y 8. Pero 9 está
        arriba de 4 en el arreglo y 9 > 4: no importa, no son padre e hijo.

        El truco: el árbol NO usa nodos con referencias. Siempre se llena
        nivel por nivel, de izquierda a derecha (un árbol "completo"), así
        que cabe en un ARREGLO sin huecos, leyéndolo por niveles:

            posición:  0  1  2  3  4  5   6
            valor:   [ 1, 3, 7, 9, 4, 10, 8 ]

        Y las relaciones se CALCULAN con aritmética:
            hijo izquierdo de i  = 2*i + 1
            hijo derecho de i    = 2*i + 2
            padre de i           = (i - 1) / 2      (división entera)
        Ejemplo: el padre de la posición 4 (el 4) es (4-1)/2 = 1 (el 3).

        Por qué así: sin nodos ni referencias, ocupa poca memoria y se
        aprovecha bien la caché (la misma razón de ArrayDeque;
        QueuesAndStacks, CAPA 4). Y crece como ArrayList cuando se llena.
    */
    static boolean heapLayoutExample() {
        PriorityQueue<Integer> heap = new PriorityQueue<>();
        for (int value : new int[] {9, 4, 7, 1, 3, 10, 8}) {
            heap.offer(value);
        }
        Integer[] layout = heap.toArray(new Integer[0]);    // el arreglo interno, tal cual

        boolean parentRuleHolds = true;
        for (int i = 1; i < layout.length; i++) {
            parentRuleHolds &= layout[(i - 1) / 2] <= layout[i];   // cada uno contra su padre
        }

        return Arrays.equals(layout, new Integer[] {1, 3, 7, 9, 4, 10, 8}) && parentRuleHolds;
    }

    /*
        =================================================================
        CAPA 4) CÓMO ENTRA Y CÓMO SALE UN ELEMENTO (O(LOG N))
        =================================================================

        ENTRAR (offer) — "flotar hacia arriba" (sift up):
        1) Se pone al FINAL del arreglo (el siguiente hueco del árbol).
        2) Mientras sea menor que su padre, se intercambian.
        Ejemplo: offer(2) en el heap de la CAPA 3:
            entra en la posición 7, su padre es la 3 (el 9): 2 < 9, sube;
            ahora en la 3, su padre es la 1 (el 3): 2 < 3, sube;
            ahora en la 1, su padre es la 0 (el 1): 2 > 1, se queda.
            [1, 2, 7, 3, 4, 10, 8, 9]

        SALIR (poll) — "hundirse" (sift down):
        1) Se guarda la raíz (el menor) para devolverla.
        2) El ÚLTIMO elemento pasa a la raíz (así no quedan huecos).
        3) Mientras sea mayor que alguno de sus hijos, se intercambia con
           el MENOR de los dos (el menor, para que la regla se cumpla con
           el otro hijo también).
        Ejemplo: poll() en el heap de la CAPA 3:
            sale el 1; el 8 (último) pasa a la raíz;
            hijos 3 y 7: el menor es 3, se intercambian;
            en la posición 1, hijos 9 y 4: el menor es 4, se intercambian;
            en la posición 4 ya no tiene hijos.
            [3, 4, 7, 9, 8, 10]

        Cada paso sube o baja UN nivel, y un árbol completo de n elementos
        tiene unos log2(n) niveles: un millón de elementos son ~20 niveles.
        Por eso offer y poll son O(log n).

        Comparado con las alternativas:
        - una lista que se mantiene ordenada: sacar es O(1) pero insertar
          es O(n) (hay que mover los elementos; Lists, CAPA 4);
        - una lista sin ordenar: insertar O(1) pero sacar el menor O(n);
        - un TreeSet: O(log n) en todo, pero no admite repetidos y usa un
          nodo con referencias por elemento.
        El heap es el equilibrio justo para "sacar siempre el menor".

        Extra: new PriorityQueue<>(coleccion) construye el heap de golpe en
        O(n) (no n inserciones de O(log n)), acomodando desde las hojas
        hacia arriba.
    */
    static boolean siftExample() {
        PriorityQueue<Integer> afterOffer = new PriorityQueue<>();
        PriorityQueue<Integer> afterPoll = new PriorityQueue<>();
        for (int value : new int[] {9, 4, 7, 1, 3, 10, 8}) {
            afterOffer.offer(value);
            afterPoll.offer(value);
        }
        afterOffer.offer(2);                        // sube dos niveles
        int smallest = afterPoll.poll();            // el 8 baja dos niveles

        return Arrays.equals(afterOffer.toArray(new Integer[0]), new Integer[] {1, 2, 7, 3, 4, 10, 8, 9})
                && smallest == 1
                && Arrays.equals(afterPoll.toArray(new Integer[0]), new Integer[] {3, 4, 7, 9, 8, 10});
    }

    /*
        =================================================================
        CAPA 5) UN HEAP HECHO A MANO
        =================================================================

        Para que no quede como magia, este es un min-heap de int completo,
        con exactamente los pasos de la CAPA 4. No lo usarías en un
        proyecto (para eso está PriorityQueue), pero es una pregunta de
        entrevista clásica y, una vez escrito, el heap deja de ser una caja
        negra.

        El ejemplo lo compara con PriorityQueue usando 1,000 números
        aleatorios (con semilla fija, para que siempre sean los mismos):
        los dos deben sacarlos en el mismo orden.
    */
    static final class IntMinHeap {
        private int[] values = new int[8];
        private int size;

        void offer(int value) {
            if (size == values.length) {
                values = Arrays.copyOf(values, size * 2);   // crece como ArrayList
            }
            int index = size++;
            values[index] = value;
            while (index > 0 && values[(index - 1) / 2] > values[index]) {   // sift up
                swap(index, (index - 1) / 2);
                index = (index - 1) / 2;
            }
        }

        int poll() {
            if (size == 0) {
                throw new NoSuchElementException("El heap está vacío");
            }
            int smallest = values[0];
            values[0] = values[--size];             // el último pasa a la raíz
            int index = 0;
            while (true) {                          // sift down
                int left = 2 * index + 1;
                int right = left + 1;
                int smaller = index;
                if (left < size && values[left] < values[smaller]) {
                    smaller = left;
                }
                if (right < size && values[right] < values[smaller]) {
                    smaller = right;
                }
                if (smaller == index) {
                    return smallest;                // ya es menor o igual que sus hijos
                }
                swap(index, smaller);
                index = smaller;
            }
        }

        boolean isEmpty() {
            return size == 0;
        }

        private void swap(int i, int j) {
            int temporary = values[i];
            values[i] = values[j];
            values[j] = temporary;
        }
    }

    static boolean handMadeHeapExample() {
        Random random = new Random(42);
        IntMinHeap mine = new IntMinHeap();
        PriorityQueue<Integer> jdk = new PriorityQueue<>();
        for (int i = 0; i < 1_000; i++) {
            int value = random.nextInt(10_000);
            mine.offer(value);
            jdk.offer(value);
        }

        while (!jdk.isEmpty()) {
            if (mine.poll() != jdk.poll()) {
                return false;
            }
        }
        return mine.isEmpty();
    }

    /*
        =================================================================
        CAPA 6) LA TRAMPA: RECORRERLA NO DA EL ORDEN
        =================================================================

        Como el heap solo está "medio ordenado" (CAPA 3), todo lo que lee
        el arreglo interno lo muestra en ese orden de heap, NO ordenado:
        - el for-each y el iterador;
        - toString() (lo que ves al imprimirla);
        - toArray() y stream().

        Tu antigua JavaDataStructuresGuide imprimía el "Top 3" directamente del
        heap; salía bien por casualidad. Con los números 9, 4, 7, 1, 3,
        10, 8 agregados uno a uno con offer, imprimir el heap da
        [1, 3, 7, 9, 4, 10, 8]: el 9 aparece antes que el 4. (Si se crea de
        una vez desde una lista, como abajo, el arreglo interno sale
        distinto, [1, 3, 7, 4, 9, 10, 8]: tampoco está ordenado.)

        Para obtener los elementos en orden:
        - sácalos con poll() uno por uno (la cola se vacía), o
        - copia y ordena: lista = new ArrayList<>(pq); lista.sort(...).

        Y otro costo escondido: contains(x) y remove(x) NO usan el heap:
        recorren el arreglo, O(n). El heap solo es rápido para el más
        prioritario.
    */
    static boolean iterationTrapExample() {
        PriorityQueue<Integer> heap = new PriorityQueue<>(List.of(9, 4, 7, 1, 3, 10, 8));

        List<Integer> iterated = new ArrayList<>(heap);     // lo que da el for-each: orden de heap
        boolean notSorted = !iterated.equals(List.of(1, 3, 4, 7, 8, 9, 10));

        List<Integer> sortedCopy = new ArrayList<>(heap);
        Collections.sort(sortedCopy);                       // la forma de verlos en orden sin vaciarla

        return notSorted && iterated.get(0) == 1            // lo único garantizado: el primero es el menor
                && sortedCopy.equals(List.of(1, 3, 4, 7, 8, 9, 10))
                && heap.size() == 7;
    }

    /*
        =================================================================
        CAPA 7) MAX-HEAP Y PRIORIDADES PROPIAS CON COMPARATOR
        =================================================================

        Para que salga el MAYOR primero (max-heap), dale el orden al revés
        (como en tu antiguo CheatSheet):
            new PriorityQueue<>(Collections.reverseOrder())
            new PriorityQueue<>(Comparator.reverseOrder())      // lo mismo

        Para objetos propios, pasa un Comparator que diga qué es "más
        prioritario" (ObjectContract, CAPA 10; part4_tools/Sorting a fondo):
            new PriorityQueue<>(Comparator.comparingInt(Patient::severity).reversed())

        Tu antiguo CheatSheet comparaba arreglos con Integer.compare(a[0], b[0]).
        Eso es correcto, y es importante: la forma "corta" que se ve en
        internet,
            (a, b) -> b - a          // ERROR escondido
        está MAL. La resta de int puede DESBORDARSE
        (v1_fundamentals/part1_first_steps/Operators): 1 - Integer.MIN_VALUE
        no cabe en un int, da la vuelta y sale negativo, así que el
        comparador dice que MIN_VALUE es "mayor" que 1. El max-heap
        devuelve el número MÁS CHICO posible como si fuera el mayor.
        Integer.compare nunca desborda: úsalo siempre (o los métodos de
        Comparator).
    */
    record Patient(String name, int severity) { }

    static boolean comparatorExample() {
        PriorityQueue<Integer> maxHeap = new PriorityQueue<>(Collections.reverseOrder());
        maxHeap.addAll(List.of(30, 10, 20, 5));

        PriorityQueue<Patient> emergencyRoom =
                new PriorityQueue<>(Comparator.comparingInt(Patient::severity).reversed());
        emergencyRoom.offer(new Patient("tos", 1));
        emergencyRoom.offer(new Patient("fractura", 7));
        emergencyRoom.offer(new Patient("infarto", 10));    // llegó al último, sale primero

        Comparator<Integer> subtraction = (a, b) -> b - a;  // parece un max-heap...
        PriorityQueue<Integer> broken = new PriorityQueue<>(subtraction);
        broken.addAll(List.of(1, Integer.MIN_VALUE, 5));

        return maxHeap.poll() == 30
                && emergencyRoom.poll().name().equals("infarto")
                && emergencyRoom.poll().name().equals("fractura")
                && broken.poll() == Integer.MIN_VALUE;      // ...y devuelve el menor de todos como "el mayor"
    }

    /*
        =================================================================
        CAPA 8) EMPATES: PRIORITYQUEUE NO RESPETA EL ORDEN DE LLEGADA
        =================================================================

        Si dos elementos tienen la MISMA prioridad, ¿cuál sale primero?
        Ninguno en particular. Los intercambios del heap (CAPA 4) mueven
        los elementos sin importar quién llegó antes. PriorityQueue no es
        "estable".

        Con seis tareas de la misma prioridad, insertadas a, b, c, d, e, f,
        salen a, f, e, d, c, b. En un sistema de tickets, eso significa
        que alguien que llegó antes espera más con la misma urgencia:
        injusto, y difícil de depurar.

        La solución estándar: un DESEMPATE con un número de secuencia que
        crece con cada llegada. Primero se compara la prioridad; si
        empatan, el número más chico (el que llegó antes) gana:
            Comparator.comparingInt(Task::priority)
                      .thenComparingLong(Task::sequence)
        Así ya no hay empates reales y el orden queda determinado.
    */
    record Task(String name, int priority, long sequence) { }

    static final class Scheduler {
        private final PriorityQueue<Task> tasks = new PriorityQueue<>(
                Comparator.comparingInt(Task::priority).thenComparingLong(Task::sequence));
        private long nextSequence;

        void submit(String name, int priority) {
            tasks.offer(new Task(name, priority, nextSequence++));   // 1 = más urgente
        }

        String runAll() {
            StringBuilder order = new StringBuilder();
            while (!tasks.isEmpty()) {
                order.append(tasks.poll().name());
            }
            return order.toString();
        }
    }

    static boolean tieBreakExample() {
        PriorityQueue<Task> unstable = new PriorityQueue<>(Comparator.comparingInt(Task::priority));
        for (String name : List.of("a", "b", "c", "d", "e", "f")) {
            unstable.offer(new Task(name, 1, 0));
        }
        StringBuilder unstableOrder = new StringBuilder();
        while (!unstable.isEmpty()) {
            unstableOrder.append(unstable.poll().name());
        }

        Scheduler scheduler = new Scheduler();
        for (String name : List.of("a", "b", "c", "d", "e", "f")) {
            scheduler.submit(name, 1);
        }
        Scheduler mixed = new Scheduler();
        mixed.submit("a", 1);
        mixed.submit("b", 2);
        mixed.submit("c", 1);
        mixed.submit("d", 2);
        mixed.submit("e", 1);

        return !unstableOrder.toString().equals("abcdef")       // misma prioridad: orden revuelto
                && scheduler.runAll().equals("abcdef")          // con secuencia: orden de llegada
                && mixed.runAll().equals("acebd");              // urgentes primero, cada grupo en orden
    }

    /*
        =================================================================
        CAPA 9) TOP K: EL HEAP DE TAMAÑO FIJO
        =================================================================

        Problema clásico: de n números, quiero los K MAYORES (los 3
        productos más vendidos, las 10 calificaciones más altas).

        Opción obvia: ordenar todo y tomar los últimos K. O(n log n), y
        necesitas todos los datos en memoria.

        Opción con heap (la de tu antigua JavaDataStructuresGuide): un MIN-heap
        que nunca pase de K elementos.
        - Metes cada número.
        - Si el heap pasa de K, sacas el MENOR.
        Al final quedan los K mayores. Por qué un min-heap para los
        MAYORES: en la raíz queda el más chico de los K finalistas, el
        primero que debe salir cuando llega alguien mejor. Es el "portero"
        del grupo.

        Costo: cada operación es sobre un heap de tamaño K, O(n log K).
        Con K = 10 y mil millones de datos, mucho mejor que ordenar, y
        funciona con datos que llegan de uno en uno (un archivo enorme,
        un flujo) porque solo guarda K.

        Bonus: el K-ÉSIMO MAYOR es justo la raíz al terminar: peek().
        (Es el ejercicio 10 de tu antiguo CheatSheet.)
    */
    static List<Integer> topK(int[] values, int k) {
        PriorityQueue<Integer> finalists = new PriorityQueue<>();    // min-heap
        for (int value : values) {
            finalists.offer(value);
            if (finalists.size() > k) {
                finalists.poll();                                     // sale el más chico de los finalistas
            }
        }
        List<Integer> result = new ArrayList<>(finalists);
        result.sort(Comparator.reverseOrder());                      // CAPA 6: el heap no viene ordenado
        return result;
    }

    static int kthLargest(int[] values, int k) {
        PriorityQueue<Integer> finalists = new PriorityQueue<>();
        for (int value : values) {
            finalists.offer(value);
            if (finalists.size() > k) {
                finalists.poll();
            }
        }
        return finalists.peek();                                      // el portero es el k-ésimo mayor
    }

    static boolean topKExample() {
        int[] values = {9, 4, 7, 1, 3, 10, 8};
        return topK(values, 3).equals(List.of(10, 9, 8))
                && kthLargest(values, 3) == 8
                && kthLargest(values, 1) == 10;
    }

    /*
        =================================================================
        CAPA 10) MEZCLAR LISTAS ORDENADAS
        =================================================================

        Problema real: tienes los registros (logs) de varios servidores,
        cada archivo ya ordenado por hora, y quieres UNA sola línea de
        tiempo ordenada. Juntar todo y ordenar funciona, pero desperdicia
        que cada lista ya venía ordenada.

        Con un heap: guarda en él solo el PRIMER elemento pendiente de
        cada lista (con una referencia a "el resto de esa lista").
        - poll() te da el menor de todos los primeros: ese es el siguiente
          de la mezcla.
        - Metes al heap el siguiente elemento de la lista de donde salió.
        El heap nunca tiene más de K elementos (uno por lista), así que
        mezclar n elementos de K listas cuesta O(n log K).

        Así se mezclan los bloques en el ordenamiento de archivos que no
        caben en memoria, y los resultados de varias bases de datos.
    */
    record Head(int value, Iterator<Integer> rest) { }

    static List<Integer> mergeSorted(List<List<Integer>> sortedLists) {
        PriorityQueue<Head> heads = new PriorityQueue<>(Comparator.comparingInt(Head::value));
        for (List<Integer> list : sortedLists) {
            Iterator<Integer> it = list.iterator();
            if (it.hasNext()) {
                heads.offer(new Head(it.next(), it));       // el primero de cada lista
            }
        }

        List<Integer> merged = new ArrayList<>();
        while (!heads.isEmpty()) {
            Head smallest = heads.poll();
            merged.add(smallest.value());
            if (smallest.rest().hasNext()) {
                heads.offer(new Head(smallest.rest().next(), smallest.rest()));   // el siguiente de esa misma lista
            }
        }
        return merged;
    }

    static boolean mergeExample() {
        List<Integer> serverA = List.of(1, 4, 9);           // horas de los eventos de cada servidor
        List<Integer> serverB = List.of(2, 3, 10, 11);
        List<Integer> serverC = List.of(5);
        return mergeSorted(List.of(serverA, serverB, serverC, List.of()))
                .equals(List.of(1, 2, 3, 4, 5, 9, 10, 11));
    }

    /*
        =================================================================
        CAPA 11) NO CAMBIES UN ELEMENTO QUE YA ESTÁ DENTRO
        =================================================================

        El heap acomoda cada elemento cuando ENTRA (CAPA 4). Si después
        cambias el campo que decide su prioridad, el heap no se entera: el
        elemento queda en una posición que ya no le corresponde y la regla
        "padre <= hijos" se rompe en silencio. poll() empieza a devolver
        elementos equivocados.

        Es el mismo problema de cambiar una clave dentro de un HashMap
        (Maps, CAPA 6) o un elemento de un TreeSet.

        La forma correcta de "cambiar la prioridad":
            heap.remove(elemento);      // O(n): lo busca recorriendo
            elemento.cambiar(...);
            heap.offer(elemento);
        O mejor: usa elementos INMUTABLES (records) y mete uno nuevo. Si
        el viejo ya no sirve, márcalo como cancelado y descártalo cuando
        salga ("borrado perezoso", el truco habitual en Dijkstra).
    */
    static final class MutableJob {
        final String name;
        int priority;

        MutableJob(String name, int priority) {
            this.name = name;
            this.priority = priority;
        }
    }

    static boolean mutationExample() {
        Comparator<MutableJob> byPriority = Comparator.comparingInt(job -> job.priority);

        PriorityQueue<MutableJob> broken = new PriorityQueue<>(byPriority);
        MutableJob report = new MutableJob("reporte", 3);
        broken.offer(new MutableJob("backup", 5));
        broken.offer(report);
        broken.offer(new MutableJob("correo", 4));
        report.priority = 10;                       // ERROR: cambiado ya dentro del heap
        boolean wrongOneComesOut = broken.poll() == report;   // sale primero aunque ahora es el menos urgente

        PriorityQueue<MutableJob> correct = new PriorityQueue<>(byPriority);
        MutableJob invoice = new MutableJob("factura", 3);
        correct.offer(new MutableJob("backup", 5));
        correct.offer(invoice);
        correct.offer(new MutableJob("correo", 4));
        correct.remove(invoice);                    // sacar, cambiar, volver a meter
        invoice.priority = 10;
        correct.offer(invoice);

        return wrongOneComesOut && correct.poll().name.equals("correo");
    }

    /*
        =================================================================
        CAPA 12) CÓMO ELEGIR
        =================================================================

        | Necesitas                                          | Usa                                 |
        |----------------------------------------------------|-------------------------------------|
        | Sacar siempre el menor/mayor, con repetidos        | PriorityQueue                       |
        | Los K mayores o el K-ésimo                          | PriorityQueue de tamaño K (min-heap) |
        | Todo ordenado, una sola vez                        | list.sort (part4_tools/Sorting)     |
        | Siempre ordenado, sin repetidos, buscar cercanos   | TreeSet / TreeMap                   |
        | Por orden de llegada                               | ArrayDeque (QueuesAndStacks)        |
        | Prioridad entre varios hilos                       | PriorityBlockingQueue (v13_concurrency/) |
    */

    /*
        =================================================================
        CAPA 13) ERRORES COMUNES
        =================================================================

        1) Imprimir o recorrer la PriorityQueue esperando verla ordenada
           (CAPA 6).

        2) Comparar con (a, b) -> b - a: desborda. Usa Integer.compare o
           Comparator (CAPA 7).

        3) Esperar que los empates salgan en orden de llegada (CAPA 8).

        4) Usar un MAX-heap para los K mayores: tendrías que guardar todo.
           Es un MIN-heap de tamaño K (CAPA 9).

        5) Cambiar la prioridad de un elemento que ya está dentro (CAPA 11).

        6) Usar contains o remove(x) en un ciclo: O(n) cada uno (CAPA 6).

        7) Meter objetos que no son Comparable sin pasar un Comparator:
           ClassCastException desde el primer offer (CAPA 2).

        8) Ordenar todo para quedarte solo con el menor o los primeros K
           (CAPAS 4 y 9).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Cola de prioridad: sale el más prioritario, no el primero en
          llegar. PriorityQueue es un MIN-heap por defecto.
        - offer y poll O(log n), peek O(1); no acepta null; sí repetidos.
        - Heap: árbol completo en un arreglo; padre <= hijos; hijos de i
          en 2i+1 y 2i+2, padre en (i-1)/2. Sube al entrar, baja al salir.
        - Recorrerla NO da el orden: para eso, poll o copiar y ordenar.
        - Max-heap: Collections.reverseOrder(). Objetos: Comparator. Nunca
          b - a.
        - Empates sin orden garantizado: desempata con una secuencia.
        - Top K: min-heap de tamaño K, O(n log K); el k-ésimo es peek().
        - Mezclar K listas ordenadas: un heap con la cabeza de cada una.
        - No cambies la prioridad de un elemento ya dentro.
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
