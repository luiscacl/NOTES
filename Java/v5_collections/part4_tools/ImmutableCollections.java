package v5_collections.part4_tools;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/*
    =====================================================================
    COLECCIONES INMUTABLES: OF, COPYOF Y VISTAS — DE CERO A NIVEL TÉCNICO
    =====================================================================

    Ruta de collections/: guía 9 de 10  (part4_tools)
    Anterior:  part4_tools/Sorting
    Siguiente: part4_tools/CollectionsAndArraysUtilities

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.

    Índice:
        CAPA 1)  Qué es una colección inmutable y por qué importa
        CAPA 2)  Cuatro niveles: modificable, tamaño fijo, vista, inmutable
        CAPA 3)  List.of, Set.of, Map.of: las reglas
        CAPA 4)  La trampa: ni siquiera puedes PREGUNTAR por null
        CAPA 5)  copyOf: congelar una copia
        CAPA 6)  Vistas unmodifiable: una ventana de solo lectura
        CAPA 7)  ¿Copia o vista? Exponer una colección desde una clase
        CAPA 8)  Inmutable por fuera, mutable por dentro
        CAPA 9)  Streams: toList, Collectors.toList y toUnmodifiableList
        CAPA 10) Constantes: por qué no un arreglo public static final
        CAPA 11) Construir mutable y congelar al final
        CAPA 12) Las fábricas antiguas de Collections
        CAPA 13) Cómo elegir
        CAPA 14) Errores comunes
        RESUMEN FINAL
*/
public class ImmutableCollections {

    public static void main(String[] args) {
        check(whyImmutableExample(), "CAPA 1");
        check(levelsExample(), "CAPA 2");
        check(factoryRulesExample(), "CAPA 3");
        check(nullQueryExample(), "CAPA 4");
        check(copyOfExample(), "CAPA 5");
        check(viewExample(), "CAPA 6");
        check(exposingExample(), "CAPA 7");
        check(shallowExample(), "CAPA 8");
        check(streamsExample(), "CAPA 9");
        check(constantsExample(), "CAPA 10");
        check(buildThenFreezeExample(), "CAPA 11");
        check(legacyFactoriesExample(), "CAPA 12");
        System.out.println("ImmutableCollections: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) QUÉ ES UNA COLECCIÓN INMUTABLE Y POR QUÉ IMPORTA
        =================================================================

        Una colección INMUTABLE no se puede cambiar después de crearse: no
        se agrega, no se quita, no se reemplaza nada. Cualquier intento
        lanza UnsupportedOperationException.

        ¿Para qué querrías una colección que no puedes cambiar? Porque
        "nadie puede cambiarla" es una GARANTÍA muy valiosa:

        1) COMPARTIR SIN MIEDO. Puedes pasarla a cualquier método o
           devolverla desde cualquier getter: nadie puede alterar tus
           datos (v2_oop/part2_pillars/Encapsulation, CAPA 8). Sin esa
           garantía tendrías que hacer copias defensivas a cada paso.
        2) RAZONAR FÁCIL. Si una lista no cambia, lo que vale ahora vale
           siempre. No hay que buscar "quién la modificó" por todo el
           código.
        3) HILOS. Varios hilos pueden leer una colección inmutable al mismo
           tiempo sin candados: no hay escrituras que coordinar
           (v13_concurrency/).
        4) CLAVES SEGURAS. Una lista inmutable puede ser clave de un mapa o
           elemento de un set sin el riesgo de "perderse" al cambiar
           (Maps, CAPA 6).
        5) ERRORES QUE SE VEN. Si alguien intenta modificarla, falla EN ESE
           momento con una excepción, en lugar de corromper datos en
           silencio.

        La regla profesional: haz inmutable todo lo que no NECESITE
        cambiar. Es la misma idea que los campos final y los records
        (Encapsulation, CAPA 7; v2_oop/part4_advanced_types/Records).
    */
    static boolean whyImmutableExample() {
        List<String> roles = List.of("ADMIN", "EDITOR");

        boolean addFails;
        try {
            roles.add("ROOT");                      // alguien intenta escalar permisos
            addFails = false;
        } catch (UnsupportedOperationException e) {
            addFails = true;                        // falla en el momento, no en silencio
        }

        Map<List<String>, String> byRoles = Map.of(roles, "grupo-a");   // segura como clave: nunca cambia

        return addFails && roles.size() == 2 && byRoles.get(List.of("ADMIN", "EDITOR")).equals("grupo-a");
    }

    /*
        =================================================================
        CAPA 2) CUATRO NIVELES: MODIFICABLE, TAMAÑO FIJO, VISTA, INMUTABLE
        =================================================================

        "No se puede modificar" no es una sola cosa. En Java hay cuatro
        niveles, y confundirlos causa errores reales:

        | Nivel              | Ejemplo                          | add/remove | set    | ¿Cambia si cambia el original? |
        |--------------------|----------------------------------|------------|--------|--------------------------------|
        | Modificable        | new ArrayList<>(...)             | sí         | sí     | (es el original)               |
        | Tamaño fijo        | Arrays.asList(arreglo)           | NO         | sí     | sí: comparte el arreglo        |
        | Vista no modificable| Collections.unmodifiableList(l) | NO         | NO     | SÍ: es una ventana al original |
        | Inmutable          | List.of(...), List.copyOf(l)     | NO         | NO     | no: no hay original            |

        - TAMAÑO FIJO: Arrays.asList envuelve el arreglo; no puede crecer
          pero sí cambiar valores (v1_fundamentals/part4_data_and_input/
          Arrays, CAPA 7).
        - VISTA NO MODIFICABLE ("unmodifiable"): TÚ no puedes cambiarla a
          través de ella, pero quien tenga el original sí, y tú verás los
          cambios (CAPA 6).
        - INMUTABLE: nadie puede cambiarla, por ningún camino.

        Ojo con el vocabulario: la documentación de Java llama
        "unmodifiable" tanto a las vistas como a List.of. La diferencia
        que importa es la última columna: ¿hay un original vivo detrás?
    */
    static boolean levelsExample() {
        String[] backing = {"a", "b"};
        List<String> fixedSize = Arrays.asList(backing);
        fixedSize.set(0, "z");                          // set sí...
        boolean fixedSizeSharesArray = backing[0].equals("z");
        boolean fixedSizeCannotGrow;
        try {
            fixedSize.add("c");                         // ...add no
            fixedSizeCannotGrow = false;
        } catch (UnsupportedOperationException e) {
            fixedSizeCannotGrow = true;
        }

        List<String> original = new ArrayList<>(List.of("a", "b"));
        List<String> view = Collections.unmodifiableList(original);
        List<String> frozen = List.copyOf(original);
        original.add("c");                              // cambia el original

        return fixedSizeSharesArray && fixedSizeCannotGrow
                && view.size() == 3                     // la vista lo ve
                && frozen.size() == 2;                  // la copia inmutable no
    }

    /*
        =================================================================
        CAPA 3) LIST.OF, SET.OF, MAP.OF: LAS REGLAS
        =================================================================

        Las fábricas "of" (Java 9+) crean colecciones inmutables con los
        elementos que escribes. Sus reglas, todas pensadas para atrapar
        errores pronto:

        1) NINGUNA operación que modifique está permitida: add, remove,
           set, clear, sort, removeIf, replaceAll, y el remove() de su
           iterador. Y lanzan la excepción SIEMPRE, aunque la operación no
           fuera a cambiar nada (clear() de una lista vacía, removeIf que
           no quitaría nada, sort de un solo elemento). Así el error
           aparece en tus pruebas, no solo el día que los datos cambien.
        2) NO aceptan null, ni como elemento, ni como clave, ni como
           valor: NullPointerException al crearlas.
        3) Set.of y Map.of rechazan duplicados al crearse
           (Sets, CAPA 8; Maps, CAPA 13).
        4) El orden de Set.of y Map.of cambia entre ejecuciones; List.of
           conserva el orden en que escribiste.

        Por dentro son clases especiales y compactas (de java.util.
        ImmutableCollections): una para 1 o 2 elementos, otra para más. No
        guardan capacidad sobrante como ArrayList (Lists, CAPA 2), así que
        ocupan menos memoria.

        Y son colecciones normales para todo lo demás: equals compara por
        contenido, así que List.of(1, 2) es igual a una ArrayList con 1 y 2.
    */
    static boolean factoryRulesExample() {
        List<Integer> numbers = List.of(1, 2, 3);

        boolean removeIfAlwaysFails;
        try {
            numbers.removeIf(n -> n > 100);             // no quitaría nada, y aun así falla
            removeIfAlwaysFails = false;
        } catch (UnsupportedOperationException e) {
            removeIfAlwaysFails = true;
        }

        boolean clearOnEmptyFails;
        try {
            List.of().clear();
            clearOnEmptyFails = false;
        } catch (UnsupportedOperationException e) {
            clearOnEmptyFails = true;
        }

        boolean rejectsNull;
        try {
            List.of("a", null);
            rejectsNull = false;
        } catch (NullPointerException e) {
            rejectsNull = true;
        }

        return removeIfAlwaysFails && clearOnEmptyFails && rejectsNull
                && numbers.equals(new ArrayList<>(List.of(1, 2, 3)));   // igualdad por contenido
    }

    /*
        =================================================================
        CAPA 4) LA TRAMPA: NI SIQUIERA PUEDES PREGUNTAR POR NULL
        =================================================================

        Una consecuencia sorprendente de "no acepta null": en las
        colecciones de List.of, Set.of y Map.of, PREGUNTAR por null también
        lanza NullPointerException:

            List.of("a").contains(null)      // NullPointerException
            List.of("a").indexOf(null)       // NullPointerException
            Set.of("a").contains(null)       // NullPointerException
            Map.of("a", 1).containsKey(null) // NullPointerException

        En una ArrayList o en una vista unmodifiable, contains(null)
        simplemente devuelve false.

        Por qué Java lo hizo así: como null nunca puede estar dentro, un
        contains(null) casi seguro es un error (una variable que no
        esperabas que fuera null), y prefiere avisarte.

        Dónde muerde: un método que recibe "cualquier List" y hace
        lista.contains(valor) con un valor que puede ser null. Funciona con
        ArrayList y explota cuando alguien le pasa un List.of. Si el valor
        puede ser null, revísalo ANTES de preguntar.
    */
    static boolean isAllowed(List<String> allowed, String value) {
        return value != null && allowed.contains(value);    // revisar null primero: sirve con cualquier List
    }

    static boolean nullQueryExample() {
        List<String> immutable = List.of("MX", "US");
        List<String> mutable = new ArrayList<>(immutable);

        boolean immutableThrows;
        try {
            immutable.contains(null);
            immutableThrows = false;
        } catch (NullPointerException e) {
            immutableThrows = true;
        }

        return immutableThrows
                && !mutable.contains(null)                  // ArrayList: solo false
                && !isAllowed(immutable, null) && isAllowed(immutable, "MX");
    }

    /*
        =================================================================
        CAPA 5) COPYOF: CONGELAR UNA COPIA
        =================================================================

        List.copyOf(coleccion), Set.copyOf y Map.copyOf (Java 10+) crean
        una copia INMUTABLE de algo que ya existe. Es la herramienta
        principal para copias defensivas: lo que recibes de fuera, lo
        congelas (Records, CAPA 6; Encapsulation, CAPA 8).

        Sus reglas:
        - Mismas reglas que "of": no acepta elementos null.
        - Set.copyOf SÍ acepta duplicados en la entrada (se queda con uno),
          a diferencia de Set.of (Sets, CAPA 8). (Un Map normal no puede
          traer claves repetidas, así que Map.copyOf no tiene ese caso.)
        - Si lo que le pasas YA es una colección inmutable de "of"/"copyOf",
          NO copia: devuelve el mismo objeto. Copiar algo que nadie puede
          cambiar sería desperdicio. Por eso puedes usar copyOf en todos
          tus constructores sin preocuparte del costo cuando los datos ya
          vienen congelados.
        - Una vista unmodifiable NO cuenta como inmutable (tiene un
          original vivo detrás), así que esa sí se copia.
    */
    static boolean copyOfExample() {
        List<String> frozen = List.of("a", "b");
        boolean noCopyWhenAlreadyImmutable = List.copyOf(frozen) == frozen;   // el mismo objeto

        List<String> view = Collections.unmodifiableList(new ArrayList<>(frozen));
        boolean viewIsCopied = List.copyOf(view) != view;                     // una vista sí se copia

        boolean rejectsNullElements;
        try {
            List.copyOf(Arrays.asList("a", null));
            rejectsNullElements = false;
        } catch (NullPointerException e) {
            rejectsNullElements = true;
        }

        return noCopyWhenAlreadyImmutable && viewIsCopied && rejectsNullElements
                && Set.copyOf(List.of("x", "x", "y")).size() == 2;
    }

    /*
        =================================================================
        CAPA 6) VISTAS UNMODIFIABLE: UNA VENTANA DE SOLO LECTURA
        =================================================================

        Collections.unmodifiableList(lista) (y unmodifiableSet,
        unmodifiableMap...) NO copia nada. Crea un objeto pequeño que
        envuelve la lista original y:
        - para leer (get, size, contains, recorrer), le pregunta al
          original;
        - para modificar (add, set, remove, el remove de su iterador),
          lanza UnsupportedOperationException.

        Por eso:
        - Crearla es O(1), sin importar el tamaño de la lista.
        - Está VIVA: si el dueño cambia el original, la vista muestra el
          cambio. Es una ventana, no una foto.
        - Proteger una vista que ya está protegida no crea otra capa: Java
          devuelve la misma.

        Es exactamente lo que quieres para que alguien OBSERVE datos que
        tú sigues cambiando: el carrito que se actualiza en pantalla, el
        log de movimientos de un almacén.
    */
    static boolean viewExample() {
        List<String> log = new ArrayList<>();
        List<String> readOnly = Collections.unmodifiableList(log);

        log.add("entrada: 5 teclados");
        log.add("salida: 2 teclados");

        boolean iteratorRemoveFails;
        try {
            var it = readOnly.iterator();
            it.next();
            it.remove();                                    // tampoco por el iterador
            iteratorRemoveFails = false;
        } catch (UnsupportedOperationException e) {
            iteratorRemoveFails = true;
        }

        return readOnly.size() == 2 && readOnly.get(1).equals("salida: 2 teclados")   // ve los cambios del dueño
                && iteratorRemoveFails
                && Collections.unmodifiableList(readOnly) == readOnly;                 // no se envuelve dos veces
    }

    /*
        =================================================================
        CAPA 7) ¿COPIA O VISTA? EXPONER UNA COLECCIÓN DESDE UNA CLASE
        =================================================================

        Una clase guarda una lista privada y necesita un getter. Tres
        opciones (Encapsulation, CAPA 8, las comparaba en una tabla):

        1) return items;                         // MAL: entregas la llave
           Quien la reciba puede agregar, borrar o vaciar tu estado
           interno y saltarse tus validaciones.

        2) return Collections.unmodifiableList(items);   // vista
           O(1). Quien la recibe ve los cambios futuros. Pero si la guarda
           y la recorre mientras tú agregas, puede recibir
           ConcurrentModificationException (Iterators, CAPA 5).

        3) return List.copyOf(items);            // foto
           O(n) en cada llamada. Quien la recibe tiene un estado fijo,
           independiente de lo que pase después.

        Cómo decidir:
        - ¿El que llama necesita ver la lista "en vivo"? Vista.
        - ¿Necesita un resultado estable (para guardarlo, mandarlo a otro
          hilo, compararlo después)? Copia.
        - Si la lista interna ya es inmutable (la guardaste con copyOf en
          el constructor), devuélvela tal cual: no hay nada que proteger,
          y es lo más simple.
    */
    static final class Course {
        private final List<String> students = new ArrayList<>();

        void enroll(String student) {
            if (students.contains(student)) {
                throw new IllegalArgumentException("Ya inscrito: " + student);   // la regla que una fuga se saltaría
            }
            students.add(student);
        }

        List<String> liveStudents() {
            return Collections.unmodifiableList(students);
        }

        List<String> studentsSnapshot() {
            return List.copyOf(students);
        }
    }

    static boolean exposingExample() {
        Course course = new Course();
        course.enroll("Ana");
        List<String> live = course.liveStudents();
        List<String> snapshot = course.studentsSnapshot();
        course.enroll("Luis");

        boolean cannotBypassRules;
        try {
            live.add("Ana");                                // intentar duplicar por fuera
            cannotBypassRules = false;
        } catch (UnsupportedOperationException e) {
            cannotBypassRules = true;
        }

        return live.equals(List.of("Ana", "Luis"))        // la vista sigue al curso
                && snapshot.equals(List.of("Ana"))         // la foto se quedó en su momento
                && cannotBypassRules;
    }

    /*
        =================================================================
        CAPA 8) INMUTABLE POR FUERA, MUTABLE POR DENTRO
        =================================================================

        Una colección inmutable impide cambiar QUÉ elementos tiene. No
        impide cambiar los elementos MISMOS si son objetos mutables. Es
        inmutabilidad SUPERFICIAL (Records, CAPA 6; v1_fundamentals/
        part3_text_and_references/ReferencesAndNull: copias superficiales).

            List<List<String>> groups = List.of(new ArrayList<>(...));
            groups.add(...)            // UnsupportedOperationException
            groups.get(0).add("x")     // funciona: la lista de dentro es mutable

        Para inmutabilidad completa, los elementos también deben ser
        inmutables: String, números, enums, records con campos inmutables,
        o colecciones inmutables dentro de colecciones inmutables.
    */
    static boolean shallowExample() {
        List<List<String>> groups = List.of(new ArrayList<>(List.of("Ana")));
        groups.get(0).add("Luis");                          // la de fuera no cambió; la de dentro sí
        boolean innerChanged = groups.get(0).equals(List.of("Ana", "Luis"));

        List<List<String>> deep = List.of(List.of("Ana"));
        boolean deepIsSafe;
        try {
            deep.get(0).add("Luis");
            deepIsSafe = false;
        } catch (UnsupportedOperationException e) {
            deepIsSafe = true;                              // inmutable en todos los niveles
        }

        return innerChanged && deepIsSafe;
    }

    /*
        =================================================================
        CAPA 9) STREAMS: TOLIST, COLLECTORS.TOLIST Y TOUNMODIFIABLELIST
        =================================================================

        Los streams (v7_functional/part2_streams/StreamsBasics) tienen tres formas de terminar en
        una lista, y NO son iguales:

        | Forma                                  | ¿Modificable?              | ¿Acepta null? |
        |----------------------------------------|----------------------------|---------------|
        | stream.toList()            (Java 16+)  | NO                         | sí            |
        | collect(Collectors.toList())           | hoy sí (ArrayList), pero   | sí            |
        |                                        | NO está garantizado        |               |
        | collect(Collectors.toUnmodifiableList())| NO                        | NO            |

        - stream.toList() es la forma moderna y la que normalmente
          quieres: corta y no modificable.
        - Collectors.toList() devuelve una ArrayList en la práctica, pero
          su documentación no promete el tipo ni que se pueda modificar.
          Si de verdad vas a modificar el resultado, dilo explícitamente:
          collect(Collectors.toCollection(ArrayList::new)).
        - Un error típico al migrar código: cambiar collect(toList()) por
          toList() y descubrir que más adelante alguien hacía add sobre el
          resultado.
    */
    static boolean streamsExample() {
        List<Integer> modern = Stream.of(3, 1, 2).sorted().toList();

        boolean modernIsUnmodifiable;
        try {
            modern.add(4);
            modernIsUnmodifiable = false;
        } catch (UnsupportedOperationException e) {
            modernIsUnmodifiable = true;
        }

        boolean modernAcceptsNull = Stream.of("a", null).toList().size() == 2;

        List<Integer> editable = Stream.of(1, 2).collect(Collectors.toCollection(ArrayList::new));
        editable.add(3);                                    // pedido explícitamente: modificable

        boolean strictRejectsNull;
        try {
            Stream.of("a", null).collect(Collectors.toUnmodifiableList());
            strictRejectsNull = false;
        } catch (NullPointerException e) {
            strictRejectsNull = true;
        }

        return modernIsUnmodifiable && modernAcceptsNull && editable.size() == 3 && strictRejectsNull;
    }

    /*
        =================================================================
        CAPA 10) CONSTANTES: POR QUÉ NO UN ARREGLO PUBLIC STATIC FINAL
        =================================================================

        final protege la VARIABLE, no el contenido (Encapsulation, CAPA 7;
        v2_oop/part1_basics/Static, constantes). Un arreglo constante:
            public static final String[] COUNTRIES = {"MX", "US"};
        no deja reasignar COUNTRIES, pero CUALQUIER clase puede hacer
        COUNTRIES[0] = "XX" y cambiar el dato para todo el programa. Es
        un hueco de seguridad conocido; los analizadores de código lo
        marcan.

        Los arreglos no tienen versión inmutable. La solución es una lista
        inmutable:
            public static final List<String> COUNTRIES = List.of("MX", "US");
        Esa sí es una constante de verdad.
    */
    static final String[] COUNTRIES_ARRAY = {"MX", "US"};          // ERROR de diseño: "constante" que cambia
    static final List<String> COUNTRIES = List.of("MX", "US");     // constante de verdad

    static boolean constantsExample() {
        String[] alias = COUNTRIES_ARRAY;
        alias[0] = "XX";                                    // compila y cambia la "constante"
        boolean arrayWasChanged = COUNTRIES_ARRAY[0].equals("XX");
        COUNTRIES_ARRAY[0] = "MX";                          // dejarla como estaba para el resto de la guía

        boolean listIsProtected;
        try {
            COUNTRIES.set(0, "XX");
            listIsProtected = false;
        } catch (UnsupportedOperationException e) {
            listIsProtected = true;
        }

        return arrayWasChanged && listIsProtected && COUNTRIES.get(0).equals("MX");
    }

    /*
        =================================================================
        CAPA 11) CONSTRUIR MUTABLE Y CONGELAR AL FINAL
        =================================================================

        Muchas veces no conoces los elementos de antemano: los calculas,
        los filtras o los lees de un archivo. El patrón es:
        1) construir con una colección MUTABLE local (ArrayList, HashMap);
        2) al terminar, congelarla con copyOf y devolver eso.

        La colección mutable nunca sale del método, así que nadie más la
        ve cambiar; lo que sale ya está congelado. Es la misma idea del
        patrón Builder (v2_oop/part3_interfaces_and_relationships/
        NestedClasses): se arma con libertad y se entrega terminado.

        (Con streams, stream...toList() hace lo mismo en una expresión.)
    */
    static Map<String, List<String>> groupByInitial(List<String> names) {
        Map<String, List<String>> building = new TreeMap<>();
        for (String name : names) {
            building.computeIfAbsent(name.substring(0, 1), initial -> new ArrayList<>()).add(name);   // Maps, CAPA 9
        }

        Map<String, List<String>> frozen = new TreeMap<>();
        building.forEach((initial, group) -> frozen.put(initial, List.copyOf(group)));   // congelar cada lista...
        return Collections.unmodifiableMap(frozen);                                      // ...y el mapa (conserva el orden)
    }

    static boolean buildThenFreezeExample() {
        Map<String, List<String>> groups = groupByInitial(List.of("Ana", "Luis", "Alma", "Leo"));

        boolean innerFrozen;
        try {
            groups.get("A").add("Andrés");
            innerFrozen = false;
        } catch (UnsupportedOperationException e) {
            innerFrozen = true;
        }

        return groups.equals(Map.of("A", List.of("Ana", "Alma"), "L", List.of("Luis", "Leo")))
                && new ArrayList<>(groups.keySet()).equals(List.of("A", "L"))
                && innerFrozen;
    }

    /*
        Nota: aquí el mapa de afuera es una VISTA de "frozen", pero nadie
        más tiene la referencia a frozen (no sale del método), así que en
        la práctica es inmutable. Se usó así para conservar el orden del
        TreeMap; Map.copyOf no garantiza orden (CAPA 3).
    */

    /*
        =================================================================
        CAPA 12) LAS FÁBRICAS ANTIGUAS DE COLLECTIONS
        =================================================================

        Antes de Java 9 se usaban estas, y las verás en código existente:

            Collections.emptyList(), emptySet(), emptyMap()   vacías e inmutables
            Collections.singletonList(x), singleton(x)        de un elemento
            Collections.nCopies(n, x)                         n veces el mismo elemento
            Arrays.asList(a, b, c)                            tamaño fijo (CAPA 2)

        Diferencias con List.of:
        - SÍ aceptan null (singletonList(null) funciona) y SÍ responden
          contains(null) con false.
        - Tienen nombres distintos por cantidad; List.of cubre todo.

        Para código nuevo: List.of() en lugar de emptyList(), List.of(x)
        en lugar de singletonList(x). nCopies sigue siendo útil (por
        ejemplo, una lista inicial de n ceros: new ArrayList<>(Collections.
        nCopies(n, 0))). Las utilidades de Collections se ven completas en
        la siguiente guía.
    */
    static boolean legacyFactoriesExample() {
        List<String> empty = Collections.emptyList();
        List<String> single = Collections.singletonList(null);         // acepta null
        List<Integer> zeros = new ArrayList<>(Collections.nCopies(3, 0));
        zeros.set(1, 5);

        return empty.isEmpty() && !empty.contains(null)                // contains(null) sin excepción
                && single.size() == 1
                && zeros.equals(List.of(0, 5, 0))
                && empty.equals(List.of());
    }

    /*
        =================================================================
        CAPA 13) CÓMO ELEGIR
        =================================================================

        | Necesitas                                         | Usa                                    |
        |---------------------------------------------------|----------------------------------------|
        | Valores fijos escritos en el código               | List.of / Set.of / Map.of              |
        | Congelar algo que recibiste (copia defensiva)     | List.copyOf / Set.copyOf / Map.copyOf  |
        | Dejar ver, sin dejar tocar, algo que sigue cambiando | Collections.unmodifiableList        |
        | Una foto estable para quien llama                 | List.copyOf                            |
        | Terminar un stream                                | stream.toList()                        |
        | Una constante pública                             | List.of, nunca un arreglo              |
        | Una lista que vas a modificar                     | new ArrayList<>(...)                   |
        | Datos con null                                    | ArrayList (o mejor, evita los null)    |
    */

    /*
        =================================================================
        CAPA 14) ERRORES COMUNES
        =================================================================

        1) Hacer add, sort o removeIf sobre una lista de List.of,
           stream.toList() o Arrays.asList (CAPAS 2, 3 y 9).

        2) Creer que una vista unmodifiable no cambia: cambia con el
           original (CAPAS 2 y 6).

        3) contains(null) sobre una colección de List.of o Set.of (CAPA 4).

        4) Meter null en List.of o copyOf (CAPAS 3 y 5).

        5) Devolver la lista interna tal cual desde un getter (CAPA 7).

        6) Creer que una lista inmutable protege a sus elementos (CAPA 8).

        7) Cambiar collect(Collectors.toList()) por toList() sin revisar si
           alguien modificaba el resultado (CAPA 9).

        8) Constantes public static final con arreglos (CAPA 10).

        9) Copiar "por si acaso" algo que ya es inmutable: copyOf ya lo
           evita; no hagas new ArrayList<>(...) de algo que solo vas a
           leer (CAPA 5).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Inmutable = nadie puede cambiarla; se comparte sin copias, sin
          candados y sin sorpresas.
        - Cuatro niveles: modificable, tamaño fijo (Arrays.asList), vista
          (unmodifiable: ventana viva al original), inmutable (of/copyOf).
        - of: sin null (ni para preguntar con contains), sin duplicados en
          Set/Map, cualquier modificación lanza excepción siempre.
        - copyOf: copia inmutable; no copia si ya era inmutable.
        - Getter: vista si debe verse en vivo, copia si debe ser estable,
          la propia lista si ya es inmutable. Nunca la lista mutable.
        - Inmutabilidad superficial: los elementos también deben serlo.
        - stream.toList(): no modificable. Collectors.toList(): no
          garantizado. Para modificar: toCollection(ArrayList::new).
        - Constantes con List.of, no con arreglos.
        - Construye mutable dentro del método, congela al devolver.
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
