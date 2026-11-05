package v6_algorithms;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/*
    =====================================================================
    COMPLEJIDAD Y PATRONES: CÓMO PENSAR UN PROBLEMA DE ALGORITMOS
    =====================================================================

    Ruta de v6_algorithms/: guía 1 de 3
    Anterior:  v5_collections/part4_tools/CollectionsAndArraysUtilities
    Siguiente: RecursionGraphsAndDP

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo. main() solo comprueba
      que cada ejemplo hace lo que el texto dice:
          java v6_algorithms/ComplexityAndPatterns.java
    - Es una sección CORTA a propósito: lo que sirve para entrevistas
      técnicas y para no escribir código lento sin darte cuenta. Las
      estructuras (HashMap, HashSet, colas, heaps) ya se vieron a fondo en
      v5_collections/; aquí se COMBINAN.

    Lo que ya se vio y NO se repite: recorrer y buscar con un for
    (v1_fundamentals/part2_control_flow/Loops), dos punteros para un
    palíndromo (Loops, CAPA 4; Strings), binarySearch de la API
    (v1_fundamentals/part4_data_and_input/Arrays, CAPA 6), el costo de cada
    operación de cada colección (v5_collections/part1_basics/
    CollectionsOverview), Top K con un heap (v5_collections/part3_queues/
    PriorityQueues, CAPA 9).

    Importancia: MEDIA para el trabajo diario con Spring; ALTA si vas a
    tener entrevistas técnicas.

    Índice:
        CAPA 1)  Big O: cuánto crece el trabajo
        CAPA 2)  Cambiar tiempo por memoria: el HashMap
        CAPA 3)  Dos punteros sobre datos ordenados
        CAPA 4)  Ventana deslizante
        CAPA 5)  Suma de prefijos
        CAPA 6)  Búsqueda binaria más allá de "¿está?"
        CAPA 7)  Cómo atacar un problema nuevo
        CAPA 8)  Errores comunes
        RESUMEN FINAL
*/
public class ComplexityAndPatterns {

    public static void main(String[] args) {
        check(bigOExample(), "CAPA 1");
        check(hashMapTradeExample(), "CAPA 2");
        check(twoPointersExample(), "CAPA 3");
        check(slidingWindowExample(), "CAPA 4");
        check(prefixSumExample(), "CAPA 5");
        check(binarySearchExample(), "CAPA 6");
        System.out.println("ComplexityAndPatterns: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) BIG O: CUÁNTO CRECE EL TRABAJO
        =================================================================

        Big O no mide segundos: dice cómo CRECE el trabajo cuando crecen
        los datos (n). Se cuenta el número de pasos y se queda solo el
        término que manda, sin constantes.

            O(1)         igual con 10 que con un millón (get de HashMap)
            O(log n)     se parte a la mitad cada paso (búsqueda binaria):
                         un millón -> ~20 pasos
            O(n)         un recorrido
            O(n log n)   ordenar bien (List.sort, Arrays.sort)
            O(n²)        un for dentro de otro sobre los mismos datos
            O(2^n)       probar todas las combinaciones (CAPA 2 de
                         RecursionGraphsAndDP)

        Cómo se calcula, en la práctica:
        - ciclos uno después de otro: se SUMAN -> O(n) + O(n) = O(n);
        - ciclos uno dentro de otro: se MULTIPLICAN -> O(n²);
        - una llamada a la API también cuenta: list.contains(x) dentro de
          un for es O(n²) aunque solo veas un for.

        El ESPACIO se mide igual: un HashMap con n entradas es O(n) de
        memoria extra.

        Medido en esta máquina, Two Sum con el par al final (dos corridas):

            n          dos for (O(n²))     HashMap (O(n), CAPA 2)
            20 000     26-27 ms            2-6 ms
            200 000    ~3 060 ms           20-34 ms

        Diez veces más datos: la versión O(n²) tardó ~115 veces más; la O(n),
        ~10. Con 20 000 los dos "se sienten rápidos"; en producción los datos
        crecen y el O(n²) es el que tumba el servicio.

        quadraticSteps abajo cuenta los pasos para que se vea sin
        cronómetro: el doble de datos, cuatro veces el trabajo.
    */
    static long quadraticSteps(int n) {
        long steps = 0;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                steps++;                                   // cada par, una vez
            }
        }
        return steps;
    }

    static boolean bigOExample() {
        return quadraticSteps(1_000) == 499_500             // n(n-1)/2: sigue siendo O(n²)
                && quadraticSteps(2_000) == 1_999_000;      // doble de datos -> 4 veces el trabajo
    }

    /*
        =================================================================
        CAPA 2) CAMBIAR TIEMPO POR MEMORIA: EL HASHMAP
        =================================================================

        El truco más usado: en vez de buscar con otro for (O(n) por
        búsqueda), GUARDAR lo visto en un HashMap o HashSet (O(1) por
        búsqueda). Pagas O(n) de memoria y bajas de O(n²) a O(n).

        Two Sum: ¿qué dos posiciones suman target? Al llegar a cada número
        pregunto "¿ya vi a mi complemento (target - número)?".

        Variantes del mismo patrón: frecuencias (contar con merge,
        v5_collections/part2_sets_and_maps/Maps), "¿ya lo vi?" con un
        HashSet (duplicados, visitados en un grafo: RecursionGraphsAndDP).
    */
    static int[] twoSum(int[] numbers, int target) {
        Map<Integer, Integer> seenAt = new HashMap<>();     // valor -> posición
        for (int i = 0; i < numbers.length; i++) {
            Integer partner = seenAt.get(target - numbers[i]);
            if (partner != null) {
                return new int[] {partner, i};
            }
            seenAt.put(numbers[i], i);
        }
        return new int[0];                                   // no hay par
    }

    static boolean hasDuplicate(int[] numbers) {
        Set<Integer> seen = new HashSet<>();
        for (int n : numbers) {
            if (!seen.add(n)) {                              // add devuelve false si ya estaba
                return true;
            }
        }
        return false;
    }

    static boolean hashMapTradeExample() {
        int[] pair = twoSum(new int[] {2, 7, 11, 15}, 18);
        return pair[0] == 1 && pair[1] == 2
                && twoSum(new int[] {1, 2}, 10).length == 0
                && hasDuplicate(new int[] {3, 1, 4, 1})
                && !hasDuplicate(new int[] {3, 1, 4});
    }

    /*
        =================================================================
        CAPA 3) DOS PUNTEROS SOBRE DATOS ORDENADOS
        =================================================================

        Si los datos están ORDENADOS, dos índices (uno al inicio, otro al
        final) pueden reemplazar el doble for. En cada paso la suma te dice
        cuál mover: si es poco, avanza el izquierdo (para crecer); si es
        mucho, retrocede el derecho. O(n) y O(1) de memoria (sin HashMap).

        Si no están ordenados, ordenar cuesta O(n log n): conviene si
        además necesitas el orden, o si la memoria importa.
    */
    static boolean hasPairWithSum(int[] sorted, int target) {
        int left = 0;
        int right = sorted.length - 1;
        while (left < right) {
            int sum = sorted[left] + sorted[right];
            if (sum == target) {
                return true;
            }
            if (sum < target) {
                left++;
            } else {
                right--;
            }
        }
        return false;
    }

    static boolean twoPointersExample() {
        int[] prices = {100, 250, 300, 450, 700};
        return hasPairWithSum(prices, 750)                   // 300 + 450
                && !hasPairWithSum(prices, 600);
    }

    /*
        =================================================================
        CAPA 4) VENTANA DESLIZANTE
        =================================================================

        Problemas de "el mejor tramo SEGUIDO": la mayor venta en 7 días
        consecutivos, la subcadena más larga sin letras repetidas. Recalcular
        cada tramo desde cero es O(n·k). La ventana se DESLIZA: al moverla un
        lugar, entra un elemento y sale otro.

        Ventana FIJA (k días): suma += entra; suma -= sale. O(n).

        Ventana VARIABLE: el extremo derecho siempre avanza; el izquierdo
        avanza solo cuando la ventana deja de cumplir la regla. Cada
        elemento entra y sale una vez: O(n).
    */
    static long bestWeek(int[] dailySales, int days) {
        long window = 0;
        for (int i = 0; i < days; i++) {
            window += dailySales[i];
        }
        long best = window;
        for (int i = days; i < dailySales.length; i++) {
            window += dailySales[i] - dailySales[i - days];   // entra hoy, sale el de hace k días
            best = Math.max(best, window);
        }
        return best;
    }

    static int longestWithoutRepeats(String text) {
        Map<Character, Integer> lastSeen = new HashMap<>();
        int best = 0;
        int start = 0;
        for (int end = 0; end < text.length(); end++) {
            Integer previous = lastSeen.put(text.charAt(end), end);
            if (previous != null && previous >= start) {
                start = previous + 1;                         // la ventana deja atrás la repetida
            }
            best = Math.max(best, end - start + 1);
        }
        return best;
    }

    static boolean slidingWindowExample() {
        int[] sales = {5, 1, 9, 2, 8, 3, 4, 7, 6, 1};
        return bestWeek(sales, 3) == 19                       // 9 + 2 + 8
                && longestWithoutRepeats("abcabcbb") == 3    // "abc"
                && longestWithoutRepeats("pwwkew") == 3;     // "wke"
    }

    /*
        =================================================================
        CAPA 5) SUMA DE PREFIJOS
        =================================================================

        "¿Cuánto se vendió del día 10 al 20?", muchas veces. Sumar el tramo
        cada vez es O(n) por pregunta. Se precalcula una vez
        prefix[i] = suma de los primeros i elementos, y entonces
            suma(desde, hasta) = prefix[hasta + 1] - prefix[desde]   O(1)
        El arreglo tiene n + 1 lugares: prefix[0] = 0 evita casos
        especiales.
    */
    static long[] prefixSums(int[] values) {
        long[] prefix = new long[values.length + 1];
        for (int i = 0; i < values.length; i++) {
            prefix[i + 1] = prefix[i] + values[i];
        }
        return prefix;
    }

    static long rangeSum(long[] prefix, int from, int to) {
        return prefix[to + 1] - prefix[from];
    }

    static boolean prefixSumExample() {
        long[] prefix = prefixSums(new int[] {5, 1, 9, 2, 8});
        return rangeSum(prefix, 1, 3) == 12                   // 1 + 9 + 2
                && rangeSum(prefix, 0, 4) == 25;
    }

    /*
        =================================================================
        CAPA 6) BÚSQUEDA BINARIA MÁS ALLÁ DE "¿ESTÁ?"
        =================================================================

        Arrays.binarySearch (Arrays, CAPA 6) responde "¿está y dónde?". En
        entrevistas te piden escribirla, y casi siempre una VARIANTE: "la
        primera posición donde el valor es >= x" (dónde insertarlo, el primer
        precio que alcanza un presupuesto).

        Dos detalles que separan una versión correcta de una con errores:
        - el punto medio: low + (high - low) / 2, no (low + high) / 2, que
          se desborda con índices grandes (v1_fundamentals/.../
          ConversionsAndCasting: los int dan la vuelta);
        - el ciclo: con high EXCLUSIVO (high = length) y while (low < high),
          el ciclo siempre termina y el resultado queda en low.

        La misma idea sirve para "buscar la respuesta": si puedes preguntar
        "¿con X alcanza?" y la respuesta pasa de no a sí una sola vez, la
        búsqueda binaria encuentra el X mínimo en O(log rango).
    */
    static int firstAtLeast(int[] sorted, int target) {
        int low = 0;
        int high = sorted.length;                             // exclusivo
        while (low < high) {
            int mid = low + (high - low) / 2;
            if (sorted[mid] < target) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        return low;                                           // length si ninguno alcanza
    }

    static boolean binarySearchExample() {
        int[] prices = {100, 250, 250, 300, 700};
        return firstAtLeast(prices, 250) == 1                 // la PRIMERA, aunque haya repetidos
                && firstAtLeast(prices, 260) == 3
                && firstAtLeast(prices, 1_000) == 5
                && firstAtLeast(prices, 50) == 0;
    }

    /*
        =================================================================
        CAPA 7) CÓMO ATACAR UN PROBLEMA NUEVO
        =================================================================

        1) Entiende con un ejemplo chico, a mano. Pregunta los límites:
           ¿datos ordenados? ¿negativos? ¿repetidos? ¿qué tan grande es n?
        2) Di la solución obvia y su Big O (casi siempre O(n²)). Está bien
           empezar ahí.
        3) Busca la señal de un patrón:
               "¿ya vi X?", contar, agrupar        HashMap / HashSet (CAPA 2)
               datos ordenados, pares               dos punteros (CAPA 3)
               "tramo seguido", "subcadena"         ventana (CAPA 4)
               muchas sumas de rangos               prefijos (CAPA 5)
               ordenado + buscar, "el mínimo X que" binaria (CAPA 6)
               los k mayores / menores              heap (PriorityQueues)
               anidar, abrir/cerrar                 pila (QueuesAndStacks)
               caminos, vecinos, niveles            BFS / DFS (RecursionGraphsAndDP)
               "todas las combinaciones"            backtracking (ídem)
               subproblemas que se repiten          DP (ídem)
        4) Escribe, y prueba con casos de borde: vacío, un elemento, todos
           iguales, sin respuesta.

        n te dice qué Big O necesitas: hasta ~10^4, O(n²) suele pasar; con
        10^5 o más, hace falta O(n log n) u O(n).
    */

    /*
        =================================================================
        CAPA 8) ERRORES COMUNES
        =================================================================

        1) No ver el O(n²) escondido: contains o indexOf de una lista dentro
           de un for (CAPA 1).

        2) Two Sum metiendo primero el número y luego buscando: con target 6
           y el número 3, se encuentra a sí mismo (CAPA 2: primero se busca,
           luego se guarda).

        3) Dos punteros sobre datos sin ordenar (CAPA 3).

        4) Ventana que recalcula todo en cada paso (CAPA 4).

        5) Prefijos sin el lugar extra prefix[0] = 0: casos especiales y
           errores de uno (CAPA 5).

        6) (low + high) / 2, y ciclos de búsqueda binaria que no terminan
           (CAPA 6).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Big O: cómo crece el trabajo; ciclos seguidos se suman, anidados se
          multiplican; la API también cuenta.
        - HashMap/HashSet cambian memoria por tiempo: O(n²) -> O(n).
        - Ordenado: dos punteros. Tramos seguidos: ventana. Muchas sumas de
          rangos: prefijos. Ordenado + buscar: binaria (low + (high-low)/2).
        - Primero la solución obvia y su costo; luego el patrón.
    */

    private static void check(boolean condition, String layer) {
        if (!condition) {
            throw new IllegalStateException("El ejemplo de la " + layer + " no hace lo que la guía explica");
        }
    }
}
