package com.practice.spring.production.async;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

/*
    =====================================================================
    @ASYNC, @SCHEDULED Y @CACHEABLE: TRABAJO EN OTRO HILO, A SU HORA, Y
    SIN REPETIRLO
    =====================================================================

    Ruta de spring/: guía 18  (part6_production)
    Anterior:  part6_production/.../observability/Observability
    Siguiente: part6_production/.../packaging/PackagingAndDocker

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo.
    - Las comprobaciones son pruebas: AsyncSchedulingCachingTest (el pool
      normal) y VirtualThreadsTest (con hilos virtuales). Desde v15_spring/:
          ./mvnw -pl part6_production test -Dtest='AsyncSchedulingCachingTest,VirtualThreadsTest'

    Lo que ya se vio: hilos, pools y por qué un ThreadLocal no viaja a otro
    hilo (v13_concurrency/part1_threads/ThreadBasics), ExecutorService y
    CompletableFuture (v13_concurrency/part3_tasks/ExecutorsUsage y
    CompletableFutureUsage), hilos virtuales (v13_concurrency/part3_tasks/
    VirtualThreads), los proxies de Spring y la trampa de "this"
    (part3_data/.../jdbc/JdbcAndTransactions, CAPA 6), el MDC con el
    requestId (observability/Observability, CAPA 5).

    Importancia: ALTA. Las tres anotaciones se ven en casi cualquier
    proyecto, y las tres fallan EN SILENCIO cuando se usan mal: el código
    compila, corre, y simplemente no hace lo que crees.

    Índice:
        CAPA 1)  Tres problemas, tres anotaciones
        CAPA 2)  @Async: el trabajo sigue en otro hilo
        CAPA 3)  La trampa del proxy (otra vez)
        CAPA 4)  Errores y contexto en otro hilo
        CAPA 5)  El pool: cuántos hilos y qué pasa cuando se llena
        CAPA 6)  Hilos virtuales con una propiedad
        CAPA 7)  @Scheduled: tareas a su hora
        CAPA 8)  @Cacheable: no repetir lo caro
        CAPA 9)  Errores comunes
        RESUMEN FINAL
*/
public class AsyncSchedulingCaching {

    /*
        =================================================================
        CAPA 1) TRES PROBLEMAS, TRES ANOTACIONES
        =================================================================

        Un cliente paga un pedido. Tres situaciones típicas:

        - mandar el recibo por correo tarda 2 segundos y al cliente no le
          importa esperarlo: la respuesta HTTP no debería esperar. Eso es
          @Async: el método corre en OTRO hilo y el que llamó sigue;
        - cada 10 minutos hay que borrar los carritos abandonados, y cada
          noche generar un reporte: nadie lo pide con una petición. Eso es
          @Scheduled;
        - la ficha de un producto se consulta mil veces por minuto y cambia
          una vez al día: ir a la base cada vez es desperdicio. Eso es
          @Cacheable: la primera llamada calcula, las siguientes reusan.

        Las tres se encienden con su @Enable... (abajo, en TasksApplication)
        y las tres funcionan igual por dentro: Spring envuelve el bean en un
        PROXY (part1_core/.../container/IoCContainer) que intercepta la
        llamada y hace algo antes: mandarla a otro hilo, o buscar en la
        caché. De ahí sale la trampa de la CAPA 3.

        @Async y @Scheduled vienen en spring-context: no piden dependencia.
        La caché pide spring-boot-starter-cache (ver el pom.xml).
    */
    @SpringBootApplication
    @EnableAsync
    @EnableScheduling
    @EnableCaching
    public static class TasksApplication {

        /** CAPA 4: copia el MDC (el requestId) del hilo que llama al hilo del pool. */
        @Bean
        TaskDecorator mdcPropagation() {
            return task -> {
                Map<String, String> context = MDC.getCopyOfContextMap();   // en el hilo que llama
                return () -> {                                            // en el hilo del pool
                    if (context != null) {
                        MDC.setContextMap(context);
                    }
                    try {
                        task.run();
                    } finally {
                        MDC.clear();                                      // el hilo vuelve al pool
                    }
                };
            };
        }
    }

    /*
        =================================================================
        CAPA 2) @ASYNC: EL TRABAJO SIGUE EN OTRO HILO
        =================================================================

        Con @EnableAsync, un método marcado con @Async ya no corre en el hilo
        que lo llama: el proxy lo manda a un POOL de hilos (un Executor,
        v13_concurrency/part3_tasks/ExecutorsUsage) y regresa de inmediato.

        Qué puede devolver:
            void                    "dispara y olvida": no sabes si terminó
                                    ni si falló (CAPA 4)
            CompletableFuture<T>    el resultado llegará después; quien
                                    llama decide si espera (join) o encadena
                                    (thenApply...), igual que en
                                    CompletableFutureUsage

        Medido en la prueba: la llamada sale del hilo "main" y el recibo
        se manda desde "task-3", un hilo del pool (task-1, task-2... según el
        orden de las pruebas) que Spring Boot crea
        solo (el bean applicationTaskExecutor).

        Delivery guarda en qué hilo corrió (y si era virtual, CAPA 6) y qué
        requestId vio, solo para
        que la prueba lo pueda revisar.
    */
    public record Delivery(String orderId, String thread, boolean virtualThread, String requestId) {
    }

    @Service
    public static class ReceiptService {
        private static final Logger LOG = LoggerFactory.getLogger(ReceiptService.class);

        @Async
        public CompletableFuture<Delivery> sendReceipt(String orderId) {
            LOG.info("Recibo enviado para {}", orderId);             // aquí iría el correo (lento)
            return CompletableFuture.completedFuture(
                    new Delivery(orderId, Thread.currentThread().getName(),
                            Thread.currentThread().isVirtual(), MDC.get("requestId")));
        }

        /*
            =============================================================
            CAPA 3) LA TRAMPA DEL PROXY (OTRA VEZ)
            =============================================================

            Es la misma que la de @Transactional (JdbcAndTransactions,
            CAPA 6): el proxy solo intercepta llamadas que ENTRAN al bean
            desde afuera. Si un método del bean llama a otro con this, la
            llamada no pasa por el proxy y la anotación se ignora.

            Medido: sendReceiptViaThis llama a sendReceipt (que tiene
            @Async) y el recibo se mandó desde "main": el MISMO hilo que
            llamó. Sin error, sin advertencia: simplemente no fue asíncrono.

            Lo mismo con @Cacheable (CAPA 8) y con @Async en un método
            private o final: el proxy no lo puede interceptar. La salida: que
            la llamada venga de OTRO bean.
        */
        public Delivery sendReceiptViaThis(String orderId) {
            return sendReceipt(orderId).join();                       // this.sendReceipt: sin proxy
        }

        /*
            =============================================================
            CAPA 4) ERRORES Y CONTEXTO EN OTRO HILO
            =============================================================

            Una excepción en otro hilo NO llega a quien llamó: ese hilo ya
            siguió con su vida (v13_concurrency/part1_threads/ThreadBasics).

            - Con CompletableFuture, la excepción queda DENTRO del futuro: al
              hacer join() sale como CompletionException, o se maneja con
              exceptionally(...). No se pierde.
            - Con void, no hay a quién devolverla. Spring la manda a un
              AsyncUncaughtExceptionHandler; el de fábrica solo la escribe en
              el log. Medido: quien llamó a notifyWarehouse no vio nada, y en
              el log apareció (en formato de texto)
                  ERROR ... SimpleAsyncUncaughtExceptionHandler : Unexpected
                  exception occurred invoking async method: public void
                  ...ReceiptService.notifyWarehouse(java.lang.String)
                  java.lang.IllegalStateException: El almacén no responde ...
              Si ese error importa, devuelve CompletableFuture, o registra
              tu propio handler (implementando AsyncConfigurer) que lo
              cuente en una métrica.

            El CONTEXTO tampoco viaja. El MDC (el requestId de Observability)
            y el usuario de Spring Security viven en ThreadLocal: en el hilo
            del pool no están. Para el MDC, un TaskDecorator (en
            TasksApplication) envuelve cada tarea: copia el mapa en el hilo
            que llama y lo pone en el hilo del pool. Spring Boot aplica solo
            un bean TaskDecorator a su pool. Medido: el recibo vio
            requestId "req-7"; una tarea en un hilo SIN el decorador
            (CompletableFuture.supplyAsync, el pool común de Java) vio null.
            Para Spring Security existe DelegatingSecurityContextExecutor,
            que hace lo mismo con el usuario.

            Y las TRANSACCIONES tampoco viajan (también son ThreadLocal): un
            @Async llamado dentro de un @Transactional corre FUERA de esa
            transacción, y puede ejecutarse ANTES de que se confirme; si
            busca el pedido recién creado, todavía no lo ve. Lo correcto es
            publicar un evento y escucharlo con
                @Async @TransactionalEventListener(phase = AFTER_COMMIT)
            que corre solo después del commit, y nunca si hubo rollback.
        */
        @Async
        public void notifyWarehouse(String orderId) {
            throw new IllegalStateException("El almacén no responde (pedido " + orderId + ")");
        }
    }

    /*
        =================================================================
        CAPA 5) EL POOL: CUÁNTOS HILOS Y QUÉ PASA CUANDO SE LLENA
        =================================================================

        El pool que Spring Boot crea (un ThreadPoolTaskExecutor) trae, medido:

            core-size        8
            max-size         2147483647   (Integer.MAX_VALUE)
            queue-capacity   2147483647   (Integer.MAX_VALUE: sin límite)

        Ojo con la combinación: un pool de Java solo crea hilos de más
        (hasta max-size) cuando la COLA está llena, y esta cola nunca se
        llena. En la práctica son 8 hilos y una fila sin fin: si llegan
        tareas más rápido de lo que salen, la cola crece en memoria hasta
        que algo truena, y nadie se entera antes. En producción se ajusta:

            spring.task.execution.pool.core-size=8
            spring.task.execution.pool.max-size=16
            spring.task.execution.pool.queue-capacity=500
            spring.task.execution.thread-name-prefix=receipts-

        Con la cola limitada, al llenarse todo se rechaza la tarea
        (TaskRejectedException): un error VISIBLE en vez de una fila
        infinita. Y al apagar la aplicación, por defecto no se espera a las
        tareas pendientes; para darles tiempo:

            spring.task.execution.shutdown.await-termination=true
            spring.task.execution.shutdown.await-termination-period=30s

        Lo que ya está en la cola cuando el proceso muere SE PIERDE: lo que
        no puede perderse (un correo de cobro, un pago) no va en @Async sino
        en una cola de mensajes o una tabla de pendientes
        (v14_architecture/part3_systems/CommandsQueriesAndConsistency).
    */

    /*
        =================================================================
        CAPA 6) HILOS VIRTUALES CON UNA PROPIEDAD
        =================================================================

        Desde Spring Boot 3.2 (y con Java 21 o más):

            spring.threads.virtual.enabled=true

        cambia varias cosas a la vez: Tomcat atiende cada petición en un hilo
        virtual, @Async usa un SimpleAsyncTaskExecutor que crea un hilo
        virtual por tarea (sin pool: los hilos virtuales son baratos,
        v13_concurrency/part3_tasks/VirtualThreads), y @Scheduled también
        corre en hilos virtuales. Medido en VirtualThreadsTest: el recibo se
        mandó desde un hilo con isVirtual() = true, y el bean
        applicationTaskExecutor era un SimpleAsyncTaskExecutor (el hilo se seguía llamando task-1:
        el nombre no dice si es virtual).

        Qué cambia para ti:
        - ya no hay "8 hilos": sin pool no hay límite natural. Si cada tarea
          llama a un servicio externo o a la base, mil tareas al mismo tiempo
          son mil conexiones. Se limita con
              spring.task.execution.simple.concurrency-limit=50
          o, mejor, limitando el recurso (el pool de conexiones);
        - los hilos virtuales son "daemon": una aplicación SIN servidor web
          que solo tiene tareas @Scheduled se cerraría al terminar main. Para
          eso existe spring.main.keep-alive=true;
        - sirven para trabajo que ESPERA (red, base de datos). Para cálculo
          puro no ganan nada: el límite son los núcleos.
    */

    /*
        =================================================================
        CAPA 7) @SCHEDULED: TAREAS A SU HORA
        =================================================================

        Con @EnableScheduling, un método sin parámetros con @Scheduled corre
        solo, cada cierto tiempo:

            fixedRateString = "PT10M"   cada 10 minutos desde que EMPEZÓ la
                                        anterior (si tarda más, la siguiente
                                        espera; no se enciman)
            fixedDelayString = "PT10M"  10 minutos desde que TERMINÓ la anterior
            (fixedRate y fixedDelay, sin String, reciben un long: milisegundos,
            o la unidad que digas en timeUnit)
            cron = "0 0 3 * * *"    a una hora del calendario: aquí, las
                                    3:00 de cada día

        El cron de Spring tiene SEIS campos (segundo, minuto, hora, día del
        mes, mes, día de la semana), no cinco como el cron de Linux. Medido
        con CronExpression (la clase que Spring usa para calcularlo):
        "0 0 3 * * MON-FRI" evaluado un viernes a las 04:00 da la siguiente
        ejecución el lunes 2026-11-09 a las 03:00. Pon siempre zone = "...": sin ella, se usa la
        zona del servidor, y en un contenedor suele ser UTC.

        Los tiempos van en una PROPIEDAD con valor por defecto
        ("${shop.carts.cleanup-delay:PT10M}"), así cada ambiente (y la
        prueba) lo cambia sin tocar código. La prueba lo pone en 50 ms y
        espera a que corra tres veces.

        Las tres trampas:
        - por defecto hay UN SOLO hilo para todas las tareas (medido: todas
          corrieron en "scheduling-1"). Una tarea lenta retrasa a TODAS las
          demás. Se sube con spring.task.scheduling.pool.size;
        - una excepción en la tarea se escribe en el log y la tarea sigue
          programada; nadie más se entera. Que la tarea registre su error en
          una métrica (Observability);
        - con TRES instancias de la aplicación, la tarea corre TRES veces: tres
          reportes, tres cobros. Se necesita un candado compartido (una fila
          en la base que solo una instancia puede tomar; la librería ShedLock
          es lo usual), o un único proceso dedicado a las tareas.
    */
    @Component
    public static class ExpiredCartsCleaner {
        private final AtomicInteger runs = new AtomicInteger();
        private volatile String lastThread;

        @Scheduled(fixedDelayString = "${shop.carts.cleanup-delay:PT10M}")
        void removeExpiredCarts() {
            runs.incrementAndGet();                                    // aquí: DELETE de carritos viejos
            lastThread = Thread.currentThread().getName();
        }

        @Scheduled(cron = "0 0 3 * * *", zone = "America/Mexico_City")
        void nightlyReport() {
            // el reporte de ventas del día; corre a las 3:00 hora de México
        }

        public int runs() {
            return runs.get();
        }

        public String lastThread() {
            return lastThread;
        }
    }

    /*
        =================================================================
        CAPA 8) @CACHEABLE: NO REPETIR LO CARO
        =================================================================

        @Cacheable("products") en un método: el proxy busca en la caché
        "products" con los parámetros como llave. Si está, devuelve eso SIN
        ejecutar el método. Si no, lo ejecuta y guarda el resultado.

        Medido en la prueba, con una "consulta" de 50 ms: la primera llamada
        a find(1) tardó 82 ms y la segunda 0 ms, y la
        base se consultó UNA vez.

        Lo difícil de una caché no es guardar, es saber cuándo BORRAR:
            @CacheEvict(value = "products", key = "#product.id()")
                al actualizar, se borra esa entrada; la próxima lectura va a
                la base. (@CachePut, en cambio, guarda el valor nuevo).
                Medido: después de update, find(1) devolvió el precio nuevo.
            @Cacheable(..., unless = "#result == null")
                por defecto también se guarda el null: un producto que no
                existía "no existe" para siempre, aunque después lo creen.
                Con unless no se guarda. Medido: find(99) dos veces fueron
                dos consultas.

        El proveedor: sin configurar nada, Spring Boot usa un mapa en memoria
        (ConcurrentMapCacheManager): sin límite de tamaño y sin tiempo de
        vida. Sirve para aprender, no para producción. Lo usual:
            Caffeine    en memoria de cada instancia, con tamaño máximo y
                        expiración (spring.cache.caffeine.spec=
                        maximumSize=10000,expireAfterWrite=10m)
            Redis       un servidor aparte, COMPARTIDO por todas las
                        instancias (spring-boot-starter-data-redis y
                        spring.cache.redis.time-to-live=10m)
        Con una caché local y tres instancias, un @CacheEvict solo borra en
        la instancia que atendió el update: las otras dos siguen dando el
        precio viejo hasta que expire. Por eso la expiración es obligatoria,
        y por eso existe Redis.

        Qué NO cachear: datos por usuario con una llave que no incluye al
        usuario (uno ve los datos del otro), datos que deben estar al
        segundo (saldo, inventario al vender), y objetos que alguien va a
        MODIFICAR: la caché en memoria devuelve el MISMO objeto, y cambiarlo
        cambia la caché. Product es un record (inmutable): no hay ese riesgo.
    */
    public record Product(long id, String name, long priceCents) {
    }

    @Service
    public static class ProductCatalog {
        private final Map<Long, Product> table = new ConcurrentHashMap<>();   // "la base"
        private final AtomicInteger queries = new AtomicInteger();

        public ProductCatalog() {
            table.put(1L, new Product(1, "Teclado", 89_900));
        }

        @Cacheable(value = "products", unless = "#result == null")
        public Product find(long id) {
            queries.incrementAndGet();
            sleep(Duration.ofMillis(50));                              // la consulta lenta
            return table.get(id);
        }

        @CacheEvict(value = "products", key = "#product.id()")
        public void update(Product product) {
            table.put(product.id(), product);
        }

        public Product findViaThis(long id) {
            return find(id);                                           // this.find: sin caché (CAPA 3)
        }

        public int queries() {
            return queries.get();
        }

        private static void sleep(Duration duration) {
            try {
                Thread.sleep(duration);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /*
        =================================================================
        CAPA 9) ERRORES COMUNES
        =================================================================

        1) Llamar al método @Async o @Cacheable desde el mismo bean (this),
           o ponerlo en un método private: la anotación no hace nada (CAPA 3).

        2) @Async void para algo cuyo fallo importa: el error solo queda en
           el log (CAPA 4).

        3) Perder el requestId, el usuario o la transacción en el otro hilo;
           @Async dentro de una transacción que todavía no se confirma (CAPA 4).

        4) Dejar la cola del pool sin límite; usar @Async para algo que no
           se puede perder si el proceso muere (CAPA 5).

        5) Hilos virtuales sin limitar cuántas tareas tocan la base a la vez
           (CAPA 6).

        6) Cron de cinco campos o sin zona; un solo hilo para tareas lentas;
           tareas que corren en cada instancia (CAPA 7).

        7) Caché sin expiración, cachear null sin querer, caché local con
           varias instancias, llaves que no incluyen al usuario (CAPA 8).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Las tres funcionan con un proxy: solo interceptan llamadas que
          vienen de otro bean, a métodos públicos.
        - @Async + @EnableAsync: otro hilo; devuelve CompletableFuture para
          no perder el resultado ni el error. MDC, usuario y transacción no
          viajan solos (TaskDecorator; @TransactionalEventListener
          AFTER_COMMIT).
        - El pool de Boot: 8 hilos y cola sin límite; ajustar
          spring.task.execution.pool.*.
        - spring.threads.virtual.enabled=true: hilos virtuales en Tomcat,
          @Async y @Scheduled; limitar la concurrencia hacia los recursos.
        - @Scheduled + @EnableScheduling: fixedRate, fixedDelay, cron de seis
          campos con zone; un hilo por defecto; cuidado con varias instancias.
        - @Cacheable / @CacheEvict / @CachePut + @EnableCaching; Caffeine
          (local) o Redis (compartida), siempre con expiración.
    */
}
