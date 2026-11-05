package v3_exceptions.part2_in_practice;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.util.Objects;

/*
    =====================================================================
    EXCEPCIONES PROPIAS Y CAUSAS ENCADENADAS — DE CERO A NIVEL TÉCNICO
    =====================================================================

    Ruta de v3_exceptions/: guía 3 de 5  (part2_in_practice)
    Anterior:  part1_concepts/CheckedVsUnchecked
    Siguiente: part2_in_practice/TryWithResources

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.

    Índice:
        CAPA 1)  Primero, reutiliza las del JDK
        CAPA 2)  Cuándo SÍ crear una excepción propia
        CAPA 3)  Cómo se escribe una excepción propia
        CAPA 4)  Excepciones con datos del problema
        CAPA 5)  Una jerarquía pequeña de excepciones
        CAPA 6)  Causas encadenadas: no pierdas el error original
        CAPA 7)  Encontrar la causa raíz
        CAPA 8)  Buenos mensajes
        CAPA 9)  Excepción o tipo de resultado
        CAPA 10) Errores comunes
        RESUMEN FINAL
*/
public class CustomExceptions {

    public static void main(String[] args) {
        check(reuseJdkExample(), "CAPA 1");
        check(contextDataExample(), "CAPA 4");
        check(hierarchyExample(), "CAPA 5");
        check(chainedCauseExample(), "CAPA 6");
        check(rootCauseExample(), "CAPA 7");
        System.out.println("CustomExceptions: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) PRIMERO, REUTILIZA LAS DEL JDK
        =================================================================

        Antes de crear una excepción, revisa si ya existe una que diga lo
        mismo. Las del JDK son conocidas por cualquier programador Java:
        al ver IllegalArgumentException, todos entienden el problema sin
        leer tu código (Effective Java: "prefiere las excepciones estándar").

        | Situación                                          | Usa                            |
        |----------------------------------------------------|--------------------------------|
        | Un argumento con valor inválido                    | IllegalArgumentException       |
        | Un argumento null que no debía serlo               | NullPointerException (requireNonNull) |
        | El objeto no está en el estado para hacer eso      | IllegalStateException          |
        | Un índice fuera de rango                           | IndexOutOfBoundsException      |
        | La operación no está soportada (colección inmutable)| UnsupportedOperationException |
        | Un fallo de lectura/escritura                      | IOException (y sus hijas)      |

        Y nunca lances Exception, RuntimeException ni Throwable "a secas":
        no dicen nada, y quien quiera atrapar tu error tendría que atrapar
        TODO.
    */
    static void setDiscount(int percent) {
        if (percent < 0 || percent > 100) {
            throw new IllegalArgumentException("El descuento debe estar entre 0 y 100: " + percent);
        }
    }

    static boolean reuseJdkExample() {
        try {
            setDiscount(150);
            return false;
        } catch (IllegalArgumentException e) {
            return e.getMessage().endsWith("150");
        }
    }

    /*
        =================================================================
        CAPA 2) CUÁNDO SÍ CREAR UNA EXCEPCIÓN PROPIA
        =================================================================

        Crea una propia cuando se cumple al menos una de estas:

        1) QUIEN LLAMA NECESITA DISTINGUIRLA para reaccionar distinto. Si
           una tienda debe ofrecer "pagar la diferencia" cuando faltan
           fondos, pero "intentar más tarde" cuando el banco no responde,
           necesita dos tipos que pueda atrapar por separado. Con
           IllegalStateException para ambos, tendría que leer el MENSAJE
           para decidir: frágil (el mensaje puede cambiar, o traducirse).

        2) NECESITAS LLEVAR DATOS del problema: cuánto faltó, qué cuenta,
           qué código devolvió el banco. Una excepción del JDK solo lleva
           un texto (CAPA 4).

        3) EL NOMBRE COMUNICA el problema del negocio:
           InsufficientFundsException dice más que IllegalStateException.

        Si no se cumple ninguna, usa una del JDK. Crear una excepción por
        cada if es ruido.
    */

    /*
        =================================================================
        CAPA 3) CÓMO SE ESCRIBE UNA EXCEPCIÓN PROPIA
        =================================================================

        Es una clase que hereda de RuntimeException (unchecked) o de
        Exception (checked); CheckedVsUnchecked, CAPA 10, explica cómo
        elegir. La receta:

            public class PaymentException extends RuntimeException {
                private static final long serialVersionUID = 1L;

                public PaymentException(String message) {
                    super(message);
                }

                public PaymentException(String message, Throwable cause) {
                    super(message, cause);
                }
            }

        1) NOMBRE terminado en Exception, que diga el problema.
        2) CONSTRUCTORES que pasan el mensaje (y la causa) al padre con
           super(...). El padre (Throwable) es quien guarda el mensaje, la
           causa y el stack trace.
        3) El constructor CON CAUSA no es opcional si tu excepción puede
           envolver a otra (CAPA 6).
        4) serialVersionUID: Throwable implementa Serializable (las
           excepciones se pueden enviar entre sistemas), y javac -Xlint
           advierte si falta:
               warning: [serial] serializable class X has no definition of serialVersionUID
           Basta con declararlo; su significado se ve en v8_apis/part2_io_and_network/Serialization.
        5) Que sea INMUTABLE: campos final, sin setters. Una excepción
           describe algo que YA pasó.
    */

    /*
        =================================================================
        CAPA 4) EXCEPCIONES CON DATOS DEL PROBLEMA
        =================================================================

        La gran ventaja de una excepción propia: puede llevar los DATOS
        del problema como campos, no solo un texto. Quien la atrapa puede
        USARLOS en lugar de leer el mensaje:
            catch (InsufficientFundsException e) {
                offerToPay(e.shortfall());   // cuánto falta, como número
            }

        Ejemplo: un servicio de pagos. La base de la jerarquía (CAPA 5) y
        la excepción de fondos insuficientes con sus datos.
    */
    static class PaymentException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        PaymentException(String message) {
            super(message);
        }

        PaymentException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    static final class InsufficientFundsException extends PaymentException {
        private static final long serialVersionUID = 1L;

        private final String accountId;
        private final BigDecimal requested;
        private final BigDecimal available;

        InsufficientFundsException(String accountId, BigDecimal requested, BigDecimal available) {
            super("Fondos insuficientes en la cuenta " + accountId
                    + ": se pidieron " + requested + " y hay " + available);   // el mensaje se arma con los datos
            this.accountId = accountId;
            this.requested = requested;
            this.available = available;
        }

        String getAccountId() {
            return accountId;
        }

        BigDecimal getRequested() {
            return requested;
        }

        BigDecimal getAvailable() {
            return available;
        }

        BigDecimal shortfall() {
            return requested.subtract(available);   // un dato derivado, útil para quien la atrapa
        }
    }

    static final class Account {
        private final String id;
        private BigDecimal balance;

        Account(String id, BigDecimal balance) {
            this.id = Objects.requireNonNull(id, "id");
            this.balance = Objects.requireNonNull(balance, "balance");
        }

        void withdraw(BigDecimal amount) {
            if (amount.compareTo(balance) > 0) {
                throw new InsufficientFundsException(id, amount, balance);
            }
            balance = balance.subtract(amount);
        }

        BigDecimal getBalance() {
            return balance;
        }
    }

    // Quien atrapa usa los DATOS, no el texto del mensaje.
    static String checkoutMessage(Account account, BigDecimal total) {
        try {
            account.withdraw(total);
            return "Pago aprobado";
        } catch (InsufficientFundsException e) {
            return "Te faltan $" + e.shortfall() + ". ¿Quieres pagar la diferencia con otra tarjeta?";
        }
    }

    static boolean contextDataExample() {
        Account account = new Account("CTA-001", new BigDecimal("300"));

        InsufficientFundsException captured = null;
        try {
            account.withdraw(new BigDecimal("450"));
        } catch (InsufficientFundsException e) {
            captured = e;
        }

        return captured != null
                && captured.getAccountId().equals("CTA-001")
                && captured.shortfall().compareTo(new BigDecimal("150")) == 0
                && captured.getMessage().equals("Fondos insuficientes en la cuenta CTA-001: se pidieron 450 y hay 300")
                && checkoutMessage(account, new BigDecimal("450")).startsWith("Te faltan $150")
                && account.getBalance().compareTo(new BigDecimal("300")) == 0;   // el saldo no cambió
    }

    /*
        =================================================================
        CAPA 5) UNA JERARQUÍA PEQUEÑA DE EXCEPCIONES
        =================================================================

        Cuando un módulo tiene varios errores relacionados, conviene una
        BASE común y algunas hijas:

            PaymentException                    base: "algo falló al pagar"
            ├── InsufficientFundsException      faltan fondos (el usuario puede resolverlo)
            └── PaymentGatewayException         el banco no respondió (reintentar más tarde)

        Así, quien llama elige su nivel de detalle:
        - catch (InsufficientFundsException e)  -> reacciona a ESE caso
        - catch (PaymentException e)            -> cualquier problema de pago
        Y puede combinar: primero los específicos, luego la base
        (WhatAreExceptions, CAPA 7).

        Qué evitar: jerarquías profundas o una excepción para cada if. Dos
        o tres niveles bastan. La pregunta de siempre: ¿alguien va a
        atrapar este tipo por separado?
    */
    static final class PaymentGatewayException extends PaymentException {
        private static final long serialVersionUID = 1L;

        private final String gateway;

        PaymentGatewayException(String gateway, Throwable cause) {
            super("El procesador de pagos " + gateway + " no respondió", cause);   // conserva la causa (CAPA 6)
            this.gateway = gateway;
        }

        String getGateway() {
            return gateway;
        }
    }

    // Runnable es una interface funcional (Interfaces, CAPA 8): los () -> ... de abajo son lambdas
    // que representan "un pago por hacer" (v7_functional/part1_lambdas/Lambdas).
    static String handlePayment(Runnable payment) {
        try {
            payment.run();
            return "aprobado";
        } catch (InsufficientFundsException e) {
            return "faltan fondos";                      // específico: el usuario puede resolverlo
        } catch (PaymentGatewayException e) {
            return "reintentar más tarde";               // específico: problema temporal
        } catch (PaymentException e) {
            return "pago rechazado";                     // la base: cualquier otro problema de pago
        }
    }

    static boolean hierarchyExample() {
        Account account = new Account("CTA-002", new BigDecimal("100"));

        return handlePayment(() -> account.withdraw(new BigDecimal("50"))).equals("aprobado")
                && handlePayment(() -> account.withdraw(new BigDecimal("500"))).equals("faltan fondos")
                && handlePayment(() -> {
                    throw new PaymentGatewayException("BancoX", new SocketTimeoutException("Read timed out"));
                }).equals("reintentar más tarde")
                && handlePayment(() -> {
                    throw new PaymentException("Tarjeta vencida");
                }).equals("pago rechazado");
    }

    /*
        =================================================================
        CAPA 6) CAUSAS ENCADENADAS: NO PIERDAS EL ERROR ORIGINAL
        =================================================================

        Muchas veces atrapas una excepción de bajo nivel y lanzas otra con
        más significado para tu capa: un SocketTimeoutException de la red
        se vuelve un PaymentGatewayException del negocio. Esto se llama
        TRADUCIR la excepción (ErrorHandlingBestPractices).

        Al traducir, SIEMPRE pasa la original como CAUSA:
            catch (IOException e) {
                throw new PaymentGatewayException("BancoX", e);   // e es la causa
            }

        Por qué: la nueva excepción explica QUÉ significa para tu negocio;
        la causa explica QUÉ pasó realmente (un timeout, un certificado
        vencido, una conexión rechazada). Para depurar necesitas las dos.

        El stack trace muestra la cadena completa:
            ...PaymentGatewayException: El procesador de pagos BancoX no respondió
                at ...PaymentService.charge(...)
                ...
            Caused by: java.net.SocketTimeoutException: Read timed out
                at ...FakeGateway.send(...)
                ...

        Si en vez de eso escribes
            throw new PaymentGatewayException("BancoX", null);
        o copias solo el mensaje, la línea "Caused by" desaparece y nadie
        podrá saber por qué falló el banco.
    */
    static final class FakeGateway {
        private final boolean networkDown;

        FakeGateway(boolean networkDown) {
            this.networkDown = networkDown;
        }

        // Simula una llamada de red: puede lanzar una checked de bajo nivel.
        String send(BigDecimal amount) throws IOException {
            if (networkDown) {
                throw new SocketTimeoutException("Read timed out");
            }
            return "AUT-" + amount.toBigInteger();
        }
    }

    static final class PaymentService {
        private final FakeGateway gateway;

        PaymentService(FakeGateway gateway) {
            this.gateway = gateway;
        }

        String charge(BigDecimal amount) {
            try {
                return gateway.send(amount);
            } catch (IOException e) {
                throw new PaymentGatewayException("BancoX", e);   // traduce y CONSERVA la causa
            }
        }
    }

    static boolean chainedCauseExample() {
        PaymentService working = new PaymentService(new FakeGateway(false));
        PaymentService broken = new PaymentService(new FakeGateway(true));

        PaymentGatewayException captured = null;
        try {
            broken.charge(new BigDecimal("250"));
        } catch (PaymentGatewayException e) {
            captured = e;
        }

        StringWriter text = new StringWriter();
        captured.printStackTrace(new PrintWriter(text));
        String trace = text.toString();

        return working.charge(new BigDecimal("250")).equals("AUT-250")
                && captured.getCause() instanceof SocketTimeoutException
                && captured.getGateway().equals("BancoX")
                && trace.contains("Caused by: java.net.SocketTimeoutException: Read timed out");
    }

    /*
        =================================================================
        CAPA 7) ENCONTRAR LA CAUSA RAÍZ
        =================================================================

        Una excepción puede envolver a otra que envuelve a otra... La
        CAUSA RAÍZ es la última de la cadena: el problema original.
        En un stack trace es la ÚLTIMA línea "Caused by:".

        Para encontrarla en código, se sigue getCause() hasta que no haya
        más. (Java ya impide que una excepción sea causa de sí misma:
        initCause(this) lanza IllegalArgumentException. Un ciclo A -> B -> A
        sí es posible, pero rarísimo; para cubrirlo habría que llevar un Set
        de las ya visitadas.)
    */
    static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    static boolean rootCauseExample() {
        Throwable network = new SocketTimeoutException("Read timed out");
        Throwable gateway = new PaymentGatewayException("BancoX", network);
        Throwable order = new IllegalStateException("No se pudo completar el pedido 1042", gateway);

        return rootCause(order) == network
                && rootCause(network) == network;   // sin causa: ella misma es la raíz
    }

    /*
        =================================================================
        CAPA 8) BUENOS MENSAJES
        =================================================================

        El mensaje de una excepción es para el PROGRAMADOR que va a
        depurar, normalmente leyéndolo en un log, a veces meses después.

        Un buen mensaje:
        1) Dice QUÉ falló y CON QUÉ VALORES:
               "Fondos insuficientes en la cuenta CTA-001: se pidieron 450 y hay 300"
           no "Error en el pago".
        2) Incluye el identificador de lo que falló (cuenta, pedido,
           archivo) para poder buscarlo.
        3) NO incluye datos sensibles: contraseñas, números de tarjeta
           completos, tokens. Los logs los lee mucha gente y se guardan
           mucho tiempo.
        4) No termina con punto ni empieza con "Error:": el tipo de la
           excepción ya lo dice.

        El mensaje para el USUARIO final es otra cosa: no le muestres el
        mensaje técnico de la excepción ("SocketTimeoutException: Read
        timed out"). Quien atrapa la excepción decide qué decirle, en su
        idioma y en términos que entienda (como checkoutMessage en la
        CAPA 4). Por eso son útiles los DATOS en la excepción: con ellos
        se arma cualquier mensaje.
    */

    /*
        =================================================================
        CAPA 9) EXCEPCIÓN O TIPO DE RESULTADO
        =================================================================

        No todo "no se pudo" tiene que ser una excepción. Una alternativa
        moderna es DEVOLVER un tipo que represente los resultados
        posibles, como el PaymentResult sealed de
        v2_oop/part4_advanced_types/SealedAndPatternMatching, CAPA 9:
            sealed interface PaymentResult permits Approved, Declined
        El compilador obliga a quien lo recibe a manejar cada caso.

        | Usa EXCEPCIÓN cuando                          | Usa TIPO DE RESULTADO cuando                  |
        |-----------------------------------------------|-----------------------------------------------|
        | Es excepcional: no pasa en el flujo normal    | Es un resultado esperado del negocio          |
        | Quien llama directo normalmente NO lo maneja: | Quien llama SIEMPRE debe decidir qué hacer    |
        |   debe subir varios niveles                   |   (un pago rechazado es parte del flujo)      |
        | Es un error de programación (bug)             | Quieres que el compilador revise los casos    |

        Ambas son herramientas válidas; lo importante es no usar
        excepciones para lo que en realidad es lógica normal del negocio
        (ErrorHandlingBestPractices).
    */

    /*
        =================================================================
        CAPA 10) ERRORES COMUNES
        =================================================================

        1) Crear una excepción propia cuando una del JDK dice lo mismo
           (CAPAS 1 y 2).

        2) Lanzar Exception, RuntimeException o Throwable genéricos
           (CAPA 1).

        3) Que quien atrapa tenga que leer el MENSAJE para decidir qué
           hacer: falta un tipo propio o un dato (CAPAS 2 y 4).

        4) Olvidar el constructor con causa (CAPA 3).

        5) Excepciones con setters o campos que cambian (CAPA 3).

        6) Jerarquías profundas, o una excepción por cada if (CAPA 5).

        7) Traducir una excepción sin pasar la original como causa
           (CAPA 6).

        8) Buscar la causa en la primera línea del stack trace en vez de en
           el último "Caused by" (CAPA 7).

        9) Mensajes sin valores, o con datos sensibles (CAPA 8).

        10) Mostrar el mensaje técnico de la excepción al usuario final
            (CAPA 8).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Primero reutiliza las del JDK (IllegalArgumentException,
          IllegalStateException...). Nunca lances Exception genérico.
        - Crea una propia si alguien necesita distinguirla, si debe llevar
          datos, o si su nombre comunica el problema del negocio.
        - Receta: extends RuntimeException (o Exception), constructores
          (mensaje) y (mensaje, causa), serialVersionUID, inmutable.
        - Lleva los datos como campos: quien atrapa los usa en vez de
          leer el mensaje.
        - Jerarquía pequeña: una base y pocas hijas.
        - Al traducir, SIEMPRE con la causa: "Caused by" en el stack trace.
          La causa raíz es la última de la cadena.
        - Mensajes con valores e identificadores, sin datos sensibles. El
          mensaje al usuario lo arma quien atrapa.
        - Para resultados esperados del negocio, considera un tipo sealed.
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
