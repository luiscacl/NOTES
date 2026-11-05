package v1_fundamentals.part1_first_steps;

/*
    =====================================================================
    TIPOS PRIMITIVOS — DE CERO A NIVEL TÉCNICO
    =====================================================================

    Ruta de v1_fundamentals/: guía 2 de 14  (part1_first_steps)
    Anterior:  part1_first_steps/HowJavaWorks
    Siguiente: part1_first_steps/Variables

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.

    Índice:
        CAPA 1)  Qué es un tipo y por qué Java los revisa antes de ejecutar
        CAPA 2)  Bits: por qué cada tipo tiene un rango
        CAPA 3)  Los 8 tipos primitivos
        CAPA 4)  Enteros: byte, short, int, long y sus literales
        CAPA 5)  Desbordamiento: cuando un entero "da la vuelta"
        CAPA 6)  Decimales: float y double
        CAPA 7)  Por qué 0.1 + 0.2 no es 0.3
        CAPA 8)  Valores especiales: infinito y NaN
        CAPA 9)  char: un carácter que en realidad es un número
        CAPA 10) boolean: verdadero o falso, nada más
        CAPA 11) Cómo elegir el tipo correcto
        CAPA 12) Errores comunes
        RESUMEN FINAL
*/
public class PrimitiveTypes {

    public static void main(String[] args) {
        check(staticTypingExample(), "CAPA 1");
        check(bitsAndRangesExample(), "CAPA 2");
        check(sizesExample(), "CAPA 3");
        check(integerLiteralsExample(), "CAPA 4");
        check(overflowExample(), "CAPA 5");
        check(floatingPointExample(), "CAPA 6");
        check(binaryFractionsExample(), "CAPA 7");
        check(specialValuesExample(), "CAPA 8");
        check(charExample(), "CAPA 9");
        check(booleanExample(), "CAPA 10");
        System.out.println("PrimitiveTypes: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) QUÉ ES UN TIPO Y POR QUÉ JAVA LOS REVISA ANTES DE EJECUTAR
        =================================================================

        Para la computadora, todo es una secuencia de ceros y unos. El
        mismo patrón de bits puede significar el número 65, la letra 'A'
        o parte de un decimal. El TIPO es lo que le dice al programa cómo
        INTERPRETAR esos bits y qué se puede hacer con ellos:
        - qué valores puede guardar una variable,
        - qué operaciones tienen sentido (sumar números, sí; sumar
          verdadero + falso, no),
        - cuánta memoria ocupa.

        Sintaxis para declarar una variable con su tipo:

            int edad = 30;
            ^^^ ^^^^   ^^
            tipo nombre valor
            "declaro una variable llamada edad, de tipo int, y le guardo 30"

        TIPADO ESTÁTICO (Java) vs TIPADO DINÁMICO (JavaScript)
        - En JavaScript, el tipo lo tiene el VALOR y una variable puede
          guardar cualquier cosa en cualquier momento:
              let x = 5;  x = "hola";       // válido en JavaScript
        - En Java, el tipo lo tiene la VARIABLE, se decide al escribir el
          código y no cambia nunca:
              int x = 5;  x = "hola";       // ERROR: incompatible types:
                                            // String cannot be converted to int

        Por qué Java eligió revisarlo todo antes de ejecutar:
        1) Los errores aparecen al COMPILAR, con la línea exacta, y no
           cuando un usuario ya está usando el programa.
        2) El código se autodocumenta: al leer int edad sabes qué esperar.
        3) Velocidad: como la JVM sabe de antemano que algo es un int, usa
           directamente la instrucción del procesador para enteros, sin
           revisar el tipo en cada operación.
        4) Los editores pueden autocompletar y avisarte mientras escribes.
    */
    static boolean staticTypingExample() {
        int age = 30;
        double price = 19.99;
        boolean active = true;
        char initial = 'L';

        // El compilador sabe el tipo de cada variable y solo permite operaciones válidas.
        int nextYearAge = age + 1;
        double withTax = price * 1.16;

        return nextYearAge == 31 && withTax > price && active && initial == 'L';
    }

    /*
        =================================================================
        CAPA 2) BITS: POR QUÉ CADA TIPO TIENE UN RANGO
        =================================================================

        Un BIT es la unidad mínima de información: vale 0 o 1. Un BYTE son
        8 bits.

        ¿Cuántos valores distintos caben en N bits? Cada bit duplica las
        combinaciones:
            1 bit  -> 2 valores       (0, 1)
            2 bits -> 4 valores       (00, 01, 10, 11)
            8 bits -> 256 valores
            N bits -> 2 elevado a N valores

        Los enteros de Java tienen SIGNO (pueden ser negativos), así que
        la mitad de las combinaciones se usa para negativos y la otra
        mitad para el cero y los positivos. Con 8 bits (un byte):
            256 valores -> de -128 a 127
        (127 y no 128 porque el cero ocupa uno de los "positivos").

        La forma en que se guardan los negativos se llama "complemento a
        dos". No necesitas dominarla, pero explica dos cosas que verás:
        - el negativo más grande en magnitud siempre tiene uno más que el
          positivo más grande (-128 vs 127),
        - al pasarte del máximo, "das la vuelta" al mínimo (CAPA 5).

        Por eso los rangos de Java no son números arbitrarios: salen
        directamente de cuántos bits usa cada tipo. Y, a diferencia de C,
        en Java esos tamaños son IGUALES en cualquier computadora: un int
        siempre son 32 bits. Es parte de "escribe una vez, corre en
        todos lados".
    */
    static boolean bitsAndRangesExample() {
        // Cada tipo numérico tiene constantes con su mínimo, máximo y tamaño en bits.
        boolean byteRange = Byte.MIN_VALUE == -128 && Byte.MAX_VALUE == 127 && Byte.SIZE == 8;

        // 8 bits = 2^8 = 256 valores posibles.
        boolean combinations = (Byte.MAX_VALUE - Byte.MIN_VALUE + 1) == 256;

        // Puedes ver los bits de un número:
        boolean binaryOfFive = Integer.toBinaryString(5).equals("101");   // 4 + 0 + 1

        return byteRange && combinations && binaryOfFive;
    }

    /*
        =================================================================
        CAPA 3) LOS 8 TIPOS PRIMITIVOS
        =================================================================

        Java tiene exactamente 8 tipos PRIMITIVOS. "Primitivo" significa
        que guarda el VALOR directamente, no es un objeto: no tiene
        métodos (no existe 5.length()), no puede ser null, y es muy
        rápido y ligero. Todo lo demás (String, List, tus clases) son
        tipos de REFERENCIA (v1_fundamentals/ReferencesAndNull).

        | Tipo    | Bits | Rango aproximado                 | Por defecto | Para qué              |
        |---------|------|----------------------------------|-------------|-----------------------|
        | byte    | 8    | -128 a 127                       | 0           | datos binarios        |
        | short   | 16   | -32 768 a 32 767                 | 0           | casi nunca            |
        | int     | 32   | ±2 147 483 647 (±2.1 mil millones)| 0          | ENTEROS, por defecto  |
        | long    | 64   | ±9.2 trillones (9.2 x 10^18)     | 0L          | enteros muy grandes   |
        | float   | 32   | ±3.4 x 10^38, ~7 dígitos exactos | 0.0f        | casi nunca            |
        | double  | 64   | ±1.8 x 10^308, ~15 dígitos exactos| 0.0        | DECIMALES, por defecto|
        | char    | 16   | 0 a 65 535 (un carácter Unicode) | '\u0000'    | un carácter           |
        | boolean | -    | true o false                     | false       | sí / no               |

        Los valores por defecto aplican a CAMPOS de una clase y a
        posiciones de arreglos. Las variables locales no tienen valor por
        defecto: hay que asignarlas antes de usarlas (ClassesAndObjects,
        CAPA 3).

        Si vienes de JavaScript: JS tiene UN solo tipo numérico (number,
        que es un double de 64 bits) más BigInt. Java distingue enteros de
        decimales y de distintos tamaños, porque cada uno tiene ventajas
        reales de exactitud, memoria y velocidad.
    */
    static boolean sizesExample() {
        return Short.SIZE == 16
                && Integer.SIZE == 32
                && Long.SIZE == 64
                && Float.SIZE == 32
                && Double.SIZE == 64
                && Character.SIZE == 16
                && Integer.MAX_VALUE == 2_147_483_647;
    }

    /*
        =================================================================
        CAPA 4) ENTEROS: BYTE, SHORT, INT, LONG Y SUS LITERALES
        =================================================================

        Un LITERAL es un valor escrito directamente en el código: 42,
        3.14, 'A', true.

        TIPO DE LOS LITERALES ENTEROS
        Un número entero escrito en el código es un int, salvo que lleve
        el sufijo L, que lo hace long:
            int  count = 42;
            long population = 8_100_000_000L;
        Si escribes un entero que no cabe en int sin la L, no compila,
        aunque lo asignes a un long:
            long big = 3_000_000_000;   // ERROR: integer number too large
        Por qué: el literal se evalúa como int ANTES de asignarse, y no
        cabe. Usa L MAYÚSCULA: la l minúscula se confunde con el 1
        (10l parece "101").

        FORMAS DE ESCRIBIR UN ENTERO
            1_000_000      decimal, con guiones bajos para leerlo mejor
                           (se ignoran: es lo mismo que 1000000)
            0xFF           hexadecimal (base 16): 255. Común para colores,
                           permisos y datos binarios
            0b1010         binario (base 2): 10
            017            OCTAL (base 8): 15. ¡Un cero al inicio cambia
                           el valor! Es una trampa clásica.
    */
    static boolean integerLiteralsExample() {
        int million = 1_000_000;
        int hex = 0xFF;
        int binary = 0b1010;
        int octalTrap = 010;   // parece "diez", pero el 0 inicial lo hace octal: vale 8
        long worldPopulation = 8_100_000_000L;

        return million == 1000000
                && hex == 255
                && binary == 10
                && octalTrap == 8
                && worldPopulation > Integer.MAX_VALUE;
    }

    /*
        Cuándo usar cada entero:
        - int: SIEMPRE por defecto. Es el tamaño natural para el
          procesador, y además las operaciones con byte y short se hacen
          como int de todas formas (ConversionsAndCasting).
        - long: cuando el valor puede pasar de 2.1 mil millones:
          milisegundos desde 1970 (System.currentTimeMillis() devuelve
          long), ids de bases de datos grandes, población mundial,
          dinero guardado en centavos.
        - byte: para datos binarios crudos (contenido de un archivo, una
          imagen, bytes de red), normalmente en arreglos byte[].
        - short: casi nunca; solo para ahorrar memoria en arreglos
          enormes.
    */

    /*
        =================================================================
        CAPA 5) DESBORDAMIENTO: CUANDO UN ENTERO "DA LA VUELTA"
        =================================================================

        ¿Qué pasa si le sumas 1 al int más grande? No hay error ni
        excepción: el resultado "da la vuelta" al número más NEGATIVO,
        como el odómetro de un coche que pasa de 999999 a 000000. Se
        llama DESBORDAMIENTO (overflow).

        Por qué Java no avisa: verificar cada suma costaría tiempo en
        cada operación, y el procesador ya funciona así. Java eligió
        velocidad y deja la responsabilidad al programador.

        Esto produce errores LÓGICOS reales (HowJavaWorks, CAPA 9),
        porque todo compila y corre. El clásico: calcular milisegundos de
        un año con int.
    */
    static boolean overflowExample() {
        boolean wrapsAround = Integer.MAX_VALUE + 1 == Integer.MIN_VALUE;

        // Cada factor cabe en un int, pero el producto (31 536 000 000) no.
        int millisPerYearWrong = 365 * 24 * 60 * 60 * 1000;   // compila sin aviso... y da 1 471 228 928
        long millisPerYearRight = 365L * 24 * 60 * 60 * 1000; // con 365L, todo se calcula como long

        // Si prefieres que te avise, las operaciones "Exact" lanzan excepción al desbordarse.
        boolean exactDetects;
        try {
            Math.multiplyExact(365 * 24 * 60 * 60, 1000);
            exactDetects = false;
        } catch (ArithmeticException e) {
            exactDetects = true;
        }

        return wrapsAround
                && millisPerYearWrong == 1_471_228_928
                && millisPerYearRight == 31_536_000_000L
                && exactDetects;
    }

    /*
        Fíjate en el detalle de millisPerYearRight: no basta con guardar
        el resultado en un long. En
            long x = 365 * 24 * 60 * 60 * 1000;
        la multiplicación se hace TODA en int (porque todos los números
        son int), se desborda, y el resultado ya roto se guarda en el
        long. Hay que hacer que la operación MISMA sea long, poniendo la L
        en el primer número.

        Defensas:
        - Usa long cuando el resultado pueda crecer.
        - Math.addExact, subtractExact, multiplyExact, toIntExact:
          lanzan ArithmeticException en vez de dar un valor equivocado.
        - Para enteros sin límite: BigInteger (v1_fundamentals/Numbers).
    */

    /*
        =================================================================
        CAPA 6) DECIMALES: FLOAT Y DOUBLE
        =================================================================

        Los números con decimales se llaman de PUNTO FLOTANTE: guardan
        unos dígitos significativos y un exponente, como la notación
        científica (6.02 x 10^23). Eso les permite representar números
        enormes y diminutos, pero con un número FIJO de dígitos exactos.

        - double: 64 bits, unos 15-16 dígitos significativos. Es el tipo
          decimal POR DEFECTO.
        - float: 32 bits, unos 7 dígitos. Solo tiene sentido para ahorrar
          memoria en cantidades enormes de datos (gráficos, sensores).

        Literales:
            double price = 19.99;      // un decimal escrito es double
            float ratio = 0.75f;       // float NECESITA la f
            double distance = 1.5e3;   // notación científica: 1500.0
            float wrong = 1.5;         // ERROR: incompatible types:
                                       // possible lossy conversion from double to float
        Por qué la f es obligatoria: 1.5 es un double, y meterlo en un
        float podría perder precisión. Java no lo hace en silencio; te
        obliga a decir explícitamente "sí, quiero un float".

        Ejemplo de lo que significan "7 dígitos": un float no puede
        distinguir 16 777 216 de 16 777 217.
    */
    static boolean floatingPointExample() {
        double scientific = 1.5e3;
        float ratio = 0.75f;

        float bigFloat = 16_777_217f;   // no cabe exacto en float: se guarda como 16 777 216
        double bigDouble = 16_777_217d; // double sí lo guarda exacto

        return scientific == 1500.0
                && ratio == 0.75f
                && bigFloat == 16_777_216f
                && bigDouble == 16_777_217d;
    }

    /*
        =================================================================
        CAPA 7) POR QUÉ 0.1 + 0.2 NO ES 0.3
        =================================================================

        Esto pasa en Java, JavaScript, Python y prácticamente todos los
        lenguajes, porque viene del estándar que usan los procesadores
        (IEEE 754).

        La causa: los decimales se guardan en BINARIO. Igual que 1/3 en
        decimal es 0.3333... infinito y hay que cortarlo en algún punto,
        0.1 en binario es 0.0001100110011... infinito. Se guarda la
        aproximación más cercana que cabe en 64 bits, y esas pequeñas
        diferencias aparecen al operar.

        Consecuencias prácticas:
        1) NUNCA compares decimales con ==. Compara si la diferencia es
           menor que un margen pequeño (tolerancia).
        2) NUNCA uses double ni float para DINERO. Un centavo de error
           multiplicado por millones de operaciones es un problema real.
           Para dinero existe BigDecimal (v1_fundamentals/Numbers), que
           trabaja en base 10 de forma exacta.
    */
    static boolean binaryFractionsExample() {
        double sum = 0.1 + 0.2;

        boolean notExact = sum != 0.3;
        boolean actualValue = sum == 0.30000000000000004;
        boolean closeEnough = Math.abs(sum - 0.3) < 1e-9;   // la forma correcta de comparar

        // Sumar 10 centavos diez veces con double no da 1.00 exacto.
        double cents = 0;
        for (int i = 0; i < 10; i++) {
            cents += 0.10;
        }

        return notExact && actualValue && closeEnough && cents != 1.0;
    }

    /*
        =================================================================
        CAPA 8) VALORES ESPECIALES: INFINITO Y NAN
        =================================================================

        Los tipos de punto flotante tienen tres valores especiales:
            Infinity    infinito positivo
            -Infinity   infinito negativo
            NaN         "Not a Number": resultado indefinido

        Diferencia IMPORTANTE con los enteros:
        - Dividir un ENTERO entre 0 lanza ArithmeticException.
        - Dividir un DECIMAL entre 0 NO lanza nada: da Infinity (o NaN si
          es 0.0 / 0.0). El programa sigue con un valor que contamina
          todos los cálculos siguientes.
        Por qué la diferencia: los enteros no tienen forma de representar
        "infinito", así que la única opción es fallar. El estándar de
        punto flotante sí la tiene.

        NaN tiene una propiedad extraña: NO es igual a nada, ni a sí
        mismo. NaN == NaN es false. Para detectarlo se usa Double.isNaN.
    */
    static boolean specialValuesExample() {
        double positiveInfinity = 1.0 / 0;
        double notANumber = 0.0 / 0;

        boolean infinityNoException = positiveInfinity == Double.POSITIVE_INFINITY;
        boolean nanNotEqualToItself = notANumber != notANumber;   // la única forma en que x != x
        boolean detectNan = Double.isNaN(notANumber);

        return infinityNoException && nanNotEqualToItself && detectNan && Double.isInfinite(positiveInfinity);
    }

    /*
        =================================================================
        CAPA 9) CHAR: UN CARÁCTER QUE EN REALIDAD ES UN NÚMERO
        =================================================================

        char guarda UN carácter, entre comillas SIMPLES:
            char letter = 'A';      // char: comillas simples, un carácter
            String word = "A";      // String: comillas dobles, texto (otra cosa)

        Por dentro, un char es un NÚMERO de 16 bits sin signo (0 a 65 535):
        el código del carácter en la tabla UNICODE, el estándar mundial
        que asigna un número a cada carácter de cada idioma. 'A' es 65,
        'a' es 97, 'ñ' es 241.

        Por eso se puede hacer aritmética con char: 'A' + 1 da 66 (un
        int), y para volver a char hay que convertirlo.

        SECUENCIAS DE ESCAPE: caracteres que no se pueden escribir
        directamente entre comillas:
            '\n'  salto de línea        '\t'  tabulador
            '\''  comilla simple        '\\'  diagonal invertida
            '\u00F1'  cualquier carácter por su código Unicode (ñ)

        LIMITACIÓN TÉCNICA: Unicode creció más allá de 65 536 caracteres
        (los emojis, muchos símbolos). Esos no caben en un solo char: se
        guardan como DOS char ("par sustituto"). Por eso un emoji tiene
        length() 2 en un String. Para texto, usa String; char sirve para
        trabajar carácter por carácter (v1_fundamentals/Strings).
    */
    static boolean charExample() {
        char letter = 'A';
        int code = letter;                 // char -> int: el número Unicode
        char next = (char) (letter + 1);   // int -> char: hay que convertirlo explícitamente
        char enye = 'ñ';

        letter++;                          // ++ sí funciona directo sobre un char

        String emoji = "😀";     // 😀: un solo símbolo, DOS char
        boolean emojiNeedsTwoChars = emoji.length() == 2 && emoji.codePointCount(0, emoji.length()) == 1;

        return code == 65 && next == 'B' && letter == 'B' && enye == 'ñ' && emojiNeedsTwoChars;
    }

    /*
        =================================================================
        CAPA 10) BOOLEAN: VERDADERO O FALSO, NADA MÁS
        =================================================================

        boolean tiene exactamente dos valores: true y false. Es el
        resultado de las comparaciones (edad >= 18) y lo que necesitan
        if, while y for.

        DIFERENCIA CLAVE CON JAVASCRIPT
        En JavaScript existen valores "truthy" y "falsy": if (0), if (""),
        if (null) se evalúan como falso. En Java NO: un boolean no es un
        número, y un número no es un boolean.

            int count = 3;
            if (count) { ... }         // ERROR: incompatible types:
                                       // int cannot be converted to boolean
            if (count > 0) { ... }     // correcto: la comparación da un boolean

        Por qué Java lo prohíbe: if (x = 5) en lugar de if (x == 5) es uno
        de los errores más comunes en C y JavaScript. En Java no compila,
        porque x = 5 da un int, no un boolean.

        Convención de nombres: que se lean como pregunta de sí o no:
        isActive, hasStock, canEdit, shouldRetry.
    */
    static boolean booleanExample() {
        int count = 3;
        boolean hasItems = count > 0;
        boolean isEmpty = !hasItems;

        return hasItems && !isEmpty;
    }

    /*
        =================================================================
        CAPA 11) CÓMO ELEGIR EL TIPO CORRECTO
        =================================================================

        | Necesitas guardar...                          | Usa          |
        |-----------------------------------------------|--------------|
        | Un entero normal (edad, cantidad, índice)     | int          |
        | Un entero que puede pasar de 2.1 mil millones | long         |
        | Milisegundos, timestamps                      | long         |
        | Un decimal para medidas o cálculos científicos| double       |
        | DINERO                                        | BigDecimal (NUNCA double) |
        | Un entero sin límite de tamaño                | BigInteger   |
        | Sí / no                                       | boolean      |
        | Texto, aunque sea de una letra                | String       |
        | Un carácter para procesar texto letra a letra | char         |
        | Datos binarios crudos                         | byte[]       |

        Regla general: int y double por defecto. Elige otro solo cuando
        tengas una razón concreta (rango, exactitud o memoria).
    */

    /*
        =================================================================
        CAPA 12) ERRORES COMUNES
        =================================================================

        1) Olvidar la L en literales long grandes, o la f en float
           (CAPAS 4 y 6).

        2) Escribir números con un 0 al inicio "para alinear": 010 vale 8
           (CAPA 4).

        3) Calcular con int algo que se desborda y guardarlo en long,
           creyendo que eso lo arregla (CAPA 5).

        4) Comparar decimales con == (CAPA 7).

        5) Usar double o float para dinero (CAPA 7).

        6) Esperar una excepción al dividir un decimal entre cero (CAPA 8).

        7) Comparar con NaN usando == (CAPA 8).

        8) Confundir 'A' (char) con "A" (String) (CAPA 9).

        9) Usar números como condiciones: if (count) no compila (CAPA 10).

        10) Usar byte o short "para ahorrar memoria" en variables sueltas.
            No ahorra nada útil y complica las operaciones (CAPA 4).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - El tipo dice cómo interpretar los bits y qué operaciones valen.
          Java lo revisa al COMPILAR (tipado estático).
        - N bits = 2^N valores. Los rangos salen de ahí, y son iguales en
          toda computadora.
        - 8 primitivos: byte, short, int, long, float, double, char,
          boolean. Guardan el valor directo; no son objetos.
        - Enteros: int por defecto; long con L; también hex, binario y
          (cuidado) octal con 0 inicial.
        - Desbordamiento: los enteros dan la vuelta sin avisar. Usa long o
          Math.xxxExact.
        - double por defecto para decimales; float necesita f.
        - Los decimales binarios son aproximados: nunca == ni dinero.
        - Decimal entre cero: Infinity o NaN, sin excepción. NaN != NaN.
        - char es un número Unicode de 16 bits; los emojis ocupan dos.
        - boolean: solo true/false; los números no son condiciones.
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
