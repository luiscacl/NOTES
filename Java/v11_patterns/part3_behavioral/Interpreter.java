package v11_patterns.part3_behavioral;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/*
    =====================================================================
    INTERPRETER: REGLAS COMO UN ÁRBOL DE OBJETOS QUE SE EVALÚA
    =====================================================================

    Ruta de patterns/: guía 15 de 28  (part3_behavioral)
    Anterior:  part3_behavioral/Command
    Siguiente: part3_behavioral/Iterator

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.

    Importancia: BAJA para escribirlo completo (casi nunca se escribe un
    lenguaje propio); MEDIA la idea de fondo: representar reglas como
    DATOS (un árbol de objetos) para poder evaluarlas, guardarlas y
    explicarlas. Esta guía se queda con esa parte.

    Índice:
        CAPA 1)  La idea: un pequeño lenguaje de reglas
        CAPA 2)  Las reglas como un árbol (sealed + records)
        CAPA 3)  Evaluar el árbol
        CAPA 4)  La ventaja real: el árbol es un dato
        CAPA 5)  ¿Y no bastaba con Predicate?
        CAPA 6)  Dónde lo usas sin escribirlo
        CAPA 7)  Cuándo NO escribir un intérprete
        RESUMEN FINAL
*/
public class Interpreter {

    public static void main(String[] args) {
        check(evaluateExample(), "CAPA 3");
        check(treeAsDataExample(), "CAPA 4");
        check(predicateComparisonExample(), "CAPA 5");
        check(jdkInterpreterExample(), "CAPA 6");
        System.out.println("Interpreter: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) LA IDEA: UN PEQUEÑO LENGUAJE DE REGLAS
        =================================================================

        El área de marketing quiere crear promociones SIN pedirle cada vez
        un cambio al equipo de desarrollo:

            "envío gratis si: total >= 500 Y (cliente VIP O cupón = BUENFIN)"

        Esa regla es una frase en un pequeño LENGUAJE: tiene piezas básicas
        (comparar el total, revisar si es VIP, comparar el cupón) y formas
        de COMBINARLAS (Y, O, NO). Con las mismas piezas se arman infinitas
        reglas.

        INTERPRETER: "Dado un lenguaje, define una representación de su
        gramática junto con un intérprete que usa esa representación para
        evaluar frases del lenguaje."

        En la práctica: cada pieza del lenguaje es una CLASE; una regla
        concreta es un ÁRBOL de objetos de esas clases; y evaluar la regla
        es recorrer el árbol (part2_structural/Composite: el árbol es un
        Composite).
    */
    record Cart(BigDecimal total, boolean vip, String coupon) { }   // el CONTEXTO contra el que se evalúa

    /*
        =================================================================
        CAPA 2) LAS REGLAS COMO UN ÁRBOL (SEALED + RECORDS)
        =================================================================

        Cada tipo de nodo es un record; la jerarquía es sealed para que el
        compilador conozca todos (v2_oop/part4_advanced_types/
        SealedAndPatternMatching):

            HOJAS (preguntan algo del carrito):
                TotalAtLeast(500)        total >= 500
                IsVip()                  ¿es cliente VIP?
                CouponIs("BUENFIN")      ¿trae ese cupón?
            COMBINADORES (tienen hijos):
                And(izquierda, derecha)
                Or(izquierda, derecha)
                Not(regla)

        La regla de la CAPA 1 queda como este árbol:

                          And
                        /     \
            TotalAtLeast(500)  Or
                              /  \
                       IsVip()   CouponIs("BUENFIN")
    */
    sealed interface Rule permits TotalAtLeast, IsVip, CouponIs, And, Or, Not { }

    record TotalAtLeast(BigDecimal minimum) implements Rule { }

    record IsVip() implements Rule { }

    record CouponIs(String code) implements Rule { }

    record And(Rule left, Rule right) implements Rule { }

    record Or(Rule left, Rule right) implements Rule { }

    record Not(Rule rule) implements Rule { }

    static final Rule FREE_SHIPPING = new And(
            new TotalAtLeast(new BigDecimal("500")),
            new Or(new IsVip(), new CouponIs("BUENFIN")));

    /*
        =================================================================
        CAPA 3) EVALUAR EL ÁRBOL
        =================================================================

        Evaluar es una función RECURSIVA con un switch: cada tipo de nodo
        dice cómo calcular su valor, y los combinadores lo calculan
        evaluando a sus hijos. Es el mismo recorrido de Composite, CAPA 5.

        Fíjate en que And y Or usan && y ||, que son de cortocircuito
        (v1_fundamentals/part1_first_steps/Operators): si la izquierda de
        un And es false, la derecha ni se evalúa.
    */
    static boolean evaluate(Rule rule, Cart cart) {
        return switch (rule) {
            case TotalAtLeast r -> cart.total().compareTo(r.minimum()) >= 0;
            case IsVip r -> cart.vip();
            case CouponIs r -> r.code().equals(cart.coupon());
            case And r -> evaluate(r.left(), cart) && evaluate(r.right(), cart);
            case Or r -> evaluate(r.left(), cart) || evaluate(r.right(), cart);
            case Not r -> !evaluate(r.rule(), cart);
        };
    }

    static boolean evaluateExample() {
        return evaluate(FREE_SHIPPING, new Cart(new BigDecimal("600"), true, ""))            // VIP con 600
                && evaluate(FREE_SHIPPING, new Cart(new BigDecimal("600"), false, "BUENFIN"))   // cupón con 600
                && !evaluate(FREE_SHIPPING, new Cart(new BigDecimal("600"), false, ""))          // ni VIP ni cupón
                && !evaluate(FREE_SHIPPING, new Cart(new BigDecimal("300"), true, ""))           // VIP, pero total bajo
                && evaluate(new Not(new IsVip()), new Cart(BigDecimal.ONE, false, ""));
    }

    /*
        =================================================================
        CAPA 4) LA VENTAJA REAL: EL ÁRBOL ES UN DATO
        =================================================================

        Lo más valioso del patrón no es evaluar (eso también lo hace un
        if). Es que la regla es un DATO que se puede INSPECCIONAR. Con el
        MISMO árbol se pueden escribir más operaciones:

        - DESCRIBIRLA en texto para mostrarla al usuario o en la pantalla
          de administración.
        - EXPLICAR por qué NO aplicó: "no tienes envío gratis porque tu
          total es menor a 500". Con un if escondido en el código, eso es
          imposible de generar.
        - GUARDARLA (los records se pueden convertir a JSON;
          v8_apis/part2_io_and_network/Serialization) para que marketing
          las cree y edite desde un panel, sin desplegar código nuevo.

        Cada operación nueva es otra función con su switch, sin tocar los
        records (el lado "fácil" del problema de la expresión;
        SealedAndPatternMatching, CAPA 10).
    */
    static String describe(Rule rule) {
        return switch (rule) {
            case TotalAtLeast r -> "total >= " + r.minimum();
            case IsVip r -> "cliente VIP";
            case CouponIs r -> "cupón " + r.code();
            case And r -> "(" + describe(r.left()) + " Y " + describe(r.right()) + ")";
            case Or r -> "(" + describe(r.left()) + " O " + describe(r.right()) + ")";
            case Not r -> "NO " + describe(r.rule());
        };
    }

    /** Devuelve las condiciones que impidieron cumplir la regla (vacía si se cumple). */
    static List<String> whyNot(Rule rule, Cart cart) {
        if (evaluate(rule, cart)) {
            return List.of();
        }
        return switch (rule) {
            case And r -> Stream.concat(whyNot(r.left(), cart).stream(), whyNot(r.right(), cart).stream()).toList();
            case Or r -> List.of("necesitas " + describe(r));                     // ninguna de las opciones se cumplió
            case TotalAtLeast _, IsVip _, CouponIs _, Not _ -> List.of("no se cumple: " + describe(rule));   // sin default
        };
    }

    static boolean treeAsDataExample() {
        Cart cart = new Cart(new BigDecimal("300"), false, "");
        return describe(FREE_SHIPPING).equals("(total >= 500 Y (cliente VIP O cupón BUENFIN))")
                && whyNot(FREE_SHIPPING, cart).equals(List.of(
                        "no se cumple: total >= 500",
                        "necesitas (cliente VIP O cupón BUENFIN)"))
                && whyNot(FREE_SHIPPING, new Cart(new BigDecimal("900"), true, "")).isEmpty();
    }

    /*
        =================================================================
        CAPA 5) ¿Y NO BASTABA CON PREDICATE?
        =================================================================

        Las mismas reglas se pueden armar componiendo Predicate con and, or
        y negate (v7_functional/part1_lambdas/FunctionalInterfaces,
        CAPA 8), y evalúan IGUAL. La diferencia:

        | Predicate compuesto                       | Árbol de records (Interpreter)             |
        |-------------------------------------------|--------------------------------------------|
        | Una función: solo sabe EVALUAR            | Un dato: se puede evaluar, describir,      |
        |                                           | explicar, guardar, comparar                |
        | No se puede saber qué tiene adentro       | Se recorre y se inspecciona                |
        | Ideal: reglas fijas escritas en el código | Ideal: reglas que crea o edita alguien que |
        |                                           | no programa, o que hay que explicar        |

        Si la regla vive en el código y solo se evalúa: Predicate. Si la
        regla es un DATO del negocio: el árbol.
    */
    static final Predicate<Cart> FREE_SHIPPING_PREDICATE =
            ((Predicate<Cart>) cart -> cart.total().compareTo(new BigDecimal("500")) >= 0)
                    .and(((Predicate<Cart>) Cart::vip).or(cart -> "BUENFIN".equals(cart.coupon())));

    static boolean predicateComparisonExample() {
        List<Cart> carts = List.of(
                new Cart(new BigDecimal("600"), true, ""), new Cart(new BigDecimal("600"), false, "BUENFIN"),
                new Cart(new BigDecimal("600"), false, ""), new Cart(new BigDecimal("300"), true, ""));
        return carts.stream().allMatch(cart -> FREE_SHIPPING_PREDICATE.test(cart) == evaluate(FREE_SHIPPING, cart))
                && FREE_SHIPPING_PREDICATE.toString().contains("$$Lambda");   // por dentro, opaco: no se puede describir
    }

    /*
        =================================================================
        CAPA 6) DÓNDE LO USAS SIN ESCRIBIRLO
        =================================================================

        Casi nunca escribirás un intérprete, pero usas muchos:
        - EXPRESIONES REGULARES: Pattern.compile convierte el texto de la
          regex en un árbol de nodos internos que luego "interpreta" contra
          cada texto (v8_apis/part1_text_and_time/RegularExpressions,
          CAPA 8). Por eso compilar una vez y reutilizar es importante.
        - SQL: la base de datos convierte cada consulta en un árbol y lo
          ejecuta.
        - Las FÓRMULAS de una hoja de cálculo, y el lenguaje de expresiones
          que usan los frameworks en su configuración (Spring tiene uno).
        - El propio compilador de Java convierte tu código en un árbol
          antes de generar el bytecode (v12_jvm/).
    */
    static boolean jdkInterpreterExample() {
        Pattern rule = Pattern.compile("[A-Z]{3}-\\d{3}");  // el texto se compila a un árbol interno, una vez
        return rule.matcher("KBD-001").matches() && !rule.matcher("kbd-001").matches();   // y se interpreta muchas
    }

    /*
        =================================================================
        CAPA 7) CUÁNDO NO ESCRIBIR UN INTÉRPRETE
        =================================================================

        Casi siempre hay algo mejor que inventar un lenguaje:
        - Reglas fijas que cambian poco: código normal, o Predicate (CAPA 5).
        - Valores que cambian (montos, porcentajes): configuración
          (v10_design/part2_solid/OpenClosed, CAPA 10).
        - Patrones de texto: expresiones regulares.
        - Reglas que sí deben editarse sin programar: el árbol de records de
          esta guía, guardado como JSON y armado desde un panel con
          opciones (no escrito como texto libre); o, si crecen mucho, un
          "motor de reglas" existente.

        Lo que conviene evitar es escribir un PARSER propio (el programa que
        convierte un texto libre como "total >= 500 Y VIP" en el árbol):
        manejar errores de sintaxis, precedencia de operadores y mensajes
        claros es un proyecto entero.
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Interpreter: un pequeño lenguaje representado como un árbol de
          objetos (cada pieza, una clase) que se evalúa recorriéndolo.
        - Hoy: sealed + records para los nodos; funciones con switch para
          evaluar, describir y explicar.
        - Su valor real: la regla es un DATO (se guarda, se muestra, se
          explica "por qué no aplicó").
        - Reglas fijas en el código: Predicate basta.
        - Lo usas a diario: regex, SQL, fórmulas, el compilador.
        - No escribas un parser propio salvo que sea indispensable.
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
