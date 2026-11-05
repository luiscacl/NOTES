package v1_fundamentals.part2_control_flow;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.List;

/*
    =====================================================================
    CICLOS — DE CERO A NIVEL TÉCNICO
    =====================================================================

    Ruta de v1_fundamentals/: guía 7 de 14  (part2_control_flow)
    Anterior:  part2_control_flow/Conditionals
    Siguiente: part2_control_flow/Methods

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.

    Índice:
        CAPA 1)  Por qué repetir y las partes de todo ciclo
        CAPA 2)  while: mientras la condición se cumpla
        CAPA 3)  do-while: al menos una vez
        CAPA 4)  for clásico: cuando hay un contador
        CAPA 5)  El error de "uno de más"
        CAPA 6)  for-each: recorrer sin índice
        CAPA 7)  break y continue
        CAPA 8)  Ciclos anidados y break con etiqueta
        CAPA 9)  Ciclos infinitos: a propósito y por accidente
        CAPA 10) Los patrones que resuelven casi todo
        CAPA 11) Cómo elegir el ciclo
        CAPA 12) Errores comunes
        RESUMEN FINAL
*/
public class Loops {

    public static void main(String[] args) {
        check(whileExample(), "CAPA 2");
        check(doWhileExample(), "CAPA 3");
        check(forExample(), "CAPA 4");
        check(offByOneExample(), "CAPA 5");
        check(forEachExample(), "CAPA 6");
        check(breakContinueExample(), "CAPA 7");
        check(nestedLoopsExample(), "CAPA 8");
        check(infiniteLoopsExample(), "CAPA 9");
        check(loopPatternsExample(), "CAPA 10");
        System.out.println("Loops: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) POR QUÉ REPETIR Y LAS PARTES DE TODO CICLO
        =================================================================

        Un CICLO (loop) repite un bloque de código. Sin ciclos, sumar las
        calificaciones de 30 alumnos serían 30 líneas; con 30 000, sería
        imposible. Con un ciclo son tres líneas sin importar cuántos sean.

        Cada repetición se llama ITERACIÓN. Todo ciclo, de cualquier tipo,
        tiene cuatro partes, aunque a veces estén escondidas:

        1) INICIO:       desde dónde empiezas      (int i = 0)
        2) CONDICIÓN:    hasta cuándo sigues        (i < 10)
        3) CUERPO:       qué haces en cada vuelta
        4) AVANCE:       qué cambia en cada vuelta  (i++)

        Si el AVANCE nunca acerca la CONDICIÓN a ser falsa, el ciclo no
        termina nunca (CAPA 9). Esa es la pregunta que hay que hacerse
        con cada ciclo: "¿qué garantiza que esto termina?".
    */

    /*
        =================================================================
        CAPA 2) WHILE: MIENTRAS LA CONDICIÓN SE CUMPLA
        =================================================================

            while (condición) {
                cuerpo
            }

        Revisa la condición ANTES de cada vuelta. Si desde el principio
        es falsa, el cuerpo no se ejecuta ni una vez.

        Cuándo usarlo: cuando NO sabes de antemano cuántas vueltas
        habrá; dependes de algo que cambia mientras trabajas.
        Ejemplo: ¿cuántos años tarda en duplicarse un ahorro al 7 %
        anual? No lo sabes hasta calcularlo.
    */
    static int yearsToDouble(double initialAmount, double annualRate) {
        double amount = initialAmount;
        int years = 0;
        while (amount < initialAmount * 2) {   // no sabemos cuántas vueltas: depende del cálculo
            amount *= 1 + annualRate;
            years++;
        }
        return years;
    }

    static boolean whileExample() {
        return yearsToDouble(1000, 0.07) == 11 && yearsToDouble(1000, 1.0) == 1;
    }

    /*
        =================================================================
        CAPA 3) DO-WHILE: AL MENOS UNA VEZ
        =================================================================

            do {
                cuerpo
            } while (condición);     // lleva punto y coma

        Igual que while, pero revisa la condición DESPUÉS de cada vuelta.
        Por eso el cuerpo se ejecuta SIEMPRE al menos una vez.

        Cuándo usarlo: cuando la primera vuelta debe ocurrir sí o sí, y
        la decisión de seguir depende de lo que pasó en ella. Casos
        típicos: mostrar un menú y repetir hasta que el usuario elija
        "salir"; pedir un dato y repetir mientras sea inválido.

        Ejemplo donde do-while es exactamente lo correcto: contar los
        dígitos de un número. El 0 tiene UN dígito, pero con while el
        ciclo no entraría nunca (la condición 0 > 0 es falsa desde el
        inicio) y daría 0.
    */
    static int countDigitsWithWhile(int number) {
        int n = Math.abs(number);
        int digits = 0;
        while (n > 0) {
            digits++;
            n /= 10;
        }
        return digits;   // con 0 devuelve 0: incorrecto
    }

    static int countDigits(int number) {
        int n = Math.abs(number);
        int digits = 0;
        do {
            digits++;      // la primera vuelta siempre ocurre: cuenta el dígito del 0
            n /= 10;
        } while (n > 0);
        return digits;
    }

    static boolean doWhileExample() {
        return countDigits(12345) == 5
                && countDigits(-90) == 2
                && countDigits(0) == 1
                && countDigitsWithWhile(0) == 0;   // la versión con while falla justo en el caso borde
    }

    /*
        =================================================================
        CAPA 4) FOR CLÁSICO: CUANDO HAY UN CONTADOR
        =================================================================

            for (inicio; condición; avance) {
                cuerpo
            }

        Junta las cuatro partes de la CAPA 1 en una sola línea. Se
        ejecuta en este orden:
            1) inicio (UNA vez)
            2) condición -> si es false, termina
            3) cuerpo
            4) avance, y vuelve al paso 2

        Cuándo usarlo: cuando el ciclo está controlado por un CONTADOR o
        necesitas el ÍNDICE (posición) de cada elemento.

        Por qué existe si while ya puede hacer lo mismo: porque pone el
        inicio, la condición y el avance JUNTOS y a la vista. Con while,
        el avance queda al final del cuerpo y es fácil olvidarlo. Además,
        la variable del for solo existe dentro del for (Variables, CAPA 5).

        No siempre es "de 0 a n, de uno en uno":
            for (int i = 10; i > 0; i--)        // hacia atrás
            for (int i = 0; i < n; i += 2)      // de dos en dos
            for (int i = 0, j = n - 1; i < j; i++, j--)   // dos índices a la vez
        El último es la técnica de "dos punteros": uno avanza desde el
        inicio y otro retrocede desde el final hasta encontrarse. Se usa
        muchísimo en algoritmos (algorithms/AlgorithmPatterns).
    */
    static int[] reversed(int[] values) {
        int[] result = values.clone();
        for (int left = 0, right = result.length - 1; left < right; left++, right--) {
            int temporary = result[left];
            result[left] = result[right];
            result[right] = temporary;
        }
        return result;
    }

    static boolean forExample() {
        int sumOfEvens = 0;
        for (int i = 0; i <= 10; i += 2) {
            sumOfEvens += i;               // 0 + 2 + 4 + 6 + 8 + 10
        }

        StringBuilder countdown = new StringBuilder();
        for (int i = 3; i > 0; i--) {
            countdown.append(i);
        }

        return sumOfEvens == 30
                && countdown.toString().equals("321")
                && Arrays.equals(reversed(new int[] {1, 2, 3, 4}), new int[] {4, 3, 2, 1});
    }

    /*
        =================================================================
        CAPA 5) EL ERROR DE "UNO DE MÁS"
        =================================================================

        Es el error más común con ciclos: dar una vuelta de más o de
        menos (en inglés, "off-by-one"). Viene de que las posiciones de un
        arreglo empiezan en 0: un arreglo de 3 elementos tiene las
        posiciones 0, 1 y 2. La posición 3 NO existe.

            for (int i = 0; i < array.length; i++)    // correcto: 0, 1, 2
            for (int i = 0; i <= array.length; i++)   // ERROR: intenta la posición 3

        Java revisa cada acceso: la posición inexistente lanza
        ArrayIndexOutOfBoundsException en vez de leer memoria ajena (en C
        leería basura en silencio).

        Regla para no equivocarte: empieza en 0 y usa < con la longitud.
        "Desde 0, menos que el tamaño" recorre exactamente "tamaño"
        elementos.
    */
    static boolean offByOneExample() {
        int[] scores = {7, 8, 9};

        int correctSum = 0;
        for (int i = 0; i < scores.length; i++) {
            correctSum += scores[i];
        }

        boolean oneTooManyFails;
        try {
            int sum = 0;
            for (int i = 0; i <= scores.length; i++) {   // <= : una vuelta de más
                sum += scores[i];
            }
            oneTooManyFails = sum < 0;   // nunca llega aquí
        } catch (ArrayIndexOutOfBoundsException e) {
            oneTooManyFails = e.getMessage().contains("Index 3 out of bounds for length 3");
        }

        return correctSum == 24 && oneTooManyFails;
    }

    /*
        =================================================================
        CAPA 6) FOR-EACH: RECORRER SIN ÍNDICE
        =================================================================

            for (Tipo elemento : colección) {
                cuerpo
            }
        Se lee: "para cada elemento EN la colección".

        Recorre todos los elementos de un arreglo o de cualquier colección
        (List, Set...), del primero al último, sin manejar índices.

        Por qué preferirlo cuando no necesitas la posición: no puede tener
        errores de "uno de más", es más corto y dice exactamente la
        intención ("procesar cada elemento").

        Sus límites, y por qué:
        1) No te da el índice. Si lo necesitas, usa el for clásico.
        2) La variable es una COPIA del elemento. Asignarle otro valor NO
           cambia el arreglo (con primitivos se copia el valor: Variables,
           CAPA 2).
        3) No puedes agregar ni quitar elementos de una colección mientras
           la recorres con for-each: normalmente lanza
           ConcurrentModificationException (la colección detecta que cambió
           "por debajo" del recorrido). Y NO está garantizado: medido, quitar
           el PENÚLTIMO no lanza nada y el ciclo termina en silencio sin
           visitar el último. Peor que un error.
           Para quitar elementos se usa removeIf (o un Iterator:
           v5_collections/Iterators).
    */
    static boolean forEachExample() {
        int[] prices = {100, 200, 300};
        int total = 0;
        for (int price : prices) {
            total += price;
        }

        // Asignar a la variable del for-each NO modifica el arreglo.
        for (int price : prices) {
            price = price * 2;   // solo cambia la copia
        }
        boolean arrayUnchanged = prices[0] == 100;

        List<String> names = new ArrayList<>(List.of("Ana", "Luis", "Mia"));
        boolean removingFails;
        try {
            for (String name : names) {
                if (name.equals("Ana")) {
                    names.remove(name);   // modificar la lista mientras se recorre
                }
            }
            removingFails = false;
        } catch (ConcurrentModificationException e) {
            removingFails = true;
        }

        List<String> safe = new ArrayList<>(List.of("Ana", "Luis", "Mia"));
        safe.removeIf(name -> name.equals("Ana"));   // la forma correcta de quitar mientras recorres

        return total == 600 && arrayUnchanged && removingFails && safe.equals(List.of("Luis", "Mia"));
    }

    /*
        =================================================================
        CAPA 7) BREAK Y CONTINUE
        =================================================================

        - break: SALE del ciclo de inmediato. Lo que falta no se ejecuta.
          Uso típico: buscar algo y parar en cuanto lo encuentras (no
          tiene sentido seguir revisando).
        - continue: SALTA el resto de ESTA vuelta y pasa a la siguiente.
          Uso típico: ignorar elementos que no aplican.

        Igual que las cláusulas de guarda (Conditionals, CAPA 4),
        continue evita anidar el cuerpo entero dentro de un if.

        Dentro de un método, return también sale del ciclo (y del
        método). Para "buscar y devolver", return es más claro que break
        más una variable bandera.
    */
    static int indexOfFirstNegative(int[] values) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] < 0) {
                return i;        // encontrado: sale del ciclo Y del método
            }
        }
        return -1;               // convención: -1 = "no está"
    }

    static int sumOfPositives(int[] values) {
        int sum = 0;
        for (int value : values) {
            if (value <= 0) {
                continue;        // ignora este y pasa al siguiente
            }
            sum += value;
        }
        return sum;
    }

    static boolean breakContinueExample() {
        int[] movements = {50, 20, -30, 40, -10};

        int firstPositiveAbove30 = -1;
        for (int value : movements) {
            if (value > 30) {
                firstPositiveAbove30 = value;
                break;           // ya lo tenemos: no revisa el resto
            }
        }

        return indexOfFirstNegative(movements) == 2
                && indexOfFirstNegative(new int[] {1, 2}) == -1
                && sumOfPositives(movements) == 110
                && firstPositiveAbove30 == 50;
    }

    /*
        =================================================================
        CAPA 8) CICLOS ANIDADOS Y BREAK CON ETIQUETA
        =================================================================

        Un ciclo dentro de otro: por cada vuelta del de afuera, el de
        adentro da TODAS sus vueltas. Con 10 filas y 10 columnas, el
        cuerpo interno se ejecuta 10 x 10 = 100 veces.

        Esto importa para el rendimiento: con n elementos, dos ciclos
        anidados hacen n x n operaciones. Con 1 000 elementos es un
        millón; con 100 000, diez mil millones (algorithms/BigOComplexity).

        BREAK CON ETIQUETA
        Un break normal solo sale del ciclo MÁS INTERNO. Para salir de
        los dos a la vez, se pone una ETIQUETA (un nombre seguido de :)
        antes del ciclo externo, y se escribe break etiqueta.
        (continue etiqueta también existe: pasa a la siguiente vuelta del
        ciclo externo.)

        Las etiquetas se usan poco porque suelen ser señal de que conviene
        sacar los ciclos a un método y usar return. Pero conviene
        reconocerlas.
    */
    static int[] findInGrid(int[][] grid, int target) {
        int[] position = {-1, -1};
        search:                                    // etiqueta del ciclo externo
        for (int row = 0; row < grid.length; row++) {
            for (int col = 0; col < grid[row].length; col++) {
                if (grid[row][col] == target) {
                    position[0] = row;
                    position[1] = col;
                    break search;                  // sale de AMBOS ciclos
                }
            }
        }
        return position;
    }

    static boolean nestedLoopsExample() {
        int iterations = 0;
        for (int row = 1; row <= 10; row++) {
            for (int col = 1; col <= 10; col++) {
                iterations++;                      // tabla de multiplicar: 100 vueltas en total
            }
        }

        int[][] seats = {
                {11, 12, 13},
                {21, 22, 23},
                {31, 32, 33}
        };
        int[] found = findInGrid(seats, 22);

        return iterations == 100 && found[0] == 1 && found[1] == 1
                && findInGrid(seats, 99)[0] == -1;
    }

    /*
        =================================================================
        CAPA 9) CICLOS INFINITOS: A PROPÓSITO Y POR ACCIDENTE
        =================================================================

        A PROPÓSITO: while (true) con un break o return adentro. Se usa
        cuando la condición de salida está a mitad del cuerpo, no al
        principio ni al final: un servidor que atiende peticiones hasta
        que lo apagan, un menú que se repite hasta elegir "salir".

        POR ACCIDENTE (el programa se "congela"):
        1) Olvidar el avance: while (i < 10) { ... } sin i++.
        2) Avanzar en la dirección equivocada: for (int i = 0; i < 10; i--).
        3) Usar un decimal como contador con != :
               for (double x = 0; x != 1.0; x += 0.1)
           Nunca termina: por el error binario de los decimales
           (PrimitiveTypes, CAPA 7), x pasa por 0.9999999999999999 y
           luego por 1.0999999999999999, pero NUNCA vale exactamente 1.0.
           Regla: los contadores van en int, y las condiciones de salida
           usan <, <=, >, >= en lugar de != cuando sea posible.
    */
    static int readUntilStop(List<String> commands) {
        int processed = 0;
        int index = 0;
        while (true) {                              // infinito a propósito...
            String command = commands.get(index++);
            if (command.equals("stop")) {
                break;                              // ...con una salida clara
            }
            processed++;
        }
        return processed;
    }

    static boolean infiniteLoopsExample() {
        // El contador decimal pasa CERCA de 1.0 pero nunca ES 1.0 (limitamos a 20 vueltas para no colgarnos).
        boolean everExactlyOne = false;
        double x = 0;
        for (int safety = 0; safety < 20; safety++) {
            if (x == 1.0) {
                everExactlyOne = true;
            }
            x += 0.1;
        }

        return readUntilStop(List.of("open", "read", "stop", "never")) == 2 && !everExactlyOne;
    }

    /*
        =================================================================
        CAPA 10) LOS PATRONES QUE RESUELVEN CASI TODO
        =================================================================

        La gran mayoría de los ciclos del mundo real son uno de estos
        patrones. Reconocerlos te permite escribirlos sin pensar:

        1) ACUMULADOR: empieza en el valor neutro y acumula.
               suma empieza en 0; producto empieza en 1.
        2) CONTADOR: cuenta cuántos cumplen una condición.
        3) MÁXIMO / MÍNIMO: empieza con el PRIMER elemento (no con 0: si
           todos fueran negativos, 0 ganaría sin estar en los datos).
        4) BÚSQUEDA: recorre hasta encontrar y sale (CAPA 7).
        5) CONSTRUCCIÓN: crea una colección nueva con los elementos que
           cumplen algo (un filtro) o transformados (un mapeo).

        Estos mismos patrones, en estilo funcional, son los streams:
        sum(), count(), max(), filter(), map() (v7_functional/).
    */
    static boolean loopPatternsExample() {
        int[] temperatures = {-5, -2, -8, -1};

        int sum = 0;                         // 1) acumulador
        int belowMinusTwo = 0;               // 2) contador
        int max = temperatures[0];           // 3) máximo: empieza con el primero, NO con 0
        List<Integer> warmer = new ArrayList<>();   // 5) construcción (filtro)

        for (int t : temperatures) {
            sum += t;
            if (t < -2) {
                belowMinusTwo++;
            }
            if (t > max) {
                max = t;
            }
            if (t > -3) {
                warmer.add(t);
            }
        }

        int wrongMax = 0;                    // si el máximo empezara en 0...
        for (int t : temperatures) {
            if (t > wrongMax) {
                wrongMax = t;
            }
        }

        return sum == -16 && belowMinusTwo == 2 && max == -1
                && warmer.equals(List.of(-2, -1))
                && wrongMax == 0;            // ...diría que la máxima fue 0, que no está en los datos
    }

    /*
        =================================================================
        CAPA 11) CÓMO ELEGIR EL CICLO
        =================================================================

        | Situación                                              | Usa              |
        |--------------------------------------------------------|------------------|
        | Recorrer todos los elementos, sin importar la posición | for-each         |
        | Necesitas el índice, o recorrer al revés o saltando    | for clásico      |
        | Repetir N veces                                        | for clásico      |
        | No sabes cuántas vueltas; depende de algo que cambia   | while            |
        | La primera vuelta debe ocurrir siempre                 | do-while         |
        | La salida está a mitad del cuerpo                      | while (true) + break |
        | Quitar elementos de una colección mientras recorres    | removeIf / Iterator |
        | Transformar, filtrar o resumir una colección           | streams (v7_functional/) |
    */

    /*
        =================================================================
        CAPA 12) ERRORES COMUNES
        =================================================================

        1) Olvidar el avance o avanzar al revés: ciclo infinito (CAPA 9).

        2) Usar <= con la longitud de un arreglo (CAPA 5).

        3) Usar while cuando la primera vuelta debe ocurrir siempre
           (contar dígitos del 0) (CAPA 3).

        4) Asignar a la variable de un for-each esperando cambiar la
           colección (CAPA 6).

        5) Agregar o quitar elementos de una colección dentro de su
           for-each (CAPA 6).

        6) Seguir recorriendo después de encontrar lo que buscabas
           (CAPA 7).

        7) Usar un decimal como contador, o != como condición de salida
           con decimales (CAPA 9).

        8) Empezar un máximo en 0 o un mínimo en 0 (CAPA 10).

        9) Ciclos anidados sobre datos grandes sin pensar en el costo
           (CAPA 8).

        10) Poner un ; justo después del for o while:
                for (int i = 0; i < 3; i++);   { cuerpo }
            El ; es un cuerpo vacío: el ciclo da sus vueltas sin hacer
            nada y el bloque de abajo se ejecuta UNA vez.
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Todo ciclo: inicio, condición, cuerpo, avance. Pregúntate
          siempre qué garantiza que termina.
        - while: revisa antes; para vueltas desconocidas.
        - do-while: revisa después; al menos una vuelta.
        - for: contador e índice; las cuatro partes juntas y a la vista.
        - Desde 0 y con < length: evita el "uno de más".
        - for-each: sin índice, sin errores de límites; su variable es una
          copia; no modifiques la colección mientras la recorres.
        - break sale; continue salta a la siguiente vuelta; return sale
          del método; break etiqueta sale de ciclos anidados.
        - Ciclos anidados multiplican el trabajo.
        - Contadores en int; evita != con decimales.
        - Patrones: acumulador, contador, máximo/mínimo, búsqueda,
          construcción.
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
