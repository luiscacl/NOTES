package v5_collections.part1_basics;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/*
    =====================================================================
    PANORAMA DE LAS COLECCIONES — DE CERO A NIVEL TÉCNICO
    =====================================================================

    Ruta de v5_collections/: guía 1 de 10  (part1_basics)
    Anterior:  — (es la primera; viene de v4_generics/AdvancedGenerics)
    Siguiente: part1_basics/Lists

    Requisito: v4_generics/GenericsBasics y v1_fundamentals/.../Arrays.

    Esta guía es el MAPA de toda la carpeta v5_collections/: qué estructuras
    existen, en qué se diferencian y cómo elegir. Cada una se estudia a
    fondo en su propia guía.

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.

    Índice:
        CAPA 1)  Qué es una colección y por qué no bastan los arreglos
        CAPA 2)  La jerarquía: interfaces e implementaciones
        CAPA 3)  Las cuatro grandes ideas: List, Set, Queue, Map
        CAPA 4)  Implementaciones: misma interface, distinto comportamiento
        CAPA 5)  El costo de cada operación (Big-O en una tabla)
        CAPA 6)  Lo que TODA colección sabe hacer
        CAPA 7)  Cómo recorrer cada estructura
        CAPA 8)  Lo que las colecciones esperan de tus objetos
        CAPA 9)  Las colecciones antiguas que ya no se usan
        CAPA 10) Cómo elegir la colección correcta
        CAPA 11) Errores comunes
        RESUMEN FINAL
*/
public class CollectionsOverview {

    public static void main(String[] args) {
        check(whyCollectionsExample(), "CAPA 1");
        check(fourIdeasExample(), "CAPA 3");
        check(implementationsExample(), "CAPA 4");
        check(commonOperationsExample(), "CAPA 6");
        check(iterationExample(), "CAPA 7");
        check(objectRequirementsExample(), "CAPA 8");
        System.out.println("CollectionsOverview: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) QUÉ ES UNA COLECCIÓN Y POR QUÉ NO BASTAN LOS ARREGLOS
        =================================================================

        Una COLECCIÓN es un objeto que agrupa varios elementos: la lista
        de productos de un carrito, el conjunto de etiquetas de un
        artículo, el mapa de clientes por id.

        Ya tienes arreglos, pero tienen límites serios
        (v1_fundamentals/.../Arrays):
        - tamaño FIJO: no pueden crecer;
        - casi NINGÚN método: ni contains, ni remove, ni nada;
        - solo una forma de organizar los datos: por posición.

        Por eso, desde Java 1.2 (1998), existe el JAVA COLLECTIONS
        FRAMEWORK: un conjunto de interfaces y clases en java.util que
        resuelven las formas más comunes de organizar datos. No es una
        sola clase: son varias, cada una optimizada para un uso distinto.
        Elegir bien entre ellas es una de las decisiones que más afecta la
        claridad y la velocidad de un programa.
    */
    static boolean whyCollectionsExample() {
        List<String> cart = new ArrayList<>();
        cart.add("teclado");
        cart.add("mouse");                   // crece sola, a diferencia de un arreglo
        cart.remove("teclado");              // y tiene métodos para todo

        return cart.size() == 1 && cart.contains("mouse");
    }

    /*
        =================================================================
        CAPA 2) LA JERARQUÍA: INTERFACES E IMPLEMENTACIONES
        =================================================================

        El framework separa el QUÉ (interfaces) del CÓMO (clases que las
        implementan), exactamente como en v2_oop/part2_pillars/Abstraction:

                              Iterable<E>           "se puede recorrer con for-each"
                                   │
                             Collection<E>          "un grupo de elementos"
                ┌──────────────────┼──────────────────┐
             List<E>            Set<E>             Queue<E>
          (orden, índice,    (sin repetidos)    (orden de atención)
           repetidos)             │                   │
                ArrayList     HashSet             Deque<E> (por los dos extremos)
                LinkedList    LinkedHashSet           ArrayDeque
                              TreeSet             PriorityQueue
                                                  LinkedList

                   Map<K, V>   "pares clave -> valor"   (NO es una Collection)
                      HashMap   LinkedHashMap   TreeMap

        ¿Por qué Map no hereda de Collection? Porque no guarda ELEMENTOS
        sueltos sino PARES clave-valor. Un add(elemento) no tendría
        sentido en un mapa. Aun así es parte del framework y se usa igual
        de seguido.

        LA REGLA PROFESIONAL (Polymorphism, CAPA 5): declara las variables,
        parámetros y retornos con la INTERFACE, y elige la implementación
        solo en el new:
            List<String> names = new ArrayList<>();      // bien
            ArrayList<String> names = new ArrayList<>(); // amarra el código a una implementación
        Así, cambiar ArrayList por otra lista es cambiar UNA palabra.
    */

    /*
        =================================================================
        CAPA 3) LAS CUATRO GRANDES IDEAS: LIST, SET, QUEUE, MAP
        =================================================================

        Antes de pensar en clases, piensa en QUÉ necesitas expresar:

        LIST — una secuencia en ORDEN, con POSICIONES y REPETIDOS.
            "Los productos del carrito, en el orden en que se agregaron;
             puedo comprar dos veces lo mismo."

        SET — un conjunto SIN REPETIDOS.
            "Las etiquetas de un artículo; agregar 'oferta' dos veces no
             tiene sentido."

        QUEUE / DEQUE — una fila de elementos POR ATENDER, en un orden
        definido (el primero que llega, el más urgente, el último que
        entró...).
            "Los pedidos esperando a ser preparados."

        MAP — una relación CLAVE -> VALOR, con claves únicas.
            "El stock de cada producto, buscado por su código."

        La misma operación se comporta distinto según la idea:
    */
    static boolean fourIdeasExample() {
        List<String> list = new ArrayList<>();
        list.add("café");
        list.add("café");                     // la lista acepta repetidos
        boolean listKeepsDuplicates = list.size() == 2 && list.get(0).equals("café");

        Set<String> tags = new HashSet<>();
        tags.add("oferta");
        boolean addedAgain = tags.add("oferta");   // el set lo rechaza: add devuelve false
        boolean setRejectsDuplicates = tags.size() == 1 && !addedAgain;

        Queue<String> orders = new ArrayDeque<>();
        orders.offer("pedido 1");
        orders.offer("pedido 2");
        boolean queueServesFirstCome = orders.poll().equals("pedido 1");   // el primero que llegó

        Map<String, Integer> stock = new HashMap<>();
        stock.put("TEC-01", 15);
        stock.put("TEC-01", 12);              // misma clave: REEMPLAZA el valor
        boolean mapReplacesValue = stock.size() == 1 && stock.get("TEC-01") == 12;

        return listKeepsDuplicates && setRejectsDuplicates && queueServesFirstCome && mapReplacesValue;
    }

    /*
        =================================================================
        CAPA 4) IMPLEMENTACIONES: MISMA INTERFACE, DISTINTO COMPORTAMIENTO
        =================================================================

        Cada interface tiene varias implementaciones. Cumplen el MISMO
        contrato, pero difieren en dos cosas: la VELOCIDAD de cada
        operación (CAPA 5) y el ORDEN en que devuelven los elementos.

        El ejemplo más claro son los tres Set (y lo mismo pasa con los tres
        Map):
        - HashSet:        sin orden garantizado (el más rápido).
        - LinkedHashSet:  en el orden en que se INSERTARON.
        - TreeSet:        ORDENADOS (alfabético, numérico...).

        Por qué HashSet no tiene orden: guarda cada elemento en una
        posición calculada a partir de su hashCode (por eso es tan
        rápido: no tiene que buscar). Esa posición no tiene nada que ver
        con el orden de llegada ni con el alfabético. El orden que veas
        al recorrerlo puede incluso cambiar entre versiones de Java: NUNCA
        dependas de él.
    */
    static boolean implementationsExample() {
        List<String> input = List.of("pera", "manzana", "uva", "kiwi");

        Set<String> insertionOrder = new LinkedHashSet<>(input);
        Set<String> sorted = new TreeSet<>(input);
        Set<String> hashed = new HashSet<>(input);

        Map<String, Integer> sortedByKey = new TreeMap<>(Map.of("c", 3, "a", 1, "b", 2));

        return new ArrayList<>(insertionOrder).equals(List.of("pera", "manzana", "uva", "kiwi"))
                && new ArrayList<>(sorted).equals(List.of("kiwi", "manzana", "pera", "uva"))
                && hashed.size() == 4 && hashed.containsAll(input)       // del HashSet solo se garantiza el contenido
                && new ArrayList<>(sortedByKey.keySet()).equals(List.of("a", "b", "c"));
    }

    /*
        =================================================================
        CAPA 5) EL COSTO DE CADA OPERACIÓN (BIG-O EN UNA TABLA)
        =================================================================

        La notación Big-O dice cómo CRECE el tiempo de una operación cuando
        crece la cantidad de elementos (n). Se estudia a fondo en
        algorithms/BigOComplexity; lo mínimo para esta tabla:

            O(1)       tiempo constante: igual con 10 que con 10 millones
            O(log n)   crece muy lento: un millón de elementos, ~20 pasos
            O(n)       crece en proporción: el doble de datos, el doble de tiempo

        | Estructura    | Buscar por posición | Contiene / buscar | Agregar        | Quitar         | Orden al recorrer |
        |---------------|---------------------|-------------------|----------------|----------------|-------------------|
        | ArrayList     | O(1)                | O(n)              | O(1)* al final | O(n)           | de inserción      |
        | LinkedList    | O(n)                | O(n)              | O(1) extremos  | O(1) extremos  | de inserción      |
        | HashSet       | —                   | O(1)              | O(1)           | O(1)           | ninguno           |
        | LinkedHashSet | —                   | O(1)              | O(1)           | O(1)           | de inserción      |
        | TreeSet       | —                   | O(log n)          | O(log n)       | O(log n)       | ordenado          |
        | HashMap       | —                   | O(1) por clave    | O(1)           | O(1)           | ninguno           |
        | LinkedHashMap | —                   | O(1) por clave    | O(1)           | O(1)           | de inserción      |
        | TreeMap       | —                   | O(log n) por clave| O(log n)       | O(log n)       | ordenado por clave|
        | ArrayDeque    | —                   | O(n)              | O(1) extremos  | O(1) extremos  | de la fila        |
        | PriorityQueue | —                   | O(n)              | O(log n)       | O(log n) el primero | ninguno (por prioridad al sacar) |

        * "O(1) amortizado": casi siempre instantáneo; de vez en cuando
          tiene que crecer y copiar (v1_fundamentals/.../Arrays, CAPA 4).
        Los Hash* son O(1) "en promedio": dependen de un buen hashCode
        (v5_collections/part2_sets_and_maps/Maps lo explica por dentro).

        La lección más importante de la tabla: list.contains(x) es O(n):
        revisa uno por uno. Si haces muchos contains sobre muchos
        elementos, un Set es MILES de veces más rápido.
    */

    /*
        =================================================================
        CAPA 6) LO QUE TODA COLECCIÓN SABE HACER
        =================================================================

        Toda List, Set y Queue hereda los métodos de Collection:

            size(), isEmpty()                     tamaño
            contains(x), containsAll(otra)        ¿está?
            add(x), addAll(otra)                  agregar
            remove(x), removeAll(otra), clear()   quitar
            retainAll(otra)                       quedarse solo con los que estén en otra
            removeIf(condición)                   quitar los que cumplan algo
            forEach(acción), stream()             recorrer y procesar (v7_functional/)
            toArray(...)                          convertir a arreglo

        addAll, retainAll y removeAll son las operaciones de CONJUNTOS de
        matemáticas: unión, intersección y diferencia. Con dos Set son la
        forma más clara de responder "¿qué tienen en común?" o "¿qué le
        falta a uno?".

        Por qué conviene escribir métodos que reciban Collection<E> (y no
        List o Set): si tu método solo necesita recorrer o preguntar
        contains, acepta CUALQUIER colección. Pide lo mínimo
        (Interfaces, CAPA 10).
    */
    static int countAvailable(Collection<String> wanted, Collection<String> inStock) {
        int count = 0;
        for (String item : wanted) {
            if (inStock.contains(item)) {
                count++;
            }
        }
        return count;
    }

    static boolean commonOperationsExample() {
        Set<String> anaLikes = new HashSet<>(Set.of("cine", "música", "fútbol"));
        Set<String> luisLikes = Set.of("música", "fútbol", "ajedrez");

        Set<String> union = new HashSet<>(anaLikes);
        union.addAll(luisLikes);                              // todo lo de ambos

        Set<String> common = new HashSet<>(anaLikes);
        common.retainAll(luisLikes);                          // lo que comparten

        Set<String> onlyAna = new HashSet<>(anaLikes);
        onlyAna.removeAll(luisLikes);                         // lo de Ana que Luis no tiene

        List<Integer> numbers = new ArrayList<>(List.of(1, 2, 3, 4, 5, 6));
        numbers.removeIf(n -> n % 2 == 0);                    // quita los pares

        // countAvailable acepta una List y un Set: pide Collection.
        int available = countAvailable(List.of("mouse", "monitor", "cable"), Set.of("mouse", "cable"));

        return union.size() == 4
                && common.equals(Set.of("música", "fútbol"))
                && onlyAna.equals(Set.of("cine"))
                && numbers.equals(List.of(1, 3, 5))
                && available == 2;
    }

    /*
        =================================================================
        CAPA 7) CÓMO RECORRER CADA ESTRUCTURA
        =================================================================

        Las reglas de tu antiguo CheatSheet, que cubren el 95 % de los casos:

        - Solo los valores, sin posición -> for-each:  for (Tipo x : coleccion)
          Sirve para List, Set, Queue y cualquier Collection (todas son
          Iterable), y para map.keySet() y map.values().
        - Necesitas la posición -> for clásico con list.get(i)
          (solo en List; y solo con ArrayList, porque en LinkedList get(i)
          es O(n): Lists).
        - Un Map con clave Y valor a la vez -> for-each sobre entrySet():
              for (Map.Entry<K, V> entry : map.entrySet()) { entry.getKey(); entry.getValue(); }
          Es mejor que recorrer keySet() y hacer get(clave) en cada vuelta:
          entrySet ya trae ambos, sin buscar otra vez.
        - Queue / Deque cuando se ATIENDEN -> se consumen con poll() hasta
          que estén vacías.
        - Para quitar elementos mientras recorres -> removeIf o un Iterator
          (part1_basics/Iterators), nunca remove dentro del for-each.
    */
    static boolean iterationExample() {
        Map<String, Integer> stock = new LinkedHashMap<>();
        stock.put("teclado", 15);
        stock.put("mouse", 0);
        stock.put("monitor", 4);

        List<String> outOfStock = new ArrayList<>();
        int totalUnits = 0;
        for (Map.Entry<String, Integer> entry : stock.entrySet()) {   // clave y valor juntos
            totalUnits += entry.getValue();
            if (entry.getValue() == 0) {
                outOfStock.add(entry.getKey());
            }
        }

        Deque<String> pending = new ArrayDeque<>(List.of("pedido 1", "pedido 2", "pedido 3"));
        int served = 0;
        while (!pending.isEmpty()) {
            pending.poll();                                           // atender = sacar
            served++;
        }

        return totalUnits == 19 && outOfStock.equals(List.of("mouse")) && served == 3 && pending.isEmpty();
    }

    /*
        =================================================================
        CAPA 8) LO QUE LAS COLECCIONES ESPERAN DE TUS OBJETOS
        =================================================================

        Las colecciones no saben nada de tus clases. Para decidir si dos
        objetos son "el mismo" o cuál va primero, llaman a métodos de tus
        objetos, y CONFÍAN en que cumplan su contrato
        (v2_oop/part4_advanced_types/ObjectContract):

        | Colección                        | Usa                          |
        |----------------------------------|------------------------------|
        | List.contains, indexOf, remove   | equals                       |
        | HashSet, HashMap (y Linked*)     | hashCode y equals            |
        | TreeSet, TreeMap, PriorityQueue  | compareTo o un Comparator    |

        Si tu clase no sobrescribe equals y hashCode, dos objetos con los
        mismos datos son DISTINTOS para un HashSet, y vas a tener
        "duplicados". Un record los trae correctos (Records, CAPA 9).
    */
    static final class ProductWithoutEquals {
        private final String sku;

        ProductWithoutEquals(String sku) {
            this.sku = sku;
        }

        String getSku() {
            return sku;
        }
    }

    record Product(String sku) { }

    static boolean objectRequirementsExample() {
        Set<ProductWithoutEquals> broken = new HashSet<>();
        broken.add(new ProductWithoutEquals("TEC-01"));
        broken.add(new ProductWithoutEquals("TEC-01"));      // mismos datos, pero sin equals/hashCode: "distintos"

        Set<Product> correct = new HashSet<>();
        correct.add(new Product("TEC-01"));
        correct.add(new Product("TEC-01"));                  // el record sí sabe que son iguales

        return broken.size() == 2 && correct.size() == 1;
    }

    /*
        =================================================================
        CAPA 9) LAS COLECCIONES ANTIGUAS QUE YA NO SE USAN
        =================================================================

        Antes del framework (Java 1.0) existían otras clases que todavía
        verás en código viejo y en tutoriales desactualizados:

        | Antigua      | Úsala en su lugar          | Por qué                                    |
        |--------------|----------------------------|--------------------------------------------|
        | Vector       | ArrayList                  | Vector sincroniza CADA operación: lento    |
        | Hashtable    | HashMap                    | igual: sincronizado y no acepta null       |
        | Stack        | ArrayDeque                 | Stack hereda de Vector (y de su lentitud)  |
        | Enumeration  | Iterator / for-each        | interfaz vieja para recorrer               |

        "Sincronizar" significa protegerse para varios hilos a la vez. Pero
        la forma en que lo hacían estas clases es lenta y además no basta
        para casi ningún caso real. Si de verdad necesitas compartir una
        colección entre hilos, existen ConcurrentHashMap y compañía
        (v13_concurrency/ConcurrentCollections).
    */

    /*
        =================================================================
        CAPA 10) CÓMO ELEGIR LA COLECCIÓN CORRECTA
        =================================================================

        Pregúntate, en este orden:

        1) ¿Necesito buscar valores por una CLAVE?            -> Map
               ¿ordenados por clave?                          -> TreeMap
               ¿en el orden en que se insertaron?             -> LinkedHashMap
               si no                                          -> HashMap
        2) ¿Los elementos NO deben repetirse, o pregunto
           mucho "¿está x?"?                                  -> Set
               (TreeSet / LinkedHashSet / HashSet, con el mismo criterio de orden)
        3) ¿Los elementos esperan a ser ATENDIDOS en un orden?
               el primero que llegó (FIFO) o el último (LIFO) -> ArrayDeque
               el más prioritario                             -> PriorityQueue
        4) En cualquier otro caso                             -> ArrayList
           (es la colección por defecto: usa ArrayList salvo que tengas un
           motivo para otra)

        EQUIVALENCIAS con otros lenguajes (ampliando la de tu antiguo CheatSheet):

        | Java                         | Python                   | JavaScript         |
        |------------------------------|--------------------------|--------------------|
        | ArrayList<T> / List<T>       | list                     | Array              |
        | HashMap<K, V>                | dict                     | Map (u objeto {})  |
        | HashSet<T>                   | set                      | Set                |
        | ArrayDeque (pila)            | list (append / pop)      | Array (push / pop) |
        | ArrayDeque (fila FIFO)       | collections.deque        | Array (push / shift) |
        | PriorityQueue                | heapq                    | (no existe)        |
        | TreeMap / TreeSet            | (no existe; sortedcontainers) | (no existe)   |
    */

    /*
        =================================================================
        CAPA 11) ERRORES COMUNES
        =================================================================

        1) Declarar con la implementación (ArrayList<String> x) en lugar
           de la interface (List<String> x) (CAPA 2).

        2) Usar una List para preguntar "¿está?" muchas veces sobre muchos
           datos: contains es O(n); un Set es O(1) (CAPA 5).

        3) Depender del orden de un HashSet o HashMap (CAPA 4).

        4) Meter en un Hash* objetos sin equals/hashCode, o en un Tree*
           objetos que no se pueden comparar (CAPA 8).

        5) Recorrer un Map con keySet() + get() en lugar de entrySet()
           (CAPA 7).

        6) Quitar elementos dentro de un for-each (CAPA 7).

        7) Usar Vector, Hashtable o Stack en código nuevo (CAPA 9).

        8) Elegir LinkedList "porque agregar es O(1)": en la práctica casi
           siempre ArrayList es más rápida (Lists lo explica).

        9) Recibir List o Set en un método que solo necesita recorrer:
           Collection o Iterable aceptan más (CAPA 6).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Colecciones: el framework de java.util para agrupar datos; crecen
          y traen métodos, a diferencia de los arreglos.
        - Interfaces: List (orden, índice, repetidos), Set (sin repetidos),
          Queue/Deque (por atender), Map (clave -> valor; no es Collection).
        - Declara con la interface; elige la implementación en el new.
        - Hash*: lo más rápido, sin orden. Linked*: orden de inserción.
          Tree*: ordenado, O(log n).
        - contains en List es O(n); en Set, O(1).
        - Toda Collection: size, contains, add, remove, addAll / retainAll /
          removeAll (unión / intersección / diferencia), removeIf.
        - Map: recórrelo con entrySet.
        - Hash* necesita equals/hashCode; Tree* necesita compareTo.
        - Nada de Vector, Hashtable ni Stack.
        - Por defecto: ArrayList y HashMap.
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
