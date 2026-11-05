package v11_patterns.part2_structural;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/*
    =====================================================================
    PROXY: CONTROLAR EL ACCESO A UN OBJETO (Y CÓMO FUNCIONAN LOS FRAMEWORKS)
    =====================================================================

    Ruta de patterns/: guía 12 de 28  (part2_structural)
    Anterior:  part2_structural/Flyweight
    Siguiente: part3_behavioral/ChainOfResponsibility  (última de part2_structural/)

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo, no para ejecutarse.
    - Cada capa explica una idea, POR QUÉ es así, y justo debajo está el
      código que la demuestra. El código es real: compila y se usa.
    - main() no imprime explicaciones: solo comprueba que cada ejemplo
      hace lo que el texto dice.

    Importancia: ALTA para entenderlo. Escribirás pocos proxies a mano,
    pero los frameworks que usarás (Spring) funcionan con ellos: sin este
    patrón, mucho de su comportamiento parece magia o "un bug".

    Índice:
        CAPA 1)  Qué es un proxy
        CAPA 2)  Proxy de protección: revisar permisos
        CAPA 3)  Proxy virtual: crear lo caro solo si se usa
        CAPA 4)  Proxy remoto: un objeto local que representa a otro lejano
        CAPA 5)  Proxies dinámicos: generados al ejecutar
        CAPA 6)  Así funciona @Transactional (y su trampa)
        CAPA 7)  Lo que cuesta un proxy
        CAPA 8)  Proxy y Decorator
        CAPA 9)  Errores comunes
        RESUMEN FINAL
*/
public class Proxy {

    public static void main(String[] args) {
        check(protectionProxyExample(), "CAPA 2");
        check(virtualProxyExample(), "CAPA 3");
        check(dynamicProxyExample(), "CAPA 5");
        check(transactionalExample(), "CAPA 6");
        check(proxyCostsExample(), "CAPA 7");
        System.out.println("Proxy: todos los ejemplos se comportan como se explica.");
    }

    /*
        =================================================================
        CAPA 1) QUÉ ES UN PROXY
        =================================================================

        Un PROXY ("apoderado", "representante") es un objeto que se pone EN
        LUGAR de otro, con la MISMA interface, para CONTROLAR el acceso a
        él. Quien lo usa cree que habla con el objeto real.

        PROXY: "Proporciona un sustituto o representante de otro objeto
        para controlar el acceso a él."

        Estructuralmente es igual que un decorador (part2_structural/
        Decorator): misma interface, contiene al real, delega. La
        diferencia es la INTENCIÓN: el proxy decide SI, CUÁNDO o CÓMO se
        llega al objeto real:
        - PROTECCIÓN: ¿tiene permiso quien llama? (CAPA 2)
        - VIRTUAL: crear el objeto real solo cuando de verdad se necesite
          (CAPA 3)
        - REMOTO: el objeto real está en otra máquina (CAPA 4)

        Nombres del libro: SUBJECT (la interface común), REAL SUBJECT (el
        objeto real), PROXY (el representante).
    */
    interface SalesReports {                                // SUBJECT
        BigDecimal monthlyRevenue(int month);

        String exportAll();
    }

    static final class DatabaseSalesReports implements SalesReports {   // REAL SUBJECT
        @Override
        public BigDecimal monthlyRevenue(int month) {
            return new BigDecimal("125000").add(BigDecimal.valueOf(month * 1_000L));
        }

        @Override
        public String exportAll() {
            return "todas las ventas (datos sensibles)";
        }
    }

    /*
        =================================================================
        CAPA 2) PROXY DE PROTECCIÓN: REVISAR PERMISOS
        =================================================================

        El objeto real sabe hacer reportes; no debería mezclar eso con
        "quién puede verlos" (v10_design/part2_solid/SingleResponsibility).
        Un proxy de protección revisa los permisos ANTES de delegar, y si
        no alcanzan, NO llega al objeto real.

        Así el control de acceso vive en un lugar, se puede probar solo, y
        el objeto real queda igual. Es la idea detrás de los frameworks de
        seguridad (una anotación de "solo administradores" sobre un método;
        v15_spring/), que generan este proxy por ti (CAPA 5).
    */
    record User(String name, Set<String> roles) { }

    static final class SecuredSalesReports implements SalesReports {   // PROXY de protección
        private final SalesReports real;
        private final User currentUser;

        SecuredSalesReports(SalesReports real, User currentUser) {
            this.real = real;
            this.currentUser = currentUser;
        }

        @Override
        public BigDecimal monthlyRevenue(int month) {
            requireRole("GERENTE");
            return real.monthlyRevenue(month);
        }

        @Override
        public String exportAll() {
            requireRole("ADMIN");                           // más sensible: otro permiso
            return real.exportAll();
        }

        private void requireRole(String role) {
            if (!currentUser.roles().contains(role)) {
                throw new SecurityException(currentUser.name() + " no tiene el rol " + role);
            }
        }
    }

    static boolean protectionProxyExample() {
        SalesReports forManager = new SecuredSalesReports(new DatabaseSalesReports(), new User("ana", Set.of("GERENTE")));
        boolean managerSeesRevenue = forManager.monthlyRevenue(3).compareTo(new BigDecimal("128000")) == 0;

        boolean exportBlocked;
        try {
            forManager.exportAll();
            exportBlocked = false;
        } catch (SecurityException e) {
            exportBlocked = e.getMessage().equals("ana no tiene el rol ADMIN");
        }
        return managerSeesRevenue && exportBlocked;
    }

    /*
        =================================================================
        CAPA 3) PROXY VIRTUAL: CREAR LO CARO SOLO SI SE USA
        =================================================================

        Algunos objetos son caros de crear: cargan un archivo grande,
        abren conexiones, leen miles de registros. Si se crean al arrancar
        "por si acaso", se paga el costo aunque nadie los use.

        Un proxy VIRTUAL se crea barato (no tiene nada adentro) y crea el
        objeto real la PRIMERA vez que alguien llama a un método. Las
        siguientes llamadas usan el mismo objeto ya creado. A esto se le
        llama carga perezosa ("lazy loading"; la misma idea del Supplier
        de v7_functional/part1_lambdas/FunctionalInterfaces, CAPA 3).

        Donde lo verás: las librerías que leen datos de una base de datos
        (como la que usa Spring para eso; v15_spring/) devuelven proxies de
        las relaciones: el cliente.getOrders() no consulta los pedidos
        hasta que los recorres.

        (Si varios hilos pueden hacer la primera llamada a la vez, crear el
        objeto real debe estar protegido para que no se cree dos veces;
        v13_concurrency/. Aquí se usa synchronized, lo más simple.)
    */
    static int expensiveCreations = 0;

    static final class LazySalesReports implements SalesReports {   // PROXY virtual
        private final Supplier<SalesReports> factory;
        private SalesReports real;                          // null hasta que se necesite

        LazySalesReports(Supplier<SalesReports> factory) {
            this.factory = factory;
        }

        private synchronized SalesReports real() {
            if (real == null) {
                real = factory.get();                       // la creación cara, solo la primera vez
            }
            return real;
        }

        @Override
        public BigDecimal monthlyRevenue(int month) {
            return real().monthlyRevenue(month);
        }

        @Override
        public String exportAll() {
            return real().exportAll();
        }
    }

    static boolean virtualProxyExample() {
        expensiveCreations = 0;
        SalesReports reports = new LazySalesReports(() -> {
            expensiveCreations++;                           // imagina: abrir conexión, cargar índices...
            return new DatabaseSalesReports();
        });
        boolean nothingCreatedYet = expensiveCreations == 0;

        reports.monthlyRevenue(1);
        reports.monthlyRevenue(2);
        reports.exportAll();
        return nothingCreatedYet && expensiveCreations == 1;   // tres usos, una sola creación
    }

    /*
        =================================================================
        CAPA 4) PROXY REMOTO: UN OBJETO LOCAL QUE REPRESENTA A OTRO LEJANO
        =================================================================

        Cuando el objeto real vive en OTRO proceso u otra máquina (otro
        servicio de la empresa), un proxy remoto lo representa en tu
        proceso: tiene la misma interface, y cada llamada se convierte en
        un mensaje por la red (normalmente una petición HTTP con JSON;
        v8_apis/part2_io_and_network/HttpClientUsage y Serialization), y la
        respuesta se convierte de vuelta en un objeto.

        Tu código llama inventory.stockOf("KBD-1") como si fuera local; el
        proxy hace la petición, espera la respuesta y la traduce.

        Lo importante de entender, porque es la fuente de muchos errores:
        una llamada remota PARECE local, pero no lo es. Puede tardar cientos
        de milisegundos, puede fallar por la red, puede quedarse colgada
        (timeouts, reintentos, idempotencia: HttpClientUsage, CAPAS 8 a
        11). Los frameworks generan estos clientes a partir de una
        interface con anotaciones (v15_spring/).
    */

    /*
        =================================================================
        CAPA 5) PROXIES DINÁMICOS: GENERADOS AL EJECUTAR
        =================================================================

        Escribir un proxy a mano significa implementar CADA método de la
        interface (los de la CAPA 2 repiten requireRole + delegar en cada
        uno). Si quieres el mismo comportamiento (medir tiempos, registrar
        llamadas, revisar permisos) para CUALQUIER interface, Java tiene
        PROXIES DINÁMICOS: una clase proxy que la JVM GENERA AL EJECUTAR.

            java.lang.reflect.Proxy.newProxyInstance(cargador, interfaces, manejador)

        - Le das las interfaces que debe implementar.
        - Le das un InvocationHandler: UN método, invoke(proxy, method,
          args), que recibe TODAS las llamadas a cualquier método, con el
          método (un objeto java.lang.reflect.Method, reflexión;
          v10_design/part3_responsibilities/DependencyInjection, CAPA 8) y
          los argumentos.
        - Dentro de invoke haces lo que quieras antes y después, y llamas
          al real con method.invoke(real, args).

        El ejemplo: un registrador genérico que funciona con CUALQUIER
        interface, sin escribir una clase por interface.

        Limitación: los proxies dinámicos del JDK solo funcionan con
        INTERFACES. Para clases sin interface, los frameworks generan una
        SUBCLASE al ejecutar con librerías que fabrican bytecode; la idea
        es la misma.
    */
    static <T> T logged(Class<T> type, T real, List<String> log) {
        InvocationHandler handler = (proxy, method, arguments) -> {
            log.add("→ " + method.getName());                       // ANTES, para cualquier método
            try {
                Object result = method.invoke(real, arguments);     // delegar en el real, por reflexión
                log.add("← " + method.getName());                   // DESPUÉS
                return result;
            } catch (InvocationTargetException e) {
                throw e.getCause();                                  // relanzar la excepción ORIGINAL del real
            }
        };
        return type.cast(java.lang.reflect.Proxy.newProxyInstance(
                type.getClassLoader(), new Class<?>[] {type}, handler));
    }

    static boolean dynamicProxyExample() {
        List<String> log = new ArrayList<>();
        SalesReports reports = logged(SalesReports.class, new DatabaseSalesReports(), log);
        reports.monthlyRevenue(1);
        reports.exportAll();

        List<String> textLog = new ArrayList<>();
        CharSequence text = logged(CharSequence.class, "hola", textLog);       // el MISMO código, otra interface
        text.length();
        text.charAt(0);

        return log.equals(List.of("→ monthlyRevenue", "← monthlyRevenue", "→ exportAll", "← exportAll"))
                && textLog.equals(List.of("→ length", "← length", "→ charAt", "← charAt"))
                && java.lang.reflect.Proxy.isProxyClass(reports.getClass());   // una clase generada al ejecutar
    }

    /*
        =================================================================
        CAPA 6) ASÍ FUNCIONA @TRANSACTIONAL (Y SU TRAMPA)
        =================================================================

        Ahora se puede entender cómo un framework convierte una ANOTACIÓN
        en comportamiento. Con Spring escribes:

            @Transactional
            void transfer(...) { ... }

        y "mágicamente" el método corre dentro de una transacción de base de
        datos (todo se guarda, o nada). Lo que pasa:
        1) Al arrancar, Spring ve la anotación (por reflexión).
        2) En lugar de inyectar tu objeto, inyecta un PROXY generado.
        3) Cada llamada pasa por el proxy, que revisa si el método tiene la
           anotación: si la tiene, abre la transacción, llama a tu método, y
           la confirma (commit) o la deshace (rollback) si hubo una excepción
           NO verificada (RuntimeException o Error). Con una checked, por
           defecto, ¡confirma! (se cambia con rollbackFor;
           v15_spring/part3_data/.../jdbc/JdbcAndTransactions).

        El ejemplo construye ese mecanismo con un proxy dinámico y una
        anotación propia, @Transactional. Y muestra LA TRAMPA más famosa:
        si un método de la clase llama a OTRO método anotado de la MISMA
        clase (this.otroMetodo()), esa llamada NO pasa por el proxy (va
        directo dentro del objeto real), así que NO abre transacción. Es una
        de las preguntas más comunes sobre Spring, y se explica sola cuando
        sabes que hay un proxy.

        (Las anotaciones necesitan @Retention(RUNTIME) para que se puedan
        leer al ejecutar; tools/.)
    */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface Transactional { }

    interface AccountService {
        @Transactional
        void transfer(String from, String to, BigDecimal amount);

        void monthEndBatch();                               // NO anotado: llama a transfer por dentro
    }

    static final class RealAccountService implements AccountService {
        final List<String> log;

        RealAccountService(List<String> log) {
            this.log = log;
        }

        @Override
        public void transfer(String from, String to, BigDecimal amount) {
            log.add("transferir " + amount + " de " + from + " a " + to);
            if (amount.signum() <= 0) {
                throw new IllegalArgumentException("Monto inválido");
            }
        }

        @Override
        public void monthEndBatch() {
            transfer("caja", "banco", new BigDecimal("500"));   // llamada INTERNA: this.transfer(...)
        }
    }

    static AccountService transactional(AccountService real, List<String> log) {
        InvocationHandler handler = (proxy, method, arguments) -> {
            if (!method.isAnnotationPresent(Transactional.class)) {
                return invokeReal(method, real, arguments);  // sin anotación: pasar directo
            }
            log.add("BEGIN");
            try {
                Object result = invokeReal(method, real, arguments);
                log.add("COMMIT");
                return result;
            } catch (RuntimeException e) {
                log.add("ROLLBACK");
                throw e;
            }
        };
        return AccountService.class.cast(java.lang.reflect.Proxy.newProxyInstance(
                AccountService.class.getClassLoader(), new Class<?>[] {AccountService.class}, handler));
    }

    private static Object invokeReal(Method method, Object real, Object[] arguments) throws Throwable {
        try {
            return method.invoke(real, arguments);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    static boolean transactionalExample() {
        List<String> log = new ArrayList<>();
        AccountService service = transactional(new RealAccountService(log), log);

        service.transfer("ana", "luis", new BigDecimal("100"));     // pasa por el proxy: con transacción
        boolean wrapped = log.equals(List.of("BEGIN", "transferir 100 de ana a luis", "COMMIT"));

        log.clear();
        try {
            service.transfer("ana", "luis", BigDecimal.ZERO);
        } catch (IllegalArgumentException expected) {
            // el proxy ya registró el ROLLBACK
        }
        boolean rolledBack = log.equals(List.of("BEGIN", "transferir 0 de ana a luis", "ROLLBACK"));

        log.clear();
        service.monthEndBatch();                            // la llamada INTERNA a transfer no pasa por el proxy
        boolean selfInvocationTrap = log.equals(List.of("transferir 500 de caja a banco"));   // ¡sin BEGIN ni COMMIT!

        return wrapped && rolledBack && selfInvocationTrap;
    }

    /*
        =================================================================
        CAPA 7) LO QUE CUESTA UN PROXY
        =================================================================

        - INDIRECCIÓN: un salto más en cada llamada. Con proxies dinámicos,
          además, la llamada pasa por reflexión. Para lógica de negocio es
          despreciable; en un ciclo de millones de llamadas por segundo, se
          mide (v12_jvm/).
        - DEPURACIÓN: los stack traces muestran clases generadas con nombres
          como $Proxy0 o jdk.proxy1.$Proxy1, y los métodos del manejador. Saber que
          hay un proxy te ahorra mucha confusión al leerlos.
        - equals, hashCode y toString TAMBIÉN pasan por el manejador de un
          proxy dinámico: si el manejador no los trata bien, el proxy se
          comporta raro en colecciones o al imprimirlo.
        - El proxy es OTRO objeto, con OTRA clase (como el decorador;
          part2_structural/Decorator, CAPA 5): no compares por clase
          concreta ni hagas casts.
    */
    static boolean proxyCostsExample() {
        List<String> log = new ArrayList<>();
        SalesReports reports = logged(SalesReports.class, new DatabaseSalesReports(), log);
        reports.toString();                                 // también pasa por el manejador
        return log.equals(List.of("→ toString", "← toString"))
                && !(reports instanceof DatabaseSalesReports)
                && reports.getClass().getName().contains("$Proxy");   // el nombre de la clase generada
    }

    /*
        =================================================================
        CAPA 8) PROXY Y DECORATOR
        =================================================================

        La estructura es la misma; la intención y las costumbres cambian:

        | Aspecto                      | Proxy                                  | Decorator                          |
        |------------------------------|----------------------------------------|------------------------------------|
        | Para qué                     | CONTROLAR el acceso al real            | AGREGAR comportamiento             |
        | ¿Siempre llega al real?      | no necesariamente (sin permiso, o aún  | sí, siempre delega                 |
        |                              | no se creó)                            |                                    |
        | ¿Quién crea al real?         | a veces el propio proxy (virtual)      | se lo dan ya creado                |
        | ¿Se apilan varios?           | normalmente uno                        | es lo habitual                     |

        En la práctica, los frameworks llaman "proxy" a los envoltorios
        generados (CAPA 6), aunque lo que hacen sea decorar (transacciones,
        caché). No te preocupes por la etiqueta exacta: entiende el
        mecanismo.
    */

    /*
        =================================================================
        CAPA 9) ERRORES COMUNES
        =================================================================

        1) Meter los permisos dentro de cada método del objeto real, en vez
           de en un proxy (o en la configuración de seguridad del framework)
           (CAPA 2).

        2) Proxies virtuales que crean el objeto dos veces cuando varios
           hilos llegan a la vez (CAPA 3).

        3) Tratar una llamada remota como si fuera local: sin timeouts, sin
           manejar fallas (CAPA 4).

        4) En un proxy dinámico, olvidar desenvolver la
           InvocationTargetException: quien llama recibe una excepción de
           reflexión en vez de la original (CAPA 5).

        5) Esperar que @Transactional, @Cacheable o la seguridad apliquen
           en llamadas internas de la misma clase (CAPA 6).

        6) Depender de la clase concreta de un objeto que el framework
           pudo haber envuelto en un proxy (CAPA 7).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Proxy: un representante con la misma interface que CONTROLA el
          acceso al objeto real.
        - Tipos usados: protección (permisos), virtual (crear lo caro al
          primer uso), remoto (un servicio en otra máquina).
        - Proxies dinámicos: Proxy.newProxyInstance + InvocationHandler;
          una clase generada al ejecutar que recibe todas las llamadas;
          solo para interfaces.
        - Así funcionan @Transactional, @Cacheable y la seguridad de los
          frameworks; y por eso las llamadas internas (this.metodo()) no
          pasan por ellos.
        - Costos: indirección, reflexión, stack traces con $Proxy;
          toString/equals/hashCode también pasan por el manejador.
        - Proxy controla; Decorator agrega. Mismo esqueleto.
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
