package v1_fundamentals.part1_first_steps;

import java.util.ArrayList;
import java.util.List;

/*
    =====================================================================
    OPERADORES — DE CERO A NIVEL TÉCNICO
    =====================================================================

    Ruta de v1_fundamentals/: guía 4 de 14  (part1_first_steps)
    Anterior:  part1_first_steps/Variables
    Siguiente: part1_first_steps/ConversionsAndCasting

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.

    Índice:
        CAPA 1)  Operador, operando y expresión
        CAPA 2)  Aritméticos y la división entera
        CAPA 3)  El residuo % y la trampa de los negativos
        CAPA 4)  + con texto: concatenar
        CAPA 5)  Incremento y decremento: ++ y --
        CAPA 6)  Asignación compuesta: += y su conversión oculta
        CAPA 7)  Comparación
        CAPA 8)  Lógicos y el cortocircuito
        CAPA 9)  Operadores de bits y desplazamientos
        CAPA 10) Precedencia, asociatividad y orden de evaluación
        CAPA 11) Errores comunes
        RESUMEN FINAL
*/
public class Operators {

    public static void main(String[] args) {
        check(arithmeticExample(), "CAPA 2");
        check(remainderExample(), "CAPA 3");
        check(concatenationExample(), "CAPA 4");
        check(incrementExample(), "CAPA 5");
        check(compoundAssignmentExample(), "CAPA 6");
        check(comparisonExample(), "CAPA 7");
        check(shortCircuitExample(), "CAPA 8");
        check(bitwiseExample(), "CAPA 9");
        check(precedenceExample(), "CAPA 10");
        System.out.println("Operators: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) OPERADOR, OPERANDO Y EXPRESIÓN
        =================================================================

        - OPERADOR: el símbolo que hace una operación: +, *, ==, &&...
        - OPERANDO: los valores sobre los que actúa: en a + b, a y b.
        - EXPRESIÓN: cualquier combinación que PRODUCE un valor:
              5          price * 2          age >= 18 && hasId

        Según cuántos operandos reciben:
        - unarios (uno):     -x   !ready   count++
        - binarios (dos):    a + b   a == b   a && b
        - ternario (tres):   condición ? siVerdadero : siFalso
          (el único ternario; se explica en v1_fundamentals/Conditionals)

        Cada expresión tiene un TIPO, que el compilador calcula: 2 + 3 es
        int, 2.0 + 3 es double, 5 > 3 es boolean. Ese tipo decide qué
        puedes hacer con el resultado.
    */

    /*
        =================================================================
        CAPA 2) ARITMÉTICOS Y LA DIVISIÓN ENTERA
        =================================================================

            +  suma        -  resta        *  multiplicación
            /  división    %  residuo (CAPA 3)

        LA REGLA MÁS IMPORTANTE: el tipo del resultado depende de los
        operandos, no de dónde lo guardes.
        - int con int        -> resultado int
        - si alguno es double -> resultado double
        (Las reglas completas de esta "promoción" están en
        ConversionsAndCasting.)

        Por eso, dividir dos enteros da un ENTERO: los decimales se
        DESCARTAN (no se redondean). 7 / 2 da 3, no 3.5.

        Por qué Java hace esto: la división de enteros es una instrucción
        distinta y más rápida del procesador, y en muchísimos casos es lo
        que quieres (¿cuántas cajas de 12 llenas con 30 piezas? 2).

        "Descartar" significa truncar HACIA CERO: 7 / 2 = 3 y -7 / 2 = -3
        (no -4). Si necesitas redondear siempre hacia abajo, usa
        Math.floorDiv.

        Para obtener el resultado con decimales, al menos un operando
        debe ser decimal ANTES de dividir.
    */
    static boolean arithmeticExample() {
        int boxesFilled = 30 / 12;          // 2: cuántas cajas COMPLETAS
        int truncated = 7 / 2;              // 3: los decimales se descartan
        int negativeTruncated = -7 / 2;     // -3: trunca hacia cero
        int floor = Math.floorDiv(-7, 2);   // -4: redondea hacia abajo

        double wrongAverage = (7 + 8) / 2;      // 7.0: se dividió como int, DESPUÉS se guardó en double
        double rightAverage = (7 + 8) / 2.0;    // 7.5: 2.0 hace que la división sea double

        return boxesFilled == 2 && truncated == 3 && negativeTruncated == -3 && floor == -4
                && wrongAverage == 7.0 && rightAverage == 7.5;
    }

    /*
        Y recuerda de PrimitiveTypes: dividir un ENTERO entre 0 lanza
        ArithmeticException; un DECIMAL entre 0 da Infinity o NaN.
    */

    /*
        =================================================================
        CAPA 3) EL RESIDUO % Y LA TRAMPA DE LOS NEGATIVOS
        =================================================================

        a % b da el RESIDUO de dividir a entre b: 17 % 5 = 2, porque
        17 = 5 * 3 + 2. No es "porcentaje".

        Usos muy comunes:
        - ¿Es par?                  n % 2 == 0
        - ¿Es múltiplo de 3?        n % 3 == 0
        - Último dígito             n % 10
        - Dar la vuelta en un ciclo (índice circular): (i + 1) % size
        - Convertir unidades: 135 minutos = 135 / 60 horas y 135 % 60 minutos

        LA TRAMPA: en Java el residuo toma el SIGNO del número de la
        izquierda. -7 % 2 es -1, no 1. Por eso esta forma de "es impar"
        falla con negativos:
            n % 2 == 1      // con n = -3 da -1, así que dice "no es impar"
        Las formas correctas: n % 2 != 0, o Math.floorMod(n, 2) == 1
        (floorMod siempre devuelve un resultado con el signo del divisor).
    */
    static boolean isOddWrong(int n) {
        return n % 2 == 1;
    }

    static boolean isOdd(int n) {
        return n % 2 != 0;
    }

    static String minutesToClock(int totalMinutes) {
        int hours = totalMinutes / 60;
        int minutes = totalMinutes % 60;
        return hours + ":" + (minutes < 10 ? "0" : "") + minutes;
    }

    static boolean remainderExample() {
        int size = 3;
        int lastIndex = 2;
        int wrappedIndex = (lastIndex + 1) % size;   // después del último, vuelve al 0

        return 17 % 5 == 2
                && 1234 % 10 == 4
                && wrappedIndex == 0
                && minutesToClock(135).equals("2:15")
                && -7 % 2 == -1
                && !isOddWrong(-3) && isOdd(-3)          // la versión ingenua falla con negativos
                && Math.floorMod(-7, 2) == 1;
    }

    /*
        =================================================================
        CAPA 4) + CON TEXTO: CONCATENAR
        =================================================================

        Si al menos uno de los operandos de + es un String, + deja de
        sumar y CONCATENA (une texto). El otro operando se convierte a
        texto automáticamente.

        La trampa: + se evalúa de IZQUIERDA A DERECHA, y en cada paso
        decide si suma o concatena según lo que tenga hasta ese momento:
            "Total: " + 1 + 2    ->  "Total: 1" + 2  ->  "Total: 12"
            1 + 2 + " artículos" ->  3 + " artículos" ->  "3 artículos"
        Para sumar dentro de un texto, usa paréntesis: "Total: " + (1 + 2)

        Otra trampa: char + char NO concatena: los char son números
        (PrimitiveTypes, CAPA 9), así que 'a' + 'b' es 97 + 98 = 195.

        Si vienes de JavaScript: + funciona parecido, pero en Java no hay
        conversión de texto a número: "5" * 2 no compila (en JS da 10).
    */
    static boolean concatenationExample() {
        String leftToRight = "Total: " + 1 + 2;
        String sumFirst = 1 + 2 + " artículos";
        String withParentheses = "Total: " + (1 + 2);
        int charsAreNumbers = 'a' + 'b';
        String charsAsText = "" + 'a' + 'b';   // empezar con "" fuerza la concatenación

        return leftToRight.equals("Total: 12")
                && sumFirst.equals("3 artículos")
                && withParentheses.equals("Total: 3")
                && charsAreNumbers == 195
                && charsAsText.equals("ab");
    }

    /*
        Para armar texto dentro de un ciclo, + crea un String nuevo en
        cada vuelta; para eso existe StringBuilder (v1_fundamentals/Strings).
    */

    /*
        =================================================================
        CAPA 5) INCREMENTO Y DECREMENTO: ++ Y --
        =================================================================

        count++ suma 1 a count; count-- le resta 1. Son atajos de
        count = count + 1, tan comunes que tienen su propio operador.

        Tienen dos formas, y la diferencia está en QUÉ VALOR DEVUELVEN
        cuando se usan dentro de otra expresión:
        - POSFIJO  count++ : devuelve el valor VIEJO, y después incrementa.
        - PREFIJO  ++count : incrementa primero, y devuelve el valor NUEVO.

        Si se usan solos en una línea (count++;), las dos formas hacen
        exactamente lo mismo.

        La trampa clásica: x = x++ deja x igual. Pasos: x++ devuelve el
        valor viejo (5) y pone x en 6; luego la asignación guarda ese
        valor viejo (5) en x, sobrescribiendo el 6.

        Regla profesional: usa ++ y -- SOLOS en su propia línea (o en el
        for). Mezclados dentro de otras expresiones hacen el código
        difícil de leer y fácil de equivocar.
    */
    static boolean incrementExample() {
        int i = 5;
        int postfix = i++;   // devuelve 5, luego i pasa a 6
        int prefix = ++i;    // i pasa a 7, devuelve 7

        int x = 5;
        x = x++;             // la trampa: x++ pone x en 6 y devuelve 5; luego x = 5 lo sobrescribe

        return postfix == 5 && prefix == 7 && i == 7 && x == 5;
    }

    /*
        =================================================================
        CAPA 6) ASIGNACIÓN COMPUESTA: += Y SU CONVERSIÓN OCULTA
        =================================================================

            a += b    es casi  a = a + b
            a -= b, a *= b, a /= b, a %= b   (y los de bits: &=, |=, ^=, <<=...)

        "Casi", porque la forma compuesta incluye una CONVERSIÓN
        AUTOMÁTICA al tipo de la variable. Técnicamente:
            a += b   es   a = (tipo de a) (a + b)

        Por qué existe esa diferencia: sin ella, algo tan normal como
        sumar 1 a un byte no compilaría, porque byte + int da int
        (ConversionsAndCasting):
            byte level = 10;
            level = level + 1;   // ERROR: incompatible types: possible lossy conversion from int to byte
            level += 1;          // compila: la conversión está incluida

        El precio: la conversión ocurre EN SILENCIO aunque pierda datos.
        Si el resultado no cabe en un byte, se desborda; si sumas un
        decimal a un int, se pierden los decimales (es la trampa de var
        en Variables, CAPA 8). javac -Xlint lo advierte como
        [lossy-conversions]; este método la suprime porque es a propósito.
    */
    @SuppressWarnings("lossy-conversions")
    static boolean compoundAssignmentExample() {
        byte level = 10;
        level += 1;                // 11: compila gracias a la conversión incluida

        byte overflowed = 100;
        overflowed += 200;         // 300 no cabe en un byte (máx. 127): da 44 sin avisar

        int total = 10;
        total *= 3;                // 30
        total -= 5;                // 25
        total /= 2;                // 12 (división entera)
        total %= 5;                // 2

        return level == 11 && overflowed == 44 && total == 2;
    }

    /*
        =================================================================
        CAPA 7) COMPARACIÓN
        =================================================================

            ==  igual      !=  distinto
            <   menor      <=  menor o igual
            >   mayor      >=  mayor o igual

        Siempre devuelven un boolean.

        Reglas importantes:
        1) == y = son cosas distintas: = asigna, == compara.

        2) Con PRIMITIVOS, == compara valores. Con OBJETOS (String,
           tus clases), == compara si son el MISMO objeto, no si tienen
           el mismo contenido. Para contenido se usa equals
           (v1_fundamentals/ReferencesAndNull y ObjectContract).

        3) En Java NO existe === ni !==. Como no hay conversiones
           automáticas entre tipos incompatibles, == ya es "estricto":
           5 == "5" no compila (en JavaScript da true).

        4) No se pueden encadenar como en matemáticas:
               1 < x < 10    // ERROR: bad operand types for binary operator '<'
           Por qué: 1 < x da un boolean, y luego boolean < 10 no tiene
           sentido. Se escribe: 1 < x && x < 10.

        5) Decimales: no uses == (PrimitiveTypes, CAPA 7).
    */
    static boolean isInRange(int value, int min, int max) {
        return min <= value && value <= max;   // así se escribe "min <= value <= max"
    }

    static boolean comparisonExample() {
        int age = 20;
        boolean adult = age >= 18;
        boolean notTeen = age != 15;

        return adult && notTeen && isInRange(5, 1, 10) && !isInRange(11, 1, 10);
    }

    /*
        =================================================================
        CAPA 8) LÓGICOS Y EL CORTOCIRCUITO
        =================================================================

            &&  Y (and):  true solo si AMBOS son true
            ||  O (or):   true si AL MENOS UNO es true
            !   NO (not): invierte el valor

        | a     | b     | a && b | a || b | !a    |
        |-------|-------|--------|--------|-------|
        | true  | true  | true   | true   | false |
        | true  | false | false  | true   | false |
        | false | true  | false  | true   | true  |
        | false | false | false  | false  | true  |

        CORTOCIRCUITO: && y || evalúan de izquierda a derecha y se
        DETIENEN en cuanto conocen el resultado:
        - en a && b, si a es false, el resultado ya es false: b NO se evalúa.
        - en a || b, si a es true, el resultado ya es true: b NO se evalúa.

        Por qué importa tanto:
        1) PROTECCIÓN: puedes poner primero la condición que hace segura
           a la segunda. El patrón más usado de Java:
               text != null && !text.isEmpty()
           Si text es null, la segunda parte (que lanzaría
           NullPointerException) ni siquiera se ejecuta.
        2) EFICIENCIA: pon primero la condición barata o la que más
           seguido decide.
        3) EFECTOS: si la segunda parte llama a un método, puede no
           ejecutarse. No pongas ahí acciones que deban ocurrir siempre.

        Existen también & y | sobre booleanos: hacen lo mismo pero SIN
        cortocircuito (evalúan ambos lados siempre). Casi nunca los
        quieres en condiciones; su uso real es con bits (CAPA 9).
        Y ^ (XOR): true si exactamente uno de los dos es true.
    */
    static int evaluations;

    static boolean expensiveCheck() {
        evaluations++;
        return true;
    }

    static boolean shortCircuitExample() {
        evaluations = 0;
        boolean result = false && expensiveCheck();   // no llega a llamar a expensiveCheck
        boolean skipped = evaluations == 0;

        String text = null;
        boolean safe = text != null && !text.isEmpty();   // false, sin NullPointerException

        boolean withoutShortCircuitFails;
        try {
            boolean unsafe = text != null & !text.isEmpty();   // & evalúa ambos lados: NPE
            withoutShortCircuitFails = unsafe;
        } catch (NullPointerException e) {
            withoutShortCircuitFails = true;
        }

        boolean exactlyOne = true ^ false;

        return !result && skipped && !safe && withoutShortCircuitFails && exactlyOne;
    }

    /*
        =================================================================
        CAPA 9) OPERADORES DE BITS Y DESPLAZAMIENTOS
        =================================================================

        Trabajan directamente sobre los BITS de un entero (PrimitiveTypes,
        CAPA 2). Se usan menos en código de negocio, pero aparecen en
        permisos, banderas, protocolos de red, criptografía, gráficos y
        en preguntas de entrevista.

            &   Y bit a bit:   1 solo donde AMBOS tienen 1
            |   O bit a bit:   1 donde AL MENOS UNO tiene 1
            ^   XOR bit a bit: 1 donde son DISTINTOS
            ~   NOT: invierte todos los bits
            <<  desplaza a la izquierda (cada posición = multiplicar por 2)
            >>  desplaza a la derecha conservando el signo (dividir por 2
                redondeando hacia ABAJO: -7 >> 1 es -4, pero -7 / 2 es -3)
            >>> desplaza a la derecha rellenando con ceros (sin signo)

        Ejemplo real: permisos de un archivo, cada uno en un bit.
            READ    = 0b001  (1)
            WRITE   = 0b010  (2)
            EXECUTE = 0b100  (4)
        Un solo int guarda cualquier combinación:
            permisos = READ | WRITE           -> 0b011
            ¿tiene WRITE?   (permisos & WRITE) != 0
            quitar WRITE:   permisos &= ~WRITE
            alternar:       permisos ^= EXECUTE

        En Java moderno, para un conjunto de opciones con nombre se
        prefiere EnumSet (Enums, CAPA 7), que por dentro hace
        exactamente esto pero con tipos seguros y legibles.
    */
    static final int READ = 0b001;
    static final int WRITE = 0b010;
    static final int EXECUTE = 0b100;

    static boolean bitwiseExample() {
        int permissions = READ | WRITE;                    // 0b011
        boolean canWrite = (permissions & WRITE) != 0;
        permissions &= ~WRITE;                             // quita WRITE: 0b001
        boolean writeRemoved = (permissions & WRITE) == 0;
        permissions ^= EXECUTE;                            // alterna EXECUTE: 0b101

        boolean shifts = (1 << 3) == 8                     // 1 x 2 x 2 x 2
                && (40 >> 2) == 10                         // 40 / 2 / 2
                && (-16 >> 2) == -4                        // >> conserva el signo
                && (-16 >>> 28) == 15;                     // >>> rellena con ceros: queda positivo

        boolean evenByBit = (10 & 1) == 0;                 // el último bit dice si es par

        return canWrite && writeRemoved && permissions == 0b101 && shifts && evenByBit;
    }

    /*
        =================================================================
        CAPA 10) PRECEDENCIA, ASOCIATIVIDAD Y ORDEN DE EVALUACIÓN
        =================================================================

        PRECEDENCIA: qué operador se aplica primero cuando hay varios.
        Como en matemáticas, * va antes que +: 2 + 3 * 4 es 14.

        De MAYOR a MENOR precedencia (los más usados):
            1.  x++  x--                         posfijos
            2.  ++x  --x  -x  !  ~  (tipo)       unarios y cast
            3.  *  /  %
            4.  +  -
            5.  <<  >>  >>>
            6.  <  >  <=  >=  instanceof
            7.  ==  !=
            8.  &
            9.  ^
            10. |
            11. &&
            12. ||
            13. ?:                               ternario
            14. =  +=  -=  ...                   asignaciones

        ASOCIATIVIDAD: cuando hay varios operadores del MISMO nivel.
        - Casi todos van de izquierda a derecha: 10 - 4 - 3 es (10 - 4) - 3 = 3.
        - Las asignaciones van de derecha a izquierda: a = b = 5 es
          a = (b = 5), y deja ambas en 5.

        Fíjate: && está por encima de ||. true || false && false se
        evalúa como true || (false && false) = true.

        ORDEN DE EVALUACIÓN: Java garantiza que los OPERANDOS se evalúan
        de izquierda a derecha, aunque la precedencia diga que una
        operación se calcula antes. En a() + b() * c(), se LLAMA primero
        a a(), luego b(), luego c(); y después se multiplica y suma.
        (En C esto no está garantizado; en Java sí.)

        Regla profesional: no memorices la tabla. Si una expresión
        mezcla operadores de distinto tipo, usa PARÉNTESIS. Le cuestan
        cero al programa y le ahorran dudas a quien lee.
    */
    static final List<String> callOrder = new ArrayList<>();

    static int recordCall(String name, int value) {
        callOrder.add(name);
        return value;
    }

    static boolean precedenceExample() {
        int a;
        int b;
        a = b = 5;   // derecha a izquierda: b = 5, luego a = 5

        callOrder.clear();
        int result = recordCall("a", 1) + recordCall("b", 2) * recordCall("c", 3);

        return 2 + 3 * 4 == 14
                && (2 + 3) * 4 == 20
                && 10 - 4 - 3 == 3
                && a == 5 && b == 5
                && (true || false && false)
                && result == 7                                  // 1 + (2 * 3)
                && callOrder.equals(List.of("a", "b", "c"));    // pero se llamaron en orden
    }

    /*
        =================================================================
        CAPA 11) ERRORES COMUNES
        =================================================================

        1) Esperar decimales al dividir dos int (CAPA 2).

        2) Convertir a double DESPUÉS de dividir: (double) (a / b) ya
           perdió los decimales; lo correcto es (double) a / b (CAPA 2).

        3) Revisar "impar" con n % 2 == 1 (falla con negativos) (CAPA 3).

        4) "Total: " + a + b cuando querías sumar a y b (CAPA 4).

        5) x = x++, o ++/-- metidos dentro de expresiones complejas
           (CAPA 5).

        6) Asumir que += no puede perder datos (CAPA 6).

        7) Usar == para comparar el contenido de objetos (CAPA 7).

        8) Escribir 1 < x < 10 (CAPA 7).

        9) Poner la condición peligrosa ANTES de la que la protege:
           !text.isEmpty() && text != null lanza NPE (CAPA 8).

        10) Confiar en la precedencia en vez de usar paréntesis (CAPA 10).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - El tipo del resultado depende de los operandos: int / int es
          int (trunca hacia cero).
        - % da el residuo con el signo del dividendo; para negativos,
          Math.floorMod.
        - + con un String concatena, evaluando de izquierda a derecha.
        - x++ devuelve el valor viejo; ++x el nuevo. Úsalos solos.
        - += incluye una conversión silenciosa al tipo de la variable.
        - == compara valores en primitivos y referencias en objetos; no
          hay ===; no se encadenan comparaciones.
        - && y || hacen cortocircuito: pon primero la condición que
          protege a la otra.
        - &, |, ^, ~, <<, >>, >>> trabajan sobre bits (permisos, banderas).
        - * antes que +, && antes que ||, asignación de derecha a
          izquierda; operandos siempre de izquierda a derecha. En la duda,
          paréntesis.
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
