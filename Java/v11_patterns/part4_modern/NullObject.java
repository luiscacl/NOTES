package v11_patterns.part4_modern;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/*
    =====================================================================
    NULL OBJECT: UN OBJETO QUE "NO HACE NADA" EN LUGAR DE NULL
    =====================================================================

    Ruta de patterns/: guía 24 de 28  (part4_modern)
    Anterior:  part3_behavioral/Visitor
    Siguiente: part4_modern/Repository

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.

    Importancia: MEDIA. Elimina muchos if (x != null) y muchos
    NullPointerException, y el JDK ya lo usa (CAPA 4). Pero mal aplicado
    esconde errores graves (CAPA 6).

    Índice:
        CAPA 1)  El problema: un colaborador que a veces no existe
        CAPA 2)  La solución: un objeto que representa "nada"
        CAPA 3)  Apagar funcionalidades sin ifs
        CAPA 4)  Null Object en el JDK
        CAPA 5)  ¿Null Object u Optional?
        CAPA 6)  El peligro: esconder lo que debía fallar
        CAPA 7)  Errores comunes
        RESUMEN FINAL
*/
public class NullObject {

    public static void main(String[] args) throws IOException {
        check(nullChecksExample(), "CAPA 1");
        check(nullObjectExample(), "CAPA 2");
        check(featureFlagExample(), "CAPA 3");
        check(jdkExample(), "CAPA 4");
        check(dangerExample(), "CAPA 6");
        System.out.println("NullObject: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) EL PROBLEMA: UN COLABORADOR QUE A VECES NO EXISTE
        =================================================================

        Algunos clientes de la tienda están inscritos en un programa de
        lealtad (Oro, Plata) y otros no. Si "no inscrito" se representa con
        null:

            LoyaltyProgram program = customer.loyalty();   // puede ser null

        CADA lugar que lo usa necesita su if:
            if (program != null) { points = program.pointsFor(total); }
            if (program != null) { banner = program.name(); }
            if (program != null && program.freeShipping()) { ... }

        Y basta UN lugar que lo olvide para un NullPointerException en
        producción, solo con los clientes no inscritos, que probablemente
        no estaban en las pruebas (v1_fundamentals/
        part3_text_and_references/ReferencesAndNull).

        El ejemplo: tres operaciones con sus ifs, y una cuarta que lo
        olvidó.
    */
    interface LoyaltyProgram {
        int pointsFor(BigDecimal total);

        boolean freeShipping();

        String name();
    }

    record Tier(String name, int pointsPer100, boolean freeShipping) implements LoyaltyProgram {
        @Override
        public int pointsFor(BigDecimal total) {
            return total.intValue() / 100 * pointsPer100;
        }
    }

    record Customer(String name, LoyaltyProgram loyalty) { }

    static int pointsWithChecks(Customer customer, BigDecimal total) {
        LoyaltyProgram program = customer.loyalty();
        return program != null ? program.pointsFor(total) : 0;   // el if obligatorio
    }

    static String bannerForgotTheCheck(Customer customer) {
        return "Programa: " + customer.loyalty().name();    // alguien olvidó el if
    }

    static boolean nullChecksExample() {
        Customer gold = new Customer("Ana", new Tier("Oro", 5, true));
        Customer none = new Customer("Luis", null);

        boolean checkedWorks = pointsWithChecks(gold, new BigDecimal("1000")) == 50
                && pointsWithChecks(none, new BigDecimal("1000")) == 0;
        boolean forgottenExplodes;
        try {
            bannerForgotTheCheck(none);
            forgottenExplodes = false;
        } catch (NullPointerException e) {
            forgottenExplodes = true;                       // solo con clientes sin programa
        }
        return checkedWorks && forgottenExplodes;
    }

    /*
        =================================================================
        CAPA 2) LA SOLUCIÓN: UN OBJETO QUE REPRESENTA "NADA"
        =================================================================

        NULL OBJECT: en lugar de null, un objeto REAL que implementa la
        misma interface con un comportamiento NEUTRO: 0 puntos, sin envío
        gratis, nombre "Sin programa".

        - El campo nunca es null: el cliente no inscrito tiene NO_PROGRAM.
        - Todo el código llama program.pointsFor(...) sin preguntar nada.
          Los ifs desaparecen, y con ellos los NullPointerException.
        - El comportamiento de "no inscrito" queda definido en UN lugar, en
          vez de repartido en cada if (¿0 puntos o -1? ¿"" o "Ninguno"?).

        Como no tiene estado, basta UNA instancia compartida: una constante.
        (Un enum de un solo valor sería lo natural, part1_creational/
        Singleton, CAPA 6, pero AQUÍ no sirve: todo enum ya trae un método
        name() FINAL. El enum compila, porque ese name() cumple con la
        interface, pero devolvería "INSTANCE" en vez de "Sin programa"; y si
        intentas sobrescribirlo:
            // ERROR: name() in NoProgram cannot override name() in Enum
            //   overridden method is final
        Se descubrió al escribir esta guía.)
    */
    record NoProgram() implements LoyaltyProgram {          // NULL OBJECT: neutro y sin estado
        @Override
        public int pointsFor(BigDecimal total) {
            return 0;
        }

        @Override
        public boolean freeShipping() {
            return false;
        }

        @Override
        public String name() {
            return "Sin programa";
        }
    }

    static final LoyaltyProgram NO_PROGRAM = new NoProgram();   // UNA instancia compartida

    static Customer customer(String name, LoyaltyProgram programOrNull) {
        return new Customer(name, programOrNull == null ? NO_PROGRAM : programOrNull);   // null se convierte UNA vez, en la frontera
    }

    static String banner(Customer customer) {
        return "Programa: " + customer.loyalty().name();    // sin if, y ya no explota
    }

    static boolean nullObjectExample() {
        Customer none = customer("Luis", null);
        Customer silver = customer("Mia", new Tier("Plata", 2, false));
        return banner(none).equals("Programa: Sin programa")
                && none.loyalty().pointsFor(new BigDecimal("1000")) == 0
                && silver.loyalty().pointsFor(new BigDecimal("1000")) == 20
                && !none.loyalty().freeShipping();
    }

    /*
        =================================================================
        CAPA 3) APAGAR FUNCIONALIDADES SIN IFS
        =================================================================

        Un uso muy práctico: funcionalidades que se pueden APAGAR por
        configuración (un "feature flag"), o que no deben actuar en ciertos
        ambientes. Por ejemplo, no mandar SMS reales en el ambiente de
        pruebas, o apagar las notificaciones push mientras se arregla el
        proveedor.

        En lugar de un if (notificationsEnabled) en cada lugar que avisa,
        el composition root (v10_design/part3_responsibilities/
        DependencyInjection, CAPA 4) inyecta un notificador que no hace
        nada. El resto del código no se entera de que está apagado.
    */
    interface Notifier {
        void send(String to, String message);
    }

    static final class SmsNotifier implements Notifier {
        final List<String> sent = new ArrayList<>();

        @Override
        public void send(String to, String message) {
            sent.add(to + ": " + message);                  // aquí se mandaría el SMS real
        }
    }

    static final Notifier DISABLED = (to, message) -> { };  // NULL OBJECT como lambda: no hace nada

    static Notifier notifierFor(boolean smsEnabled, SmsNotifier real) {   // la decisión, en UN lugar
        return smsEnabled ? real : DISABLED;
    }

    static void confirmOrder(Notifier notifier, String phone) {          // no sabe si está apagado
        notifier.send(phone, "Tu pedido fue confirmado");
    }

    static boolean featureFlagExample() {
        SmsNotifier real = new SmsNotifier();
        confirmOrder(notifierFor(true, real), "5551234");
        confirmOrder(notifierFor(false, real), "5559876");                // apagado: no llega nada
        return real.sent.equals(List.of("5551234: Tu pedido fue confirmado"));
    }

    /*
        =================================================================
        CAPA 4) NULL OBJECT EN EL JDK
        =================================================================

        - LAS COLECCIONES VACÍAS: List.of(), Map.of(), Collections.emptyList()
          son el Null Object más usado. Un método que no encuentra nada
          devuelve una lista VACÍA, nunca null: quien la recibe la recorre
          sin preguntar, y el ciclo simplemente no hace nada
          (v5_collections/part4_tools/ImmutableCollections; v7_functional/
          part2_streams/OptionalUsage, CAPA 10).
        - OutputStream.nullOutputStream(), Writer.nullWriter(): destinos que
          DESCARTAN todo lo que se les escribe (Java 11). Sirven cuando una
          API pide dónde escribir y no te interesa la salida (la versión en
          Java de /dev/null de Linux, el archivo especial que traga todo).
        - InputStream.nullInputStream(), Reader.nullReader(): fuentes
          vacías, que se terminan al primer intento de leer.

        El ejemplo pasa un PrintStream que descarta todo a un método que
        "escribe un reporte", y una lista vacía a un método que suma.
    */
    static int writeReport(PrintStream out, List<Integer> sales) {
        int total = 0;
        for (int sale : sales) {                            // con lista vacía: cero vueltas, sin if
            out.println("venta: " + sale);
            total += sale;
        }
        out.println("total: " + total);
        return total;
    }

    static boolean jdkExample() throws IOException {
        try (PrintStream discard = new PrintStream(OutputStream.nullOutputStream(), true, StandardCharsets.UTF_8)) {
            int withSales = writeReport(discard, List.of(100, 250));       // escribe... a ningún lado
            int noSales = writeReport(discard, List.of());                 // la lista vacía como Null Object
            return withSales == 350 && noSales == 0 && InputStream.nullInputStream().read() == -1;
        }
    }

    /*
        =================================================================
        CAPA 5) ¿NULL OBJECT U OPTIONAL?
        =================================================================

        Los dos evitan null, pero responden a situaciones distintas:

        | Situación                                          | Usa                              |
        |----------------------------------------------------|----------------------------------|
        | La ausencia tiene un comportamiento NATURAL y      | Null Object                      |
        | SEGURO que sirve igual para todos (0 puntos, no    |                                  |
        | notificar, lista vacía)                            |                                  |
        | Quien llama debe DECIDIR qué hacer si no hay       | Optional (v7_functional/         |
        | (otro flujo, un mensaje, una excepción)            | part2_streams/OptionalUsage)     |
        | La ausencia es un ERROR                            | una excepción (fail fast)        |

        Ejemplo de la diferencia: "buscar un cliente por correo" devuelve
        Optional, porque no encontrarlo cambia el flujo (mostrar "no
        existe"). "El programa de lealtad de un cliente" es un Null Object,
        porque "sin programa" se comporta como un programa que no da nada.
    */
    static Optional<Customer> findByEmail(String email) {   // ausencia que el que llama debe decidir
        return email.equals("ana@correo.com") ? Optional.of(customer("Ana", new Tier("Oro", 5, true))) : Optional.empty();
    }

    /*
        =================================================================
        CAPA 6) EL PELIGRO: ESCONDER LO QUE DEBÍA FALLAR
        =================================================================

        Un Null Object hace que la ausencia pase DESAPERCIBIDA. Eso es bueno
        cuando "no hacer nada" es correcto, y desastroso cuando no lo es.

        Ejemplo real de lo que NO se debe hacer: si la configuración de la
        pasarela de pagos falta, inyectar una pasarela "que no hace nada" y
        responde "cobrado". Los pedidos se confirman, se envían... y nunca se
        cobraron. Nadie se entera hasta que contabilidad revisa el banco.

        La regla: Null Object SOLO para cosas OPCIONALES cuyo "no hacer
        nada" es un comportamiento válido del negocio (no sumar puntos, no
        mandar una notificación prescindible). Para todo lo que DEBE
        ocurrir (cobrar, guardar, facturar), la ausencia es un error: falla
        al arrancar o al usarse, con un mensaje claro (v3_exceptions/
        part2_in_practice/ErrorHandlingBestPractices, CAPA 3).

        Y aun cuando sea correcto, que se NOTE en los logs al arrancar
        ("notificaciones SMS DESACTIVADAS"), para que nadie se sorprenda.
    */
    interface PaymentGateway {
        boolean charge(String customer, BigDecimal amount);
    }

    static final PaymentGateway FAKE_SUCCESS = (customer, amount) -> true;   // ERROR de diseño: "cobra" sin cobrar

    static PaymentGateway paymentGatewayFor(String apiKeyOrNull) {
        if (apiKeyOrNull == null) {
            throw new IllegalStateException("Falta la llave de la pasarela de pagos: no se puede arrancar");   // fail fast
        }
        return (customer, amount) -> true;                  // aquí iría la pasarela real
    }

    static boolean dangerExample() {
        boolean silentlyWrong = FAKE_SUCCESS.charge("ana", new BigDecimal("1250"));   // "true", y nadie pagó

        boolean failsFast;
        try {
            paymentGatewayFor(null);
            failsFast = false;
        } catch (IllegalStateException e) {
            failsFast = e.getMessage().startsWith("Falta la llave");
        }
        return silentlyWrong && failsFast && findByEmail("nadie@correo.com").isEmpty();
    }

    /*
        =================================================================
        CAPA 7) ERRORES COMUNES
        =================================================================

        1) Representar "no tiene" con null y llenar el código de ifs
           (CAPA 1).

        2) Convertir null en Null Object en muchos lugares en vez de UNA vez
           en la frontera (al crear el objeto o al leer de la base de datos)
           (CAPA 2).

        3) Devolver null en lugar de una colección vacía (CAPA 4).

        4) Null Object donde quien llama necesita saber que no había nada:
           ahí va Optional (CAPA 5).

        5) Null Object para cosas obligatorias (pagos, guardado): esconde
           errores graves (CAPA 6).

        6) Null Objects con estado o con lógica "inteligente": deben ser
           neutros y sin sorpresas.
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Null Object: un objeto real con comportamiento neutro en lugar de
          null; elimina los if (x != null) y los NullPointerException.
        - Una instancia compartida (una constante; un enum solo si no choca
          con sus métodos finales, como name()); el null se convierte una
          vez, en la frontera.
        - Muy útil para apagar funcionalidades opcionales por configuración.
        - En el JDK: colecciones vacías, nullOutputStream, nullWriter,
          nullInputStream.
        - Null Object si la ausencia tiene un comportamiento natural;
          Optional si el que llama debe decidir; excepción si es un error.
        - Nunca para lo que DEBE ocurrir: esconde el error.
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
