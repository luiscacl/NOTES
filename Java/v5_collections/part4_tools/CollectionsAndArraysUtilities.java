package v5_collections.part4_tools;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.Set;

/*
    =====================================================================
    LAS UTILIDADES DE COLLECTIONS Y ARRAYS — DE CERO A NIVEL TÉCNICO
    =====================================================================

    Ruta de collections/: guía 10 de 10  (part4_tools)
    Anterior:  part4_tools/ImmutableCollections
    Siguiente: v6_algorithms/ComplexityAndPatterns  (última de collections/)

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.

    Qué se da por sabido: lo básico de Arrays (toString, sort, fill,
    equals, copyOf, binarySearch, asList, deepToString) está en
    v1_fundamentals/part4_data_and_input/Arrays; ordenar, en Sorting; y
    las fábricas inmutables, en ImmutableCollections. Aquí va el resto de
    la caja de herramientas.

    Índice:
        CAPA 1)  Qué son Collections y Arrays
        CAPA 2)  Collections: buscar y contar
        CAPA 3)  Collections: reordenar en el lugar
        CAPA 4)  Collections.addAll
        CAPA 5)  Envoltorios: checked y synchronized
        CAPA 6)  Arrays.setAll: llenar calculando
        CAPA 7)  La trampa de fill y nCopies con objetos
        CAPA 8)  Comparar arreglos: equals, mismatch y compare
        CAPA 9)  Un arreglo no sirve como clave
        CAPA 10) Convertir entre arreglos y colecciones
        CAPA 11) Mapa de la caja de herramientas
        CAPA 12) Errores comunes
        RESUMEN FINAL
*/
public class CollectionsAndArraysUtilities {

    public static void main(String[] args) {
        check(searchAndCountExample(), "CAPA 2");
        check(reorderExample(), "CAPA 3");
        check(addAllExample(), "CAPA 4");
        check(checkedExample(), "CAPA 5");
        check(setAllExample(), "CAPA 6");
        check(sharedObjectTrapExample(), "CAPA 7");
        check(compareArraysExample(), "CAPA 8");
        check(arrayAsKeyExample(), "CAPA 9");
        check(conversionsExample(), "CAPA 10");
        System.out.println("CollectionsAndArraysUtilities: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) QUÉ SON COLLECTIONS Y ARRAYS
        =================================================================

        Son CLASES UTILITARIAS (v2_oop/part1_basics/Static, CAPA 5): solo
        métodos static, constructor privado, nunca se crea un objeto de
        ellas. Agrupan algoritmos que sirven para cualquier colección o
        cualquier arreglo.

        Cuidado con los nombres, se parecen mucho:
            Collection    (singular) la INTERFACE: List, Set y Queue la extienden
            Collections   (plural)   la clase UTILITARIA: Collections.max(...)
            Arrays        la clase utilitaria para arreglos

        Por qué existen como clases aparte: los arreglos casi no tienen
        miembros propios (el campo length y el método clone), y antes de Java 8 las
        interfaces no podían tener código, así que los algoritmos comunes
        se pusieron en estas clases. Desde Java 8 algunos pasaron a ser
        métodos de las propias colecciones (list.sort, list.replaceAll,
        removeIf), y esos son los preferidos cuando existen.
    */

    /*
        =================================================================
        CAPA 2) COLLECTIONS: BUSCAR Y CONTAR
        =================================================================

            Collections.max(c) / min(c)               el mayor / menor, O(n)
            Collections.max(c, comparador)            con un orden propio
            Collections.frequency(c, x)               cuántas veces aparece x, O(n)
            Collections.disjoint(a, b)                true si NO comparten ningún elemento
            Collections.indexOfSubList(lista, sub)    dónde empieza sub dentro de lista (-1 si no)

        Notas:
        - max y min recorren una vez: O(n). Para "el mayor" no hace falta
          ordenar (Sorting, CAPA 12).
        - Con una colección VACÍA, max y min lanzan
          NoSuchElementException: no hay ningún mayor. Revisa isEmpty()
          antes, o usa streams, que devuelven un Optional
          (v7_functional/part2_streams/OptionalUsage).
        - frequency cuenta UN valor. Para contar todos los valores a la
          vez, un mapa con merge es O(n) en total (Maps, CAPA 8); llamar
          frequency por cada valor sería O(n²).
        - disjoint responde "¿tienen algo en común?" sin crear un conjunto
          intersección. Es más rápido si uno de los dos es un Set.
    */
    record Employee(String name, int salary) { }

    static boolean searchAndCountExample() {
        List<Employee> staff = List.of(new Employee("Ana", 30_000), new Employee("Luis", 45_000), new Employee("Mia", 28_000));
        Employee bestPaid = Collections.max(staff, Comparator.comparingInt(Employee::salary));

        boolean emptyMaxFails;
        try {
            Collections.max(new ArrayList<Integer>());
            emptyMaxFails = false;
        } catch (NoSuchElementException e) {
            emptyMaxFails = true;
        }

        List<String> votes = List.of("sí", "no", "sí", "sí");
        Set<String> blocked = Set.of("spam", "estafa");

        return bestPaid.name().equals("Luis")
                && Collections.min(List.of(5, 2, 8)) == 2
                && emptyMaxFails
                && Collections.frequency(votes, "sí") == 3
                && Collections.disjoint(List.of("hola", "gracias"), blocked)          // nada en común
                && !Collections.disjoint(List.of("hola", "spam"), blocked)
                && Collections.indexOfSubList(List.of(1, 2, 3, 2, 3), List.of(2, 3)) == 1;
    }

    /*
        =================================================================
        CAPA 3) COLLECTIONS: REORDENAR EN EL LUGAR
        =================================================================

        Todos cambian la lista recibida (no crean otra), así que fallan
        con listas inmutables (ImmutableCollections, CAPA 3):

            Collections.reverse(lista)           invierte el orden, O(n)
            Collections.swap(lista, i, j)        intercambia dos posiciones
            Collections.rotate(lista, d)         mueve todo d lugares a la derecha, dando la vuelta
            Collections.shuffle(lista)           revuelve al azar
            Collections.shuffle(lista, random)   revuelve con un Random dado
            Collections.fill(lista, x)           pone x en todas las posiciones

        ROTATE: con d = 1, el último pasa a ser el primero:
            [a, b, c, d]  rotate 1  ->  [d, a, b, c]
            [a, b, c, d]  rotate -1 ->  [b, c, d, a]   (negativo: a la izquierda)
        Sirve para turnos circulares: el que jugó pasa al final.

        SHUFFLE usa el algoritmo de Fisher-Yates: recorre de atrás hacia
        adelante intercambiando cada posición con una al azar entre el
        inicio y ella misma (incluida: si no se incluyera, no saldrían
        todas las permutaciones). Es
        O(n) y todas las permutaciones son igual de probables. (Inventar
        un revuelto "a mano", por ejemplo ordenando con un comparador al
        azar, ni siquiera funciona: Sorting, CAPA 9.)

        Por qué pasarle un Random con semilla: con la misma semilla, el
        revuelto es SIEMPRE el mismo. Eso hace reproducibles las pruebas y
        las simulaciones; en producción usas shuffle(lista) sin semilla.
        (Para algo que deba ser impredecible de verdad, como sortear
        premios o generar códigos, usa java.security.SecureRandom.)
    */
    static boolean reorderExample() {
        List<String> turns = new ArrayList<>(List.of("Ana", "Luis", "Mia", "Leo"));
        Collections.rotate(turns, -1);                  // Ana ya jugó: pasa al final
        boolean rotated = turns.equals(List.of("Luis", "Mia", "Leo", "Ana"));

        Collections.swap(turns, 0, 3);
        boolean swapped = turns.equals(List.of("Ana", "Mia", "Leo", "Luis"));

        Collections.reverse(turns);
        boolean reversed = turns.equals(List.of("Luis", "Leo", "Mia", "Ana"));

        List<Integer> first = new ArrayList<>(List.of(1, 2, 3, 4, 5));
        List<Integer> second = new ArrayList<>(first);
        Collections.shuffle(first, new Random(7));
        Collections.shuffle(second, new Random(7));     // misma semilla, mismo revuelto
        boolean reproducible = first.equals(second) && first.equals(List.of(5, 4, 1, 3, 2));

        return rotated && swapped && reversed && reproducible;
    }

    /*
        =================================================================
        CAPA 4) COLLECTIONS.ADDALL
        =================================================================

        Para agregar varios elementos sueltos a una colección que ya
        existe:
            Collections.addAll(coleccion, "a", "b", "c");
        en lugar de
            coleccion.addAll(List.of("a", "b", "c"));      // crea una lista solo para tirarla
            coleccion.addAll(Arrays.asList("a", "b", "c"));

        Recibe varargs (v1_fundamentals/part2_control_flow/Methods), así
        que también acepta un arreglo directamente. Devuelve true si la
        colección cambió (con un Set, false si todos ya estaban).
    */
    static boolean addAllExample() {
        List<String> tags = new ArrayList<>(List.of("java"));
        Collections.addAll(tags, "spring", "sql");

        String[] fromArray = {"docker", "git"};
        Collections.addAll(tags, fromArray);            // un arreglo también sirve

        Set<String> unique = new HashSet<>(Set.of("a"));
        boolean changed = Collections.addAll(unique, "a");   // ya estaba: el set no cambió

        return tags.equals(List.of("java", "spring", "sql", "docker", "git")) && !changed;
    }

    /*
        =================================================================
        CAPA 5) ENVOLTORIOS: CHECKED Y SYNCHRONIZED
        =================================================================

        Además de las vistas unmodifiable (ImmutableCollections, CAPA 6),
        Collections tiene otras dos familias de envoltorios:

        1) checkedList(lista, Tipo.class) (y checkedSet, checkedMap...):
           revisa el tipo de cada elemento AL INSERTARLO.
           Por qué haría falta, si los genéricos ya revisan tipos: por el
           borrado de tipos (v4_generics/AdvancedGenerics, CAPA 8). Al
           ejecutar, una List<Integer> es solo una List. Si código viejo
           con raw types (v4_generics/GenericsBasics, CAPA 8) mete un
           String, nadie lo detiene; el error aparece MUCHO después, al
           leerlo, como ClassCastException en una línea que no tiene la
           culpa. Con checkedList, falla en el add, donde está el error.
           Úsalo para depurar o en la frontera con código antiguo.

        2) synchronizedList(lista) (y synchronizedMap...): pone un candado
           en cada operación para usarla desde varios hilos. Es la forma
           antigua: recorrerla sigue requiriendo un candado manual, y las
           colecciones de java.util.concurrent (ConcurrentHashMap,
           CopyOnWriteArrayList) son mejores casi siempre. Se ven en
           v13_concurrency/.
    */
    @SuppressWarnings({"rawtypes", "unchecked"})    // a propósito: simula código viejo con raw types
    static boolean checkedExample() {
        List<Integer> plain = new ArrayList<>();
        List rawPlain = plain;
        rawPlain.add("diez");                       // nadie lo detiene aquí...
        boolean failsLater;
        try {
            Integer value = plain.get(0);           // ...falla aquí, lejos del error real
            failsLater = value == null;
        } catch (ClassCastException e) {
            failsLater = true;
        }

        List<Integer> checked = Collections.checkedList(new ArrayList<>(), Integer.class);
        List rawChecked = checked;
        boolean failsAtInsert;
        try {
            rawChecked.add("diez");                 // falla en el lugar exacto del error
            failsAtInsert = false;
        } catch (ClassCastException e) {
            failsAtInsert = e.getMessage().startsWith("Attempt to insert class java.lang.String element");
        }

        return failsLater && failsAtInsert && checked.isEmpty();
    }

    /*
        =================================================================
        CAPA 6) ARRAYS.SETALL: LLENAR CALCULANDO
        =================================================================

        Arrays.fill pone el MISMO valor en todas las posiciones.
        Arrays.setAll(arreglo, i -> ...) calcula el valor de CADA posición
        a partir de su índice, con una lambda (v7_functional/part1_lambdas/Lambdas):

            Arrays.setAll(squares, i -> i * i);          // [0, 1, 4, 9, 16]

        Es un for de inicialización en una línea, sin poder equivocarte en
        los límites del ciclo. También sirve con arreglos de objetos, y
        ahí resuelve la trampa de la siguiente capa: crea un objeto NUEVO
        por posición.

        (Arrays.fill(arr, desde, hasta, x) también existe, para un tramo.)
    */
    static boolean setAllExample() {
        int[] squares = new int[5];
        Arrays.setAll(squares, i -> i * i);

        String[] seats = new String[3];
        Arrays.setAll(seats, i -> "A" + (i + 1));

        int[] partial = new int[5];
        Arrays.fill(partial, 1, 3, 9);                  // solo las posiciones 1 y 2

        return Arrays.equals(squares, new int[] {0, 1, 4, 9, 16})
                && Arrays.equals(seats, new String[] {"A1", "A2", "A3"})
                && Arrays.equals(partial, new int[] {0, 9, 9, 0, 0});
    }

    /*
        =================================================================
        CAPA 7) LA TRAMPA DE FILL Y NCOPIES CON OBJETOS
        =================================================================

        Arrays.fill(arr, x), Collections.fill(lista, x) y
        Collections.nCopies(n, x) ponen el MISMO valor x en todas las
        posiciones. Con números está perfecto. Con un OBJETO mutable, lo
        que se copia es la REFERENCIA (v1_fundamentals/part3_text_and_
        references/ReferencesAndNull): todas las posiciones apuntan al
        MISMO objeto.

            List<String>[] buckets = new List[3];
            Arrays.fill(buckets, new ArrayList<>());   // UNA lista, tres referencias
            buckets[0].add("x");                       // aparece en las tres

        Es un error clásico al preparar "una lista por cada día" o "una
        fila por cada jugador". La solución: crear un objeto por posición,
        con setAll (CAPA 6) o con un for.
    */
    static boolean sharedObjectTrapExample() {
        List<List<String>> shared = new ArrayList<>(Collections.nCopies(3, new ArrayList<>()));
        shared.get(0).add("lunes");                     // ERROR: las tres posiciones son la misma lista
        boolean allChanged = shared.get(2).equals(List.of("lunes"));

        List<List<String>> separate = new ArrayList<>();
        for (int day = 0; day < 3; day++) {
            separate.add(new ArrayList<>());            // una lista NUEVA por posición
        }
        separate.get(0).add("lunes");

        StringBuilder[] rows = new StringBuilder[3];
        Arrays.setAll(rows, i -> new StringBuilder());  // setAll: un objeto nuevo por posición
        rows[0].append("x");

        return allChanged
                && separate.get(2).isEmpty()
                && rows[2].isEmpty();
    }

    /*
        =================================================================
        CAPA 8) COMPARAR ARREGLOS: EQUALS, MISMATCH Y COMPARE
        =================================================================

        Arrays.equals(a, b) dice SI son iguales (y deepEquals, para
        matrices). Java 9 agregó dos más que dicen CÓMO difieren:

            Arrays.mismatch(a, b)   la primera posición donde difieren;
                                    -1 si son iguales
            Arrays.compare(a, b)    negativo, 0 o positivo: el orden
                                    "de diccionario" (lexicográfico)

        ORDEN LEXICOGRÁFICO: se compara posición por posición; la primera
        diferencia decide. Si uno se acaba antes, el más corto va primero
        (como "casa" antes que "casas"). Es como se comparan los Strings,
        pero con los NÚMEROS de cada posición, no con su texto.

        Por eso resuelve el problema de las versiones de ObjectContract
        (CAPA 9) sin escribir compareTo: {1, 10, 0} va DESPUÉS de
        {1, 9, 3}, porque en la posición 1 compara 10 contra 9 como
        números. Arrays::compare se puede usar directamente como
        Comparator<int[]> para ordenar.
    */
    static boolean compareArraysExample() {
        int[] expected = {1, 2, 3, 4};
        int[] actual = {1, 2, 9, 4};
        int firstDifference = Arrays.mismatch(expected, actual);

        int[] v1_10_0 = {1, 10, 0};
        int[] v1_9_3 = {1, 9, 3};

        List<int[]> versions = new ArrayList<>(List.of(v1_10_0, new int[] {1, 2, 0}, v1_9_3));
        versions.sort(Arrays::compare);                 // un Comparator<int[]> listo

        return firstDifference == 2
                && Arrays.mismatch(expected, expected.clone()) == -1
                && Arrays.compare(v1_10_0, v1_9_3) > 0                     // 10 > 9 como números
                && Arrays.compare(new int[] {1, 2}, new int[] {1, 2, 0}) < 0   // el más corto primero
                && versions.get(0)[1] == 2 && versions.get(2) == v1_10_0;
    }

    /*
        =================================================================
        CAPA 9) UN ARREGLO NO SIRVE COMO CLAVE
        =================================================================

        Los arreglos NO sobrescriben equals ni hashCode: usan los de
        Object, que comparan IDENTIDAD (v2_oop/part4_advanced_types/
        ObjectContract, CAPA 2). Por eso:
        - dos arreglos con el mismo contenido son "distintos" para un
          HashMap o un HashSet;
        - map.get(new int[] {1, 2}) nunca encuentra lo que guardaste con
          otro arreglo {1, 2}.

        Arrays.equals, Arrays.hashCode y Arrays.toString existen
        justamente para comparar por contenido, pero un HashMap no los
        usa: usa los del objeto. Soluciones:
        - usar una List como clave (List.of(1, 2): equals por contenido e
          inmutable);
        - mejor aún, un record con campos con nombre (Maps, CAPA 6).
        (Si un record TIENE un arreglo dentro, hereda este problema:
        Records, CAPA 7.)
    */
    record Coordinate(int x, int y) { }

    static boolean arrayAsKeyExample() {
        Map<int[], String> byArray = new HashMap<>();
        byArray.put(new int[] {1, 2}, "tesoro");
        boolean arrayKeyLost = byArray.get(new int[] {1, 2}) == null;      // otro arreglo, otra identidad

        Map<List<Integer>, String> byList = new HashMap<>();
        byList.put(List.of(1, 2), "tesoro");

        Map<Coordinate, String> byRecord = new HashMap<>();
        byRecord.put(new Coordinate(1, 2), "tesoro");

        return arrayKeyLost
                && Arrays.equals(new int[] {1, 2}, new int[] {1, 2})          // por contenido sí son iguales
                && byList.get(List.of(1, 2)).equals("tesoro")
                && byRecord.get(new Coordinate(1, 2)).equals("tesoro");
    }

    /*
        =================================================================
        CAPA 10) CONVERTIR ENTRE ARREGLOS Y COLECCIONES
        =================================================================

        DE COLECCIÓN A ARREGLO:
            String[] arr = lista.toArray(new String[0]);
            String[] arr = lista.toArray(String[]::new);      // Java 11+
        Por qué new String[0] y no new String[lista.size()]: toArray crea
        un arreglo del tamaño correcto si el que le das es chico. El de
        tamaño 0 solo le dice el TIPO, y en las JVM modernas es igual o
        más rápido. lista.toArray() sin argumento devuelve Object[], que
        no puedes convertir a String[] (por el borrado de tipos).

        DE ARREGLO A COLECCIÓN:
            new ArrayList<>(Arrays.asList(arr))   modificable
            List.of(arr)                          inmutable (rechaza null)
            Arrays.asList(arr)                    tamaño fijo, comparte el arreglo
        (v1_fundamentals/part4_data_and_input/Arrays, CAPA 7.)

        CON PRIMITIVOS no hay atajo: un int[] no es un Integer[], y
        Arrays.asList(int[]) da una lista de UN elemento (el arreglo). Se
        convierte con streams, que hacen el boxing (v7_functional/part2_streams/StreamsBasics):
            List<Integer> lista = Arrays.stream(nums).boxed().toList();
            int[] nums = lista.stream().mapToInt(Integer::intValue).toArray();
    */
    static boolean conversionsExample() {
        List<String> names = List.of("Ana", "Luis");
        String[] fromList = names.toArray(new String[0]);
        String[] fromListModern = names.toArray(String[]::new);

        String[] array = {"x", "y"};
        List<String> editable = new ArrayList<>(Arrays.asList(array));
        editable.add("z");

        int[] primitives = {3, 1, 2};
        List<Integer> boxed = Arrays.stream(primitives).boxed().toList();
        int[] back = boxed.stream().mapToInt(Integer::intValue).toArray();

        return Arrays.equals(fromList, new String[] {"Ana", "Luis"})
                && Arrays.equals(fromListModern, fromList)
                && editable.equals(List.of("x", "y", "z")) && array.length == 2
                && boxed.equals(List.of(3, 1, 2))
                && Arrays.equals(back, primitives);
    }

    /*
        =================================================================
        CAPA 11) MAPA DE LA CAJA DE HERRAMIENTAS
        =================================================================

        Dónde está cada cosa de las 10 guías de collections/:

        | Quieres                          | Herramienta                                   | Guía                    |
        |----------------------------------|-----------------------------------------------|-------------------------|
        | Mayor / menor                    | Collections.max / min                          | esta, CAPA 2            |
        | Contar un valor / todos          | Collections.frequency / map.merge              | esta, CAPA 2 / Maps     |
        | Invertir, rotar, revolver        | Collections.reverse / rotate / shuffle         | esta, CAPA 3            |
        | Agregar varios sueltos           | Collections.addAll                             | esta, CAPA 4            |
        | Llenar un arreglo                | Arrays.fill / setAll                           | esta, CAPA 6            |
        | Comparar arreglos                | Arrays.equals / mismatch / compare             | esta, CAPA 8            |
        | Imprimir un arreglo              | Arrays.toString / deepToString                 | Arrays (fundamentals)   |
        | Copiar un arreglo                | Arrays.copyOf / copyOfRange                    | Arrays (fundamentals)   |
        | Ordenar                          | list.sort / Arrays.sort                        | Sorting                 |
        | Buscar en ordenado               | Collections / Arrays.binarySearch              | Sorting, CAPA 11        |
        | Quitar con condición             | removeIf                                       | Iterators, CAPA 7       |
        | Reemplazar todos                 | list.replaceAll(x -> ...)                      | Iterators, CAPA 8       |
        | Colección fija o copia congelada | List.of / copyOf                               | ImmutableCollections    |
        | Vista de solo lectura            | Collections.unmodifiableList                   | ImmutableCollections    |
        | Vacía, de uno, n copias          | List.of() / List.of(x) / Collections.nCopies   | ImmutableCollections    |
    */

    /*
        =================================================================
        CAPA 12) ERRORES COMUNES
        =================================================================

        1) Confundir Collection (interface) con Collections (utilidades)
           (CAPA 1).

        2) Collections.max/min sobre una colección vacía:
           NoSuchElementException (CAPA 2).

        3) Llamar frequency por cada valor para contar todos: O(n²); usa un
           mapa (CAPA 2).

        4) reverse, shuffle, rotate o fill sobre una lista inmutable
           (CAPA 3).

        5) Usar shuffle con semilla fija en producción, o Random para algo
           que debe ser impredecible (CAPA 3).

        6) fill o nCopies con un objeto mutable: todas las posiciones son
           el mismo objeto (CAPA 7).

        7) Usar un arreglo como clave de un mapa o elemento de un set
           (CAPA 9).

        8) (String[]) lista.toArray(): ClassCastException; pásale el tipo
           (CAPA 10).

        9) Arrays.asList con un int[] (CAPA 10).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Collections y Arrays: clases utilitarias con algoritmos static.
          Si la colección ya tiene el método (sort, removeIf, replaceAll),
          prefiérelo.
        - Buscar y contar: max/min (no ordenes para eso), frequency,
          disjoint, indexOfSubList.
        - Reordenar en el lugar: reverse, swap, rotate, shuffle (con
          semilla para pruebas), fill.
        - addAll para varios sueltos; checkedList para atrapar tipos
          equivocados en el lugar del error.
        - Arrays.setAll calcula cada posición; fill y nCopies comparten el
          MISMO objeto.
        - mismatch y compare: dónde y cómo difieren dos arreglos;
          compare es lexicográfico y numérico.
        - Un arreglo no sirve como clave: usa List o un record.
        - toArray(new T[0]) o toArray(T[]::new); con primitivos, streams.

        Con esto termina collections/. Siguiente: v6_algorithms/, donde estas
        estructuras se usan para resolver problemas y medir su costo.
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
