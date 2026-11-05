package v6_algorithms;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/*
    =====================================================================
    RECURSIÓN, ÁRBOLES, GRAFOS Y PROGRAMACIÓN DINÁMICA
    =====================================================================

    Ruta de v6_algorithms/: guía 2 de 3
    Anterior:  ComplexityAndPatterns
    Siguiente: Exercises

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo. main() solo comprueba:
          java v6_algorithms/RecursionGraphsAndDP.java

    Lo que ya se vio y NO se repite: qué es la recursión, caso base y
    paso recursivo (v1_fundamentals/part2_control_flow/Methods, CAPA 8),
    la pila de llamadas y StackOverflowError (v12_jvm/part1_memory/
    StackAndHeapMemory, CAPAS 2 y 3), que TreeMap es un árbol ordenado
    (v5_collections/part2_sets_and_maps/Maps), ArrayDeque como cola y pila
    (v5_collections/part3_queues/QueuesAndStacks).

    Importancia: MEDIA para el trabajo; ALTA para entrevistas.

    Índice:
        CAPA 1)  Divide y vencerás: merge sort
        CAPA 2)  Backtracking: probar todas las combinaciones
        CAPA 3)  Árboles: el árbol binario de búsqueda
        CAPA 4)  Grafos: BFS para el camino más corto
        CAPA 5)  DFS: explorar todo lo conectado
        CAPA 6)  Programación dinámica: no resolver dos veces lo mismo
        CAPA 7)  Errores comunes
        RESUMEN FINAL
*/
public class RecursionGraphsAndDP {

    public static void main(String[] args) {
        check(mergeSortExample(), "CAPA 1");
        check(backtrackingExample(), "CAPA 2");
        check(treeExample(), "CAPA 3");
        check(bfsExample(), "CAPA 4");
        check(dfsExample(), "CAPA 5");
        check(dynamicProgrammingExample(), "CAPA 6");
        System.out.println("RecursionGraphsAndDP: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) DIVIDE Y VENCERÁS: MERGE SORT
        =================================================================

        Partir el problema en mitades, resolver cada una (recursión) y
        JUNTAR. Merge sort: ordena la mitad izquierda, ordena la derecha, y
        mezcla dos listas ya ordenadas tomando siempre el menor de los dos
        frentes. log n niveles de mitades x n trabajo por nivel = O(n log n),
        siempre (no hay "mal caso"), con O(n) de memoria extra.

        En el trabajo NO lo escribes: List.sort y Arrays.sort con objetos ya
        usan un merge sort mejorado (v5_collections/part4_tools/Sorting).
        Se escribe para entender por qué ordenar cuesta O(n log n) y no
        O(n²) como los métodos "de escuela" (burbuja, inserción).
    */
    static int[] mergeSort(int[] values) {
        if (values.length <= 1) {
            return values;                                    // caso base: ya está ordenado
        }
        int middle = values.length / 2;
        int[] left = mergeSort(Arrays.copyOfRange(values, 0, middle));
        int[] right = mergeSort(Arrays.copyOfRange(values, middle, values.length));
        int[] merged = new int[values.length];
        int i = 0;
        int j = 0;
        for (int k = 0; k < merged.length; k++) {
            boolean takeLeft = j >= right.length || (i < left.length && left[i] <= right[j]);   // <= : estable
            merged[k] = takeLeft ? left[i++] : right[j++];
        }
        return merged;
    }

    static boolean mergeSortExample() {
        return Arrays.equals(mergeSort(new int[] {5, 2, 9, 1, 5, 6}), new int[] {1, 2, 5, 5, 6, 9})
                && mergeSort(new int[0]).length == 0;
    }

    /*
        =================================================================
        CAPA 2) BACKTRACKING: PROBAR TODAS LAS COMBINACIONES
        =================================================================

        "Todas las combinaciones de productos para un paquete", "todas las
        formas de...". Se construye la respuesta paso a paso: en cada paso
        se ELIGE, se sigue (recursión) y se DESHACE la elección para probar
        la siguiente. Eso es backtracking.

        Los subconjuntos de n elementos son 2^n (cada uno entra o no):
        3 -> 8, 20 -> ~un millón, 40 -> un billón. Por eso se PODA: si un
        camino ya no puede llegar a una respuesta válida (el paquete ya se
        pasó del presupuesto), se abandona sin seguir.
    */
    static List<List<String>> bundlesWithinBudget(List<String> names, List<Integer> prices, int budget) {
        List<List<String>> result = new ArrayList<>();
        explore(names, prices, budget, 0, new ArrayList<>(), result);
        return result;
    }

    private static void explore(List<String> names, List<Integer> prices, int budgetLeft, int index,
            List<String> chosen, List<List<String>> result) {
        if (budgetLeft < 0) {
            return;                                           // poda: se pasó, no sigas por aquí
        }
        if (index == names.size()) {
            result.add(List.copyOf(chosen));                  // una combinación completa
            return;
        }
        chosen.add(names.get(index));                         // elegir...
        explore(names, prices, budgetLeft - prices.get(index), index + 1, chosen, result);
        chosen.removeLast();                                  // ...y deshacer
        explore(names, prices, budgetLeft, index + 1, chosen, result);   // sin este producto
    }

    static boolean backtrackingExample() {
        List<String> all = List.of("teclado", "ratón", "monitor");
        List<List<String>> unlimited = bundlesWithinBudget(all, List.of(900, 350, 4_000), Integer.MAX_VALUE / 2);
        List<List<String>> cheap = bundlesWithinBudget(all, List.of(900, 350, 4_000), 1_500);
        return unlimited.size() == 8                          // 2^3
                && cheap.size() == 4                          // {}, ratón, teclado, teclado+ratón
                && cheap.contains(List.of("teclado", "ratón"))
                && !cheap.contains(List.of("monitor"));
    }

    /*
        =================================================================
        CAPA 3) ÁRBOLES: EL ÁRBOL BINARIO DE BÚSQUEDA
        =================================================================

        Un árbol binario: cada nodo tiene hasta dos hijos. En uno DE
        BÚSQUEDA (BST), todo lo de la izquierda es menor y todo lo de la
        derecha mayor. Buscar o insertar baja un nivel por paso: O(altura).

        Si llega ordenado, el árbol degenera en una "lista" (todo a la
        derecha) y la altura es n. Por eso los de verdad se REBALANCEAN: el
        TreeMap de Java es un árbol rojo-negro, siempre O(log n). Aquí se
        escribe uno simple para ver la idea.

        Recorrerlo "en orden" (izquierda, nodo, derecha) entrega los valores
        ORDENADOS: así TreeMap te da sus claves en orden.
    */
    static final class Bst {
        private record Node(int value, Node left, Node right) {
        }

        private Node root;

        void insert(int value) {
            root = insert(root, value);
        }

        private static Node insert(Node node, int value) {
            if (node == null) {
                return new Node(value, null, null);
            }
            if (value < node.value()) {
                return new Node(node.value(), insert(node.left(), value), node.right());
            }
            if (value > node.value()) {
                return new Node(node.value(), node.left(), insert(node.right(), value));
            }
            return node;                                      // repetido: no se agrega
        }

        boolean contains(int value) {
            Node node = root;
            while (node != null) {
                if (value == node.value()) {
                    return true;
                }
                node = value < node.value() ? node.left() : node.right();
            }
            return false;
        }

        List<Integer> inOrder() {
            List<Integer> out = new ArrayList<>();
            inOrder(root, out);
            return out;
        }

        private static void inOrder(Node node, List<Integer> out) {
            if (node != null) {
                inOrder(node.left(), out);
                out.add(node.value());
                inOrder(node.right(), out);
            }
        }

        int height() {
            return height(root);
        }

        private static int height(Node node) {
            return node == null ? 0 : 1 + Math.max(height(node.left()), height(node.right()));
        }
    }

    static boolean treeExample() {
        Bst mixed = new Bst();
        for (int v : new int[] {50, 30, 70, 20, 40, 60, 80}) {
            mixed.insert(v);
        }
        Bst sorted = new Bst();
        for (int v = 1; v <= 7; v++) {
            sorted.insert(v);                                 // llega ordenado: degenera
        }
        return mixed.inOrder().equals(List.of(20, 30, 40, 50, 60, 70, 80))
                && mixed.contains(60) && !mixed.contains(65)
                && mixed.height() == 3                        // log2(7) redondeado
                && sorted.height() == 7;                      // una lista disfrazada
    }

    /*
        =================================================================
        CAPA 4) GRAFOS: BFS PARA EL CAMINO MÁS CORTO
        =================================================================

        Un GRAFO: cosas (nodos) y conexiones (aristas). Rutas entre
        bodegas, dependencias entre módulos, usuarios que se siguen. Se
        guarda como LISTA DE ADYACENCIA: Map<nodo, List<vecinos>>.

        BFS (búsqueda en anchura) ya se vio recorriendo un árbol por niveles
        con una cola (v5_collections/part3_queues/QueuesAndStacks, CAPA 9).
        En un GRAFO cambian dos cosas. Primero todos los vecinos a 1 paso,
        luego los de 2...: por eso, sin pesos, la primera vez que llegas a un
        nodo es por el camino MÁS CORTO.

        Siempre con un conjunto de VISITADOS: un grafo puede tener ciclos (A
        conecta con B y B con A), y sin él el recorrido no termina.
        O(nodos + aristas).
    */
    static int fewestHops(Map<String, List<String>> routes, String from, String to) {
        Deque<String> queue = new ArrayDeque<>(List.of(from));
        Map<String, Integer> distance = new HashMap<>(Map.of(from, 0));   // también hace de "visitados"
        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (current.equals(to)) {
                return distance.get(current);
            }
            for (String next : routes.getOrDefault(current, List.of())) {
                if (distance.putIfAbsent(next, distance.get(current) + 1) == null) {
                    queue.add(next);                          // primera vez que lo vemos
                }
            }
        }
        return -1;                                            // no hay camino
    }

    static boolean bfsExample() {
        Map<String, List<String>> routes = Map.of(
                "CDMX", List.of("Querétaro", "Puebla"),
                "Querétaro", List.of("CDMX", "Guadalajara", "Monterrey"),
                "Puebla", List.of("CDMX", "Veracruz"),
                "Guadalajara", List.of("Querétaro", "Monterrey"),
                "Monterrey", List.of("Querétaro", "Guadalajara"),
                "Veracruz", List.of("Puebla"));
        return fewestHops(routes, "CDMX", "Monterrey") == 2
                && fewestHops(routes, "Veracruz", "Guadalajara") == 4
                && fewestHops(routes, "CDMX", "Tijuana") == -1;
    }

    /*
        =================================================================
        CAPA 5) DFS: EXPLORAR TODO LO CONECTADO
        =================================================================

        DFS (búsqueda en profundidad): sigue un camino hasta el fondo antes
        de regresar. Natural con recursión (o con una PILA). No da el camino
        más corto, pero es lo más simple para "¿qué está conectado con qué?":
        contar zonas, detectar ciclos, recorrer carpetas.

        El clásico: una cuadrícula de bodegas ('#') y terreno libre ('.');
        ¿cuántas zonas de bodegas pegadas (arriba, abajo, izquierda,
        derecha) hay? Para cada '#' no visitado: una zona nueva, y DFS marca
        toda la zona.

        Cuidado con la recursión profunda: una zona de un millón de celdas
        son un millón de marcos en la pila -> StackOverflowError
        (StackAndHeapMemory, CAPA 3). Con datos grandes, la misma idea con
        un ArrayDeque como pila.
    */
    static int countZones(String[] grid) {
        Set<String> visited = new HashSet<>();
        int zones = 0;
        for (int row = 0; row < grid.length; row++) {
            for (int col = 0; col < grid[row].length(); col++) {
                if (grid[row].charAt(col) == '#' && !visited.contains(row + "," + col)) {
                    zones++;
                    markZone(grid, row, col, visited);
                }
            }
        }
        return zones;
    }

    private static void markZone(String[] grid, int row, int col, Set<String> visited) {
        if (row < 0 || row >= grid.length || col < 0 || col >= grid[row].length()
                || grid[row].charAt(col) != '#' || !visited.add(row + "," + col)) {
            return;                                           // fuera, libre, o ya visitada
        }
        markZone(grid, row + 1, col, visited);
        markZone(grid, row - 1, col, visited);
        markZone(grid, row, col + 1, visited);
        markZone(grid, row, col - 1, visited);
    }

    static boolean dfsExample() {
        String[] map = {
                "##..#",
                "#...#",
                "..#..",
                ".....",
                "##.##"};
        return countZones(map) == 5;
    }

    /*
        =================================================================
        CAPA 6) PROGRAMACIÓN DINÁMICA: NO RESOLVER DOS VECES LO MISMO
        =================================================================

        Cuando una recursión resuelve los MISMOS subproblemas muchas veces,
        se guarda cada respuesta la primera vez. Eso es programación
        dinámica (DP). Dos formas:
        - memoización: la recursión de siempre + un Map de respuestas
          (de arriba hacia abajo);
        - tabulación: un arreglo que se llena desde los casos chicos (de
          abajo hacia arriba), sin recursión.

        Medido abajo: Fibonacci(25) recursivo hace 242 785 llamadas; con
        memoización, 49. De exponencial a O(n).

        El caso práctico: dar cambio con el MÍNIMO de monedas. Con las
        monedas de México (1, 2, 5, 10), "toma siempre la más grande"
        (voraz) funciona. Con monedas {1, 3, 4} y 6: voraz da 4+1+1 = 3
        monedas, pero lo mejor es 3+3 = 2. La DP prueba todas las opciones
        sin repetir trabajo: best[x] = 1 + el mínimo de best[x - moneda].
        O(cantidad x monedas).

        Señal de DP: "el mínimo/máximo/número de formas de...", y al
        escribir la recursión ves que se repiten las mismas llamadas.
    */
    static long naiveCalls;
    static long memoCalls;

    static long fibNaive(int n) {
        naiveCalls++;
        return n < 2 ? n : fibNaive(n - 1) + fibNaive(n - 2);
    }

    static long fibMemo(int n, Map<Integer, Long> memo) {
        memoCalls++;
        if (n < 2) {
            return n;
        }
        Long known = memo.get(n);
        if (known != null) {
            return known;
        }
        long value = fibMemo(n - 1, memo) + fibMemo(n - 2, memo);
        memo.put(n, value);
        return value;
    }

    static int greedyCoins(int[] coinsDescending, int amount) {
        int count = 0;
        for (int coin : coinsDescending) {
            count += amount / coin;
            amount %= coin;
        }
        return amount == 0 ? count : -1;
    }

    static int fewestCoins(int[] coins, int amount) {
        int[] best = new int[amount + 1];                     // best[x] = mínimo de monedas para x
        Arrays.fill(best, Integer.MAX_VALUE);
        best[0] = 0;
        for (int x = 1; x <= amount; x++) {
            for (int coin : coins) {
                if (coin <= x && best[x - coin] != Integer.MAX_VALUE) {
                    best[x] = Math.min(best[x], best[x - coin] + 1);
                }
            }
        }
        return best[amount] == Integer.MAX_VALUE ? -1 : best[amount];
    }

    static boolean dynamicProgrammingExample() {
        naiveCalls = 0;
        memoCalls = 0;
        boolean sameAnswer = fibNaive(25) == 75_025 && fibMemo(25, new HashMap<>()) == 75_025;
        return sameAnswer
                && naiveCalls == 242_785
                && memoCalls == 49
                && greedyCoins(new int[] {10, 5, 2, 1}, 18) == 4      // 10+5+2+1: voraz sirve en MXN
                && greedyCoins(new int[] {4, 3, 1}, 6) == 3           // 4+1+1
                && fewestCoins(new int[] {1, 3, 4}, 6) == 2           // 3+3
                && fewestCoins(new int[] {5, 10}, 3) == -1;
    }

    /*
        =================================================================
        CAPA 7) ERRORES COMUNES
        =================================================================

        1) Escribir tu propio ordenamiento en código de trabajo (CAPA 1).

        2) Backtracking sin deshacer la elección, o sin podar (CAPA 2).

        3) Asumir que un árbol simple es O(log n): con datos ordenados es
           O(n) (CAPA 3).

        4) BFS o DFS sin conjunto de visitados en un grafo con ciclos: no
           termina (CAPAS 4 y 5).

        5) Usar DFS para el camino más corto; recursión profunda con datos
           grandes (CAPA 5).

        6) Resolver con voraz algo que necesita DP, o no ver la recursión
           que repite llamadas (CAPA 6).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Divide y vencerás: merge sort, O(n log n) siempre.
        - Backtracking: elegir, seguir, deshacer; podar. 2^n combinaciones.
        - BST: O(altura); se rebalancea (TreeMap) para O(log n); en orden =
          ordenado.
        - Grafo como Map<nodo, vecinos>. BFS (cola) = camino más corto sin
          pesos; DFS (recursión o pila) = todo lo conectado. Siempre
          visitados.
        - DP: guardar subproblemas (memo o tabla); voraz no siempre da el
          óptimo.
    */

    private static void check(boolean condition, String layer) {
        if (!condition) {
            throw new IllegalStateException("El ejemplo de la " + layer + " no hace lo que la guía explica");
        }
    }
}
