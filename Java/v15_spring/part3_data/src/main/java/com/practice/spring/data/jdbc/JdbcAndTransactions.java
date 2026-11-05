package com.practice.spring.data.jdbc;

import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/*
    =====================================================================
    JDBC Y TRANSACCIONES: CONEXIONES, SQL Y @TRANSACTIONAL A FONDO
    =====================================================================

    Ruta de spring/: guía 9  (part3_data)
    Anterior:  part2_web/.../clients/HttpClients
    Siguiente: part3_data/.../jpa/JpaEntities

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo.
    - Las comprobaciones son pruebas: JdbcAndTransactionsTest, contra un
      PostgreSQL 17 REAL que Testcontainers levanta en Docker para la
      prueba y borra al terminar. Hace falta Docker corriendo. Desde
      v15_spring/:
          ./mvnw -pl part3_data test -Dtest=JdbcAndTransactionsTest
    - El esquema está en src/main/resources/jdbc/schema.sql.
    - Números y mensajes citados: corridas reales.

    Lo que ya se vio: transacciones y rollback en memoria (v11_patterns/
    part4_modern/ServiceLayer, CAPA 5), los proxies y la auto-invocación
    (part1_core/.../container/IoCContainer, CAPA 8), ThreadLocal
    (v13_concurrency/part1_threads/ThreadBasics, CAPA 8), actualizaciones
    perdidas (v13_concurrency/part1_threads/ConcurrencyProblems, CAPA 2),
    inyección SQL (v11_patterns/part4_modern/Specification, CAPA 6).

    Importancia: MUY ALTA. Las transacciones mal puestas son de los errores
    más caros: datos a medias, dinero que desaparece, pools agotados.

    Índice:
        CAPA 1)  Una conexión es cara: el pool
        CAPA 2)  JdbcClient: SQL con parámetros y resultados en records
        CAPA 3)  Qué es una transacción
        CAPA 4)  @Transactional por dentro
        CAPA 5)  Qué provoca rollback (y la trampa de las checked)
        CAPA 6)  La auto-invocación: la transacción que no existe
        CAPA 7)  Propagación: REQUIRES_NEW para lo que debe quedar
        CAPA 8)  Dos transacciones a la vez: la actualización perdida
        CAPA 9)  Nunca esperar a otro sistema con una transacción abierta
        CAPA 10) Errores comunes
        RESUMEN FINAL
*/
public class JdbcAndTransactions {

    /*
        =================================================================
        CAPA 1) UNA CONEXIÓN ES CARA: EL POOL
        =================================================================

        JDBC es la API estándar de Java para bases de datos relacionales.
        Todo (JPA, Spring Data) termina usando JDBC por debajo.

        Una CONEXIÓN a PostgreSQL es:
        - un socket TCP con el servidor, más la autenticación;
        - del lado del servidor, un PROCESO del sistema operativo: PostgreSQL
          crea un proceso (fork) por cada conexión. Medido: tres conexiones
          tuvieron tres procesos distintos (pg_backend_pid 139, 140 y 141).
          Por eso PostgreSQL acepta pocas conexiones (100 por defecto,
          max_connections): cada una cuesta memoria y un proceso.

        Abrir una cuesta. Medido contra el PostgreSQL de Docker: ~13 a 33 ms
        por conexión nueva, contra ~0.7 a 4 ms al pedirla a un POOL (y eso
        incluye una ida y vuelta para verificarla).

        Un POOL abre unas cuantas conexiones y las REUTILIZA: "cerrar" una
        conexión del pool la devuelve, no la cierra. Spring Boot configura
        HikariCP solo, con las propiedades spring.datasource.*:

            spring.datasource.url=jdbc:postgresql://localhost:5432/tienda
            spring.datasource.username=...       (y el password, por variable
                                                  de entorno: config/
                                                  ExternalConfiguration, CAPA 8)
            spring.datasource.hikari.maximum-pool-size=10   (por defecto: 10)
            spring.datasource.hikari.connection-timeout=30000 (ms esperando una)

        Medido: el pool por defecto tiene 10 conexiones. Más no siempre es
        mejor: la base de datos tiene pocos núcleos, y 10 conexiones bien
        usadas atienden miles de peticiones (cada una usa la conexión unos
        milisegundos). El enemigo es retenerlas (CAPA 9).

        En las pruebas, Testcontainers + @ServiceConnection ponen la URL y
        el usuario del contenedor, sin escribir nada.
    */
    @SpringBootApplication
    public static class BankApplication {
    }

    /*
        =================================================================
        CAPA 2) JDBCCLIENT: SQL CON PARÁMETROS Y RESULTADOS EN RECORDS
        =================================================================

        JDBC "a mano" (Connection, PreparedStatement, ResultSet, cerrar
        todo en finally) es mucho código repetido. Spring lo envuelve; la
        forma actual es JdbcClient (Spring 6.1), que Boot ya registra como
        bean:

            jdbc.sql("select * from account where id = :id")
                .param("id", 7)                      // parámetro con nombre
                .query(Account.class)                // fila -> record
                .optional();                         // Optional, single(), list()

            jdbc.sql("update account set balance_cents = :b where id = :id")
                .param("b", 500).param("id", 7)
                .update();                           // filas afectadas

        - Los valores SIEMPRE como parámetros (:id), nunca pegados al texto
          del SQL: así no hay inyección SQL (Specification, CAPA 6). Por
          dentro es un PreparedStatement.
        - query(Account.class) llena el record por nombre de columna, y
          convierte balance_cents en balanceCents (verificado en la prueba).
        - Las excepciones de SQL se traducen a la jerarquía de Spring
          (DataAccessException): DuplicateKeyException,
          DataIntegrityViolationException... no SQLException con códigos.

        (Lo verás en código existente: JdbcTemplate y
        NamedParameterJdbcTemplate, que hacen lo mismo con otra API.
        JdbcClient está construido sobre ellos.)
    */
    public record Account(long id, String owner, long balanceCents) {
    }

    @Repository
    public static class AccountRepository {
        private final JdbcClient jdbc;

        public AccountRepository(JdbcClient jdbc) {
            this.jdbc = jdbc;
        }

        public Optional<Account> findById(long id) {
            return jdbc.sql("select id, owner, balance_cents from account where id = :id")
                    .param("id", id)
                    .query(Account.class)                   // balance_cents -> balanceCents
                    .optional();
        }

        public List<Account> findAll() {
            return jdbc.sql("select id, owner, balance_cents from account order by id").query(Account.class).list();
        }

        public void insert(Account account) {
            jdbc.sql("insert into account (id, owner, balance_cents) values (:id, :owner, :balance)")
                    .param("id", account.id())
                    .param("owner", account.owner())        // un nombre con ' o con SQL: solo es un valor
                    .param("balance", account.balanceCents())
                    .update();
        }

        public void addToBalance(long id, long deltaCents) {
            int updated = jdbc.sql("update account set balance_cents = balance_cents + :delta where id = :id")
                    .param("delta", deltaCents)
                    .param("id", id)
                    .update();
            if (updated == 0) {
                throw new AccountNotFoundException(id);
            }
        }
    }

    public static class AccountNotFoundException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public AccountNotFoundException(long id) {
            super("No existe la cuenta " + id);
        }
    }

    public static class ReceiptFailedException extends Exception {      // CHECKED (CAPA 5)
        private static final long serialVersionUID = 1L;

        public ReceiptFailedException(String message) {
            super(message);
        }
    }

    /*
        =================================================================
        CAPA 3) QUÉ ES UNA TRANSACCIÓN
        =================================================================

        Una transferencia son dos UPDATE: quitar de una cuenta y poner en
        otra. Si el segundo falla (la cuenta destino no existe, se cae el
        servidor), el primero NO debe quedar: el dinero desaparecería.

        Una TRANSACCIÓN agrupa varias operaciones en una unidad ACID:
            Atomicidad    todo o nada (COMMIT o ROLLBACK)
            Consistencia  las reglas (checks, llaves) se cumplen al final
            Aislamiento   otras transacciones no ven lo que está a medias
                          (con matices: CAPA 8)
            Durabilidad   lo confirmado sobrevive a un corte de luz (la base
                          de datos lo escribe en su log en disco antes de
                          decir "listo")

        Por defecto JDBC está en "autocommit": cada sentencia es su propia
        transacción. Para agrupar, hay que abrir una transacción, y eso es
        lo que hace @Transactional.
    */

    /*
        =================================================================
        CAPA 4) @TRANSACTIONAL POR DENTRO
        =================================================================

        @Transactional en un método de un bean hace que Spring lo envuelva
        en un PROXY (container/IoCContainer, CAPA 8). Al llamar al método, el
        proxy:
        1) toma una conexión del pool y le quita el autocommit (BEGIN);
        2) la deja "atada" al HILO actual (en un ThreadLocal:
           v13_concurrency/part1_threads/ThreadBasics, CAPA 8). Así, todo
           JdbcClient o repositorio que se use dentro, en ESE hilo, usa LA
           MISMA conexión, y por lo tanto la misma transacción;
        3) llama a tu método;
        4) si terminó bien: COMMIT; si lanzó una excepción que provoca
           rollback (CAPA 5): ROLLBACK;
        5) devuelve la conexión al pool.

        Consecuencias:
        - va en los métodos PÚBLICOS de los SERVICIOS (el caso de uso: v11_
          patterns/part4_modern/ServiceLayer, CAPA 5), no en el controller
          ni en cada método del repositorio;
        - como la conexión está atada al hilo, si dentro lanzas trabajo a
          OTRO hilo (un executor, un CompletableFuture), ese hilo NO está en
          la transacción;
        - @Transactional(readOnly = true) para las consultas: avisa al
          driver y a la base de datos que no habrá cambios (y, con JPA, evita
          trabajo extra).

        Desde código (raro): TransactionTemplate.execute(status -> ...).
    */
    @Service
    public static class TransferService {
        private final AccountRepository accounts;
        private final AuditService audit;

        public TransferService(AccountRepository accounts, AuditService audit) {
            this.accounts = accounts;
            this.audit = audit;
        }

        @Transactional
        public void transfer(long from, long to, long cents) {
            accounts.addToBalance(from, -cents);            // 1) quitar (si queda negativo: el CHECK de la tabla)
            accounts.addToBalance(to, cents);               // 2) poner (si la cuenta no existe: excepción)
        }                                                   // ambas o ninguna

        /*
            =============================================================
            CAPA 5) QUÉ PROVOCA ROLLBACK (Y LA TRAMPA DE LAS CHECKED)
            =============================================================

            Por defecto, @Transactional hace ROLLBACK solo con excepciones
            NO checked (RuntimeException y Error). Con una excepción CHECKED,
            hace COMMIT. Es una decisión histórica de Spring (venía de los
            EJB), y sorprende a todos.

            transferWithReceipt quita el dinero y luego "falla al generar el
            recibo" con una excepción checked: la prueba demuestra que el
            dinero SÍ se quitó (commit). Con rollbackFor = Exception.class,
            se deshace.

            Regla práctica: en los servicios, usa excepciones no checked
            (v3_exceptions/part1_concepts/CheckedVsUnchecked); y si tienes
            que lanzar una checked, decide explícitamente con rollbackFor.
        */
        @Transactional
        public void transferWithReceipt(long from, long to, long cents) throws ReceiptFailedException {
            accounts.addToBalance(from, -cents);
            throw new ReceiptFailedException("no se pudo generar el recibo");   // checked: COMMIT
        }

        @Transactional(rollbackFor = Exception.class)
        public void transferWithReceiptRollingBack(long from, long to, long cents) throws ReceiptFailedException {
            accounts.addToBalance(from, -cents);
            throw new ReceiptFailedException("no se pudo generar el recibo");   // ahora sí: ROLLBACK
        }

        /*
            =============================================================
            CAPA 6) LA AUTO-INVOCACIÓN: LA TRANSACCIÓN QUE NO EXISTE
            =============================================================

            El proxy solo actúa cuando la llamada viene de AFUERA del bean.
            Si un método del servicio llama a this.transfer(...), esa llamada
            no pasa por el proxy: NO HAY TRANSACCIÓN, aunque transfer tenga
            @Transactional (container/IoCContainer, CAPA 8).

            transferAndLog no tiene @Transactional y llama a this.transfer
            con una cuenta destino que no existe. Sin transacción, el primer
            UPDATE ya se confirmó solo (autocommit) cuando falla el segundo:
            el dinero desaparece. La prueba lo demuestra, y también que
            insideTransaction() dice true llamado desde afuera (por el proxy)
            y false llamado con this desde la misma clase.

            Soluciones: poner @Transactional en el método que llaman desde
            afuera (aquí, transferAndLog); o mover el método a otro bean.
        */
        public void transferAndLog(long from, long to, long cents) {
            this.transfer(from, to, cents);                 // NO pasa por el proxy: sin transacción
        }

        @Transactional
        public boolean insideTransaction() {                // ¿hay una transacción activa aquí?
            return TransactionSynchronizationManager.isActualTransactionActive();
        }

        public boolean insideTransactionViaThis() {
            return this.insideTransaction();                // misma clase: el proxy no interviene
        }

        /*
            =============================================================
            CAPA 7) PROPAGACIÓN: REQUIRES_NEW PARA LO QUE DEBE QUEDAR
            =============================================================

            ¿Qué pasa si un método @Transactional llama a otro
            @Transactional (de OTRO bean)? Lo decide la PROPAGACIÓN:

                REQUIRED (por defecto)  si ya hay una transacción, se UNE a
                                        ella; si no, abre una. Un rollback
                                        de la de afuera deshace todo.
                REQUIRES_NEW            SUSPENDE la de afuera y abre una
                                        NUEVA, con otra conexión. Se confirma
                                        por su cuenta, aunque la de afuera
                                        haga rollback.
                (MANDATORY, SUPPORTS, NOT_SUPPORTED, NEVER, NESTED: casi
                nunca se usan)

            El caso típico de REQUIRES_NEW: una BITÁCORA de auditoría. El
            intento de transferencia debe quedar registrado AUNQUE la
            transferencia falle. AuditService.record usa REQUIRES_NEW; la
            prueba lo comprueba: la transferencia hace rollback y la entrada
            de auditoría queda.

            Ojo: REQUIRES_NEW usa una SEGUNDA conexión mientras la primera
            sigue tomada. Con muchas peticiones a la vez, puede agotar el
            pool (CAPA 9). Úsalo para lo que de verdad debe sobrevivir.
        */
        @Transactional
        public void auditedTransfer(long from, long to, long cents) {
            audit.record("intento de transferir " + cents + " de " + from + " a " + to);   // REQUIRES_NEW
            accounts.addToBalance(from, -cents);
            accounts.addToBalance(to, cents);               // si falla: rollback... de esto, no de la auditoría
        }
    }

    @Service
    public static class AuditService {
        private final JdbcClient jdbc;

        public AuditService(JdbcClient jdbc) {
            this.jdbc = jdbc;
        }

        @Transactional(propagation = Propagation.REQUIRES_NEW)
        public void record(String message) {
            jdbc.sql("insert into audit_log (message) values (:m)").param("m", message).update();
        }

        @Transactional(readOnly = true)
        public List<String> messages() {
            return jdbc.sql("select message from audit_log order by id").query(String.class).list();
        }
    }

    /*
        =================================================================
        CAPA 8) DOS TRANSACCIONES A LA VEZ: LA ACTUALIZACIÓN PERDIDA
        =================================================================

        El AISLAMIENTO de ACID tiene niveles. PostgreSQL, por defecto, usa
        READ COMMITTED: cada sentencia ve lo confirmado hasta ese momento.
        Eso NO impide la actualización perdida (v13_concurrency/
        part1_threads/ConcurrencyProblems, CAPA 2), ahora entre
        transacciones de una base de datos:

            tx A: lee stock = 10               tx B: lee stock = 10
            tx A: escribe 10 - 2 = 8           tx B: escribe 10 - 2 = 8
            resultado: 8, aunque se apartaron 4 piezas

        @Transactional no lo resuelve solo. La prueba lo demuestra con
        reserveNaive: dos reservas de 2 piezas a la vez dejan 8, no 6.

        Las soluciones, de la más simple a la más pesada:
        1) que la BASE DE DATOS haga la cuenta en UNA sentencia atómica:
               update product set stock = stock - :q
               where sku = :sku and stock >= :q
           Una sola sentencia no se encima con otra (la fila queda bloqueada
           mientras se actualiza), y la condición "stock >= :q" evita bajar
           de cero: si devuelve 0 filas, no había suficiente. (reserveAtomic)
        2) BLOQUEO PESIMISTA: select ... for update bloquea la fila hasta el
           commit; la otra transacción espera.
        3) BLOQUEO OPTIMISTA: una columna version; el update incluye "where
           version = :leida" y falla si alguien cambió la fila
           (part3_data/.../repositories/SpringDataRepositories).
        4) Subir el aislamiento (REPEATABLE READ, SERIALIZABLE): la base de
           datos detecta el conflicto y aborta una; hay que reintentar.
    */
    @Service
    public static class InventoryService {
        private final JdbcClient jdbc;

        public InventoryService(JdbcClient jdbc) {
            this.jdbc = jdbc;
        }

        @Transactional
        public void reserveNaive(String sku, int quantity, Runnable betweenReadAndWrite) {
            int stock = jdbc.sql("select stock from product where sku = :sku").param("sku", sku)
                    .query(Integer.class).single();         // leer...
            betweenReadAndWrite.run();                      // (la prueba sincroniza aquí a las dos transacciones)
            jdbc.sql("update product set stock = :s where sku = :sku")
                    .param("s", stock - quantity).param("sku", sku).update();   // ...y escribir lo calculado
        }

        @Transactional
        public boolean reserveAtomic(String sku, int quantity) {
            int updated = jdbc.sql("update product set stock = stock - :q where sku = :sku and stock >= :q")
                    .param("q", quantity).param("sku", sku).update();   // una sola sentencia atómica
            return updated == 1;                            // 0: no alcanzaba
        }

        @Transactional(readOnly = true)
        public int stockOf(String sku) {
            return jdbc.sql("select stock from product where sku = :sku").param("sku", sku).query(Integer.class).single();
        }

        /*
            =============================================================
            CAPA 9) NUNCA ESPERAR A OTRO SISTEMA CON UNA TRANSACCIÓN
                    ABIERTA
            =============================================================

            Mientras una transacción está abierta, tiene tomada una conexión
            del pool (y bloqueos en la base de datos). Si dentro se llama a
            un servicio externo que tarda (un pago, un correo: part2_web/
            .../clients/HttpClients), la conexión queda tomada todo ese
            tiempo sin usarse.

            Con 10 conexiones y una llamada externa de 2 s dentro de cada
            transacción, el servicio atiende como máximo 5 peticiones por
            segundo; la 11.ª a la vez espera una conexión, y tras el
            connection-timeout falla. Medido con un pool de 10:

                SQLTransientConnectionException: HikariPool-1 - Connection
                is not available, request timed out after 502ms (total=10,
                active=10, idle=0, waiting=0)

            La prueba lo provoca con un pool de 3: tres transacciones
            "esperando a un proveedor" y una cuarta que no consigue conexión.

            Y hay algo peor que la conexión: los BLOQUEOS. Al escribir la
            prueba, las tres transacciones actualizaban el MISMO producto. La
            primera bloqueó la fila con su UPDATE y se quedó "esperando al
            proveedor"; las otras dos se quedaron detenidas en su UPDATE,
            esperando ese bloqueo, sin llegar nunca a la llamada. Una
            transacción larga no solo acapara una conexión: congela a todos
            los que necesitan las mismas filas. (Por eso la prueba usa tres
            productos distintos: para que se vea el pool agotado.)

            La regla (ServiceLayer, CAPA 6): las llamadas a otros sistemas,
            FUERA de la transacción. Primero lo externo (o después del commit,
            con outbox: v14_architecture/part3_systems/
            CommandsQueriesAndConsistency), y la transacción solo para lo de
            la base de datos, lo más corta posible.
        */
        @Transactional
        public void reserveWhileCallingProvider(String sku, Runnable slowProviderCall) {
            jdbc.sql("update product set stock = stock - 1 where sku = :sku").param("sku", sku).update();
            slowProviderCall.run();                         // la conexión queda tomada todo este tiempo
        }
    }

    /*
        =================================================================
        CAPA 10) ERRORES COMUNES
        =================================================================

        1) Abrir conexiones a mano; pools gigantes "por si acaso" (CAPA 1).

        2) Concatenar valores en el SQL (CAPA 2).

        3) @Transactional en el controller, o en cada método del
           repositorio en vez del caso de uso (CAPA 4).

        4) Lanzar trabajo a otro hilo esperando que siga en la transacción
           (CAPA 4).

        5) Excepciones checked que hacen COMMIT sin que nadie lo note
           (CAPA 5).

        6) Llamar a un método @Transactional desde la misma clase (CAPA 6).

        7) REQUIRES_NEW por costumbre: dos conexiones por petición (CAPA 7).

        8) Leer, calcular en Java y escribir el resultado, con varias
           peticiones a la vez: actualizaciones perdidas (CAPA 8).

        9) Llamadas HTTP o esperas dentro de una transacción: pool agotado
           (CAPA 9).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Una conexión es un socket y un proceso del servidor (medido): se
          reutilizan con un pool (HikariCP, 10 por defecto).
        - JdbcClient: SQL con parámetros con nombre, resultados en records,
          excepciones de Spring.
        - Transacción = ACID; @Transactional es un proxy que ata una conexión
          al hilo, y hace commit o rollback.
        - Rollback con RuntimeException; con checked, COMMIT (salvo
          rollbackFor).
        - Auto-invocación: sin proxy, sin transacción.
        - REQUIRED se une; REQUIRES_NEW abre otra (auditoría que sobrevive).
        - READ COMMITTED no evita la actualización perdida: sentencias
          atómicas, FOR UPDATE o versión.
        - Nada lento ni externo dentro de una transacción.
    */
}
