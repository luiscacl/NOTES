package v1_fundamentals.part2_control_flow;

import java.util.List;

/*
    =====================================================================
    CONDICIONALES — DE CERO A NIVEL TÉCNICO
    =====================================================================

    Ruta de v1_fundamentals/: guía 6 de 14  (part2_control_flow)
    Anterior:  part1_first_steps/ConversionsAndCasting
    Siguiente: part2_control_flow/Loops

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.

    Índice:
        CAPA 1)  Control de flujo: decidir qué se ejecuta
        CAPA 2)  if y else
        CAPA 3)  else if: el orden importa
        CAPA 4)  Anidar vs cláusulas de guarda
        CAPA 5)  Escribir condiciones claras
        CAPA 6)  El operador ternario
        CAPA 7)  switch clásico y el "fall-through"
        CAPA 8)  switch moderno con ->
        CAPA 9)  switch como expresión y yield
        CAPA 10) Qué tipos acepta switch
        CAPA 11) Cuándo usar if, switch, ternario... o ninguno
        CAPA 12) Errores comunes
        RESUMEN FINAL
*/
public class Conditionals {

    public static void main(String[] args) {
        check(ifElseExample(), "CAPA 2");
        check(elseIfOrderExample(), "CAPA 3");
        check(guardClausesExample(), "CAPA 4");
        check(clearConditionsExample(), "CAPA 5");
        check(ternaryExample(), "CAPA 6");
        check(classicSwitchExample(), "CAPA 7");
        check(arrowSwitchExample(), "CAPA 8");
        check(switchExpressionExample(), "CAPA 9");
        check(switchTypesExample(), "CAPA 10");
        System.out.println("Conditionals: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) CONTROL DE FLUJO: DECIDIR QUÉ SE EJECUTA
        =================================================================

        Sin control de flujo, un programa ejecuta sus líneas una tras
        otra, de arriba a abajo, siempre igual. Un programa útil necesita
        DECIDIR: cobrar envío solo si la compra es menor a cierto monto,
        mostrar un error solo si el dato es inválido.

        Java tiene tres herramientas para decidir:
        - if / else         -> elegir entre caminos según condiciones
        - el ternario ?:    -> elegir entre DOS VALORES
        - switch            -> elegir entre muchos casos según UN valor

        (Y para REPETIR, los ciclos: v1_fundamentals/Loops.)
    */

    /*
        =================================================================
        CAPA 2) IF Y ELSE
        =================================================================

            if (condición) {
                // se ejecuta solo si la condición es true
            } else {
                // se ejecuta solo si es false
            }

        Reglas:
        1) La condición va SIEMPRE entre paréntesis y DEBE ser boolean.
           No hay "truthy/falsy" como en JavaScript: if (count) no
           compila; se escribe if (count > 0) (PrimitiveTypes, CAPA 10).
        2) El else es opcional.

        3) USA SIEMPRE LLAVES, aunque el bloque tenga una sola línea.
           Java permite omitirlas, pero entonces el if solo controla la
           SIGUIENTE línea, y la sangría engaña:

               if (isAdmin)
                   grantAccess();
                   logAccess();     // ¡se ejecuta SIEMPRE! La sangría miente.

           No es teoría: en 2014, un fallo de seguridad de Apple ("goto
           fail") que dejaba sin verificar certificados SSL en millones de
           dispositivos fue exactamente esto: una línea duplicada bajo un
           if sin llaves.

        Ejemplo: envío gratis a partir de cierto monto.
    */
    static double shippingCost(double subtotal) {
        double cost;
        if (subtotal >= 999) {
            cost = 0;
        } else {
            cost = 99;
        }
        return cost;
    }

    /*
        Otro peligro sin llaves, el "else colgante": un else siempre
        pertenece al if MÁS CERCANO que no tenga else, sin importar la
        sangría.
    */
    static String danglingElse(boolean hasAccount, boolean isVerified) {
        String result = "sin revisar";
        if (hasAccount)
            if (isVerified)
                result = "verificado";
        else                                  // parece del primer if... pero es del segundo
            result = "sin cuenta";
        return result;
    }

    static boolean ifElseExample() {
        // Sin cuenta, esperaríamos "sin cuenta", pero el else pertenece al if interno.
        boolean danglingTrap = danglingElse(false, false).equals("sin revisar")
                && danglingElse(true, false).equals("sin cuenta");   // ¡con cuenta dice "sin cuenta"!

        return shippingCost(1200) == 0 && shippingCost(300) == 99 && danglingTrap;
    }

    /*
        =================================================================
        CAPA 3) ELSE IF: EL ORDEN IMPORTA
        =================================================================

        Para elegir entre MÁS de dos caminos se encadenan else if:

            if (a) { ... }
            else if (b) { ... }
            else if (c) { ... }
            else { ... }        // ninguno se cumplió

        Java prueba las condiciones DE ARRIBA A ABAJO y ejecuta SOLO la
        PRIMERA que sea verdadera; las demás ya ni se revisan.

        Por eso el orden importa cuando las condiciones se solapan. Con
        rangos, ve del más restrictivo al menos restrictivo (o de mayor a
        menor). Si pones primero el más amplio, se "come" a los demás:
    */
    static String letterGrade(int score) {
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException("La calificación debe estar entre 0 y 100");
        }
        if (score >= 90) {
            return "A";
        } else if (score >= 80) {
            return "B";
        } else if (score >= 70) {
            return "C";
        } else if (score >= 60) {
            return "D";
        } else {
            return "F";
        }
    }

    static String letterGradeWrongOrder(int score) {
        if (score >= 60) {          // el rango más amplio primero: atrapa también a 95
            return "D";
        } else if (score >= 90) {   // nunca se llega aquí con 90 o más
            return "A";
        } else {
            return "F";
        }
    }

    static boolean elseIfOrderExample() {
        return letterGrade(95).equals("A")
                && letterGrade(85).equals("B")
                && letterGrade(59).equals("F")
                && letterGradeWrongOrder(95).equals("D");   // error lógico: compila y corre
    }

    /*
        =================================================================
        CAPA 4) ANIDAR VS CLÁUSULAS DE GUARDA
        =================================================================

        Se pueden poner if dentro de otros if (anidar). Con varias
        validaciones, el código se va hacia la derecha en forma de
        pirámide, y lo importante (el cálculo real) queda enterrado al
        fondo:
    */
    static double expressCostNested(double weightKg, String country) {
        double cost;
        if (country != null) {
            if (country.equals("MX")) {
                if (weightKg > 0) {
                    if (weightKg <= 30) {
                        cost = 150 + weightKg * 10;   // lo que importa, cuatro niveles adentro
                    } else {
                        cost = -1;
                    }
                } else {
                    cost = -1;
                }
            } else {
                cost = -1;
            }
        } else {
            cost = -1;
        }
        return cost;
    }

    /*
        La alternativa profesional son las CLÁUSULAS DE GUARDA: revisar
        primero los casos inválidos o especiales y SALIR temprano con
        return (o lanzando una excepción). Lo que queda al final es el
        "camino feliz", sin sangría extra.

        Por qué es mejor:
        - Cada regla se lee sola, en una línea, cerca de su consecuencia.
        - El camino principal queda al nivel del método, no enterrado.
        - Agregar una validación nueva es agregar una línea, no otro
          nivel de pirámide.
        (Es el mismo estilo de "salir temprano" del instanceof negado en
        v2_oop/part4_advanced_types/SealedAndPatternMatching, CAPA 3.)
    */
    static double expressCost(double weightKg, String country) {
        if (country == null || !country.equals("MX")) {
            return -1;
        }
        if (weightKg <= 0 || weightKg > 30) {
            return -1;
        }
        return 150 + weightKg * 10;
    }

    static boolean guardClausesExample() {
        // Mismo comportamiento, muy distinta legibilidad.
        for (double weight : List.of(-1.0, 5.0, 40.0)) {
            for (String country : java.util.Arrays.asList("MX", "US", null)) {
                if (expressCostNested(weight, country) != expressCost(weight, country)) {
                    return false;
                }
            }
        }
        return expressCost(5, "MX") == 200;
    }

    /*
        (Devolver -1 para "inválido" se usa aquí solo para comparar las
        dos versiones. En código real, un dato inválido se señala con una
        excepción o con un tipo de resultado: SealedAndPatternMatching,
        CAPA 9, y v3_exceptions/.)
    */

    /*
        =================================================================
        CAPA 5) ESCRIBIR CONDICIONES CLARAS
        =================================================================

        1) DALE NOMBRE a las condiciones complejas, con una variable o un
           método boolean. Compara:
               if (age >= 18 && !isBlocked && (hasCard || balance > 0))
               if (canPurchase)
           El nombre explica la INTENCIÓN; la expresión solo el cómo.

        2) No compares un boolean con true o false:
               if (isActive == true)   ->   if (isActive)
               if (isActive == false)  ->   if (!isActive)

        3) No escribas "if (cond) return true; else return false;".
           La condición YA es el boolean que quieres devolver:
               return age >= 18;

        4) Evita la doble negación (if (!isNotValid)). Nombra en positivo.

        5) LEYES DE DE MORGAN: para negar una condición compuesta, se
           niega cada parte y se cambia && por || (y viceversa):
               !(a && b)   es lo mismo que   !a || !b
               !(a || b)   es lo mismo que   !a && !b
           Ejemplo: "NO (tiene artículos Y no está bloqueado)" es
           "no tiene artículos O está bloqueado". Se usa todo el tiempo
           para simplificar condiciones y para escribir cláusulas de
           guarda (la condición de salida es la negación de la de entrada).
    */
    static boolean isAdult(int age) {
        return age >= 18;   // no hace falta if/else
    }

    static boolean canCheckout(boolean hasItems, boolean isBlocked) {
        return hasItems && !isBlocked;
    }

    static boolean clearConditionsExample() {
        // De Morgan: comprobado para las 4 combinaciones posibles.
        boolean deMorganHolds = true;
        for (boolean hasItems : new boolean[] {true, false}) {
            for (boolean isBlocked : new boolean[] {true, false}) {
                boolean negated = !canCheckout(hasItems, isBlocked);
                boolean expanded = !hasItems || isBlocked;
                deMorganHolds &= negated == expanded;
            }
        }
        return isAdult(18) && !isAdult(17) && deMorganHolds;
    }

    /*
        =================================================================
        CAPA 6) EL OPERADOR TERNARIO
        =================================================================

            condición ? valorSiTrue : valorSiFalse

        Diferencia clave con if: el ternario es una EXPRESIÓN (produce un
        valor que puedes asignar, devolver o pasar); if es una SENTENCIA
        (ejecuta acciones, no produce valor).

            String status = score >= 60 ? "Aprobado" : "Reprobado";

        Cuándo usarlo: para elegir entre dos valores SIMPLES. Si las
        ramas tienen lógica o hay que anidarlo, usa if: un ternario
        anidado (a ? b : c ? d : e) es difícil de leer.

        LA TRAMPA DEL TIPO: el ternario tiene UN solo tipo de resultado.
        Si las ramas son de tipos numéricos distintos, se aplica la
        promoción numérica (ConversionsAndCasting, CAPA 7) a TODO el
        ternario, aunque se elija la otra rama:
            Object value = true ? 1 : 2.0;   // ¡es 1.0 (Double), no 1!
        (Y si una rama es un Integer null, puede lanzar
        NullPointerException: v1_fundamentals/Wrappers.)
    */
    static boolean ternaryExample() {
        int score = 72;
        String status = score >= 60 ? "Aprobado" : "Reprobado";

        int quantity = 3;
        String label = quantity + (quantity == 1 ? " artículo" : " artículos");

        Object promoted = true ? 1 : 2.0;   // las dos ramas se promueven a double

        return status.equals("Aprobado")
                && label.equals("3 artículos")
                && promoted.equals(1.0);
    }

    /*
        =================================================================
        CAPA 7) SWITCH CLÁSICO Y EL "FALL-THROUGH"
        =================================================================

        switch compara UN valor contra varias constantes y salta al case
        que coincide. La forma CLÁSICA (heredada de C, con dos puntos):

            switch (valor) {
                case 1:
                    ...
                    break;
                case 2:
                    ...
                    break;
                default:
                    ...   // si ningún case coincide
            }

        Lo que hay que entender: un case es solo un PUNTO DE ENTRADA, no
        un bloque. Una vez que entra, la ejecución CONTINÚA hacia los
        case siguientes hasta encontrar un break. Esto se llama
        FALL-THROUGH ("caer a través").

        Por qué existe: en C se usaba a propósito para agrupar casos.
        Pero olvidar un break es uno de los errores más comunes de la
        historia, porque no da ningún error: el programa ejecuta código
        de otros casos.
    */
    @SuppressWarnings("fallthrough") // a propósito: este es el error que se quiere mostrar
    static String sizeLabelForgotBreak(int size) {
        String label = "";
        switch (size) {
            case 1:
                label += "chico";
                // falta break: cae al caso 2
            case 2:
                label += "mediano";
                break;
            default:
                label += "grande";
        }
        return label;
    }

    // El uso legítimo: varios case vacíos seguidos para agrupar valores.
    static String season(int month) {
        String season;
        switch (month) {
            case 12:
            case 1:
            case 2:
                season = "invierno";
                break;
            case 3:
            case 4:
            case 5:
                season = "primavera";
                break;
            default:
                season = "otra";
        }
        return season;
    }

    static boolean classicSwitchExample() {
        return sizeLabelForgotBreak(1).equals("chicomediano")   // ejecutó dos casos
                && sizeLabelForgotBreak(2).equals("mediano")
                && season(1).equals("invierno")
                && season(4).equals("primavera");
    }

    /*
        El compilador puede avisarte de un break olvidado si compilas con
        -Xlint (javac -Xlint:all):
            warning: [fallthrough] possible fall-through into case
        (Este método la suprime con @SuppressWarnings solo porque el error
        es a propósito.) Pero la mejor solución es no usar esta forma:
        la CAPA 8 la vuelve innecesaria.
    */

    /*
        =================================================================
        CAPA 8) SWITCH MODERNO CON ->
        =================================================================

        Desde Java 14, cada case puede usar -> en lugar de dos puntos:

            switch (day) {
                case "Lunes" -> ...;
                case "Sábado", "Domingo" -> ...;   // varios valores en un case
                default -> ...;
            }

        Diferencias con la forma clásica:
        - NO HAY FALL-THROUGH: se ejecuta solo el case que coincide. No
          hace falta break, y no se puede olvidar.
        - Varios valores en un mismo case, separados por comas (reemplaza
          al truco de los case vacíos).
        - A la derecha de -> va UNA expresión, un bloque { } o un throw.

        Es la forma que debes usar siempre en código nuevo. (Tu Base.java
        ya la usaba con los días de la semana.)
    */
    static String dayType(String day) {
        String type;
        switch (day) {
            case "Lunes", "Martes", "Miércoles", "Jueves", "Viernes" -> type = "laboral";
            case "Sábado", "Domingo" -> type = "fin de semana";
            default -> throw new IllegalArgumentException("Día no válido: " + day);
        }
        return type;
    }

    static boolean arrowSwitchExample() {
        boolean invalidRejected;
        try {
            dayType("Funday");
            invalidRejected = false;
        } catch (IllegalArgumentException e) {
            invalidRejected = true;
        }
        return dayType("Martes").equals("laboral") && dayType("Domingo").equals("fin de semana") && invalidRejected;
    }

    /*
        =================================================================
        CAPA 9) SWITCH COMO EXPRESIÓN Y YIELD
        =================================================================

        Un switch también puede ser una EXPRESIÓN: produce un valor que
        se asigna o se devuelve directamente.

            String message = switch (day) {
                case "Lunes" -> "Inicio de semana";
                default -> "Otro día";
            };                                   // ¡lleva punto y coma: es una asignación!

        Ventajas sobre asignar en cada case:
        - La variable se asigna en UN solo lugar, y puede ser final.
        - El compilador EXIGE que todos los casos devuelvan algo: si
          falta un caso posible, no compila. Con String o int (infinitos
          valores posibles) eso significa que necesitas default. Con un
          enum o un tipo sealed, el compilador puede comprobar que cubriste
          todos sin default (Enums, CAPA 5; SealedAndPatternMatching, CAPA 4).

        YIELD: si un case necesita varias líneas, se usa un bloque { } y
        el valor del caso se entrega con yield (no con return, que
        saldría de todo el método):

            case 2 -> {
                boolean leap = ...;
                yield leap ? 29 : 28;
            }
    */
    static int daysInMonth(int month, int year) {
        return switch (month) {
            case 1, 3, 5, 7, 8, 10, 12 -> 31;
            case 4, 6, 9, 11 -> 30;
            case 2 -> {
                boolean leap = (year % 4 == 0 && year % 100 != 0) || year % 400 == 0;
                yield leap ? 29 : 28;
            }
            default -> throw new IllegalArgumentException("Mes inválido: " + month);
        };
    }

    // yield también funciona con la forma clásica de dos puntos dentro de un switch expresión.
    static String priority(int level) {
        return switch (level) {
            case 1:
                yield "alta";
            case 2:
                yield "media";
            default:
                yield "baja";
        };
    }

    static boolean switchExpressionExample() {
        return daysInMonth(4, 2026) == 30
                && daysInMonth(2, 2024) == 29     // bisiesto
                && daysInMonth(2, 1900) == 28     // divisible entre 100 pero no entre 400: no bisiesto
                && daysInMonth(2, 2000) == 29     // divisible entre 400: bisiesto
                && priority(2).equals("media");
    }

    /*
        =================================================================
        CAPA 10) QUÉ TIPOS ACEPTA SWITCH
        =================================================================

        Con constantes en los case, switch acepta:
        - int, y los que se promueven a int: byte, short, char
        - sus wrappers: Integer, Byte, Short, Character
        - String
        - enum

        NO acepta (como constantes) long, float, double ni boolean:
            switch (someLong) { case 1L -> ... }
            // ERROR: primitive patterns are a preview feature and are disabled by default.
        Por qué: switch se diseñó para saltar rápido a una posición según
        un entero pequeño. Con decimales, comparar por igualdad exacta no
        tiene sentido (PrimitiveTypes, CAPA 7). Java está agregando estos
        tipos como función "preview" (en prueba): todavía no forman parte
        estable del lenguaje.

        Desde Java 21, switch también acepta CUALQUIER objeto usando
        patrones de tipo (case CardPayment card ->): ver
        SealedAndPatternMatching.

        Dos detalles técnicos:
        - Con String, switch compara con equals (no con ==), así que
          compara el contenido. Distingue mayúsculas.
        - Si el valor es null, switch lanza NullPointerException, aunque
          tenga default. Desde Java 21 se puede manejar con case null.
    */
    static String commandType(String command) {
        return switch (command) {
            case null -> "vacío";
            case "start", "run" -> "iniciar";
            case "stop" -> "detener";
            default -> "desconocido";
        };
    }

    static String commandTypeWithoutNull(String command) {
        return switch (command) {
            case "start" -> "iniciar";
            default -> "desconocido";
        };
    }

    static boolean switchTypesExample() {
        String built = new StringBuilder("st").append("op").toString();   // otro objeto, mismo contenido

        boolean nullWithoutCaseNullFails;
        try {
            commandTypeWithoutNull(null);
            nullWithoutCaseNullFails = false;
        } catch (NullPointerException e) {
            nullWithoutCaseNullFails = true;
        }

        return commandType(built).equals("detener")      // compara contenido, no identidad
                && commandType("START").equals("desconocido")   // distingue mayúsculas
                && commandType(null).equals("vacío")
                && nullWithoutCaseNullFails;
    }

    /*
        =================================================================
        CAPA 11) CUÁNDO USAR IF, SWITCH, TERNARIO... O NINGUNO
        =================================================================

        | Situación                                          | Usa                 |
        |----------------------------------------------------|---------------------|
        | Una o dos condiciones, o condiciones distintas     | if / else           |
        | Rangos (>= 90, >= 80...)                           | if / else if        |
        | Elegir entre DOS valores simples                   | ternario            |
        | UN valor comparado contra muchas constantes        | switch con ->       |
        | Calcular un valor según muchos casos               | switch expresión    |
        | Validaciones al inicio de un método                | cláusulas de guarda |

        Y la pregunta de diseño más importante: ¿esta decisión depende
        del TIPO de algo, y se repite en varios lugares del sistema?
        Entonces quizás no deberías escribir el if/switch: el
        comportamiento debería vivir en el objeto (Polymorphism, CAPA 2:
        "reemplazar condicional con polimorfismo"), en un enum con
        comportamiento (Enums, CAPA 4), o en un switch sobre un tipo
        sealed que el compilador revise (SealedAndPatternMatching).
    */

    /*
        =================================================================
        CAPA 12) ERRORES COMUNES
        =================================================================

        1) Omitir las llaves de un if: la sangría engaña (CAPA 2).

        2) Usar números u objetos como condición: if (count) (CAPA 2).

        3) Ordenar mal los else if con rangos (CAPA 3).

        4) Pirámides de if anidados en lugar de cláusulas de guarda
           (CAPA 4).

        5) if (x == true), o if (cond) return true; else return false;
           (CAPA 5).

        6) Ternarios anidados, o mezclar tipos numéricos en sus ramas
           (CAPA 6).

        7) Olvidar break en un switch clásico (CAPA 7).

        8) Usar return en lugar de yield dentro de un bloque de un switch
           expresión, u olvidar el ; al final del switch expresión (CAPA 9).

        9) Hacer switch sobre un String que puede ser null sin case null
           (CAPA 10).

        10) Repetir el mismo if/switch por tipo en muchos lugares en vez
            de usar polimorfismo (CAPA 11).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - if/else elige caminos; la condición es siempre boolean. Llaves
          siempre.
        - else if: se ejecuta solo la primera condición verdadera; el
          orden importa.
        - Cláusulas de guarda: valida y sal temprano; el camino principal
          queda limpio.
        - Nombra las condiciones complejas; devuelve la condición
          directamente; De Morgan para negarlas.
        - Ternario: expresión para elegir entre dos valores simples. Ojo
          con la promoción de tipos.
        - switch clásico (:) tiene fall-through y necesita break.
        - switch con ->: sin fall-through, varios valores por case. Úsalo
          siempre.
        - switch expresión: devuelve un valor, exige cubrir todos los
          casos, yield para bloques.
        - switch acepta int y menores, String, enum y wrappers (y objetos
          con patrones). null lanza NPE salvo case null.
        - Si la decisión es por tipo y se repite, piensa en polimorfismo.
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
