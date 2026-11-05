package v3_exceptions.part1_concepts;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

/*
    =====================================================================
    QUÉ SON LAS EXCEPCIONES — DE CERO A NIVEL TÉCNICO
    =====================================================================

    Ruta de v3_exceptions/: guía 1 de 5  (part1_concepts)
    Anterior:  — (es la primera; viene de v2_oop/part4_advanced_types/SealedAndPatternMatching)
    Siguiente: part1_concepts/CheckedVsUnchecked

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.

    Índice:
        CAPA 1)  El problema: cómo avisa un método que algo salió mal
        CAPA 2)  Qué es una excepción
        CAPA 3)  throw: lanzar una excepción
        CAPA 4)  try / catch: atraparla
        CAPA 5)  Propagación: la excepción sube por la pila
        CAPA 6)  finally: lo que se ejecuta pase lo que pase
        CAPA 7)  Varios catch y multi-catch
        CAPA 8)  La jerarquía de excepciones
        CAPA 9)  Leer un stack trace
        CAPA 10) Qué pasa si nadie la atrapa
        CAPA 11) Errores comunes
        RESUMEN FINAL
*/
public class WhatAreExceptions {

    public static void main(String[] args) {
        check(errorCodesProblemExample(), "CAPA 1");
        check(throwExample(), "CAPA 3");
        check(tryCatchFlowExample(), "CAPA 4");
        check(propagationExample(), "CAPA 5");
        check(finallyExample(), "CAPA 6");
        check(multipleCatchExample(), "CAPA 7");
        check(hierarchyExample(), "CAPA 8");
        check(stackTraceExample(), "CAPA 9");
        System.out.println("WhatAreExceptions: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) EL PROBLEMA: CÓMO AVISA UN MÉTODO QUE ALGO SALIÓ MAL
        =================================================================

        Un método puede encontrarse con algo que le impide hacer su
        trabajo: un texto que no es un número, un retiro mayor al saldo,
        un archivo que no existe. ¿Cómo se lo dice a quien lo llamó?

        La forma antigua (la de C) era devolver un valor especial: -1,
        null, false. Tiene tres problemas graves:

        1) SE PUEDE IGNORAR EN SILENCIO. Nada obliga a revisar el -1. Si
           quien llama lo olvida, el programa sigue con un dato inválido
           y el error aparece mucho después, lejos de su causa.
        2) SE MEZCLA CON LOS RESULTADOS VÁLIDOS. Si -1 significa "error",
           ¿qué devuelve un método cuyo resultado correcto puede ser -1?
        3) HAY QUE PASARLO A MANO por cada nivel: si A llama a B, que
           llama a C, y C falla, B tiene que revisar el código de C y
           devolverlo a A. El código se llena de "if (resultado == -1)".
    */
    static int parseAgeOrMinusOne(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return -1;                                   // "código de error"
        }
    }

    static boolean errorCodesProblemExample() {
        int age = parseAgeOrMinusOne("abc");
        int ageNextYear = age + 1;                      // nadie revisó el -1: el error sigue en silencio
        return ageNextYear == 0;                         // un resultado absurdo, sin ninguna señal
    }

    /*
        =================================================================
        CAPA 2) QUÉ ES UNA EXCEPCIÓN
        =================================================================

        Una EXCEPCIÓN es la forma de Java de decir "no puedo continuar
        normalmente". Es un OBJETO que describe el problema (qué tipo de
        error, un mensaje, dónde ocurrió), y que interrumpe el flujo
        normal del programa.

        Resuelve los tres problemas de la CAPA 1:
        1) NO SE PUEDE IGNORAR: si nadie la atiende, el programa se
           detiene y muestra exactamente qué pasó (CAPA 10).
        2) VA POR UN CANAL APARTE: no ocupa el valor de retorno, así que
           el método puede devolver cualquier resultado válido.
        3) VIAJA SOLA hacia arriba por la pila de llamadas hasta que
           alguien la atienda (CAPA 5). Los niveles intermedios no tienen
           que hacer nada.

        El vocabulario:
        - LANZAR (throw): crear la excepción y "dispararla".
        - ATRAPAR (catch): atenderla en algún punto de la pila.
        - PROPAGAR: dejar que siga subiendo.

        Como decía tu OOP.java: en programación orientada a objetos las
        excepciones suelen expresar REGLAS DEL NEGOCIO que no se pueden
        romper: no retirar más de lo disponible, no crear un producto sin
        nombre (Encapsulation, CAPA 4).
    */

    /*
        =================================================================
        CAPA 3) THROW: LANZAR UNA EXCEPCIÓN
        =================================================================

            throw new TipoDeExcepcion("mensaje que explica qué pasó");

        - throw interrumpe el método AHÍ MISMO: las líneas siguientes no
          se ejecutan, y no se devuelve ningún valor.
        - El TIPO de excepción dice qué clase de problema es (CAPA 8).
        - El MENSAJE es para la persona que va a leer el error: debe decir
          qué valor era incorrecto y por qué. "Error" no sirve; "No se
          puede retirar 2000: el saldo es 700" sí.

        La cuenta bancaria de tu OOP.java, con mensajes útiles:
    */
    static final class BankAccount {
        private final String owner;
        private int balance;

        BankAccount(String owner, int initialBalance) {
            if (owner == null || owner.isBlank()) {
                throw new IllegalArgumentException("El titular es obligatorio");
            }
            if (initialBalance < 0) {
                throw new IllegalArgumentException("El saldo inicial no puede ser negativo: " + initialBalance);
            }
            this.owner = owner;
            this.balance = initialBalance;
        }

        void withdraw(int amount) {
            if (amount <= 0) {
                throw new IllegalArgumentException("El retiro debe ser mayor que cero: " + amount);
            }
            if (amount > balance) {
                throw new IllegalStateException("No se puede retirar " + amount + ": el saldo es " + balance);
            }
            balance -= amount;   // solo llega aquí si las reglas se cumplen
        }

        int getBalance() {
            return balance;
        }

        String getOwner() {
            return owner;
        }
    }

    static boolean throwExample() {
        BankAccount account = new BankAccount("Ana", 1000);
        account.withdraw(300);

        String message;
        try {
            account.withdraw(2000);
            message = "";
        } catch (IllegalStateException e) {
            message = e.getMessage();
        }

        // El saldo no cambió: throw cortó el método antes de restar.
        return account.getBalance() == 700 && message.equals("No se puede retirar 2000: el saldo es 700");
    }

    /*
        (IllegalArgumentException: el ARGUMENTO es inválido, como un
        retiro negativo. IllegalStateException: el argumento está bien,
        pero el ESTADO del objeto no lo permite ahora, como un saldo
        insuficiente. La elección de tipos se profundiza en
        part2_in_practice/ErrorHandlingBestPractices.)
    */

    /*
        =================================================================
        CAPA 4) TRY / CATCH: ATRAPARLA
        =================================================================

            try {
                código que podría lanzar una excepción
            } catch (TipoDeExcepcion e) {
                qué hacer si se lanzó
            }

        El flujo exacto, que conviene tener muy claro:
        1) Se ejecuta el try línea por línea.
        2) Si una línea lanza una excepción, el resto del try SE SALTA
           (como decía tu OOP.java: "esta línea no se ejecuta").
        3) Java busca un catch cuyo tipo coincida. Si lo encuentra, ejecuta
           ese bloque, con la excepción en la variable e.
        4) Después del catch, el programa CONTINÚA normalmente con lo que
           sigue a todo el try/catch.
        Si no se lanzó nada, el catch simplemente no se ejecuta.

        La variable e es el objeto excepción. Sus métodos más usados:
            e.getMessage()      el mensaje
            e.getClass()        el tipo
            e.getCause()        la excepción que la provocó, si hay (CustomExceptions)
            e.printStackTrace() imprime el stack trace en System.err (CAPA 9)
    */
    static boolean tryCatchFlowExample() {
        List<String> steps = new ArrayList<>();
        BankAccount account = new BankAccount("Ana", 1000);

        try {
            steps.add("retiro 300");
            account.withdraw(300);
            steps.add("retiro 2000");
            account.withdraw(2000);                      // lanza
            steps.add("nunca se ejecuta");               // se salta
        } catch (IllegalStateException e) {
            steps.add("catch: " + e.getClass().getSimpleName());
        }
        steps.add("después del try");                    // el programa sigue

        return steps.equals(List.of("retiro 300", "retiro 2000", "catch: IllegalStateException", "después del try"));
    }

    /*
        =================================================================
        CAPA 5) PROPAGACIÓN: LA EXCEPCIÓN SUBE POR LA PILA
        =================================================================

        Si el método donde se lanza la excepción no la atrapa, la
        excepción SALE de ese método y aparece en el método que lo llamó,
        justo en la línea de la llamada. Si ese tampoco la atrapa, sale
        también, y así sucesivamente, subiendo por la pila de llamadas
        (Methods, CAPA 9).

            checkout()   <- atrapa aquí
              └ pay()     no atrapa: la excepción pasa de largo
                  └ withdraw()   lanza

        A esto se le llama "desenrollar la pila": cada método que la
        excepción atraviesa TERMINA en ese punto. Lo que pay() iba a hacer
        después de llamar a withdraw() no se ejecuta.

        Por qué es la gran ventaja: withdraw() no tiene que saber quién
        lo llamó ni cómo se le avisa al usuario, y pay() no necesita
        código para "pasar el error hacia arriba". Cada nivel atrapa solo
        lo que sabe resolver. Normalmente, se atrapa en el nivel más
        ALTO, el que puede decidir qué hacer (mostrar un mensaje,
        reintentar, cancelar la compra).
    */
    static void pay(BankAccount account, int amount, List<String> log) {
        log.add("pay: inicio");
        account.withdraw(amount);                       // si lanza, pay termina aquí
        log.add("pay: pago registrado");
    }

    static String checkout(BankAccount account, int amount, List<String> log) {
        try {
            pay(account, amount, log);
            return "Compra completada";
        } catch (IllegalStateException e) {
            log.add("checkout: atrapada");
            return "Compra rechazada: " + e.getMessage();
        }
    }

    static boolean propagationExample() {
        List<String> log = new ArrayList<>();
        String result = checkout(new BankAccount("Ana", 100), 500, log);

        return result.equals("Compra rechazada: No se puede retirar 500: el saldo es 100")
                && log.equals(List.of("pay: inicio", "checkout: atrapada"));   // "pago registrado" nunca ocurrió
    }

    /*
        =================================================================
        CAPA 6) FINALLY: LO QUE SE EJECUTA PASE LO QUE PASE
        =================================================================

            try { ... } catch (...) { ... } finally {
                se ejecuta SIEMPRE: haya excepción o no, se atrape o no,
                e incluso si el try o el catch hacen return
            }

        Para qué: LIMPIEZA. Cerrar un archivo, liberar una conexión,
        quitar un bloqueo, restaurar un estado. Cosas que deben pasar sin
        importar cómo terminó el trabajo.
        (Para cerrar recursos existe una forma mejor, try-with-resources:
        part2_in_practice/TryWithResources.)

        La única forma de saltarse un finally es apagar la JVM
        (System.exit) o que muera el proceso.

        TRAMPA: NUNCA pongas return dentro de un finally. Si el try lanzó
        una excepción, el return del finally la DESCARTA en silencio y el
        método termina como si todo hubiera salido bien. El compilador lo
        advierte con -Xlint:
            warning: [finally] finally clause cannot complete normally
    */
    static int finallyRuns;

    static String withFinally(boolean fail) {
        try {
            if (fail) {
                throw new IllegalStateException("falló");
            }
            return "ok";                                  // el finally corre ANTES de salir con este return
        } finally {
            finallyRuns++;
        }
    }

    @SuppressWarnings("finally") // a propósito: es el error que se quiere mostrar
    static String returnInFinallySwallows() {
        try {
            throw new IllegalStateException("este error se pierde");
        } finally {
            return "todo bien";                           // descarta la excepción
        }
    }

    static boolean finallyExample() {
        finallyRuns = 0;
        String normal = withFinally(false);
        try {
            withFinally(true);
        } catch (IllegalStateException e) {
            // esperado
        }

        return normal.equals("ok")
                && finallyRuns == 2                            // corrió en los dos casos
                && returnInFinallySwallows().equals("todo bien");   // la excepción desapareció
    }

    /*
        =================================================================
        CAPA 7) VARIOS CATCH Y MULTI-CATCH
        =================================================================

        Un try puede tener varios catch, uno por tipo de problema. Java
        los prueba DE ARRIBA A ABAJO y usa el PRIMERO que coincida.

        Por eso el orden importa: los tipos ESPECÍFICOS van antes que los
        generales. Si pones primero uno general, el específico nunca se
        alcanzaría, y el compilador lo rechaza:
            catch (Exception e) { ... }
            catch (NumberFormatException e) { ... }
            // ERROR: exception NumberFormatException has already been caught

        MULTI-CATCH (Java 7): si varios tipos se manejan IGUAL, se juntan
        con |:
            catch (NumberFormatException | ArithmeticException e) { ... }
        Los tipos no pueden estar emparentados (si uno es hijo del otro,
        con poner el padre bastaba):
            catch (NumberFormatException | IllegalArgumentException e)
            // ERROR: Alternatives in a multi-catch statement cannot be related by subclassing
    */
    static String divide100By(String text) {
        try {
            int divisor = Integer.parseInt(text);
            return "Resultado: " + (100 / divisor);
        } catch (NumberFormatException | ArithmeticException e) {   // mismo manejo para los dos
            return "Entrada inválida (" + e.getClass().getSimpleName() + ")";
        }
    }

    static String classify(String text) {
        try {
            int divisor = Integer.parseInt(text);
            return "Resultado: " + (100 / divisor);
        } catch (NumberFormatException e) {                 // específico: el texto no es número
            return "No es un número";
        } catch (RuntimeException e) {                      // general: cualquier otro error de ejecución
            return "Otro error: " + e.getClass().getSimpleName();
        }
    }

    static boolean multipleCatchExample() {
        return divide100By("4").equals("Resultado: 25")
                && divide100By("abc").equals("Entrada inválida (NumberFormatException)")
                && divide100By("0").equals("Entrada inválida (ArithmeticException)")
                && classify("abc").equals("No es un número")
                && classify("0").equals("Otro error: ArithmeticException");
    }

    /*
        =================================================================
        CAPA 8) LA JERARQUÍA DE EXCEPCIONES
        =================================================================

        Las excepciones son clases, y forman una jerarquía de herencia
        (Inheritance). Por eso catch (RuntimeException e) atrapa también a
        todas sus hijas: un NumberFormatException "ES UN" RuntimeException.

                                 Throwable
                         ┌───────────┴───────────┐
                       Error                  Exception
              (la JVM tiene un problema   ┌──────────┴───────────┐
               grave: NO se atrapan)   IOException,        RuntimeException
              OutOfMemoryError,        SQLException...     (errores de programación)
              StackOverflowError       ("checked")         ("unchecked")
                                                           NullPointerException
                                                           IllegalArgumentException
                                                             └ NumberFormatException
                                                           IllegalStateException
                                                           IndexOutOfBoundsException
                                                           ArithmeticException
                                                           ClassCastException
                                                           UnsupportedOperationException

        - Throwable: la raíz. Solo lo que es Throwable se puede lanzar.
        - Error: problemas de la JVM o del entorno (se acabó la memoria,
          se llenó la pila). Tu programa normalmente no puede hacer nada
          razonable: no se atrapan.
        - Exception: problemas que una aplicación SÍ puede querer atender.
        - La división entre RuntimeException (unchecked) y el resto de
          Exception (checked) es tan importante que tiene su propia guía:
          CheckedVsUnchecked.

        Las que más vas a ver, y qué suelen significar:
        | Excepción                      | Causa típica                                  |
        |--------------------------------|-----------------------------------------------|
        | NullPointerException           | usar una referencia null (ReferencesAndNull)   |
        | IllegalArgumentException       | un argumento inválido                         |
        | IllegalStateException          | el objeto no está en estado de hacer eso      |
        | NumberFormatException          | texto que no es un número (parseInt)          |
        | ArrayIndexOutOfBoundsException | posición inexistente (Loops, CAPA 5)         |
        | ArithmeticException            | división entera entre cero                    |
        | ClassCastException             | cast al tipo equivocado (Polymorphism)        |
        | UnsupportedOperationException  | modificar una colección inmutable             |
        | ConcurrentModificationException| cambiar una colección mientras se recorre     |
        | IOException                    | falló leer/escribir archivos o red (checked)  |
    */
    static boolean hierarchyExample() {
        Throwable numberFormat = new NumberFormatException("abc");
        Throwable stackOverflow = new StackOverflowError();

        return numberFormat instanceof IllegalArgumentException    // NumberFormatException es hija de IllegalArgumentException
                && numberFormat instanceof RuntimeException
                && numberFormat instanceof Exception
                && stackOverflow instanceof Error
                && !(stackOverflow instanceof Exception);           // un Error NO es una Exception
    }

    /*
        =================================================================
        CAPA 9) LEER UN STACK TRACE
        =================================================================

        Cuando una excepción no se atrapa (o se imprime con
        printStackTrace), Java muestra su STACK TRACE: la foto de la pila
        de llamadas en el momento en que se lanzó (Methods, CAPA 9).

            java.lang.IllegalStateException: No se puede retirar 500: el saldo es 100
                at exceptions...BankAccount.withdraw(WhatAreExceptions.java:<línea>)
                at exceptions...WhatAreExceptions.pay(WhatAreExceptions.java:<línea>)
                at exceptions...WhatAreExceptions.checkout(WhatAreExceptions.java:<línea>)
                at exceptions...WhatAreExceptions.main(...)

        Cómo leerlo, en orden:
        1) PRIMERA LÍNEA: el TIPO y el MENSAJE. Ya dice qué pasó.
        2) PRIMER "at": DÓNDE se lanzó: clase, método, archivo y número de
           línea exacto.
        3) Los siguientes "at": QUIÉN llamó a quién, hacia abajo. Busca la
           primera línea que sea de TU código (las de java.base son del
           JDK): ahí suele estar la causa real.
        4) Si hay líneas "Caused by:", la excepción fue provocada por
           otra; la causa original es la ÚLTIMA "Caused by"
           (part2_in_practice/CustomExceptions).

        Leer el stack trace completo antes de tocar el código ahorra
        horas: casi siempre dice exactamente qué falló y dónde.
    */
    static boolean stackTraceExample() {
        IllegalStateException captured = null;
        try {
            pay(new BankAccount("Ana", 100), 500, new ArrayList<>());
        } catch (IllegalStateException e) {
            captured = e;
        }

        StringWriter text = new StringWriter();
        captured.printStackTrace(new PrintWriter(text));   // el mismo texto que verías en la consola
        String trace = text.toString();

        StackTraceElement top = captured.getStackTrace()[0];

        return trace.startsWith("java.lang.IllegalStateException: No se puede retirar 500: el saldo es 100")
                && top.getMethodName().equals("withdraw")              // el primer "at": donde se lanzó
                && captured.getStackTrace()[1].getMethodName().equals("pay")
                && top.getLineNumber() > 0;
    }

    /*
        =================================================================
        CAPA 10) QUÉ PASA SI NADIE LA ATRAPA
        =================================================================

        Si la excepción sube hasta main y main tampoco la atrapa:
        1) El hilo de ejecución termina.
        2) Java imprime "Exception in thread "main"" seguido del stack
           trace, en System.err.
        3) El programa termina con código de salida 1 (InputAndOutput,
           CAPA 8), así que un script sabe que falló.

        Un programa de un solo archivo que lance algo sin atraparlo
        muestra exactamente esto:
            Exception in thread "main" java.lang.IllegalStateException: Fondos insuficientes
                at Programa.main(Programa.java:1)

        Que el programa se detenga NO es lo peor que puede pasar. Lo peor
        es atrapar la excepción y no hacer nada ("tragarla"), porque
        entonces el error desaparece sin dejar rastro y el programa sigue
        con datos inválidos, igual que con los códigos de error de la
        CAPA 1 (ErrorHandlingBestPractices).
    */

    /*
        =================================================================
        CAPA 11) ERRORES COMUNES
        =================================================================

        1) Devolver -1 o null para señalar errores que deberían ser
           excepciones (CAPA 1).

        2) Mensajes inútiles: throw new Exception("Error") (CAPA 3).

        3) Esperar que el código después de la línea que lanzó dentro del
           try se ejecute (CAPA 4).

        4) Atrapar en el nivel equivocado, demasiado abajo, donde no se
           sabe qué hacer con el error (CAPA 5).

        5) return dentro de finally: descarta la excepción (CAPA 6).

        6) Poner un catch general antes de uno específico (CAPA 7).

        7) Atrapar Error o Throwable (CAPA 8).

        8) Leer solo la primera línea del stack trace, o buscar la causa
           en las líneas del JDK en vez de en tu código (CAPA 9).

        9) catch vacío: "tragarse" la excepción (CAPA 10).

        10) Usar excepciones para el flujo normal del programa, como
            terminar un ciclo (ErrorHandlingBestPractices).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Los códigos de error (-1, null) se ignoran, se mezclan con los
          resultados y hay que pasarlos a mano. Las excepciones no.
        - Una excepción es un objeto que describe el problema y corta el
          flujo normal.
        - throw la lanza (con un tipo y un mensaje útil); try/catch la
          atrapa; lo que sigue a la línea que lanzó dentro del try se salta.
        - Si nadie la atrapa, sube por la pila terminando cada método que
          atraviesa. Atrapa donde se pueda decidir qué hacer.
        - finally corre siempre: limpieza. Nunca return en finally.
        - Varios catch: de específico a general. Multi-catch con |.
        - Jerarquía: Throwable -> Error (no se atrapa) / Exception ->
          RuntimeException (unchecked) / el resto (checked).
        - Stack trace: tipo y mensaje, dónde se lanzó, quién llamó.
        - Sin atrapar: stack trace en System.err y código de salida 1.
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
