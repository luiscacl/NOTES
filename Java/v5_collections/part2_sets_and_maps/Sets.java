package v5_collections.part2_sets_and_maps;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;

/*
    =====================================================================
    CONJUNTOS: HASHSET, LINKEDHASHSET Y TREESET — DE CERO A NIVEL TÉCNICO
    =====================================================================

    Ruta de v5_collections/: guía 4 de 10  (part2_sets_and_maps)
    Anterior:  part1_basics/Iterators
    Siguiente: part2_sets_and_maps/Maps

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.

    Índice:
        CAPA 1)  Qué es un Set y para qué sirve
        CAPA 2)  HashSet por dentro: por qué contains es instantáneo
        CAPA 3)  Los usos clásicos de un HashSet
        CAPA 4)  LinkedHashSet: sin repetidos y en orden de llegada
        CAPA 5)  TreeSet: siempre ordenado
        CAPA 6)  TreeSet como NavigableSet: buscar "el más cercano"
        CAPA 7)  TreeSet decide los repetidos con compareTo, no con equals
        CAPA 8)  Set.of: conjuntos inmutables
        CAPA 9)  Cómo elegir
        CAPA 10) Errores comunes
        RESUMEN FINAL
*/
public class Sets {

    public static void main(String[] args) {
        check(setBasicsExample(), "CAPA 1");
        check(classicUsesExample(), "CAPA 3");
        check(linkedHashSetExample(), "CAPA 4");
        check(treeSetExample(), "CAPA 5");
        check(navigableExample(), "CAPA 6");
        check(comparatorDuplicatesExample(), "CAPA 7");
        check(setOfExample(), "CAPA 8");
        System.out.println("Sets: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) QUÉ ES UN SET Y PARA QUÉ SIRVE
        =================================================================

        Un SET (conjunto) es una colección SIN ELEMENTOS REPETIDOS, como
        un conjunto en matemáticas. No tiene posiciones: no hay get(i).

        Para qué se usa, en la práctica, casi siempre es una de dos
        cosas:
        1) GARANTIZAR UNICIDAD: las etiquetas de un artículo, los correos
           de una lista de envío, los permisos de un usuario.
        2) PREGUNTAR "¿ESTÁ?" MUY RÁPIDO: ¿este usuario ya votó?, ¿esta
           palabra es una palabra prohibida?, ¿ya visité este nodo?

        Lo que lo diferencia de una List:
        - add(x) DEVUELVE un boolean: true si lo agregó, false si ya
          estaba. Ese valor es muy útil (CAPA 3).
        - contains(x) es O(1) en un HashSet, contra O(n) en una List
          (CollectionsOverview, CAPA 5).

        Tres implementaciones, que difieren solo en el ORDEN y en la
        velocidad (CollectionsOverview, CAPA 4):
            HashSet         sin orden          O(1)
            LinkedHashSet   orden de llegada   O(1)
            TreeSet         ordenado           O(log n)
    */
    static boolean setBasicsExample() {
        Set<Integer> set = new HashSet<>();
        boolean first = set.add(10);
        set.add(20);
        boolean again = set.add(10);           // ya estaba: no se duplica y add avisa con false

        set.remove(20);

        return first && !again && set.size() == 1 && set.contains(10) && !set.contains(99);
    }

    /*
        =================================================================
        CAPA 2) HASHSET POR DENTRO: POR QUÉ CONTAINS ES INSTANTÁNEO
        =================================================================

        Buscar en una lista es revisar uno por uno. ¿Cómo hace un HashSet
        para saber si algo está SIN revisar todos?

        La idea: un arreglo de "cajones" (buckets). Para guardar un
        elemento:
        1) Calcula su hashCode() (un número; ObjectContract, CAPA 7).
        2) Convierte ese número en el número de un cajón.
        3) Lo guarda en ESE cajón.

        Para preguntar contains(x), hace lo mismo: calcula el cajón de x y
        mira SOLO ahí. Si en ese cajón hay algo, lo compara con equals.
        No importa si el set tiene 10 o 10 millones de elementos: siempre
        mira un cajón. Eso es O(1).

            elementos: "ana", "luis", "mia"
            cajón 0: [ ]
            cajón 1: [ "luis" ]
            cajón 2: [ ]
            cajón 3: [ "ana", "mia" ]   <- dos con el mismo cajón: una "colisión"
            contains("mia"): calcula el cajón 3 y compara solo con 2 elementos

        Consecuencias:
        - Por eso un HashSet NO tiene orden: el orden de los cajones no
          tiene relación con el de llegada ni con el alfabético.
        - Por eso tus objetos NECESITAN equals y hashCode coherentes: si
          dos iguales dieran distinto hashCode, irían a cajones distintos
          y el set aceptaría "duplicados" (CollectionsOverview, CAPA 8).
        - Por eso no debes cambiar un objeto que ya está en un HashSet si
          eso cambia su hashCode: queda en el cajón viejo y se "pierde"
          (ObjectContract, CAPA 8).

        (Dato técnico: un HashSet es por dentro un HashMap que solo usa
        las claves. Cómo crecen los cajones y qué pasa con las colisiones
        está en part2_sets_and_maps/Maps.)
    */

    /*
        =================================================================
        CAPA 3) LOS USOS CLÁSICOS DE UN HASHSET
        =================================================================

        Los ejemplos de tu antigua JavaDataStructuresGuide, que aparecen en casi
        cualquier problema de programación y de entrevista:

        1) ¿HAY REPETIDOS? Recorrer y preguntar si ya se vio. Con el
           boolean de add se simplifica: si add devuelve false, ese valor
           ya estaba.
        2) ELIMINAR DUPLICADOS: meter todo en un set.
        3) "VISITADOS": al recorrer un grafo o una matriz, guardar lo que
           ya se procesó para no repetirlo (algorithms/TreesAndGraphs).
        4) BÚSQUEDA RÁPIDA: convertir una lista grande en set antes de
           hacer muchas preguntas contains.
    */
    static boolean hasDuplicate(int[] values) {
        Set<Integer> seen = new HashSet<>();
        for (int value : values) {
            if (!seen.add(value)) {        // add devuelve false: ya lo habíamos visto
                return true;
            }
        }
        return false;
    }

    static List<String> blockedOnly(List<String> comments, List<String> blockedWords) {
        Set<String> blocked = new HashSet<>(blockedWords);   // una vez: O(n)
        List<String> result = new ArrayList<>();
        for (String comment : comments) {
            if (blocked.contains(comment)) {                 // cada pregunta: O(1), no O(n)
                result.add(comment);
            }
        }
        return result;
    }

    static boolean classicUsesExample() {
        boolean duplicates = hasDuplicate(new int[] {1, 2, 3, 4, 2}) && !hasDuplicate(new int[] {8, 1, 4, 9});

        Set<Integer> unique = new HashSet<>(List.of(5, 5, 2, 8, 2, 1));   // los únicos, sin orden garantizado
        boolean deduplicated = unique.size() == 4 && unique.containsAll(List.of(1, 2, 5, 8));

        List<String> flagged = blockedOnly(List.of("hola", "spam", "gracias", "estafa"), List.of("spam", "estafa"));

        return duplicates && deduplicated && flagged.equals(List.of("spam", "estafa"));
    }

    /*
        =================================================================
        CAPA 4) LINKEDHASHSET: SIN REPETIDOS Y EN ORDEN DE LLEGADA
        =================================================================

        Tu antigua JavaDataStructuresGuide advertía: "HashSet no mantiene orden".
        Al quitar duplicados de [5, 5, 2, 8, 2, 1], muchas veces quieres
        [5, 2, 8, 1], en el orden en que aparecieron.

        LinkedHashSet es un HashSet que ADEMÁS une sus elementos con una
        lista enlazada en el orden de inserción. Así:
        - contains, add y remove siguen siendo O(1);
        - al recorrerlo, sale en el orden en que llegaron;
        - cuesta un poco más de memoria (las referencias de la lista).

        Detalle: volver a agregar un elemento que ya estaba NO cambia su
        posición: conserva la de su primera llegada.

        Es la forma estándar de "quitar duplicados de una lista sin
        cambiar el orden":
            List<T> sinRepetidos = new ArrayList<>(new LinkedHashSet<>(lista));
    */
    static boolean linkedHashSetExample() {
        List<Integer> withDuplicates = List.of(5, 5, 2, 8, 2, 1);
        List<Integer> unique = new ArrayList<>(new LinkedHashSet<>(withDuplicates));

        Set<String> visits = new LinkedHashSet<>();
        visits.add("inicio");
        visits.add("productos");
        visits.add("inicio");                    // ya estaba: conserva su primera posición

        return unique.equals(List.of(5, 2, 8, 1))
                && new ArrayList<>(visits).equals(List.of("inicio", "productos"));
    }

    /*
        =================================================================
        CAPA 5) TREESET: SIEMPRE ORDENADO
        =================================================================

        TreeSet mantiene sus elementos ORDENADOS en todo momento: los
        números de menor a mayor, los textos alfabéticamente (por Unicode;
        Strings, CAPA 7), o con el Comparator que le des.

        Por dentro es un ÁRBOL de búsqueda balanceado (un "árbol
        rojo-negro"): cada nodo tiene a la izquierda los menores y a la
        derecha los mayores, y el árbol se reacomoda para mantenerse
        equilibrado. Para buscar, en cada nodo decides "izquierda o
        derecha" y descartas la mitad, como en la búsqueda binaria
        (v1_fundamentals/.../Arrays, CAPA 6). Por eso sus operaciones son
        O(log n): un poco más lentas que un HashSet, pero siempre en orden.

        Requisitos:
        - Los elementos deben poder COMPARARSE: implementar Comparable
          (ObjectContract, CAPA 9) o pasar un Comparator al crearlo. Si no,
          falla al agregar (ClassCastException).
        - No acepta null (no se puede comparar null): NullPointerException.
          HashSet sí acepta un null.
    */
    static boolean treeSetExample() {
        Set<String> sorted = new TreeSet<>(List.of("pera", "manzana", "uva", "kiwi"));
        Set<Integer> descending = new TreeSet<>((a, b) -> Integer.compare(b, a));   // con un Comparator
        descending.addAll(List.of(3, 10, 7));

        boolean rejectsNull;
        try {
            new TreeSet<String>().add(null);
            rejectsNull = false;
        } catch (NullPointerException e) {
            rejectsNull = true;
        }

        return new ArrayList<>(sorted).equals(List.of("kiwi", "manzana", "pera", "uva"))
                && new ArrayList<>(descending).equals(List.of(10, 7, 3))
                && rejectsNull;
    }

    /*
        =================================================================
        CAPA 6) TREESET COMO NAVIGABLESET: BUSCAR "EL MÁS CERCANO"
        =================================================================

        Como TreeSet está ordenado, puede responder preguntas que un
        HashSet no puede, todas en O(log n). Están en la interface
        NavigableSet:

            first(), last()        el menor y el mayor
            ceiling(x)             el menor que sea >= x   ("techo")
            floor(x)               el mayor que sea <= x   ("piso")
            higher(x), lower(x)    estrictamente mayor / menor que x
            headSet(x)             los menores que x
            tailSet(x)             los mayores o iguales que x
            subSet(desde, hasta)   el rango [desde, hasta)
            descendingSet()        vista en orden inverso
        (ceiling, floor, higher y lower devuelven null si no hay ninguno.)

        Esto resuelve problemas reales con una línea: "¿cuál es el
        siguiente horario disponible a partir de las 10:20?", "¿qué
        calificación es la más cercana por debajo de 80?".
    */
    static LocalTime nextAvailable(NavigableSet<LocalTime> freeSlots, LocalTime requested) {
        return freeSlots.ceiling(requested);         // el primer horario libre a partir del pedido
    }

    static boolean navigableExample() {
        NavigableSet<Integer> scores = new TreeSet<>(List.of(70, 85, 90, 100));

        boolean navigation = scores.first() == 70 && scores.last() == 100
                && scores.ceiling(80) == 85 && scores.floor(80) == 70
                && scores.higher(90) == 100 && scores.lower(70) == null
                && new ArrayList<>(scores.headSet(90)).equals(List.of(70, 85))
                && new ArrayList<>(scores.subSet(80, 95)).equals(List.of(85, 90))
                && new ArrayList<>(scores.descendingSet()).equals(List.of(100, 90, 85, 70));

        NavigableSet<LocalTime> slots = new TreeSet<>(List.of(
                LocalTime.of(9, 0), LocalTime.of(10, 30), LocalTime.of(12, 0)));

        return navigation
                && nextAvailable(slots, LocalTime.of(10, 20)).equals(LocalTime.of(10, 30))
                && nextAvailable(slots, LocalTime.of(13, 0)) == null;   // ya no hay horarios
    }

    /*
        =================================================================
        CAPA 7) TREESET DECIDE LOS REPETIDOS CON COMPARETO, NO CON EQUALS
        =================================================================

        Un detalle técnico que causa errores reales:
        - HashSet decide si dos elementos son "el mismo" con equals (y
          hashCode).
        - TreeSet lo decide con compareTo (o con su Comparator): si da 0,
          son el mismo, aunque equals diga que no.

        Ejemplo: un TreeSet de nombres que ignora mayúsculas
        (String.CASE_INSENSITIVE_ORDER). Para él, "Ana" y "ana" son el
        mismo elemento y guarda solo el primero; un HashSet guardaría los
        dos.

        Puede ser exactamente lo que quieres (nombres únicos sin importar
        mayúsculas) o un error silencioso (un Comparator que solo compara
        el precio hace que dos productos distintos con el mismo precio se
        consideren repetidos, y uno desaparece). Es la consecuencia de que
        compareTo no sea "consistente con equals" (ObjectContract, CAPA 9).
    */
    record Product(String name, int price) { }

    static boolean comparatorDuplicatesExample() {
        Set<String> caseInsensitive = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        caseInsensitive.add("Ana");
        caseInsensitive.add("ana");                          // compare da 0: "repetido"

        Set<String> hashed = new HashSet<>(List.of("Ana", "ana"));   // equals los distingue

        Set<Product> byPriceOnly = new TreeSet<>((a, b) -> Integer.compare(a.price(), b.price()));
        byPriceOnly.add(new Product("teclado", 500));
        byPriceOnly.add(new Product("mouse", 500));          // mismo precio: ¡se pierde el mouse!

        return caseInsensitive.size() == 1 && hashed.size() == 2 && byPriceOnly.size() == 1;
    }

    /*
        Si ordenas por un campo que puede repetirse, desempata con otro
        para que el Comparator solo dé 0 cuando de verdad son iguales:
            Comparator.comparing(Product::price).thenComparing(Product::name)
        (part4_tools/Sorting.)
    */

    /*
        =================================================================
        CAPA 8) SET.OF: CONJUNTOS INMUTABLES
        =================================================================

        Set.of(a, b, c) crea un conjunto que no se puede modificar
        (part4_tools/ImmutableCollections). Tres detalles que lo distinguen
        de new HashSet<>(...):

        1) RECHAZA DUPLICADOS AL CREARSE: Set.of("a", "a") lanza
           IllegalArgumentException ("duplicate element: a"). Por qué:
           escribir el mismo valor dos veces en un conjunto fijo es casi
           seguro un error de tipeo, y Java prefiere avisarte. Para quitar
           duplicados de datos que vienen de fuera usa new HashSet<>(...)
           o Set.copyOf(...), que sí los aceptan.
        2) RECHAZA null.
        3) SU ORDEN DE RECORRIDO CAMBIA ENTRE EJECUCIONES. Java lo
           mezcla a propósito con un valor aleatorio al arrancar, para que
           nadie escriba código que dependa de un orden que nunca fue
           garantizado. (Ejecuta dos veces un programa que imprima
           Set.of("uno", "dos", "tres") y verás órdenes distintos.)
    */
    static boolean setOfExample() {
        Set<String> colors = Set.of("rojo", "verde", "azul");

        boolean rejectsDuplicates;
        try {
            Set.of("a", "a");
            rejectsDuplicates = false;
        } catch (IllegalArgumentException e) {
            rejectsDuplicates = e.getMessage().equals("duplicate element: a");
        }

        boolean copyOfAcceptsDuplicates = Set.copyOf(List.of("a", "a", "b")).size() == 2;

        boolean immutable;
        try {
            colors.add("negro");
            immutable = false;
        } catch (UnsupportedOperationException e) {
            immutable = true;
        }

        return colors.contains("verde") && rejectsDuplicates && copyOfAcceptsDuplicates && immutable;
    }

    /*
        =================================================================
        CAPA 9) CÓMO ELEGIR
        =================================================================

        | Necesitas                                        | Usa                |
        |--------------------------------------------------|--------------------|
        | Unicidad o contains rápido, sin importar el orden| HashSet            |
        | Lo mismo, pero respetando el orden de llegada    | LinkedHashSet      |
        | Siempre ordenado, o buscar "el más cercano"      | TreeSet            |
        | Un conjunto fijo que no cambia                   | Set.of             |
        | Valores de un enum                               | EnumSet (v2_oop/part4_advanced_types/Enums, CAPA 7) |
        | Muchos hilos a la vez                            | ConcurrentHashMap.newKeySet() (v13_concurrency/) |

        Por defecto: HashSet. Cámbialo solo si necesitas un orden.
    */

    /*
        =================================================================
        CAPA 10) ERRORES COMUNES
        =================================================================

        1) Usar una List y contains para preguntar "¿está?" muchas veces
           (CAPA 1).

        2) Ignorar el boolean que devuelve add (CAPA 3).

        3) Esperar que un HashSet conserve el orden (CAPAS 2 y 4).

        4) Meter en un HashSet objetos sin equals/hashCode, o cambiar
           objetos que ya están dentro (CAPA 2).

        5) Meter en un TreeSet objetos que no se pueden comparar, o null
           (CAPA 5).

        6) Un Comparator que da 0 para elementos distintos: el TreeSet
           pierde elementos en silencio (CAPA 7).

        7) Usar Set.of con datos que pueden traer duplicados (CAPA 8).

        8) Depender del orden de Set.of o HashSet en pruebas: pasan hoy y
           fallan mañana (CAPA 8).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Set: sin repetidos, sin posiciones. add devuelve si agregó.
        - HashSet: cajones por hashCode; contains O(1); sin orden. Necesita
          equals/hashCode correctos.
        - Usos: detectar repetidos, quitar duplicados, visitados, búsqueda
          rápida.
        - LinkedHashSet: O(1) y orden de llegada; quita duplicados sin
          perder el orden.
        - TreeSet: árbol balanceado, ordenado, O(log n); first, last,
          ceiling, floor, subSet... Necesita compareTo/Comparator; no
          acepta null; decide repetidos con compareTo.
        - Set.of: inmutable, rechaza duplicados y null, orden cambiante.
        - Por defecto, HashSet.
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
