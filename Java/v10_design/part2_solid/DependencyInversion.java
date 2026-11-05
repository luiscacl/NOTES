package v10_design.part2_solid;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/*
    =====================================================================
    PRINCIPIO DE INVERSIÓN DE DEPENDENCIAS (LA D DE SOLID)
    =====================================================================

    Ruta de design/: guía 6 de 10  (part2_solid)
    Anterior:  part2_solid/InterfaceSegregation
    Siguiente: part3_responsibilities/DependencyInjection

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.
    - Los "detalles" (base de datos, servidor de correo) se simulan con
      clases en memoria: lo que importa aquí es la DIRECCIÓN de las
      dependencias, no la tecnología.

    Índice:
        CAPA 1)  Qué dice el principio
        CAPA 2)  Alto nivel y bajo nivel: políticas y mecanismos
        CAPA 3)  El problema: las reglas de negocio atadas a los detalles
        CAPA 4)  La inversión: la regla define lo que necesita
        CAPA 5)  Quién es dueño de la interface
        CAPA 6)  Las abstracciones no dependen de los detalles
        CAPA 7)  El premio: probar las reglas sin infraestructura
        CAPA 8)  Cambiar y combinar implementaciones
        CAPA 9)  El tiempo también es una dependencia
        CAPA 10) Inversión, inyección e inversión de control
        CAPA 11) Dónde NO invertir
        CAPA 12) Errores comunes
        RESUMEN FINAL
*/
public class DependencyInversion {

    public static void main(String[] args) {
        check(coupledServiceExample(), "CAPA 3");
        check(invertedServiceExample(), "CAPA 4");
        check(leakyAbstractionExample(), "CAPA 5");
        check(domainErrorsExample(), "CAPA 6");
        check(testingExample(), "CAPA 7");
        check(swapAndDecorateExample(), "CAPA 8");
        check(timeDependencyExample(), "CAPA 9");
        System.out.println("DependencyInversion: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) QUÉ DICE EL PRINCIPIO
        =================================================================

        Robert C. Martin lo escribió en dos partes:

        A) Los módulos de ALTO NIVEL no deben depender de los módulos de
           BAJO NIVEL. Ambos deben depender de ABSTRACCIONES.
        B) Las abstracciones no deben depender de los detalles. Los
           detalles deben depender de las abstracciones.

        Antes de explicarlo hay que entender tres palabras:
        - "Depender de": que el código de A MENCIONA a B (lo importa, lo
          crea con new, usa sus tipos). Si B cambia, A puede tener que
          cambiar, y no compila sin B.
        - "Alto nivel" y "bajo nivel" (CAPA 2).
        - "Abstracción": una interface (o clase abstracta) que describe QUÉ
          se necesita sin decir CÓMO se hace (v2_oop/part2_pillars/
          Abstraction, CAPA 4).
    */

    /*
        =================================================================
        CAPA 2) ALTO NIVEL Y BAJO NIVEL: POLÍTICAS Y MECANISMOS
        =================================================================

        ALTO NIVEL = las POLÍTICAS: las reglas del negocio, la razón de
        ser del sistema. "Un pedido se aprueba si el cliente no tiene
        adeudos y hay existencias; al aprobarse, se le avisa."

        BAJO NIVEL = los MECANISMOS: cómo se hacen las cosas técnicamente.
        "Se guarda en una tabla de PostgreSQL", "el aviso es un correo por
        SMTP", "las existencias se consultan por HTTP a otro servicio".

        Dos diferencias clave:
        1) VALOR: las políticas son lo que el negocio paga. Los mecanismos
           son medios: intercambiables.
        2) RITMO DE CAMBIO: los mecanismos cambian por razones técnicas
           (otra base de datos, otro proveedor de correo, una versión nueva
           de una API) que no tienen nada que ver con las reglas.

        Por eso lo deseable es que los mecanismos dependan de las
        políticas, y no al revés: que cambiar el proveedor de correo NUNCA
        obligue a tocar la regla de aprobación.
    */

    /*
        =================================================================
        CAPA 3) EL PROBLEMA: LAS REGLAS DE NEGOCIO ATADAS A LOS DETALLES
        =================================================================

        La forma "natural" de programar, de arriba hacia abajo: la regla
        necesita guardar y avisar, así que crea lo que necesita:

            final class OrderApproval {
                private final PostgresOrderTable table = new PostgresOrderTable();
                private final SmtpMailer mailer = new SmtpMailer("smtp.tienda.com");
                ...
            }

        Las flechas de dependencia van de la política hacia los
        mecanismos:

            OrderApproval  ──depende de──>  PostgresOrderTable
                           ──depende de──>  SmtpMailer

        Consecuencias:
        - NO SE PUEDE PROBAR la regla sin una base de datos y un servidor
          de correo reales. Y una prueba que manda correos de verdad...
        - NO SE PUEDE CAMBIAR el mecanismo sin editar la regla.
        - Los tipos técnicos (conexiones, excepciones de SQL) se filtran al
          código de negocio.

        El ejemplo simula un SmtpMailer que, como en una computadora sin
        servidor de correo, falla al conectarse. La regla de aprobación es
        correcta, pero es IMPOSIBLE ejecutarla para probarla.
    */
    record Order(String id, String customer, BigDecimal total) { }

    static final class SmtpMailer {                         // detalle: hablaría con un servidor real
        private final String host;

        SmtpMailer(String host) {
            this.host = host;
        }

        void send(String to, String text) {
            throw new IllegalStateException("No se pudo conectar a " + host + ":25");   // aquí no hay servidor
        }
    }

    static final class OrderApprovalCoupled {
        private final Map<String, Order> table = new HashMap<>();             // "la tabla de la base de datos"
        private final SmtpMailer mailer = new SmtpMailer("smtp.tienda.com");  // creado aquí adentro: imposible cambiarlo

        boolean approve(Order order, boolean customerHasDebt) {
            if (customerHasDebt) {                          // la REGLA (lo valioso)
                return false;
            }
            table.put(order.id(), order);
            mailer.send(order.customer(), "Pedido " + order.id() + " aprobado");
            return true;
        }
    }

    static boolean coupledServiceExample() {
        OrderApprovalCoupled approval = new OrderApprovalCoupled();
        boolean rejectsDebtors = !approval.approve(new Order("A-1", "ana", BigDecimal.TEN), true);
        boolean cannotEvenRunTheHappyPath;
        try {
            approval.approve(new Order("A-2", "luis", BigDecimal.TEN), false);
            cannotEvenRunTheHappyPath = false;
        } catch (IllegalStateException e) {
            cannotEvenRunTheHappyPath = true;               // la regla está bien; el detalle no deja probarla
        }
        return rejectsDebtors && cannotEvenRunTheHappyPath;
    }

    /*
        =================================================================
        CAPA 4) LA INVERSIÓN: LA REGLA DEFINE LO QUE NECESITA
        =================================================================

        La regla de aprobación DECLARA, con interfaces, lo que necesita en
        SUS términos (no en términos de SQL o SMTP):

            interface ApprovedOrders { void store(Order order); }
            interface CustomerNotifier { void orderApproved(Order order); }

        y las RECIBE desde afuera (por el constructor; cómo se arma todo es
        part3_responsibilities/DependencyInjection). Los mecanismos
        IMPLEMENTAN esas interfaces.

        Ahora las flechas de dependencia del CÓDIGO son:

            OrderApproval  ──depende de──>  ApprovedOrders  <──implementa──  PostgresApprovedOrders
                           ──depende de──>  CustomerNotifier <──implementa── SmtpCustomerNotifier

        La flecha que antes iba de la política al mecanismo ahora va del
        mecanismo HACIA la abstracción que definió la política. Esa es la
        "inversión".

        Ojo con la confusión típica: al EJECUTAR, el flujo sigue igual (la
        regla llama a store y store guarda en la base de datos). Lo que se
        invierte es la dependencia del CÓDIGO FUENTE: quién menciona a
        quién, quién compila sin quién. La regla de negocio ya compila sin
        que exista ninguna base de datos ni ningún correo.
    */
    interface ApprovedOrders {
        void store(Order order);

        Optional<Order> find(String id);
    }

    interface CustomerNotifier {
        void orderApproved(Order order);
    }

    // ALTO NIVEL: no menciona ninguna tecnología.
    static final class OrderApproval {
        private final ApprovedOrders orders;
        private final CustomerNotifier notifier;

        OrderApproval(ApprovedOrders orders, CustomerNotifier notifier) {
            this.orders = orders;
            this.notifier = notifier;
        }

        boolean approve(Order order, boolean customerHasDebt) {
            if (customerHasDebt) {
                return false;
            }
            orders.store(order);
            notifier.orderApproved(order);
            return true;
        }
    }

    // BAJO NIVEL: implementan lo que la política definió.
    static final class InMemoryApprovedOrders implements ApprovedOrders {
        private final Map<String, Order> rows = new HashMap<>();

        @Override
        public void store(Order order) {
            rows.put(order.id(), order);
        }

        @Override
        public Optional<Order> find(String id) {
            return Optional.ofNullable(rows.get(id));
        }
    }

    static final class RecordingNotifier implements CustomerNotifier {
        final List<String> sent = new ArrayList<>();

        @Override
        public void orderApproved(Order order) {
            sent.add(order.customer() + ": pedido " + order.id() + " aprobado");
        }
    }

    static boolean invertedServiceExample() {
        InMemoryApprovedOrders orders = new InMemoryApprovedOrders();
        RecordingNotifier notifier = new RecordingNotifier();
        OrderApproval approval = new OrderApproval(orders, notifier);

        boolean approved = approval.approve(new Order("A-2", "luis", BigDecimal.TEN), false);   // ahora sí se ejecuta
        return approved && orders.find("A-2").isPresent()
                && notifier.sent.equals(List.of("luis: pedido A-2 aprobado"));
    }

    /*
        =================================================================
        CAPA 5) QUIÉN ES DUEÑO DE LA INTERFACE
        =================================================================

        Que haya una interface en medio NO basta. Si la interface la
        escribió el equipo de base de datos, a su medida, la política
        sigue dependiendo del detalle, solo que con un paso más:

            interface Database {
                void executeSql(String sql);       // la regla tiene que escribir SQL
            }

        La regla de negocio tendría que armar SQL: sigue atada a la
        tecnología, y cambiar a otra base de datos rompe la regla igual.
        A eso se le llama una ABSTRACCIÓN CON FUGAS (leaky abstraction):
        deja pasar los detalles que debía esconder.

        La interface debe pertenecer al lado de la POLÍTICA: con nombres y
        tipos del negocio (store(Order), orderApproved(Order)), pensada
        desde quien la usa (part2_solid/InterfaceSegregation, CAPA 5). En
        un proyecto real, incluso vive en el MISMO paquete que la regla, no
        en el de la base de datos.

        El ejemplo muestra la regla escrita contra una abstracción con
        fugas: para guardar, la regla de negocio arma texto SQL.
    */
    interface Database {                                    // abstracción con FUGAS: habla de SQL
        void executeSql(String sql);
    }

    static final class LeakyOrderApproval {
        private final Database database;

        LeakyOrderApproval(Database database) {
            this.database = database;
        }

        void approve(Order order) {
            database.executeSql("INSERT INTO orders VALUES ('" + order.id() + "')");   // la regla escribe SQL
        }
    }

    static boolean leakyAbstractionExample() {
        List<String> executed = new ArrayList<>();
        new LeakyOrderApproval(executed::add).approve(new Order("A-3", "mia", BigDecimal.ONE));
        return executed.equals(List.of("INSERT INTO orders VALUES ('A-3')"));   // tecnología dentro del negocio
    }

    /*
        =================================================================
        CAPA 6) LAS ABSTRACCIONES NO DEPENDEN DE LOS DETALLES
        =================================================================

        La parte B del principio. La FIRMA de la interface no debe
        mencionar tipos de ninguna tecnología:
        - nada de ResultSet o SQLException (de la base de datos),
        - ni de HttpResponse (de la red),
        - ni de las clases de una librería de correo.

        Si la interface de la política lanza SQLException, toda la política
        queda atada a SQL, aunque nunca vea una consulta.

        Lo correcto: la implementación TRADUCE los errores técnicos a
        excepciones del NEGOCIO, definidas junto a la interface
        (v3_exceptions/part2_in_practice/ErrorHandlingBestPractices,
        CAPA 8: traducir en los límites). La política maneja
        "NotificationFailedException", sin saber si era SMTP, SMS o una
        API; la causa técnica viaja dentro, para los logs.
    */
    static final class NotificationFailedException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        NotificationFailedException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    // El detalle traduce su error técnico al lenguaje del negocio.
    static final class SmtpCustomerNotifier implements CustomerNotifier {
        @Override
        public void orderApproved(Order order) {
            try {
                connectToSmtp();
            } catch (IllegalStateException technical) {
                throw new NotificationFailedException("No se pudo avisar al cliente " + order.customer(), technical);
            }
        }

        private void connectToSmtp() {
            throw new IllegalStateException("Connection refused: smtp.tienda.com:25");
        }
    }

    static boolean domainErrorsExample() {
        try {
            new SmtpCustomerNotifier().orderApproved(new Order("A-4", "ana", BigDecimal.ONE));
            return false;
        } catch (NotificationFailedException e) {
            return e.getMessage().equals("No se pudo avisar al cliente ana")           // lenguaje del negocio
                    && e.getCause().getMessage().startsWith("Connection refused");     // el detalle, guardado como causa
        }
    }

    /*
        =================================================================
        CAPA 7) EL PREMIO: PROBAR LAS REGLAS SIN INFRAESTRUCTURA
        =================================================================

        Con la dependencia invertida, la regla se prueba con
        implementaciones de prueba en memoria ("dobles de prueba";
        testing/): rápidas, sin red, sin base de datos, y que permiten
        SIMULAR situaciones difíciles de provocar con lo real, como que el
        correo falle.

        El ejemplo prueba tres casos de la regla en microsegundos:
        - cliente con adeudo: no se guarda ni se avisa;
        - cliente sin adeudo: se guarda y se avisa;
        - el aviso falla: ¿qué pasa con el pedido? La prueba hace visible
          una decisión de negocio que hay que tomar (aquí, el pedido queda
          guardado aunque el aviso falle, y el error sube para que alguien
          reintente el aviso).
    */
    static boolean testingExample() {
        InMemoryApprovedOrders orders = new InMemoryApprovedOrders();
        RecordingNotifier notifier = new RecordingNotifier();
        OrderApproval approval = new OrderApproval(orders, notifier);

        boolean debtorRejected = !approval.approve(new Order("B-1", "deudor", BigDecimal.TEN), true)
                && orders.find("B-1").isEmpty() && notifier.sent.isEmpty();
        boolean normalApproved = approval.approve(new Order("B-2", "ana", BigDecimal.TEN), false)
                && notifier.sent.size() == 1;

        CustomerNotifier failing = order -> {               // simular la falla: imposible con un servidor real estable
            throw new NotificationFailedException("servicio de correo caído", null);
        };
        OrderApproval withFailingNotifier = new OrderApproval(orders, failing);
        boolean failurePropagates;
        try {
            withFailingNotifier.approve(new Order("B-3", "luis", BigDecimal.TEN), false);
            failurePropagates = false;
        } catch (NotificationFailedException e) {
            failurePropagates = orders.find("B-3").isPresent();   // la decisión de negocio, ahora visible y probada
        }

        return debtorRejected && normalApproved && failurePropagates;
    }

    /*
        =================================================================
        CAPA 8) CAMBIAR Y COMBINAR IMPLEMENTACIONES
        =================================================================

        Como la política solo conoce la interface:

        1) CAMBIAR el mecanismo no toca la regla: pasar de correo a SMS (o a
           una notificación en la app) es escribir otra implementación de
           CustomerNotifier y entregarla al construir OrderApproval.

        2) COMBINAR comportamientos sin tocar ninguna de las piezas: una
           implementación puede ENVOLVER a otra y agregarle algo (avisar
           por dos canales, reintentar, registrar en un log, medir
           tiempos). Es el patrón Decorator (v11_patterns/). El ejemplo arma un
           notificador que avisa por correo y por SMS, a partir de dos que
           ya existían.
    */
    static final class BothChannelsNotifier implements CustomerNotifier {   // compone dos implementaciones
        private final CustomerNotifier first;
        private final CustomerNotifier second;

        BothChannelsNotifier(CustomerNotifier first, CustomerNotifier second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public void orderApproved(Order order) {
            first.orderApproved(order);
            second.orderApproved(order);
        }
    }

    static boolean swapAndDecorateExample() {
        RecordingNotifier email = new RecordingNotifier();
        RecordingNotifier sms = new RecordingNotifier();
        OrderApproval approval = new OrderApproval(new InMemoryApprovedOrders(), new BothChannelsNotifier(email, sms));

        approval.approve(new Order("C-1", "ana", BigDecimal.TEN), false);   // OrderApproval no cambió
        return email.sent.size() == 1 && sms.sent.size() == 1;
    }

    /*
        =================================================================
        CAPA 9) EL TIEMPO TAMBIÉN ES UNA DEPENDENCIA
        =================================================================

        Una dependencia escondida muy común: el RELOJ. Una regla que llama
        Instant.now() o LocalDate.now() por dentro depende del reloj del
        sistema operativo, un "detalle" que no controlas, igual que la base
        de datos. Por eso es imposible probar "la oferta vence a
        medianoche" sin esperar a medianoche.

        El JDK ya trae la abstracción: java.time.Clock
        (v8_apis/part1_text_and_time/DatesAndTimes, CAPA 12). La regla
        recibe un Clock; en producción se le da el reloj del sistema y en
        pruebas uno fijo. Es DIP aplicado al tiempo.

        Lo mismo pasa con lo ALEATORIO (recibe un Random o un generador),
        con las variables de entorno y con los archivos: todo lo que viene
        "de afuera" del programa es un detalle que conviene recibir.
    */
    static final class FlashSale {
        private final Clock clock;
        private final Instant endsAt;

        FlashSale(Clock clock, Instant endsAt) {
            this.clock = clock;
            this.endsAt = endsAt;
        }

        boolean isActive() {
            return Instant.now(clock).isBefore(endsAt);     // el tiempo llega como dependencia
        }
    }

    static boolean timeDependencyExample() {
        Instant midnight = Instant.parse("2024-11-16T06:00:00Z");
        FlashSale beforeMidnight = new FlashSale(Clock.fixed(Instant.parse("2024-11-16T05:59:00Z"), ZoneOffset.UTC), midnight);
        FlashSale afterMidnight = new FlashSale(Clock.fixed(Instant.parse("2024-11-16T06:01:00Z"), ZoneOffset.UTC), midnight);
        return beforeMidnight.isActive() && !afterMidnight.isActive();     // sin esperar a medianoche
    }

    /*
        =================================================================
        CAPA 10) INVERSIÓN, INYECCIÓN E INVERSIÓN DE CONTROL
        =================================================================

        Tres nombres parecidos que se confunden todo el tiempo:

        | Concepto                    | Qué es                                       | Dónde se ve                  |
        |-----------------------------|----------------------------------------------|------------------------------|
        | Inversión de dependencias   | Un PRINCIPIO de diseño: las políticas        | esta guía                    |
        | (DIP)                       | dependen de abstracciones, y los detalles    |                              |
        |                             | las implementan                              |                              |
        | Inyección de dependencias   | Una TÉCNICA: un objeto RECIBE sus            | part3_responsibilities/      |
        | (DI)                        | dependencias desde afuera (normalmente por   | DependencyInjection          |
        |                             | el constructor) en vez de crearlas           |                              |
        | Inversión de control (IoC)  | Un estilo: un framework controla el flujo y  | v15_spring/                      |
        |                             | llama a TU código ("no nos llames, nosotros  |                              |
        |                             | te llamamos")                                |                              |

        Cómo se relacionan: DIP dice HACIA DÓNDE deben apuntar las
        dependencias; la inyección es la forma práctica de entregarlas; y
        un framework como Spring (el más usado en Java) usa inversión de
        control para hacer esa inyección automáticamente.

        En esta guía ya usaste inyección (OrderApproval recibe sus
        dependencias por el constructor) porque es la forma natural de
        aplicar DIP.
    */

    /*
        =================================================================
        CAPA 11) DÓNDE NO INVERTIR
        =================================================================

        Una interface por cada clase "por si acaso" es un antipatrón
        conocido: duplica tipos, agrega saltos al leer, y no protege de
        nada (BasicPrinciples, CAPAS 7 y 8).

        NO hace falta invertir la dependencia con lo ESTABLE:
        - el JDK: String, List, BigDecimal, LocalDate no van a cambiar
          detrás de ti;
        - objetos de valor y records del propio dominio (Order, Money);
        - lógica pura que no toca el mundo exterior (un cálculo de
          impuestos).

        SÍ conviene invertir en los BORDES del sistema, donde están los
        detalles VOLÁTILES o lentos:
        - base de datos y archivos,
        - red: otros servicios, APIs, correo, mensajería,
        - el tiempo y lo aleatorio (CAPA 9),
        - cualquier cosa de un proveedor externo que podrías cambiar.

        Llevado a todo un sistema, este principio da la forma de las
        arquitecturas modernas (hexagonal, "clean"; v14_architecture/): el
        dominio (las reglas) en el centro, sin depender de nada técnico, y
        los adaptadores (base de datos, web, correo) alrededor,
        implementando las interfaces que el dominio define.
    */

    /*
        =================================================================
        CAPA 12) ERRORES COMUNES
        =================================================================

        1) La regla de negocio crea con new sus detalles (base de datos,
           correo, cliente HTTP): imposible probar o cambiar (CAPA 3).

        2) Creer que "invertir" cambia el flujo al ejecutar: cambia la
           dependencia del código fuente (CAPA 4).

        3) Interfaces escritas a la medida del detalle (executeSql): una
           abstracción con fugas (CAPA 5).

        4) Firmas de la abstracción con tipos técnicos (SQLException,
           ResultSet, HttpResponse) (CAPA 6).

        5) Llamar Instant.now(), LocalDate.now() o new Random() dentro de
           la lógica (CAPA 9).

        6) Confundir DIP, inyección e IoC (CAPA 10).

        7) Una interface para cada clase, incluidas las estables (CAPA 11).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Las políticas (reglas de negocio) no dependen de los mecanismos
          (base de datos, red, correo); ambos dependen de abstracciones.
        - La política DEFINE las interfaces que necesita, en sus términos;
          los mecanismos las implementan: la dependencia del código se
          invierte (el flujo al ejecutar no).
        - Sin fugas: nada de SQL ni tipos técnicos en la abstracción; los
          errores técnicos se traducen a errores del negocio.
        - Premio: probar las reglas con dobles en memoria, cambiar
          mecanismos y combinarlos sin tocar la regla.
        - El reloj y lo aleatorio también son dependencias: Clock.
        - DIP es el principio; la inyección, la técnica; IoC, el estilo de
          los frameworks.
        - Invierte en los bordes volátiles, no en lo estable.
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
