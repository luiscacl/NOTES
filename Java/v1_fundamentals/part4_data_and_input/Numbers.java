package v1_fundamentals.part4_data_and_input;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HexFormat;
import java.util.List;
import java.util.Random;

/*
    =====================================================================
    NÚMEROS: MATH, ALEATORIOS, BIGDECIMAL Y BIGINTEGER — DE CERO A TÉCNICO
    =====================================================================

    Ruta de v1_fundamentals/: guía 13 de 14  (part4_data_and_input)
    Anterior:  part4_data_and_input/Arrays
    Siguiente: part4_data_and_input/InputAndOutput

    Requisito: PrimitiveTypes (rangos, desbordamiento, por qué 0.1 + 0.2
    no es 0.3) y ConversionsAndCasting (round, floor, ceil).

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.

    Índice:
        CAPA 1)  Las herramientas numéricas de Java
        CAPA 2)  La clase Math y sus trampas
        CAPA 3)  Comparar decimales: tolerancia absoluta y relativa
        CAPA 4)  Números aleatorios: Random, semillas y SecureRandom
        CAPA 5)  BigDecimal: decimales exactos
        CAPA 6)  Dividir con BigDecimal
        CAPA 7)  Modos de redondeo (RoundingMode)
        CAPA 8)  Dinero en la práctica
        CAPA 9)  BigInteger: enteros sin límite
        CAPA 10) Cómo elegir el tipo numérico
        CAPA 11) Errores comunes
        RESUMEN FINAL
*/
public class Numbers {

    public static void main(String[] args) {
        check(mathExample(), "CAPA 2");
        check(toleranceExample(), "CAPA 3");
        check(randomExample(), "CAPA 4");
        check(bigDecimalBasicsExample(), "CAPA 5");
        check(divisionExample(), "CAPA 6");
        check(roundingModesExample(), "CAPA 7");
        check(moneyExample(), "CAPA 8");
        check(bigIntegerExample(), "CAPA 9");
        System.out.println("Numbers: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) LAS HERRAMIENTAS NUMÉRICAS DE JAVA
        =================================================================

        Los primitivos (int, long, double) son rápidos, pero tienen
        límites: los enteros se desbordan y los decimales son aproximados
        (PrimitiveTypes, CAPAS 5 y 7). Para todo lo demás, Java trae:

        | Herramienta      | Para qué                                              |
        |------------------|-------------------------------------------------------|
        | Math             | funciones matemáticas: raíz, potencia, valor absoluto |
        | Random y otras   | números aleatorios                                    |
        | BigDecimal       | decimales EXACTOS: dinero, cantidades de negocio      |
        | BigInteger       | enteros de CUALQUIER tamaño                           |

        La idea que recorre toda la guía: los primitivos son rápidos pero
        aproximados o limitados; BigDecimal y BigInteger son exactos pero
        más lentos. Se elige según qué importa más en cada caso.
    */

    /*
        =================================================================
        CAPA 2) LA CLASE MATH Y SUS TRAMPAS
        =================================================================

        Math es una clase utilitaria (Static, CAPA 5): todo es static.

            Math.abs(x)          valor absoluto
            Math.max(a, b), Math.min(a, b)
            Math.pow(base, exp)  potencia (devuelve double)
            Math.sqrt(x), Math.cbrt(x)   raíz cuadrada y cúbica
            Math.hypot(a, b)     hipotenusa: raíz de a² + b², sin desbordarse
            Math.clamp(x, min, max)  (Java 21) limita x al rango [min, max]
            Math.PI, Math.E
            round, floor, ceil    (ConversionsAndCasting, CAPA 6)
            floorDiv, floorMod    (Operators, CAPAS 2 y 3)
            addExact, multiplyExact, toIntExact...  (PrimitiveTypes, CAPA 5)

        TRAMPA 1: Math.abs(Integer.MIN_VALUE) es NEGATIVO.
        El rango de int va de -2 147 483 648 a 2 147 483 647 (PrimitiveTypes,
        CAPA 2): el positivo 2 147 483 648 no existe, así que el resultado
        se desborda y vuelve a ser el mismo negativo. Pasa en código real
        al calcular Math.abs(hashCode()) para elegir una posición. Si te
        importa, Math.absExact lanza una excepción en ese caso.

        TRAMPA 2: Math.pow trabaja en double. Para potencias enteras
        GRANDES el resultado ya no es exacto (un double tiene unos 16
        dígitos exactos): 3 elevado a 39 tiene 19 dígitos.
        Para potencias enteras exactas: multiplicar en un ciclo con long
        (si cabe) o BigInteger.pow (CAPA 9).
    */
    static long powExact(long base, int exponent) {
        long result = 1;
        for (int i = 0; i < exponent; i++) {
            result = Math.multiplyExact(result, base);   // lanza excepción si se desborda
        }
        return result;
    }

    static boolean mathExample() {
        boolean basics = Math.max(3, 9) == 9
                && Math.sqrt(16) == 4.0
                && Math.cbrt(27) == 3.0
                && Math.hypot(3, 4) == 5.0
                && Math.clamp(15, 0, 10) == 10;   // 15 queda limitado al máximo, 10

        boolean absTrap = Math.abs(Integer.MIN_VALUE) == Integer.MIN_VALUE;   // ¡sigue siendo negativo!

        boolean absExactDetects;
        try {
            Math.absExact(Integer.MIN_VALUE);
            absExactDetects = false;
        } catch (ArithmeticException e) {
            absExactDetects = true;
        }

        long viaPow = (long) Math.pow(3, 39);
        long viaLoop = powExact(3, 39);
        boolean powIsApproximate = viaPow != viaLoop && viaLoop == 4_052_555_153_018_976_267L;

        return basics && absTrap && absExactDetects && powIsApproximate;
    }

    /*
        =================================================================
        CAPA 3) COMPARAR DECIMALES: TOLERANCIA ABSOLUTA Y RELATIVA
        =================================================================

        Ya sabes que los decimales no se comparan con == (PrimitiveTypes,
        CAPA 7), sino con una tolerancia:
            Math.abs(a - b) < 1e-9
        Eso es una tolerancia ABSOLUTA, y falla con números GRANDES.

        Por qué: un double guarda unos 16 dígitos significativos. Con
        números alrededor de 10^20, la distancia entre dos doubles
        consecutivos ya es de miles (la "unidad en el último lugar", ulp).
        Dos cálculos que deberían dar lo mismo pueden diferir en miles, y
        una tolerancia de 1e-9 diría que son distintos.

        La solución robusta es una tolerancia RELATIVA: ¿la diferencia es
        pequeña COMPARADA con el tamaño de los números?
            Math.abs(a - b) <= tolerancia * Math.max(Math.abs(a), Math.abs(b))

        Para ORDENAR doubles, usa Double.compare: maneja bien NaN y -0.0.
    */
    static boolean closeAbsolute(double a, double b) {
        return Math.abs(a - b) < 1e-9;
    }

    static boolean closeRelative(double a, double b) {
        return Math.abs(a - b) <= 1e-12 * Math.max(Math.abs(a), Math.abs(b));
    }

    static boolean toleranceExample() {
        double big = 1e20;
        double nearlySame = big + 1e5;   // diferencia relativa de una parte en mil billones

        boolean smallNumbersBothWork = closeAbsolute(0.1 + 0.2, 0.3) && closeRelative(0.1 + 0.2, 0.3);
        boolean absoluteFailsForBig = !closeAbsolute(big, nearlySame) && closeRelative(big, nearlySame);
        boolean ulpIsLarge = Math.ulp(big) > 1000;   // distancia al siguiente double posible

        return smallNumbersBothWork && absoluteFailsForBig && ulpIsLarge
                && Double.compare(1.5, 2.5) < 0;
    }

    /*
        =================================================================
        CAPA 4) NÚMEROS ALEATORIOS: RANDOM, SEMILLAS Y SECURERANDOM
        =================================================================

        Una computadora no puede "inventar" números al azar: sigue una
        fórmula. Los números que produce parecen aleatorios, pero son
        PSEUDOALEATORIOS: a partir de un valor inicial (la SEMILLA, seed),
        la fórmula genera SIEMPRE la misma secuencia.

        - new Random()       semilla distinta cada vez: secuencias distintas.
        - new Random(42)     semilla fija: SIEMPRE la misma secuencia.
          Parece un defecto, pero es muy útil para pruebas y simulaciones:
          puedes reproducir exactamente el mismo resultado.

        Rangos (la trampa de siempre: el límite superior se EXCLUYE):
            random.nextInt(10)         de 0 a 9
            random.nextInt(1, 7)       de 1 a 6 (Java 17) -> un dado
            random.nextDouble()        de 0.0 a 0.999...
            Math.random()              igual que nextDouble(), sin crear un Random

        Cuál usar:
        - Random: lo normal (juegos, simulaciones, datos de prueba).
        - ThreadLocalRandom.current(): cuando varios hilos generan números
          a la vez (v13_concurrency/), para no competir por el mismo Random.
        - SecureRandom: para SEGURIDAD (contraseñas temporales, tokens,
          códigos de verificación). Random es PREDECIBLE: quien vea
          algunos de sus números puede calcular los siguientes. SecureRandom
          usa fuentes de entropía del sistema operativo y no se puede
          predecir. Nunca uses Random para algo que protege una cuenta.
    */
    static List<Integer> rollDice(Random random, int times) {
        List<Integer> rolls = new ArrayList<>();
        for (int i = 0; i < times; i++) {
            rolls.add(random.nextInt(1, 7));   // 1 a 6: el 7 queda fuera
        }
        return rolls;
    }

    static String securityToken() {
        byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);          // impredecible
        return HexFormat.of().formatHex(bytes);       // 16 bytes = 32 caracteres hexadecimales
    }

    static boolean randomExample() {
        // Misma semilla, misma secuencia: reproducible.
        boolean reproducible = rollDice(new Random(42), 10).equals(rollDice(new Random(42), 10));

        List<Integer> rolls = rollDice(new Random(7), 1000);
        boolean inRange = true;
        for (int roll : rolls) {
            inRange &= roll >= 1 && roll <= 6;
        }
        boolean allFacesAppear = rolls.contains(1) && rolls.contains(6);

        return reproducible && inRange && allFacesAppear && securityToken().length() == 32;
    }

    /*
        =================================================================
        CAPA 5) BIGDECIMAL: DECIMALES EXACTOS
        =================================================================

        double guarda los decimales en BINARIO y por eso 0.1 es
        aproximado. BigDecimal los guarda en DECIMAL: un número entero de
        cualquier tamaño más una ESCALA (cuántos dígitos van después del
        punto). 19.99 es "1999 con escala 2". Así 0.1 es EXACTAMENTE 0.1.

        Por qué no se usa siempre: es un objeto (memoria y basura), y cada
        operación es mucho más lenta que con double. Se usa donde la
        exactitud importa más que la velocidad: DINERO, impuestos,
        cantidades de negocio.

        REGLAS PARA USARLO BIEN:

        1) CRÉALO DESDE TEXTO, no desde un double:
               new BigDecimal("0.1")   -> exactamente 0.1
               new BigDecimal(0.1)     -> 0.1000000000000000055511151231257827...
           El constructor con double recibe el valor YA aproximado del
           double, y lo copia con toda su imprecisión. Si tienes un
           double, usa BigDecimal.valueOf(d), que pasa por su texto.

        2) ES INMUTABLE (como String): add, subtract, multiply DEVUELVEN
           uno nuevo. total.add(x); sin guardar el resultado no hace nada.

        3) COMPARA CON compareTo, no con equals: equals considera la
           escala, y 2.0 no es "igual" a 2.00 (ObjectContract, CAPA 9).

        4) toString puede usar notación científica ("1E+3"); para mostrar
           a una persona usa toPlainString().
    */
    static boolean bigDecimalBasicsExample() {
        boolean fromStringIsExact = new BigDecimal("0.1").toString().equals("0.1");
        boolean fromDoubleIsNot = new BigDecimal(0.1).toString().startsWith("0.1000000000000000055511");
        boolean valueOfIsSafe = BigDecimal.valueOf(0.1).toString().equals("0.1");

        BigDecimal sum = new BigDecimal("0.1").add(new BigDecimal("0.2"));
        boolean exactSum = sum.compareTo(new BigDecimal("0.3")) == 0;   // con double esto fallaba

        BigDecimal total = new BigDecimal("10");
        total.add(BigDecimal.ONE);                                      // resultado perdido: inmutable
        boolean immutable = total.compareTo(BigDecimal.TEN) == 0;

        boolean equalsChecksScale = !new BigDecimal("2.0").equals(new BigDecimal("2.00"))
                && new BigDecimal("2.0").compareTo(new BigDecimal("2.00")) == 0;

        BigDecimal thousand = new BigDecimal("1000").stripTrailingZeros();
        boolean scientificToString = thousand.toString().equals("1E+3") && thousand.toPlainString().equals("1000");

        return fromStringIsExact && fromDoubleIsNot && valueOfIsSafe && exactSum
                && immutable && equalsChecksScale && scientificToString;
    }

    /*
        =================================================================
        CAPA 6) DIVIDIR CON BIGDECIMAL
        =================================================================

        Sumar, restar y multiplicar decimales exactos siempre da un
        resultado exacto. Dividir NO: 1 / 3 = 0.3333... infinitos dígitos.
        BigDecimal no puede guardar infinitos dígitos, así que:

            BigDecimal.ONE.divide(three)
            // ArithmeticException: Non-terminating decimal expansion;
            //                      no exact representable decimal result.

        Es a propósito: BigDecimal se niega a redondear en silencio (eso
        es justo lo que hace double y lo que se quería evitar). Tú decides
        cuántos decimales y cómo redondear:

            dividend.divide(divisor, 2, RoundingMode.HALF_UP)   // 2 decimales

        Regla: TODA división con BigDecimal lleva escala y RoundingMode,
        salvo que estés seguro de que el resultado es exacto.
    */
    static boolean divisionExample() {
        BigDecimal three = new BigDecimal("3");

        boolean refusesToGuess;
        try {
            BigDecimal.ONE.divide(three);
            refusesToGuess = false;
        } catch (ArithmeticException e) {
            refusesToGuess = e.getMessage().startsWith("Non-terminating decimal expansion");
        }

        BigDecimal third = BigDecimal.ONE.divide(three, 2, RoundingMode.HALF_UP);
        BigDecimal exactHalf = BigDecimal.ONE.divide(new BigDecimal("2"));   // 0.5: exacto, sí funciona

        return refusesToGuess && third.toString().equals("0.33") && exactHalf.toString().equals("0.5");
    }

    /*
        =================================================================
        CAPA 7) MODOS DE REDONDEO (ROUNDINGMODE)
        =================================================================

        "Redondear" no es una sola regla. BigDecimal te obliga a elegir,
        porque en negocios la regla la define la ley o el contrato.

        Redondeando a 0 decimales (setScale(0, modo)):

        | Modo       | Regla                                   | 2.5 | 3.5 | -2.5 |
        |------------|-----------------------------------------|-----|-----|------|
        | HALF_UP    | .5 se aleja del cero (el de la escuela) | 3   | 4   | -3   |
        | HALF_EVEN  | .5 va al número PAR ("bancario")        | 2   | 4   | -2   |
        | HALF_DOWN  | .5 se acerca al cero                    | 2   | 3   | -2   |
        | UP         | siempre se aleja del cero               | 3   | 4   | -3   |
        | DOWN       | siempre hacia el cero (truncar)         | 2   | 3   | -2   |
        | CEILING    | siempre hacia arriba (+infinito)        | 3   | 4   | -2   |
        | FLOOR      | siempre hacia abajo (-infinito)         | 2   | 3   | -3   |
        | UNNECESSARY| exige que no haga falta redondear       | (lanza excepción si hace falta) |

        POR QUÉ EXISTE HALF_EVEN ("redondeo bancario"): con HALF_UP, todos
        los .5 suben. Si redondeas millones de cantidades, el total queda
        sistemáticamente INFLADO. HALF_EVEN sube la mitad de las veces y
        baja la otra mitad (2.5 -> 2, 3.5 -> 4), así que los errores se
        cancelan. Es el modo por defecto en finanzas y en el estándar de
        punto flotante. (HALF_UP es lo que normalmente se espera en un
        ticket de compra; la regla la decide el negocio.)

        CUIDADO: el HALF_UP de BigDecimal NO es igual a Math.round.
        Con -2.5: HALF_UP da -3 (se aleja del cero); Math.round da -2 (va
        hacia +infinito; ConversionsAndCasting, CAPA 6).
    */
    static BigDecimal roundTo(String value, RoundingMode mode) {
        return new BigDecimal(value).setScale(0, mode);
    }

    static boolean roundingModesExample() {
        boolean halfUp = roundTo("2.5", RoundingMode.HALF_UP).intValue() == 3
                && roundTo("-2.5", RoundingMode.HALF_UP).intValue() == -3;
        boolean halfEven = roundTo("2.5", RoundingMode.HALF_EVEN).intValue() == 2
                && roundTo("3.5", RoundingMode.HALF_EVEN).intValue() == 4;
        boolean ceilingFloor = roundTo("-2.5", RoundingMode.CEILING).intValue() == -2
                && roundTo("-2.5", RoundingMode.FLOOR).intValue() == -3;
        boolean differsFromMathRound = Math.round(-2.5) == -2;   // Math.round no es HALF_UP

        boolean unnecessaryRejects;
        try {
            roundTo("2.5", RoundingMode.UNNECESSARY);
            unnecessaryRejects = false;
        } catch (ArithmeticException e) {
            unnecessaryRejects = true;
        }

        return halfUp && halfEven && ceilingFloor && differsFromMathRound && unnecessaryRejects;
    }

    /*
        =================================================================
        CAPA 8) DINERO EN LA PRÁCTICA
        =================================================================

        Decisiones profesionales al manejar dinero:

        1) ESCALA SEGÚN LA MONEDA: los pesos tienen 2 decimales, el yen
           japonés 0. java.util.Currency lo sabe:
               Currency.getInstance("MXN").getDefaultFractionDigits() -> 2

        2) DÓNDE REDONDEAR CAMBIA EL RESULTADO. Redondear el impuesto
           de cada línea y luego sumar no da lo mismo que sumar y luego
           redondear. Ninguna es "la correcta": la define la regla fiscal
           o del negocio, y debe aplicarse SIEMPRE igual.

        3) REPARTIR NO ES DIVIDIR. 100.00 entre 3 personas son 33.33 cada
           una... y se pierde un centavo (33.33 x 3 = 99.99). Al repartir
           dinero, el sobrante se asigna explícitamente a alguien, para
           que la suma de las partes dé EXACTAMENTE el total.

        4) ALTERNATIVA: guardar el dinero como long en CENTAVOS (19.99 ->
           1999). Es exacto y rápido; muchas empresas de pagos lo hacen.
           El costo es convertir al mostrar y tener cuidado con monedas de
           distinta escala.

        5) Nunca double. Nunca.
    */
    static BigDecimal taxRoundedPerLine(List<BigDecimal> lines, BigDecimal rate) {
        BigDecimal tax = BigDecimal.ZERO;
        for (BigDecimal line : lines) {
            tax = tax.add(line.multiply(rate).setScale(2, RoundingMode.HALF_UP));   // redondea cada línea
        }
        return tax;
    }

    static BigDecimal taxRoundedOnTotal(List<BigDecimal> lines, BigDecimal rate) {
        BigDecimal subtotal = BigDecimal.ZERO;
        for (BigDecimal line : lines) {
            subtotal = subtotal.add(line);
        }
        return subtotal.multiply(rate).setScale(2, RoundingMode.HALF_UP);              // redondea una vez
    }

    // Reparte un total en n partes que suman EXACTAMENTE el total.
    static List<BigDecimal> split(BigDecimal total, int parts) {
        BigDecimal base = total.divide(BigDecimal.valueOf(parts), 2, RoundingMode.DOWN);
        BigDecimal remainder = total.subtract(base.multiply(BigDecimal.valueOf(parts)));
        int extraCents = remainder.movePointRight(2).intValueExact();   // centavos que sobraron

        List<BigDecimal> shares = new ArrayList<>();
        for (int i = 0; i < parts; i++) {
            shares.add(i < extraCents ? base.add(new BigDecimal("0.01")) : base);
        }
        return shares;
    }

    static boolean moneyExample() {
        boolean currencyScale = Currency.getInstance("MXN").getDefaultFractionDigits() == 2
                && Currency.getInstance("JPY").getDefaultFractionDigits() == 0;

        List<BigDecimal> lines = List.of(new BigDecimal("0.05"), new BigDecimal("0.05"), new BigDecimal("0.05"));
        BigDecimal rate = new BigDecimal("0.16");
        boolean roundingPointMatters = taxRoundedPerLine(lines, rate).compareTo(new BigDecimal("0.03")) == 0
                && taxRoundedOnTotal(lines, rate).compareTo(new BigDecimal("0.02")) == 0;

        List<BigDecimal> shares = split(new BigDecimal("100.00"), 3);
        BigDecimal sumOfShares = shares.get(0).add(shares.get(1)).add(shares.get(2));
        boolean splitIsExact = shares.toString().equals("[33.34, 33.33, 33.33]")
                && sumOfShares.compareTo(new BigDecimal("100.00")) == 0;

        long priceInCents = 1999;                           // $19.99 como entero exacto
        boolean centsWork = priceInCents * 3 == 5997;

        return currencyScale && roundingPointMatters && splitIsExact && centsWork;
    }

    /*
        =================================================================
        CAPA 9) BIGINTEGER: ENTEROS SIN LÍMITE
        =================================================================

        long llega hasta unos 9.2 x 10^18 (PrimitiveTypes, CAPA 3). Más
        allá se desborda en silencio: 21! (factorial de 21) ya no cabe y
        un long da un número NEGATIVO sin sentido.

        BigInteger guarda enteros de CUALQUIER tamaño (crece en memoria
        según haga falta). Igual que BigDecimal: es inmutable, se opera
        con métodos (add, multiply, pow, mod...) y es mucho más lento que
        long.

        Dónde se usa: criptografía (RSA trabaja con números de cientos de
        dígitos), combinatoria, identificadores enormes, y cualquier
        cálculo donde el desbordamiento sería un error inaceptable.
        Incluso trae isProbablePrime, para saber si un número enorme es
        primo.
    */
    static BigInteger factorial(int n) {
        BigInteger result = BigInteger.ONE;
        for (int i = 2; i <= n; i++) {
            result = result.multiply(BigInteger.valueOf(i));
        }
        return result;
    }

    static boolean bigIntegerExample() {
        long overflowed = 1;
        for (int i = 2; i <= 21; i++) {
            overflowed *= i;                              // 21! no cabe en long
        }

        return overflowed < 0                             // desbordado: negativo
                && factorial(21).toString().equals("51090942171709440000")
                && factorial(25).toString().equals("15511210043330985984000000")
                && BigInteger.TWO.pow(100).toString().length() == 31
                && BigInteger.valueOf(97).isProbablePrime(20);
    }

    /*
        =================================================================
        CAPA 10) CÓMO ELEGIR EL TIPO NUMÉRICO
        =================================================================

        | Necesitas                                     | Usa                    |
        |-----------------------------------------------|------------------------|
        | Contar, índices, cantidades enteras normales  | int                    |
        | Enteros grandes (ids, milisegundos)           | long                   |
        | Medidas, ciencia, gráficos, estadística       | double                 |
        | Dinero, impuestos, cantidades exactas         | BigDecimal (o long en centavos) |
        | Enteros más grandes que long                  | BigInteger             |
        | Números al azar                               | Random                 |
        | Números al azar para seguridad                | SecureRandom           |

        La pregunta para decidir: ¿un error en el último decimal es
        aceptable? Si mides la temperatura, sí: double. Si cobras dinero,
        no: BigDecimal.
    */

    /*
        =================================================================
        CAPA 11) ERRORES COMUNES
        =================================================================

        1) Creer que Math.abs siempre devuelve un positivo (CAPA 2).

        2) Usar Math.pow para potencias enteras grandes y esperar
           exactitud (CAPA 2).

        3) Comparar decimales grandes con una tolerancia absoluta (CAPA 3).

        4) Usar Random para contraseñas, tokens o códigos de seguridad
           (CAPA 4).

        5) Olvidar que el límite superior de nextInt se excluye (CAPA 4).

        6) Crear BigDecimal con new BigDecimal(doubleValue) (CAPA 5).

        7) Comparar BigDecimal con equals, o no guardar el resultado de
           add/multiply (CAPA 5).

        8) Dividir BigDecimal sin escala ni RoundingMode (CAPA 6).

        9) Redondear sin decidir el modo, o asumir que HALF_UP es igual a
           Math.round (CAPA 7).

        10) Repartir dinero dividiendo y perder centavos (CAPA 8).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Primitivos: rápidos pero limitados o aproximados. BigDecimal y
          BigInteger: exactos pero lentos.
        - Math: utilitaria. Ojo con abs(MIN_VALUE) y con pow para enteros
          grandes.
        - Decimales: tolerancia relativa para números grandes.
        - Random es pseudoaleatorio (semilla = reproducible); SecureRandom
          para seguridad; el límite superior se excluye.
        - BigDecimal: desde String, inmutable, compareTo, toPlainString.
          Dividir exige escala y RoundingMode.
        - RoundingMode: HALF_UP (escuela), HALF_EVEN (bancario, sin
          sesgo)... lo decide el negocio.
        - Dinero: escala de la moneda, un punto de redondeo consistente,
          repartir asignando el sobrante, o long en centavos. Nunca double.
        - BigInteger: enteros sin límite.
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
