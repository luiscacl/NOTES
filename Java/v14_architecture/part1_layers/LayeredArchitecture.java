package v14_architecture.part1_layers;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/*
    =====================================================================
    ARQUITECTURA EN CAPAS: ORGANIZAR UNA APLICACIÓN COMPLETA
    =====================================================================

    Ruta de architecture/: guía 1 de 6  (part1_layers)
    Anterior:  v13_concurrency/part3_tasks/VirtualThreads
    Siguiente: part1_layers/DDDBasics

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.
    - Las "capas" de una arquitectura son paquetes; aquí, como todo vive
      en un archivo, cada clase lleva una anotación @Layer que dice a qué
      capa pertenece, y una prueba de arquitectura (CAPA 6) revisa las
      dependencias de verdad.

    Lo que ya se vio y aquí se junta: la capa de servicio (v11_patterns/
    part4_modern/ServiceLayer), los repositorios (v11_patterns/
    part4_modern/Repository), los DTO (v11_patterns/part4_modern/DTO) y la
    inversión de dependencias (v10_design/part2_solid/
    DependencyInversion).

    Importancia: MUY ALTA. Es la arquitectura de la mayoría de las
    aplicaciones empresariales, y el punto de partida para entender las
    demás (hexagonal, clean).

    Índice:
        CAPA 1)  Qué es la arquitectura de una aplicación
        CAPA 2)  Las capas clásicas y la regla de dependencias
        CAPA 3)  El problema de las capas clásicas: el negocio depende de la base de datos
        CAPA 4)  La corrección: el dominio define, la infraestructura implementa
        CAPA 5)  Una aplicación completa, capa por capa
        CAPA 6)  Pruebas de arquitectura: que las reglas no se rompan
        CAPA 7)  Paquetes por capa o por funcionalidad
        CAPA 8)  Lo que no es una capa: logs, seguridad, transacciones
        CAPA 9)  Cuándo bastan las capas y cuándo no
        CAPA 10) Errores comunes
        RESUMEN FINAL
*/
public class LayeredArchitecture {

    public static void main(String[] args) {
        check(applicationExample(), "CAPA 5");
        check(architectureTestExample(), "CAPA 6");
        System.out.println("LayeredArchitecture: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) QUÉ ES LA ARQUITECTURA DE UNA APLICACIÓN
        =================================================================

        Diseño (v10_design/) y patrones (v11_patterns/) hablan de clases y
        objetos. La ARQUITECTURA habla de la aplicación COMPLETA: en qué
        grandes partes se divide, qué hace cada una, y quién puede depender
        de quién.

        Una definición útil: la arquitectura son las decisiones que son
        CARAS DE CAMBIAR después. Cambiar el nombre de un método cuesta un
        minuto; cambiar que el negocio dependa directamente de la base de
        datos, meses.

        Su objetivo práctico: que la aplicación se pueda CAMBIAR y PROBAR
        durante años. Que un cambio en cómo se guarda un pedido no obligue
        a tocar las reglas de los descuentos, y que las reglas se puedan
        probar sin levantar una base de datos.
    */

    /*
        =================================================================
        CAPA 2) LAS CAPAS CLÁSICAS Y LA REGLA DE DEPENDENCIAS
        =================================================================

        La arquitectura en capas divide la aplicación por RESPONSABILIDAD
        TÉCNICA, de afuera hacia adentro:

            PRESENTACIÓN    recibe y responde: controllers HTTP, pantallas,
                            lectores de archivos. Traduce DTOs (DTO).
                 |
                 v
            APLICACIÓN      los casos de uso: coordina, abre la
                            transacción (ServiceLayer). Sin reglas de
                            negocio propias.
                 |
                 v
            DOMINIO         las reglas del negocio: Order, Product,
                            descuentos, límites. El corazón.
                 |
                 v
            INFRAESTRUCTURA lo técnico: base de datos, correo, colas, APIs
                            de otros sistemas.

        LA REGLA: cada capa depende SOLO de las de abajo. Nunca hacia
        arriba: el dominio no sabe que hay controllers; la aplicación no
        sabe si la llamó HTTP o un CSV.

        Variantes:
        - ESTRICTA: cada capa solo conoce a la inmediata de abajo;
        - RELAJADA: puede saltarse capas (la presentación lee del dominio).
          Más práctica, con el riesgo de que las reglas terminen en el
          controller.

        En una aplicación real son PAQUETES (o módulos de Maven, o módulos
        de Java: v12_jvm/part2_classes/Modules):

            com.tienda.web          presentación
            com.tienda.application  aplicación
            com.tienda.domain       dominio
            com.tienda.persistence  infraestructura
    */
    enum LayerKind { PRESENTATION, APPLICATION, DOMAIN, INFRASTRUCTURE }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @interface Layer {
        LayerKind value();
    }

    /*
        =================================================================
        CAPA 3) EL PROBLEMA DE LAS CAPAS CLÁSICAS: EL NEGOCIO DEPENDE DE
                LA BASE DE DATOS
        =================================================================

        Seguida al pie de la letra, la regla "cada capa depende de la de
        abajo" pone la INFRAESTRUCTURA en el fondo, y el DOMINIO encima de
        ella:

            dominio  -->  infraestructura (la clase que habla con la base
                          de datos)

        Es decir: las reglas del negocio, lo más valioso y lo que más
        cambia por razones de negocio, DEPENDEN de lo más técnico. Para
        probar un descuento hay que tener la base de datos; cambiar de base
        de datos obliga a tocar el dominio; y las clases del dominio se
        llenan de detalles de tablas y columnas.

        Es justo el problema de v10_design/part2_solid/DependencyInversion,
        CAPA 3, a nivel de toda la aplicación.
    */

    /*
        =================================================================
        CAPA 4) LA CORRECCIÓN: EL DOMINIO DEFINE, LA INFRAESTRUCTURA
                IMPLEMENTA
        =================================================================

        La versión moderna invierte esa única flecha: el DOMINIO declara lo
        que necesita como una interface en SUS términos (OrderRepository,
        PaymentGateway), y la INFRAESTRUCTURA la implementa:

            PRESENTACIÓN --> APLICACIÓN --> DOMINIO <-- INFRAESTRUCTURA
                                            (interfaces)  (implementaciones)

        Ahora el dominio no depende de NADA: es Java puro. Se prueba en
        milisegundos con implementaciones en memoria (Repository, CAPAS 3 y
        5), y la base de datos se puede cambiar sin tocarlo.

        ¿Quién conecta la interface con su implementación? El "composition
        root" al arrancar (v10_design/part3_responsibilities/
        DependencyInjection, CAPA 4), o Spring.

        Este cambio de una flecha es la idea de fondo de las arquitecturas
        hexagonal y clean (part2_boundaries/), que la llevan más lejos.
    */

    // ---- DOMINIO: las reglas, sin depender de nadie ----------------------

    @Layer(LayerKind.DOMAIN)
    record Order(long id, String customerEmail, long totalCents) {
        static final long MINIMUM_CENTS = 10_000;

        Order {
            if (totalCents < MINIMUM_CENTS) {               // regla del negocio: aquí, no en el controller
                throw new IllegalArgumentException("El pedido mínimo es de " + MINIMUM_CENTS / 100 + " pesos");
            }
        }

        Order withId(long newId) {
            return new Order(newId, customerEmail, totalCents);
        }
    }

    @Layer(LayerKind.DOMAIN)
    interface OrderRepository {                             // la define el dominio, en sus términos
        Order save(Order order);

        Optional<Order> findById(long id);
    }

    // ---- INFRAESTRUCTURA: implementa lo que el dominio pide ---------------

    @Layer(LayerKind.INFRASTRUCTURE)
    static final class InMemoryOrderRepository implements OrderRepository {
        private final Map<Long, Order> rows = new HashMap<>();
        private long nextId = 1;

        @Override
        public Order save(Order order) {
            Order stored = order.id() == 0 ? order.withId(nextId++) : order;
            rows.put(stored.id(), stored);
            return stored;
        }

        @Override
        public Optional<Order> findById(long id) {
            return Optional.ofNullable(rows.get(id));
        }
    }

    // ---- APLICACIÓN: el caso de uso ---------------------------------------

    @Layer(LayerKind.APPLICATION)
    record PlaceOrderCommand(String customerEmail, long totalCents) {
    }

    @Layer(LayerKind.APPLICATION)
    static final class PlaceOrderService {
        private final OrderRepository orders;               // depende de la interface del dominio

        PlaceOrderService(OrderRepository orders) {
            this.orders = orders;
        }

        long placeOrder(PlaceOrderCommand command) {
            Order order = new Order(0, command.customerEmail(), command.totalCents());   // el dominio valida
            return orders.save(order).id();
        }
    }

    // ---- PRESENTACIÓN: traduce el formato de afuera -----------------------

    @Layer(LayerKind.PRESENTATION)
    record HttpResponse(int status, String body) {
    }

    @Layer(LayerKind.PRESENTATION)
    static final class OrderController {
        private final PlaceOrderService service;

        OrderController(PlaceOrderService service) {
            this.service = service;
        }

        HttpResponse post(Map<String, String> json) {
            try {
                long id = service.placeOrder(new PlaceOrderCommand(json.get("email"),
                        Long.parseLong(json.get("totalCents"))));
                return new HttpResponse(201, "{\"id\":" + id + "}");
            } catch (IllegalArgumentException e) {          // el error del negocio, traducido a HTTP
                return new HttpResponse(400, e.getMessage());
            }
        }
    }

    /*
        =================================================================
        CAPA 5) UNA APLICACIÓN COMPLETA, CAPA POR CAPA
        =================================================================

        Las clases de arriba forman una aplicación mínima, cada una en su
        capa:

            OrderController        presentación   JSON -> comando; errores -> 400
            PlaceOrderService      aplicación     crea el pedido y lo guarda
            Order                  dominio        el mínimo de 100 pesos
            OrderRepository        dominio        "necesito guardar pedidos"
            InMemoryOrderRepository infraestructura  cómo se guardan

        El "composition root" (aquí, el método de abajo) las conecta. Un
        pedido válido llega hasta el repositorio; uno por debajo del mínimo
        lo rechaza el DOMINIO, y la presentación lo convierte en un 400.
        Cambiar la memoria por una base de datos de verdad es cambiar UNA
        línea en el composition root.
    */
    static boolean applicationExample() {
        OrderRepository repository = new InMemoryOrderRepository();          // composition root:
        OrderController controller = new OrderController(new PlaceOrderService(repository));   // se arma una vez

        HttpResponse accepted = controller.post(Map.of("email", "ana@correo.com", "totalCents", "25000"));
        HttpResponse rejected = controller.post(Map.of("email", "ana@correo.com", "totalCents", "5000"));
        return accepted.status() == 201 && accepted.body().equals("{\"id\":1}")
                && repository.findById(1).isPresent()
                && rejected.status() == 400 && rejected.body().equals("El pedido mínimo es de 100 pesos");
    }

    /*
        =================================================================
        CAPA 6) PRUEBAS DE ARQUITECTURA: QUE LAS REGLAS NO SE ROMPAN
        =================================================================

        Las reglas de dependencia se rompen sin que nadie lo note: un día
        alguien necesita algo "rápido" y desde el dominio usa una clase de
        infraestructura. Compila, funciona, y la arquitectura se degrada
        poco a poco.

        La solución profesional: una PRUEBA que revisa las dependencias,
        igual que una prueba revisa un cálculo. Si alguien rompe la regla,
        la prueba falla en el momento. En proyectos reales se usa ArchUnit
        (una librería de pruebas que lee las clases compiladas y verifica
        reglas como "nada en ..domain.. depende de ..persistence..").

        Aquí, una versión pequeña con reflexión (v12_jvm/part2_classes/
        ReflectionAndAnnotations): para cada clase con @Layer, revisa los
        tipos de sus campos y de los parámetros de sus constructores, y
        compara la capa de cada uno con lo permitido:

            PRESENTATION    -> APPLICATION
            APPLICATION     -> DOMAIN
            INFRASTRUCTURE  -> DOMAIN
            DOMAIN          -> (nadie)

        Las clases de la CAPA 5 pasan. DiscountWithDatabase, un "atajo" del
        dominio que guarda una referencia a una tabla de la infraestructura,
        se detecta.
    */
    @Layer(LayerKind.INFRASTRUCTURE)
    static final class DiscountTable {
        int percentFor(String customerEmail) {
            return customerEmail.endsWith("@empresa.com") ? 10 : 0;
        }
    }

    @Layer(LayerKind.DOMAIN)
    static final class DiscountWithDatabase {               // el atajo que rompe la regla
        private final DiscountTable table;

        DiscountWithDatabase(DiscountTable table) {
            this.table = table;
        }

        long apply(long cents, String customerEmail) {
            return cents - cents * table.percentFor(customerEmail) / 100;
        }
    }

    static final Map<LayerKind, Set<LayerKind>> ALLOWED = Map.of(
            LayerKind.PRESENTATION, EnumSet.of(LayerKind.APPLICATION),
            LayerKind.APPLICATION, EnumSet.of(LayerKind.DOMAIN),
            LayerKind.INFRASTRUCTURE, EnumSet.of(LayerKind.DOMAIN),
            LayerKind.DOMAIN, EnumSet.noneOf(LayerKind.class));

    static List<String> violations(List<Class<?>> classes) {
        List<String> found = new ArrayList<>();
        for (Class<?> type : classes) {
            Layer own = type.getAnnotation(Layer.class);
            if (own == null) {
                continue;
            }
            List<Class<?>> dependencies = new ArrayList<>();
            for (Field field : type.getDeclaredFields()) {
                dependencies.add(field.getType());
            }
            for (Constructor<?> constructor : type.getDeclaredConstructors()) {
                dependencies.addAll(List.of(constructor.getParameterTypes()));
            }
            for (Class<?> dependency : dependencies) {
                Layer target = dependency.getAnnotation(Layer.class);
                if (target != null && target.value() != own.value()
                        && !ALLOWED.get(own.value()).contains(target.value())) {
                    String violation = type.getSimpleName() + " (" + own.value() + ") depende de "
                            + dependency.getSimpleName() + " (" + target.value() + ")";
                    if (!found.contains(violation)) {
                        found.add(violation);
                    }
                }
            }
        }
        return found;
    }

    static boolean architectureTestExample() {
        List<Class<?>> application = List.of(Order.class, OrderRepository.class, InMemoryOrderRepository.class,
                PlaceOrderCommand.class, PlaceOrderService.class, HttpResponse.class, OrderController.class);
        List<Class<?>> withShortcut = new ArrayList<>(application);
        withShortcut.add(DiscountTable.class);
        withShortcut.add(DiscountWithDatabase.class);

        return violations(application).isEmpty()
                && violations(withShortcut).equals(List.of(
                        "DiscountWithDatabase (DOMAIN) depende de DiscountTable (INFRASTRUCTURE)"));
    }

    /*
        =================================================================
        CAPA 7) PAQUETES POR CAPA O POR FUNCIONALIDAD
        =================================================================

        Dos formas de organizar las carpetas:

            POR CAPA                         POR FUNCIONALIDAD
            controller/                      orders/
                OrderController                  OrderController
                CustomerController               PlaceOrderService
            service/                             Order
                PlaceOrderService                OrderRepository
                RegisterCustomerService          JdbcOrderRepository
            repository/                      customers/
                ...                              ...

        POR CAPA es lo que muestran casi todos los tutoriales. El problema:
        un cambio en "pedidos" toca cuatro carpetas, y todo tiene que ser
        public para cruzar de paquete, así que cualquier clase puede usar
        cualquier otra.

        POR FUNCIONALIDAD junta lo que cambia junto (SingleResponsibility,
        CAPA 9). Y permite usar package-private (v2_oop/part1_basics/
        AccessModifiers): JdbcOrderRepository puede ser invisible fuera de
        orders/, y solo OrderController y el servicio son public. Es la
        forma de que "pedidos" no dependa de las tripas de "clientes".

        Lo usual en proyectos que crecen: por funcionalidad afuera, y por
        capa adentro de cada funcionalidad (orders/web, orders/domain,
        orders/persistence). Ese es el primer paso hacia un "monolito
        modular" (part3_systems/MonolithVsMicroservices).
    */

    /*
        =================================================================
        CAPA 8) LO QUE NO ES UNA CAPA: LOGS, SEGURIDAD, TRANSACCIONES
        =================================================================

        Algunas cosas no pertenecen a una capa porque se necesitan en
        TODAS: registrar en el log, revisar permisos, medir tiempos, abrir
        transacciones. Se llaman preocupaciones TRANSVERSALES
        (cross-cutting concerns).

        Ponerlas a mano en cada método (un log al entrar, un if de permisos)
        llena el código de ruido y se olvida en algún lado. Se aplican desde
        AFUERA, sin tocar las clases:
        - un Decorator o un Proxy que envuelve al servicio (v11_patterns/
          part2_structural/Decorator, Proxy);
        - en Spring, anotaciones como @Transactional o @PreAuthorize, que
          un proxy generado aplica alrededor del método (Proxy, CAPA 6);
        - filtros en la entrada HTTP para lo que aplica a todas las
          peticiones (autenticación, un id por petición para los logs).
    */

    /*
        =================================================================
        CAPA 9) CUÁNDO BASTAN LAS CAPAS Y CUÁNDO NO
        =================================================================

        Las capas (con la corrección de la CAPA 4) son suficientes para la
        mayoría de las aplicaciones de negocio: formularios, consultas,
        reglas moderadas, una base de datos.

        Señales de que hace falta más estructura:
        - el dominio tiene reglas complicadas que cambian seguido, y se
          pierden entre servicios: DDD (part1_layers/DDDBasics);
        - la aplicación habla con muchos sistemas externos (varias APIs,
          colas, varias bases de datos) y quieres poder cambiarlos o
          probarlos sin ellos: hexagonal (part2_boundaries/Hexagonal);
        - las lecturas y las escrituras tienen necesidades muy distintas:
          CQRS (part3_systems/CommandsQueriesAndConsistency).

        Y la señal contraria: si la aplicación es casi solo "guardar y
        mostrar" (un CRUD), varias capas que solo pasan los datos de una a
        otra sin hacer nada son ceremonia. Está bien que el servicio sea
        delgado (ServiceLayer, CAPA 9).
    */

    /*
        =================================================================
        CAPA 10) ERRORES COMUNES
        =================================================================

        1) El dominio dependiendo de la infraestructura (la versión
           clásica sin invertir) (CAPAS 3 y 4).

        2) Reglas de negocio en el controller o en el servicio, y un
           dominio de puros datos (CAPA 5; ServiceLayer, CAPA 3).

        3) Objetos del dominio o de la base de datos devueltos tal cual por
           la API (DTO, CAPA 1).

        4) Reglas de dependencia que solo existen en un documento: sin
           prueba de arquitectura, se rompen (CAPA 6).

        5) Todo public y organizado solo por capa: nada impide que
           cualquier clase use cualquier otra (CAPA 7).

        6) Logs, permisos y transacciones repetidos a mano en cada método
           (CAPA 8).

        7) Capas que solo reenvían llamadas sin agregar nada, por seguir la
           receta (CAPA 9).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Arquitectura: las decisiones caras de cambiar; su objetivo es que
          la aplicación se pueda cambiar y probar por años.
        - Capas: presentación -> aplicación -> dominio, e infraestructura;
          cada una depende solo de las de abajo.
        - Corrección clave: el dominio define interfaces y la
          infraestructura las implementa; el dominio no depende de nada.
        - Las reglas se verifican con pruebas de arquitectura (ArchUnit).
        - Paquetes por funcionalidad (con capas adentro) y package-private.
        - Lo transversal, con decoradores, proxies o filtros.
        - Suficiente para la mayoría; DDD, hexagonal o CQRS cuando las
          señales lo piden.
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
