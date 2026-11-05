package v11_patterns.part3_behavioral;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.SubmissionPublisher;
import java.util.function.Consumer;

/*
    =====================================================================
    OBSERVER: AVISAR A QUIEN LE INTERESE, SIN CONOCERLO
    =====================================================================

    Ruta de patterns/: guía 19 de 28  (part3_behavioral)
    Anterior:  part3_behavioral/Memento
    Siguiente: part3_behavioral/State

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.

    Importancia: ALTA. Los "listeners" de cualquier interfaz gráfica, los
    eventos de los frameworks y la programación reactiva se basan en este
    patrón. Esta guía se enfoca en los errores reales que causa, que son
    los que más cuestan.

    Índice:
        CAPA 1)  El problema: un objeto que tiene que avisar a muchos
        CAPA 2)  La solución: suscribirse y recibir avisos
        CAPA 3)  Qué mandar en el aviso: el evento como record
        CAPA 4)  Darse de baja: la fuga de memoria del Observer
        CAPA 5)  Un suscriptor que falla no debe callar a los demás
        CAPA 6)  Darse de baja DURANTE un aviso
        CAPA 7)  Los avisos son síncronos (y el orden no está garantizado)
        CAPA 8)  Observer en el JDK: de los listeners a los streams reactivos
        CAPA 9)  Observer, Mediator y publicar/suscribir
        CAPA 10) Errores comunes
        RESUMEN FINAL
*/
public class Observer {

    public static void main(String[] args) throws Exception {
        check(observerExample(), "CAPA 2");
        check(unsubscribeExample(), "CAPA 4");
        check(failingObserverExample(), "CAPA 5");
        check(unsubscribeDuringNotificationExample(), "CAPA 6");
        check(reactiveExample(), "CAPA 8");
        System.out.println("Observer: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) EL PROBLEMA: UN OBJETO QUE TIENE QUE AVISAR A MUCHOS
        =================================================================

        Cuando cambia el precio de un producto, varias partes del sistema
        necesitan enterarse:
        - avisar a los clientes que lo tienen en su lista de deseos;
        - borrar el precio viejo de la caché;
        - registrarlo para el reporte de cambios de precio.

        Si el producto llama a cada una directamente, el producto CONOCE a
        todas: depende del correo, de la caché y de los reportes. Cada
        interesado nuevo (la app móvil quiere mandar una notificación push)
        obliga a modificar el producto (v10_design/part2_solid/OpenClosed).
        Y no se puede probar el producto sin todas esas piezas.

        Lo que se quiere: que el producto AVISE "mi precio cambió" sin saber
        a quién le importa, y que cada interesado se apunte solo.
    */

    /*
        =================================================================
        CAPA 2) LA SOLUCIÓN: SUSCRIBIRSE Y RECIBIR AVISOS
        =================================================================

        OBSERVER: "Define una dependencia uno-a-muchos entre objetos, de
        modo que cuando un objeto cambia de estado, todos sus dependientes
        son notificados automáticamente."

        - El SUJETO (lo que se observa, el producto) tiene una lista de
          OBSERVADORES y métodos para suscribir y dar de baja.
        - Cuando cambia, recorre la lista y llama a cada observador.
        - Cada OBSERVADOR implementa una interface pequeña. En Java moderno,
          basta un Consumer<Evento> (una lambda;
          v7_functional/part1_lambdas/FunctionalInterfaces).

        El producto depende SOLO de esa interface. Un interesado nuevo se
        suscribe desde afuera, sin tocar el producto.
    */
    record PriceChanged(String sku, BigDecimal oldPrice, BigDecimal newPrice) {   // EL EVENTO (CAPA 3)
        boolean isDrop() {
            return newPrice.compareTo(oldPrice) < 0;
        }
    }

    static final class Product {                            // SUJETO
        private final String sku;
        private BigDecimal price;
        private final List<Consumer<PriceChanged>> observers = new CopyOnWriteArrayList<>();   // CAPA 6

        Product(String sku, BigDecimal price) {
            this.sku = sku;
            this.price = price;
        }

        Subscription onPriceChange(Consumer<PriceChanged> observer) {
            observers.add(observer);
            return () -> observers.remove(observer);        // para darse de baja (CAPA 4)
        }

        List<RuntimeException> changePrice(BigDecimal newPrice) {
            PriceChanged event = new PriceChanged(sku, price, newPrice);
            price = newPrice;                               // primero el cambio, luego el aviso
            List<RuntimeException> failures = new ArrayList<>();
            for (Consumer<PriceChanged> observer : observers) {
                try {
                    observer.accept(event);
                } catch (RuntimeException e) {
                    failures.add(e);                        // CAPA 5: uno que falla no calla a los demás
                }
            }
            return failures;
        }

        int observerCount() {
            return observers.size();
        }
    }

    @FunctionalInterface
    interface Subscription extends AutoCloseable {
        @Override
        void close();                                       // sin excepciones checked: cerrar no falla
    }

    static boolean observerExample() {
        Product keyboard = new Product("KBD-1", new BigDecimal("500"));
        List<String> wishlistEmails = new ArrayList<>();
        List<String> cache = new ArrayList<>(List.of("KBD-1=500"));
        List<String> report = new ArrayList<>();

        keyboard.onPriceChange(event -> {                   // cada interesado se suscribe solo
            if (event.isDrop()) {
                wishlistEmails.add("¡" + event.sku() + " bajó a " + event.newPrice() + "!");
            }
        });
        keyboard.onPriceChange(event -> cache.remove(event.sku() + "=" + event.oldPrice()));
        keyboard.onPriceChange(event -> report.add(event.oldPrice() + " -> " + event.newPrice()));

        keyboard.changePrice(new BigDecimal("450"));        // el producto no conoce a ninguno

        return wishlistEmails.equals(List.of("¡KBD-1 bajó a 450!")) && cache.isEmpty() && report.equals(List.of("500 -> 450"));
    }

    /*
        =================================================================
        CAPA 3) QUÉ MANDAR EN EL AVISO: EL EVENTO COMO RECORD
        =================================================================

        Hay dos estilos para avisar:
        - PULL ("jalar"): el sujeto solo dice "cambié", y cada observador le
          PREGUNTA lo que necesita (product.price()). Problema: los
          observadores dependen de la interface completa del sujeto, y si el
          sujeto cambia otra vez antes de que pregunten, leen otro valor.
        - PUSH ("empujar"): el sujeto manda TODO lo necesario en el aviso.

        La forma recomendada es PUSH con un EVENTO INMUTABLE: un record que
        describe qué pasó, con los valores de ese momento (PriceChanged con
        el precio viejo y el nuevo). Así:
        - cada observador recibe exactamente lo que necesita, aunque el
          sujeto ya haya cambiado otra vez;
        - nadie puede alterar el evento que reciben los demás;
        - el evento se puede registrar, encolar o mandar a otro sistema
          tal cual (part3_behavioral/Command, CAPA 6).

        Nombra los eventos en PASADO (PriceChanged, OrderPlaced): describen
        algo que YA ocurrió, no una orden.
    */

    /*
        =================================================================
        CAPA 4) DARSE DE BAJA: LA FUGA DE MEMORIA DEL OBSERVER
        =================================================================

        El error MÁS común del patrón: el sujeto guarda una referencia a
        cada observador en su lista. Mientras el sujeto exista, sus
        observadores NO pueden ser recolectados por el recolector de basura,
        aunque nadie más los use.

        Ejemplo típico: una pantalla se suscribe a cambios de precio al
        abrirse y nunca se da de baja al cerrarse. Cada vez que se abre, se
        agrega otra suscripción; las pantallas "cerradas" siguen en memoria
        y siguen recibiendo (y procesando) avisos. A esto se le llama la
        fuga de memoria de los listeners ("lapsed listener"), y es una de
        las fugas más frecuentes en aplicaciones Java.

        La solución: suscribirse devuelve un "recibo" (Subscription) con el
        que darse de baja, y el ciclo de vida de quien observa debe
        cerrarlo. Como es AutoCloseable, si la suscripción vive dentro de un
        bloque, se usa try-with-resources (v3_exceptions/part2_in_practice/
        TryWithResources).
    */
    static boolean unsubscribeExample() {
        Product mouse = new Product("MOU-1", new BigDecimal("250"));
        List<String> screenUpdates = new ArrayList<>();

        for (int opened = 0; opened < 3; opened++) {        // la pantalla se abre y cierra 3 veces
            try (Subscription _ = mouse.onPriceChange(event -> screenUpdates.add("pantalla: " + event.newPrice()))) {
                mouse.changePrice(new BigDecimal(200 + opened));
            }                                               // al cerrar la pantalla: se da de baja
        }
        mouse.changePrice(new BigDecimal("199"));           // nadie debería recibir este

        return mouse.observerCount() == 0 && screenUpdates.size() == 3;   // sin suscripciones acumuladas
    }

    /*
        =================================================================
        CAPA 5) UN SUSCRIPTOR QUE FALLA NO DEBE CALLAR A LOS DEMÁS
        =================================================================

        Si el sujeto recorre la lista llamando a cada observador y uno lanza
        una excepción, el ciclo se corta: los observadores que venían
        DESPUÉS nunca se enteran. Peor: la excepción sube hasta quien
        cambió el precio, que no tiene nada que ver con el correo que falló.

        Por eso el sujeto atrapa las fallas de CADA observador por
        separado, sigue avisando a los demás, y reporta las fallas (aquí las
        devuelve; en un sistema real, las registra en el log;
        v3_exceptions/part2_in_practice/ErrorHandlingBestPractices, CAPA 7).
        El cambio de precio ya ocurrió y es válido: un aviso fallido no lo
        deshace.
    */
    static boolean failingObserverExample() {
        Product monitor = new Product("MON-1", new BigDecimal("3000"));
        List<String> received = new ArrayList<>();
        monitor.onPriceChange(event -> received.add("primero"));
        monitor.onPriceChange(event -> {
            throw new IllegalStateException("servidor de correo caído");   // un observador defectuoso
        });
        monitor.onPriceChange(event -> received.add("tercero"));

        List<RuntimeException> failures = monitor.changePrice(new BigDecimal("2800"));
        return received.equals(List.of("primero", "tercero"))           // el tercero sí se enteró
                && failures.size() == 1 && failures.get(0).getMessage().equals("servidor de correo caído");
    }

    /*
        =================================================================
        CAPA 6) DARSE DE BAJA DURANTE UN AVISO
        =================================================================

        Caso real: un observador quiere recibir SOLO el primer aviso ("dime
        cuando baje de precio, una vez") y se da de baja dentro de su propio
        manejador. Eso modifica la lista de observadores MIENTRAS el sujeto
        la está recorriendo. Con una ArrayList común:
            ConcurrentModificationException
        (v5_collections/part1_basics/Iterators, CAPA 5).

        La solución estándar es CopyOnWriteArrayList (java.util.concurrent):
        cada modificación crea una copia nueva del arreglo interno, y un
        recorrido en curso sigue usando la copia que tenía. Es ideal para
        listas de observadores: se recorren muchísimo y se modifican poco.
        Además es segura si varios hilos suscriben y avisan a la vez
        (v13_concurrency/). Es la lista que usa Product en la CAPA 2.

        El ejemplo muestra el error con ArrayList y el arreglo con
        CopyOnWriteArrayList.
    */
    static boolean unsubscribeDuringNotificationExample() {
        List<Runnable> plainList = new ArrayList<>();
        plainList.add(() -> plainList.remove(0));           // se da de baja durante el aviso
        plainList.add(() -> { });
        plainList.add(() -> { });                           // con solo 2, el error NO se detectaría (Iterators, CAPA 6)
        boolean plainFails;
        try {
            for (Runnable observer : plainList) {
                observer.run();
            }
            plainFails = false;
        } catch (ConcurrentModificationException e) {
            plainFails = true;
        }

        Product tablet = new Product("TAB-1", new BigDecimal("6000"));
        List<String> onceOnly = new ArrayList<>();
        Subscription[] holder = new Subscription[1];
        holder[0] = tablet.onPriceChange(event -> {
            onceOnly.add("avisado a " + event.newPrice());
            holder[0].close();                              // se da de baja DENTRO del aviso
        });
        tablet.changePrice(new BigDecimal("5500"));
        tablet.changePrice(new BigDecimal("5000"));         // ya no está suscrito

        return plainFails && onceOnly.equals(List.of("avisado a 5500")) && tablet.observerCount() == 0;
    }

    /*
        =================================================================
        CAPA 7) LOS AVISOS SON SÍNCRONOS (Y EL ORDEN NO ESTÁ GARANTIZADO)
        =================================================================

        En la forma básica, changePrice llama a cada observador UNO POR UNO
        y en el MISMO hilo, y no termina hasta que todos terminan:
        - si un observador tarda (manda un correo por la red: 300 ms), el
          cambio de precio tarda eso más; con diez observadores lentos, el
          sujeto se vuelve lento;
        - si un observador se cuelga, el sujeto se cuelga.

        Para observadores lentos, el aviso se hace ASÍNCRONO: el observador
        recibe el evento y lo entrega a una cola o a otro hilo para
        procesarlo después (part3_behavioral/Command, CAPA 5;
        v13_concurrency/). La CAPA 8 muestra cómo lo hace el JDK.

        Y no dependas del ORDEN en que se avisa: aunque la lista lo
        conserve, nada en el contrato del patrón lo promete, y en versiones
        asíncronas no se cumple. Si un observador necesita que otro haya
        terminado antes, no son observadores independientes: es un proceso
        con pasos, que se escribe explícito.
    */

    /*
        =================================================================
        CAPA 8) OBSERVER EN EL JDK: DE LOS LISTENERS A LOS STREAMS REACTIVOS
        =================================================================

        - LISTENERS: en cualquier interfaz gráfica, button.addActionListener
          (o el equivalente de cada librería) es este patrón: el botón es el
          sujeto, tu código el observador.

        - java.beans.PropertyChangeSupport: una clase del JDK que implementa
          la lista de observadores con avisos de "propiedad X cambió de A a
          B". La vas a ver en código existente.

        - java.util.Observable y Observer (las clases originales del JDK)
          están DEPRECADAS desde Java 9: no las uses.

        - java.util.concurrent.Flow y SubmissionPublisher (Java 9): la
          versión moderna y ASÍNCRONA. El publicador entrega cada elemento a
          sus suscriptores en OTROS hilos, y el suscriptor controla cuántos
          elementos puede recibir a la vez ("backpressure", como en
          v8_apis/part2_io_and_network/HttpClientUsage, CAPA 11, pero para
          eventos). Es la base de la PROGRAMACIÓN REACTIVA, un estilo para
          procesar flujos de eventos sin bloquear hilos (v15_spring/ tiene una
          versión reactiva).

        El ejemplo publica tres cambios de precio con SubmissionPublisher:
        se entregan en otro hilo. close() NO espera: solo avisa (también en
        otro hilo) que ya no habrá más; la espera está en done.get(), el
        futuro que devuelve consume.
    */
    static boolean reactiveExample() throws Exception {
        List<String> received = new CopyOnWriteArrayList<>();      // lo escribe OTRO hilo
        List<String> threads = new CopyOnWriteArrayList<>();
        SubmissionPublisher<PriceChanged> publisher = new SubmissionPublisher<>();
        CompletableFuture<Void> done = publisher.consume(event -> {   // el suscriptor, en otro hilo
            received.add(event.sku() + ":" + event.newPrice());
            threads.add(Thread.currentThread().getName());
        });
        try {
            publisher.submit(new PriceChanged("KBD-1", new BigDecimal("500"), new BigDecimal("450")));
            publisher.submit(new PriceChanged("KBD-1", new BigDecimal("450"), new BigDecimal("420")));
            publisher.submit(new PriceChanged("MOU-1", new BigDecimal("250"), new BigDecimal("230")));
        } finally {
            publisher.close();                              // "no habrá más eventos", pase lo que pase
        }
        done.get();                                         // esperar a que el suscriptor procese todos
        return received.equals(List.of("KBD-1:450", "KBD-1:420", "MOU-1:230"))
                && threads.stream().noneMatch(name -> name.equals("main"));   // se procesaron en otro hilo
    }

    /*
        =================================================================
        CAPA 9) OBSERVER, MEDIATOR Y PUBLICAR/SUSCRIBIR
        =================================================================

        | Forma                  | Quién conoce a quién                        | Ejemplo                       |
        |------------------------|---------------------------------------------|-------------------------------|
        | Observer               | el sujeto tiene la lista de sus observadores| Product y sus suscriptores    |
        |                        | y les avisa directo                         |                               |
        | Bus de eventos         | un intermediario reparte; publicador y      | part3_behavioral/Mediator,    |
        | (publicar/suscribir)   | suscriptor no se conocen ni la lista        | CAPA 4                        |
        | Mensajería entre       | lo mismo, pero entre procesos o máquinas,   | colas de mensajes             |
        | sistemas               | por la red                                  | (v14_architecture/)           |

        Es la misma idea a escalas distintas: algo pasó, y quien quiera,
        reacciona.
    */

    /*
        =================================================================
        CAPA 10) ERRORES COMUNES
        =================================================================

        1) El sujeto llamando directamente a cada interesado (CAPA 1).

        2) Avisos tipo "pull" o eventos mutables: los observadores leen
           datos que ya cambiaron (CAPA 3).

        3) Suscribirse sin darse de baja: fugas de memoria y avisos a
           pantallas cerradas (CAPA 4).

        4) Un observador que lanza una excepción y corta los avisos a los
           demás (CAPA 5).

        5) Usar ArrayList para la lista de observadores y darse de baja
           durante un aviso (CAPA 6).

        6) Observadores lentos que vuelven lento al sujeto, o depender del
           orden de los avisos (CAPA 7).

        7) Usar java.util.Observable (deprecada) (CAPA 8).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Observer: el sujeto mantiene una lista de observadores y les avisa
          cuando cambia, sin conocerlos (Consumer<Evento> basta).
        - Eventos PUSH, inmutables (records), nombrados en pasado.
        - Suscribirse devuelve un recibo para darse de baja (AutoCloseable):
          sin eso, fugas de memoria.
        - Atrapa la falla de cada observador por separado.
        - CopyOnWriteArrayList para la lista: soporta bajas durante el aviso
          y varios hilos.
        - Los avisos son síncronos; para lentos, asíncronos. No dependas del
          orden.
        - En el JDK: listeners, PropertyChangeSupport, Flow y
          SubmissionPublisher (reactivo, en otros hilos). Observable está
          deprecada.
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
