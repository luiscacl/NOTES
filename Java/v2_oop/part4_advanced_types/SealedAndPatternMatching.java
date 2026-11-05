package v2_oop.part4_advanced_types;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

/*
    =====================================================================
    SEALED Y PATTERN MATCHING — DE INTERMEDIO A NIVEL TÉCNICO
    =====================================================================

    Ruta de v2_oop/: guía 14 de 14  (part4_advanced_types)
    Anterior:  part4_advanced_types/ObjectContract
    Siguiente: v3_exceptions/part1_concepts/WhatAreExceptions (última de v2_oop/)

    Requisito: Polymorphism, Inheritance (CAPA 8: sealed),
    Enums (CAPA 11) y Records.

    Esta es la ÚLTIMA guía de v2_oop/. Junta todo: interfaces, records,
    sealed y el switch moderno, para modelar datos de una forma que
    Java permite desde la versión 21.

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea y JUSTO DEBAJO está el código que la
      demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.

    Índice:
        CAPA 1)  El problema: variantes con datos distintos
        CAPA 2)  Jerarquías sealed de records
        CAPA 3)  Patrones de tipo con instanceof: alcance por flujo
        CAPA 4)  switch con patrones y exhaustividad
        CAPA 5)  Record patterns: desarmar un record
        CAPA 6)  Patrones anidados, var y el patrón _
        CAPA 7)  Guardas when y el orden de los case
        CAPA 8)  null en un switch
        CAPA 9)  Modelar resultados: tipos suma y producto
        CAPA 10) Polimorfismo o pattern matching: cómo decidir
        CAPA 11) Errores comunes
        RESUMEN FINAL
*/
public class SealedAndPatternMatching {

    public static void main(String[] args) {
        check(sealedHierarchyExample(), "CAPA 2");
        check(instanceofFlowScopingExample(), "CAPA 3");
        check(typePatternSwitchExample(), "CAPA 4");
        check(recordPatternExample(), "CAPA 5");
        check(nestedPatternExample(), "CAPA 6");
        check(guardsExample(), "CAPA 7");
        check(nullCaseExample(), "CAPA 8");
        check(resultTypeExample(), "CAPA 9");
        System.out.println("SealedAndPatternMatching: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) EL PROBLEMA: VARIANTES CON DATOS DISTINTOS
        =================================================================

        Un pago puede ser de tres formas, y CADA UNA necesita datos
        distintos:
        - Tarjeta:       número de tarjeta y meses sin intereses
        - Efectivo:      cuánto entregó el cliente
        - Transferencia: cuenta CLABE y banco de origen

        Herramientas que ya conoces y por qué no encajan del todo:
        - Un ENUM da los mismos campos a todas sus constantes, y cada
          constante existe una sola vez. No puede guardar "el número de
          ESTA tarjeta" (Enums, CAPA 11).
        - Una clase con TODOS los campos posibles (cardNumber, received,
          clabe...) deja la mayoría en null según el tipo: un objeto
          lleno de huecos y de if (tipo == ...).
        - Una INTERFACE con implementaciones (Polymorphism, CAPA 3)
          funciona muy bien cuando cada variante debe saber HACER algo.
          Pero si muchas partes distintas del sistema necesitan tratar
          los pagos de forma distinta (calcular comisión, mostrar,
          validar, reportar, exportar...), habría que meter TODAS esas
          operaciones dentro de cada clase de pago.

        Java moderno ofrece otra forma, "orientada a datos":
        1) Cada variante es un RECORD con exactamente sus datos.
        2) Un tipo SEALED dice: "estas son TODAS las variantes; no hay
           más".
        3) Las operaciones se escriben FUERA, con un switch que el
           compilador verifica que cubra todas las variantes.
    */

    /*
        =================================================================
        CAPA 2) JERARQUÍAS SEALED DE RECORDS
        =================================================================

        sealed (Inheritance, CAPA 8) limita quién puede implementar o
        heredar de un tipo. Combinado con records:

            sealed interface Payment permits CardPayment, CashPayment, TransferPayment { }

        - "sealed": solo los tipos listados en permits pueden implementarla.
        - Los records son implícitamente final, así que la jerarquía
          queda CERRADA por completo: el compilador conoce TODAS las
          formas posibles de un Payment.
        - Si todos están en el mismo archivo, permits se puede omitir
          (aquí se escribe para que se vea la lista).
        - Los tipos permitidos deben estar en el mismo módulo (o en el
          mismo paquete si no usas módulos).

        Intentar agregar una variante fuera de la lista no compila:
            record CryptoPayment(String wallet) implements Payment { }
            // ERROR: class is not allowed to extend sealed class: Payment
            // (as it is not listed in its 'permits' clause)
    */
    sealed interface Payment permits CardPayment, CashPayment, TransferPayment { }

    record CardPayment(String cardNumber, int installments) implements Payment {
        CardPayment {
            Objects.requireNonNull(cardNumber, "cardNumber");
            if (!cardNumber.matches("\\d{16}")) {
                throw new IllegalArgumentException("La tarjeta debe tener 16 dígitos");
            }
            if (installments < 1) {
                throw new IllegalArgumentException("Al menos un pago");
            }
        }
    }

    record CashPayment(BigDecimal received) implements Payment {
        CashPayment {
            Objects.requireNonNull(received, "received");
        }
    }

    record Bank(String name, String country) {
        Bank {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(country, "country");
        }
    }

    record TransferPayment(String clabe, Bank bank) implements Payment {
        TransferPayment {
            Objects.requireNonNull(clabe, "clabe");
            Objects.requireNonNull(bank, "bank");
        }
    }

    static boolean sealedHierarchyExample() {
        List<Payment> payments = samplePayments();

        // El compilador sabe que un Payment solo puede ser una de estas tres cosas.
        Class<?>[] permitted = Payment.class.getPermittedSubclasses();

        return payments.size() == 4 && permitted.length == 3 && Payment.class.isSealed();
    }

    /*
        Recuerda las tres opciones para cada tipo permitido (Inheritance,
        CAPA 8): final (los records ya lo son), sealed (con su propia
        lista) o non-sealed (se vuelve a abrir). Con records, la
        jerarquía siempre termina ahí.
    */

    /*
        =================================================================
        CAPA 3) PATRONES DE TIPO CON INSTANCEOF: ALCANCE POR FLUJO
        =================================================================

        Ya viste el patrón de tipo en Polymorphism, CAPA 7:
            if (payment instanceof CardPayment card) { ...usa card... }

        Lo que falta saber es DÓNDE existe la variable "card". Java usa
        "alcance por flujo": la variable existe en todos los lugares donde
        el compilador puede GARANTIZAR que el patrón se cumplió.

        - Dentro del if:                    if (x instanceof A a) { a... }
        - En el resto de un &&:             x instanceof A a && a.algo()
        - DESPUÉS de un if negado que sale:
              if (!(x instanceof A a)) {
                  return ...;          // si no era A, ya salimos
              }
              a.algo();                // aquí es seguro: a existe

        La última forma ("salir temprano") evita anidar todo el método
        dentro de un if.

        En un || NO existe: x instanceof A a || a.algo() no compila,
        porque si la primera parte es false, a nunca se asignó.
    */
    static String maskedCard(Payment payment) {
        if (!(payment instanceof CardPayment card)) {
            return "no es pago con tarjeta";
        }
        // card existe aquí: el if de arriba garantiza que el pago es CardPayment.
        return "**** " + card.cardNumber().substring(12);
    }

    static boolean isMultiInstallment(Payment payment) {
        return payment instanceof CardPayment card && card.installments() > 1;   // card existe tras el &&
    }

    static boolean instanceofFlowScopingExample() {
        Payment card = new CardPayment("4111111111111111", 6);
        Payment cash = new CashPayment(new BigDecimal("500"));

        return maskedCard(card).equals("**** 1111")
                && maskedCard(cash).equals("no es pago con tarjeta")
                && isMultiInstallment(card)
                && !isMultiInstallment(cash);
    }

    /*
        =================================================================
        CAPA 4) SWITCH CON PATRONES Y EXHAUSTIVIDAD
        =================================================================

        Desde Java 21, cada case de un switch puede ser un PATRÓN DE TIPO:

            switch (payment) {
                case CardPayment card -> ...
                case CashPayment cash -> ...
            }

        Y aquí está la gran ventaja de sealed: como el compilador conoce
        TODAS las variantes, verifica que el switch las cubra todas. No
        hace falta default.

        Si mañana se agrega una cuarta variante a permits, TODO switch
        que no la contemple deja de compilar:
            // ERROR: the switch expression does not cover all possible input values
        El compilador te muestra cada lugar del sistema que debes
        actualizar. Con una cadena de if instanceof ... else, el caso
        nuevo caería en el último else sin que nadie se entere.

        Ejemplo: la comisión que cobra la tienda por cada forma de pago.
        Esta operación vive FUERA de los records: es una regla de la
        tienda, no algo que el pago "sepa".
    */
    static BigDecimal commission(Payment payment, BigDecimal total) {
        BigDecimal rate = switch (payment) {
            case CardPayment card -> card.installments() > 1 ? new BigDecimal("0.035") : new BigDecimal("0.02");
            case CashPayment cash -> BigDecimal.ZERO;
            case TransferPayment transfer -> new BigDecimal("0.005");
            // sin default: el compilador ya sabe que no hay más casos
        };
        return total.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }

    static boolean typePatternSwitchExample() {
        BigDecimal total = new BigDecimal("1000");
        return commission(new CardPayment("4111111111111111", 1), total).equals(new BigDecimal("20.00"))
                && commission(new CardPayment("4111111111111111", 12), total).equals(new BigDecimal("35.00"))
                && commission(new CashPayment(total), total).signum() == 0;
    }

    /*
        Un switch con patrones puede recibir CUALQUIER tipo de objeto, no
        solo enums, String o números como el switch clásico. Si el tipo
        no es sealed (por ejemplo, Object), el compilador no puede saber
        cuáles son todos los casos y te exige un default.
    */

    /*
        =================================================================
        CAPA 5) RECORD PATTERNS: DESARMAR UN RECORD
        =================================================================

        Un RECORD PATTERN (Java 21) no solo pregunta el tipo: DESARMA el
        record y saca sus componentes a variables, en el mismo paso.

            case CardPayment(String number, int installments) -> ...
                             ^^^^^^^^^^^^^  ^^^^^^^^^^^^^^^^
                             componentes, en el orden en que se declararon

        Es el equivalente a escribir:
            case CardPayment card -> {
                String number = card.cardNumber();
                int installments = card.installments();
                ...
            }

        Los nombres de las variables los eliges tú (no tienen que
        coincidir con los del record); lo que importa es el ORDEN y el
        TIPO. Funciona igual con instanceof:
            if (payment instanceof CashPayment(BigDecimal received)) { ... }
    */
    static String describe(Payment payment) {
        return switch (payment) {
            case CardPayment(String number, int installments) ->
                    "Tarjeta terminación " + number.substring(12) + (installments > 1 ? " a " + installments + " meses" : "");
            case CashPayment(BigDecimal received) -> "Efectivo, recibido $" + received;
            case TransferPayment(String clabe, Bank bank) -> "Transferencia desde " + bank.name();
        };
    }

    static boolean recordPatternExample() {
        boolean viaInstanceof = new CashPayment(new BigDecimal("500")) instanceof CashPayment(BigDecimal received)
                && received.compareTo(new BigDecimal("500")) == 0;

        return describe(new CardPayment("4111111111111111", 6)).equals("Tarjeta terminación 1111 a 6 meses")
                && describe(new CashPayment(new BigDecimal("500"))).equals("Efectivo, recibido $500")
                && viaInstanceof;
    }

    /*
        =================================================================
        CAPA 6) PATRONES ANIDADOS, VAR Y EL PATRÓN _
        =================================================================

        Los record patterns se pueden ANIDAR: si un componente es otro
        record, también se puede desarmar en el mismo patrón.

            case TransferPayment(String clabe, Bank(String name, String country)) -> ...

        Dos ayudas para escribir menos:
        - var: deja que el compilador deduzca el tipo del componente.
              case TransferPayment(var clabe, Bank(var name, var country))
        - _ (Java 22+, "patrón sin nombre"): para componentes que NO
          usas. Deja claro que se ignoran a propósito y evita variables
          sin usar.
              case CardPayment(_, int installments)
              case CashPayment _                     // el tipo, sin variable
    */
    static BigDecimal transferFee(Payment payment) {
        return switch (payment) {
            case TransferPayment(_, Bank(_, var country)) when !country.equals("MX") -> new BigDecimal("150");
            case TransferPayment _ -> BigDecimal.ZERO;   // nacionales: sin costo
            case CardPayment _, CashPayment _ -> BigDecimal.ZERO;
        };
    }

    static boolean nestedPatternExample() {
        Payment domestic = new TransferPayment("012345678901234567", new Bank("BBVA", "MX"));
        Payment international = new TransferPayment("012345678901234567", new Bank("Chase", "US"));

        return transferFee(domestic).signum() == 0
                && transferFee(international).compareTo(new BigDecimal("150")) == 0
                && transferFee(new CashPayment(BigDecimal.TEN)).signum() == 0;
    }

    /*
        Detalle: "case CardPayment _, CashPayment _ ->" agrupa varios
        patrones en un solo case. Solo se permite cuando NINGUNO declara
        variables (de otro modo, ¿cuáles existirían en el cuerpo?).
    */

    /*
        =================================================================
        CAPA 7) GUARDAS WHEN Y EL ORDEN DE LOS CASE
        =================================================================

        Una GUARDA agrega una condición a un patrón:

            case CashPayment(BigDecimal received) when received.compareTo(total) < 0 -> ...

        El case solo se elige si el patrón coincide Y la condición es
        verdadera. Si no, se sigue probando con los siguientes case.

        Por eso el ORDEN importa: Java prueba los case de arriba a abajo
        y usa el PRIMERO que coincida. Regla: los casos ESPECÍFICOS (con
        guarda) van ANTES que los generales (sin guarda).

        Si pones un case general antes que uno específico del mismo
        tipo, el específico nunca podría ejecutarse, y el compilador lo
        rechaza:
            case CashPayment cash -> ...
            case CashPayment cash when ... -> ...
            // ERROR: this case label is dominated by a preceding case label

        Importante para la exhaustividad: una guarda puede ser falsa, así
        que un case con guarda NO cuenta como "cubierto". Siempre debe
        haber un case del mismo tipo sin guarda (o un default) después.
    */
    static String validate(Payment payment, BigDecimal total) {
        return switch (payment) {
            case CashPayment(BigDecimal received) when received.compareTo(total) < 0 -> "Efectivo insuficiente";
            case CashPayment _ -> "OK";
            case CardPayment(_, int installments) when installments > 12 -> "Máximo 12 meses";
            case CardPayment _ -> "OK";
            case TransferPayment _ -> "OK";
        };
    }

    static boolean guardsExample() {
        BigDecimal total = new BigDecimal("800");
        return validate(new CashPayment(new BigDecimal("500")), total).equals("Efectivo insuficiente")
                && validate(new CashPayment(new BigDecimal("1000")), total).equals("OK")
                && validate(new CardPayment("4111111111111111", 18), total).equals("Máximo 12 meses")
                && validate(new CardPayment("4111111111111111", 6), total).equals("OK");
    }

    /*
        =================================================================
        CAPA 8) NULL EN UN SWITCH
        =================================================================

        Un switch sobre una referencia null lanza NullPointerException,
        aunque tenga default. Es la regla histórica del switch, y se
        mantiene para no cambiar el comportamiento del código existente.

        Desde Java 21 puedes manejarlo explícitamente con case null:
            case null -> ...
        o juntarlo con default:
            case null, default -> ...

        Úsalo cuando null sea un valor esperado (por ejemplo, "el cliente
        todavía no eligió forma de pago"). Si null es un error, déjalo
        lanzar la excepción: fallar rápido es mejor que esconderlo.
    */
    static String paymentStatus(Payment payment) {
        return switch (payment) {
            case null -> "Sin forma de pago";
            case CardPayment _ -> "Pago con tarjeta";
            case CashPayment _ -> "Pago en efectivo";
            case TransferPayment _ -> "Pago por transferencia";
        };
    }

    static boolean nullCaseExample() {
        boolean handled = paymentStatus(null).equals("Sin forma de pago");

        boolean withoutCaseNullThrows;
        try {
            commission(null, BigDecimal.TEN);   // su switch no tiene case null
            withoutCaseNullThrows = false;
        } catch (NullPointerException e) {
            withoutCaseNullThrows = true;
        }

        return handled && withoutCaseNullThrows;
    }

    /*
        =================================================================
        CAPA 9) MODELAR RESULTADOS: TIPOS SUMA Y PRODUCTO
        =================================================================

        Con sealed + records, Java tiene lo que en otros lenguajes
        (Haskell, Rust, Kotlin, TypeScript) se llama TIPOS ALGEBRAICOS:

        - TIPO PRODUCTO: un record. Tiene un dato Y otro Y otro.
          CardPayment = número Y meses.
        - TIPO SUMA: una interface sealed. Es una variante O la otra O la
          otra. Payment = tarjeta O efectivo O transferencia.

        Un uso muy profesional: representar el RESULTADO de una operación
        que puede salir de varias formas, en lugar de devolver null o
        lanzar excepciones para casos que son normales en el negocio. Un
        pago rechazado no es un error del programa: es un resultado
        posible.
    */
    sealed interface PaymentResult permits Approved, Declined, PendingVerification { }

    record Approved(String authorizationCode, BigDecimal change) implements PaymentResult { }

    record Declined(String reason) implements PaymentResult { }

    record PendingVerification(String verificationUrl) implements PaymentResult { }

    static PaymentResult process(Payment payment, BigDecimal total) {
        return switch (payment) {
            case CashPayment(BigDecimal received) when received.compareTo(total) < 0 ->
                    new Declined("Efectivo insuficiente");
            case CashPayment(BigDecimal received) ->
                    new Approved("EF-" + total.toBigInteger(), received.subtract(total));
            case CardPayment(String number, int installments) when installments > 12 ->
                    new Declined("Máximo 12 meses sin intereses");
            case CardPayment(String number, _) ->
                    new Approved("TC-" + number.substring(12), BigDecimal.ZERO);
            case TransferPayment(_, Bank(_, var country)) when !country.equals("MX") ->
                    new PendingVerification("https://tienda.example/verificar");
            case TransferPayment _ ->
                    new Approved("TR-OK", BigDecimal.ZERO);
        };
    }

    // Quien recibe el resultado está OBLIGADO por el compilador a manejar los tres casos.
    static String customerMessage(PaymentResult result) {
        return switch (result) {
            case Approved(var code, var change) when change.signum() > 0 -> "Pago aprobado (" + code + "). Su cambio: $" + change;
            case Approved(var code, _) -> "Pago aprobado (" + code + ")";
            case Declined(var reason) -> "Pago rechazado: " + reason;
            case PendingVerification(var url) -> "Confirme su transferencia en " + url;
        };
    }

    static boolean resultTypeExample() {
        BigDecimal total = new BigDecimal("800");

        return customerMessage(process(new CashPayment(new BigDecimal("1000")), total))
                        .equals("Pago aprobado (EF-800). Su cambio: $200")
                && customerMessage(process(new CardPayment("4111111111111111", 18), total))
                        .equals("Pago rechazado: Máximo 12 meses sin intereses")
                && customerMessage(process(new TransferPayment("012345678901234567", new Bank("Chase", "US")), total))
                        .startsWith("Confirme su transferencia");
    }

    /*
        Ventaja sobre las excepciones: la firma del método DICE que el
        pago puede rechazarse o quedar pendiente, y el compilador obliga
        a quien lo llama a contemplarlo. Una excepción se puede olvidar.
        (Cuándo usar excepciones de verdad: v3_exceptions/ErrorHandlingBestPractices.)
    */

    /*
        =================================================================
        CAPA 10) POLIMORFISMO O PATTERN MATCHING: CÓMO DECIDIR
        =================================================================

        Ahora tienes DOS formas de manejar variantes, y cada una es buena
        para cosas distintas. Es un dilema clásico de diseño conocido como
        el "problema de la expresión":

        POLIMORFISMO (Polymorphism.java): el comportamiento vive DENTRO
        de cada clase.
        - Agregar una VARIANTE nueva: fácil (una clase nueva, nada más
          cambia).
        - Agregar una OPERACIÓN nueva: difícil (hay que agregar un método
          a la interface y a TODAS las clases).

        PATTERN MATCHING con sealed: los datos son records y las
        operaciones viven FUERA, en switches.
        - Agregar una OPERACIÓN nueva: fácil (un método nuevo con un
          switch; los records no se tocan).
        - Agregar una VARIANTE nueva: hay que actualizar todos los switch,
          pero el COMPILADOR te dice exactamente cuáles.

        | Usa POLIMORFISMO cuando                     | Usa SEALED + PATTERN MATCHING cuando           |
        |---------------------------------------------|------------------------------------------------|
        | Las variantes cambian seguido o son abiertas| Las variantes son fijas y conocidas            |
        |   (plugins, cualquiera puede agregar una)   |   (formas de pago que acepta la tienda)        |
        | Hay pocas operaciones y son "propias" del   | Hay muchas operaciones distintas sobre los     |
        |   objeto (apply, pay, send)                 |   mismos datos (validar, cobrar, mostrar,      |
        |                                             |   reportar, exportar)                          |
        | Los objetos tienen estado y comportamiento  | Los objetos son datos inmutables               |
        | Diseñas una API para que otros extiendan    | Modelas resultados, mensajes, eventos, ASTs    |

        Ninguna es "la moderna" y la otra "la vieja". El polimorfismo
        sigue siendo la herramienta central de la POO. Pattern matching
        con sealed reemplaza principalmente al patrón VISITOR, que era la
        forma complicada de hacer esto antes de Java 21
        (v11_patterns/part3_behavioral/Visitor).

        Lo que SÍ es una mala práctica: cadenas de instanceof sobre tipos
        NO sealed para decidir comportamiento (Polymorphism, CAPA 7). Sin
        sealed no hay verificación de exhaustividad: es el switch por tipo
        de siempre, con el mismo problema.
    */

    /*
        =================================================================
        CAPA 11) ERRORES COMUNES
        =================================================================

        1) Usar un enum o una clase llena de campos null para variantes
           que tienen datos distintos (CAPA 1).

        2) Poner default en un switch sobre un tipo sealed: pierdes el
           aviso del compilador cuando se agrega una variante (CAPA 4).

        3) Usar la variable de un patrón fuera de su alcance (después de
           un ||, o fuera del if) (CAPA 3).

        4) Equivocar el orden de los componentes en un record pattern: se
           desarman por POSICIÓN, no por nombre (CAPA 5).

        5) Declarar variables en el patrón que no usas: usa _ (CAPA 6).

        6) Poner el case general antes del específico con guarda: no
           compila (dominancia) (CAPA 7).

        7) Creer que un case con guarda cubre ese tipo para la
           exhaustividad (CAPA 7).

        8) Olvidar que un switch sobre null lanza NullPointerException
           aunque tenga default (CAPA 8).

        9) Lanzar excepciones para resultados normales del negocio en
           lugar de modelarlos con un tipo sealed (CAPA 9).

        10) Usar pattern matching donde el polimorfismo es mejor, o al
            revés (CAPA 10).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - sealed + records = un conjunto CERRADO de variantes, cada una
          con exactamente sus datos.
        - instanceof con patrón: la variable existe donde el flujo
          garantiza que coincidió (también tras un if negado que sale).
        - switch con patrones de tipo: sobre un sealed, el compilador
          exige cubrir todas las variantes, sin default.
        - Record patterns desarman componentes por posición; se anidan;
          var deduce el tipo; _ ignora lo que no usas.
        - Guardas when: condición extra; los específicos van antes que
          los generales.
        - case null para manejar null explícitamente.
        - Tipos suma (sealed) y producto (records): ideales para
          resultados, eventos y mensajes.
        - Polymorphism: variantes abiertas, pocas operaciones.
          Pattern matching: variantes fijas, muchas operaciones.
    */

    // -----------------------------------------------------------------
    // Utilidades internas de la guía.
    // -----------------------------------------------------------------

    private static List<Payment> samplePayments() {
        return List.of(
                new CardPayment("4111111111111111", 1),
                new CardPayment("5500000000000004", 6),
                new CashPayment(new BigDecimal("500")),
                new TransferPayment("012345678901234567", new Bank("BBVA", "MX")));
    }

    private static void check(boolean condition, String layer) {
        if (!condition) {
            throw new IllegalStateException("El ejemplo de la " + layer + " no hace lo que la guía explica");
        }
    }
}
