package v5_collections.part4_tools;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/*
    =====================================================================
    ORDENAR: COMPARABLE, COMPARATOR Y CÓMO ORDENA JAVA — DE CERO A TÉCNICO
    =====================================================================

    Ruta de collections/: guía 8 de 10  (part4_tools)
    Anterior:  part3_queues/PriorityQueues
    Siguiente: part4_tools/ImmutableCollections

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.

    Qué se da por sabido: qué son Comparable y Comparator, su contrato y
    por qué nunca se compara restando (v2_oop/part4_advanced_types/
    ObjectContract, CAPAS 9 y 10). Aquí se trata de USARLOS bien para
    ordenar.

    Índice:
        CAPA 1)  Dónde se ordena en Java
        CAPA 2)  Ordenar texto: Unicode, mayúsculas y acentos
        CAPA 3)  Construir comparadores sin escribir compare
        CAPA 4)  Las trampas de reversed()
        CAPA 5)  null al ordenar: nullsFirst y nullsLast
        CAPA 6)  Estabilidad: los empates conservan su orden
        CAPA 7)  Cómo ordena Java por dentro
        CAPA 8)  Primitivos: descendente y por columna
        CAPA 9)  Un comparador roto rompe el ordenamiento
        CAPA 10) Ordenar sin modificar el original
        CAPA 11) Aprovechar que ya está ordenado: búsqueda binaria
        CAPA 12) Cuándo NO ordenar
        CAPA 13) Errores comunes
        RESUMEN FINAL
*/
public class Sorting {

    public static void main(String[] args) {
        check(whereToSortExample(), "CAPA 1");
        check(textOrderExample(), "CAPA 2");
        check(comparatorBuildingExample(), "CAPA 3");
        check(reversedTrapExample(), "CAPA 4");
        check(nullsExample(), "CAPA 5");
        check(stabilityExample(), "CAPA 6");
        check(primitivesExample(), "CAPA 8");
        check(brokenComparatorExample(), "CAPA 9");
        check(nonDestructiveExample(), "CAPA 10");
        check(binarySearchExample(), "CAPA 11");
        System.out.println("Sorting: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) DÓNDE SE ORDENA EN JAVA
        =================================================================

        Ordenar es acomodar elementos según un criterio: de menor a mayor,
        alfabéticamente, por fecha. Es de las operaciones más usadas:
        mostrar resultados, preparar datos para buscar rápido (CAPA 11),
        y la base de muchos algoritmos.

        Las formas, según qué tengas:

        | Tienes          | Ordena "en el lugar" (cambia el original) | Sin tocar el original       |
        |-----------------|-------------------------------------------|-----------------------------|
        | Un arreglo      | Arrays.sort(arr)                           | copiar y ordenar            |
        | Una List        | list.sort(comparador)                      | stream().sorted().toList()  |
        | Datos que llegan| TreeSet / TreeMap (siempre ordenados)      |                             |

        Sobre las listas:
        - list.sort(comparador) es la forma moderna (Java 8+).
        - Collections.sort(list) es la antigua, de tu antigua JavaDataStructuresGuide;
          hoy simplemente llama a list.sort. Hacen lo mismo.
        - list.sort(null) usa el ORDEN NATURAL (Comparable): números de
          menor a mayor, texto por Unicode, fechas cronológicas.
        - list.sort(Comparator.reverseOrder()) o
          Collections.reverseOrder(): el natural al revés.

        Ordenar "en el lugar" significa que la lista misma cambia: no se
        crea otra. Es lo normal, pero tiene consecuencias (CAPA 10).
    */
    static boolean whereToSortExample() {
        int[] array = {5, 2, 8, 1, 9};
        Arrays.sort(array);

        List<Integer> list = new ArrayList<>(List.of(7, 3, 10, 1, 6));
        Collections.sort(list);                         // la forma antigua
        boolean ascending = list.equals(List.of(1, 3, 6, 7, 10));
        list.sort(Comparator.reverseOrder());           // la forma moderna, al revés

        return Arrays.equals(array, new int[] {1, 2, 5, 8, 9})
                && ascending && list.equals(List.of(10, 7, 6, 3, 1));
    }

    /*
        =================================================================
        CAPA 2) ORDENAR TEXTO: UNICODE, MAYÚSCULAS Y ACENTOS
        =================================================================

        El orden natural de String compara el NÚMERO de cada carácter en
        Unicode (v1_fundamentals/part3_text_and_references/Strings). Eso
        da tres sorpresas con texto real:

        1) Las MAYÚSCULAS van antes que TODAS las minúsculas ('Z' es 90,
           'a' es 97): "Zeta" queda antes que "ana".
        2) Las letras con ACENTO van después de la 'z' ('á' es 225):
           "árbol" queda al final.
        3) Los números dentro del texto se comparan como texto: "10" antes
           que "9" (como las versiones en ObjectContract, CAPA 9).

        Las soluciones:
        - String.CASE_INSENSITIVE_ORDER: ignora mayúsculas. Arregla 1, no 2.
        - Collator: compara texto como lo haría una PERSONA de cierto
          idioma. Collator.getInstance(Locale.forLanguageTag("es")) pone
          "árbol" junto a "ana", e ignora mayúsculas en la primera
          pasada. Es lo correcto para mostrar listas a usuarios (nombres,
          ciudades, productos).

        Cuándo usar cada uno:
        - Para la máquina (claves, ids, deduplicar): el orden natural,
          que es rápido y no depende del idioma.
        - Para mostrar a personas: Collator.
    */
    static boolean textOrderExample() {
        List<String> words = new ArrayList<>(List.of("zorro", "Zeta", "árbol", "ana", "Beto"));

        words.sort(null);
        boolean unicodeOrder = words.equals(List.of("Beto", "Zeta", "ana", "zorro", "árbol"));

        words.sort(String.CASE_INSENSITIVE_ORDER);
        boolean ignoringCase = words.equals(List.of("ana", "Beto", "Zeta", "zorro", "árbol"));   // la 'á' sigue al final

        words.sort(Collator.getInstance(Locale.forLanguageTag("es")));
        boolean likeAPerson = words.equals(List.of("ana", "árbol", "Beto", "Zeta", "zorro"));

        return unicodeOrder && ignoringCase && likeAPerson;
    }

    /*
        =================================================================
        CAPA 3) CONSTRUIR COMPARADORES SIN ESCRIBIR COMPARE
        =================================================================

        Tu antigua JavaDataStructuresGuide escribía cada comparador a mano:
            people.sort((p1, p2) -> Integer.compare(p1.age, p2.age));
        Es correcto, pero Comparator trae MÉTODOS DE FÁBRICA que lo dicen
        más claro y con menos errores:

            Comparator.comparing(Person::name)          por un campo Comparable
            Comparator.comparingInt(Person::age)        por un int (sin boxing)
            comparingLong / comparingDouble             igual, para long y double
            .thenComparing(Person::name)                desempate: si empatan, por esto
            .thenComparingInt(Person::age)
            .reversed()                                 todo al revés (CAPA 4)
            Comparator.naturalOrder() / reverseOrder()  orden natural y su inverso
            Comparator.comparing(campo, otroComparador) por un campo, con un orden propio

        Se leen casi como una frase: "compara por departamento, luego por
        salario de mayor a menor, luego por nombre".

        Por qué comparingInt y no comparing para un int: comparing
        necesita objetos, así que convertiría cada int en Integer en cada
        comparación (v1_fundamentals/part3_text_and_references/Wrappers,
        CAPA 7). En listas grandes se nota.

        (Person::name es una referencia a método: "el name de cada
        persona". Se explica en v7_functional/part1_lambdas/MethodReferences.)
    */
    record Employee(String name, String department, int salary) { }

    static final List<Employee> STAFF = List.of(
            new Employee("Luis", "TI", 30_000),
            new Employee("Ana", "Ventas", 25_000),
            new Employee("Carlos", "TI", 45_000),
            new Employee("Mia", "TI", 30_000),
            new Employee("Bea", "Ventas", 25_000));

    static List<String> names(List<Employee> employees) {
        List<String> names = new ArrayList<>();
        for (Employee employee : employees) {
            names.add(employee.name());
        }
        return names;
    }

    static boolean comparatorBuildingExample() {
        List<Employee> staff = new ArrayList<>(STAFF);

        staff.sort((a, b) -> Integer.compare(a.salary(), b.salary()));   // a mano: correcto pero ruidoso
        List<String> byHand = names(staff);
        staff.sort(Comparator.comparingInt(Employee::salary));           // lo mismo, dicho más claro
        boolean sameResult = names(staff).equals(byHand);

        staff.sort(Comparator.comparing(Employee::department)
                .thenComparing(Employee::salary, Comparator.reverseOrder())   // solo el salario, descendente
                .thenComparing(Employee::name));

        return sameResult
                && names(staff).equals(List.of("Carlos", "Luis", "Mia", "Ana", "Bea"));
    }

    /*
        =================================================================
        CAPA 4) LAS TRAMPAS DE REVERSED()
        =================================================================

        TRAMPA 1: reversed() invierte TODO lo que está a su izquierda, no
        solo el último criterio.
            comparing(Employee::department).thenComparing(Employee::name).reversed()
        no es "departamento ascendente, nombre descendente": es
        departamento DESCENDENTE y nombre descendente.

        Para invertir UN solo criterio, pásale el orden a ese criterio:
            thenComparing(Employee::name, Comparator.reverseOrder())
        o, si es numérico, invierte solo ese comparador:
            thenComparing(Comparator.comparingInt(Employee::salary).reversed())

        TRAMPA 2: con una LAMBDA en lugar de una referencia a método,
        reversed() puede no compilar:

            // ERROR: cannot find symbol
            //   symbol:   method name()
            //   location: variable e of type Object
            staff.sort(Comparator.comparing(e -> e.name()).reversed());

        Por qué: Java deduce los tipos de los genéricos "de afuera hacia
        adentro" (v4_generics/GenericsBasics, inferencia). Al llamar
        .reversed() sobre el resultado de comparing(...), ese comparing ya
        no está directamente en el sort, así que Java no sabe que "e" es
        un Employee y supone Object. Soluciones: usar Employee::name, o
        declarar el tipo: (Employee e) -> e.name().
    */
    static boolean reversedTrapExample() {
        List<Employee> staff = new ArrayList<>(STAFF);

        staff.sort(Comparator.comparing(Employee::department).thenComparing(Employee::name).reversed());
        boolean allReversed = names(staff).equals(List.of("Bea", "Ana", "Mia", "Luis", "Carlos"));   // Ventas primero

        staff.sort(Comparator.comparing(Employee::department).thenComparing(Employee::name, Comparator.reverseOrder()));
        boolean onlyName = names(staff).equals(List.of("Mia", "Luis", "Carlos", "Bea", "Ana"));      // TI primero

        staff.sort(Comparator.comparing((Employee e) -> e.name()).reversed());   // con el tipo declarado sí compila
        boolean typedLambda = names(staff).get(0).equals("Mia");

        return allReversed && onlyName && typedLambda;
    }

    /*
        =================================================================
        CAPA 5) NULL AL ORDENAR: NULLSFIRST Y NULLSLAST
        =================================================================

        Si un campo por el que ordenas puede ser null (un correo opcional,
        una fecha de baja), comparing lanza NullPointerException al
        intentar compararlo: compareTo no se puede llamar sobre null.

        En lugar de llenar el comparador de ifs, envuelve el orden de ese
        campo:
            Comparator.comparing(Client::email, Comparator.nullsLast(Comparator.naturalOrder()))
        "Por correo; los que no tienen, al final". nullsFirst los pone al
        principio.

        (Mejor aún es que el dato no sea null: un Optional o un valor por
        defecto. Pero con datos que vienen de fuera, pasa.)
    */
    record Client(String name, String email) { }

    static boolean nullsExample() {
        List<Client> clients = new ArrayList<>(Arrays.asList(   // Arrays.asList acepta null; List.of no
                new Client("Luis", "luis@correo.com"),
                new Client("Ana", null),
                new Client("Mia", "ana@correo.com")));

        boolean plainComparingFails;
        try {
            clients.sort(Comparator.comparing(Client::email));
            plainComparingFails = false;
        } catch (NullPointerException e) {
            plainComparingFails = true;
        }

        clients.sort(Comparator.comparing(Client::email, Comparator.nullsLast(Comparator.naturalOrder())));

        return plainComparingFails
                && clients.get(0).name().equals("Mia")
                && clients.get(2).name().equals("Ana");     // sin correo: al final
    }

    /*
        =================================================================
        CAPA 6) ESTABILIDAD: LOS EMPATES CONSERVAN SU ORDEN
        =================================================================

        Un ordenamiento es ESTABLE si los elementos que empatan quedan en
        el MISMO orden relativo que tenían antes.

        list.sort y Arrays.sort con objetos SON estables: está garantizado
        en su documentación. (PriorityQueue no lo es; PriorityQueues,
        CAPA 8.)

        Para qué sirve: ordenar por varios criterios en VARIAS PASADAS,
        como en una hoja de cálculo cuando das clic en una columna y luego
        en otra. Si la lista ya está ordenada por nombre y la ordenas por
        departamento, dentro de cada departamento los nombres SIGUEN en
        orden alfabético.

        Por eso el orden de las pasadas es al revés de la importancia:
        primero el criterio MENOS importante, al final el MÁS importante.
        Da lo mismo que un solo sort con thenComparing (CAPA 3); las
        pasadas son útiles cuando el usuario elige los criterios uno por
        uno en una tabla.
    */
    static boolean stabilityExample() {
        List<Employee> staff = new ArrayList<>(STAFF);
        staff.sort(Comparator.comparing(Employee::name));           // primera pasada: el criterio menor
        staff.sort(Comparator.comparing(Employee::department));     // segunda: el mayor; los empates conservan el orden

        List<Employee> oneSort = new ArrayList<>(STAFF);
        oneSort.sort(Comparator.comparing(Employee::department).thenComparing(Employee::name));

        return names(staff).equals(List.of("Carlos", "Luis", "Mia", "Ana", "Bea"))
                && staff.equals(oneSort);
    }

    /*
        =================================================================
        CAPA 7) CÓMO ORDENA JAVA POR DENTRO
        =================================================================

        Java usa DOS algoritmos distintos según qué ordenes.

        1) PRIMITIVOS (int[], double[]...): DUAL-PIVOT QUICKSORT.
           Elige dos "pivotes" y reparte el arreglo en tres zonas (menores
           que el primero, entre los dos, mayores que el segundo), y repite
           en cada zona. En promedio O(n log n), muy rápido en la práctica
           y sin memoria extra. Para arreglos pequeños usa ordenamiento por
           inserción, que gana con pocos elementos. (Desde Java 14 cambia
           a heapsort si detecta un caso que se volvería lento, así que
           nunca llega a O(n²).)
           NO es estable, pero con primitivos no importa: dos 5 son
           indistinguibles, no hay "cuál iba primero".

        2) OBJETOS (listas, Integer[], String[]...): TIMSORT.
           Un híbrido de merge sort e inserción. Busca tramos que YA
           vienen ordenados ("runs") y los mezcla. Por eso:
           - es ESTABLE (CAPA 6), que con objetos sí importa;
           - es O(n log n) garantizado en el peor caso;
           - con datos casi ordenados es casi O(n): una lista ya ordenada
             se "ordena" en un recorrido. Los datos reales suelen venir
             parcialmente ordenados.
           Usa memoria extra (hasta n/2 referencias) para mezclar.

        Por qué no usan el mismo: la estabilidad solo vale con objetos, y
        para primitivos quicksort es más rápido y no gasta memoria.

        Extra: Arrays.parallelSort reparte arreglos muy grandes entre los
        núcleos del procesador (v13_concurrency/). Solo conviene con muchos
        datos (cientos de miles o más).

        (Cómo funcionan quicksort, merge sort y los demás paso a paso:
        algorithms/SortingAlgorithms.)
    */

    /*
        =================================================================
        CAPA 8) PRIMITIVOS: DESCENDENTE Y POR COLUMNA
        =================================================================

        Arrays.sort(int[]) solo ordena de MENOR A MAYOR (tu antiguo CheatSheet:
        "primitivos: solo ascendente"). No acepta un Comparator, porque
        un Comparator<T> trabaja con objetos y los primitivos no lo son.
        Para descendente hay tres opciones:

        1) Ordenar ascendente y voltear el arreglo en el lugar. Rápido y
           sin objetos: la mejor para arreglos grandes.
        2) Usar Integer[] y Collections.reverseOrder() (tu antiguo CheatSheet).
           Funciona, pero cada número es un objeto: más memoria y más
           lento (Wrappers, CAPA 7).
        3) Con streams: IntStream.of(arr).boxed().sorted(reverseOrder())...
           cómodo, también con boxing (v7_functional/part2_streams/StreamsBasics).

        Un int[][] SÍ acepta Comparator, porque cada fila (int[]) es un
        objeto. Así ordenas filas por una columna, como los intervalos de
        tu antiguo CheatSheet:
            Arrays.sort(intervals, Comparator.comparingInt(row -> row[0]));
        Y Arrays.sort(arr, desde, hasta) ordena solo un tramo.
    */
    static void sortDescending(int[] values) {
        Arrays.sort(values);
        for (int left = 0, right = values.length - 1; left < right; left++, right--) {
            int temporary = values[left];               // voltear en el lugar: dos índices hacia el centro
            values[left] = values[right];
            values[right] = temporary;
        }
    }

    static boolean primitivesExample() {
        int[] scores = {70, 95, 82, 60};
        sortDescending(scores);

        Integer[] boxed = {70, 95, 82, 60};
        Arrays.sort(boxed, Collections.reverseOrder());     // funciona, pero con objetos

        int[][] intervals = {{5, 8}, {1, 3}, {2, 6}};
        Arrays.sort(intervals, Comparator.comparingInt(row -> row[0]));   // cada fila es un objeto

        int[] partial = {9, 8, 7, 3, 2, 1};
        Arrays.sort(partial, 0, 3);                         // solo las posiciones 0, 1 y 2

        return Arrays.equals(scores, new int[] {95, 82, 70, 60})
                && Arrays.equals(boxed, new Integer[] {95, 82, 70, 60})
                && intervals[0][0] == 1 && intervals[1][0] == 2 && intervals[2][0] == 5
                && Arrays.equals(partial, new int[] {7, 8, 9, 3, 2, 1});
    }

    /*
        =================================================================
        CAPA 9) UN COMPARADOR ROTO ROMPE EL ORDENAMIENTO
        =================================================================

        TimSort CONFÍA en que el comparador cumple su contrato
        (ObjectContract, CAPA 9): si a < b y b < c, entonces a < c; si
        a < b, entonces b > a; y siempre da la misma respuesta para los
        mismos dos elementos.

        Si no lo cumple, pasan dos cosas posibles:
        - el resultado sale mal ordenado, sin avisar; o
        - TimSort detecta la contradicción a la mitad y lanza:
              IllegalArgumentException: Comparison method violates its general contract!

        Ese mensaje aparece en sistemas reales y desconcierta, porque el
        error no está en el sort sino en el comparador. Causas típicas:
        - comparar restando (desbordamiento; ObjectContract, CAPA 9);
        - comparar double con < y > a mano, olvidando NaN (usa
          Double.compare);
        - un comparador que depende de algo que cambia mientras se ordena
          (la hora, un valor aleatorio, un campo que otro hilo modifica).

        El ejemplo usa el caso extremo: un comparador que responde al azar.
    */
    static boolean brokenComparatorExample() {
        List<Integer> numbers = new ArrayList<>();
        for (int i = 0; i < 1_000; i++) {
            numbers.add(i);
        }
        Random random = new Random(1);
        try {
            numbers.sort((a, b) -> random.nextInt(3) - 1);  // ERROR de diseño: responde al azar
        } catch (IllegalArgumentException e) {
            return e.getMessage().equals("Comparison method violates its general contract!");
        }
        List<Integer> sorted = new ArrayList<>(numbers);
        Collections.sort(sorted);
        return !numbers.equals(sorted);                     // si no lo detectó, al menos quedó mal ordenada
    }

    /*
        =================================================================
        CAPA 10) ORDENAR SIN MODIFICAR EL ORIGINAL
        =================================================================

        list.sort cambia la lista. A veces eso es un error:
        - la lista te la pasaron como parámetro, y quien llamó no espera
          que le reordenes sus datos (v2_oop/part2_pillars/Encapsulation:
          copias defensivas);
        - la lista es INMUTABLE (List.of, Collections.unmodifiableList, la
          que devuelve stream().toList()): sort lanza
          UnsupportedOperationException, incluso si ya estaba ordenada o
          tiene un solo elemento.

        Para obtener una versión ordenada sin tocar el original:
            List<T> ordenada = new ArrayList<>(original);  ordenada.sort(...);
            List<T> ordenada = original.stream().sorted(comparador).toList();

        Y para recorrer al revés sin copiar ni reordenar, desde Java 21:
            lista.reversed()      // una VISTA invertida (SequencedCollection)
    */
    static List<Integer> sortedCopy(List<Integer> original) {
        return original.stream().sorted().toList();
    }

    static boolean nonDestructiveExample() {
        List<Integer> fixed = List.of(3, 1, 2);

        boolean immutableFails;
        try {
            fixed.sort(null);
            immutableFails = false;
        } catch (UnsupportedOperationException e) {
            immutableFails = true;
        }

        List<Integer> copy = sortedCopy(fixed);
        List<Integer> backwards = copy.reversed();          // vista: no copia ni reordena

        return immutableFails && fixed.equals(List.of(3, 1, 2))      // el original intacto
                && copy.equals(List.of(1, 2, 3)) && backwards.equals(List.of(3, 2, 1));
    }

    /*
        =================================================================
        CAPA 11) APROVECHAR QUE YA ESTÁ ORDENADO: BÚSQUEDA BINARIA
        =================================================================

        Un motivo para ordenar es poder BUSCAR rápido: en datos ordenados
        la búsqueda binaria descarta la mitad en cada paso, O(log n)
        (v1_fundamentals/part4_data_and_input/Arrays, CAPA 6). Para listas
        está Collections.binarySearch(lista, x).

        Lo que devuelve:
        - si lo encuentra: su posición (>= 0);
        - si no: -(punto de inserción) - 1, un número NEGATIVO que dice
          DÓNDE iría. El "-1" existe para que el punto de inserción 0 no
          se confunda con "encontrado en la posición 0".

        Ese punto de inserción sirve para INSERTAR manteniendo el orden
        sin volver a ordenar todo: buscas, calculas la posición y haces
        add(posicion, x).

        Dos reglas:
        - Solo funciona si la lista está ordenada CON EL MISMO criterio
          que usas para buscar; si no, el resultado no tiene sentido (no
          lanza error).
        - En una ArrayList, add(posicion, x) sigue moviendo elementos:
          O(n). Si insertas mucho, un TreeSet o TreeMap es mejor (Sets,
          CAPA 5).
    */
    static void insertSorted(List<Integer> sorted, int value) {
        int position = Collections.binarySearch(sorted, value);
        if (position < 0) {
            position = -position - 1;                       // deshacer el "-(inserción) - 1"
        }
        sorted.add(position, value);
    }

    static boolean binarySearchExample() {
        List<Integer> sorted = new ArrayList<>(List.of(1, 3, 5, 7));
        boolean found = Collections.binarySearch(sorted, 5) == 2;
        boolean notFound = Collections.binarySearch(sorted, 4) == -3;   // iría en la posición 2: -(2) - 1

        insertSorted(sorted, 4);
        insertSorted(sorted, 0);
        insertSorted(sorted, 9);

        return found && notFound && sorted.equals(List.of(0, 1, 3, 4, 5, 7, 9));
    }

    /*
        =================================================================
        CAPA 12) CUÁNDO NO ORDENAR
        =================================================================

        Ordenar cuesta O(n log n). Muchas veces se ordena por costumbre
        cuando hay algo más barato:

        | Quieres                              | En lugar de ordenar           | Costo      |
        |--------------------------------------|-------------------------------|------------|
        | El menor o el mayor                  | Collections.min / max         | O(n)       |
        | Los K mayores                        | PriorityQueue de tamaño K     | O(n log K) |
        | Siempre ordenado mientras cambia     | TreeSet / TreeMap             | O(log n) c/u |
        | Saber si algo está                   | HashSet                       | O(1)       |
        | Mostrar ordenado una vez             | sí: list.sort                 | O(n log n) |

        Y nunca ordenes DENTRO de un ciclo la misma lista una y otra vez:
        ordénala una vez afuera, o usa una estructura que se mantenga
        ordenada.
    */

    /*
        =================================================================
        CAPA 13) ERRORES COMUNES
        =================================================================

        1) Comparar restando, o double con < y >: desborda o falla con NaN
           (CAPA 9).

        2) Ordenar texto para personas con el orden natural: mayúsculas
           primero y acentos al final (CAPA 2).

        3) Creer que reversed() invierte solo el último criterio (CAPA 4).

        4) Una lambda antes de .reversed() que no compila: usa una
           referencia a método o declara el tipo (CAPA 4).

        5) Ordenar por un campo que puede ser null sin nullsFirst/nullsLast
           (CAPA 5).

        6) Ordenar una lista que te pasaron, o una inmutable (CAPA 10).

        7) Buscar con binarySearch en una lista que no está ordenada con
           el mismo criterio (CAPA 11).

        8) Usar Integer[] con reverseOrder para arreglos grandes, en lugar
           de ordenar int[] y voltear (CAPA 8).

        9) Ordenar todo para quedarse con el mínimo o los primeros K
           (CAPA 12).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Arrays.sort para arreglos; list.sort para listas (Collections.sort
          es lo mismo); sort(null) = orden natural.
        - Texto: orden Unicode (mayúsculas primero, acentos al final);
          CASE_INSENSITIVE_ORDER o, para personas, Collator.
        - Comparadores: comparing / comparingInt / thenComparing /
          reversed / nullsLast. Nunca restar.
        - reversed() invierte todo lo anterior; para un solo criterio,
          pásale reverseOrder() a ese criterio.
        - Objetos: TimSort, estable, O(n log n), casi O(n) si ya viene
          ordenado. Primitivos: Dual-Pivot Quicksort, no estable, solo
          ascendente.
        - Un comparador que rompe el contrato da resultados malos o
          "Comparison method violates its general contract!".
        - Sin tocar el original: copiar o stream().sorted().toList().
        - binarySearch para buscar e insertar en orden; y no ordenes si
          min/max, un heap o un TreeSet bastan.
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
