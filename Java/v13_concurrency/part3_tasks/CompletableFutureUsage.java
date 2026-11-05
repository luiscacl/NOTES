package v13_concurrency.part3_tasks;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/*
    =====================================================================
    COMPLETABLEFUTURE: ENCADENAR Y COMBINAR TAREAS SIN BLOQUEAR
    =====================================================================

    Ruta de concurrency/: guía 8 de 9  (part3_tasks)
    Anterior:  part3_tasks/ExecutorsUsage
    Siguiente: part3_tasks/VirtualThreads

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.
    - Los "servicios" de los ejemplos simulan la espera de una llamada
      por red con sleep.
    - La guía se llama CompletableFutureUsage para no tapar a la clase del
      JDK (como part3_tasks/ExecutorsUsage).

    Lo que ya se vio: Future y los pools (part3_tasks/ExecutorsUsage) y
    las lambdas que aquí se encadenan (v7_functional/).

    Importancia: ALTA. Llamar a varios servicios en paralelo y combinar
    sus respuestas es de lo más común en un backend; HttpClient.sendAsync
    devuelve un CompletableFuture (v8_apis/part2_io_and_network/
    HttpClientUsage).

    Índice:
        CAPA 1)  El problema: Future.get bloquea un hilo esperando
        CAPA 2)  Crear: supplyAsync, siempre con tu propio executor
        CAPA 3)  Transformar: thenApply, thenAccept y thenCompose
        CAPA 4)  Combinar: thenCombine, allOf y anyOf
        CAPA 5)  ¿En qué hilo corre cada paso?
        CAPA 6)  Errores: exceptionally, handle y whenComplete
        CAPA 7)  Tiempos límite (que no detienen el trabajo)
        CAPA 8)  Un caso completo: la página de checkout
        CAPA 9)  ¿Y con hilos virtuales?
        CAPA 10) Errores comunes
        RESUMEN FINAL
*/
public class CompletableFutureUsage {

    public static void main(String[] args) {
        try (ExecutorService io = Executors.newFixedThreadPool(8, named("io"))) {
            check(createExample(io), "CAPA 2");
            check(transformExample(io), "CAPA 3");
            check(combineExample(io), "CAPA 4");
            check(whichThreadExample(io), "CAPA 5");
            check(errorsExample(io), "CAPA 6");
            check(timeoutExample(io), "CAPA 7");
            check(checkoutExample(io), "CAPA 8");
        }
        System.out.println("CompletableFutureUsage: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) EL PROBLEMA: FUTURE.GET BLOQUEA UN HILO ESPERANDO
        =================================================================

        Con Future (ExecutorsUsage, CAPA 2), para usar un resultado hay que
        llamar a get(), que DETIENE el hilo hasta que llega. Para "pide el
        usuario, y cuando llegue, pide su carrito, y cuando llegue, calcula
        el total", el código queda así:

            User user = userFuture.get();               // hilo detenido
            Cart cart = pool.submit(() -> carts.of(user)).get();   // otra vez
            ...

        Cada get() tiene un hilo sentado sin hacer nada. Con hilos de
        plataforma (caros), eso limita cuántas peticiones a la vez atiende
        un servidor.

        CompletableFuture (Java 8) permite DESCRIBIR qué hacer cuando llegue
        el resultado, sin esperar:

            fetchUser(id)
                .thenCompose(user -> fetchCart(user))
                .thenApply(cart -> cart.total())
                .thenAccept(total -> responder(total));

        Cada paso se registra y corre cuando el anterior termina. Ningún
        hilo espera sentado. Es la misma idea que las promesas de
        JavaScript (then): un pipeline de pasos, como un stream
        (v7_functional/part2_streams/StreamsBasics), pero en el TIEMPO.
    */

    /*
        =================================================================
        CAPA 2) CREAR: SUPPLYASYNC, SIEMPRE CON TU PROPIO EXECUTOR
        =================================================================

            CompletableFuture.supplyAsync(() -> valor, executor)   tarea con resultado
            CompletableFuture.runAsync(() -> { ... }, executor)    sin resultado
            CompletableFuture.completedFuture(valor)               ya terminado
            new CompletableFuture<>() + future.complete(valor)     lo completas tú

        Pasa SIEMPRE el executor. Sin él, la tarea va al
        ForkJoinPool.commonPool(), compartido por toda la JVM y pensado para
        trabajo de CPU, con pocos hilos (núcleos - 1). Unas cuantas llamadas
        por red lentas lo llenan, y se frenan los streams paralelos y todo lo
        demás que lo usa (ExecutorsUsage, CAPA 4).

        complete() sirve para CONECTAR con código que avisa por callbacks:
        quien recibe el aviso completa el future, y quien lo esperaba sigue.

        Para obtener el resultado al final (en una prueba, o en main):
        join() espera como get(), pero lanza excepciones no checked
        (CompletionException en vez de ExecutionException).
    */
    static boolean createExample(ExecutorService io) {
        CompletableFuture<Integer> price = CompletableFuture.supplyAsync(() -> service(50, 4_500), io);

        CompletableFuture<String> fromCallback = new CompletableFuture<>();
        Thread.ofPlatform().start(() -> fromCallback.complete("pago aprobado"));   // "el callback"

        return price.join() == 4_500 && fromCallback.join().equals("pago aprobado");
    }

    /*
        =================================================================
        CAPA 3) TRANSFORMAR: THENAPPLY, THENACCEPT Y THENCOMPOSE
        =================================================================

            thenApply(x -> y)       transformar el resultado (como map)
            thenAccept(x -> ...)    usarlo, sin devolver nada
            thenRun(() -> ...)      hacer algo después, sin el resultado
            thenCompose(x -> cf)    el siguiente paso TAMBIÉN es asíncrono
                                    (como flatMap)

        La diferencia entre thenApply y thenCompose es la más confundida:

            fetchUser(1).thenApply(user -> fetchCart(user))
                -> CompletableFuture<CompletableFuture<Cart>>   (anidado: mal)
            fetchUser(1).thenCompose(user -> fetchCart(user))
                -> CompletableFuture<Cart>                      (plano: bien)

        Igual que map vs flatMap en Optional y en los streams
        (v7_functional/part2_streams/OptionalUsage). Regla: si la función
        devuelve un CompletableFuture, thenCompose.
    */
    record User(long id, String name) {
    }

    record Cart(long userId, List<Integer> pricesCents) {
        long totalCents() {
            return pricesCents.stream().mapToLong(Integer::longValue).sum();
        }
    }

    static CompletableFuture<User> fetchUser(long id, ExecutorService io) {
        return CompletableFuture.supplyAsync(() -> service(50, new User(id, "Ana")), io);
    }

    static CompletableFuture<Cart> fetchCart(User user, ExecutorService io) {
        return CompletableFuture.supplyAsync(() -> service(50, new Cart(user.id(), List.of(4_500, 12_000))), io);
    }

    static boolean transformExample(ExecutorService io) {
        AtomicReference<String> shown = new AtomicReference<>();
        CompletableFuture<Void> page = fetchUser(1, io)
                .thenCompose(user -> fetchCart(user, io))   // depende del usuario: asíncrono -> compose
                .thenApply(Cart::totalCents)                // transformar: apply
                .thenAccept(total -> shown.set("Total: " + total));   // usar: accept
        page.join();
        return "Total: 16500".equals(shown.get());
    }

    /*
        =================================================================
        CAPA 4) COMBINAR: THENCOMBINE, ALLOF Y ANYOF
        =================================================================

        Cuando dos llamadas NO dependen una de la otra, se lanzan A LA VEZ y
        se combinan al final:

            a.thenCombine(b, (ra, rb) -> ...)    dos resultados en uno
            CompletableFuture.allOf(f1, f2, f3)  termina cuando TODOS
                                                 terminan (devuelve Void:
                                                 los resultados se leen de
                                                 cada uno con join, que ya
                                                 no espera)
            CompletableFuture.anyOf(f1, f2)      termina con el PRIMERO

        El tiempo total es el de la MÁS LENTA, no la suma. El ejemplo
        combina tres servicios de 200 ms: en total ~200 ms, no 600.
    */
    static boolean combineExample(ExecutorService io) {
        long start = System.nanoTime();
        CompletableFuture<Integer> stock = CompletableFuture.supplyAsync(() -> service(200, 7), io);
        CompletableFuture<Integer> price = CompletableFuture.supplyAsync(() -> service(200, 4_500), io);
        CompletableFuture<String> shipping = CompletableFuture.supplyAsync(() -> service(200, "mañana"), io);

        CompletableFuture<String> summary = stock
                .thenCombine(price, (units, cents) -> units + " piezas a " + cents)
                .thenCombine(shipping, (text, when) -> text + ", llega " + when);
        String result = summary.join();

        CompletableFuture<Void> all = CompletableFuture.allOf(stock, price, shipping);
        all.join();
        int sum = stock.join() + price.join();              // ya terminaron: join no espera

        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        return result.equals("7 piezas a 4500, llega mañana") && sum == 4_507
                && elapsedMs < 500;                         // ~200 ms: en paralelo, no 600
    }

    /*
        =================================================================
        CAPA 5) ¿EN QUÉ HILO CORRE CADA PASO?
        =================================================================

        Una pregunta que importa (bloquear el hilo equivocado congela algo):

        - thenApply, thenAccept... (SIN "Async"): corren en el hilo que
          COMPLETÓ el paso anterior; o, si el future YA estaba terminado
          cuando registras el paso, en TU hilo, en ese momento.
        - thenApplyAsync(f, executor)...: corren en el executor que le das.

        Consecuencia: un paso lento o bloqueante (otra llamada por red,
        escribir un archivo) puesto con thenApply puede terminar corriendo
        en un hilo que no esperabas: el del pool de otro servicio, o el de
        quien atiende la petición. Si un paso bloquea, usa la versión Async
        con el executor adecuado.

        El ejemplo: el mismo thenApply corre en un hilo del pool "io"
        cuando el future termina después, y en main cuando ya estaba
        terminado.
    */
    static boolean whichThreadExample(ExecutorService io) {
        AtomicReference<String> laterThread = new AtomicReference<>();
        CompletableFuture.supplyAsync(() -> service(50, "dato"), io)
                .thenApply(value -> {
                    laterThread.set(Thread.currentThread().getName());   // el hilo que lo completó
                    return value;
                })
                .join();

        AtomicReference<String> alreadyDoneThread = new AtomicReference<>();
        CompletableFuture.completedFuture("dato")
                .thenApply(value -> {
                    alreadyDoneThread.set(Thread.currentThread().getName());   // ya estaba: el mío
                    return value;
                });

        AtomicReference<String> asyncThread = new AtomicReference<>();
        CompletableFuture.completedFuture("dato")
                .thenApplyAsync(value -> {
                    asyncThread.set(Thread.currentThread().getName());   // siempre el executor
                    return value;
                }, io)
                .join();

        return laterThread.get().startsWith("io-")
                && alreadyDoneThread.get().equals("main")
                && asyncThread.get().startsWith("io-");
    }

    /*
        =================================================================
        CAPA 6) ERRORES: EXCEPTIONALLY, HANDLE Y WHENCOMPLETE
        =================================================================

        Si un paso lanza una excepción, los siguientes NO corren: el error
        viaja por la cadena hasta que alguien lo maneja (como un try/catch
        que abarca todo el pipeline).

            exceptionally(error -> valorDeRespaldo)     recuperarse
            handle((resultado, error) -> ...)           ver los dos casos y
                                                        decidir el valor
            whenComplete((resultado, error) -> ...)     solo observar (un
                                                        log); NO cambia el
                                                        resultado ni el error

        Dentro de la cadena, el error llega ENVUELTO en una
        CompletionException; la real está en getCause(). Y al final:
            join() lanza CompletionException (no checked)
            get()  lanza ExecutionException  (checked)

        Y la misma trampa de submit (ExecutorsUsage, CAPA 8): una cadena
        que falla y que nadie une con join ni maneja con exceptionally
        pierde la excepción en silencio. Toda cadena termina en un manejo
        de error o en alguien que la espera.
    */
    static boolean errorsExample(ExecutorService io) {
        AtomicBoolean neverRan = new AtomicBoolean(true);
        AtomicReference<String> logged = new AtomicReference<>();

        String recovered = CompletableFuture
                .supplyAsync(() -> CompletableFutureUsage.<String>failingService("recomendaciones caído"), io)
                .thenApply(text -> {
                    neverRan.set(false);                    // no corre: hubo error antes
                    return text;
                })
                .whenComplete((result, error) -> logged.set(error.getCause().getMessage()))   // solo observa
                .exceptionally(error -> "sin recomendaciones")                                // se recupera
                .join();

        String unwrapped;
        try {
            CompletableFuture.supplyAsync(() -> CompletableFutureUsage.<String>failingService("pagos caído"), io).join();
            unwrapped = "";
        } catch (CompletionException e) {
            unwrapped = e.getCause().getMessage();          // la real, adentro
        }

        String handled = CompletableFuture.supplyAsync(() -> service(10, 5), io)
                .handle((value, error) -> error == null ? "ok: " + value : "error")
                .join();

        return recovered.equals("sin recomendaciones") && neverRan.get()
                && "recomendaciones caído".equals(logged.get())
                && unwrapped.equals("pagos caído") && handled.equals("ok: 5");
    }

    /*
        =================================================================
        CAPA 7) TIEMPOS LÍMITE (QUE NO DETIENEN EL TRABAJO)
        =================================================================

        Java 9 agregó:
            orTimeout(1, SECONDS)                  si no terminó, falla con
                                                   TimeoutException
            completeOnTimeout(valor, 1, SECONDS)   si no terminó, usa ese
                                                   valor de respaldo

        Ideal para servicios opcionales: "si las recomendaciones no llegan
        en 300 ms, muestra la página sin ellas".

        LO QUE NO HACEN: detener el trabajo. El future se da por terminado,
        pero la tarea sigue corriendo en su hilo hasta acabar, ocupándolo.
        Tampoco cancel(true): a diferencia de un Future de ExecutorService,
        en CompletableFuture NO interrumpe la tarea (la documentación lo
        dice: el parámetro no tiene efecto). Si la llamada lenta tiene que
        cortarse de verdad, se le pone su PROPIO tiempo límite (el cliente
        HTTP: v8_apis/part2_io_and_network/HttpClientUsage; la base de
        datos).

        El ejemplo: un servicio de 1 segundo con un límite de 100 ms. El
        future termina a los ~100 ms con el respaldo, y el servicio... sigue
        y termina después.
    */
    static boolean timeoutExample(ExecutorService io) {
        AtomicBoolean slowServiceFinished = new AtomicBoolean();
        long start = System.nanoTime();
        List<String> recommendations = CompletableFuture
                .supplyAsync(() -> {
                    List<String> result = service(1_000, List.of("taza", "filtro"));
                    slowServiceFinished.set(true);          // sigue corriendo aunque nadie lo espere
                    return result;
                }, io)
                .completeOnTimeout(List.of(), 100, TimeUnit.MILLISECONDS)   // respaldo: lista vacía
                .join();
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        boolean stillRunning = !slowServiceFinished.get();

        boolean failedWithTimeout;
        try {
            CompletableFuture.supplyAsync(() -> service(1_000, "x"), io)
                    .orTimeout(100, TimeUnit.MILLISECONDS)
                    .join();
            failedWithTimeout = false;
        } catch (CompletionException e) {
            failedWithTimeout = e.getCause() instanceof TimeoutException;
        }
        return recommendations.isEmpty() && elapsedMs < 600 && stillRunning && failedWithTimeout;
    }

    /*
        =================================================================
        CAPA 8) UN CASO COMPLETO: LA PÁGINA DE CHECKOUT
        =================================================================

        Para armar la página se necesitan:
        - el usuario (100 ms) y, con él, su carrito (100 ms): dependen;
        - el costo de envío (150 ms): independiente;
        - recomendaciones (opcional, a veces tarda 2 s): con límite de
          200 ms y respaldo vacío.

        En secuencia serían 100 + 100 + 150 + 2,000 = 2,350 ms. Con
        CompletableFuture, todo lo independiente va en paralelo, lo
        dependiente se encadena, y lo opcional tiene su límite: ~200 ms.
    */
    record CheckoutPage(String user, long totalCents, int shippingCents, List<String> recommendations) {
    }

    static boolean checkoutExample(ExecutorService io) {
        long start = System.nanoTime();
        CompletableFuture<User> user = CompletableFuture.supplyAsync(() -> service(100, new User(1, "Ana")), io);
        CompletableFuture<Cart> cart = user.thenCompose(u ->
                CompletableFuture.supplyAsync(() -> service(100, new Cart(u.id(), List.of(4_500, 12_000))), io));
        CompletableFuture<Integer> shipping = CompletableFuture.supplyAsync(() -> service(150, 9_900), io);
        CompletableFuture<List<String>> recommendations = CompletableFuture
                .supplyAsync(() -> service(2_000, List.of("taza")), io)
                .completeOnTimeout(List.of(), 200, TimeUnit.MILLISECONDS);

        CheckoutPage page = CompletableFuture.allOf(cart, shipping, recommendations)
                .thenApply(ignored -> new CheckoutPage(user.join().name(), cart.join().totalCents(),
                        shipping.join(), recommendations.join()))
                .join();
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        return page.equals(new CheckoutPage("Ana", 16_500, 9_900, List.of()))
                && elapsedMs < 800;                         // ~200 ms, no 2,350
    }

    /*
        =================================================================
        CAPA 9) ¿Y CON HILOS VIRTUALES?
        =================================================================

        CompletableFuture existe porque bloquear un hilo de plataforma es
        caro. Con hilos virtuales (part3_tasks/VirtualThreads), bloquear es
        barato, y el mismo flujo se puede escribir como código normal, de
        arriba hacia abajo:

            User user = fetchUser(1);           // bloquea... un hilo virtual: barato
            Cart cart = fetchCart(user);

        Más fácil de leer, de depurar (el stack trace es el de siempre) y
        de manejar errores (try/catch normal).

        ¿Entonces CompletableFuture sobra? No del todo:
        - para lanzar varias cosas EN PARALELO y combinarlas sigue siendo
          útil (aunque la concurrencia estructurada, en la siguiente guía,
          apunta a reemplazarlo también ahí);
        - muchas APIs lo devuelven (HttpClient.sendAsync, librerías de
          bases de datos y mensajería);
        - mucho código existente está escrito así.

        La recomendación actual: código bloqueante simple en hilos
        virtuales para el flujo normal; CompletableFuture cuando necesitas
        componer varias operaciones en paralelo o una API te lo da.
    */

    /*
        =================================================================
        CAPA 10) ERRORES COMUNES
        =================================================================

        1) supplyAsync sin executor: todo al commonPool (CAPA 2).

        2) thenApply con una función que devuelve un CompletableFuture:
           resultados anidados; era thenCompose (CAPA 3).

        3) Llamar join() a la mitad de la cadena, bloqueando, en vez de
           combinar (CAPA 4).

        4) Pasos lentos en thenApply sin Async: corren en un hilo
           inesperado (CAPA 5).

        5) Cadenas sin exceptionally ni join: errores perdidos; olvidar que
           el error llega envuelto (CAPA 6).

        6) Creer que orTimeout o cancel(true) detienen el trabajo (CAPA 7).

        7) Usar CompletableFuture para flujos secuenciales que con hilos
           virtuales serían código simple (CAPA 9).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - CompletableFuture describe "qué hacer cuando llegue" sin bloquear
          hilos.
        - supplyAsync/runAsync SIEMPRE con tu executor.
        - thenApply = map; thenCompose = flatMap (el siguiente paso también
          es asíncrono); thenAccept/thenRun para terminar.
        - thenCombine, allOf, anyOf: lo independiente en paralelo; tarda lo
          que la más lenta.
        - Sin Async, el paso corre en el hilo que completó (o en el tuyo);
          con Async, en el executor.
        - exceptionally/handle recuperan; whenComplete observa; el error
          llega en CompletionException.
        - orTimeout/completeOnTimeout terminan el future, no el trabajo.
        - Con hilos virtuales, el flujo simple se escribe bloqueante.
    */

    // -----------------------------------------------------------------
    // Utilidades internas de la guía.
    // -----------------------------------------------------------------

    /** Simula un servicio remoto que tarda. */
    private static <T> T service(long millis, T result) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrumpido", e);
        }
        return result;
    }

    private static <T> T failingService(String message) {
        service(20, null);
        throw new IllegalStateException(message);
    }

    private static ThreadFactory named(String prefix) {
        AtomicInteger number = new AtomicInteger();
        return task -> Thread.ofPlatform().name(prefix + "-" + number.incrementAndGet()).daemon(true).unstarted(task);
    }

    private static void check(boolean condition, String layer) {
        if (!condition) {
            throw new IllegalStateException("El ejemplo de la " + layer + " no hace lo que la guía explica");
        }
    }
}
