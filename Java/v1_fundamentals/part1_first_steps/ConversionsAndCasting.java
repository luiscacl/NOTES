package v1_fundamentals.part1_first_steps;

/*
    =====================================================================
    CONVERSIONES Y CASTING — DE CERO A NIVEL TÉCNICO
    =====================================================================

    Ruta de v1_fundamentals/: guía 5 de 14  (part1_first_steps)
    Anterior:  part1_first_steps/Operators
    Siguiente: part2_control_flow/Conditionals

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.

    Índice:
        CAPA 1)  Por qué hace falta convertir entre tipos
        CAPA 2)  Conversión de ampliación: automática y (casi) segura
        CAPA 3)  La excepción: ampliar a float o double puede perder precisión
        CAPA 4)  Casting: conversión de reducción explícita
        CAPA 5)  Qué pasa exactamente cuando el valor no cabe
        CAPA 6)  Truncar no es redondear
        CAPA 7)  Promoción numérica: el tipo de una operación
        CAPA 8)  Constantes que caben: la excepción de los literales
        CAPA 9)  char y números
        CAPA 10) Texto y números: no se castea, se convierte
        CAPA 11) Y las referencias, boolean y wrappers
        CAPA 12) Errores comunes
        RESUMEN FINAL
*/
public class ConversionsAndCasting {

    public static void main(String[] args) {
        check(wideningExample(), "CAPA 2");
        check(wideningPrecisionLossExample(), "CAPA 3");
        check(castingExample(), "CAPA 4");
        check(outOfRangeExample(), "CAPA 5");
        check(roundingExample(), "CAPA 6");
        check(numericPromotionExample(), "CAPA 7");
        check(constantNarrowingExample(), "CAPA 8");
        check(charConversionsExample(), "CAPA 9");
        check(stringConversionsExample(), "CAPA 10");
        System.out.println("ConversionsAndCasting: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) POR QUÉ HACE FALTA CONVERTIR ENTRE TIPOS
        =================================================================

        Cada tipo primitivo se guarda de forma distinta (PrimitiveTypes):
        un int son 32 bits en entero, un double son 64 bits en formato de
        punto flotante. El procesador no sabe sumar "un int con un
        double" directamente: primero hay que llevar ambos al MISMO tipo.

        Además, a veces el programador necesita cambiar un valor de tipo:
        guardar un promedio decimal como entero, usar el código numérico
        de una letra, leer un número que llegó como texto.

        Java divide las conversiones en dos familias, según si hay riesgo
        de perder información:

        - AMPLIACIÓN (widening): de un tipo "chico" a uno "grande".
          Cabe siempre: Java la hace SOLA, sin que escribas nada.
        - REDUCCIÓN (narrowing): de un tipo "grande" a uno "chico".
          Puede no caber: Java EXIGE que la pidas explícitamente con un
          CAST: (tipo) valor.

        Por qué esa regla: Java no quiere que pierdas datos sin darte
        cuenta. Si una conversión puede perder información, tienes que
        escribirla tú, y con eso queda claro que lo hiciste a propósito.
        (JavaScript, en cambio, convierte todo en silencio: "5" * 2 es 10.)
    */

    /*
        =================================================================
        CAPA 2) CONVERSIÓN DE AMPLIACIÓN: AUTOMÁTICA Y (CASI) SEGURA
        =================================================================

        El "camino" de ampliación, de menor a mayor:

            byte -> short -> int -> long -> float -> double
                             ^
                    char ----┘

        Cualquier conversión hacia la DERECHA en ese camino es
        automática: puedes asignar un int a un long, un int a un double,
        un char a un int, sin escribir nada.

        Por qué es segura: el tipo de destino tiene un rango mayor, así
        que cualquier valor del tipo de origen cabe. (Casi siempre: la
        CAPA 3 muestra la excepción.)

        Ocurre en tres situaciones:
        - al asignar:              long big = someInt;
        - al pasar un argumento:   Math.sqrt(16)  (recibe double, le pasas int)
        - al operar tipos mezclados (CAPA 7)
    */
    static double half(double value) {
        return value / 2;
    }

    static boolean wideningExample() {
        int units = 42;
        long asLong = units;          // int -> long: automático
        double asDouble = units;      // int -> double: automático
        char letter = 'A';
        int code = letter;            // char -> int: automático

        double result = half(7);      // el argumento int 7 se amplía a double 7.0

        return asLong == 42L && asDouble == 42.0 && code == 65 && result == 3.5;
    }

    /*
        =================================================================
        CAPA 3) LA EXCEPCIÓN: AMPLIAR A FLOAT O DOUBLE PUEDE PERDER PRECISIÓN
        =================================================================

        Aquí está la letra pequeña. int -> float y long -> float/double
        son conversiones de ampliación (automáticas, sin cast)... pero
        PUEDEN PERDER PRECISIÓN.

        Por qué: un float tiene más RANGO que un int (llega a 10^38), pero
        menos DÍGITOS EXACTOS (unos 7, contra los 10 de un int). Un int
        grande "cabe" en el rango de float, pero sus últimos dígitos se
        redondean. Lo mismo pasa con long (19 dígitos) y double (unos 16).

        Java las permite sin cast porque el valor queda CERCA y nunca
        cambia de magnitud (un número enorme sigue siendo enorme), pero
        no siempre queda EXACTO. Es el único caso donde una conversión
        automática cambia el valor.
    */
    static boolean wideningPrecisionLossExample() {
        int bigInt = 16_777_217;
        float asFloat = bigInt;                          // automático... y pierde el último 1
        boolean intToFloatLost = asFloat == 16_777_216f;

        long bigLong = 9_007_199_254_740_993L;
        double asDouble = bigLong;                        // automático... y pierde precisión
        boolean longToDoubleLost = (long) asDouble == 9_007_199_254_740_992L;

        return intToFloatLost && longToDoubleLost;
    }

    /*
        En la práctica: no guardes identificadores, cantidades de dinero en
        centavos ni timestamps (long) en un double "porque cabe". Los
        lenguajes que solo tienen double, como JavaScript, tienen
        exactamente este problema con números mayores a 2^53.
    */

    /*
        =================================================================
        CAPA 4) CASTING: CONVERSIÓN DE REDUCCIÓN EXPLÍCITA
        =================================================================

        Ir hacia la IZQUIERDA en el camino de la CAPA 2 (double -> int,
        long -> int, int -> byte...) puede perder información, así que
        Java no lo hace solo:

            double price = 19.99;
            int whole = price;           // ERROR: incompatible types:
                                         // possible lossy conversion from double to int

        Para pedirlo escribes un CAST: el tipo destino entre paréntesis,
        delante del valor:

            int whole = (int) price;     // "sé que puedo perder datos; hazlo"

        Al convertir un decimal a entero, la parte decimal se DESCARTA
        (truncar hacia cero): 19.99 se vuelve 19, y -3.99 se vuelve -3.
        No redondea (CAPA 6).

        Cuidado con la PRECEDENCIA: el cast aplica solo a lo que está
        inmediatamente a su derecha (Operators, CAPA 10).
            (int) 7.9 + 0.5     es  7 + 0.5    = 7.5
            (int) (7.9 + 0.5)   es  (int) 8.4  = 8
            (double) a / b      convierte a, y luego divide: división decimal
            (double) (a / b)    divide como int primero: ya perdió decimales
    */
    static boolean castingExample() {
        double price = 19.99;
        int whole = (int) price;
        int negative = (int) -3.99;

        int a = 7;
        int b = 2;
        double rightDivision = (double) a / b;     // 3.5
        double tooLate = (double) (a / b);         // 3.0: la división ya fue entera

        return whole == 19 && negative == -3 && rightDivision == 3.5 && tooLate == 3.0
                && (int) 7.9 + 0.5 == 7.5 && (int) (7.9 + 0.5) == 8;
    }

    /*
        =================================================================
        CAPA 5) QUÉ PASA EXACTAMENTE CUANDO EL VALOR NO CABE
        =================================================================

        El cast nunca lanza una excepción. Si el valor no cabe, Java
        produce un valor DISTINTO, según reglas fijas que conviene
        conocer porque producen errores silenciosos:

        DE ENTERO A ENTERO MÁS CHICO (long -> int, int -> byte...):
        Java se queda solo con los bits de abajo que caben y descarta el
        resto. El resultado "da la vuelta" igual que el desbordamiento
        (PrimitiveTypes, CAPA 5):
            (byte) 200             -> -56
            (int) 3_000_000_000L   -> -1 294 967 296

        DE DECIMAL A ENTERO:
        No da la vuelta: se SATURA en el límite.
            (int) 1e20             -> Integer.MAX_VALUE (2 147 483 647)
            (int) -1e20            -> Integer.MIN_VALUE
            (int) NaN              -> 0

        Por qué esa diferencia: los enteros se convierten cortando bits
        (es lo más rápido para el procesador); para los decimales, Java
        define que se use el entero más cercano posible.

        Si prefieres un error a un valor equivocado:
            Math.toIntExact(longValue)   lanza ArithmeticException si no cabe
    */
    static boolean outOfRangeExample() {
        boolean intToByteWraps = (byte) 200 == -56;
        boolean longToIntWraps = (int) 3_000_000_000L == -1_294_967_296;

        boolean doubleSaturates = (int) 1e20 == Integer.MAX_VALUE && (int) -1e20 == Integer.MIN_VALUE;
        boolean nanBecomesZero = (int) Double.NaN == 0;

        boolean exactDetects;
        try {
            Math.toIntExact(3_000_000_000L);
            exactDetects = false;
        } catch (ArithmeticException e) {
            exactDetects = true;
        }

        return intToByteWraps && longToIntWraps && doubleSaturates && nanBecomesZero && exactDetects;
    }

    /*
        =================================================================
        CAPA 6) TRUNCAR NO ES REDONDEAR
        =================================================================

        (int) solo descarta decimales. Para redondear de verdad hay que
        elegir CÓMO:

        | Método            | Qué hace                         | 2.5  | -2.5 | 2.1 | Devuelve |
        |-------------------|----------------------------------|------|------|-----|----------|
        | (int) x           | descarta decimales (hacia cero)  | 2    | -2   | 2   | int      |
        | Math.round(x)     | al más cercano; .5 hacia arriba  | 3    | -2   | 2   | long     |
        | Math.floor(x)     | hacia abajo (piso)               | 2.0  | -3.0 | 2.0 | double   |
        | Math.ceil(x)      | hacia arriba (techo)             | 3.0  | -2.0 | 3.0 | double   |

        Detalles que confunden:
        - Math.round(-2.5) es -2, no -3: ".5 hacia arriba" significa hacia
          el número MAYOR, y -2 es mayor que -3.
        - Math.round devuelve long (si recibe double), y floor/ceil
          devuelven double. Para guardarlos en un int todavía necesitas un
          cast o Math.toIntExact.
        - Para dinero o reglas de redondeo de negocio (redondeo bancario,
          a dos decimales) se usa BigDecimal con RoundingMode
          (v1_fundamentals/Numbers).

        Ejemplo de uso real: ¿cuántas páginas necesito para mostrar 45
        resultados de 10 en 10? Hay que redondear HACIA ARRIBA.
    */
    static int pagesNeeded(int items, int perPage) {
        return (int) Math.ceil((double) items / perPage);
    }

    static boolean roundingExample() {
        return (int) 2.5 == 2
                && Math.round(2.5) == 3L
                && Math.round(-2.5) == -2L
                && Math.floor(-2.5) == -3.0
                && Math.ceil(2.1) == 3.0
                && pagesNeeded(45, 10) == 5      // con división entera saldría 4
                && pagesNeeded(40, 10) == 4;
    }

    /*
        =================================================================
        CAPA 7) PROMOCIÓN NUMÉRICA: EL TIPO DE UNA OPERACIÓN
        =================================================================

        Cuando un operador aritmético recibe dos tipos distintos, Java
        AMPLÍA uno para que coincidan, con estas reglas en orden:

            1) Si alguno es double  -> los dos se vuelven double
            2) Si no, si alguno es float -> float
            3) Si no, si alguno es long  -> long
            4) Si no                -> los dos se vuelven INT

        La regla 4 sorprende: byte, short y char NUNCA se operan como
        tales. byte + byte da int. Incluso -someByte da int.

        Por qué: los procesadores trabajan de forma nativa con al menos
        32 bits, y la JVM ni siquiera tiene instrucciones para sumar
        bytes. Así es más simple y más rápido.

        Consecuencia que verás pronto:
            byte a = 10, b = 20;
            byte c = a + b;          // ERROR: incompatible types: possible lossy conversion from int to byte
            byte c = (byte) (a + b); // OK, decides tú que cabe
            int c = a + b;           // lo más común: usa int
        (Por eso en PrimitiveTypes se recomendaba int por defecto.)
    */
    static boolean numericPromotionExample() {
        byte a = 10;
        byte b = 20;
        int sum = a + b;                 // byte + byte = int
        byte packed = (byte) (a + b);    // de vuelta a byte, explícitamente

        char letter = 'A';
        int shifted = letter + 2;        // char + int = int (67)

        long bigTimes = 1_000_000L * 3;  // long * int = long
        double mixed = 5 / 2 + 1.5;      // primero 5 / 2 = 2 (int), luego 2 + 1.5 = 3.5 (double)

        return sum == 30 && packed == 30 && shifted == 67 && bigTimes == 3_000_000L && mixed == 3.5;
    }

    /*
        Fíjate en mixed: la promoción se decide en CADA operación por
        separado, no para toda la expresión. 5 / 2 se hace en int (da 2)
        antes de que aparezca el 1.5.
    */

    /*
        =================================================================
        CAPA 8) CONSTANTES QUE CABEN: LA EXCEPCIÓN DE LOS LITERALES
        =================================================================

        Si 10 es un literal int, ¿por qué compila byte b = 10; sin cast?

        Porque Java hace una excepción: si el valor de la derecha es una
        CONSTANTE conocida al compilar (un literal, o una variable final
        inicializada con una constante) y CABE en el tipo de destino, se
        permite la reducción sin cast. El compilador puede comprobar que
        no se pierde nada, así que no te obliga a escribirlo.

            byte ok = 100;           // 100 cabe en byte: compila
            byte tooBig = 200;       // ERROR: incompatible types: possible lossy conversion from int to byte
            final int LIMIT = 50;
            byte fromFinal = LIMIT;  // compila: LIMIT es constante y cabe
            int notFinal = 50;
            byte fromVar = notFinal; // ERROR: notFinal podría cambiar; el compilador no lo garantiza

        Solo aplica a byte, short y char (y a sus wrappers: Byte b = 10 y
        Character c = 65 también compilan), y solo en asignaciones (no al
        pasar argumentos a un método).
    */
    static boolean constantNarrowingExample() {
        byte ok = 100;
        short alsoOk = 30_000;
        final int limit = 50;
        byte fromFinal = limit;
        char fromInt = 65;               // una constante int que cabe en char: 'A'

        return ok == 100 && alsoOk == 30_000 && fromFinal == 50 && fromInt == 'A';
    }

    /*
        =================================================================
        CAPA 9) CHAR Y NÚMEROS
        =================================================================

        Un char es un número Unicode (PrimitiveTypes, CAPA 9), así que se
        convierte a int sin cast, y de int a char con cast. Esto se usa
        MUCHO en algoritmos y entrevistas:

        1) Dígito de texto a número: '7' NO es 7; es el código 55.
           Restando '0' (código 48) obtienes el número: '7' - '0' = 7.
           Funciona porque los dígitos están seguidos en la tabla Unicode.

        2) Posición de una letra en el alfabeto: c - 'a'
           ('a' -> 0, 'b' -> 1, ... 'z' -> 25). Sirve como índice de un
           arreglo de 26 contadores.

        3) Siguiente letra: (char) (c + 1). Hace falta el cast porque
           c + 1 es int (CAPA 7).
    */
    static int[] letterCounts(String lowercaseWord) {
        int[] counts = new int[26];
        for (char c : lowercaseWord.toCharArray()) {
            counts[c - 'a']++;                 // 'a' cuenta en la posición 0, 'b' en la 1...
        }
        return counts;
    }

    static boolean charConversionsExample() {
        char digit = '7';
        int value = digit - '0';
        boolean digitIsNotTheNumber = digit == 55;

        int[] counts = letterCounts("banana");
        char next = (char) ('a' + 1);

        return value == 7 && digitIsNotTheNumber
                && counts[0] == 3 && counts['n' - 'a'] == 2 && counts['b' - 'a'] == 1
                && next == 'b'
                && Character.getNumericValue('7') == 7;   // alternativa más legible al - '0'
    }

    /*
        =================================================================
        CAPA 10) TEXTO Y NÚMEROS: NO SE CASTEA, SE CONVIERTE
        =================================================================

        Un String no es un número: es un objeto que contiene caracteres.
        El cast no sirve:
            int n = (int) "42";   // ERROR: incompatible types: String cannot be converted to int

        Por qué no: un cast solo cambia cómo se ve un valor que YA es
        compatible. Convertir "42" a 42 requiere LEER el texto carácter
        por carácter e interpretarlo, y puede FALLAR ("hola" no es un
        número). Eso es trabajo de un método:

        | De -> A          | Cómo                                   |
        |------------------|----------------------------------------|
        | String -> int    | Integer.parseInt("42")                 |
        | String -> long   | Long.parseLong("42")                   |
        | String -> double | Double.parseDouble("3.14")             |
        | número -> String | String.valueOf(42)  o  Integer.toString(42) |

        Si el texto no es un número válido, parseInt lanza
        NumberFormatException. Cuando el texto viene de un usuario o de
        un archivo, SIEMPRE puede venir mal, así que hay que manejarlo
        (v3_exceptions/).

        Diferencia con JavaScript: en JS, "5" * 2 da 10 y "5" + 2 da "52"
        por conversiones automáticas. En Java, solo + con un String
        convierte a texto (Operators, CAPA 4); cualquier otra mezcla de
        texto y número no compila.
    */
    static int parseQuantityOrDefault(String text, int defaultValue) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    static boolean stringConversionsExample() {
        int quantity = Integer.parseInt("42");
        double price = Double.parseDouble("19.99");
        String asText = String.valueOf(3.5);

        return quantity == 42
                && price == 19.99
                && asText.equals("3.5")
                && parseQuantityOrDefault(" 7 ", 1) == 7     // trim quita los espacios
                && parseQuantityOrDefault("siete", 1) == 1;  // texto inválido: valor por defecto
    }

    /*
        =================================================================
        CAPA 11) Y LAS REFERENCIAS, BOOLEAN Y WRAPPERS
        =================================================================

        BOOLEAN: no se convierte a ni desde ningún número, ni con cast.
            int n = (int) true;   // ERROR: incompatible types: boolean cannot be converted to int
        Si necesitas 1/0: flag ? 1 : 0 (v1_fundamentals/Conditionals).

        REFERENCIAS (objetos): el cast de objetos es otra cosa. No
        convierte el valor, solo cambia el tipo con que VES un objeto
        (upcasting y downcasting). Está explicado a fondo en
        Polymorphism, CAPAS 5 y 7.

        WRAPPERS: la conversión automática entre int e Integer (boxing y
        unboxing) se estudia en v1_fundamentals/Wrappers.
    */

    /*
        =================================================================
        CAPA 12) ERRORES COMUNES
        =================================================================

        1) Creer que toda conversión automática es exacta: int -> float y
           long -> double pueden perder dígitos (CAPA 3).

        2) Esperar que (int) redondee. Trunca (CAPAS 4 y 6).

        3) (double) (a / b) en lugar de (double) a / b (CAPA 4).

        4) Castear valores que no caben sin revisarlos: dan la vuelta o se
           saturan en silencio (CAPA 5).

        5) Olvidar que Math.round devuelve long y floor/ceil double
           (CAPA 6).

        6) Esperar que byte + byte sea byte (CAPA 7).

        7) Creer que la promoción se decide para toda la expresión y no
           operación por operación (CAPA 7).

        8) Usar '7' como si fuera el número 7 (CAPA 9).

        9) Intentar (int) "42" en lugar de Integer.parseInt, o no manejar
           NumberFormatException con texto que viene de fuera (CAPA 10).

        10) Buscar convertir boolean en número con cast (CAPA 11).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Ampliación (byte -> short -> int -> long -> float -> double, y
          char -> int): automática. Pero a float/double puede perder
          precisión.
        - Reducción: exige cast (tipo) valor, porque puede perder datos.
        - Decimal -> entero: trunca hacia cero; si no cabe, se satura.
          Entero -> entero más chico: da la vuelta. Nunca hay excepción;
          usa Math.toIntExact si la quieres.
        - Redondear: Math.round (long), floor y ceil (double).
        - Promoción: double > float > long > int; byte, short y char se
          operan como int. Se decide en cada operación.
        - Constantes que caben se asignan a byte/short/char sin cast.
        - char: '7' - '0' = 7; c - 'a' = posición en el alfabeto.
        - Texto <-> número: parseInt/parseDouble y String.valueOf, nunca
          cast. parse puede lanzar NumberFormatException.
        - boolean no se convierte a números. El cast de objetos es otro
          tema (Polymorphism).
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
