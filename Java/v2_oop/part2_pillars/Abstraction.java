package v2_oop.part2_pillars;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/*
    =====================================================================
    ABSTRACCIÓN EN JAVA — DE CERO A NIVEL TÉCNICO
    =====================================================================

    Ruta de v2_oop/: guía 7 de 14  (part2_pillars)
    Anterior:  part2_pillars/Polymorphism
    Siguiente: part3_interfaces_and_relationships/Interfaces

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea y JUSTO DEBAJO está el código que la
      demuestra. El código es real: compila y se usa.
    - Las clases de ejemplo viven dentro de la clase Abstraction
      ("clases anidadas"). Así cada ejemplo queda pegado a su explicación
      y no choca con clases de otros archivos del paquete.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice. Si algún día cambias un ejemplo y lo
      rompes, main() te avisa.

    Índice:
        CAPA 0)  Vocabulario mínimo antes de empezar
        CAPA 1)  Qué es la abstracción
        CAPA 2)  El problema: código SIN abstracción
        CAPA 3)  Interfaces: la forma más pura de abstracción
        CAPA 4)  Depender de la abstracción, no del detalle
        CAPA 5)  Clases abstractas: abstracción + código compartido
        CAPA 6)  Reglas técnicas de los métodos abstractos
        CAPA 7)  Abstracción + polimorfismo
        CAPA 8)  Varias interfaces y métodos default
        CAPA 9)  Clase abstracta vs interface: cuándo usar cada una
        CAPA 10) Buenas prácticas y abstracciones mal hechas
        CAPA 11) Errores comunes
        RESUMEN FINAL
*/
public class Abstraction {

    public static void main(String[] args) {
        check(listIsAnAbstractionExample() == 4, "CAPA 1");
        check(withoutAbstractionExample().startsWith("Cobrado"), "CAPA 2");
        check(interfaceExample().approved(), "CAPA 3");
        check(checkoutExample().size() == 3, "CAPA 4");
        check(abstractClassExample().startsWith("SMS"), "CAPA 5");
        check(polymorphismExample() == 2, "CAPA 7");
        check(multipleInterfacesExample().approved(), "CAPA 8");
        System.out.println("Abstraction: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 0) VOCABULARIO MÍNIMO ANTES DE EMPEZAR
        =================================================================

        Si no sabes nada de programación orientada a objetos, estas son
        las únicas palabras que necesitas para entender esta guía:

        - CLASE: un molde. Describe qué datos tiene algo y qué sabe hacer.
              class Perro { ... }

        - OBJETO: algo concreto creado con ese molde.
              Perro firulais = new Perro();

        - MÉTODO: una acción que el objeto sabe hacer.
              firulais.ladrar();

        - FIRMA de un método: su nombre + lo que recibe + lo que devuelve.
              Receipt pay(BigDecimal amount)
              ^^^^^^^ ^^^ ^^^^^^^^^^^^^^^^^
              devuelve nombre    recibe

        - IMPLEMENTACIÓN: el cuerpo del método, el código entre { }.
          Es el CÓMO se hace algo.

        - CÓDIGO CLIENTE: cualquier código que USA un objeto.
          Si la clase Tienda llama a pago.pay(...), Tienda es "cliente"
          del pago.

        - CONTRATO: la promesa pública de un tipo. "Cualquier cosa que sea
          un PaymentMethod tiene un método pay(amount) que devuelve un
          Receipt". El cliente confía en esa promesa.

        Con eso ya puedes leer todo lo demás.
    */

    /*
        =================================================================
        CAPA 1) QUÉ ES LA ABSTRACCIÓN
        =================================================================

        Abstraer es quedarte SOLO con lo importante de algo e ignorar
        los detalles que no necesitas para usarlo.

        Ejemplo de la vida real: el volante de un coche.
        Para manejar te importa:
            - girar a la izquierda / derecha
        No te importa:
            - si la dirección es hidráulica o eléctrica
            - cuántos engranes tiene
        Puedes manejar CUALQUIER coche porque todos te dan la misma
        abstracción: "un volante". El detalle interno cambia; el volante no.

        En programación es exactamente igual:
            - la abstracción dice QUÉ se puede hacer   (el volante)
            - la implementación decide CÓMO se hace    (el motor de dirección)

        La pregunta que te haces al abstraer es:
            "¿Qué necesita saber el código que USA esto,
             y qué detalles no le deberían importar?"

        -----------------------------------------------------------------
        YA USAS ABSTRACCIÓN SIN DARTE CUENTA: List
        -----------------------------------------------------------------

        Cuando escribes:

            List<String> words = new ArrayList<>();

        List es una abstracción (una interface): "una secuencia ordenada
        de elementos a la que puedes agregar, leer y quitar".
        ArrayList es UNA forma de cumplirla (por dentro usa un arreglo).
        LinkedList es OTRA forma (por dentro usa nodos enlazados).

        El método de abajo recibe un List. No sabe ni le importa si le
        pasas un ArrayList o un LinkedList: funciona con los dos porque
        solo usa lo que promete List (recorrerla).
    */
    static int countLongWords(List<String> words, int minLength) {
        int count = 0;
        for (String word : words) {
            if (word.length() >= minLength) {
                count++;
            }
        }
        return count;
    }

    static int listIsAnAbstractionExample() {
        List<String> backedByArray = new ArrayList<>(List.of("abstracción", "sol", "interface"));
        List<String> backedByNodes = new LinkedList<>(List.of("polimorfismo", "mar", "contrato"));

        // Mismo método, dos implementaciones distintas por dentro.
        return countLongWords(backedByArray, 5) + countLongWords(backedByNodes, 5); // 2 + 2
    }

    /*
        -----------------------------------------------------------------
        ABSTRACCIÓN NO ES LO MISMO QUE ENCAPSULACIÓN
        -----------------------------------------------------------------

        Se confunden mucho porque las dos "esconden" cosas:

        - ENCAPSULACIÓN protege los DATOS de un objeto (private, validar
          en setters). Responde: "¿quién puede tocar mi estado interno?"

        - ABSTRACCIÓN decide qué COMPORTAMIENTO se expone y oculta el
          resto. Responde: "¿qué necesita saber quien me usa?"

        Encapsulación = cómo proteges el interior.
        Abstracción   = qué cara le muestras al exterior.
    */

    /*
        =================================================================
        CAPA 2) EL PROBLEMA: CÓDIGO SIN ABSTRACCIÓN
        =================================================================

        Para entender por qué la abstracción importa, primero mira el
        código que se escribe cuando NO la usas. Un cobro en una tienda
        que acepta tarjeta y efectivo:
    */
    static final class CheckoutWithoutAbstraction {

        String pay(String method, BigDecimal amount, String cardNumber, BigDecimal cashReceived) {
            if (method.equals("CARD")) {
                String lastFour = cardNumber.substring(cardNumber.length() - 4);
                return "Cobrado " + amount + " a tarjeta **** " + lastFour;
            } else if (method.equals("CASH")) {
                BigDecimal change = cashReceived.subtract(amount);
                return "Cobrado " + amount + " en efectivo, cambio " + change;
            } else {
                throw new IllegalArgumentException("Método de pago desconocido: " + method);
            }
        }
    }

    static String withoutAbstractionExample() {
        CheckoutWithoutAbstraction checkout = new CheckoutWithoutAbstraction();
        // Para pagar con tarjeta hay que pasar null en el parámetro del efectivo. Mala señal.
        return checkout.pay("CARD", new BigDecimal("250.00"), "4111111111111111", null);
    }

    /*
        Funciona, pero tiene problemas que crecen con el proyecto:

        1) Cada método de pago nuevo (transferencia, vales, PayPal...)
           obliga a ABRIR y MODIFICAR esta clase. Más ramas if/else,
           más riesgo de romper lo que ya funcionaba.

        2) Los parámetros no tienen sentido para todos: cardNumber no
           sirve en efectivo y cashReceived no sirve en tarjeta. Por eso
           hay que pasar null, y null es una fuente clásica de errores.

        3) "CARD" es un texto. Si escribes "CRAD", compila igual y truena
           cuando el programa ya está corriendo.

        4) No puedes probar ni reutilizar el cobro con tarjeta por
           separado: está mezclado con todo lo demás.

        La raíz del problema: la tienda conoce los DETALLES de cada forma
        de pago. Lo único que la tienda necesita saber es:
            "esto se puede cobrar, y me devuelve un comprobante".
        Esa frase ES la abstracción. La siguiente capa la escribe en Java.
    */

    /*
        =================================================================
        CAPA 3) INTERFACES: LA FORMA MÁS PURA DE ABSTRACCIÓN
        =================================================================

        Una interface es un CONTRATO: una lista de métodos que una clase
        promete tener. Solo dice QUÉ, nunca el CÓMO.

        Sintaxis:

            interface NombreDelContrato {
                TipoQueDevuelve metodo(Parametros);   // sin cuerpo, termina en ;
            }

        Reglas técnicas:
        - Los métodos de una interface son public y abstract
          automáticamente. No hace falta escribir esas palabras.
        - NO puedes hacer new de una interface: un contrato no es un
          objeto, es una promesa. new PaymentMethod() no compila.
        - Una clase la cumple con la palabra "implements" y está OBLIGADA
          a implementar todos sus métodos. Si falta uno, no compila.
        - Sí puedes declarar variables del tipo de la interface:
              PaymentMethod method = new CashPayment(...);

        Primero, lo que devuelve un cobro. Un "record" es una clase
        pensada solo para cargar datos: Java genera solo el constructor,
        los getters (approved(), detail()), equals, hashCode y toString.
        Tiene su propia guía en el roadmap; aquí solo lo usamos.
    */
    record Receipt(boolean approved, String detail) { }

    /*
        Ahora el contrato. Es literalmente la frase de la capa anterior:
        "esto se puede cobrar, y me devuelve un comprobante".
    */
    interface PaymentMethod {
        Receipt pay(BigDecimal amount);
    }

    /*
        Implementación 1: tarjeta.
        Tiene SUS propios datos (número y límite por compra) y SU propia
        regla. Nada de esto le importa a quien cobre: queda escondido
        detrás de pay().

        Nota sobre BigDecimal: el dinero NUNCA se maneja con double,
        porque double no puede representar exactamente valores como 0.1
        (0.1 + 0.2 da 0.30000000000000004). BigDecimal es exacto.
        Para compararlos se usa compareTo: devuelve >0 si el primero es
        mayor, 0 si son iguales y <0 si es menor.
    */
    static final class CardPayment implements PaymentMethod {
        private final String cardNumber;
        private final BigDecimal limitPerPurchase;

        CardPayment(String cardNumber, BigDecimal limitPerPurchase) {
            requireText(cardNumber, "cardNumber");
            requirePositive(limitPerPurchase);
            this.cardNumber = cardNumber;
            this.limitPerPurchase = limitPerPurchase;
        }

        @Override // le pide al compilador verificar que esto sí cumple un método del contrato
        public Receipt pay(BigDecimal amount) {
            requirePositive(amount);
            if (amount.compareTo(limitPerPurchase) > 0) {
                return new Receipt(false, "Rechazado: excede el límite de la tarjeta " + masked());
            }
            return new Receipt(true, "Cobrado " + amount + " a tarjeta " + masked());
        }

        // Detalle interno: nadie fuera de esta clase necesita saber cómo se enmascara.
        private String masked() {
            return "**** " + cardNumber.substring(cardNumber.length() - 4);
        }
    }

    /*
        Implementación 2: efectivo.
        Datos distintos (cuánto entregó el cliente) y regla distinta
        (calcular cambio). Mismo contrato.
    */
    static final class CashPayment implements PaymentMethod {
        private final BigDecimal received;

        CashPayment(BigDecimal received) {
            requirePositive(received);
            this.received = received;
        }

        @Override
        public Receipt pay(BigDecimal amount) {
            requirePositive(amount);
            if (received.compareTo(amount) < 0) {
                return new Receipt(false, "Rechazado: efectivo insuficiente");
            }
            return new Receipt(true, "Cobrado " + amount + " en efectivo, cambio " + received.subtract(amount));
        }

        // Método propio de CashPayment que NO está en el contrato (se usa en la CAPA 7).
        BigDecimal getReceived() {
            return received;
        }
    }

    static Receipt interfaceExample() {
        // La variable es del tipo del CONTRATO; el objeto es una implementación concreta.
        PaymentMethod method = new CardPayment("4111111111111111", new BigDecimal("5000"));
        return method.pay(new BigDecimal("250.00"));
    }

    /*
        =================================================================
        CAPA 4) DEPENDER DE LA ABSTRACCIÓN, NO DEL DETALLE
        =================================================================

        Ahora reescribimos la tienda. Fíjate en lo que CAMBIÓ respecto a
        la CAPA 2: CheckoutService no menciona tarjeta ni efectivo en
        ningún lado. Solo conoce el contrato PaymentMethod.

        La tienda sigue teniendo SU propia lógica (calcular el IVA), y
        delega el CÓMO cobrar a quien sí sabe hacerlo.
    */
    static final class CheckoutService {
        private static final BigDecimal TAX_RATE = new BigDecimal("0.16");

        Receipt checkout(BigDecimal subtotal, PaymentMethod method) {
            requirePositive(subtotal);
            BigDecimal total = subtotal
                    .multiply(BigDecimal.ONE.add(TAX_RATE))
                    .setScale(2, RoundingMode.HALF_UP);
            return method.pay(total);
        }
    }

    /*
        La prueba de fuego de una buena abstracción: agregar un método
        de pago NUEVO sin tocar CheckoutService.

        Una transferencia bancaria es una clase nueva que cumple el
        contrato. CheckoutService no se modifica ni una línea.
        Esto tiene nombre: Principio Abierto/Cerrado (la "O" de SOLID):
        el código está ABIERTO a extenderse y CERRADO a modificarse.
    */
    static final class TransferPayment implements PaymentMethod {
        private final String accountNumber;

        TransferPayment(String accountNumber) {
            requireText(accountNumber, "accountNumber");
            this.accountNumber = accountNumber;
        }

        @Override
        public Receipt pay(BigDecimal amount) {
            requirePositive(amount);
            return new Receipt(true, "Transferencia de " + amount + " desde cuenta " + accountNumber + " en proceso");
        }
    }

    static List<Receipt> checkoutExample() {
        CheckoutService checkout = new CheckoutService();
        BigDecimal subtotal = new BigDecimal("100.00"); // total con IVA = 116.00

        return List.of(
                checkout.checkout(subtotal, new CardPayment("4111111111111111", new BigDecimal("5000"))),
                checkout.checkout(subtotal, new CashPayment(new BigDecimal("200.00"))),
                checkout.checkout(subtotal, new TransferPayment("012345678901234567"))
        );
    }

    /*
        Compara con la CAPA 2:
        - Sin if/else por tipo de pago.
        - Sin parámetros null: cada clase pide solo lo que necesita.
        - Sin textos "CARD"/"CASH": el tipo lo revisa el compilador.
        - Cada forma de pago se puede probar sola.
        - Agregar una forma nueva = agregar una clase, no editar las demás.
    */

    /*
        =================================================================
        CAPA 5) CLASES ABSTRACTAS: ABSTRACCIÓN + CÓDIGO COMPARTIDO
        =================================================================

        Las interfaces dicen QUÉ, pero no comparten código. A veces varias
        implementaciones repiten exactamente los mismos pasos y solo
        cambian en una parte. Copiar esos pasos en cada clase es duplicar
        código (y duplicar errores).

        Para eso existe la CLASE ABSTRACTA: una clase "a medio hacer".

            abstract class Nombre {
                campos              -> sí puede tener estado
                constructor         -> sí, lo usan las hijas con super(...)
                métodos normales    -> lógica ya terminada y compartida
                métodos abstract    -> huecos que cada hija DEBE rellenar
            }

        Reglas técnicas:
        - No se puede hacer new de una clase abstracta (está incompleta).
        - Una hija la hereda con "extends" y debe implementar TODOS los
          métodos abstract, o declararse abstract ella también.
        - Una clase solo puede extender UNA clase (abstracta o no), pero
          puede implementar MUCHAS interfaces.

        Ejemplo: enviar notificaciones por correo y por SMS.
        Pasos COMPARTIDOS por todos los canales:
            1) validar que destinatario y mensaje no estén vacíos
            2) validar el destinatario según el canal
            3) recortar el mensaje si excede el máximo del canal
            4) darle el formato del canal
        Los pasos 2 y 4 cambian; el 1 y el 3 no, y el ORDEN tampoco.

        Primero el contrato público, como en la capa anterior:
    */
    interface MessageSender {
        String send(String recipient, String message);
    }

    /*
        Y la clase abstracta que lo implementa con los pasos compartidos.
        Esta combinación (interface + clase abstracta base) es la más
        profesional y la que usa el propio Java: List es la interface y
        AbstractList la base que comparte código.
    */
    abstract static class BaseMessageSender implements MessageSender {

        /*
            final: las hijas NO pueden sobrescribir este método.
            Así garantizamos que nadie se salte la validación ni cambie
            el orden de los pasos.
            A este diseño (un método fijo que define el flujo y llama a
            "huecos" que rellenan las hijas) se le llama patrón
            TEMPLATE METHOD.
        */
        @Override
        public final String send(String recipient, String message) {
            requireText(recipient, "recipient");
            requireText(message, "message");
            if (!isValidRecipient(recipient)) {
                throw new IllegalArgumentException("Destinatario inválido: " + recipient);
            }
            String body = message.length() > maxLength() ? message.substring(0, maxLength()) : message;
            return format(recipient, body);
        }

        /*
            Método concreto con un valor por defecto. Las hijas PUEDEN
            sobrescribirlo, pero no están obligadas ("hook").
        */
        protected int maxLength() {
            return 1000;
        }

        /*
            Métodos abstract: los "huecos". Sin cuerpo y con ;.
            protected: solo las hijas los ven; el código cliente usa send().
        */
        protected abstract boolean isValidRecipient(String recipient);

        protected abstract String format(String recipient, String body);
    }

    static final class EmailSender extends BaseMessageSender {
        @Override
        protected boolean isValidRecipient(String recipient) {
            return recipient.contains("@");
        }

        @Override
        protected String format(String recipient, String body) {
            return "EMAIL para " + recipient + ": " + body;
        }
    }

    static final class SmsSender extends BaseMessageSender {
        @Override
        protected boolean isValidRecipient(String recipient) {
            return recipient.matches("\\d{10}"); // exactamente 10 dígitos
        }

        @Override
        protected int maxLength() {
            return 160; // un SMS real tiene ese límite
        }

        @Override
        protected String format(String recipient, String body) {
            return "SMS al " + recipient + ": " + body;
        }
    }

    static String abstractClassExample() {
        MessageSender sms = new SmsSender();
        // Validación, recorte y formato pasan dentro de send(); EmailSender y SmsSender
        // solo escribieron lo que los hace distintos.
        return sms.send("5512345678", "Tu pedido #1042 ya va en camino.");
    }

    /*
        =================================================================
        CAPA 6) REGLAS TÉCNICAS DE LOS MÉTODOS ABSTRACTOS
        =================================================================

        Los fragmentos de esta capa están en comentario por una sola
        razón: son ERRORES DE COMPILACIÓN a propósito. Así sabes qué
        no se puede hacer y por qué.

        1) Un método abstract NO tiene cuerpo.
               abstract void pay(BigDecimal amount) { }     // ERROR
               abstract void pay(BigDecimal amount);        // correcto

        2) Si una clase tiene un método abstract, la clase DEBE ser abstract.
               class Report { abstract String render(); }   // ERROR
           Lógico: si un método no tiene cuerpo, la clase está incompleta.
           (Lo contrario sí se vale: una clase abstract puede no tener
           ningún método abstract; solo impides que la instancien.)

        3) No puede ser private.
               private abstract void validate();            // ERROR
           private significa "nadie más lo ve", pero abstract significa
           "una hija lo debe implementar". Una hija no puede implementar
           algo que no ve. Contradicción.

        4) No puede ser final.
               final abstract void validate();              // ERROR
           final = "nadie lo puede sobrescribir"; abstract = "alguien
           TIENE que sobrescribirlo". Contradicción.

        5) No puede ser static.
               static abstract void validate();             // ERROR
           static pertenece a la clase, no al objeto, y no participa en
           el polimorfismo (Java decide cuál ejecutar al compilar, no al
           correr). Un método abstracto solo tiene sentido si se resuelve
           según el objeto real.

        6) Al implementar, puedes AMPLIAR la visibilidad pero no reducirla.
           protected abstract -> la hija puede hacerlo protected o public.
           public abstract    -> la hija NO puede hacerlo protected.
           Motivo: quien confía en el contrato del padre debe poder
           llamar al método en cualquier hija.

        7) Una clase abstracta SÍ tiene constructor, aunque no se pueda
           hacer new de ella. Se ejecuta cuando creas una hija, a través
           de super(...). Sirve para inicializar el estado compartido.
    */

    /*
        =================================================================
        CAPA 7) ABSTRACCIÓN + POLIMORFISMO
        =================================================================

        La abstracción define un contrato común. El POLIMORFISMO es lo
        que permite que una misma llamada se comporte distinto según el
        objeto real. Una sin la otra sirve poco.

        La regla técnica clave, la que más confusión resuelve:

            PaymentMethod method = new CashPayment(new BigDecimal("50"));
            ^^^^^^^^^^^^^                ^^^^^^^^^^^
            tipo de la REFERENCIA        tipo del OBJETO real

        - Al COMPILAR, Java solo mira el tipo de la referencia para
          decidir qué métodos puedes llamar:
              method.pay(...)        // compila: pay está en PaymentMethod
              method.getReceived()   // NO compila: el contrato no lo tiene,
                                     // aunque el objeto real sí lo tenga

        - Al EJECUTAR, Java mira el objeto real para decidir QUÉ código
          corre. Si el objeto es CashPayment, corre el pay() de
          CashPayment. A esto se le llama "dynamic dispatch" (despacho
          dinámico).

        Eso es lo que permite tratar una lista de cosas distintas como
        si fueran iguales:
    */
    static int countApproved(List<PaymentMethod> methods, BigDecimal amount) {
        int approved = 0;
        for (PaymentMethod method : methods) {
            // Una sola línea; en cada vuelta se ejecuta un pay() diferente.
            if (method.pay(amount).approved()) {
                approved++;
            }
        }
        return approved;
    }

    /*
        El mismo principio aplicado a una dependencia que se guarda como
        campo. OrderNotifier recibe su MessageSender por el constructor.
        No sabe (ni le importa) si avisa por correo o por SMS: eso lo
        decide quien lo crea. A esto se le llama INYECCIÓN DE
        DEPENDENCIAS, y es la base de frameworks como Spring.
    */
    static final class OrderNotifier {
        private final MessageSender sender;

        OrderNotifier(MessageSender sender) {
            this.sender = sender;
        }

        String notifyShipped(String recipient, int orderId) {
            return sender.send(recipient, "Tu pedido #" + orderId + " fue enviado.");
        }
    }

    static int polymorphismExample() {
        List<PaymentMethod> methods = List.of(
                new CardPayment("4111111111111111", new BigDecimal("100")), // límite 100 -> rechaza 150
                new CashPayment(new BigDecimal("200")),                      // alcanza -> aprueba
                new TransferPayment("012345678901234567")                    // aprueba
        );

        // Cambiar de correo a SMS es cambiar UNA línea, no la clase OrderNotifier.
        OrderNotifier byEmail = new OrderNotifier(new EmailSender());
        OrderNotifier bySms = new OrderNotifier(new SmsSender());
        byEmail.notifyShipped("ana@correo.com", 1042);
        bySms.notifyShipped("5512345678", 1042);

        return countApproved(methods, new BigDecimal("150")); // 2
    }

    /*
        =================================================================
        CAPA 8) VARIAS INTERFACES Y MÉTODOS DEFAULT
        =================================================================

        Una clase solo puede extender UNA clase, pero puede implementar
        VARIAS interfaces. Esto permite modelar CAPACIDADES separadas.

        Ejemplo: no todos los métodos de pago permiten reembolso.
        El efectivo ya entregado no se "reembolsa" por sistema; un
        monedero electrónico sí. Si pusiéramos refund() dentro de
        PaymentMethod, obligaríamos a CashPayment a implementar algo
        que no puede hacer (y terminaría lanzando una excepción).

        Solución: una interface aparte para esa capacidad.
        (Esto es la "I" de SOLID: Interface Segregation. Mejor varias
        interfaces pequeñas que una gigante.)

        MÉTODOS DEFAULT (Java 8+):
        Una interface puede traer un método CON cuerpo usando "default".
        Las clases lo heredan gratis y pueden sobrescribirlo si quieren.
        Sirve para dar un comportamiento razonable sin obligar a todas
        las implementaciones a escribirlo.
        Lo que una interface sigue sin poder tener: estado (campos de
        instancia). Solo constantes static final.
    */
    interface Refundable {
        Receipt refund(BigDecimal amount);

        default int refundWindowDays() {
            return 30; // la mayoría usa 30 días; quien sea distinto lo sobrescribe
        }
    }

    /*
        Un monedero electrónico cumple LOS DOS contratos.
        A diferencia de los otros ejemplos, este sí tiene estado que
        cambia (el saldo), por eso balance no es final.
    */
    static final class WalletPayment implements PaymentMethod, Refundable {
        private BigDecimal balance;

        WalletPayment(BigDecimal initialBalance) {
            this.balance = initialBalance;
        }

        @Override
        public Receipt pay(BigDecimal amount) {
            requirePositive(amount);
            if (balance.compareTo(amount) < 0) {
                return new Receipt(false, "Rechazado: saldo insuficiente en monedero");
            }
            balance = balance.subtract(amount);
            return new Receipt(true, "Cobrado " + amount + " del monedero, saldo " + balance);
        }

        @Override
        public Receipt refund(BigDecimal amount) {
            requirePositive(amount);
            balance = balance.add(amount);
            return new Receipt(true, "Reembolsado " + amount + " al monedero, saldo " + balance);
        }

        @Override
        public int refundWindowDays() {
            return 90; // el monedero da más tiempo que el valor por defecto
        }
    }

    /*
        El código cliente sigue trabajando con PaymentMethod, y pregunta
        por la capacidad extra solo donde la necesita.

        "method instanceof Refundable refundable" hace dos cosas a la vez
        (Java 16+): revisa si el objeto real cumple Refundable y, si sí,
        lo deja disponible ya convertido en la variable refundable.
    */
    static Receipt refund(PaymentMethod method, BigDecimal amount, int daysSincePurchase) {
        if (method instanceof Refundable refundable) {
            if (daysSincePurchase > refundable.refundWindowDays()) {
                return new Receipt(false, "Rechazado: fuera del plazo de reembolso");
            }
            return refundable.refund(amount);
        }
        return new Receipt(false, "Este método de pago no admite reembolsos");
    }

    static Receipt multipleInterfacesExample() {
        PaymentMethod wallet = new WalletPayment(new BigDecimal("500"));
        wallet.pay(new BigDecimal("120"));
        return refund(wallet, new BigDecimal("120"), 45); // 45 días < 90 -> aprobado
    }

    /*
        =================================================================
        CAPA 9) CLASE ABSTRACTA VS INTERFACE: CUÁNDO USAR CADA UNA
        =================================================================

        | Pregunta                                | Interface | Clase abstracta |
        |-----------------------------------------|-----------|-----------------|
        | ¿Puede tener campos de instancia?       | No        | Sí              |
        | ¿Puede tener constructor?               | No        | Sí              |
        | ¿Puede tener métodos con cuerpo?        | Sí, default/static/private | Sí |
        | ¿Cuántas puede usar una clase?          | Muchas    | Solo una        |
        | ¿Visibilidad de sus métodos abstractos? | Siempre public | La que elijas |
        | ¿Qué relación expresa?                  | "PUEDE hacer" (capacidad) | "ES UN" (tipo base) |

        Regla práctica que usan los equipos profesionales:

        1) Empieza SIEMPRE con una interface. Es el contrato más ligero
           y no ata a nadie a una jerarquía.
               PaymentMethod, MessageSender, Refundable

        2) Agrega una clase abstracta SOLO cuando veas código repetido
           entre implementaciones, y haz que implemente la interface.
               BaseMessageSender implements MessageSender

        3) El código cliente depende de la INTERFACE, nunca de la clase
           abstracta. Así, si mañana alguien implementa MessageSender
           sin heredar de la base, todo sigue funcionando.
               OrderNotifier recibe MessageSender, no BaseMessageSender.

        Es justo lo que hace Java con sus colecciones:
            List (interface) -> AbstractList (base) -> ArrayList (concreta)
    */

    /*
        =================================================================
        CAPA 10) BUENAS PRÁCTICAS Y ABSTRACCIONES MAL HECHAS
        =================================================================

        1) LA ABSTRACCIÓN NO DEBE "GOTEAR" DETALLES (leaky abstraction)
           Si el contrato menciona la tecnología concreta, ya no abstrae
           nada: el cliente queda atado a ella igual que antes.
    */

    // MAL: el contrato habla de Stripe. Cambiar de proveedor obliga a cambiar el contrato y a todos sus clientes.
    interface LeakyPaymentGateway {
        void chargeWithStripe(String stripeCustomerId, long amountInCents);
    }

    // BIEN: el contrato habla del NEGOCIO. Stripe, PayPal o un banco son solo implementaciones.
    interface PaymentGateway {
        Receipt charge(String customerId, BigDecimal amount);
    }

    /*
        2) NOMBRA POR LO QUE ES, NO POR LO QUE TÉCNICAMENTE ES
           Bien: PaymentMethod, MessageSender, Refundable
           Mal:  IPaymentMethod, MessageSenderContract, PaymentInterface
           En Java no se usa prefijo "I" ni sufijos tipo "Contract".
           Las implementaciones se nombran por lo que las distingue:
           EmailSender, SmsSender (no MessageSenderImpl).

        3) NO ABSTRAIGAS POR SI ACASO
           Una interface con una sola implementación y sin planes reales
           de tener otra solo agrega un archivo más que leer.
           Crea la abstracción cuando aparezca la SEGUNDA implementación
           o cuando necesites cambiarla en tests. (YAGNI: "You Aren't
           Gonna Need It".)

        4) CONTRATOS PEQUEÑOS
           Pocos métodos y muy relacionados. Si una clase implementa una
           interface y deja métodos lanzando UnsupportedOperationException,
           la interface es demasiado grande (ver CAPA 8).

        5) EL CONTRATO DEBE PODER CUMPLIRSE POR TODAS LAS IMPLEMENTACIONES
           Si el código cliente tiene que preguntar "¿y tú qué clase
           eres?" para saber cómo tratar cada objeto, la abstracción está
           mal diseñada. (Preguntar por una CAPACIDAD extra, como
           Refundable en la CAPA 8, sí está bien.)

        6) DEPENDE DE ABSTRACCIONES Y RECÍBELAS DESDE FUERA
           Tus clases deben recibir sus dependencias como interfaces por
           el constructor (CAPA 7, OrderNotifier) en lugar de hacer new
           de una clase concreta por dentro. Es la "D" de SOLID
           (Dependency Inversion) y es lo que hace el código testeable.
    */

    /*
        =================================================================
        CAPA 11) ERRORES COMUNES
        =================================================================

        1) "Abstracción es lo mismo que herencia."
           No. La forma más común de abstraer es con interfaces, sin
           herencia de clases (CAPAS 3 y 4).

        2) "Abstraer es esconder todo."
           No. Es mostrar exactamente lo necesario para usar algo, ni más
           ni menos.

        3) "Abstracción y encapsulación son lo mismo."
           No. Encapsulación protege el estado; abstracción define la cara
           pública (CAPA 1).

        4) "Una clase abstracta se puede instanciar."
           No. new BaseMessageSender() no compila. Necesitas una hija
           concreta.

        5) "Si tengo una clase abstracta no necesito interface."
           Funciona, pero ata a todos a tu jerarquía. Interface primero,
           base abstracta después (CAPA 9).

        6) "El cliente debe conocer la clase concreta para usarla bien."
           Al revés: si el cliente necesita conocerla, la abstracción
           falló (CAPA 10, punto 5).

        7) Hacer downcasting para usar métodos que no están en el contrato.
           ((CashPayment) method).getReceived()
           Si lo necesitas seguido, o el método debería estar en el
           contrato, o ese código debería recibir CashPayment directamente.

        8) Crear abstracciones que no representan una idea real del
           negocio ("Manager", "Helper", "Processor" genéricos).
           Una buena abstracción se puede explicar en una frase:
           "un método de pago es algo que puede cobrar una cantidad".
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Abstraer = separar QUÉ hace algo (contrato) de CÓMO lo hace
          (implementación).
        - Interface  = contrato puro. Empieza siempre por aquí.
        - Clase abstracta = contrato + código compartido entre hijas.
        - El código cliente depende del contrato; las implementaciones
          se pueden agregar o cambiar sin tocarlo.
        - Al compilar manda el tipo de la referencia (qué puedes llamar);
          al ejecutar manda el objeto real (qué código corre).
        - Una buena abstracción habla el idioma del negocio, es pequeña,
          no gotea detalles y aparece cuando hay una necesidad real.
    */

    // -----------------------------------------------------------------
    // Utilidades internas de la guía (validaciones compartidas).
    // -----------------------------------------------------------------

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " no puede ser null ni vacío");
        }
    }

    private static void requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor que cero");
        }
    }

    private static void check(boolean condition, String layer) {
        if (!condition) {
            throw new IllegalStateException("El ejemplo de la " + layer + " no hace lo que la guía explica");
        }
    }
}
