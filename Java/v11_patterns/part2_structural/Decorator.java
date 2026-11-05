package v11_patterns.part2_structural;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/*
    =====================================================================
    DECORATOR: AGREGAR COMPORTAMIENTO ENVOLVIENDO, SIN MODIFICAR
    =====================================================================

    Ruta de patterns/: guía 9 de 28  (part2_structural)
    Anterior:  part2_structural/Composite
    Siguiente: part2_structural/Facade

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.

    Importancia: ALTA. Caché, reintentos, registro de tiempos, validación,
    permisos: casi toda funcionalidad "transversal" se agrega con esta
    idea, a mano o a través de un framework.

    Índice:
        CAPA 1)  El problema: agregar comportamiento sin tocar la clase
        CAPA 2)  La solución: un objeto con la misma interface que envuelve
        CAPA 3)  Apilar decoradores (y por qué el orden importa)
        CAPA 4)  Un decorador de reintentos
        CAPA 5)  Lo que el decorador oculta: identidad y tipo concreto
        CAPA 6)  Decoradores en el JDK
        CAPA 7)  Decoradores funcionales: envolver una función
        CAPA 8)  Decoradores automáticos en los frameworks
        CAPA 9)  Decorator, herencia, Adapter y Proxy
        CAPA 10) Errores comunes
        RESUMEN FINAL
*/
public class Decorator {

    public static void main(String[] args) throws IOException {
        check(cachingExample(), "CAPA 2");
        check(orderMattersExample(), "CAPA 3");
        check(retryExample(), "CAPA 4");
        check(identityExample(), "CAPA 5");
        check(jdkExample(), "CAPA 6");
        check(functionalExample(), "CAPA 7");
        System.out.println("Decorator: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) EL PROBLEMA: AGREGAR COMPORTAMIENTO SIN TOCAR LA CLASE
        =================================================================

        La tienda consulta precios a un servicio de catálogo que es LENTO
        (otro sistema, por la red). Aparecen necesidades:
        - una CACHÉ, para no preguntar dos veces lo mismo;
        - REINTENTOS, porque a veces falla por un momento;
        - REGISTRAR cuántas consultas se hacen y cuánto tardan.

        Ninguna de esas cosas es la responsabilidad del cliente del
        catálogo (su trabajo es consultar precios; v10_design/part2_solid/
        SingleResponsibility). Opciones malas:
        - Meterlas DENTRO de la clase: la llenas de cosas que no son suyas,
          y no siempre quieres todas (en pruebas, sin caché).
        - HEREDAR: CachedCatalog extends RemoteCatalog,
          CachedLoggedCatalog extends CachedCatalog... Una subclase por
          COMBINACIÓN (la explosión de part2_structural/Bridge), el orden
          queda fijo, y no se puede si la clase es final o de una librería.

        Lo que se quiere: agregar y quitar cada comportamiento por
        separado, combinarlos como piezas, sin tocar la clase original.
    */
    interface PriceCatalog {                                // COMPONENT: la interface común
        BigDecimal priceOf(String sku);
    }

    static final class RemoteCatalog implements PriceCatalog {   // CONCRETE COMPONENT: el objeto real
        private final Map<String, BigDecimal> prices = Map.of(
                "KBD-1", new BigDecimal("500"), "MOU-1", new BigDecimal("250"));
        int remoteCalls;                                    // para que la guía pueda contar las llamadas "caras"

        @Override
        public BigDecimal priceOf(String sku) {
            remoteCalls++;                                  // imagina una petición HTTP de 300 ms
            BigDecimal price = prices.get(sku);
            if (price == null) {
                throw new IllegalArgumentException("SKU desconocido: " + sku);
            }
            return price;
        }
    }

    /*
        =================================================================
        CAPA 2) LA SOLUCIÓN: UN OBJETO CON LA MISMA INTERFACE QUE ENVUELVE
        =================================================================

        DECORATOR: "Agrega responsabilidades a un objeto de forma dinámica.
        Es una alternativa flexible a la herencia para extender
        funcionalidad."

        - El decorador IMPLEMENTA LA MISMA interface que el objeto real
          (PriceCatalog).
        - CONTIENE otro PriceCatalog (el "envuelto", o delegado).
        - Cada método hace su trabajo extra ANTES y/o DESPUÉS de delegar
          en el envuelto.

        Como tiene la misma interface, quien lo usa NO SE ENTERA de que hay
        un decorador: recibe "un PriceCatalog". Y el objeto real tampoco
        se entera de que lo envuelven.

        El ejemplo: una caché. La primera consulta de cada producto llega
        al catálogo remoto; las siguientes se responden desde memoria.
    */
    static final class CachingCatalog implements PriceCatalog {   // DECORATOR
        private final PriceCatalog delegate;                // el envuelto
        private final Map<String, BigDecimal> cache = new HashMap<>();

        CachingCatalog(PriceCatalog delegate) {
            this.delegate = delegate;
        }

        @Override
        public BigDecimal priceOf(String sku) {
            return cache.computeIfAbsent(sku, delegate::priceOf);   // solo delega si no lo tiene
        }
    }

    static boolean cachingExample() {
        RemoteCatalog remote = new RemoteCatalog();
        PriceCatalog catalog = new CachingCatalog(remote);  // quien lo usa solo ve un PriceCatalog
        for (int i = 0; i < 100; i++) {
            catalog.priceOf("KBD-1");
        }
        catalog.priceOf("MOU-1");
        return remote.remoteCalls == 2;                     // 101 consultas, 2 llamadas reales
    }

    /*
        =================================================================
        CAPA 3) APILAR DECORADORES (Y POR QUÉ EL ORDEN IMPORTA)
        =================================================================

        Como un decorador ES un PriceCatalog, se puede envolver con OTRO
        decorador, como capas de una cebolla:

            PriceCatalog catalog = new CountingCatalog(new CachingCatalog(new RemoteCatalog()));

        La llamada entra por la capa de afuera y va bajando; la respuesta
        sube en orden inverso. Cada capa es una pieza independiente: se
        agrega, se quita o se reordena cambiando UNA línea donde se arma
        (el composition root; v10_design/part3_responsibilities/
        DependencyInjection, CAPA 4).

        EL ORDEN CAMBIA EL RESULTADO. Un contador de consultas:
        - AFUERA de la caché: cuenta TODAS las consultas que hace la
          aplicación (para saber cuánto se usa el catálogo).
        - ADENTRO de la caché: cuenta solo las que llegan al remoto (las
          que la caché no pudo responder).
        Las dos son útiles; responden preguntas distintas. Al apilar,
        decide a propósito qué ve cada capa.
    */
    static final class CountingCatalog implements PriceCatalog {
        private final PriceCatalog delegate;
        final List<String> requested = new ArrayList<>();

        CountingCatalog(PriceCatalog delegate) {
            this.delegate = delegate;
        }

        @Override
        public BigDecimal priceOf(String sku) {
            requested.add(sku);                             // ANTES de delegar
            return delegate.priceOf(sku);
        }
    }

    static boolean orderMattersExample() {
        CountingCatalog outside = new CountingCatalog(new CachingCatalog(new RemoteCatalog()));
        CountingCatalog inside = new CountingCatalog(new RemoteCatalog());
        PriceCatalog cachedOverCounter = new CachingCatalog(inside);

        for (String sku : List.of("KBD-1", "KBD-1", "KBD-1", "MOU-1")) {
            outside.priceOf(sku);
            cachedOverCounter.priceOf(sku);
        }
        return outside.requested.size() == 4                // afuera: todas las consultas de la aplicación
                && inside.requested.size() == 2;            // adentro: solo las que la caché no respondió
    }

    /*
        =================================================================
        CAPA 4) UN DECORADOR DE REINTENTOS
        =================================================================

        Otro decorador clásico: si el envuelto falla con un error
        TEMPORAL, reintentar unas cuantas veces antes de rendirse
        (v8_apis/part2_io_and_network/HttpClientUsage, CAPA 11: reintentar
        solo lo idempotente, con límite). Consultar un precio es
        idempotente, así que se puede.

        El catálogo real no cambia: no sabe que lo reintentan. Y el
        decorador no sabe nada de precios: se podría escribir genérico para
        cualquier operación (CAPA 7).
    */
    static final class FlakyCatalog implements PriceCatalog {   // falla las primeras veces, como un servicio inestable
        private int failuresLeft;

        FlakyCatalog(int failures) {
            this.failuresLeft = failures;
        }

        @Override
        public BigDecimal priceOf(String sku) {
            if (failuresLeft > 0) {
                failuresLeft--;
                throw new IllegalStateException("Servicio no disponible");
            }
            return new BigDecimal("500");
        }
    }

    static final class RetryingCatalog implements PriceCatalog {
        private final PriceCatalog delegate;
        private final int maxAttempts;
        int attempts;

        RetryingCatalog(PriceCatalog delegate, int maxAttempts) {
            this.delegate = delegate;
            this.maxAttempts = maxAttempts;
        }

        @Override
        public BigDecimal priceOf(String sku) {
            IllegalStateException last = null;
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                attempts++;
                try {
                    return delegate.priceOf(sku);
                } catch (IllegalStateException temporary) { // solo errores temporales; un SKU inválido no se reintenta
                    last = temporary;
                }
            }
            throw new IllegalStateException("Falló después de " + maxAttempts + " intentos", last);
        }
    }

    static boolean retryExample() {
        RetryingCatalog recovers = new RetryingCatalog(new FlakyCatalog(2), 3);
        boolean recovered = recovers.priceOf("KBD-1").compareTo(new BigDecimal("500")) == 0 && recovers.attempts == 3;

        boolean givesUp;
        try {
            new RetryingCatalog(new FlakyCatalog(5), 3).priceOf("KBD-1");
            givesUp = false;
        } catch (IllegalStateException e) {
            givesUp = e.getMessage().equals("Falló después de 3 intentos")
                    && e.getCause().getMessage().equals("Servicio no disponible");   // la causa real, conservada
        }
        return recovered && givesUp;
    }

    /*
        =================================================================
        CAPA 5) LO QUE EL DECORADOR OCULTA: IDENTIDAD Y TIPO CONCRETO
        =================================================================

        Un decorador es OTRO objeto. Consecuencias que conviene conocer:
        - decorado != original: si algún código compara con == o guarda el
          objeto en un Set, el decorado y el original son cosas distintas.
        - El tipo concreto se pierde: un CachingCatalog NO es un
          RemoteCatalog. Si algún código hace instanceof RemoteCatalog o un
          cast, deja de funcionar al decorar. Es otra razón para depender
          siempre de la INTERFACE (v10_design/part2_solid/
          DependencyInversion).
        - Solo se decoran los métodos de la INTERFACE: si RemoteCatalog
          tuviera un método extra que no está en PriceCatalog, a través
          del decorador ya no se ve.
    */
    static boolean identityExample() {
        RemoteCatalog real = new RemoteCatalog();
        PriceCatalog decorated = new CachingCatalog(real);
        return decorated != real
                && !(decorated instanceof RemoteCatalog)    // el tipo concreto quedó escondido
                && decorated.priceOf("KBD-1").equals(real.priceOf("KBD-1"));   // pero se comporta como un PriceCatalog
    }

    /*
        =================================================================
        CAPA 6) DECORADORES EN EL JDK
        =================================================================

        1) LOS STREAMS DE E/S, el ejemplo más famoso del patrón:

            new BufferedReader(new InputStreamReader(new FileInputStream("datos.txt"), UTF_8))

           - FileInputStream: el objeto real, lee bytes del archivo (cada
             read es una llamada al sistema).
           - BufferedInputStream / BufferedReader: DECORADORES que agregan un
             buffer (menos llamadas al sistema; v8_apis/part2_io_and_network/
             FilesAndIO, CAPA 10) y, el de texto, leer por líneas.
           - InputStreamReader, en medio, NO es un decorador: es un
             ADAPTADOR (cambia de bytes a caracteres; part2_structural/
             Adapter, CAPA 7). Una pila real suele mezclar los dos patrones.

        2) LAS VISTAS DE Collections: unmodifiableList agrega la restricción
           "no se puede modificar", synchronizedList agrega un candado en
           cada método, checkedList agrega la verificación de tipos
           (v5_collections/part4_tools/CollectionsAndArraysUtilities,
           CAPA 5). Las tres envuelven una List y SON una List.

        El ejemplo arma un BufferedReader sobre un StringReader (un
        Reader que lee de un String en memoria) y una vista no modificable.
    */
    static boolean jdkExample() throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new StringReader("uno\ndos\ntres"))) {   // decorador sobre un Reader
            String line;
            while ((line = reader.readLine()) != null) {    // readLine: lo que agrega el decorador
                lines.add(line);
            }
        }

        List<String> internal = new ArrayList<>(List.of("a"));
        List<String> readOnly = Collections.unmodifiableList(internal);   // decora agregando una restricción
        boolean restricted;
        try {
            readOnly.add("b");
            restricted = false;
        } catch (UnsupportedOperationException e) {
            restricted = true;
        }
        return lines.equals(List.of("uno", "dos", "tres")) && restricted && readOnly.get(0).equals("a");
    }

    /*
        =================================================================
        CAPA 7) DECORADORES FUNCIONALES: ENVOLVER UNA FUNCIÓN
        =================================================================

        Si la interface es FUNCIONAL, el decorador puede ser un MÉTODO que
        recibe una función y devuelve OTRA función que hace algo extra y
        llama a la original. Se escribe una vez y sirve para cualquier
        función del mismo tipo:

            static <T, R> Function<T, R> memoized(Function<T, R> function)   // caché genérica
            static <T, R> Function<T, R> counted(Function<T, R> function, List<T> log)

        Y se apilan igual que los de clase: memoized(counted(original)).

        Ya conoces uno del JDK: function.andThen(otra) devuelve una función
        nueva que hace algo DESPUÉS (v7_functional/part1_lambdas/
        FunctionalInterfaces, CAPA 7).

        Es la forma más corta cuando el comportamiento extra es simple; para
        decoradores con estado o configuración (reintentos con varios
        parámetros), una clase se lee mejor.
    */
    static <T, R> Function<T, R> memoized(Function<T, R> function) {
        Map<T, R> cache = new HashMap<>();
        return input -> cache.computeIfAbsent(input, function);   // la función nueva envuelve a la original
    }

    static <T, R> Function<T, R> counted(Function<T, R> function, List<T> log) {
        return input -> {
            log.add(input);
            return function.apply(input);
        };
    }

    static boolean functionalExample() {
        List<String> realCalls = new ArrayList<>();
        Function<String, BigDecimal> slowLookup = sku -> new RemoteCatalog().priceOf(sku);
        Function<String, BigDecimal> fast = memoized(counted(slowLookup, realCalls));   // apilados

        fast.apply("KBD-1");
        fast.apply("KBD-1");
        fast.apply("MOU-1");

        Function<String, String> withVat = memoized(fast.andThen(price -> "$" + price.multiply(new BigDecimal("1.16"))));
        return realCalls.equals(List.of("KBD-1", "MOU-1"))  // la caché evitó la segunda llamada
                && withVat.apply("KBD-1").equals("$580.00");
    }

    /*
        =================================================================
        CAPA 8) DECORADORES AUTOMÁTICOS EN LOS FRAMEWORKS
        =================================================================

        En Spring (v15_spring/), muchas funcionalidades se activan con una
        anotación sobre un método:
            @Cacheable      guardar el resultado en caché
            @Transactional  abrir y cerrar una transacción de base de datos
            @Retryable      reintentar si falla
        Por dentro, Spring hace exactamente lo de esta guía: cuando te
        inyecta el objeto, en realidad te da un ENVOLTORIO con la misma
        interface que agrega ese comportamiento antes y después de llamar a
        tu método. Lo genera automáticamente al arrancar (con la técnica de
        part2_structural/Proxy).

        Entender el decorador explica errores típicos de esos frameworks:
        por ejemplo, que @Transactional no funcione si un método llama a
        otro de la MISMA clase. La llamada interna no pasa por el
        envoltorio: va directo al objeto real.
    */

    /*
        =================================================================
        CAPA 9) DECORATOR, HERENCIA, ADAPTER Y PROXY
        =================================================================

        | Comparado con | Diferencia                                                    |
        |---------------|---------------------------------------------------------------|
        | Herencia      | se combina al EJECUTAR y en cualquier orden; no multiplica     |
        |               | clases; funciona con clases final y de terceros                |
        | Adapter       | Adapter CAMBIA la interface; Decorator conserva la MISMA y     |
        |               | AGREGA comportamiento                                          |
        | Proxy         | misma estructura; Proxy CONTROLA el acceso (permisos, carga    |
        |               | diferida, llamada remota) en vez de agregar funcionalidad.     |
        |               | En la práctica la línea es borrosa, y los frameworks usan      |
        |               | "proxies" para hacer decoradores (CAPA 8)                      |
        | Composite     | también envuelve, pero a VARIOS hijos para formar un árbol;    |
        |               | el decorador envuelve a UNO                                    |
    */

    /*
        =================================================================
        CAPA 10) ERRORES COMUNES
        =================================================================

        1) Meter caché, reintentos o logs dentro de la clase que hace el
           trabajo principal (CAPA 1).

        2) Subclases por cada combinación de comportamientos (CAPA 1).

        3) Apilar sin pensar el ORDEN: un contador o una caché en la capa
           equivocada miden o guardan otra cosa (CAPA 3).

        4) Reintentar errores que no son temporales, o sin límite (CAPA 4).

        5) Código que depende del tipo concreto (instanceof, casts) y se
           rompe al decorar (CAPA 5).

        6) Esperar que el comportamiento de un decorador de framework
           (@Transactional, @Cacheable) aplique a llamadas internas de la
           misma clase (CAPA 8).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Decorator: un objeto con la MISMA interface que envuelve a otro y
          agrega comportamiento antes/después de delegar.
        - Ni el que lo usa ni el envuelto se enteran.
        - Se apilan como capas; el orden decide qué ve cada una.
        - Clásicos: caché, reintentos, conteo/tiempos, validación,
          restricciones.
        - El decorado es otro objeto: depende de la interface, no del tipo
          concreto.
        - En el JDK: BufferedReader/BufferedInputStream y las vistas de
          Collections.
        - Funcional: un método que recibe una función y devuelve otra.
        - Los frameworks (Spring) generan decoradores automáticamente para
          @Transactional, @Cacheable...
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
