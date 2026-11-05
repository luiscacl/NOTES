package v5_collections.part3_queues;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.Stack;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/*
    =====================================================================
    COLAS Y PILAS: QUEUE, DEQUE Y ARRAYDEQUE — DE CERO A NIVEL TÉCNICO
    =====================================================================

    Ruta de collections/: guía 6 de 10  (part3_queues)
    Anterior:  part2_sets_and_maps/Maps
    Siguiente: part3_queues/PriorityQueues

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.

    Índice:
        CAPA 1)  Pila y cola: las dos formas de "esperar turno"
        CAPA 2)  La interface Queue: dos familias de métodos
        CAPA 3)  Por qué una ArrayList no sirve como cola
        CAPA 4)  ArrayDeque por dentro: el arreglo circular
        CAPA 5)  Deque: los dos extremos, y cómo hace de pila
        CAPA 6)  Por qué no usar la clase Stack
        CAPA 7)  LinkedList como cola, y por qué ArrayDeque rechaza null
        CAPA 8)  La pila en la práctica: paréntesis y deshacer/rehacer
        CAPA 9)  La cola en la práctica: turnos y recorrido por niveles (BFS)
        CAPA 10) Usar los dos extremos: un historial con límite
        CAPA 11) Colas con límite y entre hilos: BlockingQueue
        CAPA 12) Cómo elegir
        CAPA 13) Errores comunes
        RESUMEN FINAL
*/
public class QueuesAndStacks {

    public static void main(String[] args) {
        check(lifoFifoExample(), "CAPA 1");
        check(queueMethodsExample(), "CAPA 2");
        check(arrayListAsStackExample(), "CAPA 3");
        check(circularBufferExample(), "CAPA 4");
        check(dequeExample(), "CAPA 5");
        check(legacyStackExample(), "CAPA 6");
        check(nullAmbiguityExample(), "CAPA 7");
        check(stackUsesExample(), "CAPA 8");
        check(queueUsesExample(), "CAPA 9");
        check(recentHistoryExample(), "CAPA 10");
        check(blockingQueueExample(), "CAPA 11");
        System.out.println("QueuesAndStacks: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) PILA Y COLA: LAS DOS FORMAS DE "ESPERAR TURNO"
        =================================================================

        Muchas veces no necesitas acceder a CUALQUIER elemento, sino
        procesarlos en un orden fijo: el siguiente en la fila, la última
        acción hecha. Para eso hay dos estructuras clásicas.

        COLA (queue) — FIFO: "First In, First Out", el primero que entra
        es el primero que sale. Como la fila del banco:
            entra:  A, B, C          sale: A, luego B, luego C
            frente -> [A, B, C] <- final (se forma al final)
        Para: turnos, tareas pendientes, mensajes, recorrer por niveles.

        PILA (stack) — LIFO: "Last In, First Out", el último que entra es
        el primero que sale. Como una pila de platos: solo tocas el de
        arriba.
            entra:  A, B, C          sale: C, luego B, luego A
            arriba -> [C, B, A]
        Para: deshacer, el historial "atrás" del navegador, paréntesis
        balanceados, y la propia ejecución de tus métodos (la "pila de
        llamadas" del stack trace es exactamente esto:
        v3_exceptions/part1_concepts/WhatAreExceptions).

        POR QUÉ restringir las operaciones, si una lista puede hacer todo:
        tu antiguo CheatSheet lo dice bien: "la razón real no es velocidad, es
        disciplina de API". Una pila que solo deja push/pop/peek no te
        permite leer ni cambiar la mitad por error. El tipo comunica la
        intención: quien lee Deque<Action> undo sabe cómo se usa.

        En Java, las dos se hacen con la MISMA clase: ArrayDeque.
    */
    static boolean lifoFifoExample() {
        Queue<String> queue = new ArrayDeque<>();
        Deque<String> stack = new ArrayDeque<>();
        for (String letter : List.of("A", "B", "C")) {
            queue.offer(letter);
            stack.push(letter);
        }

        StringBuilder queueOrder = new StringBuilder();
        while (!queue.isEmpty()) {                  // se CONSUMEN hasta vaciarse, no se recorren con índice
            queueOrder.append(queue.poll());
        }
        StringBuilder stackOrder = new StringBuilder();
        while (!stack.isEmpty()) {
            stackOrder.append(stack.pop());
        }

        return queueOrder.toString().equals("ABC") && stackOrder.toString().equals("CBA");
    }

    /*
        =================================================================
        CAPA 2) LA INTERFACE QUEUE: DOS FAMILIAS DE MÉTODOS
        =================================================================

        Queue tiene cada operación DOS veces. La diferencia es qué pasa
        cuando no se puede hacer:

        | Operación              | Lanza excepción | Devuelve un valor especial |
        |------------------------|-----------------|----------------------------|
        | Agregar al final       | add(x)          | offer(x)  -> false si no cabe |
        | Sacar el frente        | remove()        | poll()    -> null si está vacía |
        | Ver el frente sin sacar| element()       | peek()    -> null si está vacía |

        Por qué existen las dos:
        - Hay colas con LÍMITE (CAPA 11). Que una cola llena rechace un
          elemento es algo normal, no un error, y offer te lo dice con un
          false en lugar de obligarte a atrapar una excepción.
        - Una cola vacía también es normal: "no hay más trabajo". poll()
          devuelve null y lo usas como condición.

        En la práctica se usan offer, poll y peek (los de tu
        JavaDataStructuresGuide). Las versiones que lanzan excepción
        sirven cuando una cola vacía SÍ sería un error de lógica y
        prefieres enterarte de inmediato.
    */
    static boolean queueMethodsExample() {
        Queue<Integer> queue = new ArrayDeque<>();
        boolean emptyGivesNull = queue.poll() == null && queue.peek() == null;

        boolean removeFails;
        try {
            queue.remove();
            removeFails = false;
        } catch (NoSuchElementException e) {
            removeFails = true;
        }

        queue.offer(10);
        queue.offer(20);
        queue.offer(30);
        boolean peekDoesNotRemove = queue.peek() == 10 && queue.size() == 3;
        boolean pollRemoves = queue.poll() == 10 && queue.size() == 2 && queue.element() == 20;

        return emptyGivesNull && removeFails && peekDoesNotRemove && pollRemoves;
    }

    /*
        =================================================================
        CAPA 3) POR QUÉ UNA ARRAYLIST NO SIRVE COMO COLA
        =================================================================

        Una ArrayList guarda todo en un arreglo continuo (Lists, CAPA 2).

        Como PILA funciona bien. Tu antiguo CheatSheet lo confirmó en el código
        del JDK: remove(size() - 1) no mueve nada, es O(1) real, igual que
        ArrayDeque:
            list.add(x);                       // push
            list.remove(list.size() - 1);      // pop
        Aun así se prefiere Deque por la disciplina de la CAPA 1: con una
        lista puedes hacer get(3) por error, y los nombres no dicen "pila".

        Como COLA es una mala idea. Sacar el frente es remove(0), y eso
        obliga a recorrer TODOS los demás elementos un lugar a la izquierda:
            [A, B, C, D, E]  remove(0)  ->  [B, C, D, E]  (4 elementos movidos)
        Cada salida cuesta O(n). Procesar una cola de n tareas así cuesta
        O(n²). Con 100,000 tareas son unos 5,000 millones de movimientos;
        con ArrayDeque, 100,000.

        "Con pocos elementos no se nota; con muchos, sí" (tu antiguo CheatSheet).
        La solución es una estructura que saque del frente en O(1): la
        siguiente capa explica cómo lo logra ArrayDeque.
    */
    static boolean arrayListAsStackExample() {
        List<Integer> listStack = new ArrayList<>();
        listStack.add(1);
        listStack.add(2);
        int top = listStack.remove(listStack.size() - 1);       // pop: O(1), no mueve nada

        List<Integer> listQueue = new ArrayList<>(List.of(1, 2, 3));
        int front = listQueue.remove(0);                        // funciona, pero mueve todo lo demás: O(n)

        return top == 2 && listStack.equals(List.of(1)) && front == 1 && listQueue.equals(List.of(2, 3));
    }

    /*
        =================================================================
        CAPA 4) ARRAYDEQUE POR DENTRO: EL ARREGLO CIRCULAR
        =================================================================

        ArrayDeque también usa un arreglo, pero NO obliga a que el primer
        elemento esté en la posición 0. Guarda dos índices:
            head: dónde está el primero
            tail: dónde va el siguiente que llegue al final

        Sacar del frente = leer arreglo[head] y mover head una posición.
        No se mueve ningún elemento: O(1).

        Y cuando un índice llega al final del arreglo, DA LA VUELTA al
        principio (por eso se llama "circular"), reutilizando los lugares
        que quedaron libres:

            capacidad 8, después de varias entradas y salidas:
            posición:  0    1    2    3    4    5    6    7
                     [ G ][ H ][   ][   ][   ][ D ][ E ][ F ]
                                 ^tail          ^head
            orden lógico: D, E, F, G, H   (G y H "dieron la vuelta")

        Agregar al frente funciona igual, moviendo head hacia atrás (y
        dando la vuelta hacia el final). Por eso los DOS extremos son O(1).

        Cuando se llena, crea un arreglo más grande, copia los elementos y
        reacomoda el tramo que había dado la vuelta: O(n) de vez en cuando, O(1) amortizado, la misma idea
        que ArrayList (Lists, CAPA 2) y HashMap (Maps, CAPA 5).

        Ventaja sobre LinkedList (CAPA 7): los elementos están contiguos
        en memoria, sin un nodo extra por elemento. Menos memoria y mucho
        mejor uso de la caché del procesador (Lists, CAPA 6).

        El ejemplo de abajo hace muchas vueltas: mete y saca miles de
        veces manteniendo pocos elementos. El arreglo no crece, solo
        circulan head y tail.
    */
    static boolean circularBufferExample() {
        Deque<Integer> deque = new ArrayDeque<>();
        int processed = 0;
        for (int i = 0; i < 10_000; i++) {
            deque.offerLast(i);
            if (deque.size() > 3) {
                deque.pollFirst();                  // sale por el frente sin mover a nadie
                processed++;
            }
        }
        return processed == 9_997 && List.copyOf(deque).equals(List.of(9_997, 9_998, 9_999));
    }

    /*
        =================================================================
        CAPA 5) DEQUE: LOS DOS EXTREMOS, Y CÓMO HACE DE PILA
        =================================================================

        Deque ("double ended queue", se pronuncia "dek") es una Queue que
        permite meter y sacar por AMBOS extremos. Cada operación existe
        para el frente (First) y para el final (Last), en las dos
        familias de la CAPA 2:

        |              | Frente (First)                  | Final (Last)                  |
        |--------------|---------------------------------|-------------------------------|
        | Agregar      | addFirst / offerFirst           | addLast / offerLast           |
        | Sacar        | removeFirst / pollFirst         | removeLast / pollLast         |
        | Ver          | getFirst / peekFirst            | getLast / peekLast            |

        Como COLA usas un extremo para entrar y el otro para salir:
            offer(x) = offerLast(x)       poll() = pollFirst()

        Como PILA usas UN solo extremo, el frente:
            push(x) = addFirst(x)         pop() = removeFirst()      peek() = peekFirst()

        Consecuencia que confunde al principio: como push mete por el
        FRENTE, al imprimir o recorrer una pila de ArrayDeque el tope sale
        primero:
            push(1), push(2), push(3)   ->   [3, 2, 1]
        Es correcto: se recorre en el orden en que irían saliendo.

        Ojo: pop() en una pila vacía lanza NoSuchElementException (es de
        la familia que lanza). Si vacía es normal, usa poll() o revisa
        isEmpty() antes.

        Regla práctica: elige UN uso por variable. Si declaras
        Deque<T> stack, usa solo push/pop/peek; mezclar con addLast o
        pollLast la convierte en algo que nadie entiende.
    */
    static boolean dequeExample() {
        Deque<Integer> deque = new ArrayDeque<>();
        deque.addFirst(20);
        deque.addFirst(10);
        deque.addLast(30);
        deque.addLast(40);
        boolean bothEnds = List.copyOf(deque).equals(List.of(10, 20, 30, 40))
                && deque.peekFirst() == 10 && deque.peekLast() == 40;
        deque.pollFirst();
        deque.pollLast();
        boolean afterPolls = List.copyOf(deque).equals(List.of(20, 30));

        Deque<Integer> stack = new ArrayDeque<>();
        stack.push(1);
        stack.push(2);
        stack.push(3);
        boolean topFirst = stack.toString().equals("[3, 2, 1]") && stack.peek() == 3;
        boolean popTakesTop = stack.pop() == 3 && stack.peek() == 2;

        boolean popOnEmptyFails;
        try {
            new ArrayDeque<Integer>().pop();
            popOnEmptyFails = false;
        } catch (NoSuchElementException e) {
            popOnEmptyFails = true;
        }

        return bothEnds && afterPolls && topFirst && popTakesTop && popOnEmptyFails;
    }

    /*
        =================================================================
        CAPA 6) POR QUÉ NO USAR LA CLASE STACK
        =================================================================

        Java tiene una clase llamada Stack desde la versión 1.0. Suena a
        lo que buscas, pero la documentación oficial recomienda usar
        Deque en su lugar. Las razones:

        1) Stack HEREDA de Vector, una lista antigua con todos sus métodos
           sincronizados (synchronized): cada operación pide un candado
           para hilos aunque tu programa tenga uno solo. Costo sin
           beneficio (CollectionsOverview, CAPA 9).

        2) Por heredar de Vector, una Stack ES una lista: tiene get(i),
           add(i, x), remove(i)... Puedes meter un elemento a la mitad de
           la pila y el compilador no se queja. Es el ejemplo clásico de
           herencia mal usada (v2_oop/part2_pillars/Inheritance): "una
           pila ES UNA lista" no es verdad, una pila deja tocar solo el
           tope. Debió usar composición.

        3) Su orden de recorrido es el CONTRARIO al de ArrayDeque: recorre
           desde el fondo ([1, 2, 3] después de push 1, 2, 3), porque
           push agrega al final del Vector. Cambiar de Stack a ArrayDeque
           invierte cualquier código que recorra la pila.

        4) Una pila vacía lanza EmptyStackException en pop, otra excepción
           distinta a la del resto de colecciones.

        Úsala solo si la encuentras en código viejo. Para código nuevo:
            Deque<T> stack = new ArrayDeque<>();
    */
    static boolean legacyStackExample() {
        Stack<Integer> legacy = new Stack<>();
        legacy.push(1);
        legacy.push(2);
        legacy.push(3);
        legacy.add(0, 99);                          // compila: una "pila" que deja meter por el fondo

        Deque<Integer> modern = new ArrayDeque<>();
        modern.push(1);
        modern.push(2);
        modern.push(3);

        return legacy.equals(List.of(99, 1, 2, 3))          // recorre del fondo al tope
                && List.copyOf(modern).equals(List.of(3, 2, 1))   // recorre del tope al fondo
                && legacy.peek() == 3 && modern.peek() == 3;      // el tope es el mismo
    }

    /*
        =================================================================
        CAPA 7) LINKEDLIST COMO COLA, Y POR QUÉ ARRAYDEQUE RECHAZA NULL
        =================================================================

        Tu antigua JavaDataStructuresGuide y tu antiguo CheatSheet usan:
            Queue<Integer> queue = new LinkedList<>();
        Funciona: LinkedList implementa Deque y saca del frente en O(1).
        Pero ArrayDeque es mejor opción, y la documentación de Java lo
        dice: "probablemente más rápida que LinkedList usada como cola".
        Por qué:
        - LinkedList crea un NODO por elemento (el valor más dos
          referencias): más memoria y más trabajo para el recolector de
          basura.
        - Los nodos están dispersos en memoria; el arreglo de ArrayDeque
          está contiguo (Lists, CAPA 6).

        La otra diferencia: LinkedList ACEPTA null y ArrayDeque NO
        (lanza NullPointerException). No es un capricho: poll() y peek()
        usan null para decir "está vacía" (CAPA 2). Si null pudiera ser un
        elemento, poll() == null significaría dos cosas: "no hay nada" o
        "saqué un null". Es la misma ambigüedad de Map.get (Maps, CAPA 3).
        ArrayDeque la elimina de raíz.

        Cuándo LinkedList: casi nunca. Si de verdad necesitas guardar
        "ausencia" en una cola, usa un valor que lo represente (un
        Optional, un objeto "vacío"), no null.
    */
    static boolean nullAmbiguityExample() {
        Queue<Integer> linked = new LinkedList<>();
        linked.offer(null);                         // LinkedList lo acepta...
        Integer polled = linked.poll();             // ...y ahora poll() devolvió null con un elemento dentro
        boolean ambiguous = polled == null && linked.isEmpty() && linked.poll() == null;   // dos null, dos significados

        boolean arrayDequeRejectsNull;
        try {
            new ArrayDeque<Integer>().offer(null);
            arrayDequeRejectsNull = false;
        } catch (NullPointerException e) {
            arrayDequeRejectsNull = true;
        }

        return ambiguous && arrayDequeRejectsNull;
    }

    /*
        =================================================================
        CAPA 8) LA PILA EN LA PRÁCTICA: PARÉNTESIS Y DESHACER/REHACER
        =================================================================

        1) PARÉNTESIS BALANCEADOS (tu antigua JavaDataStructuresGuide lo hacía con
           paréntesis; aquí con los tres tipos). La pila es la estructura
           perfecta porque cada cierre debe corresponder al ÚLTIMO
           símbolo abierto que sigue sin cerrar: eso es justo el tope.
           - Si es un símbolo de apertura: push del cierre que esperamos.
           - Si es de cierre: debe coincidir con pop(); si la pila está
             vacía o no coincide, está mal.
           - Al final, la pila debe quedar vacía (nada sin cerrar).
           Compiladores, editores y validadores de JSON hacen esto mismo.

        2) DESHACER / REHACER, como en cualquier editor. Dos pilas:
           - hacer una acción: push en "undo" y se vacía "redo" (una
             acción nueva invalida lo que se podía rehacer);
           - deshacer: pop de "undo", push en "redo";
           - rehacer: pop de "redo", push en "undo".
           Siempre se deshace lo ÚLTIMO que se hizo: LIFO.
    */
    private static final Map<Character, Character> CLOSING_FOR = Map.of('(', ')', '[', ']', '{', '}');

    static boolean isBalanced(String expression) {
        Deque<Character> expectedClosings = new ArrayDeque<>();
        for (char symbol : expression.toCharArray()) {
            if (CLOSING_FOR.containsKey(symbol)) {
                expectedClosings.push(CLOSING_FOR.get(symbol));     // guardamos el cierre que debe venir
            } else if (CLOSING_FOR.containsValue(symbol)) {
                if (expectedClosings.isEmpty() || expectedClosings.pop() != symbol) {
                    return false;                                   // cierre de más, o del tipo equivocado
                }
            }
        }
        return expectedClosings.isEmpty();                          // algo quedó abierto
    }

    static final class TextEditor {
        private final StringBuilder text = new StringBuilder();
        private final Deque<String> undo = new ArrayDeque<>();
        private final Deque<String> redo = new ArrayDeque<>();

        void type(String words) {
            text.append(words);
            undo.push(words);
            redo.clear();                           // una acción nueva borra el futuro "rehacer"
        }

        void undo() {
            if (undo.isEmpty()) {
                return;
            }
            String last = undo.pop();
            text.setLength(text.length() - last.length());
            redo.push(last);
        }

        void redo() {
            if (redo.isEmpty()) {
                return;
            }
            String again = redo.pop();
            text.append(again);
            undo.push(again);
        }

        String text() {
            return text.toString();
        }
    }

    static boolean stackUsesExample() {
        boolean balance = isBalanced("((()))()") && isBalanced("{[()]}(x + y)")
                && !isBalanced("(]") && !isBalanced("(()") && !isBalanced("())");

        TextEditor editor = new TextEditor();
        editor.type("Hola");
        editor.type(" mundo");
        editor.type("!");
        editor.undo();
        editor.undo();                              // quita "!" y luego " mundo"
        String afterUndo = editor.text();
        editor.redo();                              // vuelve " mundo"
        String afterRedo = editor.text();
        editor.type("?");                           // acción nueva: ya no se puede rehacer "!"
        editor.redo();

        return balance && afterUndo.equals("Hola") && afterRedo.equals("Hola mundo")
                && editor.text().equals("Hola mundo?");
    }

    /*
        =================================================================
        CAPA 9) LA COLA EN LA PRÁCTICA: TURNOS Y RECORRIDO POR NIVELES (BFS)
        =================================================================

        1) TURNOS: atender en orden de llegada (tickets de soporte,
           trabajos de impresión, pedidos). offer al llegar, poll al
           atender. Nadie se cuela.

        2) BFS ("Breadth-First Search", búsqueda en anchura): recorrer un
           grafo o una cuadrícula POR NIVELES: primero todo lo que está a
           1 paso, luego todo lo que está a 2... La cola lo garantiza
           sola: lo que se descubre se forma AL FINAL, así que no se
           atiende nada a distancia 2 hasta terminar con los de distancia 1.

           Consecuencia muy útil: la PRIMERA vez que BFS llega a una celda
           lo hace por el camino MÁS CORTO (en número de pasos). Es la base
           de "ruta más corta en un laberinto", "grados de separación"
           entre usuarios, etc.

           El patrón (el mismo de tu antigua JavaDataStructuresGuide):
           - meter el inicio en la cola y marcarlo como visitado;
           - mientras haya algo: sacar, revisar sus vecinos, y meter (y
             marcar) los que no se han visitado.
           Se marca AL METER, no al sacar: si no, una celda podría entrar
           varias veces a la cola por distintos vecinos.

           (Grafos y árboles a fondo: algorithms/TreesAndGraphs.)
    */
    static List<String> serveInOrder(List<String> arrivals) {
        Queue<String> waiting = new ArrayDeque<>(arrivals);
        List<String> served = new ArrayList<>();
        while (!waiting.isEmpty()) {
            served.add(waiting.poll());
        }
        return served;
    }

    record Cell(int row, int column) { }

    private static final int[][] DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    /** Pasos mínimos desde (0, 0) hasta el destino moviéndose por celdas con 1; -1 si no se puede. */
    static int shortestPath(int[][] grid, Cell target) {
        int[][] distance = new int[grid.length][grid[0].length];
        for (int[] row : distance) {
            Arrays.fill(row, -1);                   // -1 = no visitado
        }

        Queue<Cell> queue = new ArrayDeque<>();
        queue.offer(new Cell(0, 0));
        distance[0][0] = 0;

        while (!queue.isEmpty()) {
            Cell current = queue.poll();
            if (current.equals(target)) {
                return distance[current.row()][current.column()];   // la primera llegada es la más corta
            }
            for (int[] direction : DIRECTIONS) {
                int row = current.row() + direction[0];
                int column = current.column() + direction[1];
                boolean inside = row >= 0 && row < grid.length && column >= 0 && column < grid[0].length;
                if (inside && grid[row][column] == 1 && distance[row][column] == -1) {
                    distance[row][column] = distance[current.row()][current.column()] + 1;   // marcar AL METER
                    queue.offer(new Cell(row, column));
                }
            }
        }
        return -1;
    }

    static boolean queueUsesExample() {
        int[][] grid = {
            {1, 1, 0},
            {0, 1, 1},
            {0, 0, 1}
        };
        int[][] blocked = {
            {1, 0},
            {0, 1}
        };

        return serveInOrder(List.of("ticket-1", "ticket-2", "ticket-3")).equals(List.of("ticket-1", "ticket-2", "ticket-3"))
                && shortestPath(grid, new Cell(2, 2)) == 4
                && shortestPath(grid, new Cell(0, 0)) == 0
                && shortestPath(blocked, new Cell(1, 1)) == -1;
    }

    /*
        =================================================================
        CAPA 10) USAR LOS DOS EXTREMOS: UN HISTORIAL CON LÍMITE
        =================================================================

        A veces necesitas los dos extremos a la vez. Ejemplo real: las
        "búsquedas recientes" de una app, máximo 3, la más nueva primero.
        - Cada búsqueda nueva entra por el FRENTE (addFirst).
        - Si se pasa del límite, la más vieja sale por el FINAL
          (removeLast).
        - Si ya estaba, se quita de donde estaba para que suba al frente.
          Ese remove(x) recorre la deque: O(n), aceptable con un límite
          pequeño.

        Con una lista, insertar al frente sería O(n) en cada búsqueda;
        con ArrayDeque, los dos extremos son O(1).

        (Otros usos de los dos extremos, más avanzados: la "ventana
        deslizante" con máximo, en algorithms/.)
    */
    static final class RecentSearches {
        private final Deque<String> searches = new ArrayDeque<>();
        private final int limit;

        RecentSearches(int limit) {
            this.limit = limit;
        }

        void add(String search) {
            searches.remove(search);                // si ya estaba, que suba al frente
            searches.addFirst(search);
            if (searches.size() > limit) {
                searches.removeLast();              // sale la más vieja
            }
        }

        List<String> newestFirst() {
            return List.copyOf(searches);
        }
    }

    static boolean recentHistoryExample() {
        RecentSearches recent = new RecentSearches(3);
        for (String search : List.of("java", "maps", "deque", "spring", "maps")) {
            recent.add(search);
        }
        return recent.newestFirst().equals(List.of("maps", "spring", "deque"));
    }

    /*
        =================================================================
        CAPA 11) COLAS CON LÍMITE Y ENTRE HILOS: BLOCKINGQUEUE
        =================================================================

        ArrayDeque crece sin límite y no es segura para varios hilos. En
        sistemas reales, una cola muchas veces conecta a quien PRODUCE
        trabajo (recibe pedidos) con quien lo CONSUME (los procesa), en
        hilos distintos. Para eso existe BlockingQueue
        (java.util.concurrent), por ejemplo ArrayBlockingQueue con
        capacidad fija.

        Aquí se ve por qué Queue tiene dos familias (CAPA 2), y suma una
        tercera:
            offer(x)   cola llena -> devuelve false (rechazo normal)
            add(x)     cola llena -> IllegalStateException("Queue full")
            put(x)     cola llena -> ESPERA hasta que haya lugar
            take()     cola vacía -> ESPERA hasta que llegue algo

        El límite es una protección: si los pedidos llegan más rápido de
        lo que se procesan, una cola sin límite crecería hasta agotar la
        memoria. Con límite, el sistema rechaza o frena a quien produce
        ("backpressure").

        put y take, el patrón productor-consumidor y los hilos están en
        v13_concurrency/. Aquí basta con saber que existe y por qué.
    */
    static boolean blockingQueueExample() {
        BlockingQueue<String> orders = new ArrayBlockingQueue<>(2);
        boolean accepted = orders.offer("pedido-1") && orders.offer("pedido-2");
        boolean rejectedWhenFull = !orders.offer("pedido-3");      // sin excepción: simplemente no cabe

        boolean addFails;
        try {
            orders.add("pedido-3");
            addFails = false;
        } catch (IllegalStateException e) {
            addFails = e.getMessage().equals("Queue full");
        }

        return accepted && rejectedWhenFull && addFails && orders.poll().equals("pedido-1");
    }

    /*
        =================================================================
        CAPA 12) CÓMO ELEGIR
        =================================================================

        | Necesitas                                        | Usa                                  |
        |--------------------------------------------------|--------------------------------------|
        | Una cola FIFO                                    | Queue<T> q = new ArrayDeque<>()      |
        | Una pila LIFO                                    | Deque<T> s = new ArrayDeque<>()      |
        | Meter y sacar por los dos extremos               | ArrayDeque                           |
        | Atender por prioridad, no por llegada            | PriorityQueue (siguiente guía)       |
        | Cola con límite o entre hilos                    | ArrayBlockingQueue / LinkedBlockingQueue (v13_concurrency/) |
        | Stack                                            | no: código viejo                     |
        | LinkedList como cola                             | funciona, pero ArrayDeque es mejor   |

        Declara la variable con la interface que expresa el uso (Queue
        para cola, Deque para pila) y crea un ArrayDeque.
    */

    /*
        =================================================================
        CAPA 13) ERRORES COMUNES
        =================================================================

        1) Usar ArrayList como cola con remove(0): O(n) por cada salida
           (CAPA 3).

        2) Usar la clase Stack en código nuevo (CAPA 6).

        3) Llamar pop() o remove() sobre una pila o cola que puede estar
           vacía: NoSuchElementException. Usa poll() o revisa isEmpty()
           (CAPAS 2 y 5).

        4) Meter null en una cola: ArrayDeque lanza NullPointerException;
           LinkedList lo acepta y poll() se vuelve ambiguo (CAPA 7).

        5) Sorprenderse de que una pila de ArrayDeque se recorra del tope
           al fondo, o cambiar Stack por ArrayDeque sin revisar los
           recorridos (CAPAS 5 y 6).

        6) Mezclar operaciones de pila y de cola en la misma variable
           (CAPA 5).

        7) En BFS, marcar como visitado al SACAR en lugar de al METER: la
           misma celda entra varias veces (CAPA 9).

        8) Recorrer una cola con for-each esperando vaciarla: el for-each
           solo mira, no saca. Para consumir: while (!q.isEmpty()) q.poll()
           (CAPA 1).

        9) Una cola sin límite entre un productor rápido y un consumidor
           lento: se acaba la memoria (CAPA 11).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Cola = FIFO (turnos, BFS). Pila = LIFO (deshacer, paréntesis,
          pila de llamadas).
        - Queue: offer/poll/peek (devuelven false o null) o add/remove/
          element (lanzan excepción).
        - ArrayList sirve de pila pero no de cola: remove(0) es O(n).
        - ArrayDeque: arreglo circular con head y tail; O(1) en los dos
          extremos; no acepta null. Es LA implementación para pilas y
          colas.
        - Como pila: push/pop/peek, que trabajan en el frente; se recorre
          del tope al fondo.
        - Stack: antigua, sincronizada, hereda de Vector y rompe la idea de
          pila. No la uses.
        - LinkedList como cola funciona, pero gasta más y acepta null.
        - BlockingQueue: colas con límite y entre hilos (v13_concurrency/).
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
