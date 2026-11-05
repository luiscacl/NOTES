package v7_functional.part2_streams;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/*
    =====================================================================
    STREAMS: LO ESENCIAL — DE CERO A NIVEL TÉCNICO
    =====================================================================

    Ruta de functional/: guía 4 de 6  (part2_streams)
    Anterior:  part1_lambdas/MethodReferences
    Siguiente: part2_streams/AdvancedStreams

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.

    Qué se da por sabido: lambdas, interfaces funcionales y referencias a
    métodos (part1_lambdas/).

    Índice:
        CAPA 1)  Qué es un stream (y qué NO es)
        CAPA 2)  Las tres partes: fuente, intermedias, terminal
        CAPA 3)  Las fuentes: de dónde sale un stream
        CAPA 4)  Evaluación perezosa: sin terminal no pasa nada
        CAPA 5)  Elemento por elemento, no paso por paso
        CAPA 6)  Cortocircuito y streams infinitos
        CAPA 7)  Un stream se usa UNA sola vez
        CAPA 8)  Las intermedias: filter, map, distinct, sorted, limit, skip, peek
        CAPA 9)  Las terminales: preguntar, buscar, contar, juntar
        CAPA 10) reduce: combinar todo en un valor
        CAPA 11) Las reglas: sin efectos secundarios, sin tocar la fuente
        CAPA 12) ¿Stream o for?
        CAPA 13) Errores comunes
        RESUMEN FINAL
*/
public class StreamsBasics {

    public static void main(String[] args) {
        check(whatIsAStreamExample(), "CAPA 1");
        check(sourcesExample(), "CAPA 3");
        check(lazinessExample(), "CAPA 4");
        check(verticalProcessingExample(), "CAPA 5");
        check(shortCircuitExample(), "CAPA 6");
        check(singleUseExample(), "CAPA 7");
        check(intermediateExample(), "CAPA 8");
        check(terminalExample(), "CAPA 9");
        check(reduceExample(), "CAPA 10");
        check(rulesExample(), "CAPA 11");
        System.out.println("StreamsBasics: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) QUÉ ES UN STREAM (Y QUÉ NO ES)
        =================================================================

        Problema típico: "los nombres de los clientes activos con más de 3
        pedidos, en orden alfabético". Con un ciclo:

            List<String> result = new ArrayList<>();
            for (Customer c : customers) {
                if (c.active() && c.orders() > 3) {
                    result.add(c.name());
                }
            }
            Collections.sort(result);

        Funciona, pero lo que QUIERES está mezclado con CÓMO lograrlo: la
        lista temporal, el if, el add, el sort. Con un stream:

            customers.stream()
                     .filter(c -> c.active() && c.orders() > 3)
                     .map(Customer::name)
                     .sorted()
                     .toList();

        Se lee como la frase del problema: filtra, toma el nombre, ordena.
        Esto es programación DECLARATIVA (dices QUÉ quieres) frente a
        IMPERATIVA (dices CÓMO hacerlo paso a paso).

        Un STREAM (flujo) es una secuencia de elementos que pasan por una
        cadena de operaciones. Lo que NO es:
        - NO es una colección: no guarda elementos. Los toma de una fuente
          (una lista, un arreglo, un archivo) y los va pasando.
        - NO modifica la fuente: filter no quita nada de la lista
          original; produce un resultado nuevo.
        - NO tiene nada que ver con InputStream/OutputStream (bytes de
          archivos y red; v8_apis/). Solo comparten el nombre.
    */
    record Customer(String name, boolean active, int orders) { }

    static final List<Customer> CUSTOMERS = List.of(
            new Customer("Luis", true, 5),
            new Customer("Ana", true, 8),
            new Customer("Beto", false, 10),
            new Customer("Mia", true, 2));

    static boolean whatIsAStreamExample() {
        List<String> imperative = new ArrayList<>();
        for (Customer customer : CUSTOMERS) {
            if (customer.active() && customer.orders() > 3) {
                imperative.add(customer.name());
            }
        }
        imperative.sort(null);

        List<String> declarative = CUSTOMERS.stream()
                .filter(customer -> customer.active() && customer.orders() > 3)
                .map(Customer::name)
                .sorted()
                .toList();

        return declarative.equals(imperative) && declarative.equals(List.of("Ana", "Luis"))
                && CUSTOMERS.size() == 4;                   // la fuente no cambió
    }

    /*
        =================================================================
        CAPA 2) LAS TRES PARTES: FUENTE, INTERMEDIAS, TERMINAL
        =================================================================

        Todo stream es una TUBERÍA (pipeline) de tres partes:

            CUSTOMERS.stream()          1) FUENTE: de dónde salen los elementos
                .filter(...)            2) INTERMEDIAS: transforman el flujo;
                .map(...)                  cada una devuelve OTRO Stream,
                .sorted()                  por eso se encadenan
                .toList();              3) TERMINAL: produce el resultado
                                           (una lista, un número, un boolean...)
                                           y CIERRA el stream

        Puede haber cero o muchas intermedias, pero solo UNA terminal, y
        siempre al final. Cómo distinguirlas: si el método devuelve
        Stream, es intermedia; si devuelve otra cosa (o void), es terminal.
    */

    /*
        =================================================================
        CAPA 3) LAS FUENTES: DE DÓNDE SALE UN STREAM
        =================================================================

            coleccion.stream()                   cualquier List, Set, Queue...
            map.entrySet().stream()              un Map, a través de sus vistas
            Arrays.stream(arreglo)               un arreglo
            Stream.of(a, b, c)                   valores sueltos
            Stream.empty()                       vacío
            IntStream.range(0, 5)                0, 1, 2, 3, 4      (el final no se incluye)
            IntStream.rangeClosed(1, 5)          1, 2, 3, 4, 5      (el final sí)
            Stream.iterate(semilla, siguiente)   infinito: semilla, f(semilla), f(f(semilla))...
            Stream.iterate(semilla, condición, siguiente)   como un for (Java 9)
            Stream.generate(supplier)            infinito: llama al Supplier cada vez
            "texto".chars()                      los caracteres como int
            Files.lines(ruta)                    las líneas de un archivo (v8_apis/)

        IntStream es un stream de int primitivos (sin boxing); se ve en
        AdvancedStreams.
    */
    static boolean sourcesExample() {
        List<Integer> fromRange = IntStream.rangeClosed(1, 5).boxed().toList();
        List<Integer> powersOfTwo = Stream.iterate(1, n -> n * 2).limit(5).toList();      // infinito, cortado
        List<Integer> likeAFor = Stream.iterate(0, n -> n < 10, n -> n + 3).toList();     // for (n = 0; n < 10; n += 3)
        long fromArray = Arrays.stream(new String[] {"a", "b"}).count();

        return fromRange.equals(List.of(1, 2, 3, 4, 5))
                && powersOfTwo.equals(List.of(1, 2, 4, 8, 16))
                && likeAFor.equals(List.of(0, 3, 6, 9))
                && fromArray == 2
                && Stream.empty().count() == 0;
    }

    /*
        =================================================================
        CAPA 4) EVALUACIÓN PEREZOSA: SIN TERMINAL NO PASA NADA
        =================================================================

        Las operaciones intermedias NO hacen nada al llamarlas. Solo
        ANOTAN qué hay que hacer. El trabajo empieza cuando llamas a la
        terminal, que "jala" los elementos a través de la tubería.

        Por eso esto no filtra nada ni imprime nada:
            names.stream().filter(n -> { System.out.println(n); return true; });
        No hay terminal: es una receta que nadie cocinó.

        Por qué Java lo hace así: si el trabajo se hace al final, sabiendo
        ya TODA la cadena, puede:
        - procesar cada elemento de una vez por toda la cadena, sin listas
          intermedias (CAPA 5);
        - detenerse en cuanto tenga la respuesta (CAPA 6);
        - trabajar con fuentes infinitas.
        Es la misma idea del Supplier perezoso (part1_lambdas/
        FunctionalInterfaces, CAPA 3).
    */
    static boolean lazinessExample() {
        List<String> visited = new ArrayList<>();
        Stream<String> pending = Stream.of("a", "b", "c")
                .filter(letter -> visited.add(letter));     // anotado, no ejecutado
        boolean nothingYet = visited.isEmpty();

        long count = pending.count();                       // la terminal arranca todo
        return nothingYet && count == 3 && visited.equals(List.of("a", "b", "c"));
    }

    /*
        =================================================================
        CAPA 5) ELEMENTO POR ELEMENTO, NO PASO POR PASO
        =================================================================

        Intuitivamente uno imagina que primero se filtran TODOS, luego se
        transforman TODOS, etc., con una lista entre cada paso. No es así.
        Cada elemento recorre la cadena COMPLETA antes de que entre el
        siguiente:

            Stream.of("ana", "bo", "carla").filter(largo > 2).map(mayúsculas)

            filter ana -> pasa -> map ana
            filter bo  -> no pasa (no llega a map)
            filter carla -> pasa -> map carla

        Ventajas: no se crean listas intermedias (menos memoria) y un
        elemento descartado no gasta trabajo en los pasos siguientes. Por
        eso conviene poner los filter ANTES que los map caros.

        Excepción: las operaciones "con estado" (stateful) como sorted y
        distinct necesitan recordar elementos anteriores. sorted no puede
        dejar salir el primero hasta haber visto TODOS (el último podría ser
        el menor). Es una "barrera" en medio de la tubería, y en un stream
        infinito nunca terminaría. distinct NO es barrera: guarda lo ya
        visto, pero deja pasar cada elemento nuevo de inmediato (medido:
        Stream.iterate(1, n -> n + 1).distinct().limit(3) da [1, 2, 3]).
    */
    static boolean verticalProcessingExample() {
        List<String> trace = new ArrayList<>();
        List<String> result = Stream.of("ana", "bo", "carla")
                .filter(name -> {
                    trace.add("filter " + name);
                    return name.length() > 2;
                })
                .map(name -> {
                    trace.add("map " + name);
                    return name.toUpperCase();
                })
                .toList();

        List<String> barrier = new ArrayList<>();
        Stream.of("c", "a", "b")
                .peek(letter -> barrier.add("antes " + letter))
                .sorted()                                   // espera a ver todos
                .peek(letter -> barrier.add("después " + letter))
                .findFirst();

        return result.equals(List.of("ANA", "CARLA"))
                && trace.equals(List.of("filter ana", "map ana", "filter bo", "filter carla", "map carla"))
                && barrier.equals(List.of("antes c", "antes a", "antes b", "después a"));
    }

    /*
        =================================================================
        CAPA 6) CORTOCIRCUITO Y STREAMS INFINITOS
        =================================================================

        Algunas operaciones pueden terminar SIN ver todos los elementos:
            findFirst, findAny        en cuanto encuentran uno
            anyMatch                  en cuanto uno cumple
            allMatch, noneMatch       en cuanto uno NO cumple / sí cumple
            limit(n)                  después de n elementos

        Gracias a la evaluación perezosa y al procesamiento elemento por
        elemento (CAPAS 4 y 5), "el primer múltiplo de 7 mayor que 0" en un
        stream INFINITO revisa exactamente 7 números y para.

        Un stream infinito (iterate, generate) SIEMPRE necesita algo que
        lo corte: limit, una operación de cortocircuito, o la versión de
        iterate con condición. Sin eso, el programa no termina.
    */
    static boolean shortCircuitExample() {
        int[] checked = {0};
        Optional<Integer> firstMultipleOf7 = Stream.iterate(1, n -> n + 1)   // 1, 2, 3, ... para siempre
                .filter(n -> {
                    checked[0]++;
                    return n % 7 == 0;
                })
                .findFirst();                               // se detiene al encontrarlo

        return firstMultipleOf7.get() == 7 && checked[0] == 7;
    }

    /*
        =================================================================
        CAPA 7) UN STREAM SE USA UNA SOLA VEZ
        =================================================================

        La terminal "consume" el stream. Usarlo otra vez falla:

            Stream<String> names = list.stream();
            names.count();
            names.count();   // IllegalStateException: stream has already
                             //   been operated upon or closed

        Por qué: un stream no guarda los elementos (CAPA 1). Si la fuente
        fuera un archivo o un generador, ya se leyeron; no hay forma
        general de "rebobinar". Es como un Iterator (v5_collections/
        part1_basics/Iterators, CAPA 1), no como una lista.

        Por eso casi nunca se guarda un Stream en una variable: se crea,
        se encadena y se termina en la misma expresión. Si necesitas el
        mismo pipeline varias veces, guarda la FUENTE (la lista) o un
        Supplier<Stream<T>> que cree uno nuevo cada vez.
    */
    static boolean singleUseExample() {
        Stream<String> once = Stream.of("a", "b");
        once.count();

        boolean reuseFails;
        try {
            once.count();
            reuseFails = false;
        } catch (IllegalStateException e) {
            reuseFails = e.getMessage().equals("stream has already been operated upon or closed");
        }

        Supplier<Stream<String>> fresh = () -> Stream.of("a", "b");   // una receta de streams
        return reuseFails && fresh.get().count() == 2 && fresh.get().count() == 2;
    }

    /*
        =================================================================
        CAPA 8) LAS INTERMEDIAS: FILTER, MAP, DISTINCT, SORTED, LIMIT, SKIP, PEEK
        =================================================================

        | Operación             | Hace                                         | Recibe       |
        |-----------------------|----------------------------------------------|--------------|
        | filter(condición)     | deja pasar solo los que cumplen              | Predicate    |
        | map(transformación)   | cambia cada elemento por otro (puede ser de  | Function     |
        |                       | OTRO tipo: Customer -> String)               |              |
        | distinct()            | quita repetidos (con equals/hashCode)        | —            |
        | sorted()              | ordena por el orden natural                  | —            |
        | sorted(comparador)    | ordena con un Comparator (Sorting)           | Comparator   |
        | limit(n)              | solo los primeros n                          | —            |
        | skip(n)               | se salta los primeros n                      | —            |
        | takeWhile(condición)  | toma mientras se cumpla, y para (Java 9)     | Predicate    |
        | dropWhile(condición)  | se salta mientras se cumpla (Java 9)         | Predicate    |
        | peek(acción)          | mira cada elemento sin cambiarlo             | Consumer     |

        Notas:
        - skip + limit = paginación: página p de tamaño t es
          skip(p * t).limit(t).
        - takeWhile NO es filter: filter revisa todos; takeWhile se
          detiene en el primero que no cumple. Sirve con datos ORDENADOS
          ("los pedidos de hoy" en una lista ordenada por fecha).
        - peek existe para DEPURAR (ver qué pasa por la tubería, como en
          la CAPA 5). No lo uses para lógica: puede no ejecutarse si Java
          optimiza la tubería (por ejemplo, count() puede calcular el
          tamaño sin recorrer).
        - map no puede "aplanar" (convertir un elemento en varios): eso es
          flatMap (AdvancedStreams).
    */
    static boolean intermediateExample() {
        List<Integer> numbers = List.of(5, 3, 8, 3, 1, 9, 2, 8);

        List<Integer> page2 = numbers.stream().distinct().sorted().skip(3).limit(3).toList();   // ordenados: 1 2 3 | 5 8 9
        List<String> labels = numbers.stream().filter(n -> n > 4).map(n -> "#" + n).toList();
        List<Integer> whileSmall = Stream.of(1, 2, 7, 3).takeWhile(n -> n < 5).toList();      // para en el 7
        List<Integer> filtered = Stream.of(1, 2, 7, 3).filter(n -> n < 5).toList();           // revisa todos
        List<String> byLength = Stream.of("pera", "uva", "manzana")
                .sorted(Comparator.comparingInt(String::length)).toList();

        return page2.equals(List.of(5, 8, 9))
                && labels.equals(List.of("#5", "#8", "#9", "#8"))
                && whileSmall.equals(List.of(1, 2)) && filtered.equals(List.of(1, 2, 3))
                && byLength.equals(List.of("uva", "pera", "manzana"));
    }

    /*
        =================================================================
        CAPA 9) LAS TERMINALES: PREGUNTAR, BUSCAR, CONTAR, JUNTAR
        =================================================================

        | Terminal                    | Devuelve                                   |
        |-----------------------------|--------------------------------------------|
        | toList()                    | List no modificable (Java 16)              |
        | collect(Collectors.toSet()) | Set; toCollection(TreeSet::new) para elegir |
        | toArray(String[]::new)      | arreglo                                    |
        | count()                     | long                                       |
        | anyMatch / allMatch / noneMatch | boolean                                |
        | findFirst() / findAny()     | Optional<T>                                |
        | min(comp) / max(comp)       | Optional<T>                                |
        | forEach(acción)             | nada: ejecuta la acción con cada uno       |
        | reduce(...)                 | un valor combinado (CAPA 10)               |

        Notas importantes:
        - findFirst, min y max devuelven Optional porque el stream puede
          estar VACÍO y no haber respuesta (part2_streams/OptionalUsage). Es la
          versión segura de Collections.max, que lanza excepción
          (CollectionsAndArraysUtilities, CAPA 2).
        - allMatch sobre un stream VACÍO devuelve true ("todos los
          elementos cumplen" es verdad si no hay ninguno: la "verdad
          vacía" de la lógica), y anyMatch devuelve false. Si "vacío" debe
          ser un error en tu caso, revísalo aparte.
        - toList() devuelve una lista NO modificable; collect(
          Collectors.toList()) no lo garantiza (ImmutableCollections,
          CAPA 9).
        - forEach es para EFECTOS al final (guardar, enviar). Si lo usas
          para llenar una lista de afuera, querías toList() o collect.
        - Collectors tiene mucho más (agrupar, contar por grupo, unir
          textos): AdvancedStreams.
    */
    static boolean terminalExample() {
        Optional<Customer> mostOrders = CUSTOMERS.stream().max(Comparator.comparingInt(Customer::orders));
        Optional<Customer> none = CUSTOMERS.stream().filter(c -> c.orders() > 100).findFirst();

        boolean anyInactive = CUSTOMERS.stream().anyMatch(c -> !c.active());
        boolean vacuousTruth = Stream.<Customer>empty().allMatch(Customer::active);   // vacío: true
        long activeCount = CUSTOMERS.stream().filter(Customer::active).count();
        Set<Integer> sortedOrders = CUSTOMERS.stream().map(Customer::orders)
                .collect(Collectors.toCollection(TreeSet::new));

        return mostOrders.get().name().equals("Beto") && none.isEmpty()
                && anyInactive && vacuousTruth && activeCount == 3
                && sortedOrders.toString().equals("[2, 5, 8, 10]");
    }

    /*
        =================================================================
        CAPA 10) REDUCE: COMBINAR TODO EN UN VALOR
        =================================================================

        reduce combina los elementos de dos en dos hasta que queda uno:

            Stream.of(1, 2, 3, 4).reduce(0, (a, b) -> a + b)
            ->  ((((0 + 1) + 2) + 3) + 4)  =  10

        Dos formas:
        - reduce(identidad, operación): empieza en la identidad. Si el
          stream está vacío, devuelve la identidad.
        - reduce(operación): sin valor inicial. Devuelve Optional, porque
          con un stream vacío no hay nada que combinar.

        Reglas que debes cumplir (por los streams paralelos,
        AdvancedStreams, que reparten el trabajo en trozos):
        1) La IDENTIDAD debe ser NEUTRA: no cambiar nada al combinarse.
           0 para la suma, 1 para la multiplicación, "" para concatenar.
           reduce(10, Integer::sum) parece "sumar y agregar 10", pero en
           paralelo cada trozo empieza en 10: con 4 trozos, se suman 40
           de más.
        2) La operación debe ser ASOCIATIVA: (a+b)+c == a+(b+c). La suma y
           el máximo lo son; la resta no.

        En la práctica, para lo común hay atajos más claros y rápidos:
        sum(), max() en streams primitivos (AdvancedStreams), count(), y
        Collectors.joining para textos. reduce es para combinaciones
        propias (por ejemplo, sumar BigDecimal).
    */
    static boolean reduceExample() {
        int sum = Stream.of(1, 2, 3, 4).reduce(0, Integer::sum);
        int product = Stream.of(1, 2, 3, 4).reduce(1, (a, b) -> a * b);     // identidad neutra de *: 1
        Optional<Integer> noIdentity = Stream.of(1, 2, 3).reduce(Integer::max);
        Optional<Integer> empty = Stream.<Integer>empty().reduce(Integer::max);
        BigDecimal total = Stream.of("19.99", "5.01")
                .map(BigDecimal::new)
                .reduce(BigDecimal.ZERO, BigDecimal::add);   // un uso real de reduce

        return sum == 10 && product == 24 && noIdentity.get() == 3 && empty.isEmpty()
                && total.compareTo(new BigDecimal("25.00")) == 0;
    }

    /*
        =================================================================
        CAPA 11) LAS REGLAS: SIN EFECTOS SECUNDARIOS, SIN TOCAR LA FUENTE
        =================================================================

        Las lambdas que pasas a un stream deben cumplir dos reglas (la
        documentación de java.util.stream las llama "non-interference" y
        "stateless"):

        1) NO MODIFICAR LA FUENTE mientras el stream la recorre. Agregar o
           quitar de la lista desde dentro del pipeline lanza
           ConcurrentModificationException (o algo peor), igual que en un
           for-each (Iterators, CAPA 5).

        2) NO DEPENDER NI MODIFICAR ESTADO DE AFUERA. Una lambda que agrega
           a una lista externa o suma en un contador externo (el truco de
           Lambdas, CAPA 8) funciona en secuencial, pero en paralelo da
           resultados equivocados o desordenados. Que el resultado salga
           de la TERMINAL (toList, count, reduce, collect), no de efectos
           secundarios.

        Los ejemplos de esta guía usan listas externas en peek y filter
        solo para MOSTRAR el orden de ejecución; en código real, no.
    */
    static boolean rulesExample() {
        List<Integer> source = new ArrayList<>(List.of(1, 2, 3));
        boolean modifyingSourceFails;
        try {
            source.stream().forEach(n -> {
                if (n == 1) {
                    source.add(99);                     // ERROR: modifica la fuente mientras se recorre
                }
            });
            modifyingSourceFails = false;
        } catch (ConcurrentModificationException e) {
            modifyingSourceFails = true;
        }

        List<Integer> doubledWrong = new ArrayList<>();
        List.of(1, 2, 3).stream().map(n -> n * 2).forEach(doubledWrong::add);   // efecto secundario: evítalo
        List<Integer> doubledRight = List.of(1, 2, 3).stream().map(n -> n * 2).toList();   // el resultado sale de la terminal

        return modifyingSourceFails && doubledRight.equals(doubledWrong);
    }

    /*
        =================================================================
        CAPA 12) ¿STREAM O FOR?
        =================================================================

        Los streams no reemplazan a los ciclos; son otra herramienta.

        Un STREAM brilla cuando:
        - el problema es una cadena de "filtra, transforma, junta";
        - quieres que el código se lea como la descripción del problema;
        - vas a agrupar o resumir datos (AdvancedStreams).

        Un FOR es mejor cuando:
        - necesitas break/continue complejos, o salir con return a la
          mitad de una lógica con varias condiciones;
        - modificas cosas en cada paso (el estado es parte del problema);
        - trabajas con índices (comparar el elemento i con el i+1);
        - el cuerpo lanza excepciones checked (Lambdas, CAPA 10);
        - calculas VARIAS cosas en una pasada (mínimo, máximo y suma a la
          vez: con streams serían tres recorridos, o un collector propio).

        Sobre rendimiento: un stream NO es más rápido que un for por ser
        moderno. Para colecciones pequeñas tiene un pequeño costo extra
        (crear la tubería, las lambdas). Casi nunca importa: elige por
        CLARIDAD, y mide (v12_jvm/) solo si es código crítico.

        Depurar: un stream largo es difícil de seguir con el depurador
        paso a paso. Divide en variables con nombre o usa peek
        temporalmente. IntelliJ tiene un "Stream Debugger" que muestra los
        datos en cada paso.
    */

    /*
        =================================================================
        CAPA 13) ERRORES COMUNES
        =================================================================

        1) Escribir intermedias sin terminal y esperar que algo pase
           (CAPA 4).

        2) Guardar un Stream y usarlo dos veces (CAPA 7).

        3) Un stream infinito sin limit ni cortocircuito, o con sorted
           antes del limit: nunca termina (CAPAS 5 y 6).

        4) Poner el map caro antes del filter que descarta casi todo
           (CAPA 5).

        5) Usar peek para lógica, o forEach para llenar una lista de afuera
           (CAPAS 8 y 9).

        6) Llamar .get() a un Optional de findFirst/max sin saber si hay
           valor (CAPA 9; part2_streams/OptionalUsage).

        7) Olvidar que allMatch de un stream vacío es true (CAPA 9).

        8) reduce con una identidad que no es neutra, o una operación no
           asociativa (CAPA 10).

        9) Modificar la fuente o estado externo desde las lambdas (CAPA 11).

        10) Convertir en stream cualquier ciclo por costumbre, aunque se
            lea peor (CAPA 12).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Un stream es una tubería sobre una secuencia de elementos: no
          guarda datos ni cambia la fuente. Es declarativo: dice QUÉ.
        - Fuente -> intermedias (devuelven Stream) -> UNA terminal.
        - Perezoso: nada se ejecuta hasta la terminal.
        - Cada elemento recorre toda la cadena antes del siguiente;
          sorted es una barrera; distinct recuerda lo visto.
        - findFirst, anyMatch, limit... cortan temprano; los infinitos los
          necesitan.
        - Un stream se usa una sola vez.
        - Intermedias: filter, map, distinct, sorted, limit, skip,
          takeWhile, dropWhile, peek (solo depurar).
        - Terminales: toList, collect, count, any/all/noneMatch,
          findFirst, min/max (Optional), forEach, reduce.
        - reduce: identidad neutra, operación asociativa.
        - Sin efectos secundarios ni tocar la fuente.
        - Stream para "filtra-transforma-junta"; for para lo demás.
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
