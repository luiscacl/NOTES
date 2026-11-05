package v10_design.part3_responsibilities;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/*
    =====================================================================
    INYECCIÓN DE DEPENDENCIAS — DE CERO A NIVEL TÉCNICO
    =====================================================================

    Ruta de design/: guía 7 de 10  (part3_responsibilities)
    Anterior:  part2_solid/DependencyInversion
    Siguiente: part3_responsibilities/GRASP

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.

    Qué se da por sabido: el principio de inversión de dependencias
    (part2_solid/DependencyInversion), que dice HACIA DÓNDE deben apuntar
    las dependencias. Esta guía trata de CÓMO se entregan.

    Índice:
        CAPA 1)  Qué es la inyección de dependencias
        CAPA 2)  Las tres formas: constructor, setter y campo
        CAPA 3)  Por qué el constructor es la forma recomendada
        CAPA 4)  El composition root: un solo lugar donde se arma todo
        CAPA 5)  Cuántas instancias: compartidas o nuevas
        CAPA 6)  Objetos que se crean después: inyectar una fábrica
        CAPA 7)  Qué hace un contenedor de inyección (como Spring)
        CAPA 8)  Un contenedor de juguete, para ver la magia por dentro
        CAPA 9)  Dependencias circulares
        CAPA 10) Antipatrones: service locator y singletons globales
        CAPA 11) Demasiadas dependencias: una señal de diseño
        CAPA 12) A mano o con contenedor
        CAPA 13) Errores comunes
        RESUMEN FINAL
*/
public class DependencyInjection {

    public static void main(String[] args) throws Exception {
        check(whatIsItExample(), "CAPA 1");
        check(setterInjectionExample(), "CAPA 2");
        check(constructorInjectionExample(), "CAPA 3");
        check(compositionRootExample(), "CAPA 4");
        check(lifetimesExample(), "CAPA 5");
        check(factoryInjectionExample(), "CAPA 6");
        check(miniContainerExample(), "CAPA 8");
        check(circularDependencyExample(), "CAPA 9");
        check(serviceLocatorExample(), "CAPA 10");
        System.out.println("DependencyInjection: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) QUÉ ES LA INYECCIÓN DE DEPENDENCIAS
        =================================================================

        Una DEPENDENCIA de un objeto es otro objeto que necesita para hacer
        su trabajo: un servicio de pedidos necesita un repositorio y un
        notificador.

        Hay dos formas de conseguirla:
        1) CREARLA él mismo, por dentro:
               this.notifier = new EmailNotifier("smtp.tienda.com");
        2) RECIBIRLA desde afuera, ya creada:
               OrderService(Notifier notifier) { this.notifier = notifier; }

        INYECCIÓN DE DEPENDENCIAS (dependency injection, DI) es la segunda:
        el objeto no busca ni crea lo que necesita; alguien más se lo da.
        Eso es todo. Un programador lo describió como "un término de 25
        dólares para un concepto de 5 centavos".

        El valor está en sus consecuencias (part2_solid/DependencyInversion,
        CAPAS 7 y 8): si el objeto recibe sus dependencias, quien lo arma
        DECIDE cuáles darle: la de verdad en producción, una en memoria en
        las pruebas, una decorada con reintentos, otra versión mañana. El
        objeto no cambia.
    */
    record Order(String id, String customer, BigDecimal total) { }

    interface Notifier {
        void notify(String customer, String message);
    }

    interface OrderRepository {
        void save(Order order);

        int count();
    }

    static final class RecordingNotifier implements Notifier {
        final List<String> sent = new ArrayList<>();

        @Override
        public void notify(String customer, String message) {
            sent.add(customer + ": " + message);
        }
    }

    static final class InMemoryOrderRepository implements OrderRepository {
        private final List<Order> orders = new ArrayList<>();

        @Override
        public void save(Order order) {
            orders.add(order);
        }

        @Override
        public int count() {
            return orders.size();
        }
    }

    static boolean whatIsItExample() {
        RecordingNotifier chosenFromOutside = new RecordingNotifier();
        OrderService service = new OrderService(new InMemoryOrderRepository(), chosenFromOutside);   // se lo DAMOS
        service.place(new Order("A-1", "ana", BigDecimal.TEN));
        return chosenFromOutside.sent.equals(List.of("ana: pedido A-1 confirmado"));
    }

    /*
        =================================================================
        CAPA 2) LAS TRES FORMAS: CONSTRUCTOR, SETTER Y CAMPO
        =================================================================

        1) POR CONSTRUCTOR: las dependencias son parámetros del constructor
           y se guardan en campos final.
               OrderService(OrderRepository repository, Notifier notifier)

        2) POR SETTER: el objeto se crea vacío y después se le asignan:
               service.setNotifier(notifier);

        3) POR CAMPO: un framework escribe directamente en un campo privado
           usando reflexión (la capacidad de Java de inspeccionar y
           modificar clases al ejecutar; CAPA 8), guiado por una anotación:
               @Autowired private Notifier notifier;     // así se ve en Spring
           Es la más corta de escribir, y la que más problemas da: el campo
           no puede ser final, la dependencia no se ve en ninguna firma, y
           el objeto SOLO se puede crear con el framework (en una prueba,
           new OrderService() te deja el campo en null).

        El problema de las formas 2 y 3 se ve en el ejemplo: el objeto
        puede EXISTIR SIN sus dependencias. Si alguien olvida llamar al
        setter, el error aparece mucho después, como NullPointerException,
        en el primer uso.
    */
    static final class SetterInjectedService {
        private Notifier notifier;                          // no puede ser final

        void setNotifier(Notifier notifier) {
            this.notifier = notifier;
        }

        void place(Order order) {
            notifier.notify(order.customer(), "pedido " + order.id() + " confirmado");
        }
    }

    static boolean setterInjectionExample() {
        SetterInjectedService service = new SetterInjectedService();   // nació incompleto... y nadie avisó
        try {
            service.place(new Order("A-1", "ana", BigDecimal.TEN));    // el olvido aparece aquí, lejos del error
            return false;
        } catch (NullPointerException e) {
            return true;
        }
    }

    /*
        =================================================================
        CAPA 3) POR QUÉ EL CONSTRUCTOR ES LA FORMA RECOMENDADA
        =================================================================

        La inyección por constructor es la recomendación de prácticamente
        toda la industria (incluida la documentación de Spring):

        1) EL OBJETO NACE COMPLETO: no se puede crear sin sus dependencias.
           Con Objects.requireNonNull, ni siquiera con null: falla AL
           CREARLO (fail fast; v2_oop/part2_pillars/Encapsulation, CAPA 4).

        2) CAMPOS FINAL: las dependencias no cambian después; el objeto es
           más fácil de razonar y seguro de compartir entre hilos
           (v13_concurrency/).

        3) LAS DEPENDENCIAS SE VEN: la firma del constructor es la lista
           honesta de lo que la clase necesita. Si la lista crece a 8, la
           clase te está avisando algo (CAPA 11).

        4) NO NECESITA FRAMEWORK: en una prueba es solo new
           OrderService(fakeRepo, fakeNotifier).

        El setter sirve para dependencias OPCIONALES de verdad (con un
        valor por defecto razonable), que son raras.
    */
    static final class OrderService {
        private final OrderRepository repository;
        private final Notifier notifier;

        OrderService(OrderRepository repository, Notifier notifier) {
            this.repository = Objects.requireNonNull(repository, "repository");
            this.notifier = Objects.requireNonNull(notifier, "notifier");
        }

        void place(Order order) {
            repository.save(order);
            notifier.notify(order.customer(), "pedido " + order.id() + " confirmado");
        }
    }

    static boolean constructorInjectionExample() {
        try {
            new OrderService(new InMemoryOrderRepository(), null);    // falla AL NACER, con nombre
            return false;
        } catch (NullPointerException e) {
            return e.getMessage().equals("notifier");
        }
    }

    /*
        =================================================================
        CAPA 4) EL COMPOSITION ROOT: UN SOLO LUGAR DONDE SE ARMA TODO
        =================================================================

        Si ningún objeto crea sus dependencias, ¿quién las crea? UN SOLO
        lugar, lo más cerca posible del arranque del programa (el main, o
        lo que el framework use como arranque): el COMPOSITION ROOT ("raíz
        de composición").

        Ahí, y solo ahí:
        - se leen la configuración y los secretos;
        - se crean los objetos de bajo nivel (repositorios, clientes HTTP,
          notificadores);
        - se crean los servicios pasándoles lo anterior;
        - se entrega el objeto "de entrada" listo para usarse.

        El resto del código NUNCA hace new de un servicio: los recibe.

        Ventajas:
        - Todo el "cableado" de la aplicación está a la vista en un lugar.
        - Cambiar una implementación (correo por SMS) es cambiar UNA línea.
        - Las pruebas tienen su propio composition root, con dobles.

        El ejemplo arma la misma aplicación dos veces: con la configuración
        de "producción" y con la de pruebas. OrderService no cambia.
    */
    record AppConfig(String smtpHost, boolean testMode) { }

    record Application(OrderService orders, OrderRepository repository, RecordingNotifier notifierProbe) { }

    static final class EmailNotifier implements Notifier {   // "real": aquí solo recuerda a quién avisaría
        private final String host;
        final List<String> queued = new ArrayList<>();

        EmailNotifier(String host) {
            this.host = host;
        }

        @Override
        public void notify(String customer, String message) {
            queued.add(host + " -> " + customer);
        }
    }

    static Application wireApplication(AppConfig config) {   // EL composition root
        OrderRepository repository = new InMemoryOrderRepository();     // en producción: el de la base de datos
        RecordingNotifier probe = new RecordingNotifier();
        Notifier notifier = config.testMode() ? probe : new EmailNotifier(config.smtpHost());
        OrderService orders = new OrderService(repository, notifier);
        return new Application(orders, repository, probe);
    }

    static boolean compositionRootExample() {
        Application test = wireApplication(new AppConfig("smtp.tienda.com", true));
        test.orders().place(new Order("T-1", "ana", BigDecimal.ONE));

        Application production = wireApplication(new AppConfig("smtp.tienda.com", false));
        production.orders().place(new Order("P-1", "luis", BigDecimal.ONE));

        return test.notifierProbe().sent.size() == 1
                && production.notifierProbe().sent.isEmpty()        // en "producción" usó el EmailNotifier
                && production.repository().count() == 1;
    }

    /*
        =================================================================
        CAPA 5) CUÁNTAS INSTANCIAS: COMPARTIDAS O NUEVAS
        =================================================================

        Quien arma los objetos también decide su CICLO DE VIDA (en los
        frameworks se le llama "scope"):

        - UNA INSTANCIA COMPARTIDA para toda la aplicación (en Spring, el
          valor por defecto, llamado "singleton"): el repositorio, el
          HttpClient (v8_apis/part2_io_and_network/HttpClientUsage,
          CAPA 7), los servicios. Para eso deben ser SIN ESTADO propio que
          cambie (o seguros con hilos), porque muchas peticiones los usan a
          la vez.
        - UNA NUEVA cada vez que se pide ("prototype"): objetos con estado
          de una sola operación, como un carrito en construcción o un
          generador de reporte.
        - UNA POR PETICIÓN web o por sesión de usuario (frameworks web).

        Ojo: "singleton" aquí significa "una instancia, porque el
        composition root solo creó una", NO el patrón Singleton con un
        método static getInstance() (CAPA 10). Es la misma idea con una
        diferencia enorme: quien la usa la recibe, no la va a buscar.

        El ejemplo: dos servicios que reciben EL MISMO repositorio, así que
        ven los mismos datos.
    */
    static final class SalesReport {
        private final OrderRepository repository;

        SalesReport(OrderRepository repository) {
            this.repository = repository;
        }

        String summary() {
            return repository.count() + " pedidos";
        }
    }

    static boolean lifetimesExample() {
        OrderRepository shared = new InMemoryOrderRepository();      // UNA instancia...
        OrderService orders = new OrderService(shared, new RecordingNotifier());
        SalesReport report = new SalesReport(shared);                // ...para los dos

        orders.place(new Order("A-1", "ana", BigDecimal.TEN));
        orders.place(new Order("A-2", "luis", BigDecimal.TEN));
        return report.summary().equals("2 pedidos");                 // el reporte ve lo que guardó el servicio
    }

    /*
        =================================================================
        CAPA 6) OBJETOS QUE SE CREAN DESPUÉS: INYECTAR UNA FÁBRICA
        =================================================================

        A veces un objeto necesita CREAR otros objetos durante su trabajo,
        con datos que solo existen en ese momento (el id del pedido, la
        fecha actual). No se pueden inyectar al arrancar porque todavía no
        existen.

        La solución no es volver a hacer new de la dependencia adentro,
        sino inyectar una FÁBRICA: algo que sabe crearlas. Para casos
        simples, un Supplier<T> (v7_functional/part1_lambdas/
        FunctionalInterfaces) o una interface propia con un método create.

        El ejemplo: un generador de folios de factura necesita la hora
        actual en cada llamada. Recibe un Supplier<Instant> (o un Clock,
        part2_solid/DependencyInversion, CAPA 9): en producción da la hora
        real, en la prueba una fija.
    */
    static final class InvoiceNumberGenerator {
        private final Supplier<Instant> now;
        private int sequence;

        InvoiceNumberGenerator(Supplier<Instant> now) {
            this.now = now;
        }

        String next() {
            sequence++;
            return "F-" + now.get().getEpochSecond() + "-" + sequence;   // la hora se pide en cada uso
        }
    }

    static boolean factoryInjectionExample() {
        Clock fixed = Clock.fixed(Instant.parse("2024-03-15T10:00:00Z"), ZoneOffset.UTC);
        InvoiceNumberGenerator generator = new InvoiceNumberGenerator(fixed::instant);
        return generator.next().equals("F-1710496800-1") && generator.next().equals("F-1710496800-2");
    }

    /*
        =================================================================
        CAPA 7) QUÉ HACE UN CONTENEDOR DE INYECCIÓN (COMO SPRING)
        =================================================================

        En una aplicación con cientos de clases, escribir el composition
        root a mano se vuelve largo. Un CONTENEDOR de inyección (Spring es
        el estándar en Java; v15_spring/) lo automatiza:

        1) DESCUBRE las clases que debe administrar (en Spring, las marcadas
           con anotaciones como @Service o @Repository).
        2) LEE sus constructores para saber qué necesita cada una.
        3) Para cada parámetro, busca un objeto del TIPO pedido; si la
           interface tiene una sola implementación, la elige sola.
        4) Crea todo EN EL ORDEN correcto (primero lo que no depende de
           nada) y guarda una instancia de cada uno (CAPA 5).
        5) Si falta algo, o hay dos candidatos y no sabe cuál, o hay un
           ciclo (CAPA 9), FALLA AL ARRANCAR, con un mensaje que dice qué
           pasa. Es mucho mejor que fallar a la mitad de una venta.

        Es exactamente el composition root de la CAPA 4, escrito por una
        máquina. Por eso, si entiendes la inyección a mano, entiendes
        Spring. La CAPA 8 construye uno de juguete para que lo veas.
    */

    /*
        =================================================================
        CAPA 8) UN CONTENEDOR DE JUGUETE, PARA VER LA MAGIA POR DENTRO
        =================================================================

        Este contenedor de 50 líneas hace, en pequeño, lo mismo que Spring
        al arrancar:
        - bind(Interface, Implementación): dice qué clase usar para cada
          interface (Spring lo deduce solo cuando hay una sola).
        - get(Tipo): si ya creó uno, lo devuelve (una instancia compartida);
          si no, toma el constructor de la clase, pide RECURSIVAMENTE cada
          parámetro, y lo crea.
        - Detecta ciclos llevando la cuenta de lo que está creando en ese
          momento (CAPA 9).

        Para "leer" constructores y crear objetos de clases que no conoce
        de antemano usa REFLEXIÓN (java.lang.reflect): Java permite, al
        ejecutar, preguntarle a una clase qué constructores tiene y
        llamarlos. Es lo que hacen todos los frameworks por dentro. En tu
        código normal casi nunca la usarás: es más lenta que una llamada
        normal, se salta la verificación del compilador y rompe el
        encapsulamiento (v12_jvm/part2_classes/ReflectionAndAnnotations).

        No es para producción; es para que la palabra "contenedor" deje de
        sonar a magia.
    */
    static final class MiniContainer {
        private final Map<Class<?>, Class<?>> bindings = new HashMap<>();
        private final Map<Class<?>, Object> instances = new HashMap<>();
        private final Deque<Class<?>> creating = new ArrayDeque<>();      // para detectar ciclos

        <T> MiniContainer bind(Class<T> type, Class<? extends T> implementation) {
            bindings.put(type, implementation);
            return this;
        }

        <T> T get(Class<T> type) {
            Object existing = instances.get(type);
            if (existing != null) {
                return type.cast(existing);                 // una sola instancia por tipo
            }
            if (creating.contains(type)) {
                throw new IllegalStateException("Dependencia circular: " + cyclePath(type));
            }
            creating.push(type);
            try {
                T created = type.cast(create(bindings.getOrDefault(type, type)));
                instances.put(type, created);
                return created;
            } finally {
                creating.pop();
            }
        }

        private Object create(Class<?> implementation) {
            if (implementation.isInterface()) {
                throw new IllegalStateException("No hay implementación registrada para " + implementation.getSimpleName());
            }
            Constructor<?> constructor = implementation.getDeclaredConstructors()[0];   // las clases de ejemplo tienen uno
            Object[] arguments = new Object[constructor.getParameterCount()];
            Class<?>[] parameterTypes = constructor.getParameterTypes();
            for (int i = 0; i < arguments.length; i++) {
                arguments[i] = get(parameterTypes[i]);      // resuelve cada dependencia, recursivamente
            }
            try {
                constructor.setAccessible(true);            // reflexión: saltarse el acceso de paquete
                return constructor.newInstance(arguments);
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                throw new IllegalStateException("No se pudo crear " + implementation.getSimpleName(), e);
            }
        }

        private String cyclePath(Class<?> repeated) {
            List<Class<?>> path = new ArrayList<>(creating);
            Collections.reverse(path);            // en orden de creación
            path.add(repeated);
            return path.stream().map(Class::getSimpleName).collect(Collectors.joining(" -> "));
        }
    }

    static boolean miniContainerExample() {
        MiniContainer container = new MiniContainer()
                .bind(OrderRepository.class, InMemoryOrderRepository.class)
                .bind(Notifier.class, RecordingNotifier.class);

        OrderService orders = container.get(OrderService.class);      // nadie escribió new OrderService(...)
        SalesReport report = container.get(SalesReport.class);
        orders.place(new Order("A-1", "ana", BigDecimal.TEN));

        boolean missingBindingFails;
        try {
            new MiniContainer().get(OrderService.class);
            missingBindingFails = false;
        } catch (IllegalStateException e) {
            missingBindingFails = e.getMessage().equals("No hay implementación registrada para OrderRepository");
        }

        return report.summary().equals("1 pedidos")                   // los dos recibieron el MISMO repositorio
                && container.get(Notifier.class) == container.get(Notifier.class)
                && ((RecordingNotifier) container.get(Notifier.class)).sent.size() == 1
                && missingBindingFails;
    }

    /*
        =================================================================
        CAPA 9) DEPENDENCIAS CIRCULARES
        =================================================================

        A necesita a B para crearse, y B necesita a A. Con inyección por
        constructor, eso es IMPOSIBLE de construir: para crear A hace falta
        un B ya creado, que a su vez necesita un A ya creado... El
        contenedor lo detecta y falla al arrancar (Spring siempre ha
        rechazado los ciclos por constructor, con
        BeanCurrentlyInCreationException).

        Eso es BUENO: una dependencia circular casi siempre indica un error
        de DISEÑO. Dos clases que se necesitan mutuamente están tan
        acopladas que en realidad son una, o les falta una tercera pieza.

        Cómo se resuelve (no con trucos para que "arranque"):
        1) Extraer lo que ambas comparten a una TERCERA clase de la que
           dependan las dos.
        2) Revisar si una de las dependencias en realidad debería ser un
           EVENTO: en vez de que Orders llame a Billing y Billing a Orders,
           Orders publica "pedido creado" y Billing reacciona (v11_patterns/,
           Observer).
        3) Revisar si una de las dos clases tiene demasiadas
           responsabilidades (part2_solid/SingleResponsibility).

        La inyección por setter o por campo "permitía" el ciclo (se crean
        vacías y luego se conectan), y por eso escondía el problema en vez
        de mostrarlo. Desde Spring Boot 2.6 eso también falla al arrancar
        por defecto; se puede reactivar con
        spring.main.allow-circular-references=true, pero eso solo vuelve a
        esconder el problema de diseño.
    */
    static final class Orders {
        Orders(Billing billing) {
            Objects.requireNonNull(billing);
        }
    }

    static final class Billing {
        Billing(Orders orders) {
            Objects.requireNonNull(orders);
        }
    }

    static boolean circularDependencyExample() {
        try {
            new MiniContainer().get(Orders.class);
            return false;
        } catch (IllegalStateException e) {
            return e.getMessage().equals("Dependencia circular: Orders -> Billing -> Orders");
        }
    }

    /*
        =================================================================
        CAPA 10) ANTIPATRONES: SERVICE LOCATOR Y SINGLETONS GLOBALES
        =================================================================

        Dos formas de conseguir dependencias que PARECEN resolver el
        problema pero lo esconden:

        1) SERVICE LOCATOR: un registro global al que cada clase le PIDE lo
           que necesita:
               this.notifier = ServiceLocator.get(Notifier.class);
           La clase ya no hace new, pero:
           - sus dependencias NO se ven en el constructor: hay que leer
             todo su código para saber qué necesita;
           - depende del registro global, y para probarla hay que
             configurar ese registro (y limpiarlo después de cada prueba);
           - si falta algo, falla al USARSE, no al arrancar.

        2) SINGLETON CON getInstance(): la clase guarda su única instancia
           en un campo static y todos la buscan con EmailSender.getInstance().
           Es estado global (BasicPrinciples, CAPA 3): cualquier clase lo
           usa sin declararlo, y en las pruebas no se puede reemplazar.
           (Lo correcto: UNA instancia creada en el composition root e
           inyectada, CAPA 5.)

        Ojo con lo mismo dentro de un framework: pedirle objetos al
        contenedor desde la lógica (inyectar "el contenedor") es un service
        locator con otro nombre.

        El ejemplo: con un service locator, la clase COMPILA y se crea sin
        problema, y falla hasta que se usa, porque nadie registró su
        dependencia escondida.
    */
    static final class ServiceLocator {                     // estado global: un mapa static
        private static final Map<Class<?>, Object> SERVICES = new HashMap<>();

        static <T> void register(Class<T> type, T service) {
            SERVICES.put(type, service);
        }

        static <T> T get(Class<T> type) {
            Object service = SERVICES.get(type);
            if (service == null) {
                throw new IllegalStateException("Nadie registró " + type.getSimpleName());
            }
            return type.cast(service);
        }

        static void clear() {
            SERVICES.clear();
        }
    }

    static final class LocatorOrderService {               // su constructor no dice qué necesita
        void place(Order order) {
            ServiceLocator.get(Notifier.class).notify(order.customer(), "confirmado");   // dependencia escondida
        }
    }

    static boolean serviceLocatorExample() {
        ServiceLocator.clear();
        LocatorOrderService service = new LocatorOrderService();     // se crea sin problemas...
        boolean failsOnlyWhenUsed;
        try {
            service.place(new Order("A-1", "ana", BigDecimal.ONE));  // ...y falla al usarse
            failsOnlyWhenUsed = false;
        } catch (IllegalStateException e) {
            failsOnlyWhenUsed = e.getMessage().equals("Nadie registró Notifier");
        }

        RecordingNotifier notifier = new RecordingNotifier();
        ServiceLocator.register(Notifier.class, notifier);           // la prueba tiene que tocar estado global
        service.place(new Order("A-2", "luis", BigDecimal.ONE));
        ServiceLocator.clear();                                      // y acordarse de limpiarlo

        return failsOnlyWhenUsed && notifier.sent.size() == 1;
    }

    /*
        =================================================================
        CAPA 11) DEMASIADAS DEPENDENCIAS: UNA SEÑAL DE DISEÑO
        =================================================================

        Con inyección por constructor, las dependencias quedan a la vista.
        Y eso tiene un efecto útil: un constructor con 7 u 8 parámetros
        INCOMODA. Esa incomodidad es la señal de que la clase tiene
        demasiadas responsabilidades (part2_solid/SingleResponsibility,
        CAPA 7).

        Lo que NO se debe hacer es "arreglar" la incomodidad escondiendo
        las dependencias: pasar a inyección por campo (CAPA 2), agruparlas
        en un objeto "Dependencies" con todo, o usar un service locator
        (CAPA 10). El problema sigue ahí, solo que ya no se ve.

        Lo que sí:
        - dividir la clase por responsabilidades;
        - o agrupar dependencias que siempre van juntas detrás de una
          abstracción con sentido propio (por ejemplo, "cobrar" usa el
          procesador de pagos, el antifraude y la facturación: eso es un
          PaymentFacade con una responsabilidad clara).

        Como referencia aproximada: más de 4 o 5 dependencias merece una
        revisión.
    */

    /*
        =================================================================
        CAPA 12) A MANO O CON CONTENEDOR
        =================================================================

        | Inyección a mano (composition root propio) | Con contenedor (Spring)                  |
        |--------------------------------------------|------------------------------------------|
        | Cero magia: todo se ve en código           | Menos código de "cableado"               |
        | Errores al COMPILAR (falta un parámetro)   | Errores al ARRANCAR (falta un bean)      |
        | Ideal para librerías, herramientas,        | Estándar en aplicaciones empresariales:  |
        | programas pequeños y medianos              | además da transacciones, web, seguridad, |
        |                                            | configuración...                         |
        | Hay que mantener el orden de creación      | El contenedor resuelve el orden          |

        La regla importante: escribas como escribas el composition root,
        tus CLASES deben ser iguales en los dos casos: dependencias por
        constructor, sin anotaciones de inyección en los campos, sin pedir
        nada a un registro global. Así se pueden probar con new, y el
        contenedor es un detalle del arranque, no del diseño.
    */

    /*
        =================================================================
        CAPA 13) ERRORES COMUNES
        =================================================================

        1) Crear dependencias con new dentro de la lógica (CAPA 1).

        2) Inyección por campo o por setter para dependencias obligatorias:
           objetos que pueden existir incompletos (CAPAS 2 y 3).

        3) Hacer new de servicios fuera del composition root (CAPA 4).

        4) Servicios compartidos con estado mutable no seguro con hilos
           (CAPA 5).

        5) Hacer new de una dependencia adentro porque "necesita datos del
           momento": inyecta una fábrica o un Supplier (CAPA 6).

        6) Resolver un ciclo con setters en vez de rediseñar (CAPA 9).

        7) Service locator, getInstance() o inyectar el contenedor en la
           lógica (CAPA 10).

        8) Esconder un constructor demasiado grande en vez de dividir la
           clase (CAPA 11).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Inyectar = recibir las dependencias desde afuera en vez de
          crearlas o buscarlas.
        - Por constructor (con final y requireNonNull): nace completo, las
          dependencias se ven, se prueba con new. Setter solo para lo
          opcional; por campo, evitarlo.
        - Todo se arma en UN lugar: el composition root.
        - Quien arma decide cuántas instancias: compartidas (sin estado) o
          nuevas.
        - Para crear objetos durante el trabajo, inyecta una fábrica o un
          Supplier.
        - Un contenedor (Spring) lee constructores, resuelve tipos, crea en
          orden, comparte instancias y falla al arrancar si falta algo o hay
          ciclos. Por dentro usa reflexión.
        - Los ciclos son errores de diseño: rediseña.
        - Service locator y getInstance esconden dependencias: evítalos.
        - Muchas dependencias = demasiadas responsabilidades.
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
