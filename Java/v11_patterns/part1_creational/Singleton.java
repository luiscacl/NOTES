package v11_patterns.part1_creational;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

/*
    =====================================================================
    SINGLETON: UNA INSTANCIA SÍ, ACCESO GLOBAL NO
    =====================================================================

    Ruta de patterns/: guía 5 de 28  (part1_creational)
    Anterior:  part1_creational/Prototype
    Siguiente: part2_structural/Adapter  (última de part1_creational/)

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.

    Importancia: ALTA para reconocerlo y saber por qué evitar su forma
    clásica (es una pregunta típica de entrevista); BAJA para escribirlo:
    hoy casi siempre se reemplaza por una instancia inyectada.

    Qué se da por sabido: static (v2_oop/part1_basics/Static, CAPA 10:
    cuándo NO usar static), el singleton con enum (v2_oop/
    part4_advanced_types/Enums, CAPA 10) y la inyección de dependencias
    (v10_design/part3_responsibilities/DependencyInjection, CAPAS 5 y 10).

    Índice:
        CAPA 1)  Qué promete el Singleton: dos cosas distintas
        CAPA 2)  La forma clásica, que vas a encontrar en código existente
        CAPA 3)  El problema del acceso global, demostrado
        CAPA 4)  Lo que se usa hoy: una instancia, inyectada
        CAPA 5)  Una instancia compartida debe ser segura con hilos
        CAPA 6)  Cuándo sí es legítimo un único objeto en toda la JVM
        CAPA 7)  Errores comunes
        RESUMEN FINAL
*/
public class Singleton {

    public static void main(String[] args) {
        check(classicSingletonExample(), "CAPA 2");
        check(globalStateLeakExample(), "CAPA 3");
        check(injectedInstanceExample(), "CAPA 4");
        check(legitimateSingletonsExample(), "CAPA 6");
        System.out.println("Singleton: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) QUÉ PROMETE EL SINGLETON: DOS COSAS DISTINTAS
        =================================================================

        El libro GoF lo define así: "Asegura que una clase tenga UNA SOLA
        INSTANCIA y proporciona un PUNTO DE ACCESO GLOBAL a ella."

        Son DOS promesas, y conviene separarlas porque una es útil y la otra
        es la que causa los problemas:

        1) UNA SOLA INSTANCIA: legítimo y muy común. Hay objetos de los que
           conviene tener uno: el pool de conexiones a la base de datos, el
           HttpClient (v8_apis/part2_io_and_network/HttpClientUsage,
           CAPA 7), una caché, la configuración ya leída.

        2) ACCESO GLOBAL: cualquier clase, desde cualquier lugar, lo obtiene
           con Clase.getInstance(), sin recibirlo. Esto es estado global
           (v10_design/part1_basics/BasicPrinciples, CAPA 3) y una
           dependencia escondida (DependencyInjection, CAPA 10).

        La solución moderna se queda con la primera promesa y descarta la
        segunda (CAPA 4).
    */

    /*
        =================================================================
        CAPA 2) LA FORMA CLÁSICA, QUE VAS A ENCONTRAR EN CÓDIGO EXISTENTE
        =================================================================

        Para reconocerla:
        - constructor PRIVADO: nadie más puede crear instancias;
        - un campo static con la única instancia;
        - un método static getInstance() que la devuelve.

            ExchangeRates.getInstance().rateFor("USD")

        Esta versión crea la instancia al cargar la clase (inicialización
        "ansiosa"). La JVM garantiza que la carga de una clase ocurre una
        sola vez y de forma segura entre hilos, así que no necesita nada
        más. (Las variantes "perezosas" con candados que verás en
        artículos viejos no hacen falta: si de verdad debe ser un singleton
        global, la forma recomendada es un enum; Enums, CAPA 10.)
    */
    static final class ExchangeRates {                      // SINGLETON CLÁSICO
        private static final ExchangeRates INSTANCE = new ExchangeRates();
        private final Map<String, BigDecimal> rates = new HashMap<>(Map.of("USD", new BigDecimal("17.00")));

        private ExchangeRates() { }                         // nadie más puede crear otra

        static ExchangeRates getInstance() {
            return INSTANCE;
        }

        BigDecimal rateFor(String currency) {
            return rates.get(currency);
        }

        void update(String currency, BigDecimal rate) {     // estado MUTABLE y global...
            rates.put(currency, rate);
        }
    }

    static boolean classicSingletonExample() {
        return ExchangeRates.getInstance() == ExchangeRates.getInstance()      // siempre la misma
                && ExchangeRates.getInstance().rateFor("USD").compareTo(new BigDecimal("17.00")) == 0;
    }

    /*
        =================================================================
        CAPA 3) EL PROBLEMA DEL ACCESO GLOBAL, DEMOSTRADO
        =================================================================

        Una clase que hace ExchangeRates.getInstance() por dentro:
        1) ESCONDE su dependencia: su constructor no dice que necesita
           tipos de cambio; hay que leer todo su código para saberlo.
        2) NO SE PUEDE PROBAR con otros valores: siempre usa EL global. Para
           probar "precio en dólares con el dólar a 20", la prueba tiene
           que MODIFICAR el global...
        3) ...y ese cambio se FILTRA a todo lo demás que corra después en el
           mismo proceso: otras pruebas, u otras peticiones en un servidor.
           Pruebas que pasan solas y fallan juntas, según el orden.

        El ejemplo lo reproduce: la "prueba A" cambia el dólar a 20 y no lo
        restaura; la "prueba B", que no tiene nada que ver, ahora obtiene
        otro resultado.
    */
    static final class PriceConverterGlobal {               // su dependencia no se ve en ninguna firma
        BigDecimal toPesos(BigDecimal dollars) {
            return dollars.multiply(ExchangeRates.getInstance().rateFor("USD"));
        }
    }

    static boolean globalStateLeakExample() {
        BigDecimal before = new PriceConverterGlobal().toPesos(BigDecimal.TEN);

        ExchangeRates.getInstance().update("USD", new BigDecimal("20.00"));     // "prueba A" ajusta el global...

        BigDecimal after = new PriceConverterGlobal().toPesos(BigDecimal.TEN); // "prueba B" recibe otro resultado
        ExchangeRates.getInstance().update("USD", new BigDecimal("17.00"));     // (limpiar a mano: fácil de olvidar)

        return before.compareTo(new BigDecimal("170.00")) == 0 && after.compareTo(new BigDecimal("200.00")) == 0;
    }

    /*
        =================================================================
        CAPA 4) LO QUE SE USA HOY: UNA INSTANCIA, INYECTADA
        =================================================================

        La solución moderna conserva "una sola instancia" y elimina el
        acceso global:
        - La clase es NORMAL: constructor accesible, sin static, sin
          getInstance.
        - El composition root crea UNA instancia al arrancar
          (DependencyInjection, CAPA 4) y se la PASA a quien la necesita.
          Todos comparten la misma porque solo se creó una, no porque la
          clase lo impida.
        - Es exactamente el comportamiento por defecto de Spring: cada
          objeto que administra existe UNA vez por aplicación (scope
          "singleton"), y se inyecta.

        Resultado: la dependencia se ve en el constructor, cada prueba crea
        su PROPIA instancia con los valores que quiera, y no hay nada global
        que se filtre entre pruebas.
    */
    interface RateProvider {
        BigDecimal rateFor(String currency);
    }

    static final class FixedRates implements RateProvider {   // una clase normal
        private final Map<String, BigDecimal> rates;

        FixedRates(Map<String, BigDecimal> rates) {
            this.rates = Map.copyOf(rates);
        }

        @Override
        public BigDecimal rateFor(String currency) {
            return rates.get(currency);
        }
    }

    static final class PriceConverter {
        private final RateProvider rates;                   // la dependencia, a la vista

        PriceConverter(RateProvider rates) {
            this.rates = rates;
        }

        BigDecimal toPesos(BigDecimal dollars) {
            return dollars.multiply(rates.rateFor("USD"));
        }
    }

    static final class Reports {
        private final RateProvider rates;

        Reports(RateProvider rates) {
            this.rates = rates;
        }

        String usdLine() {
            return "USD: " + rates.rateFor("USD");
        }
    }

    static boolean injectedInstanceExample() {
        // El composition root: UNA instancia, compartida por quien la necesite.
        RateProvider shared = new FixedRates(Map.of("USD", new BigDecimal("17.00")));
        PriceConverter converter = new PriceConverter(shared);
        Reports reports = new Reports(shared);

        // Cada "prueba" con su propia instancia: nada global que limpiar.
        PriceConverter strongDollar = new PriceConverter(new FixedRates(Map.of("USD", new BigDecimal("20.00"))));

        return converter.toPesos(BigDecimal.TEN).compareTo(new BigDecimal("170.00")) == 0
                && strongDollar.toPesos(BigDecimal.TEN).compareTo(new BigDecimal("200.00")) == 0
                && converter.toPesos(BigDecimal.TEN).compareTo(new BigDecimal("170.00")) == 0   // no se contaminó
                && reports.usdLine().equals("USD: 17.00");
    }

    /*
        =================================================================
        CAPA 5) UNA INSTANCIA COMPARTIDA DEBE SER SEGURA CON HILOS
        =================================================================

        Inyectada o global, una instancia única la usan MUCHOS hilos a la
        vez: en un servidor web, cada petición corre en su propio hilo, y
        todas llaman al mismo objeto. Por eso un objeto compartido debe ser:

        1) SIN ESTADO que cambie (lo ideal): solo lee su configuración y
           trabaja con los parámetros. Los servicios típicos son así.
        2) INMUTABLE: su estado se fija al crearlo (como FixedRates, que
           copia el mapa con Map.copyOf).
        3) O, si de verdad necesita estado cambiante (una caché, un
           contador), usar estructuras seguras con hilos
           (ConcurrentHashMap, AtomicInteger; v13_concurrency/).

        El ExchangeRates clásico de la CAPA 2 falla aquí también: usa un
        HashMap que se modifica con update(). Si un hilo actualiza mientras
        otros leen, el HashMap puede corromperse. "Una sola instancia" y
        "estado mutable sin protección" es una combinación peligrosa,
        venga de donde venga.
    */

    /*
        =================================================================
        CAPA 6) CUÁNDO SÍ ES LEGÍTIMO UN ÚNICO OBJETO EN TODA LA JVM
        =================================================================

        Hay casos donde un único objeto global es correcto, porque no tiene
        estado que cambie o porque representa algo que de verdad es único:

        1) CONSTANTES INMUTABLES: static final de objetos inmutables
           (BigDecimal.ZERO, List.of() vacía, Collections.emptyList()). No
           tienen estado que se filtre.

        2) ENUMS: cada constante de un enum es una instancia única que la
           JVM garantiza (incluso frente a reflexión y serialización). Si
           necesitas un objeto único y sin estado que implemente una
           interface (una estrategia fija), un enum es la forma correcta
           (Enums, CAPA 10).

        3) LO QUE REPRESENTA ALGO ÚNICO DEL PROCESO: Runtime.getRuntime()
           es un singleton del JDK, y es legítimo porque representa AL
           PROCESO de la JVM en el que corre tu programa, y de ese hay
           exactamente uno. A través de él preguntas cosas que el sistema
           operativo sabe del proceso: cuántos núcleos puede usar
           (availableProcessors), cuánta memoria tiene el heap (totalMemory,
           maxMemory), o registrar código que corra cuando el proceso
           termine (addShutdownHook). Lo mismo con System.out: el proceso
           tiene una sola salida estándar (su descriptor 1;
           v8_apis/part2_io_and_network/FilesAndIO, CAPA 2).

        Fíjate en el patrón: en los tres casos no hay estado mutable de TU
        aplicación escondido en un global.
    */
    enum CurrencyRounding {                                 // instancia única garantizada por la JVM, sin estado
        INSTANCE;

        BigDecimal round(BigDecimal amount) {
            return amount.setScale(2, RoundingMode.HALF_EVEN);
        }
    }

    static boolean legitimateSingletonsExample() {
        Runtime process = Runtime.getRuntime();             // UNO por proceso de la JVM
        int cores = process.availableProcessors();

        return process == Runtime.getRuntime()
                && cores >= 1                                // núcleos que el sistema operativo le deja usar
                && process.maxMemory() >= process.totalMemory()
                && CurrencyRounding.INSTANCE.round(new BigDecimal("10.125")).compareTo(new BigDecimal("10.12")) == 0;
    }

    /*
        =================================================================
        CAPA 7) ERRORES COMUNES
        =================================================================

        1) Confundir "necesito una instancia" con "necesito acceso global"
           (CAPA 1).

        2) Clases de lógica que llaman getInstance() por dentro:
           dependencias escondidas e imposibles de reemplazar en pruebas
           (CAPA 3).

        3) Pruebas que modifican un singleton y no lo restauran: fallan
           según el orden (CAPA 3).

        4) Singletons con estado mutable sin protección para hilos
           (CAPA 5).

        5) Crear un "singleton" para algo que solo necesita ser una
           constante o un método estático sin estado.

        6) Escribir un Singleton clásico en código nuevo cuando basta crear
           una instancia e inyectarla (CAPA 4).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - El Singleton promete dos cosas: una instancia (útil) y acceso
          global (el problema).
        - Forma clásica: constructor privado, static INSTANCE, getInstance().
          Reconócela; no la escribas en código nuevo.
        - Acceso global = dependencias escondidas, pruebas que se contaminan
          y estado compartido sin control.
        - Hoy: una instancia creada en el composition root e inyectada (lo
          que Spring hace por defecto).
        - Lo compartido debe ser sin estado, inmutable o seguro con hilos.
        - Legítimos: constantes inmutables, enums, y lo que representa algo
          único del proceso, como Runtime.getRuntime().
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
