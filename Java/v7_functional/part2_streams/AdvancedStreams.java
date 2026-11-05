package v7_functional.part2_streams;

import java.util.Arrays;
import java.util.IntSummaryStatistics;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/*
    =====================================================================
    STREAMS AVANZADOS: COLLECTORS, FLATMAP, PRIMITIVOS Y PARALELOS
    =====================================================================

    Ruta de functional/: guía 5 de 6  (part2_streams)
    Anterior:  part2_streams/StreamsBasics
    Siguiente: part2_streams/OptionalUsage

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.

    Índice:
        CAPA 1)  Qué es un Collector
        CAPA 2)  joining: unir textos
        CAPA 3)  toMap y su trampa: claves repetidas
        CAPA 4)  groupingBy: agrupar
        CAPA 5)  Collectors "de abajo": qué hacer con cada grupo
        CAPA 6)  partitioningBy: dividir en dos
        CAPA 7)  teeing: dos resultados en una sola pasada
        CAPA 8)  flatMap: de uno a muchos
        CAPA 9)  Streams primitivos: IntStream, LongStream, DoubleStream
        CAPA 10) Streams paralelos: cómo funcionan
        CAPA 11) Cuándo un stream paralelo ayuda y cuándo daña
        CAPA 12) Recetario
        CAPA 13) Errores comunes
        RESUMEN FINAL
*/
public class AdvancedStreams {

    public static void main(String[] args) {
        check(joiningExample(), "CAPA 2");
        check(toMapExample(), "CAPA 3");
        check(groupingExample(), "CAPA 4");
        check(downstreamExample(), "CAPA 5");
        check(partitioningExample(), "CAPA 6");
        check(teeingExample(), "CAPA 7");
        check(flatMapExample(), "CAPA 8");
        check(primitiveStreamsExample(), "CAPA 9");
        check(parallelExample(), "CAPA 10");
        System.out.println("AdvancedStreams: todos los ejemplos se comportan como se explica.");
    }

    // Datos de ejemplo para toda la guía: ventas de una tienda.
    record Sale(String product, String category, String city, int units, int unitPrice) {
        int total() {
            return units * unitPrice;
        }
    }

    static final List<Sale> SALES = List.of(
            new Sale("teclado", "periféricos", "CDMX", 2, 500),
            new Sale("mouse", "periféricos", "GDL", 5, 250),
            new Sale("monitor", "pantallas", "CDMX", 1, 3_000),
            new Sale("cable", "accesorios", "MTY", 10, 80),
            new Sale("mouse", "periféricos", "CDMX", 3, 250),
            new Sale("funda", "accesorios", "GDL", 4, 150));

    /*
        =================================================================
        CAPA 1) QUÉ ES UN COLLECTOR
        =================================================================

        collect(...) es la terminal más flexible (StreamsBasics, CAPA 9).
        Recibe un COLLECTOR: una "receta" que le dice cómo juntar los
        elementos en un resultado. Por dentro, un Collector son cuatro
        funciones:
        1) crear el contenedor vacío          (new ArrayList<>())
        2) agregar un elemento                (lista.add(x))
        3) combinar dos contenedores parciales (para streams paralelos)
        4) dar el resultado final             (por ejemplo, hacerlo inmutable)

        Casi nunca escribes uno a mano: la clase Collectors trae fábricas
        para todo lo común. Lo poderoso es que se ANIDAN: "agrupa por
        ciudad y, de cada grupo, suma los totales" es un collector dentro
        de otro (CAPA 5).

        Convención: muchos proyectos importan los métodos con
        import static java.util.stream.Collectors.*; para escribir
        groupingBy(...) en lugar de Collectors.groupingBy(...)
        (v2_oop/part1_basics/Static, CAPA 8). Aquí se deja el nombre
        completo para que se vea de dónde sale cada uno.
    */

    /*
        =================================================================
        CAPA 2) JOINING: UNIR TEXTOS
        =================================================================

            Collectors.joining()                         "abc"
            Collectors.joining(", ")                     "a, b, c"
            Collectors.joining(", ", "[", "]")           "[a, b, c]"

        Por qué no reduce con +: cada + crea un String nuevo; joining usa
        un StringBuilder por dentro (v1_fundamentals/part3_text_and_
        references/Strings). Y con prefijo/sufijo, un stream vacío da
        "[]" sin casos especiales.

        Solo funciona con streams de texto (CharSequence): primero haz
        map(String::valueOf) o map(Sale::product).
    */
    static boolean joiningExample() {
        String products = SALES.stream()
                .map(Sale::product)
                .distinct()
                .collect(Collectors.joining(", ", "Productos: ", "."));
        String emptyList = Stream.<String>empty().collect(Collectors.joining(", ", "[", "]"));

        return products.equals("Productos: teclado, mouse, monitor, cable, funda.") && emptyList.equals("[]");
    }

    /*
        =================================================================
        CAPA 3) TOMAP Y SU TRAMPA: CLAVES REPETIDAS
        =================================================================

            Collectors.toMap(funciónClave, funciónValor)

        Cada elemento produce UN par clave -> valor. Para indexar por id:
            users.stream().collect(Collectors.toMap(User::id, Function.identity()))
        (identity() = "el valor es el elemento mismo"; FunctionalInterfaces,
        CAPA 7.)

        LA TRAMPA: si dos elementos producen la MISMA clave, no reemplaza
        como map.put: LANZA una excepción, porque no sabe cuál quieres:

            IllegalStateException: Duplicate key mouse
                (attempted merging values 5 and 3)

        "mouse" aparece en dos ventas. Soluciones:
        - una función de COMBINACIÓN como tercer argumento, que dice qué
          hacer con dos valores de la misma clave: sumarlos
          (Integer::sum), quedarse con el primero ((a, b) -> a)...
        - si en realidad quieres TODOS los valores por clave, no es toMap:
          es groupingBy (CAPA 4).

        Otros detalles:
        - Devuelve un HashMap (sin orden). El cuarto argumento elige el
          tipo: LinkedHashMap::new, TreeMap::new.
        - Un valor null lanza NullPointerException (toMap lo rechaza a
          propósito, igual que map.merge; Maps, CAPA 3).
    */
    static boolean toMapExample() {
        boolean duplicateFails;
        try {
            SALES.stream().collect(Collectors.toMap(Sale::product, Sale::units));
            duplicateFails = false;
        } catch (IllegalStateException e) {
            duplicateFails = e.getMessage().equals("Duplicate key mouse (attempted merging values 5 and 3)");
        }

        Map<String, Integer> unitsByProduct = SALES.stream()
                .collect(Collectors.toMap(Sale::product, Sale::units, Integer::sum));   // mismo producto: suma

        Map<String, Integer> priceByProduct = SALES.stream()
                .collect(Collectors.toMap(Sale::product, Sale::unitPrice,
                        (first, second) -> first,           // repetido: quédate con el primero
                        LinkedHashMap::new));               // y conserva el orden de aparición

        return duplicateFails
                && unitsByProduct.get("mouse") == 8
                && List.copyOf(priceByProduct.keySet()).equals(List.of("teclado", "mouse", "monitor", "cable", "funda"));
    }

    /*
        =================================================================
        CAPA 4) GROUPINGBY: AGRUPAR
        =================================================================

            Collectors.groupingBy(funciónClave)  ->  Map<Clave, List<Elemento>>

        Para cada elemento calcula su clave y lo mete en la lista de esa
        clave. Es exactamente el patrón computeIfAbsent(...).add(...) de
        v5_collections/part2_sets_and_maps/Maps, CAPA 9, en una línea.

        Equivalente a GROUP BY de SQL, y la operación más usada para
        reportes: ventas por ciudad, pedidos por cliente, errores por tipo.

        Detalles:
        - El mapa es un HashMap: sin orden. Para claves ordenadas, pasa
          la fábrica del mapa: groupingBy(clave, TreeMap::new, downstream).
        - Solo aparecen las claves que tienen al menos un elemento.
    */
    static boolean groupingExample() {
        Map<String, List<Sale>> byCity = SALES.stream().collect(Collectors.groupingBy(Sale::city));

        Map<String, List<String>> productsByCityOrdered = SALES.stream()
                .collect(Collectors.groupingBy(Sale::city, TreeMap::new,
                        Collectors.mapping(Sale::product, Collectors.toList())));   // CAPA 5: mapping

        return byCity.get("CDMX").size() == 3 && byCity.get("MTY").size() == 1
                && List.copyOf(productsByCityOrdered.keySet()).equals(List.of("CDMX", "GDL", "MTY"))
                && productsByCityOrdered.get("GDL").equals(List.of("mouse", "funda"));
    }

    /*
        =================================================================
        CAPA 5) COLLECTORS "DE ABAJO": QUÉ HACER CON CADA GRUPO
        =================================================================

        groupingBy(clave, OTRO_COLLECTOR): el segundo collector
        ("downstream", de abajo) decide qué se hace con los elementos de
        CADA grupo, en lugar de dejarlos en una lista:

        | Downstream                          | Resultado por grupo        | SQL            |
        |-------------------------------------|----------------------------|----------------|
        | counting()                          | Long (¡no Integer!)        | COUNT(*)       |
        | summingInt(f)                       | Integer                    | SUM(...)       |
        | averagingInt(f)                     | Double                     | AVG(...)       |
        | maxBy(comparador) / minBy           | Optional<T>                | MAX(...)       |
        | mapping(f, otro)                    | transforma y luego junta   | SELECT columna |
        | filtering(condición, otro)          | filtra dentro del grupo    | (Java 9)       |
        | toSet() / toList()                  | Set / List                 |                |
        | groupingBy(...)                     | otro mapa: varios niveles  | GROUP BY a, b  |
        | collectingAndThen(otro, f)          | aplica f al resultado      |                |

        Detalles que causan errores:
        - counting() devuelve LONG: Map<String, Long>. Comparar con un
          Integer da false (v1_fundamentals/part3_text_and_references/
          Wrappers, CAPA 5).
        - maxBy devuelve Optional porque, en general, un collector puede
          recibir cero elementos. Dentro de groupingBy nunca está vacío;
          collectingAndThen(maxBy(...), Optional::get) lo desenvuelve.
        - filtering(...) vs filter antes de agrupar: con filter, los
          grupos que se quedan sin elementos DESAPARECEN; con filtering,
          aparecen con una lista vacía. Importa si el reporte debe mostrar
          "MTY: 0".
    */
    static boolean downstreamExample() {
        Map<String, Long> salesPerCity = SALES.stream()
                .collect(Collectors.groupingBy(Sale::city, Collectors.counting()));
        Map<String, Integer> revenuePerCity = SALES.stream()
                .collect(Collectors.groupingBy(Sale::city, Collectors.summingInt(Sale::total)));
        Map<String, String> bestSellerPerCategory = SALES.stream()
                .collect(Collectors.groupingBy(Sale::category,
                        Collectors.collectingAndThen(
                                Collectors.maxBy((a, b) -> Integer.compare(a.total(), b.total())),
                                best -> best.get().product())));

        Map<String, List<String>> bigSalesWithFilter = SALES.stream()
                .filter(sale -> sale.total() > 1_000)
                .collect(Collectors.groupingBy(Sale::city, Collectors.mapping(Sale::product, Collectors.toList())));
        Map<String, List<String>> bigSalesWithFiltering = SALES.stream()
                .collect(Collectors.groupingBy(Sale::city,
                        Collectors.filtering(sale -> sale.total() > 1_000,
                                Collectors.mapping(Sale::product, Collectors.toList()))));

        Map<String, Map<String, Long>> cityThenCategory = SALES.stream()
                .collect(Collectors.groupingBy(Sale::city,
                        Collectors.groupingBy(Sale::category, Collectors.counting())));

        return salesPerCity.get("CDMX") == 3L                   // Long: 3L, no 3
                && revenuePerCity.get("CDMX") == 1_000 + 3_000 + 750
                && bestSellerPerCategory.get("periféricos").equals("mouse")    // 1,250 vs 1,000
                && !bigSalesWithFilter.containsKey("MTY")                       // el grupo desapareció
                && bigSalesWithFiltering.get("MTY").isEmpty()                   // el grupo sigue, vacío
                && cityThenCategory.get("CDMX").get("periféricos") == 2L;
    }

    /*
        =================================================================
        CAPA 6) PARTITIONINGBY: DIVIDIR EN DOS
        =================================================================

            Collectors.partitioningBy(condición)  ->  Map<Boolean, List<T>>

        Un groupingBy especial con una condición: separa en los que cumplen
        (true) y los que no (false). Dos diferencias con
        groupingBy(condición):
        - SIEMPRE tiene las dos claves, true y false, aunque una lista
          quede vacía. No hay que preguntar containsKey.
        - Está optimizado para dos grupos.

        También acepta un downstream: partitioningBy(condición, counting()).
    */
    static boolean partitioningExample() {
        Map<Boolean, List<String>> bigOrSmall = SALES.stream()
                .collect(Collectors.partitioningBy(sale -> sale.total() >= 1_000,
                        Collectors.mapping(Sale::product, Collectors.toList())));
        Map<Boolean, List<Integer>> evens = Stream.of(1, 3, 5)
                .collect(Collectors.partitioningBy(n -> n % 2 == 0));

        return bigOrSmall.get(true).equals(List.of("teclado", "mouse", "monitor"))
                && evens.get(true).isEmpty()                    // la clave existe aunque esté vacía
                && evens.get(false).size() == 3;
    }

    /*
        =================================================================
        CAPA 7) TEEING: DOS RESULTADOS EN UNA SOLA PASADA
        =================================================================

        StreamsBasics, CAPA 12, dijo que un for gana cuando necesitas
        varias cosas en una pasada. teeing (Java 12) resuelve dos:

            Collectors.teeing(collectorA, collectorB, (a, b) -> combinar)

        Manda cada elemento a los DOS collectors (como una "T" de
        plomería, de ahí el nombre) y al final combina los dos resultados.
        Ejemplo: el promedio de venta necesita la suma Y la cantidad.
    */
    record Summary(long count, int revenue) {
        double average() {
            return (double) revenue / count;
        }
    }

    static boolean teeingExample() {
        Summary summary = SALES.stream()
                .collect(Collectors.teeing(
                        Collectors.counting(),
                        Collectors.summingInt(Sale::total),
                        Summary::new));                     // combina los dos resultados en un record

        return summary.count() == 6 && summary.revenue() == 7_400 && summary.average() > 1_233;
    }

    /*
        =================================================================
        CAPA 8) FLATMAP: DE UNO A MUCHOS
        =================================================================

        map convierte cada elemento en EXACTAMENTE uno. A veces cada
        elemento contiene VARIOS: un pedido tiene varios artículos, una
        línea de texto tiene varias palabras. Con map obtendrías un stream
        de listas (Stream<List<Item>>), no de artículos.

        flatMap recibe una función que devuelve un STREAM por elemento, y
        "aplana" todos esos streams en uno solo:

            pedidos:   [ [teclado, mouse], [monitor], [] ]
            map        -> Stream<List<Item>>   (3 elementos: listas)
            flatMap    -> Stream<Item>         (3 elementos: teclado, mouse, monitor)

        Usos típicos:
            orders.stream().flatMap(order -> order.items().stream())
            lines.stream().flatMap(line -> Arrays.stream(line.split(" ")))
            listOfLists.stream().flatMap(List::stream)

        (Java 16 agregó mapMulti, una alternativa más rápida cuando cada
        elemento produce pocos resultados; flatMap es más común y claro.)
    */
    record Order(String id, List<String> items) { }

    static boolean flatMapExample() {
        List<Order> orders = List.of(
                new Order("A", List.of("teclado", "mouse")),
                new Order("B", List.of("monitor", "mouse")),
                new Order("C", List.of()));

        long withMap = orders.stream().map(Order::items).count();             // 3 listas
        Set<String> distinctItems = orders.stream()
                .flatMap(order -> order.items().stream())                     // 4 artículos sueltos
                .collect(Collectors.toSet());

        Map<String, Long> wordCount = Stream.of("hola mundo", "hola java")
                .flatMap(line -> Arrays.stream(line.split(" ")))
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        return withMap == 3 && distinctItems.equals(Set.of("teclado", "mouse", "monitor"))
                && wordCount.get("hola") == 2L && wordCount.get("java") == 1L;
    }

    /*
        =================================================================
        CAPA 9) STREAMS PRIMITIVOS: INTSTREAM, LONGSTREAM, DOUBLESTREAM
        =================================================================

        Un Stream<Integer> guarda cada número como un objeto Integer
        (boxing). IntStream, LongStream y DoubleStream trabajan con
        primitivos, igual que las interfaces funcionales primitivas
        (part1_lambdas/FunctionalInterfaces, CAPA 6). Además traen
        operaciones numéricas que Stream no tiene:

            sum()                  la suma (0 si está vacío)
            average()              OptionalDouble (vacío si no hay elementos)
            max(), min()           OptionalInt
            summaryStatistics()    count, sum, min, max y average en UNA pasada

        Cómo entrar y salir:
            stream.mapToInt(Sale::total)     Stream<T>  -> IntStream
            intStream.boxed()                IntStream  -> Stream<Integer>
            intStream.mapToObj(i -> ...)     IntStream  -> Stream<T>
            IntStream.range(0, n)            índices: el for de los streams
            "texto".chars()                  IntStream de caracteres

        Por qué average es Optional y sum no: la suma de nada es 0 (tiene
        sentido); el promedio de nada es 0/0 (no existe).
    */
    static boolean primitiveStreamsExample() {
        int revenue = SALES.stream().mapToInt(Sale::total).sum();
        OptionalDouble averageUnits = SALES.stream().mapToInt(Sale::units).average();
        OptionalDouble noAverage = IntStream.empty().average();
        IntSummaryStatistics stats = SALES.stream().mapToInt(Sale::unitPrice).summaryStatistics();

        List<String> numbered = IntStream.range(0, 3)
                .mapToObj(i -> (i + 1) + ". " + SALES.get(i).product())       // índice + elemento
                .toList();
        long vowels = "programación".chars().filter(c -> "aeiouáéíóú".indexOf(c) >= 0).count();

        return revenue == 7_400 && averageUnits.getAsDouble() == 25 / 6.0 && noAverage.isEmpty()
                && stats.getMin() == 80 && stats.getMax() == 3_000 && stats.getCount() == 6
                && numbered.equals(List.of("1. teclado", "2. mouse", "3. monitor"))
                && vowels == 5;
    }

    /*
        =================================================================
        CAPA 10) STREAMS PARALELOS: CÓMO FUNCIONAN
        =================================================================

        list.parallelStream() o stream.parallel() reparte el trabajo entre
        varios núcleos del procesador:
        1) DIVIDE la fuente en trozos (un Spliterator sabe partirla).
        2) Procesa cada trozo en un hilo del ForkJoinPool común (un grupo
           de hilos compartido por toda la JVM, con tantos hilos como
           núcleos menos uno; v13_concurrency/).
        3) COMBINA los resultados parciales (la tercera función del
           Collector, CAPA 1; la identidad y asociatividad de reduce,
           StreamsBasics, CAPA 10).

        Lo que garantiza: el RESULTADO de toList, collect, reduce, sum...
        es el mismo que en secuencial (respetando el orden de la fuente),
        si tus lambdas cumplen las reglas de StreamsBasics, CAPA 11.

        Lo que NO garantiza:
        - el orden en que se EJECUTAN las lambdas: forEach en paralelo
          imprime en cualquier orden (forEachOrdered respeta el orden, a
          costa de velocidad);
        - nada, si modificas estado compartido. Agregar a una ArrayList
          común desde un stream paralelo pierde elementos o lanza
          ArrayIndexOutOfBoundsException (ArrayList no es segura para
          hilos). Este error no se ve en pruebas pequeñas y aparece en
          producción.
    */
    static boolean parallelExample() {
        List<Integer> numbers = IntStream.rangeClosed(1, 10_000).boxed().toList();

        long sequentialSum = numbers.stream().mapToLong(Integer::longValue).sum();
        long parallelSum = numbers.parallelStream().mapToLong(Integer::longValue).sum();
        List<Integer> parallelDoubled = numbers.parallelStream().map(n -> n * 2).toList();   // orden respetado

        Optional<Integer> anyBig = numbers.parallelStream().filter(n -> n > 9_990).findAny();   // cualquiera que cumpla

        return sequentialSum == parallelSum && sequentialSum == 50_005_000L
                && parallelDoubled.get(0) == 2 && parallelDoubled.get(9_999) == 20_000
                && anyBig.isPresent() && anyBig.get() > 9_990;
    }

    /*
        =================================================================
        CAPA 11) CUÁNDO UN STREAM PARALELO AYUDA Y CUÁNDO DAÑA
        =================================================================

        Repartir, coordinar hilos y combinar CUESTA. Solo vale la pena si
        el trabajo ahorrado es mayor. Regla práctica (de Brian Goetz, uno
        de los diseñadores de los streams): N × Q > 10,000, donde N es el
        número de elementos y Q el costo de procesar cada uno.

        AYUDA cuando:
        - hay MUCHOS datos (decenas de miles o más) o cada elemento es
          caro de procesar (cálculo pesado);
        - la fuente se divide fácil y parejo: ArrayList, arreglos,
          IntStream.range;
        - las operaciones no dependen del orden y no tienen estado.

        DAÑA (o no sirve) cuando:
        - pocos datos: el costo de coordinar supera el trabajo;
        - la fuente se divide mal: LinkedList, Stream.iterate, líneas de
          un archivo;
        - hay operaciones atadas al orden: findFirst, limit, sorted,
          skip (usa findAny o unordered() si el orden no importa);
        - el trabajo es ESPERAR (llamadas a una API, a la base de datos):
          los pocos hilos del pool común se quedan bloqueados y frenan a
          TODA la aplicación que use ese pool. Para I/O hay hilos
          virtuales (v13_concurrency/);
        - el código ya corre dentro de un servidor con muchas peticiones
          a la vez: los núcleos ya están ocupados.

        La regla de oro: empieza SECUENCIAL. Pasa a paralelo solo si
        MIDES (con un benchmark serio, v12_jvm/part3_performance/
        Performance) que mejora. Agregar .parallel() "por si acaso" es un
        error común.
    */

    /*
        =================================================================
        CAPA 12) RECETARIO
        =================================================================

        | Quiero                                     | Escribo                                                   |
        |--------------------------------------------|-----------------------------------------------------------|
        | Unir textos con comas                      | map(...).collect(joining(", "))                           |
        | Indexar por id                             | collect(toMap(T::id, Function.identity()))                |
        | Mapa con claves que se repiten, sumando    | collect(toMap(clave, valor, Integer::sum))                |
        | Lista por categoría                        | collect(groupingBy(T::categoria))                         |
        | Cuántos por categoría                      | collect(groupingBy(T::categoria, counting()))             |
        | Suma por categoría                         | collect(groupingBy(T::categoria, summingInt(T::monto)))   |
        | Solo un campo por categoría                | groupingBy(T::categoria, mapping(T::nombre, toList()))    |
        | Categorías ordenadas                       | groupingBy(T::categoria, TreeMap::new, ...)               |
        | Los que cumplen y los que no               | collect(partitioningBy(condición))                        |
        | Dos resultados en una pasada               | collect(teeing(a, b, combinar))                           |
        | Aplanar listas de listas                   | flatMap(List::stream)                                     |
        | Suma, promedio, mínimo y máximo de números | mapToInt(...).summaryStatistics()                         |
        | Recorrer con índice                        | IntStream.range(0, n).mapToObj(i -> ...)                  |
    */

    /*
        =================================================================
        CAPA 13) ERRORES COMUNES
        =================================================================

        1) toMap con claves repetidas sin función de combinación
           (CAPA 3).

        2) Usar toMap cuando querías todos los valores por clave:
           groupingBy (CAPAS 3 y 4).

        3) Comparar el resultado de counting() con un int/Integer: es Long
           (CAPA 5).

        4) Filtrar antes de agrupar y esperar ver los grupos vacíos
           (CAPA 5).

        5) Esperar orden en el mapa de groupingBy o toMap (CAPAS 3 y 4).

        6) map donde se necesitaba flatMap: un stream de listas (CAPA 8).

        7) Stream<Integer> con reduce(0, Integer::sum) para sumar en lugar
           de mapToInt(...).sum() (CAPA 9).

        8) .parallel() sin medir, con pocos datos, fuentes que se dividen
           mal, operaciones de I/O, o estado compartido (CAPAS 10 y 11).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Un Collector es la receta de collect; Collectors trae fábricas
          que se anidan.
        - joining para textos; toMap para un valor por clave (con función
          de combinación si se repiten).
        - groupingBy = GROUP BY: lista por clave, o con un downstream:
          counting (Long), summingInt, averagingInt, maxBy, mapping,
          filtering, otro groupingBy.
        - partitioningBy: siempre true y false. teeing: dos resultados en
          una pasada.
        - flatMap: cada elemento se vuelve varios, en un solo stream.
        - IntStream/LongStream/DoubleStream: sin boxing, con sum, average,
          summaryStatistics; range para índices.
        - Paralelo: divide, procesa en el pool común, combina. Solo con
          muchos datos o trabajo caro, fuentes divisibles, sin estado ni
          I/O, y después de medir.
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
