package v14_architecture.part2_boundaries;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/*
    =====================================================================
    ARQUITECTURA HEXAGONAL: PUERTOS Y ADAPTADORES
    =====================================================================

    Ruta de architecture/: guía 3 de 6  (part2_boundaries)
    Anterior:  part1_layers/DDDBasics
    Siguiente: part2_boundaries/CleanArchitecture

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.
    - El adaptador HTTP es de verdad: levanta un servidor en 127.0.0.1 en
      un puerto libre y le hace peticiones (como v8_apis/
      part2_io_and_network/HttpClientUsage, CAPA 2).

    Lo que ya se vio: la inversión de la flecha hacia la base de datos
    (part1_layers/LayeredArchitecture, CAPA 4), el patrón Adapter
    (v11_patterns/part2_structural/Adapter) y la capa anticorrupción
    (part1_layers/DDDBasics, CAPA 9). Hexagonal lleva esas ideas a TODAS
    las fronteras de la aplicación.

    Importancia: ALTA. Es la arquitectura más mencionada en equipos
    modernos de backend, y la que hace la aplicación más fácil de probar.

    Índice:
        CAPA 1)  La idea: adentro y afuera, no arriba y abajo
        CAPA 2)  Puertos de entrada: lo que la aplicación ofrece
        CAPA 3)  Puertos de salida: lo que la aplicación necesita
        CAPA 4)  El núcleo: los casos de uso, sin tecnología
        CAPA 5)  Adaptadores de salida
        CAPA 6)  Adaptadores de entrada: el mismo núcleo, tres puertas
        CAPA 7)  Probar el núcleo sin nada de afuera
        CAPA 8)  Cambiar de proveedor sin tocar el núcleo
        CAPA 9)  Cómo se organiza en paquetes
        CAPA 10) Cuándo vale la pena y errores comunes
        RESUMEN FINAL
*/
public class Hexagonal {

    public static void main(String[] args) throws Exception {
        check(coreWithFakesExample(), "CAPA 7");
        check(threeDrivingAdaptersExample(), "CAPA 6");
        check(swapProviderExample(), "CAPA 8");
        System.out.println("Hexagonal: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) LA IDEA: ADENTRO Y AFUERA, NO ARRIBA Y ABAJO
        =================================================================

        Alistair Cockburn la propuso en 2005 con un dibujo de un hexágono
        (el número de lados no significa nada: era espacio para dibujar
        varias conexiones). La idea:

                         HTTP        línea de comandos     pruebas
                            \               |                /
                         [adaptador]   [adaptador]     [adaptador]
                              \             |              /
                               ( puerto de entrada: casos de uso )
                         +------------------------------------------+
                         |          NÚCLEO DE LA APLICACIÓN          |
                         |   casos de uso + dominio, Java puro       |
                         +------------------------------------------+
                               ( puertos de salida: lo que necesita )
                              /             |              \
                       [adaptador]     [adaptador]      [adaptador]
                           /                |                \
                    base de datos     proveedor de pagos    correo

        En las capas clásicas (part1_layers/LayeredArchitecture) hay un
        "arriba" (la interfaz) y un "abajo" (la base de datos). Hexagonal
        dice: todo lo que NO es el núcleo está AFUERA, igual. La interfaz
        web y la base de datos son lo mismo para el núcleo: tecnología que
        se conecta por un puerto.

        - PUERTO: una interface que define cómo se conversa con el núcleo,
          en SUS términos.
        - ADAPTADOR: la clase que conecta una tecnología concreta con un
          puerto, traduciendo (el patrón Adapter).

        La regla: TODAS las dependencias apuntan HACIA ADENTRO. El núcleo no
        importa nada de HTTP, SQL, ni de ningún proveedor.
    */

    /*
        =================================================================
        CAPA 2) PUERTOS DE ENTRADA: LO QUE LA APLICACIÓN OFRECE
        =================================================================

        Los puertos de ENTRADA (también "primarios" o "driving": los que
        PONEN EN MARCHA a la aplicación) son interfaces con los casos de uso
        que la aplicación ofrece al mundo. Los llama alguien de afuera: un
        controller HTTP, un comando de consola, un consumidor de mensajes,
        una prueba.

        Reciben comandos y devuelven resultados, que son objetos del núcleo
        (records simples), nunca requests ni JSON.
    */
    record PlaceOrderCommand(String customerEmail, long totalCents) {
    }

    record OrderConfirmation(long orderId, String paymentReference) {
    }

    interface PlaceOrderUseCase {                           // puerto de entrada
        OrderConfirmation placeOrder(PlaceOrderCommand command);
    }

    /*
        =================================================================
        CAPA 3) PUERTOS DE SALIDA: LO QUE LA APLICACIÓN NECESITA
        =================================================================

        Los puertos de SALIDA ("secundarios" o "driven": los que la
        aplicación PONE EN MARCHA) son interfaces con lo que el núcleo
        necesita del mundo: guardar pedidos, cobrar, avisar, saber la hora.
        Los DEFINE el núcleo, en sus términos, y los implementa un
        adaptador afuera (DependencyInversion, en v10_design/part2_solid/).

        Fíjate en los nombres: PaymentPort.charge, no StripeClient ni
        BankApiRequest. El núcleo dice QUÉ necesita; cómo se hace es
        problema del adaptador. Y el error también es del núcleo:
        PaymentDeclinedException, no la excepción de la librería del
        proveedor.

        Hasta la HORA es un puerto: el núcleo recibe un Clock en vez de
        llamar a Instant.now(), para que las pruebas controlen el tiempo
        (DependencyInversion, CAPA 9).
    */
    record Order(long id, String customerEmail, long totalCents, Instant placedAt) {
    }

    interface OrderStorePort {                              // puertos de salida
        Order save(Order order);

        Optional<Order> findById(long id);
    }

    interface PaymentPort {
        String charge(String customerEmail, long cents);    // devuelve una referencia de pago
    }

    interface NotificationPort {
        void orderConfirmed(Order order);
    }

    static final class PaymentDeclinedException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        PaymentDeclinedException(String reason) {
            super("Pago rechazado: " + reason);
        }
    }

    /*
        =================================================================
        CAPA 4) EL NÚCLEO: LOS CASOS DE USO, SIN TECNOLOGÍA
        =================================================================

        El servicio que IMPLEMENTA el puerto de entrada y USA los puertos de
        salida. Es la capa de servicio de v11_patterns/part4_modern/
        ServiceLayer, sin cambios en su forma: lo que cambia es que TODO lo
        que toca está detrás de un puerto.

        Revisa sus imports mentalmente: solo cosas del propio núcleo y de
        java.time. Ni HTTP, ni archivos, ni proveedores. Por eso se puede
        compilar, entender y probar solo.
    */
    static final class OrderService implements PlaceOrderUseCase {
        static final long MINIMUM_CENTS = 10_000;

        private final OrderStorePort orders;
        private final PaymentPort payments;
        private final NotificationPort notifications;
        private final Clock clock;

        OrderService(OrderStorePort orders, PaymentPort payments, NotificationPort notifications, Clock clock) {
            this.orders = orders;
            this.payments = payments;
            this.notifications = notifications;
            this.clock = clock;
        }

        @Override
        public OrderConfirmation placeOrder(PlaceOrderCommand command) {
            if (command.totalCents() < MINIMUM_CENTS) {
                throw new IllegalArgumentException("El pedido mínimo es de 100 pesos");
            }
            String reference = payments.charge(command.customerEmail(), command.totalCents());
            Order saved = orders.save(new Order(0, command.customerEmail(), command.totalCents(), clock.instant()));
            notifications.orderConfirmed(saved);
            return new OrderConfirmation(saved.id(), reference);
        }
    }

    /*
        =================================================================
        CAPA 5) ADAPTADORES DE SALIDA
        =================================================================

        Cada adaptador de salida implementa un puerto con una tecnología:

            OrderStorePort    -> InMemoryOrderStore (pruebas), y en
                                 producción uno con la base de datos
            PaymentPort       -> AcmePayAdapter, que habla con la "API" del
                                 proveedor Acme
            NotificationPort  -> un adaptador de correo, o uno que solo
                                 anota (pruebas)

        El adaptador TRADUCE en las dos direcciones (es una capa
        anticorrupción; DDDBasics, CAPA 9):
        - del núcleo al proveedor: centavos -> el formato que pide Acme;
        - del proveedor al núcleo: sus códigos de respuesta -> la
          referencia de pago o una PaymentDeclinedException del núcleo.

        AcmePayApi representa la librería o API de un proveedor externo: no
        es nuestra, no la podemos cambiar, y su forma es la suya (montos en
        pesos con decimales como texto, códigos de estado).
    */
    static final class InMemoryOrderStore implements OrderStorePort {
        private final Map<Long, Order> rows = new HashMap<>();
        private long nextId = 1;

        @Override
        public synchronized Order save(Order order) {       // el servidor HTTP atiende en varios hilos
            Order stored = new Order(nextId++, order.customerEmail(), order.totalCents(), order.placedAt());
            rows.put(stored.id(), stored);
            return stored;
        }

        @Override
        public synchronized Optional<Order> findById(long id) {
            return Optional.ofNullable(rows.get(id));
        }
    }

    record AcmeChargeResult(String status, String transactionId, String errorCode) {
    }

    static final class AcmePayApi {                         // "la librería del proveedor": no es nuestra
        AcmeChargeResult createCharge(String payerEmail, String amountMxn) {
            if (Double.parseDouble(amountMxn) > 5_000) {
                return new AcmeChargeResult("DECLINED", null, "LIMIT_EXCEEDED");
            }
            return new AcmeChargeResult("APPROVED", "acme-" + payerEmail.hashCode(), null);
        }
    }

    static final class AcmePayAdapter implements PaymentPort {
        private final AcmePayApi api;

        AcmePayAdapter(AcmePayApi api) {
            this.api = api;
        }

        @Override
        public String charge(String customerEmail, long cents) {
            String amount = cents / 100 + "." + String.format(Locale.ROOT, "%02d", cents % 100);   // núcleo -> Acme
            AcmeChargeResult result = api.createCharge(customerEmail, amount);
            if (!result.status().equals("APPROVED")) {
                throw new PaymentDeclinedException(result.errorCode());              // Acme -> núcleo
            }
            return result.transactionId();
        }
    }

    static final class RecordingNotifications implements NotificationPort {
        final List<String> sent = new ArrayList<>();

        @Override
        public synchronized void orderConfirmed(Order order) {
            sent.add(order.customerEmail() + ": pedido " + order.id());
        }
    }

    /*
        =================================================================
        CAPA 6) ADAPTADORES DE ENTRADA: EL MISMO NÚCLEO, TRES PUERTAS
        =================================================================

        Un adaptador de entrada recibe algo del mundo, lo traduce a un
        comando, llama al puerto de entrada, y traduce la respuesta o el
        error de vuelta. Tres, sobre el MISMO núcleo:

        - HttpOrderAdapter: un servidor HTTP real (com.sun.net.httpserver,
          el servidor pequeño que trae el JDK) que recibe
          "email=...&total=..." y responde 201, 400 o 402;
        - CliOrderAdapter: una "línea de comandos" que recibe argumentos
          y devuelve un texto;
        - y las PRUEBAS (CAPA 7), que llaman al puerto directamente.

        Ninguno sabe cómo se cobra ni dónde se guarda. Si mañana llegan
        pedidos por una cola de mensajes, es otro adaptador pequeño.
    */
    static final class HttpOrderAdapter implements AutoCloseable {
        private final HttpServer server;

        HttpOrderAdapter(PlaceOrderUseCase useCase) throws IOException {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/orders", exchange -> {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                int status;
                String response;
                try {
                    Map<String, String> form = parseForm(body);                     // HTTP -> comando
                    OrderConfirmation confirmation = useCase.placeOrder(
                            new PlaceOrderCommand(form.get("email"), Long.parseLong(form.get("total"))));
                    status = 201;
                    response = "pedido " + confirmation.orderId();
                } catch (PaymentDeclinedException e) {                              // error del núcleo -> HTTP
                    status = 402;
                    response = e.getMessage();
                } catch (IllegalArgumentException e) {
                    status = 400;
                    response = e.getMessage();
                }
                byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status, bytes.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(bytes);
                }
            });
            server.start();
        }

        URI uri() {
            return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/orders");
        }

        @Override
        public void close() {
            server.stop(0);
        }

        private static Map<String, String> parseForm(String body) {
            Map<String, String> form = new HashMap<>();
            for (String pair : body.split("&")) {
                String[] keyValue = pair.split("=", 2);
                form.put(keyValue[0], keyValue.length > 1 ? keyValue[1] : "");
            }
            return form;
        }
    }

    static final class CliOrderAdapter {
        private final PlaceOrderUseCase useCase;

        CliOrderAdapter(PlaceOrderUseCase useCase) {
            this.useCase = useCase;
        }

        String run(String... args) {                        // "pedido --email ana@correo.com --total 25000"
            try {
                OrderConfirmation confirmation = useCase.placeOrder(
                        new PlaceOrderCommand(args[2], Long.parseLong(args[4])));
                return "OK pedido " + confirmation.orderId() + " (pago " + confirmation.paymentReference() + ")";
            } catch (RuntimeException e) {
                return "ERROR " + e.getMessage();
            }
        }
    }

    static boolean threeDrivingAdaptersExample() throws Exception {
        InMemoryOrderStore store = new InMemoryOrderStore();
        RecordingNotifications notifications = new RecordingNotifications();
        PlaceOrderUseCase core = new OrderService(store, new AcmePayAdapter(new AcmePayApi()),
                notifications, Clock.systemUTC());          // composition root: se arma una vez

        String cli = new CliOrderAdapter(core).run("pedido", "--email", "luis@correo.com", "--total", "15000");

        try (HttpClient client = HttpClient.newHttpClient();
             HttpOrderAdapter http = new HttpOrderAdapter(core)) {
            HttpResponse<String> created = post(client, http.uri(), "email=ana@correo.com&total=25000");
            HttpResponse<String> tooSmall = post(client, http.uri(), "email=ana@correo.com&total=500");
            HttpResponse<String> declined = post(client, http.uri(), "email=ana@correo.com&total=900000");

            return cli.startsWith("OK pedido 1 (pago acme-")
                    && created.statusCode() == 201 && created.body().equals("pedido 2")
                    && tooSmall.statusCode() == 400
                    && declined.statusCode() == 402 && declined.body().equals("Pago rechazado: LIMIT_EXCEEDED")
                    && notifications.sent.equals(List.of("luis@correo.com: pedido 1", "ana@correo.com: pedido 2"));
        }
    }

    /*
        =================================================================
        CAPA 7) PROBAR EL NÚCLEO SIN NADA DE AFUERA
        =================================================================

        La mayor ganancia práctica. Para probar las reglas del núcleo se
        conecta a cada puerto de salida una versión FALSA y controlable:
        - un almacén en memoria;
        - un pago falso que aprueba o rechaza a voluntad (una lambda: los
          puertos de un método son interfaces funcionales);
        - notificaciones que solo se anotan;
        - un reloj FIJO.

        Sin red, sin base de datos, sin servidor: la prueba corre en
        microsegundos y siempre da lo mismo. Se prueba lo que importa:
        "si el pago se rechaza, NO se guarda el pedido ni se avisa".

        Los adaptadores se prueban aparte, cada uno con su tecnología (el
        adaptador de base de datos contra una base de datos de pruebas; ver
        las pruebas de contrato de v11_patterns/part4_modern/Repository,
        CAPA 5).
    */
    static boolean coreWithFakesExample() {
        Clock fixed = Clock.fixed(Instant.parse("2026-11-04T10:00:00Z"), ZoneOffset.UTC);
        InMemoryOrderStore store = new InMemoryOrderStore();
        RecordingNotifications notifications = new RecordingNotifications();

        PlaceOrderUseCase approving = new OrderService(store, (email, cents) -> "fake-123", notifications, fixed);
        OrderConfirmation ok = approving.placeOrder(new PlaceOrderCommand("ana@correo.com", 20_000));
        boolean savedWithFixedTime = store.findById(ok.orderId())
                .map(order -> order.placedAt().equals(Instant.parse("2026-11-04T10:00:00Z")))
                .orElse(false);

        PlaceOrderUseCase declining = new OrderService(store, (email, cents) -> {
            throw new PaymentDeclinedException("FONDOS");
        }, notifications, fixed);
        boolean declined;
        try {
            declining.placeOrder(new PlaceOrderCommand("eve@correo.com", 20_000));
            declined = false;
        } catch (PaymentDeclinedException e) {
            declined = true;
        }
        return ok.paymentReference().equals("fake-123") && savedWithFixedTime
                && declined
                && store.findById(2).isEmpty()              // rechazado: no se guardó...
                && notifications.sent.size() == 1;          // ...ni se avisó
    }

    /*
        =================================================================
        CAPA 8) CAMBIAR DE PROVEEDOR SIN TOCAR EL NÚCLEO
        =================================================================

        La empresa cambia de proveedor de pagos: de Acme a Zeta, que tiene
        OTRA forma (montos en centavos como número, un booleano "ok", y
        otro nombre para todo). En una aplicación sin puertos, el código de
        Acme estaría repartido por los servicios, y el cambio tocaría
        muchos archivos.

        Aquí: se escribe ZetaPayAdapter, se cambia UNA línea en el
        composition root, y el núcleo, sus reglas y sus pruebas no se
        tocan. El ejemplo procesa el mismo pedido con los dos proveedores.

        Durante una migración, incluso pueden convivir: un adaptador que
        manda el 10% de los pagos a Zeta y el resto a Acme (un Strategy o
        un Decorator sobre el mismo puerto).
    */
    record ZetaResponse(boolean ok, String id, String reason) {
    }

    static final class ZetaPayClient {                      // otro proveedor, otra forma
        ZetaResponse pay(long amountInCents, String customerRef) {
            return new ZetaResponse(true, "zeta-" + amountInCents, null);
        }
    }

    static final class ZetaPayAdapter implements PaymentPort {
        private final ZetaPayClient client;

        ZetaPayAdapter(ZetaPayClient client) {
            this.client = client;
        }

        @Override
        public String charge(String customerEmail, long cents) {
            ZetaResponse response = client.pay(cents, customerEmail);
            if (!response.ok()) {
                throw new PaymentDeclinedException(response.reason());
            }
            return response.id();
        }
    }

    static boolean swapProviderExample() {
        PlaceOrderCommand command = new PlaceOrderCommand("ana@correo.com", 30_000);
        PlaceOrderUseCase withAcme = new OrderService(new InMemoryOrderStore(),
                new AcmePayAdapter(new AcmePayApi()), new RecordingNotifications(), Clock.systemUTC());
        PlaceOrderUseCase withZeta = new OrderService(new InMemoryOrderStore(),
                new ZetaPayAdapter(new ZetaPayClient()), new RecordingNotifications(), Clock.systemUTC());   // la línea que cambia
        return withAcme.placeOrder(command).paymentReference().startsWith("acme-")
                && withZeta.placeOrder(command).paymentReference().equals("zeta-30000");
    }

    /*
        =================================================================
        CAPA 9) CÓMO SE ORGANIZA EN PAQUETES
        =================================================================

        Una forma común:

            com.tienda.orders
                application/
                    port/in/       PlaceOrderUseCase, comandos y resultados
                    port/out/      OrderStorePort, PaymentPort, NotificationPort
                    OrderService   (implementa port/in, usa port/out)
                domain/            Order, value objects, reglas (DDDBasics)
                adapter/
                    in/web/        HttpOrderAdapter (en Spring: un @RestController)
                    in/cli/        CliOrderAdapter
                    out/persistence/  el repositorio con la base de datos
                    out/payment/   AcmePayAdapter
                config/            el composition root (en Spring: @Configuration)

        La regla de dependencias se verifica con una prueba de arquitectura
        (part1_layers/LayeredArchitecture, CAPA 6): nada en application/ ni
        domain/ importa de adapter/.
    */

    /*
        =================================================================
        CAPA 10) CUÁNDO VALE LA PENA Y ERRORES COMUNES
        =================================================================

        Vale la pena cuando:
        - la aplicación habla con VARIOS sistemas externos (pagos, correo,
          otras APIs, colas) que pueden cambiar o fallar;
        - hay reglas de negocio que se quieren probar rápido y sin
          infraestructura;
        - hay varias formas de entrar (web, mensajes, tareas programadas).

        Es exceso en un CRUD pequeño: un puerto y un adaptador por cada
        tabla, que solo pasan datos, es ceremonia (LayeredArchitecture,
        CAPA 9).

        Errores comunes:

        1) Puertos con nombres o tipos del proveedor (StripePort,
           BankApiRequest): el núcleo queda atado a la tecnología (CAPA 3).

        2) Dejar pasar las excepciones de la librería del proveedor hacia
           el núcleo, en vez de traducirlas (CAPA 5).

        3) Lógica de negocio en los adaptadores ("si el total es mayor
           a...", en el controller) (CAPA 6).

        4) Usar Instant.now() o UUID.randomUUID() directo en el núcleo: no
           se puede probar (CAPA 3).

        5) Probar el núcleo con la base de datos o el proveedor reales,
           cuando un falso bastaba (CAPA 7).

        6) Un puerto por cada método de cada tabla: hexagonal no significa
           multiplicar interfaces sin necesidad (CAPA 10).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Núcleo adentro (casos de uso + dominio, Java puro); todo lo demás
          afuera; dependencias siempre hacia adentro.
        - Puertos de entrada: los casos de uso que se ofrecen. Puertos de
          salida: lo que el núcleo necesita, en sus términos (hasta el
          reloj).
        - Adaptadores: conectan una tecnología con un puerto y traducen
          formatos y errores en las dos direcciones.
        - El mismo núcleo atiende HTTP, línea de comandos y pruebas.
        - El núcleo se prueba con falsos, en microsegundos.
        - Cambiar de proveedor: un adaptador nuevo y una línea en el
          composition root.
    */

    // -----------------------------------------------------------------
    // Utilidades internas de la guía.
    // -----------------------------------------------------------------

    private static HttpResponse<String> post(HttpClient client, URI uri, String form)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static void check(boolean condition, String layer) {
        if (!condition) {
            throw new IllegalStateException("El ejemplo de la " + layer + " no hace lo que la guía explica");
        }
    }
}
