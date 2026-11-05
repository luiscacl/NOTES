package v1_fundamentals.part3_text_and_references;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
    =====================================================================
    WRAPPERS (CLASES ENVOLTORIO) — DE CERO A NIVEL TÉCNICO
    =====================================================================

    Ruta de v1_fundamentals/: guía 11 de 14  (part3_text_and_references)
    Anterior:  part3_text_and_references/ReferencesAndNull
    Siguiente: part4_data_and_input/Arrays

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.

    Índice:
        CAPA 1)  Qué es un wrapper y por qué existe
        CAPA 2)  Autoboxing y unboxing: la conversión automática
        CAPA 3)  La caché de Integer y la trampa de ==
        CAPA 4)  Unboxing de null: NullPointerException escondida
        CAPA 5)  Wrappers de distinto tipo nunca son iguales
        CAPA 6)  List<Integer>.remove: índice o valor
        CAPA 7)  El costo de los wrappers
        CAPA 8)  Los métodos útiles de los wrappers
        CAPA 9)  Cuándo usar primitivo y cuándo wrapper
        CAPA 10) Errores comunes
        RESUMEN FINAL
*/
public class Wrappers {

    public static void main(String[] args) {
        check(whyWrappersExample(), "CAPA 1");
        check(autoboxingExample(), "CAPA 2");
        check(integerCacheExample(), "CAPA 3");
        check(nullUnboxingExample(), "CAPA 4");
        check(crossTypeEqualityExample(), "CAPA 5");
        check(listRemoveExample(), "CAPA 6");
        check(costExample(), "CAPA 7");
        check(utilityMethodsExample(), "CAPA 8");
        System.out.println("Wrappers: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) QUÉ ES UN WRAPPER Y POR QUÉ EXISTE
        =================================================================

        Un WRAPPER ("envoltorio") es una clase que ENVUELVE un valor
        primitivo dentro de un objeto. Cada primitivo tiene el suyo:

            byte -> Byte        short -> Short      int -> Integer
            long -> Long        float -> Float      double -> Double
            char -> Character   boolean -> Boolean
        (Fíjate: todos son el nombre con mayúscula, salvo Integer y
        Character.)

        ¿Por qué hace falta "envolver" un número? Porque hay partes de
        Java que SOLO trabajan con objetos:

        1) COLECCIONES Y GENÉRICOS: List<int> NO compila; hay que escribir
           List<Integer>. Los genéricos se diseñaron para trabajar con
           cualquier OBJETO (v4_generics/), y un primitivo no es un objeto.
               List<int> numbers;   // ERROR: unexpected type
                                    //   required: reference
                                    //   found:    int
        2) AUSENCIA DE VALOR: un int siempre tiene un número; no puede
           decir "no hay dato". Un Integer puede ser null.
        3) UTILIDADES: las clases wrapper agrupan métodos y constantes
           relacionados con su tipo: Integer.parseInt, Integer.MAX_VALUE,
           Character.isDigit... (CAPA 8).

        (Como decía tu antigua TypingGuide: "muchas APIs de Java trabajan con
        objetos; por eso una lista usa Integer y no int".)
    */
    static boolean whyWrappersExample() {
        List<Integer> scores = new ArrayList<>();   // List<int> no compilaría
        scores.add(90);
        scores.add(85);

        Integer unknownAge = null;                  // un int no puede expresar "no se sabe"

        return scores.size() == 2 && unknownAge == null && Integer.MAX_VALUE == 2_147_483_647;
    }

    /*
        =================================================================
        CAPA 2) AUTOBOXING Y UNBOXING: LA CONVERSIÓN AUTOMÁTICA
        =================================================================

        Desde Java 5, el compilador convierte entre primitivo y wrapper
        automáticamente:
        - AUTOBOXING: primitivo -> wrapper     Integer boxed = 10;
        - UNBOXING:   wrapper -> primitivo     int raw = boxed;

        No es magia: el compilador ESCRIBE por ti una llamada a un método.
        Si inspeccionas el bytecode con javap -c, ves exactamente esto:
            Integer boxed = 10;    se compila como   Integer.valueOf(10)
            int raw = boxed;       se compila como   boxed.intValue()

        Ocurre en todos lados, muchas veces sin que lo notes:
        - al asignar,
        - al pasar argumentos (scores.add(90) recibe un Integer),
        - al leer de colecciones (int first = scores.get(0)),
        - al operar: boxed + 1 hace unboxing, suma, y si lo guardas en un
          Integer, hace boxing otra vez.

        Por qué importa saber que son llamadas a métodos: explica las
        trampas de las CAPAS 3 y 4. intValue() sobre null lanza
        NullPointerException, y valueOf a veces reutiliza objetos.
    */
    static boolean autoboxingExample() {
        int primitive = 10;
        Integer boxed = primitive;            // autoboxing: Integer.valueOf(10)
        int unboxed = boxed;                  // unboxing: boxed.intValue()

        List<Integer> numbers = new ArrayList<>(List.of(1, 2, 3));
        int sum = 0;
        for (int n : numbers) {               // unboxing en cada vuelta
            sum += n;
        }

        Integer incremented = boxed + 1;      // unboxing, suma, boxing

        return unboxed == 10 && sum == 6 && incremented == 11;
    }

    /*
        =================================================================
        CAPA 3) LA CACHÉ DE INTEGER Y LA TRAMPA DE ==
        =================================================================

        Integer.valueOf no siempre crea un objeto nuevo. Los números del
        -128 al 127 se usan TANTO que Java los crea una sola vez al
        arrancar y los reutiliza (una caché). Byte, Short, Long, Character
        (hasta 127) y Boolean hacen lo mismo.

        Por qué: ahorra muchísimos objetos en programas que usan números
        pequeños constantemente (contadores, índices, códigos).

        La consecuencia es una de las trampas más famosas de Java. Un
        Integer es un OBJETO, así que == compara si es el MISMO objeto
        (ReferencesAndNull, CAPA 4):
        - 127 == 127 -> true (mismo objeto de la caché)
        - 128 == 128 -> false (dos objetos distintos)

        Lo peligroso: un código que compara ids con == funciona en las
        pruebas (ids pequeños) y falla en producción cuando los ids pasan
        de 127. Pasa mucho con Long que vienen de una base de datos.

        La regla, igual que con String: compara wrappers con equals, o
        convierte a primitivo primero.

        (El constructor new Integer(5), que siempre creaba un objeto
        nuevo, está obsoleto y marcado para eliminarse. Usa valueOf o
        autoboxing.)
    */
    static boolean integerCacheExample() {
        Integer smallA = 127;
        Integer smallB = 127;
        Integer bigA = 128;
        Integer bigB = 128;

        boolean cachedAreSame = smallA == smallB;     // mismo objeto: true
        boolean bigAreDifferent = bigA != bigB;        // objetos distintos: ¡== daría false!
        boolean equalsWorks = bigA.equals(bigB);       // compara el valor
        boolean primitiveWorks = bigA.intValue() == bigB.intValue();

        return cachedAreSame && bigAreDifferent && equalsWorks && primitiveWorks;
    }

    /*
        =================================================================
        CAPA 4) UNBOXING DE NULL: NULLPOINTEREXCEPTION ESCONDIDA
        =================================================================

        Como el unboxing es una llamada a intValue() (CAPA 2), hacer
        unboxing de un wrapper null lanza NullPointerException. Lo
        traicionero es que en el código NO se ve ninguna llamada a
        método: parece una simple asignación o una suma.

            Integer stock = map.get("teclado");   // null si no existe
            int units = stock;                    // NPE: null.intValue()

        LA TRAMPA DEL TERNARIO (muy difícil de ver):
            Integer qty = useDefault ? 0 : stock.get("x");
        Si una rama es int (0) y la otra Integer, el ternario completo es
        de tipo int (Conditionals, CAPA 6). Java hace unboxing de la rama
        Integer... y si es null, NPE, aunque al final ibas a guardar el
        resultado en un Integer que sí aceptaba null. El bytecode lo
        confirma: intValue() y luego valueOf() otra vez.

        Defensas:
        - map.getOrDefault(clave, 0) en lugar de get.
        - Revisar null antes de usar el wrapper como número.
        - Mantener el mismo tipo en ambas ramas del ternario.
    */
    static boolean nullUnboxingExample() {
        Map<String, Integer> stock = new HashMap<>();
        stock.put("mouse", 15);

        boolean directUnboxingFails;
        try {
            int units = stock.get("teclado");      // no existe: null -> int
            directUnboxingFails = units < 0;
        } catch (NullPointerException e) {
            directUnboxingFails = true;
        }

        boolean ternaryFails;
        try {
            boolean useDefault = false;
            Integer quantity = useDefault ? 0 : stock.get("teclado");   // ¡el ternario es int!
            ternaryFails = quantity == null;
        } catch (NullPointerException e) {
            ternaryFails = true;
        }

        int safe = stock.getOrDefault("teclado", 0);

        return directUnboxingFails && ternaryFails && safe == 0;
    }

    /*
        =================================================================
        CAPA 5) WRAPPERS DE DISTINTO TIPO NUNCA SON IGUALES
        =================================================================

        El equals de un wrapper solo da true con otro wrapper del MISMO
        tipo. Integer 1 y Long 1 NO son iguales:
            Integer.valueOf(1).equals(Long.valueOf(1))   // false

        Por qué: equals respeta su contrato (ObjectContract, CAPA 4).
        Si Integer.equals aceptara Longs, Long.equals tendría que aceptar
        Integers para ser simétrico, y así con todos.

        El bug real que provoca: un Map<Long, ...> donde buscas con un
        literal int. El literal 1 se convierte en Integer (no en Long),
        así que no encuentra nada:
            Map<Long, String> users = ...;   users.put(1L, "Ana");
            users.get(1)     -> null    (busca un Integer)
            users.get(1L)    -> "Ana"
        Compila sin errores porque Map.get recibe Object.
    */
    static boolean crossTypeEqualityExample() {
        boolean differentTypesNotEqual = !Integer.valueOf(1).equals(Long.valueOf(1));

        Map<Long, String> usersById = new HashMap<>();
        usersById.put(1L, "Ana");

        boolean intKeyFindsNothing = usersById.get(1) == null;    // 1 se volvió Integer
        boolean longKeyWorks = "Ana".equals(usersById.get(1L));

        return differentTypesNotEqual && intKeyFindsNothing && longKeyWorks;
    }

    /*
        =================================================================
        CAPA 6) LIST<INTEGER>.REMOVE: ÍNDICE O VALOR
        =================================================================

        List tiene dos métodos remove:
            remove(int index)       quita el elemento en esa POSICIÓN
            remove(Object value)    quita la primera aparición de ese VALOR

        Con una List<Integer>, list.remove(1) es ambiguo para una persona,
        pero no para el compilador: elige remove(int) porque no necesita
        boxing (Methods, CAPA 6: la coincidencia exacta gana). Así que
        quita la POSICIÓN 1, no el número 1.

        Para quitar el VALOR 1: list.remove(Integer.valueOf(1)).
    */
    static boolean listRemoveExample() {
        List<Integer> byIndex = new ArrayList<>(List.of(5, 10, 1));
        byIndex.remove(1);                          // quita la posición 1 (el 10)

        List<Integer> byValue = new ArrayList<>(List.of(5, 10, 1));
        byValue.remove(Integer.valueOf(1));         // quita el número 1

        return byIndex.equals(List.of(5, 1)) && byValue.equals(List.of(5, 10));
    }

    /*
        =================================================================
        CAPA 7) EL COSTO DE LOS WRAPPERS
        =================================================================

        Un int ocupa 4 bytes y vive directo en la variable. Un Integer es
        un objeto en el heap: ocupa unos 16 bytes, más la referencia que
        apunta a él, y el recolector de basura tiene que limpiarlo.

        Por eso una List<Integer> de un millón de números ocupa varias
        veces más memoria que un int[] de un millón.

        El error clásico (Effective Java, "prefiere primitivos a
        wrappers"): declarar un acumulador como wrapper por descuido.
            Long total = 0L;
            for (int i = 0; i < 1_000_000; i++) {
                total += i;       // unboxing, suma, boxing: ¡un objeto Long NUEVO por vuelta!
            }
        Con long total = 0L no se crea ningún objeto. El resultado es el
        mismo; la diferencia es de velocidad y de basura generada.

        Por esto existen arreglos primitivos (int[]), y en v7_functional/
        verás IntStream y LongStream, que evitan el boxing.
    */
    static long sumWithPrimitive(int limit) {
        long total = 0L;
        for (int i = 0; i < limit; i++) {
            total += i;
        }
        return total;
    }

    static long sumWithWrapper(int limit) {
        Long total = 0L;                  // un descuido: el acumulador es un objeto
        for (int i = 0; i < limit; i++) {
            total += i;                   // crea un Long nuevo en cada vuelta (salvo los de la caché)
        }
        return total;
    }

    static boolean costExample() {
        // Mismo resultado; la versión con wrapper solo es más lenta y genera basura.
        return sumWithPrimitive(100_000) == sumWithWrapper(100_000);
    }

    /*
        =================================================================
        CAPA 8) LOS MÉTODOS ÚTILES DE LOS WRAPPERS
        =================================================================

        Además de envolver, las clases wrapper son "cajas de
        herramientas" de su tipo. Los más usados:

        CONVERTIR TEXTO <-> NÚMERO (ConversionsAndCasting, CAPA 10)
            Integer.parseInt("42")       -> int 42        (primitivo)
            Integer.valueOf("42")        -> Integer 42    (objeto)
            Integer.parseInt("ff", 16)   -> 255           (en otra base)
            Integer.toString(42), Integer.toHexString(255), Integer.toBinaryString(10)

        LÍMITES
            Integer.MAX_VALUE, Integer.MIN_VALUE, Long.MAX_VALUE, Double.MAX_VALUE...

        COMPARAR SIN ERRORES (ObjectContract, CAPA 9)
            Integer.compare(a, b), Double.compare(a, b)

        ARITMÉTICA (útiles con referencias a métodos: Integer::sum)
            Integer.sum, Integer.max, Integer.min, Integer.signum

        CHARACTER (Strings, CAPA 8)
            Character.isDigit, isLetter, isLetterOrDigit, isWhitespace,
            toUpperCase, getNumericValue

        BOOLEAN
            Boolean.parseBoolean("true") -> true, sin importar mayúsculas.
            CUALQUIER otro texto ("yes", "1", "sí") da false, sin error.
            Ojo si lees configuraciones escritas por personas.

        Y algo importante: los wrappers son INMUTABLES, como String.
        boxed++ no modifica el objeto: crea uno nuevo y reasigna la
        variable. Por eso pasar un Integer a un método se comporta como
        pasar un int: el método no puede cambiar el valor de quien llamó.
    */
    static boolean utilityMethodsExample() {
        boolean parsing = Integer.parseInt("42") == 42
                && Integer.valueOf("42").equals(42)
                && Integer.parseInt("ff", 16) == 255
                && Integer.toHexString(255).equals("ff")
                && Integer.toBinaryString(10).equals("1010");

        boolean comparing = Integer.compare(3, 7) < 0 && Integer.max(3, 7) == 7 && Integer.signum(-5) == -1;

        boolean characters = Character.isDigit('7') && Character.isLetterOrDigit('ñ') && !Character.isWhitespace('a');

        boolean booleans = Boolean.parseBoolean("TRUE")
                && !Boolean.parseBoolean("yes")
                && !Boolean.parseBoolean("sí");

        Integer counter = 5;
        Integer sameObject = counter;
        counter++;                                // crea OTRO Integer; sameObject sigue apuntando al 5
        boolean immutable = sameObject == 5 && counter == 6;

        return parsing && comparing && characters && booleans && immutable;
    }

    /*
        =================================================================
        CAPA 9) CUÁNDO USAR PRIMITIVO Y CUÁNDO WRAPPER
        =================================================================

        | Situación                                         | Usa                    |
        |---------------------------------------------------|------------------------|
        | Variables locales, cálculos, contadores           | primitivo (int, long)  |
        | Campos de una clase que siempre tienen valor      | primitivo              |
        | Elementos de colecciones y genéricos              | wrapper (obligatorio)  |
        | Un dato que de verdad puede "no existir"          | wrapper, con cuidado   |
        |   (columna opcional de una base de datos)         |                        |
        | Muchos números y el rendimiento importa           | arreglo primitivo, IntStream |

        Regla general: PRIMITIVOS POR DEFECTO. Usa wrappers solo donde son
        obligatorios o donde null tiene un significado claro. Y para "puede
        no haber valor" en lo que devuelve un método, Optional suele
        comunicarlo mejor que un Integer null (v7_functional/part2_streams/OptionalUsage).
    */

    /*
        =================================================================
        CAPA 10) ERRORES COMUNES
        =================================================================

        1) Comparar wrappers con ==. Funciona de -128 a 127 y falla
           después (CAPA 3).

        2) Hacer unboxing de un posible null (map.get, campos opcionales)
           (CAPA 4).

        3) Mezclar int y wrapper en las ramas de un ternario (CAPA 4).

        4) Buscar en un Map<Long, ...> con un literal int (CAPA 5).

        5) Usar list.remove(1) esperando quitar el número 1 (CAPA 6).

        6) Acumuladores declarados como wrapper dentro de ciclos (CAPA 7).

        7) Usar new Integer(...) (obsoleto) en lugar de valueOf (CAPA 3).

        8) Esperar que Boolean.parseBoolean entienda "sí" o "1" (CAPA 8).

        9) Creer que boxed++ modifica el objeto (CAPA 8).

        10) Usar wrappers "por si acaso" donde un primitivo basta (CAPA 9).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Wrapper: un objeto que envuelve un primitivo (Integer, Double,
          Character, Boolean...). Necesario para colecciones y genéricos,
          puede ser null y trae utilidades.
        - Autoboxing/unboxing: el compilador inserta valueOf() e
          intValue() por ti.
        - Caché de -128 a 127: == funciona ahí por casualidad. Compara con
          equals.
        - Unboxing de null = NullPointerException, también escondida en
          ternarios.
        - Integer y Long nunca son iguales entre sí; cuidado con las
          claves de mapas.
        - List<Integer>.remove(1) quita por posición.
        - Los wrappers cuestan memoria y tiempo: primitivos por defecto.
        - Son inmutables, como String.
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
