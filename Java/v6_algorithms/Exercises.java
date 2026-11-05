package v6_algorithms;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
    =====================================================================
    LOS 10 EJERCICIOS DE TU CHEATSHEET, RESUELTOS
    =====================================================================

    Ruta de v6_algorithms/: guía 3 de 3
    Anterior:  RecursionGraphsAndDP
    Siguiente: v7_functional/part1_lambdas/Lambdas

    Cómo leer esta guía:
    - Primero intenta cada ejercicio TÚ (en Ejercicios.java, tu espacio de
      práctica), con la pista de la estructura. Después compara.
          java v6_algorithms/Exercises.java

    Eran los 10 ejercicios del final de CheatSheet.java (ya borrado: todo
    lo que tenía vive en las guías nuevas). Siete ya quedaron resueltos
    donde se explicó su idea; aquí no se repiten:

    | # | Ejercicio                          | Dónde está resuelto                                       |
    |---|------------------------------------|-----------------------------------------------------------|
    | 1 | Two Sum                            | ComplexityAndPatterns, CAPA 2 (twoSum)                    |
    | 2 | Valid Anagram                      | aquí, CAPA 1                                              |
    | 3 | Contains Duplicate                 | ComplexityAndPatterns, CAPA 2 (hasDuplicate)              |
    | 4 | Group Anagrams                     | aquí, CAPA 2                                              |
    | 5 | Top K Frequent Elements            | v5_collections/part3_queues/PriorityQueues, CAPA 9        |
    | 6 | Valid Parentheses                  | v5_collections/part3_queues/QueuesAndStacks, CAPA 8       |
    | 7 | Binary Search a mano               | ComplexityAndPatterns, CAPA 6 (firstAtLeast)              |
    | 8 | Simular una fila (offer/poll)      | v5_collections/part3_queues/QueuesAndStacks, CAPA 9       |
    | 9 | Merge Intervals                    | aquí, CAPA 3                                              |
    |10 | Kth Largest Element                | v5_collections/part3_queues/PriorityQueues, CAPA 9        |

    Índice:
        CAPA 1)  Valid Anagram: contar en vez de ordenar
        CAPA 2)  Group Anagrams: la llave correcta
        CAPA 3)  Merge Intervals: ordenar primero
        RESUMEN FINAL
*/
public class Exercises {

    public static void main(String[] args) {
        check(anagramExample(), "CAPA 1");
        check(groupAnagramsExample(), "CAPA 2");
        check(mergeIntervalsExample(), "CAPA 3");
        System.out.println("Exercises: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) VALID ANAGRAM: CONTAR EN VEZ DE ORDENAR
        =================================================================

        "listen" y "silent" usan las mismas letras, las mismas veces.

        Opción obvia: ordenar las letras de los dos y comparar. O(n log n).

        Mejor: CONTAR (ComplexityAndPatterns, CAPA 2). Suma 1 por cada letra
        del primero, resta 1 por cada letra del segundo; si todo queda en 0,
        son anagramas. O(n). Si son solo letras a-z, un int[26] (la letra
        menos 'a' es la posición) en vez de un HashMap: más rápido y sin
        boxing. Distinto largo -> no, sin contar nada.
    */
    static boolean isAnagram(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int[] counts = new int[26];                          // solo a-z
        for (int i = 0; i < a.length(); i++) {
            counts[a.charAt(i) - 'a']++;
            counts[b.charAt(i) - 'a']--;
        }
        for (int count : counts) {
            if (count != 0) {
                return false;
            }
        }
        return true;
    }

    static boolean anagramExample() {
        return isAnagram("listen", "silent")
                && !isAnagram("rat", "car")
                && !isAnagram("aab", "abb")                  // mismas letras, distintas veces
                && isAnagram("", "");
    }

    /*
        =================================================================
        CAPA 2) GROUP ANAGRAMS: LA LLAVE CORRECTA
        =================================================================

        ["eat","tea","tan","ate","nat","bat"] -> [eat,tea,ate], [tan,nat],
        [bat].

        La idea: agrupar es un Map<llave, List<palabra>>
        (v5_collections/part2_sets_and_maps/Maps: computeIfAbsent). El
        problema entero es elegir la LLAVE: algo que sea IGUAL para todos
        los anagramas. Las letras ordenadas sirven: "eat", "tea" y "ate"
        dan "aet".

        O(n · k log k), con n palabras de largo k. Con un int[26] de conteos
        convertido a texto como llave se baja a O(n · k), pero para palabras
        cortas ordenar es más simple y suficiente.
    */
    static Collection<List<String>> groupAnagrams(List<String> words) {
        Map<String, List<String>> groups = new HashMap<>();
        for (String word : words) {
            char[] letters = word.toCharArray();
            Arrays.sort(letters);
            groups.computeIfAbsent(new String(letters), key -> new ArrayList<>()).add(word);
        }
        return groups.values();
    }

    static boolean groupAnagramsExample() {
        Collection<List<String>> groups = groupAnagrams(List.of("eat", "tea", "tan", "ate", "nat", "bat"));
        return groups.size() == 3
                && groups.contains(List.of("eat", "tea", "ate"))
                && groups.contains(List.of("tan", "nat"))
                && groups.contains(List.of("bat"));
    }

    /*
        =================================================================
        CAPA 3) MERGE INTERVALS: ORDENAR PRIMERO
        =================================================================

        Reservas de una sala: [9,10], [14,16], [9.5,12] -> la sala está
        ocupada de 9 a 12 y de 14 a 16. Juntar los que se traslapan.

        Sin orden, cada intervalo tendría que compararse con todos: O(n²).
        ORDENADOS por inicio (v5_collections/part4_tools/Sorting: ordenar
        filas por una columna), basta con mirar el ÚLTIMO intervalo juntado:
        si el siguiente empieza antes de que el último termine (o justo
        cuando termina: [1,3] y [3,5] se juntan), se traslapan y se extiende el final (con el MAYOR de los dos finales:
        uno puede estar dentro del otro). Si no, empieza uno nuevo.
        O(n log n) por el ordenamiento; el recorrido es O(n).

        Aquí las horas están en minutos (9:30 = 570) para usar int.
    */
    static List<int[]> mergeIntervals(int[][] intervals) {
        int[][] sorted = intervals.clone();
        Arrays.sort(sorted, (x, y) -> Integer.compare(x[0], y[0]));
        List<int[]> merged = new ArrayList<>();
        for (int[] current : sorted) {
            int[] last = merged.isEmpty() ? null : merged.getLast();
            if (last != null && current[0] <= last[1]) {
                last[1] = Math.max(last[1], current[1]);      // se traslapa: extender
            } else {
                merged.add(new int[] {current[0], current[1]});   // copia: no tocar la entrada
            }
        }
        return merged;
    }

    static boolean mergeIntervalsExample() {
        List<int[]> rooms = mergeIntervals(new int[][] {{540, 600}, {840, 960}, {570, 720}});
        List<int[]> nested = mergeIntervals(new int[][] {{1, 10}, {2, 3}, {4, 5}});
        return rooms.size() == 2
                && Arrays.equals(rooms.get(0), new int[] {540, 720})   // 9:00 a 12:00
                && Arrays.equals(rooms.get(1), new int[] {840, 960})   // 14:00 a 16:00
                && nested.size() == 1
                && Arrays.equals(nested.get(0), new int[] {1, 10})     // el mayor final, no el último
                && mergeIntervals(new int[0][]).isEmpty();
    }

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Anagrama: contar (int[26]) en vez de ordenar; O(n).
        - Agrupar: Map<llave, lista>; todo está en elegir una llave igual
          para los que van juntos.
        - Intervalos: ordenar por inicio y comparar solo con el último;
          extender con el MAYOR final.
        - Los otros 7, en la tabla de arriba, junto a la idea que los
          resuelve.
    */

    private static void check(boolean condition, String layer) {
        if (!condition) {
            throw new IllegalStateException("El ejemplo de la " + layer + " no hace lo que la guía explica");
        }
    }
}
