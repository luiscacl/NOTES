package v4_generics;

import java.lang.reflect.Array;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

/*
    =====================================================================
    GENÉRICOS AVANZADOS — DE INTERMEDIO A NIVEL TÉCNICO
    =====================================================================

    Ruta de v4_generics/: guía 2 de 2
    Anterior:  GenericsBasics
    Siguiente: v5_collections/part1_basics/CollectionsOverview (última de v4_generics/)

    Requisito: GenericsBasics.

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.

    Índice:
        CAPA 1)  Tipos acotados: <T extends Algo>
        CAPA 2)  Invarianza: List<Integer> NO es una List<Number>
        CAPA 3)  El comodín ?: "una lista de algo"
        CAPA 4)  ? extends: leer de un productor
        CAPA 5)  ? super: escribir en un consumidor
        CAPA 6)  PECS: la regla para elegir
        CAPA 7)  Comparable<? super T>: un caso real con LocalDate
        CAPA 8)  Borrado de tipos: los genéricos desaparecen al ejecutar
        CAPA 9)  Trabajar con el borrado: Supplier y Class<T>
        CAPA 10) Varargs genéricos y @SafeVarargs
        CAPA 11) Errores comunes
        RESUMEN FINAL
*/
public class AdvancedGenerics {

    public static void main(String[] args) {
        check(boundedTypesExample(), "CAPA 1");
        check(unboundedWildcardExample(), "CAPA 3");
        check(extendsWildcardExample(), "CAPA 4");
        check(superWildcardExample(), "CAPA 5");
        check(pecsExample(), "CAPA 6");
        check(comparableSuperExample(), "CAPA 7");
        check(erasureExample(), "CAPA 8");
        check(erasureWorkaroundsExample(), "CAPA 9");
        check(safeVarargsExample(), "CAPA 10");
        System.out.println("AdvancedGenerics: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) TIPOS ACOTADOS: <T EXTENDS ALGO>
        =================================================================

        En un genérico simple, T puede ser CUALQUIER tipo, así que dentro
        del código solo puedes usar lo que tiene TODO objeto (los métodos
        de Object). No puedes comparar dos T, ni sumarlos.

        Un TIPO ACOTADO (bounded) pone un límite: "T puede ser cualquier
        tipo, siempre que sea un Number" (o que sea Comparable...).
            static <T extends Number> double sum(List<T> values)
        Ahora el compilador sabe que todo T tiene los métodos de Number
        (como doubleValue()), y te deja usarlos.

        - extends aquí significa "es un", y sirve también para
          interfaces: <T extends Comparable<T>> ("T se puede comparar con
          otro T").
        - Varios límites a la vez con &:  <T extends Number & Comparable<T>>
          (si uno es una clase, va primero).

        Por qué existe: permite escribir algoritmos genéricos que NECESITAN
        algo del tipo (comparar para ordenar, operar números) sin perder la
        revisión de tipos.
    */
    static <T extends Comparable<T>> T maxOf(List<T> values) {
        T max = values.get(0);
        for (T value : values) {
            if (value.compareTo(max) > 0) {     // posible porque T "es un" Comparable<T>
                max = value;
            }
        }
        return max;
    }

    static <T extends Number> double sumOf(List<T> values) {
        double total = 0;
        for (T value : values) {
            total += value.doubleValue();       // posible porque T "es un" Number
        }
        return total;
    }

    static boolean boundedTypesExample() {
        return maxOf(List.of(3, 9, 4)) == 9
                && maxOf(List.of("pera", "uva", "manzana")).equals("uva")
                && sumOf(List.of(1, 2, 3)) == 6.0
                && sumOf(List.of(1.5, 2.5)) == 4.0;
        // maxOf(List.of(new Object()));   // ERROR: Object no es Comparable
    }

    /*
        =================================================================
        CAPA 2) INVARIANZA: LIST<INTEGER> NO ES UNA LIST<NUMBER>
        =================================================================

        Esto sorprende a todos. Integer ES UN Number. Pero:
            List<Integer> integers = new ArrayList<>();
            List<Number> numbers = integers;
            // ERROR: incompatible types: List<Integer> cannot be converted to List<Number>

        Por qué Java lo prohíbe: si lo permitiera, podrías hacer
            numbers.add(3.14);           // un Double es un Number: válido para List<Number>
        y acabarías con un Double DENTRO de la lista de Integer. Cuando
        otro código leyera integers.get(0) esperando un Integer: fallo.

        Esto es exactamente el problema de los ARREGLOS, que sí lo
        permiten y lo pagan con ArrayStoreException al ejecutar
        (v1_fundamentals/.../Arrays, CAPA 10). Los genéricos lo cortan al
        COMPILAR. Se dice que los genéricos son INVARIANTES: List<A> y
        List<B> no tienen ninguna relación, aunque A y B sí la tengan.

        El problema práctico: ¿cómo escribes un método que sume una
        List<Integer> Y una List<Double>? Con List<Number> no puedes
        pasarle ninguna de las dos. Para eso existen los COMODINES
        (CAPAS 3 a 6). (sumOf de la CAPA 1 lo resolvía con un tipo
        acotado; la CAPA 4 lo resuelve con un comodín.)
    */

    /*
        =================================================================
        CAPA 3) EL COMODÍN ?: "UNA LISTA DE ALGO"
        =================================================================

        List<?> significa "una lista de ALGÚN tipo, que no sé cuál es".
        Acepta List<String>, List<Integer>, List<lo que sea>.

        Lo que puedes hacer con ella:
        - LEER: cada elemento sale como Object (es lo único seguro).
        - Preguntar size(), isEmpty(), recorrer.
        - NO puedes AGREGAR nada (salvo null): como no sabes qué tipo
          guarda, cualquier cosa podría ser incorrecta.

        Se usa en métodos que trabajan con la colección sin importar el
        tipo de sus elementos.

        Diferencia con List<Object>: una List<Object> solo acepta
        List<Object> (invarianza); una List<?> acepta cualquier lista.
    */
    static String describe(List<?> list) {
        return list.size() + " elementos, el primero es " + (list.isEmpty() ? "nada" : list.get(0));
    }

    static boolean unboundedWildcardExample() {
        return describe(List.of("a", "b")).equals("2 elementos, el primero es a")
                && describe(List.of(10, 20, 30)).equals("3 elementos, el primero es 10")
                && describe(List.of()).equals("0 elementos, el primero es nada");
    }

    /*
        =================================================================
        CAPA 4) ? EXTENDS: LEER DE UN PRODUCTOR
        =================================================================

        List<? extends Number> significa "una lista de algún tipo que ES
        UN Number": puede ser List<Integer>, List<Double>, List<Number>...

        - LEER: es seguro leer cada elemento como Number, sea cual sea la
          lista real.
        - AGREGAR: no se puede. Si la lista real es List<Integer>, meter
          un Double la rompería; y el compilador no sabe cuál es:
              values.add(1);   // ERROR: incompatible types: int cannot be converted to CAP#1
          (CAP#1 es el nombre que el compilador le da a "ese tipo
          desconocido que es un Number".)

        Por eso se dice que una colección con ? extends es un PRODUCTOR:
        solo te DA elementos.
    */
    static double total(List<? extends Number> values) {
        double total = 0;
        for (Number value : values) {           // leer como Number siempre es seguro
            total += value.doubleValue();
        }
        return total;
    }

    static boolean extendsWildcardExample() {
        List<Integer> integers = List.of(1, 2, 3);
        List<Double> doubles = List.of(0.5, 0.5);
        // Una List<Number> no aceptaría ninguna de las dos (CAPA 2); ? extends Number acepta ambas.
        return total(integers) == 6.0 && total(doubles) == 1.0;
    }

    /*
        =================================================================
        CAPA 5) ? SUPER: ESCRIBIR EN UN CONSUMIDOR
        =================================================================

        List<? super Integer> significa "una lista de algún tipo que es
        Integer o un PADRE de Integer": List<Integer>, List<Number> o
        List<Object>.

        - AGREGAR un Integer: SEGURO. En cualquiera de esas listas cabe un
          Integer (en una List<Number> cabe porque Integer es Number; en
          una List<Object>, porque todo es Object).
        - LEER: solo como Object, porque podría ser una List<Object> con
          cualquier cosa adentro.

        Por eso una colección con ? super es un CONSUMIDOR: tú le DAS
        elementos.

        Ejemplo útil: un método que llena una lista con números. Gracias a
        ? super, sirve para guardar en una List<Integer>, pero también en
        una List<Number> o List<Object> que ya tenías.
    */
    static void addRange(List<? super Integer> target, int from, int to) {
        for (int i = from; i <= to; i++) {
            target.add(i);                      // un Integer cabe en cualquiera de esas listas
        }
    }

    static boolean superWildcardExample() {
        List<Integer> integers = new ArrayList<>();
        List<Number> numbers = new ArrayList<>(List.of(0.5));
        List<Object> anything = new ArrayList<>(List.of("texto"));

        addRange(integers, 1, 3);
        addRange(numbers, 1, 2);
        addRange(anything, 1, 1);

        return integers.equals(List.of(1, 2, 3))
                && numbers.size() == 3
                && anything.equals(List.of("texto", 1));
    }

    /*
        =================================================================
        CAPA 6) PECS: LA REGLA PARA ELEGIR
        =================================================================

        La regla mnemotécnica de Effective Java:

            PECS = Producer Extends, Consumer Super

        - Si un parámetro te DA elementos (lo LEES) -> ? extends T
        - Si un parámetro RECIBE elementos (lo LLENAS) -> ? super T
        - Si haces las dos cosas -> T exacto, sin comodín
        - Si no te importa el tipo -> ?

        El ejemplo perfecto es copiar de una lista a otra (así está escrito
        Collections.copy en el JDK):
            static <T> void copy(List<? super T> destination, List<? extends T> source)
        source PRODUCE (extends), destination CONSUME (super).
        Gracias a eso puedes copiar una List<Integer> a una List<Number>.

        Otro caso real: ordenar. Un Comparator<Person> sabe comparar
        cualquier Person, así que también sirve para ordenar empleados
        (que SON Person). Por eso List.sort recibe Comparator<? super E>:
        el comparador CONSUME elementos de la lista.

        Por qué importa: con los comodines correctos, tus métodos
        aceptan MÁS tipos sin perder seguridad. Sin ellos, quien los use
        se encuentra con errores de invarianza (CAPA 2) que no tienen
        sentido para él.
    */
    static <T> void copy(List<? super T> destination, List<? extends T> source) {
        for (T item : source) {                 // leer del productor
            destination.add(item);              // escribir en el consumidor
        }
    }

    static class Person {
        private final String name;

        Person(String name) {
            this.name = name;
        }

        String getName() {
            return name;
        }
    }

    static final class Employee extends Person {
        Employee(String name) {
            super(name);
        }
    }

    static <T> List<T> sortedCopy(List<T> list, Comparator<? super T> comparator) {
        List<T> copy = new ArrayList<>(list);
        copy.sort(comparator);
        return copy;
    }

    static boolean pecsExample() {
        List<Integer> source = List.of(1, 2, 3);
        List<Number> destination = new ArrayList<>(List.of(0.5));
        copy(destination, source);                                   // Integer -> Number: válido gracias a PECS

        Comparator<Person> byName = Comparator.comparing(Person::getName);   // sabe comparar cualquier Person
        List<Employee> employees = List.of(new Employee("Luis"), new Employee("Ana"));
        List<Employee> sorted = sortedCopy(employees, byName);               // ...y por eso ordena empleados

        return destination.size() == 4 && sorted.get(0).getName().equals("Ana");
    }

    /*
        =================================================================
        CAPA 7) COMPARABLE<? SUPER T>: UN CASO REAL CON LOCALDATE
        =================================================================

        El maxOf de la CAPA 1 tiene un límite <T extends Comparable<T>>:
        "T se puede comparar con otro T". Parece perfecto... hasta que lo
        usas con LocalDate:

            List<LocalDate> dates = ...;
            LocalDate latest = maxOf(dates);
            // ERROR: method maxOf cannot be applied to given types;
            //   reason: inference variable T has incompatible equality
            //           constraints ChronoLocalDate,LocalDate

        Por qué: LocalDate NO implementa Comparable<LocalDate>. Implementa
        Comparable<ChronoLocalDate> (su interface PADRE), porque se puede
        comparar con cualquier fecha de cualquier calendario. Así que
        "LocalDate es Comparable con un PADRE de LocalDate", no consigo
        mismo, y el límite simple no se cumple.

        La solución es aplicar PECS al Comparable (que "consume" un T para
        compararlo):
            <T extends Comparable<? super T>>
        "T se puede comparar con T o con algún PADRE de T". Esa es la firma
        de Collections.max, Collections.sort y compañía, y la razón de que
        se vean tan complicadas: hacen que funcionen con casos como este.
    */
    static <T extends Comparable<? super T>> T latest(List<T> values) {
        T max = values.get(0);
        for (T value : values) {
            if (value.compareTo(max) > 0) {
                max = value;
            }
        }
        return max;
    }

    static boolean comparableSuperExample() {
        List<LocalDate> dates = List.of(LocalDate.of(2026, 1, 15), LocalDate.of(2026, 11, 3), LocalDate.of(2025, 12, 31));
        LocalDate last = latest(dates);     // con <T extends Comparable<T>> esta línea no compilaría
        return last.equals(LocalDate.of(2026, 11, 3)) && latest(List.of(3, 9, 4)) == 9;
    }

    /*
        =================================================================
        CAPA 8) BORRADO DE TIPOS: LOS GENÉRICOS DESAPARECEN AL EJECUTAR
        =================================================================

        Los genéricos llegaron en Java 5 (2004), cuando ya existían
        millones de programas y librerías compiladas SIN ellos. Para que
        todo siguiera funcionando junto, Java eligió el BORRADO DE TIPOS
        (type erasure):

        - Los genéricos existen SOLO para el COMPILADOR: revisa los tipos
          y agrega los casts necesarios.
        - Al generar el bytecode, BORRA los parámetros de tipo: List<String>
          y List<Integer> se vuelven simplemente List; un T sin límite se
          vuelve Object; un <T extends Number> se vuelve Number.
        - Al EJECUTAR, la JVM no sabe que una lista era "de String".

        (C# eligió lo contrario, genéricos que existen al ejecutar, a cambio
        de romper compatibilidad. Ninguna opción es perfecta.)

        CONSECUENCIAS, todas porque al ejecutar "no se sabe qué es T":
        1) new T() y new T[] no compilan (GenericsBasics, CAPA 10).
        2) No se puede preguntar por el parámetro con instanceof:
               obj instanceof List<String>
               // ERROR: Object cannot be safely cast to List<String>
           Desde un Object solo se puede preguntar instanceof List<?>.
           (List<String> solo compila cuando el tipo de la variable ya
           garantiza el <String>, por ejemplo una Collection<String>: ahí no
           pregunta por el parámetro, solo si es List.)
        3) No se puede sobrecargar solo por el parámetro de tipo:
               void process(List<String> a) { }
               void process(List<Integer> a) { }
               // ERROR: name clash: process(List<Integer>) and process(List<String>) have the same erasure
           Después del borrado, las dos son process(List).
        4) Los campos static son UNO para todas las versiones: no existe
           un static distinto para Box<String> y Box<Integer>.
        5) Las clases de excepciones no pueden ser genéricas.
    */
    static boolean erasureExample() {
        List<String> strings = new ArrayList<>();
        List<Integer> integers = new ArrayList<>();

        // Al ejecutar son exactamente la misma clase: el <String> y el <Integer> ya no existen.
        boolean sameClassAtRuntime = strings.getClass() == integers.getClass();

        Object unknown = strings;
        boolean onlyWildcardCheck = unknown instanceof List<?>;   // lo único que se puede preguntar

        return sameClassAtRuntime && onlyWildcardCheck;
    }

    /*
        =================================================================
        CAPA 9) TRABAJAR CON EL BORRADO: SUPPLIER Y CLASS<T>
        =================================================================

        Si un método genérico necesita CREAR objetos de tipo T, como no
        puede hacer new T(), alguien tiene que decirle CÓMO:

        1) PASAR UNA FÁBRICA (Supplier<T>, la forma moderna y preferida):
               static <T> List<T> createMany(Supplier<T> factory, int count)
               createMany(ArrayList::new, 3)    // "así se crea uno"
           Supplier es una interface funcional (Interfaces, CAPA 8) con un
           método get(); ArrayList::new es una referencia a su constructor
           (v7_functional/part1_lambdas/MethodReferences).

        2) PASAR EL Class<T> ("type token"): un objeto que SÍ existe al
           ejecutar y dice qué tipo es. Permite revisar y convertir con
           seguridad (isInstance, cast) y crear arreglos de T con
           java.lang.reflect.Array (v12_jvm/part2_classes/ReflectionAndAnnotations).
           Es lo que hacen muchas librerías: objectMapper.readValue(json, Product.class).

        3) Para pasar una lista a arreglo: list.toArray(String[]::new) o
           list.toArray(new String[0]), que le dicen a la lista qué tipo de
           arreglo crear.
    */
    static <T> List<T> createMany(Supplier<T> factory, int count) {
        List<T> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            result.add(factory.get());          // la fábrica sabe hacer lo que el genérico no puede: new T()
        }
        return result;
    }

    static <T> List<T> onlyOfType(List<?> mixed, Class<T> type) {
        List<T> result = new ArrayList<>();
        for (Object item : mixed) {
            if (type.isInstance(item)) {        // revisar el tipo al ejecutar, con el Class<T>
                result.add(type.cast(item));    // convertir con seguridad, sin advertencias
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked") // Array.newInstance devuelve Object: el cast a T[] es seguro porque usa el mismo type
    static <T> T[] newArray(Class<T> type, int length) {
        return (T[]) Array.newInstance(type, length);
    }

    static boolean erasureWorkaroundsExample() {
        List<StringBuilder> builders = createMany(StringBuilder::new, 3);
        boolean independentObjects = builders.size() == 3 && builders.get(0) != builders.get(1);

        List<String> onlyStrings = onlyOfType(List.of("a", 1, "b", 2.5), String.class);

        String[] array = newArray(String.class, 2);
        String[] fromList = List.of("x", "y").toArray(String[]::new);

        return independentObjects && onlyStrings.equals(List.of("a", "b"))
                && array.length == 2 && array.getClass() == String[].class
                && Arrays.equals(fromList, new String[] {"x", "y"});
    }

    /*
        =================================================================
        CAPA 10) VARARGS GENÉRICOS Y @SAFEVARARGS
        =================================================================

        Un método con varargs genéricos (T... items) tiene un problema
        técnico: los varargs son un ARREGLO (Methods, CAPA 7), y no existen
        arreglos de T (CAPA 8). El arreglo lo crea QUIEN LLAMA, con el tipo
        que se dedujo: listOf("a", "b") recibe un String[]. Pero si quien
        llama es a su vez genérico, para él T es solo Object, y el arreglo
        es un Object[] que se usa como T[]: ahí se puede meter un tipo
        equivocado sin aviso ("contaminación del heap", heap pollution).
        Por eso el compilador advierte:
            warning: [unchecked] Possible heap pollution from parameterized vararg type T

        Si tu método solo LEE los elementos del arreglo y no lo guarda ni
        lo devuelve tal cual, es seguro, y lo declaras con @SafeVarargs
        (solo en métodos static, final o private, que no se pueden
        sobrescribir). List.of, Arrays.asList y Set.of lo usan.
    */
    @SafeVarargs
    static <T> List<T> listOf(T... items) {
        List<T> result = new ArrayList<>();
        for (T item : items) {                  // solo lee el arreglo: seguro
            result.add(item);
        }
        return result;
    }

    static boolean safeVarargsExample() {
        return listOf("a", "b", "c").equals(List.of("a", "b", "c")) && listOf().isEmpty();
    }

    /*
        =================================================================
        CAPA 11) ERRORES COMUNES
        =================================================================

        1) Creer que List<Integer> es una List<Number> (CAPA 2).

        2) Usar List<Object> como parámetro cuando se quería "cualquier
           lista": usa List<?> (CAPA 3).

        3) Intentar agregar a una List<? extends T> (CAPA 4).

        4) Parámetros demasiado estrictos (List<T> donde bastaba
           List<? extends T>) que obligan a quien llama a convertir sus
           listas (CAPA 6).

        5) Escribir <T extends Comparable<T>> en métodos que deberían
           funcionar con LocalDate y similares (CAPA 7).

        6) Esperar que el tipo genérico exista al ejecutar: instanceof
           List<String>, new T(), sobrecargas que solo difieren en el
           parámetro (CAPA 8).

        7) Resolver la creación de T con casts inseguros en vez de pasar un
           Supplier o un Class<T> (CAPA 9).

        8) Poner @SafeVarargs en un método que guarda o devuelve el arreglo
           de varargs (CAPA 10).

        9) Abusar de los comodines en los TIPOS DE RETORNO: obligan a quien
           recibe el resultado a lidiar con ellos. Los comodines van en los
           parámetros.
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - <T extends X>: T debe ser un X, y puedes usar sus métodos. Varios
          límites con &.
        - Los genéricos son invariantes: List<Integer> no es List<Number>.
          Así se evita el ArrayStoreException de los arreglos.
        - List<?>: de algo; se lee como Object, no se agrega.
        - ? extends T: productor, se LEE como T.
        - ? super T: consumidor, se le AGREGAN T.
        - PECS: Producer Extends, Consumer Super. Comodines en parámetros,
          no en retornos.
        - Comparable<? super T> hace que funcione con tipos como LocalDate.
        - Borrado de tipos: los genéricos solo existen al compilar (por
          compatibilidad con código anterior a Java 5).
        - Para crear T: Supplier<T> o Class<T>.
        - Varargs genéricos: @SafeVarargs si solo se leen.
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
