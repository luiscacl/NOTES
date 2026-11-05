package v5_collections.part2_sets_and_maps;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;

/*
    =====================================================================
    MAPAS: HASHMAP, LINKEDHASHMAP Y TREEMAP — DE CERO A NIVEL TÉCNICO
    =====================================================================

    Ruta de collections/: guía 5 de 10  (part2_sets_and_maps)
    Anterior:  part2_sets_and_maps/Sets
    Siguiente: part3_queues/QueuesAndStacks

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.

    Índice:
        CAPA 1)  Qué es un Map y para qué sirve
        CAPA 2)  Las operaciones básicas (y lo que devuelven)
        CAPA 3)  get devuelve null: ¿no existe o vale null?
        CAPA 4)  HashMap por dentro: hash, cajones y colisiones
        CAPA 5)  Capacidad, load factor y por qué a veces crece
        CAPA 6)  Lo que una clave necesita
        CAPA 7)  Recorrer un mapa
        CAPA 8)  Contar: getOrDefault y merge
        CAPA 9)  Agrupar: computeIfAbsent
        CAPA 10) compute y computeIfPresent: actualizar o borrar en un paso
        CAPA 11) LinkedHashMap: orden de llegada y caché LRU
        CAPA 12) TreeMap: claves ordenadas y "el más cercano"
        CAPA 13) Map.of: mapas inmutables
        CAPA 14) Cómo elegir
        CAPA 15) Errores comunes
        RESUMEN FINAL
*/
public class Maps {

    public static void main(String[] args) {
        check(basicOperationsExample(), "CAPA 2");
        check(nullValuesExample(), "CAPA 3");
        check(collisionsExample(), "CAPA 4");
        check(resizeModelExample(), "CAPA 5");
        check(keysExample(), "CAPA 6");
        check(iterationExample(), "CAPA 7");
        check(countingExample(), "CAPA 8");
        check(groupingExample(), "CAPA 9");
        check(computeExample(), "CAPA 10");
        check(linkedHashMapExample(), "CAPA 11");
        check(treeMapExample(), "CAPA 12");
        check(mapOfExample(), "CAPA 13");
        check(recursiveComputeFails(), "CAPA 15");
        System.out.println("Maps: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) QUÉ ES UN MAP Y PARA QUÉ SIRVE
        =================================================================

        Un MAP guarda PARES clave -> valor, como un diccionario: buscas
        por la palabra (la clave) y obtienes su definición (el valor).

            "Luis"  -> 25
            "Ana"   -> 30
            "Carlos"-> 28

        Las reglas:
        - Cada CLAVE es única. Si guardas otra vez la misma clave,
          REEMPLAZAS su valor (no se agrega un segundo par).
        - Los VALORES sí pueden repetirse.
        - Buscas siempre por clave, nunca por posición: no hay get(i).

        Map NO es una Collection (CollectionsOverview, CAPA 2): no guarda
        elementos sueltos sino pares. Pero ofrece vistas que sí lo son
        (CAPA 7).

        Para qué se usa (de tu antigua JavaDataStructuresGuide):
        - buscar rápido por un identificador (usuario por id, producto por
          código);
        - contar frecuencias (CAPA 8);
        - agrupar (CAPA 9);
        - recordar resultados ya calculados (memoización, algorithms/);
        - índices y tablas de acceso (código de país -> nombre).

        Diferencia mental contra JS: en JavaScript usas un objeto literal
        { "Luis": 25 } o un Map. En Java un objeto normal NO cumple ese
        papel: sus campos se fijan al escribir la clase y no se pueden
        agregar en tiempo de ejecución. Para claves dinámicas, siempre
        Map.

        Las tres implementaciones repiten la historia de los Set (de hecho
        un HashSet es un HashMap que solo usa las claves; Sets, CAPA 2):
            HashMap         sin orden          O(1)
            LinkedHashMap   orden de llegada   O(1)
            TreeMap         claves ordenadas   O(log n)
    */

    /*
        =================================================================
        CAPA 2) LAS OPERACIONES BÁSICAS (Y LO QUE DEVUELVEN)
        =================================================================

            put(k, v)          guarda o reemplaza; DEVUELVE el valor anterior
                               (o null si la clave no estaba)
            get(k)             el valor, o null si la clave no está
            containsKey(k)     ¿está la clave?            O(1)
            containsValue(v)   ¿está el valor?            O(n): revisa todos
            remove(k)          quita el par; devuelve el valor que tenía
            putIfAbsent(k, v)  guarda SOLO si la clave no estaba
            size(), isEmpty()

        Dos detalles que mucha gente no aprovecha:
        - put devuelve el valor ANTERIOR. Con eso sabes, en la misma
          operación, si la clave ya existía.
        - containsValue es lento: un mapa está organizado por CLAVE. Si
          buscas mucho por valor, probablemente necesitas otro mapa al
          revés (valor -> clave).
    */
    static boolean basicOperationsExample() {
        Map<String, Integer> ages = new HashMap<>();
        Integer firstPut = ages.put("Luis", 25);       // no existía: devuelve null
        ages.put("Ana", 30);
        ages.put("Carlos", 28);
        Integer replaced = ages.put("Luis", 26);       // existía: reemplaza y devuelve el viejo

        Integer removed = ages.remove("Carlos");
        ages.putIfAbsent("Ana", 100);                  // Ana ya está: no cambia
        ages.putIfAbsent("Pedro", 40);                 // Pedro no estaba: se agrega

        return firstPut == null && replaced == 25 && removed == 28
                && ages.size() == 3
                && ages.get("Luis") == 26 && ages.get("Ana") == 30 && ages.get("Pedro") == 40
                && ages.containsKey("Ana") && !ages.containsKey("Carlos")
                && ages.containsValue(40);
    }

    /*
        =================================================================
        CAPA 3) GET DEVUELVE NULL: ¿NO EXISTE O VALE NULL?
        =================================================================

        get devuelve null cuando la clave NO ESTÁ. Eso trae dos problemas.

        1) EL UNBOXING DE NULL. Si el valor es un wrapper y lo guardas en
           un primitivo, Java lo "desenvuelve" y explota:
               int age = ages.get("Pedro");      // NullPointerException
           (v1_fundamentals/part3_text_and_references/Wrappers, CAPA 4.)
           Guárdalo en Integer y revisa null, o usa getOrDefault.

        2) LA AMBIGÜEDAD. HashMap PERMITE valores null (y una clave null).
           Entonces get(k) == null puede significar "no está" o "está y
           vale null". Solo containsKey distingue los dos casos.

        Y una trampa más fina: getOrDefault(k, x) devuelve x solo si la
        clave NO ESTÁ. Si está con valor null, devuelve null, no x.

        Por eso la regla profesional es: NO GUARDES null COMO VALOR. Si
        algo "no tiene valor", no lo pongas en el mapa. Así get == null
        significa siempre "no está", y getOrDefault funciona como esperas.
        (Map.of y ConcurrentHashMap ni siquiera aceptan null.)
    */
    static boolean nullValuesExample() {
        Map<String, Integer> ages = new HashMap<>();
        ages.put("Luis", 25);
        ages.put("Mia", null);                        // justo lo que NO deberías hacer

        Integer pedro = ages.get("Pedro");            // Integer, no int: puede ser null
        boolean missingIsNull = pedro == null && !ages.containsKey("Pedro");
        boolean nullValueIsNull = ages.get("Mia") == null && ages.containsKey("Mia");   // mismo null, otro significado

        boolean defaultWhenMissing = ages.getOrDefault("Pedro", 0) == 0;
        boolean noDefaultWhenNullValue = ages.getOrDefault("Mia", 0) == null;           // ¡no devuelve 0!

        boolean unboxingFails;
        try {
            int age = ages.get("Pedro");              // unboxing de null
            unboxingFails = age < 0;
        } catch (NullPointerException e) {
            unboxingFails = true;
        }

        return missingIsNull && nullValueIsNull && defaultWhenMissing && noDefaultWhenNullValue && unboxingFails;
    }

    /*
        =================================================================
        CAPA 4) HASHMAP POR DENTRO: HASH, CAJONES Y COLISIONES
        =================================================================

        Un HashMap es un ARREGLO de cajones (buckets). Cada cajón guarda
        los pares cuya clave "cae" ahí. Para put(clave, valor):

        1) Calcula clave.hashCode(): un int (ObjectContract, CAPA 7).
        2) Lo MEZCLA: hash = h ^ (h >>> 16). Junta los bits altos con los
           bajos (v1_fundamentals/part1_first_steps/Operators, operadores
           de bits). Por qué: el paso 3 solo usa los bits bajos, y sin
           esto, claves que solo difieren en los bits altos caerían todas
           en el mismo cajón.
        3) Elige el cajón: índice = hash & (capacidad - 1). La capacidad
           es SIEMPRE una potencia de 2 (16, 32, 64...), y con eso este
           "&" da lo mismo que hash % capacidad, pero mucho más rápido.
        4) En ese cajón busca un par con una clave IGUAL (primero compara
           el hash, luego equals). Si existe, reemplaza el valor; si no,
           agrega el par.

            capacidad 8:
            cajón 0: [ ]
            cajón 1: [ "Ana"=30 ]
            cajón 2: [ ]
            cajón 3: [ "Luis"=25 -> "Mia"=22 ]   <- colisión: dos claves, un cajón
            ...
            get("Mia"): calcula el cajón 3 y compara solo con esos 2 pares

        get hace los mismos pasos: va directo a UN cajón. Por eso es O(1)
        sin importar el tamaño del mapa.

        COLISIONES: dos claves distintas en el mismo cajón. Es normal y no
        rompe nada, porque dentro del cajón se decide con equals. Pero si
        un cajón se llena, buscar en él es recorrerlo:
        - Al principio, cada cajón es una lista enlazada (O(k) en el
          cajón).
        - Desde Java 8, si un cajón PASA de 8 pares (y la tabla tiene al
          menos 64 cajones), se convierte en un ÁRBOL balanceado: O(log k)
          en lugar de O(k)... pero solo si las claves son Comparable (String,
          Integer...), porque el árbol necesita ordenarlas. Es una defensa
          contra hashCode malos y contra ataques que mandan muchas claves
          con el mismo hash.

        El peor caso es un hashCode que devuelve SIEMPRE lo mismo: todos
        los pares en un solo cajón. El mapa sigue siendo CORRECTO (equals
        decide), pero deja de ser rápido. El ejemplo de abajo lo
        demuestra: funciona, pero es O(n) en vez de O(1). Y aquí ni el árbol
        ayuda: ConstantHashKey no es Comparable, así que el árbol no puede
        ordenarlas y la búsqueda sigue revisando una por una.

        Consecuencia visible: el orden de recorrido de un HashMap es el
        orden de los cajones, no el de llegada. Con claves Integer
        pequeñas PARECE ordenado (el hashCode de un Integer es su propio
        valor, así que 0, 1, 3, 7... caen en cajones 0, 1, 3, 7...). Es una
        casualidad de la implementación: con Strings el orden se ve
        revuelto. Nunca dependas de él.
    */
    static final class ConstantHashKey {
        private final String id;

        ConstantHashKey(String id) {
            this.id = id;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof ConstantHashKey key && key.id.equals(id);
        }

        @Override
        public int hashCode() {
            return 42;                      // legal pero pésimo: todas las claves al mismo cajón
        }
    }

    static boolean collisionsExample() {
        Map<ConstantHashKey, Integer> map = new HashMap<>();
        for (int i = 0; i < 100; i++) {
            map.put(new ConstantHashKey("k" + i), i);
        }
        // 100 claves en UN cajón: todo funciona porque equals decide, solo que más lento.
        return map.size() == 100
                && map.get(new ConstantHashKey("k57")) == 57
                && !map.containsKey(new ConstantHashKey("k100"));
    }

    /*
        =================================================================
        CAPA 5) CAPACIDAD, LOAD FACTOR Y POR QUÉ A VECES CRECE
        =================================================================

        Si el arreglo de cajones nunca creciera, con muchos pares cada
        cajón tendría muchos elementos y todo se volvería lento. Por eso
        HashMap crece:

        - CAPACIDAD: cuántos cajones hay. Por defecto 16 (el arreglo se
          crea con el primer put, no con new HashMap<>()).
        - LOAD FACTOR (factor de carga): qué tan lleno se permite estar.
          Por defecto 0.75.
        - UMBRAL = capacidad * load factor = 16 * 0.75 = 12. Al pasar de
          12 pares, el mapa DUPLICA su capacidad (32 cajones) y vuelve a
          repartir TODOS los pares en el arreglo nuevo (rehash).

        Por qué 0.75: es un equilibrio. Más bajo (0.5) desperdicia
        memoria en cajones vacíos; más alto (1.0) produce más colisiones.

        El rehash cuesta O(n), pero ocurre cada vez más espaciado (al
        duplicar, el siguiente tarda el doble en llegar). Repartido entre
        todos los put, cada uno sigue costando O(1) AMORTIZADO: la misma
        idea del crecimiento de ArrayList (Lists, CAPA 2).

        Si sabes de antemano cuántos pares vas a guardar, evita todos esos
        rehash creando el mapa del tamaño correcto:
            Map<String, User> users = HashMap.newHashMap(1_000_000);   // Java 19+
        Ojo: new HashMap<>(1_000_000) NO significa "para un millón de
        pares", sino "al menos un millón de cajones" (se redondea a la
        potencia de 2 siguiente: 1,048,576), que con 0.75 solo aguantan
        786,432 antes de crecer. newHashMap hace la cuenta por ti.

        El método de abajo es un MODELO de esa regla (no lee el mapa por
        dentro): cuenta cuántas veces se duplicaría un HashMap por defecto
        para guardar cierta cantidad de pares.
    */
    static int resizesFor(int entries) {
        int capacity = 16;
        int resizes = 0;
        while (entries > capacity * 0.75) {       // se pasó del umbral: duplica y reparte todo
            capacity *= 2;
            resizes++;
        }
        return resizes;
    }

    static boolean resizeModelExample() {
        Map<String, Integer> presized = HashMap.newHashMap(100);   // listo para 100 sin crecer
        presized.put("a", 1);

        return resizesFor(12) == 0              // 12 pares caben en 16 cajones
                && resizesFor(13) == 1          // el 13 pasa el umbral: 16 -> 32
                && resizesFor(1_000_000) == 17  // un millón: 17 copias completas que newHashMap evita
                && presized.size() == 1;
    }

    /*
        =================================================================
        CAPA 6) LO QUE UNA CLAVE NECESITA
        =================================================================

        Como el mapa encuentra las claves con hashCode y equals (CAPA 4),
        una clave debe:

        1) Tener equals y hashCode coherentes: dos claves iguales deben
           dar el mismo hashCode (ObjectContract, CAPA 7). Si no, iguales
           caen en cajones distintos y get no las encuentra.
        2) NO CAMBIAR mientras está en el mapa. Si cambias algo que afecta
           su hashCode, el par queda en el cajón VIEJO, y el mapa ya no lo
           encuentra con ninguna clave (ObjectContract, CAPA 8). El par
           sigue ahí ocupando memoria, perdido.

        Por eso las mejores claves son INMUTABLES: String, Integer, enums
        y records (v2_oop/part4_advanced_types/Records, CAPA 9). Un record con dos campos es
        la forma limpia de una clave compuesta, en lugar de pegar textos
        como "fila" + "," + "columna".

        Para claves que son constantes de un enum, existe EnumMap, más
        rápido y compacto (v2_oop/part4_advanced_types/Enums, CAPA 7).

        El ejemplo usa una List como clave (List sí tiene equals y
        hashCode) y la modifica después de guardarla.
    */
    record Cell(int row, int column) { }

    static boolean keysExample() {
        Map<Cell, String> board = new HashMap<>();
        board.put(new Cell(0, 2), "X");
        boolean recordKeyWorks = board.get(new Cell(0, 2)).equals("X");   // otro objeto, misma clave

        List<String> mutableKey = new ArrayList<>(List.of("a"));
        Map<List<String>, Integer> map = new HashMap<>();
        map.put(mutableKey, 1);
        mutableKey.add("b");                       // cambia su hashCode: el par queda en el cajón viejo

        boolean lost = map.get(mutableKey) == null          // con la clave nueva: otro cajón
                && map.get(List.of("a")) == null            // con la vieja: mismo cajón, pero equals ya no coincide
                && map.size() == 1;                         // y el par sigue ahí, inalcanzable

        return recordKeyWorks && lost;
    }

    /*
        =================================================================
        CAPA 7) RECORRER UN MAPA
        =================================================================

        Un Map no se recorre directamente; se recorre una de sus tres
        VISTAS (CollectionsOverview, CAPA 7):

            for (Map.Entry<K, V> entry : map.entrySet())   clave Y valor
            for (K key : map.keySet())                     solo claves
            for (V value : map.values())                   solo valores
            map.forEach((key, value) -> ...)               clave y valor, con lambda

        Cuando necesitas clave y valor, usa entrySet (o forEach), NO esto:
            for (K key : map.keySet()) {
                V value = map.get(key);       // una búsqueda extra por cada clave
            }
        Funciona, pero hace un get por vuelta que entrySet se ahorra: el
        Entry ya trae los dos. Tu antigua JavaDataStructuresGuide lo decía: "esta
        es la forma más importante para algoritmos".

        Para quitar o cambiar valores mientras recorres: Iterators, CAPA 9.
    */
    static boolean iterationExample() {
        Map<String, Integer> prices = new HashMap<>(Map.of("teclado", 500, "mouse", 200, "monitor", 3000));

        int total = 0;
        StringBuilder expensive = new StringBuilder();
        for (Map.Entry<String, Integer> entry : prices.entrySet()) {
            total += entry.getValue();
            if (entry.getValue() > 1000) {
                expensive.append(entry.getKey());
            }
        }

        int[] totalWithForEach = {0};                  // una lambda no puede cambiar una variable local (v7_functional/)
        prices.forEach((name, price) -> totalWithForEach[0] += price);

        return total == 3700 && totalWithForEach[0] == 3700 && expensive.toString().equals("monitor");
    }

    /*
        =================================================================
        CAPA 8) CONTAR: GETORDEFAULT Y MERGE
        =================================================================

        El uso más clásico de un mapa: contar cuántas veces aparece cada
        cosa (palabras, letras, visitas por página).

        La forma de tu antigua JavaDataStructuresGuide:
            counts.put(x, counts.getOrDefault(x, 0) + 1);
        "Dame el conteo actual (0 si no hay), súmale 1 y guárdalo". Sin
        getOrDefault necesitarías un if para el primer caso.

        La forma moderna (la de tu antiguo CheatSheet):
            counts.merge(x, 1, Integer::sum);
        Se lee: "pon 1; si ya había algo, combínalo con la suma". merge:
        - si la clave no está (o vale null), guarda el valor dado (1);
        - si está, guarda funcion(viejo, nuevo): aquí viejo + 1;
        - DEVUELVE el valor que quedó.
        Además de ser más corta, busca la clave UNA vez en lugar de dos
        (get + put).

        Como merge devuelve el conteo nuevo, "el primer número que se
        repite" sale en el mismo paso: cuando el conteo llega a 2.
    */
    static Map<Character, Integer> letterCounts(String text) {
        Map<Character, Integer> counts = new HashMap<>();
        for (char letter : text.toCharArray()) {
            counts.merge(letter, 1, Integer::sum);
        }
        return counts;
    }

    static Integer firstRepeated(int[] values) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (int value : values) {
            if (counts.merge(value, 1, Integer::sum) == 2) {   // merge devuelve el conteo nuevo
                return value;
            }
        }
        return null;
    }

    static boolean countingExample() {
        Map<Integer, Integer> classic = new HashMap<>();
        for (int n : new int[] {1, 2, 2, 3, 3, 3}) {
            classic.put(n, classic.getOrDefault(n, 0) + 1);    // la forma de tu guía: funciona
        }

        return classic.equals(Map.of(1, 1, 2, 2, 3, 3))
                && letterCounts("aabbbcccc").equals(Map.of('a', 2, 'b', 3, 'c', 4))
                && firstRepeated(new int[] {7, 1, 5, 3, 5, 9}) == 5
                && firstRepeated(new int[] {1, 2, 3}) == null;
    }

    /*
        =================================================================
        CAPA 9) AGRUPAR: COMPUTEIFABSENT
        =================================================================

        Otro uso clásico: AGRUPAR. Un mapa de clave -> lista: pedidos por
        cliente, palabras por longitud, alumnos por grupo.

        El problema: la primera vez que aparece una clave, todavía no hay
        lista. La forma larga:
            List<String> group = map.get(key);
            if (group == null) {
                group = new ArrayList<>();
                map.put(key, group);
            }
            group.add(word);

        computeIfAbsent(key, funcion) hace todo eso en una línea:
            map.computeIfAbsent(key, k -> new ArrayList<>()).add(word);
        - Si la clave no está, llama a la función, guarda lo que devuelve
          y LO DEVUELVE.
        - Si ya está, devuelve el valor existente sin llamar la función.
        En ambos casos devuelve LA lista que está en el mapa, así que el
        .add(word) siempre funciona.

        Por qué no putIfAbsent(key, new ArrayList<>()):
        - Crea una lista nueva en CADA llamada, aunque ya exista (basura).
        - Devuelve el valor ANTERIOR: null la primera vez, así que
          putIfAbsent(...).add(word) lanza NullPointerException.
        computeIfAbsent solo crea la lista cuando hace falta (la función
        es "perezosa") y devuelve la lista actual.

        (Con streams existe Collectors.groupingBy, que hace lo mismo en
        una expresión: v7_functional/part2_streams/AdvancedStreams.)
    */
    static Map<Integer, List<String>> groupByLength(List<String> words) {
        Map<Integer, List<String>> groups = new TreeMap<>();      // TreeMap: claves en orden (CAPA 12)
        for (String word : words) {
            groups.computeIfAbsent(word.length(), length -> new ArrayList<>()).add(word);
        }
        return groups;
    }

    static boolean groupingExample() {
        Map<Integer, List<String>> groups = groupByLength(List.of("sol", "luna", "mar", "cielo", "pez", "nube"));

        boolean putIfAbsentTrap;
        try {
            Map<Integer, List<String>> wrong = new HashMap<>();
            wrong.putIfAbsent(3, new ArrayList<>()).add("sol");   // devuelve el anterior: null
            putIfAbsentTrap = false;
        } catch (NullPointerException e) {
            putIfAbsentTrap = true;
        }

        return groups.equals(Map.of(
                        3, List.of("sol", "mar", "pez"),
                        4, List.of("luna", "nube"),
                        5, List.of("cielo")))
                && putIfAbsentTrap;
    }

    /*
        =================================================================
        CAPA 10) COMPUTE Y COMPUTEIFPRESENT: ACTUALIZAR O BORRAR EN UN PASO
        =================================================================

        La familia completa (todas buscan la clave UNA sola vez):

            merge(k, v, f)          no está -> v;  está -> f(viejo, v)
            computeIfAbsent(k, f)   no está -> f(k);  está -> no toca nada
            computeIfPresent(k, f)  está -> f(k, viejo);  no está -> no toca nada
            compute(k, f)           siempre -> f(k, viejo o null)

        Una regla común a todas: si la función devuelve null, la clave SE
        QUITA del mapa. Eso permite "actualizar o borrar" en una sola
        operación.

        Ejemplo real: un inventario donde vender resta una unidad, y un
        producto con 0 unidades no debe quedar en el mapa (si quedara,
        containsKey diría que "hay" algo que no hay).
    */
    static void sell(Map<String, Integer> stock, String product) {
        stock.computeIfPresent(product, (name, units) -> units == 1 ? null : units - 1);   // null: se quita
    }

    static boolean computeExample() {
        Map<String, Integer> stock = new HashMap<>(Map.of("teclado", 2, "mouse", 1));
        sell(stock, "teclado");
        sell(stock, "mouse");                      // era la última unidad: el par desaparece
        sell(stock, "monitor");                    // no está: computeIfPresent no hace nada

        Map<String, String> owners = new HashMap<>();
        owners.compute("repo", (key, current) -> current == null ? "ana" : current + ",ana");
        owners.compute("repo", (key, current) -> current == null ? "luis" : current + ",luis");

        return stock.equals(Map.of("teclado", 1)) && owners.get("repo").equals("ana,luis");
    }

    /*
        =================================================================
        CAPA 11) LINKEDHASHMAP: ORDEN DE LLEGADA Y CACHÉ LRU
        =================================================================

        LinkedHashMap es un HashMap que además une sus pares con una lista
        enlazada (igual que LinkedHashSet; Sets, CAPA 4). Sigue siendo O(1)
        y se recorre en ORDEN DE LLEGADA. Reemplazar el valor de una
        clave que ya estaba no cambia su posición.

        Úsalo cuando el orden importa para quien lo lee: los campos de un
        formulario, las columnas de un reporte, un JSON que debe salir en
        el mismo orden.

        Su truco avanzado: ORDEN DE ACCESO. Con
            new LinkedHashMap<>(16, 0.75f, true)
        cada get o put mueve ese par al FINAL, así que el primero es
        siempre el usado hace más tiempo. Si además sobrescribes
        removeEldestEntry (un método protected que el mapa llama después
        de cada put que agrega una clave NUEVA), obtienes una CACHÉ LRU ("Least Recently Used": al
        llenarse, se descarta lo que hace más tiempo no se usa) en pocas
        líneas. Es una pregunta clásica de entrevista.

        (Desde Java 21 LinkedHashMap es un SequencedMap: tiene
        firstEntry(), lastEntry() y pollFirstEntry() para ver o sacar los
        extremos directamente.)
    */
    static final class LruCache<K, V> extends LinkedHashMap<K, V> {
        private static final long serialVersionUID = 1L;    // LinkedHashMap es Serializable: -Xlint pide declararlo
        private final int maxEntries;

        LruCache(int maxEntries) {
            super(16, 0.75f, true);                // true = orden de acceso, no de llegada
            this.maxEntries = maxEntries;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
            return size() > maxEntries;            // true: el mapa quita el que lleva más tiempo sin usarse
        }
    }

    static boolean linkedHashMapExample() {
        Map<String, Integer> form = new LinkedHashMap<>();
        form.put("nombre", 1);
        form.put("correo", 2);
        form.put("edad", 3);
        form.put("nombre", 10);                    // reemplazar no cambia su posición
        boolean insertionOrder = new ArrayList<>(form.keySet()).equals(List.of("nombre", "correo", "edad"));

        LruCache<String, String> cache = new LruCache<>(2);
        cache.put("a", "A");
        cache.put("b", "B");
        cache.get("a");                            // "a" se usó: ahora el más viejo es "b"
        cache.put("c", "C");                       // se llena: sale "b", no "a"

        return insertionOrder
                && new ArrayList<>(cache.keySet()).equals(List.of("a", "c"))
                && cache.firstEntry().getKey().equals("a");
    }

    /*
        =================================================================
        CAPA 12) TREEMAP: CLAVES ORDENADAS Y "EL MÁS CERCANO"
        =================================================================

        TreeMap mantiene las CLAVES ordenadas, con el mismo árbol
        balanceado de TreeSet (Sets, CAPA 5): O(log n). Lo mismo que allá:
        - las claves deben ser Comparable o necesitas un Comparator;
        - no acepta clave null;
        - decide si dos claves son la misma con compareTo, no con equals
          (Sets, CAPA 7).

        Y lo más útil: es un NavigableMap, con búsquedas por cercanía:

            firstKey(), lastKey()            la menor y la mayor
            floorKey(x), floorEntry(x)       la mayor clave <= x
            ceilingKey(x), ceilingEntry(x)   la menor clave >= x
            higherKey(x), lowerKey(x)        estrictamente > o < que x
            headMap(x), tailMap(x), subMap(desde, hasta)   rangos (vistas)

        Esto resuelve con UNA línea los problemas de "tramos": tablas de
        impuestos, descuentos por volumen, calificaciones por puntaje,
        tarifas por peso. Guardas el inicio de cada tramo como clave y
        floorEntry encuentra en qué tramo cae un valor, sin una cadena de
        if/else.
    */
    static final NavigableMap<Integer, String> GRADES = new TreeMap<>(Map.of(
            0, "F",
            70, "C",
            80, "B",
            90, "A"));

    static String gradeFor(int score) {
        return GRADES.floorEntry(score).getValue();     // el tramo cuyo inicio es el mayor <= score
    }

    static boolean treeMapExample() {
        NavigableMap<Integer, String> grades = GRADES;

        return gradeFor(85).equals("B") && gradeFor(90).equals("A") && gradeFor(69).equals("F")
                && grades.firstKey() == 0 && grades.lastKey() == 90
                && grades.ceilingKey(75) == 80 && grades.higherKey(90) == null
                && new ArrayList<>(grades.headMap(80).values()).equals(List.of("F", "C"))
                && new ArrayList<>(grades.descendingMap().values()).equals(List.of("A", "B", "C", "F"));
    }

    /*
        =================================================================
        CAPA 13) MAP.OF: MAPAS INMUTABLES
        =================================================================

        Map.of(k1, v1, k2, v2, ...) crea un mapa que no se puede
        modificar (part4_tools/ImmutableCollections), ideal para tablas
        fijas. Igual que Set.of (Sets, CAPA 8):
        - RECHAZA CLAVES DUPLICADAS al crearse: IllegalArgumentException
          ("duplicate key: a"). Casi seguro es un error de tipeo.
        - RECHAZA null en claves y valores.
        - Su orden de recorrido cambia entre ejecuciones.

        Map.of acepta hasta 10 pares. Para más, Map.ofEntries con
        Map.entry(k, v). Map.copyOf(otroMapa) hace una copia inmutable.

        Truco: Map.of va bien para declarar datos, y "new HashMap<>(Map.of(...))"
        da un mapa modificable ya con contenido.
    */
    static boolean mapOfExample() {
        Map<String, String> countries = Map.ofEntries(
                Map.entry("MX", "México"),
                Map.entry("US", "Estados Unidos"));

        boolean rejectsDuplicates;
        try {
            Map.of("a", 1, "a", 2);
            rejectsDuplicates = false;
        } catch (IllegalArgumentException e) {
            rejectsDuplicates = e.getMessage().equals("duplicate key: a");
        }

        boolean immutable;
        try {
            countries.put("CA", "Canadá");
            immutable = false;
        } catch (UnsupportedOperationException e) {
            immutable = true;
        }

        Map<String, Integer> editable = new HashMap<>(Map.of("x", 1));
        editable.put("y", 2);

        return countries.get("MX").equals("México") && rejectsDuplicates && immutable
                && Objects.equals(editable, Map.of("x", 1, "y", 2));
    }

    /*
        =================================================================
        CAPA 14) CÓMO ELEGIR
        =================================================================

        | Necesitas                                        | Usa                      |
        |--------------------------------------------------|--------------------------|
        | Buscar por clave, sin importar el orden          | HashMap                  |
        | Lo mismo, respetando el orden de llegada         | LinkedHashMap            |
        | Una caché que descarta lo menos usado            | LinkedHashMap (acceso)   |
        | Claves ordenadas, tramos, "el más cercano"       | TreeMap                  |
        | Una tabla fija que no cambia                     | Map.of / Map.ofEntries   |
        | Claves de un enum                                | EnumMap                  |
        | Muchos hilos a la vez                            | ConcurrentHashMap (v13_concurrency/) |

        Por defecto: HashMap. Y Hashtable no: es la versión antigua,
        sincronizada y más lenta (CollectionsOverview, CAPA 9).
    */

    /*
        =================================================================
        CAPA 15) ERRORES COMUNES
        =================================================================

        1) Guardar get(k) en un int: NullPointerException si la clave no
           está (CAPA 3).

        2) Guardar null como valor y luego confiar en get o getOrDefault
           (CAPA 3).

        3) Claves sin equals/hashCode, o que cambian dentro del mapa: pares
           perdidos (CAPA 6).

        4) Recorrer keySet y hacer get por cada clave en lugar de usar
           entrySet (CAPA 7).

        5) Esperar que HashMap conserve un orden, o creer que lo conserva
           porque con Integer pequeños "salió ordenado" (CAPA 4).

        6) putIfAbsent(k, new ArrayList<>()).add(x): NullPointerException
           la primera vez (CAPA 9).

        7) Modificar el mismo mapa DENTRO de la función de
           computeIfAbsent/compute/merge (por ejemplo, un Fibonacci
           recursivo con memo.computeIfAbsent): HashMap lanza
           ConcurrentModificationException. Para memoización recursiva,
           usa get y put por separado (algorithms/).

        8) containsValue dentro de un ciclo: O(n) cada vez (CAPA 2).

        9) new HashMap<>(n) creyendo que caben n pares sin crecer; para
           eso es HashMap.newHashMap(n) (CAPA 5).

        10) Map.of con datos que pueden traer claves repetidas (CAPA 13).
    */
    static boolean recursiveComputeFails() {
        Map<Integer, Long> memo = new HashMap<>();
        try {
            fibonacci(30, memo);
            return false;
        } catch (ConcurrentModificationException e) {
            return true;
        }
    }

    private static long fibonacci(int n, Map<Integer, Long> memo) {
        if (n < 2) {
            return n;
        }
        // ERROR de diseño: la función modifica "memo" mientras computeIfAbsent lo está modificando.
        return memo.computeIfAbsent(n, k -> fibonacci(k - 1, memo) + fibonacci(k - 2, memo));
    }

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Map: pares clave -> valor; claves únicas; buscar por clave.
        - put devuelve el valor anterior; get devuelve null si no está.
          No guardes null como valor.
        - HashMap: hashCode mezclado -> cajón (& con capacidad potencia de
          2) -> equals dentro del cajón. O(1). Cajones llenos se vuelven
          árbol (Java 8+).
        - Crece al pasar capacidad * 0.75, duplicando y repartiendo todo;
          O(1) amortizado. HashMap.newHashMap(n) si sabes el tamaño.
        - Claves: equals/hashCode correctos e inmutables (String, records,
          enums).
        - Recorre con entrySet o forEach.
        - Contar: merge(k, 1, Integer::sum). Agrupar: computeIfAbsent.
          Actualizar o borrar: computeIfPresent devolviendo null.
        - LinkedHashMap: orden de llegada, o de acceso para una caché LRU.
        - TreeMap: claves ordenadas, O(log n), floorEntry para tramos.
        - Map.of: inmutable, sin duplicados ni null.
        - Por defecto, HashMap.
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
