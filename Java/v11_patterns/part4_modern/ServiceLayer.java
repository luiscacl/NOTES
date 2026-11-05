package v11_patterns.part4_modern;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/*
    =====================================================================
    SERVICE LAYER: LOS CASOS DE USO DE LA APLICACIÓN, EN UN SOLO LUGAR
    =====================================================================

    Ruta de patterns/: guía 27 de 28  (part4_modern)
    Anterior:  part4_modern/DTO
    Siguiente: part4_modern/Specification

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.
    - No hay base de datos real: hay una base de datos EN MEMORIA que sí
      tiene transacciones (deshace todo si algo falla), para ver lo que
      hace una de verdad.

    Lo que ya se vio y aquí solo se usa:
    - una fachada que coordina sin tener las reglas (part2_structural/
      Facade, CAPAS 2-3): un servicio ES una fachada sobre el dominio;
    - inyección por constructor (v10_design/part3_responsibilities/
      DependencyInjection, CAPA 3);
    - repositorios (part4_modern/Repository) y DTOs (part4_modern/DTO);
    - cómo funciona @Transactional por dentro y su trampa (part2_structural/
      Proxy, CAPA 6).

    Importancia: ALTA. Es la capa del medio de casi todas las aplicaciones
    empresariales (las clases que en Spring llevan @Service).

    Índice:
        CAPA 1)  El problema: el mismo flujo en cada puerta de entrada
        CAPA 2)  Las capas y qué va en cada una
        CAPA 3)  El servicio coordina; el dominio decide
        CAPA 4)  Varias puertas, un solo servicio
        CAPA 5)  El servicio es la frontera de la transacción
        CAPA 6)  Efectos externos: después de confirmar
        CAPA 7)  Errores del negocio y su traducción en la puerta
        CAPA 8)  Cómo organizar los servicios
        CAPA 9)  Las consultas no necesitan tanta ceremonia
        CAPA 10) Errores comunes
        RESUMEN FINAL
*/
public class ServiceLayer {

    public static void main(String[] args) {
        check(domainRulesExample(), "CAPA 3");
        check(twoEntryPointsExample(), "CAPA 4");
        check(rollbackExample(), "CAPA 5");
        check(afterCommitExample(), "CAPA 6");
        check(errorTranslationExample(), "CAPA 7");
        System.out.println("ServiceLayer: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) EL PROBLEMA: EL MISMO FLUJO EN CADA PUERTA DE ENTRADA
        =================================================================

        "Hacer un pedido" es un flujo de varios pasos:

            buscar el producto -> apartar existencias -> guardar el pedido
            -> cobrar -> avisar al cliente por correo

        Y una aplicación real recibe pedidos por varias PUERTAS: la API HTTP
        que usa la app, un archivo CSV que sube un cliente mayorista cada
        noche, mensajes de otro sistema. Si cada puerta escribe el flujo por
        su cuenta, hay tres copias; alguien corrige un error en una (por
        ejemplo, no avisar si el cobro falló) y las otras dos siguen mal.

        La CAPA DE SERVICIO (Martin Fowler) resuelve eso: define las
        OPERACIONES que ofrece la aplicación ("hacer un pedido", "cancelar
        un pedido") en un solo lugar, y cada puerta solo traduce su formato
        y llama a la operación. Cada operación es un CASO DE USO.
    */

    /*
        =================================================================
        CAPA 2) LAS CAPAS Y QUÉ VA EN CADA UNA
        =================================================================

            Puerta de entrada (controller HTTP, lector de CSV, consumidor
            de mensajes)
                - traduce el formato de afuera (JSON, líneas, mensajes) a un
                  comando, y el resultado o el error de vuelta;
                - SIN reglas de negocio.
                        |
                        v
            Servicio (un método por caso de uso)
                - abre y cierra la transacción;
                - busca con los repositorios, llama al dominio, guarda;
                - dispara los efectos externos (correo, cobro);
                - SIN HTTP, SIN SQL, y sin las reglas del dominio.
                        |
                        v
            Dominio (Product, Order) + repositorios
                - las REGLAS: no apartar más de lo que hay, cómo se calcula
                  el total;
                - no sabe que existen el servicio, HTTP ni la base de datos.

        La regla de las dependencias: cada capa conoce a la de abajo, nunca
        a la de arriba. El servicio no sabe si lo llamó HTTP o un CSV; el
        dominio no sabe que hay un servicio.
    */

    // ---- Dominio -----------------------------------------------------

    static final class OutOfStockException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        OutOfStockException(String sku, int requested, int available) {
            super("Sin existencias de " + sku + ": pidió " + requested + ", hay " + available);
        }
    }

    static final class UnknownProductException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        UnknownProductException(String sku) {
            super("No existe el producto " + sku);
        }
    }

    static final class PaymentDeclinedException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        PaymentDeclinedException(String email) {
            super("Pago rechazado para " + email);
        }
    }

    /*
        =================================================================
        CAPA 3) EL SERVICIO COORDINA; EL DOMINIO DECIDE
        =================================================================

        El error más común de esta capa es el SERVICIO QUE HACE TODO:

            Product product = products.findBySku(sku);
            if (product.getStock() < quantity) throw ...;      // regla
            product.setStock(product.getStock() - quantity);   // regla

        Funciona, pero la regla "no apartar más de lo que hay" vive en el
        servicio. El día que otro servicio (devoluciones, ajustes de
        inventario) toque las existencias, la repetirá o la olvidará. Y
        Product queda como una bolsa de getters y setters sin
        comportamiento: a esto Fowler le llama MODELO DE DOMINIO ANÉMICO.

        Lo correcto: la regla vive EN Product (reserve), y cualquiera que
        aparte existencias pasa por ella (es el Information Expert de
        v10_design/part3_responsibilities/GRASP). El servicio solo
        coordina: busca, le pide al dominio, guarda.

        Una pista para saber si un servicio está bien: se lee como la lista
        de pasos del caso de uso, sin ifs de reglas de negocio.
    */
    record Product(String sku, long priceCents, int stock) {
        Product reserve(int quantity) {                     // LA regla, en un solo lugar
            if (quantity > stock) {
                throw new OutOfStockException(sku, quantity, stock);
            }
            return new Product(sku, priceCents, stock - quantity);
        }

        long priceFor(int quantity) {
            return priceCents * quantity;
        }
    }

    record Order(long id, String customerEmail, String sku, int quantity, long totalCents) {
        Order withId(long newId) {
            return new Order(newId, customerEmail, sku, quantity, totalCents);
        }
    }

    static boolean domainRulesExample() {
        Product coffee = new Product("CAFE", 4_500, 3);
        boolean rejected;
        try {
            coffee.reserve(5);
            rejected = false;
        } catch (OutOfStockException e) {
            rejected = true;
        }
        return rejected && coffee.reserve(2).stock() == 1;  // el dominio se prueba solo, sin servicio
    }

    // ---- Lo que el servicio necesita (interfaces del lado del negocio) ---

    interface ProductRepository {
        Optional<Product> findBySku(String sku);

        void save(Product product);
    }

    interface OrderRepository {
        Order save(Order order);

        long count();
    }

    interface Transactions {
        <T> T inTransaction(Supplier<T> work);
    }

    interface PaymentGateway {
        void charge(String email, long cents);             // lanza PaymentDeclinedException
    }

    interface Notifier {
        void orderConfirmed(Order order);
    }

    // ---- Servicio ------------------------------------------------------

    /*
        La entrada del servicio es un COMANDO: un record con lo que el caso
        de uso necesita, validado en su forma (como un DTO de entrada;
        part4_modern/DTO, CAPA 5). La salida es otro record con lo que la
        puerta necesita contestar. Así el servicio no depende del formato
        de ninguna puerta.
    */
    record PlaceOrderCommand(String customerEmail, String sku, int quantity) {
        PlaceOrderCommand {
            if (customerEmail == null || !customerEmail.contains("@")) {
                throw new IllegalArgumentException("correo inválido");
            }
            if (sku == null || sku.isBlank()) {
                throw new IllegalArgumentException("sku es obligatorio");
            }
            if (quantity <= 0) {
                throw new IllegalArgumentException("la cantidad debe ser positiva");
            }
        }
    }

    record PlaceOrderResult(long orderId, long totalCents) {
    }

    static final class OrderService {
        private final ProductRepository products;
        private final OrderRepository orders;
        private final Transactions transactions;
        private final PaymentGateway payments;
        private final Notifier notifier;

        OrderService(ProductRepository products, OrderRepository orders, Transactions transactions,
                PaymentGateway payments, Notifier notifier) {
            this.products = products;
            this.orders = orders;
            this.transactions = transactions;
            this.payments = payments;
            this.notifier = notifier;
        }

        /** El caso de uso, leído como su lista de pasos. */
        PlaceOrderResult placeOrder(PlaceOrderCommand command) {
            Order order = transactions.inTransaction(() -> {                  // todo o nada (CAPA 5)
                Product product = products.findBySku(command.sku())
                        .orElseThrow(() -> new UnknownProductException(command.sku()));
                products.save(product.reserve(command.quantity()));          // la regla la decide el dominio
                Order saved = orders.save(new Order(0, command.customerEmail(), command.sku(),
                        command.quantity(), product.priceFor(command.quantity())));
                payments.charge(command.customerEmail(), saved.totalCents()); // si falla, se deshace todo
                return saved;
            });
            notifier.orderConfirmed(order);                                   // después de confirmar (CAPA 6)
            return new PlaceOrderResult(order.id(), order.totalCents());
        }
    }

    /*
        =================================================================
        CAPA 4) VARIAS PUERTAS, UN SOLO SERVICIO
        =================================================================

        Dos puertas distintas, el MISMO servicio:
        - OrderHttpController: recibe el JSON (aquí, un Map, como en la guía
          de DTO) y contesta un código HTTP;
        - NightlyCsvImport: recibe líneas "correo;sku;cantidad" de un
          archivo y cuenta cuántas entraron y cuántas fallaron.

        Ninguna de las dos sabe apartar existencias ni cobrar. Si mañana
        llega una tercera puerta (mensajes de otro sistema), es otra clase
        pequeña que traduce y llama a placeOrder. El flujo y sus
        correcciones están en un solo lugar.
    */
    record HttpReply(int status, String body) {
    }

    static final class OrderHttpController {
        private final OrderService service;

        OrderHttpController(OrderService service) {
            this.service = service;
        }

        HttpReply post(Map<String, String> json) {
            try {
                PlaceOrderCommand command = new PlaceOrderCommand(json.get("email"), json.get("sku"),
                        Integer.parseInt(json.getOrDefault("quantity", "0")));
                PlaceOrderResult result = service.placeOrder(command);
                return new HttpReply(201, "{\"orderId\":" + result.orderId() + ",\"totalCents\":" + result.totalCents() + "}");
            } catch (UnknownProductException e) {                             // CAPA 7: traducir
                return new HttpReply(404, e.getMessage());
            } catch (OutOfStockException e) {
                return new HttpReply(409, e.getMessage());
            } catch (PaymentDeclinedException e) {
                return new HttpReply(402, e.getMessage());
            } catch (IllegalArgumentException e) {                            // incluye NumberFormatException
                return new HttpReply(400, e.getMessage());
            }
        }
    }

    record ImportReport(int accepted, List<String> rejected) {
        ImportReport {
            rejected = List.copyOf(rejected);
        }
    }

    static final class NightlyCsvImport {
        private final OrderService service;

        NightlyCsvImport(OrderService service) {
            this.service = service;
        }

        ImportReport run(List<String> lines) {
            int accepted = 0;
            List<String> rejected = new ArrayList<>();
            for (String line : lines) {
                String[] parts = line.split(";");
                try {
                    service.placeOrder(new PlaceOrderCommand(parts[0], parts[1], Integer.parseInt(parts[2])));
                    accepted++;
                } catch (RuntimeException e) {                                // una línea mala no detiene el archivo
                    rejected.add(line + " -> " + e.getMessage());
                }
            }
            return new ImportReport(accepted, rejected);
        }
    }

    static boolean twoEntryPointsExample() {
        Wiring app = Wiring.create(1_000_000);
        HttpReply reply = new OrderHttpController(app.service())
                .post(Map.of("email", "ana@correo.com", "sku", "CAFE", "quantity", "2"));
        ImportReport report = new NightlyCsvImport(app.service())
                .run(List.of("tienda@correo.com;CAFE;5", "tienda@correo.com;TAZA;50"));

        return reply.status() == 201
                && report.accepted() == 1 && report.rejected().size() == 1     // TAZA: no alcanzan
                && app.database().stockOf("CAFE") == 3                         // 10 - 2 - 5: las dos puertas, mismas reglas
                && app.database().count() == 2;
    }

    /*
        =================================================================
        CAPA 5) EL SERVICIO ES LA FRONTERA DE LA TRANSACCIÓN
        =================================================================

        Un caso de uso es TODO O NADA. Si el cobro falla después de apartar
        las existencias y guardar el pedido, no puede quedar un pedido sin
        pagar ni existencias apartadas para nadie.

        Eso lo da una TRANSACCIÓN de la base de datos: se abre al empezar el
        caso de uso; si todo sale bien se CONFIRMA (commit) y los cambios
        quedan; si algo lanza una excepción se DESHACE (rollback) y la base
        de datos queda como estaba, como si nada hubiera pasado.

        ¿Dónde se abre? En el SERVICIO, porque es el único que sabe dónde
        empieza y termina el caso de uso:
        - el repositorio no sabe: un save es solo un paso;
        - el controller no debe: no sabe de base de datos.

        En Spring es @Transactional sobre el método del servicio; un proxy
        abre y cierra la transacción alrededor (part2_structural/Proxy,
        CAPA 6). Aquí está a mano con inTransaction, y la base de datos en
        memoria lo imita guardando una foto antes y restaurándola si falla
        (un Memento: part3_behavioral/Memento).

        El ejemplo: el cobro es rechazado. Las existencias siguen iguales y
        no hay pedido guardado.
    */
    static final class InMemoryDatabase implements ProductRepository, OrderRepository, Transactions {
        private Map<String, Product> productsBySku = new HashMap<>();
        private Map<Long, Order> ordersById = new LinkedHashMap<>();
        private long nextOrderId = 1;

        // Simplificación: sin aislamiento entre hilos ni bloqueos; una base de datos real sí los tiene.
        @Override
        public <T> T inTransaction(Supplier<T> work) {
            Map<String, Product> productsBefore = new HashMap<>(productsBySku);    // la foto
            Map<Long, Order> ordersBefore = new LinkedHashMap<>(ordersById);
            long nextIdBefore = nextOrderId;
            try {
                return work.get();                                                  // commit: los cambios quedan
            } catch (RuntimeException e) {
                productsBySku = productsBefore;                                     // rollback
                ordersById = ordersBefore;
                nextOrderId = nextIdBefore;
                throw e;
            }
        }

        @Override
        public Optional<Product> findBySku(String sku) {
            return Optional.ofNullable(productsBySku.get(sku));
        }

        @Override
        public void save(Product product) {
            productsBySku.put(product.sku(), product);
        }

        @Override
        public Order save(Order order) {
            Order stored = order.id() == 0 ? order.withId(nextOrderId++) : order;
            ordersById.put(stored.id(), stored);
            return stored;
        }

        @Override
        public long count() {
            return ordersById.size();
        }

        int stockOf(String sku) {
            return productsBySku.get(sku).stock();
        }
    }

    static boolean rollbackExample() {
        Wiring app = Wiring.create(10_000);                    // el banco rechaza más de 100.00
        boolean declined;
        try {
            app.service().placeOrder(new PlaceOrderCommand("ana@correo.com", "CAFE", 3));   // 135.00
            declined = false;
        } catch (PaymentDeclinedException e) {
            declined = true;
        }
        return declined
                && app.database().stockOf("CAFE") == 10        // las existencias regresaron
                && app.database().count() == 0;                // el pedido no quedó
    }

    /*
        =================================================================
        CAPA 6) EFECTOS EXTERNOS: DESPUÉS DE CONFIRMAR
        =================================================================

        El rollback solo deshace lo que está EN la base de datos. Un correo
        enviado ya se fue; no se puede "desenviar". Si placeOrder mandara el
        correo DENTRO de la transacción y después algo fallara, el cliente
        recibiría "tu pedido fue confirmado" de un pedido que no existe.

        Regla: los efectos que no se pueden deshacer (correos, mensajes a
        otros sistemas) van DESPUÉS de confirmar. En placeOrder, el aviso
        está fuera de inTransaction: solo se ejecuta si la transacción
        terminó bien.

        El cobro también es una llamada externa, y tiene el problema
        contrario: si el cobro pasa y DESPUÉS falla el commit, se cobró un
        pedido que no quedó. Los sistemas de pagos reales lo manejan
        separando "apartar el dinero" de "cobrarlo", o revisando y
        devolviendo después. Es un tema de arquitectura (v14_architecture/);
        aquí basta con saber que existe y que un caso de uso que habla con
        otros sistemas no es tan simple como "todo o nada".

        Y si el correo es importante (no puede perderse aunque el programa
        se caiga justo después del commit), la técnica usual es guardar
        "hay que mandar este correo" en la MISMA transacción, y que otro
        proceso los mande después. Se llama "outbox" (bandeja de salida).
    */
    static boolean afterCommitExample() {
        Wiring failing = Wiring.create(10_000);
        try {
            failing.service().placeOrder(new PlaceOrderCommand("ana@correo.com", "CAFE", 3));
        } catch (PaymentDeclinedException expected) {
            // el rechazo es el caso que se prueba; lo que importa es el aviso
        }
        Wiring working = Wiring.create(1_000_000);
        working.service().placeOrder(new PlaceOrderCommand("ana@correo.com", "CAFE", 3));

        return failing.sentEmails().isEmpty()                  // no se avisó de un pedido que no existe
                && working.sentEmails().equals(List.of("ana@correo.com: pedido 1"));
    }

    /*
        =================================================================
        CAPA 7) ERRORES DEL NEGOCIO Y SU TRADUCCIÓN EN LA PUERTA
        =================================================================

        El servicio lanza excepciones con SIGNIFICADO DEL NEGOCIO:
        OutOfStockException, UnknownProductException,
        PaymentDeclinedException. No sabe de códigos HTTP: un 409 no
        significa nada para el importador de CSV.

        Cada puerta las traduce a SU idioma:
            OrderHttpController         NightlyCsvImport
            ------------------------    ------------------------------
            producto no existe -> 404   la línea va a "rechazadas"
            sin existencias    -> 409   la línea va a "rechazadas"
            pago rechazado     -> 402   la línea va a "rechazadas"
            datos mal formados -> 400   la línea va a "rechazadas"

        Así las reglas de cuándo algo es un error viven en el servicio y el
        dominio, y cómo se le comunica a cada quien vive en cada puerta.
        (En Spring, la traducción a HTTP se hace en una sola clase con
        @ControllerAdvice, para no repetir los catch en cada controller.)
    */
    static boolean errorTranslationExample() {
        Wiring app = Wiring.create(10_000);
        OrderHttpController http = new OrderHttpController(app.service());
        return http.post(Map.of("email", "a@b.com", "sku", "CAFE", "quantity", "2")).status() == 201    // 90.00
                && http.post(Map.of("email", "a@b.com", "sku", "NADA", "quantity", "1")).status() == 404
                && http.post(Map.of("email", "a@b.com", "sku", "TAZA", "quantity", "99")).status() == 409
                && http.post(Map.of("email", "a@b.com", "sku", "CAFE", "quantity", "5")).status() == 402
                && http.post(Map.of("email", "a@b.com", "sku", "CAFE", "quantity", "dos")).status() == 400;
    }

    /*
        =================================================================
        CAPA 8) CÓMO ORGANIZAR LOS SERVICIOS
        =================================================================

        Dos formas comunes, las dos válidas:

        - UN SERVICIO POR ÁREA del negocio: OrderService con placeOrder,
          cancelOrder, changeAddress. Es lo más común con Spring. El
          riesgo: crece hasta tener 40 métodos y 15 dependencias, y
          entonces mezcla actores distintos (v10_design/part2_solid/
          SingleResponsibility).

        - UNA CLASE POR CASO DE USO: PlaceOrder, CancelOrder, cada una con
          un solo método público y solo las dependencias que ese caso
          necesita. Más clases, pero cada una pequeña y fácil de probar.

        Señales de que hay que partir: un constructor con muchas
        dependencias, métodos que no comparten ninguna, o pruebas que tienen
        que armar cosas que el método probado no usa.

        Y un cuidado: un servicio que llama a otro servicio que llama a otro
        forma cadenas difíciles de seguir, y cada uno puede abrir su propia
        transacción. Si dos casos de uso comparten pasos, los pasos van al
        dominio o a una clase auxiliar, no a un servicio que llama a otro.
    */

    /*
        =================================================================
        CAPA 9) LAS CONSULTAS NO NECESITAN TANTA CEREMONIA
        =================================================================

        Todo lo anterior es para OPERACIONES QUE CAMBIAN algo: ahí hay
        reglas, transacción y efectos. Una CONSULTA ("los últimos 20 pedidos
        del cliente") no cambia nada: no hay reglas del dominio que
        proteger.

        Para consultas, el servicio puede ser muy delgado (o no existir):
        pide al repositorio una proyección (part4_modern/DTO, CAPA 6) y la
        devuelve. Cargar objetos del dominio completos solo para copiarlos a
        un DTO es trabajo inútil.

        Separar así los comandos (cambian) de las consultas (leen) es una
        idea que llevada al extremo se llama CQRS, y se verá en
        v14_architecture/.
    */

    /*
        =================================================================
        CAPA 10) ERRORES COMUNES
        =================================================================

        1) El mismo flujo escrito en cada puerta de entrada (CAPA 1).

        2) Reglas de negocio en el servicio y un dominio de puros getters y
           setters (modelo anémico) (CAPA 3).

        3) El servicio recibiendo o devolviendo cosas de HTTP (requests,
           códigos de estado) (CAPAS 4 y 7).

        4) La transacción abierta en el repositorio o en el controller, o
           sin transacción: casos de uso a medias (CAPA 5).

        5) Correos y mensajes enviados dentro de la transacción: avisos de
           cosas que se deshicieron (CAPA 6).

        6) Servicios gigantes, o servicios que se llaman en cadena (CAPA 8).

        7) Cargar el dominio completo para una simple consulta (CAPA 9).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - La capa de servicio define las operaciones de la aplicación (los
          casos de uso) en un solo lugar; todas las puertas las usan.
        - Puerta: traduce formatos y errores. Servicio: coordina y define
          la transacción. Dominio: las reglas.
        - Entrada como comando, salida como resultado: el servicio no sabe
          de HTTP.
        - Todo o nada con una transacción alrededor del caso de uso; los
          efectos que no se deshacen van después de confirmar.
        - Excepciones con significado del negocio; cada puerta las traduce.
        - Servicios pequeños; las consultas, sin ceremonia.
    */

    // -----------------------------------------------------------------
    // Utilidades internas de la guía: arma la aplicación para cada ejemplo
    // (el "composition root" de DependencyInjection, CAPA 4).
    // -----------------------------------------------------------------

    record Wiring(OrderService service, InMemoryDatabase database, List<String> sentEmails) {
        static Wiring create(long paymentLimitCents) {
            InMemoryDatabase database = new InMemoryDatabase();
            database.save(new Product("CAFE", 4_500, 10));
            database.save(new Product("TAZA", 12_000, 2));
            List<String> sentEmails = new ArrayList<>();
            PaymentGateway bank = (email, cents) -> {
                if (cents > paymentLimitCents) {
                    throw new PaymentDeclinedException(email);
                }
            };
            Notifier notifier = order -> sentEmails.add(order.customerEmail() + ": pedido " + order.id());
            OrderService service = new OrderService(database, database, database, bank, notifier);
            return new Wiring(service, database, sentEmails);
        }
    }

    private static void check(boolean condition, String layer) {
        if (!condition) {
            throw new IllegalStateException("El ejemplo de la " + layer + " no hace lo que la guía explica");
        }
    }
}
