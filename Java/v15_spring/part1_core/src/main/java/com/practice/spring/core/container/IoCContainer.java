package com.practice.spring.core.container;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

/*
    =====================================================================
    EL CONTENEDOR DE SPRING: BEANS, INYECCIÓN, SCOPES Y PROXIES
    =====================================================================

    Ruta de spring/: guía 2  (part1_core)
    Anterior:  part1_core/.../startup/SpringAndBoot
    Siguiente: part1_core/.../autoconfig/AutoConfigurationUsage

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo.
    - Las comprobaciones son pruebas: IoCContainerTest, una por capa.
      Desde v15_spring/:  ./mvnw -pl part1_core test -Dtest=IoCContainerTest
    - Las pruebas arman el contenedor a mano con SOLO las clases de cada
      ejemplo (new AnnotationConfigApplicationContext(Clase1, Clase2...)),
      sin escanear paquetes: así cada capa ve exactamente sus beans.
    - Los mensajes de error citados son los reales de Spring Framework 7.

    Lo que ya se vio: el contenedor de juguete (v10_design/
    part3_responsibilities/DependencyInjection, CAPA 8), los proxies
    dinámicos (v11_patterns/part2_structural/Proxy, CAPAS 5 y 6) y
    Strategy (v11_patterns/part3_behavioral/Strategy).

    Importancia: MUY ALTA. Todo en Spring es un bean inyectado. Entender
    esta guía explica la mitad de los errores que verás en el trabajo.

    Índice:
        CAPA 1)  Bean y ApplicationContext
        CAPA 2)  Cómo se registra un bean: estereotipos y @Bean
        CAPA 3)  Inyección por constructor (y por qué no por campo)
        CAPA 4)  Varias implementaciones de una interface
        CAPA 5)  Scopes: cuántas instancias hay
        CAPA 6)  El ciclo de vida de un bean
        CAPA 7)  @Configuration es un proxy
        CAPA 8)  Cómo Spring agrega comportamiento: BeanPostProcessor y proxies
        CAPA 9)  Dependencias circulares
        CAPA 10) Errores comunes
        RESUMEN FINAL
*/
public class IoCContainer {

    /*
        =================================================================
        CAPA 1) BEAN Y APPLICATIONCONTEXT
        =================================================================

        Un BEAN es un objeto que CREA y ADMINISTRA el contenedor de Spring.
        No es un tipo especial de clase: es cualquier objeto que Spring
        conoce. El contenedor (la interface ApplicationContext) es, en el
        fondo, un mapa "nombre -> objeto" más la lógica para crearlos en el
        orden correcto y pasarles sus dependencias.

        Al arrancar (startup/SpringAndBoot, CAPA 5), el contenedor:
        1) junta las DEFINICIONES de beans (qué clase, qué dependencias);
        2) los crea en orden: primero los que no dependen de nadie;
        3) cada uno recibe sus dependencias por el constructor.

        Por defecto cada bean es ÚNICO en el contenedor (scope "singleton",
        CAPA 5): pedir OrderService dos veces da el MISMO objeto.

        En tu código NUNCA deberías pedirle beans al contenedor
        (context.getBean(...)): los recibes inyectados (CAPA 3). getBean
        aparece en pruebas y en código de infraestructura; en el negocio
        es el anti-patrón "service locator".
    */

    /*
        =================================================================
        CAPA 2) CÓMO SE REGISTRA UN BEAN: ESTEREOTIPOS Y @BEAN
        =================================================================

        Dos formas, para dos casos:

        1) Anotando TU clase con un ESTEREOTIPO; el escaneo de componentes
           (@ComponentScan, dentro de @SpringBootApplication) la encuentra:

               @Component    genérico
               @Service      lógica de negocio / casos de uso
               @Repository   acceso a datos; además, Spring traduce las
                             excepciones de la base de datos a su propia
                             jerarquía (DataAccessException)
               @Controller / @RestController   web (part2_web)

           Para el contenedor, @Service y @Component son lo mismo: el nombre
           documenta el ROL (y sirve para herramientas y aspectos).

        2) Con un método @Bean en una clase @Configuration, para objetos de
           clases que NO son tuyas (no puedes anotarlas) o que necesitan
           armarse con lógica: un Clock, un cliente HTTP configurado, un
           ObjectMapper.

        Nombre del bean: el de la clase con minúscula inicial
        ("orderService") o el del método @Bean ("clock"). (En clases anidadas
        como las de esta guía, Spring antepone la externa:
        "ioCContainer.OrderService".)
    */
    @Repository
    public static class InMemoryOrderRepository {
        private final List<String> orders = new CopyOnWriteArrayList<>();

        public void save(String order) {
            orders.add(order);
        }

        public int count() {
            return orders.size();
        }
    }

    @Configuration
    public static class ClockConfig {
        @Bean
        public Clock clock() {                              // Clock no es nuestra: se registra con @Bean
            return Clock.fixed(Instant.parse("2026-11-04T10:00:00Z"), ZoneOffset.UTC);
        }
    }

    /*
        =================================================================
        CAPA 3) INYECCIÓN POR CONSTRUCTOR (Y POR QUÉ NO POR CAMPO)
        =================================================================

        Si la clase tiene UN solo constructor, Spring lo usa y le pasa los
        beans que pide. No hace falta @Autowired (solo con varios
        constructores, para decir cuál).

        Es la forma recomendada (v10_design/part3_responsibilities/
        DependencyInjection, CAPA 3):
        - los campos pueden ser final: el objeto nace completo e inmutable;
        - las dependencias se ven en la firma: si son 9, la clase hace
          demasiado (SingleResponsibility);
        - en una prueba unitaria se crea con new, pasando falsos, SIN
          Spring.

        Lo que verás en código viejo, y conviene evitar:
            @Autowired private OrderRepository repository;   // por campo
        Funciona (Spring usa reflexión), pero el campo no puede ser final,
        la dependencia queda escondida, y para probar la clase hace falta
        Spring o reflexión.

        Si falta una dependencia, el contenedor NO arranca. Medido:

            NoSuchBeanDefinitionException: No qualifying bean of type
            'java.lang.Runnable' available: expected at least 1 bean which
            qualifies as autowire candidate.

        Y en una aplicación Boot, el mensaje viene "traducido" bajo
        "APPLICATION FAILED TO START", con una sección Description (qué
        pasó) y una Action (qué hacer).
    */
    @Service
    public static class OrderService {
        private final InMemoryOrderRepository repository;   // final: nace completo
        private final Clock clock;

        public OrderService(InMemoryOrderRepository repository, Clock clock) {   // un constructor: no hace falta @Autowired
            this.repository = repository;
            this.clock = clock;
        }

        public String placeOrder(String product) {
            String order = product + " @ " + clock.instant();
            repository.save(order);
            return order;
        }
    }

    public static class NeedsSomethingMissing {
        public NeedsSomethingMissing(Runnable notRegistered) {
            notRegistered.run();
        }
    }

    /*
        =================================================================
        CAPA 4) VARIAS IMPLEMENTACIONES DE UNA INTERFACE
        =================================================================

        Si un constructor pide PaymentMethod y hay DOS beans que la
        implementan, Spring no adivina. Medido, en una aplicación Boot:

            ***************************
            APPLICATION FAILED TO START
            ***************************
            Description:
            Parameter 0 of constructor in ...Checkout required a single
            bean, but 2 were found:
                - ...CardPayment: defined in file [...]
                - ...CashPayment: defined in file [...]
            Action:
            Consider marking one of the beans as @Primary, updating the
            consumer to accept multiple beans, or using @Qualifier to
            identify the bean that should be consumed

        Las tres salidas que propone, y cuándo usar cada una:

        1) @Primary en una: "si piden una sin decir cuál, esta". Para un
           valor por defecto claro.
        2) @Qualifier("nombre") en el parámetro: "quiero ESTA". Para cuando
           el consumidor sabe cuál necesita. (Si no hay @Qualifier, Spring
           intenta además con el NOMBRE del parámetro: un parámetro llamado
           cardPayment elige el bean "cardPayment". Funciona porque Boot
           compila con -parameters, pero es frágil: renombrar el parámetro
           cambia el bean. Mejor explícito.)
        3) Pedir TODAS: List<PaymentMethod> (en orden, con @Order) o
           Map<String, PaymentMethod> (nombre del bean -> bean). Es el
           patrón Strategy resuelto por el contenedor: agregar una forma de
           pago nueva es agregar una clase con @Component, sin tocar al
           consumidor (v10_design/part2_solid/OpenClosed).
    */
    public interface PaymentMethod {
        String code();
    }

    @Component("card")
    @Primary
    public static class CardPayment implements PaymentMethod {
        @Override
        public String code() {
            return "CARD";
        }
    }

    @Component("cash")
    public static class CashPayment implements PaymentMethod {
        @Override
        public String code() {
            return "CASH";
        }
    }

    @Service
    public static class Checkout {
        private final PaymentMethod defaultMethod;
        private final PaymentMethod cashOnly;
        private final Map<String, PaymentMethod> allByName;

        public Checkout(PaymentMethod defaultMethod,                       // el @Primary
                        @Qualifier("cash") PaymentMethod cashOnly,         // uno en concreto
                        Map<String, PaymentMethod> allByName) {            // todos: Strategy
            this.defaultMethod = defaultMethod;
            this.cashOnly = cashOnly;
            this.allByName = allByName;
        }

        public String defaultCode() {
            return defaultMethod.code();
        }

        public String cashCode() {
            return cashOnly.code();
        }

        public String pay(String methodName) {
            PaymentMethod method = allByName.get(methodName);
            if (method == null) {
                throw new IllegalArgumentException("Forma de pago no soportada: " + methodName);
            }
            return method.code();
        }
    }

    /*
        =================================================================
        CAPA 5) SCOPES: CUÁNTAS INSTANCIAS HAY
        =================================================================

            singleton (por defecto)  UNA instancia por contenedor
            prototype                una NUEVA cada vez que se pide
            request, session         una por petición HTTP / por sesión
                                     (solo en aplicaciones web)

        SINGLETON aquí NO es el patrón Singleton (v11_patterns/
        part1_creational/Singleton): es "una por contenedor", sin static.
        Pero tiene la misma consecuencia importante: UN objeto atiende a
        TODAS las peticiones, desde muchos hilos a la vez. Un servicio
        singleton NO debe guardar estado de una petición en sus campos (el
        usuario actual, un total parcial): se mezclaría entre usuarios
        (v13_concurrency/part1_threads/ConcurrencyProblems). Sus campos son
        sus dependencias (final) y nada más. Casi todos los beans son
        singleton y sin estado.

        La trampa del PROTOTYPE: si un singleton recibe un prototype por el
        constructor, se crea UNA vez (cuando se crea el singleton) y se
        queda para siempre: "prototype" no hace nada. Para obtener uno nuevo
        cada vez, se inyecta un ObjectProvider<T> y se le pide getObject()
        cuando se necesita (es inyectar una fábrica: DependencyInjection,
        CAPA 6).
    */
    @Component
    @Scope("prototype")
    public static class ReportBuilder {
        private final String id = UUID.randomUUID().toString();
        private final List<String> lines = new ArrayList<>();   // estado propio: por eso prototype

        public ReportBuilder add(String line) {
            lines.add(line);
            return this;
        }

        public String id() {
            return id;
        }

        public int size() {
            return lines.size();
        }
    }

    @Service
    public static class ReportService {
        private final ReportBuilder injectedOnce;           // la trampa: el mismo para siempre
        private final ObjectProvider<ReportBuilder> builders;   // la forma correcta: uno nuevo cada vez

        public ReportService(ReportBuilder injectedOnce, ObjectProvider<ReportBuilder> builders) {
            this.injectedOnce = injectedOnce;
            this.builders = builders;
        }

        public ReportBuilder trapped() {
            return injectedOnce;
        }

        public ReportBuilder fresh() {
            return builders.getObject();
        }
    }

    /*
        =================================================================
        CAPA 6) EL CICLO DE VIDA DE UN BEAN
        =================================================================

            1) constructor             (con sus dependencias)
            2) @PostConstruct          ya está todo inyectado: inicializar
            3) ... la aplicación lo usa ...
            4) @PreDestroy             el contenedor se cierra: liberar
                                       recursos (conexiones, hilos, archivos)

        @PostConstruct y @PreDestroy son anotaciones estándar
        (jakarta.annotation). Equivalentes: @Bean(initMethod = "...",
        destroyMethod = "...") para clases que no son tuyas, o implementar
        InitializingBean / DisposableBean (más viejo). Y si el bean es
        AutoCloseable, Spring llama a close() al cerrar, solo.

        Dos cuidados:
        - @PreDestroy SOLO corre si el contenedor se cierra ordenadamente
          (context.close(), o la JVM recibe la señal de apagado: SIGTERM en
          Linux, que es lo que manda Docker o Kubernetes al detener un
          contenedor). Con kill -9 no corre nada (v12_jvm/part1_memory/
          GarbageCollector, CAPA 8: lo importante no depende de esto).
        - Nada pesado ni por red en el constructor o en @PostConstruct:
          retrasa el arranque y, si falla, la aplicación no arranca
          (startup/SpringAndBoot, CAPA 9).
    */
    @Component
    public static class ConnectionPoolLike {
        private final List<String> lifecycle;

        public ConnectionPoolLike(LifecycleLog log) {
            this.lifecycle = log.events();
            lifecycle.add("constructor");
        }

        @PostConstruct
        public void open() {
            lifecycle.add("@PostConstruct: abrir conexiones");
        }

        @PreDestroy
        public void close() {
            lifecycle.add("@PreDestroy: cerrar conexiones");
        }
    }

    @Component
    public static class LifecycleLog {
        private final List<String> events = new CopyOnWriteArrayList<>();

        public List<String> events() {
            return events;
        }
    }

    /*
        =================================================================
        CAPA 7) @CONFIGURATION ES UN PROXY
        =================================================================

        Dentro de una clase @Configuration, un método @Bean puede llamar a
        otro:

            @Bean RestClient paymentsClient() { return build(httpSettings()); }
            @Bean RestClient shippingClient() { return build(httpSettings()); }
            @Bean HttpSettings httpSettings() { return new HttpSettings(); }

        Como código Java normal, httpSettings() crearía un objeto NUEVO en
        cada llamada. Pero Spring no usa tu clase tal cual: crea una
        SUBCLASE generada al ejecutar (con CGLIB, una librería que genera
        bytecode; su nombre trae $$SpringCGLIB$$) que intercepta las
        llamadas a métodos @Bean y devuelve el bean que ya existe. Así
        httpSettings() da SIEMPRE el mismo singleton.

        Con @Configuration(proxyBeanMethods = false) ("modo lite") no hay
        subclase: cada llamada crea uno nuevo. Arranca un poco más rápido y
        es lo que usan las autoconfiguraciones de Boot, que nunca llaman a
        un @Bean desde otro: piden lo que necesitan como PARÁMETRO del
        método @Bean. Esa es la forma recomendada en ambos modos:

            @Bean RestClient paymentsClient(HttpSettings settings) { ... }

        Por eso una clase @Configuration no puede ser final (hay que poder
        heredar de ella), y sus métodos @Bean tampoco.
    */
    public static class HttpSettings {
    }

    @Configuration
    public static class FullConfig {
        @Bean
        public HttpSettings httpSettings() {
            return new HttpSettings();
        }

        @Bean
        public List<HttpSettings> twoCalls() {
            return List.of(httpSettings(), httpSettings());  // interceptadas: el mismo bean dos veces
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class LiteConfig {
        @Bean
        public HttpSettings httpSettings() {
            return new HttpSettings();
        }

        @Bean
        public List<HttpSettings> twoCalls() {
            return List.of(httpSettings(), httpSettings());  // llamadas normales: dos objetos nuevos
        }
    }

    /*
        =================================================================
        CAPA 8) CÓMO SPRING AGREGA COMPORTAMIENTO: BEANPOSTPROCESSOR Y
                PROXIES
        =================================================================

        @Transactional, @Cacheable, @Async, @PreAuthorize... agregan
        comportamiento a TUS métodos sin que escribas nada adentro. ¿Cómo?

        Entre que el bean se crea y que se entrega a quien lo pide, pasa por
        los BeanPostProcessor: objetos del contenedor que pueden
        REEMPLAZARLO por otro. Los que implementan esas anotaciones
        devuelven un PROXY que envuelve a tu bean: el proxy hace lo suyo
        (abrir la transacción, revisar la caché) y luego llama a tu método
        (v11_patterns/part2_structural/Proxy, CAPAS 5 y 6).

        Aquí uno pequeño, con la misma mecánica: @Counted cuenta cuántas
        veces se llama un método. El BeanPostProcessor ve los beans cuya
        clase tiene métodos con @Counted y los envuelve en un proxy dinámico
        del JDK.

        Dos consecuencias que explican errores reales:
        1) Lo que se inyecta es el PROXY, no tu objeto. Por eso los
           servicios conviene que tengan interface, o que la clase no sea
           final (Spring también sabe hacer proxies de clase con CGLIB).
        2) LA AUTO-INVOCACIÓN: si un método del bean llama a OTRO método
           del mismo bean (this.otro()), esa llamada NO pasa por el proxy:
           ya está "adentro". Por eso un @Transactional en un método
           llamado desde la misma clase no abre transacción. La prueba lo
           demuestra con @Counted (part3_data lo verá con transacciones).
    */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface Counted {
    }

    public interface Greeter {
        String greet(String name);

        String greetTwice(String name);
    }

    @Component
    public static class FriendlyGreeter implements Greeter {
        @Counted
        @Override
        public String greet(String name) {
            return "Hola, " + name;
        }

        @Override
        public String greetTwice(String name) {
            return greet(name) + " / " + greet(name);       // this.greet: NO pasa por el proxy
        }
    }

    @Component
    public static class CallCounter {
        private final AtomicInteger calls = new AtomicInteger();

        public int calls() {
            return calls.get();
        }

        void increment() {
            calls.incrementAndGet();
        }
    }

    @Component
    public static class CountedPostProcessor implements BeanPostProcessor {
        private final ObjectProvider<CallCounter> counter;  // perezoso: los BeanPostProcessor se crean primero

        public CountedPostProcessor(ObjectProvider<CallCounter> counter) {
            this.counter = counter;
        }

        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) {
            boolean hasCounted = java.util.Arrays.stream(bean.getClass().getMethods())
                    .anyMatch(method -> method.isAnnotationPresent(Counted.class));
            if (!hasCounted) {
                return bean;                                // sin @Counted: se entrega tal cual
            }
            return Proxy.newProxyInstance(bean.getClass().getClassLoader(), bean.getClass().getInterfaces(),
                    (proxy, method, args) -> {
                        if (isCounted(bean, method)) {
                            counter.getObject().increment();   // el "comportamiento agregado"
                        }
                        return method.invoke(bean, args);   // y luego, tu método
                    });
        }

        private static boolean isCounted(Object bean, java.lang.reflect.Method interfaceMethod) {
            try {
                return bean.getClass().getMethod(interfaceMethod.getName(), interfaceMethod.getParameterTypes())
                        .isAnnotationPresent(Counted.class);
            } catch (NoSuchMethodException e) {
                return false;
            }
        }
    }

    /*
        =================================================================
        CAPA 9) DEPENDENCIAS CIRCULARES
        =================================================================

        A necesita a B en su constructor, y B necesita a A. Con inyección
        por constructor es imposible crearlos (para crear A hay que tener B,
        y viceversa). Medido:

            BeanCurrentlyInCreationException: Error creating bean with name
            'ioCContainer.ChickenService': Requested bean is currently in
            creation: Is there an unresolvable circular reference or an
            asynchronous initialization dependency?

        Spring Boot, además, PROHÍBE los ciclos por defecto desde la 2.6
        (spring.main.allow-circular-references=false), incluso los que
        antes "funcionaban" con inyección por campo.

        No se arregla con trucos (@Lazy, inyección por setter): un ciclo es
        un problema de DISEÑO. Las salidas (DependencyInjection, CAPA 9):
        - sacar lo que los dos necesitan a una tercera clase;
        - que uno avise con un EVENTO en vez de llamar al otro
          (v14_architecture/part1_layers/DDDBasics, CAPA 8).
    */
    @Service
    public static class ChickenService {
        public ChickenService(EggService eggs) {
            eggs.getClass();
        }
    }

    @Service
    public static class EggService {
        public EggService(ChickenService chickens) {
            chickens.getClass();
        }
    }

    /*
        =================================================================
        CAPA 10) ERRORES COMUNES
        =================================================================

        1) context.getBean(...) en código de negocio, o new de un servicio
           que debería ser bean (CAPA 1).

        2) Anotar clases de librerías... que no puedes anotar: para eso es
           @Bean (CAPA 2).

        3) @Autowired en campos; clases con 9 dependencias (CAPA 3).

        4) Varias implementaciones sin decidir cuál; @Qualifier con nombres
           escritos a mano por todos lados en vez de List/Map o @Primary
           (CAPA 4).

        5) Guardar estado de una petición en un singleton; inyectar un
           prototype por constructor esperando uno nuevo cada vez (CAPA 5).

        6) Trabajo pesado en @PostConstruct; confiar en @PreDestroy para
           lo que no puede perderse (CAPA 6).

        7) Clases @Configuration o métodos @Bean final; llamar @Bean entre sí
           en modo lite esperando el mismo objeto (CAPA 7).

        8) Esperar que @Transactional, @Cacheable o @Async funcionen en
           llamadas dentro de la misma clase (CAPA 8).

        9) Resolver dependencias circulares con @Lazy en vez de rediseñar
           (CAPA 9).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Bean: objeto que crea y conecta el contenedor (ApplicationContext).
        - Se registran con estereotipos (@Component, @Service, @Repository)
          o con @Bean en una @Configuration (clases ajenas).
        - Inyección por constructor, campos final; si falta algo, no arranca.
        - Varias implementaciones: @Primary, @Qualifier, o List/Map.
        - Singleton por defecto (uno por contenedor, compartido por todos
          los hilos: sin estado). Prototype con ObjectProvider.
        - Ciclo de vida: constructor -> @PostConstruct -> uso -> @PreDestroy.
        - @Configuration es una subclase CGLIB; mejor pedir dependencias
          como parámetros de @Bean.
        - Las anotaciones de comportamiento son proxies puestos por
          BeanPostProcessors; la auto-invocación no pasa por ellos.
        - Ciclos: error de diseño, no de configuración.
    */
}
