package com.practice.spring.data.jpa;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/*
    =====================================================================
    JPA E HIBERNATE: ENTIDADES, RELACIONES Y EL PROBLEMA N+1
    =====================================================================

    Ruta de spring/: guía 10  (part3_data)
    Anterior:  part3_data/.../jdbc/JdbcAndTransactions
    Siguiente: part3_data/.../repositories/SpringDataRepositories

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo.
    - Las comprobaciones son pruebas: JpaEntitiesTest, contra PostgreSQL 17
      real (Testcontainers). Las pruebas CUENTAN las sentencias SQL que
      Hibernate manda (con sus estadísticas), para ver el N+1 con números.
      Desde v15_spring/:
          ./mvnw -pl part3_data test -Dtest=JpaEntitiesTest
    - Versiones: Hibernate 7.4, Jakarta Persistence 3.2 (verificado con
      dependency:tree). Mensajes y conteos citados: corridas reales.

    Lo que ya se vio: JDBC y transacciones (jdbc/JdbcAndTransactions), los
    agregados de DDD (v14_architecture/part1_layers/DDDBasics, CAPA 5), los
    DTO (v11_patterns/part4_modern/DTO) y los proxies (part1_core/.../
    container/IoCContainer, CAPA 8).

    Importancia: MUY ALTA. JPA es lo más usado para datos en Spring, y lo
    que más problemas de rendimiento causa cuando no se entiende.

    Índice:
        CAPA 1)  Qué es JPA y qué es Hibernate
        CAPA 2)  Mapear una entidad
        CAPA 3)  El contexto de persistencia: identidad y dirty checking
        CAPA 4)  Relaciones: quién es el dueño
        CAPA 5)  Carga perezosa y LazyInitializationException
        CAPA 6)  El problema N+1, medido
        CAPA 7)  Resolver el N+1
        CAPA 8)  El esquema: quién crea las tablas
        CAPA 9)  Entidades fuera de la capa de datos
        CAPA 10) Errores comunes
        RESUMEN FINAL
*/
public class JpaEntities {

    /*
        =================================================================
        CAPA 1) QUÉ ES JPA Y QUÉ ES HIBERNATE
        =================================================================

        Con JDBC (jdbc/JdbcAndTransactions) escribes el SQL y conviertes
        filas en objetos a mano. Un ORM (Object-Relational Mapping) hace esa
        conversión por ti: describes cómo se guarda una clase en una tabla,
        y él genera el SQL para guardarla, cargarla y relacionarla.

        - JPA (Jakarta Persistence) es la ESPECIFICACIÓN: las anotaciones
          (@Entity, @Id, @OneToMany...) y la API (EntityManager). Está en
          jakarta.persistence (javax.persistence era hasta Spring Boot 2).
        - HIBERNATE es la IMPLEMENTACIÓN que trae Spring Boot. Casi todo lo
          que verás es JPA estándar; algunas cosas son de Hibernate.

        Lo que da: menos SQL repetido, relaciones navegables (order.getLines()),
        y cambios guardados solos (CAPA 3). Lo que cuesta: el SQL lo genera
        otro, y si no sabes CUÁNTO SQL genera, aparecen problemas de
        rendimiento (CAPA 6). Regla de supervivencia: mira siempre el SQL
        (logging.level.org.hibernate.SQL=debug).
    */
    @SpringBootApplication
    public static class ShopApplication {
    }

    /*
        =================================================================
        CAPA 2) MAPEAR UNA ENTIDAD
        =================================================================

        Una ENTIDAD es una clase cuyas instancias se guardan como filas:

            @Entity                    es una entidad
            @Table(name = "orders")    nombre de la tabla ("order" es palabra
                                       reservada de SQL: no se usa)
            @Id                        la llave primaria
            @GeneratedValue(SEQUENCE)  el id lo genera una secuencia de la
                                       base de datos
            @Column(nullable = false, length = 100)   detalles de la columna
            @Enumerated(EnumType.STRING)  un enum como TEXTO
            @Embedded                  un value object en las mismas columnas

        Las reglas que impone JPA (y por qué):
        - un constructor SIN ARGUMENTOS (puede ser protected): Hibernate crea
          el objeto vacío y luego llena los campos;
        - la clase NO final y con campos no final: Hibernate crea SUBCLASES
          (proxies, CAPA 5) y cambia los campos;
        - por eso una entidad NO puede ser un record. Los records SÍ sirven
          como @Embeddable (value objects: DDDBasics, CAPA 3) y para los DTO.

        Dos decisiones que importan:
        - @Enumerated(STRING), nunca ORDINAL (el de por defecto): ORDINAL
          guarda la POSICIÓN (0, 1, 2); si alguien agrega un valor en medio
          del enum, todas las filas viejas cambian de significado.
        - SEQUENCE en vez de IDENTITY: con IDENTITY (autoincremento) la base
          de datos da el id al insertar, así que Hibernate tiene que hacer
          cada INSERT de inmediato y no puede agruparlos. Con SEQUENCE pide
          ids por bloques y puede mandar los inserts en lote.

        Constructores con lo obligatorio y métodos con nombre del negocio
        (DDDBasics, CAPA 4), no setters para todo.

        Un detalle que apareció al escribir esta guía: las consultas JPQL
        usan el NOMBRE DE LA ENTIDAD, que por defecto sale del nombre de la
        clase. Con clases anidadas como las de aquí, Hibernate no encontró
        "PurchaseOrder" (medido: "UnknownEntityException: Could not resolve
        root entity 'PurchaseOrder'"). @Entity(name = "PurchaseOrder") lo
        fija explícito; en clases normales (no anidadas) no hace falta.
    */
    @Entity(name = "Customer")                              // nombre para JPQL (ver CAPA 2)
    @Table(name = "customers")
    public static class Customer {
        @Id
        @GeneratedValue(strategy = GenerationType.SEQUENCE)
        private Long id;

        @Column(nullable = false, length = 100)
        private String name;

        protected Customer() {                              // para Hibernate
        }

        public Customer(String name) {
            this.name = name;
        }

        public Long getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public void rename(String newName) {                // un método del negocio, no un setter suelto
            this.name = newName;
        }
    }

    @Embeddable
    public record Money(@Column(name = "amount_cents", nullable = false) long cents) {   // un value object
    }

    public enum OrderStatus { OPEN, PAID, CANCELLED }

    /*
        =================================================================
        CAPA 4) RELACIONES: QUIÉN ES EL DUEÑO
        =================================================================

            @ManyToOne    muchos pedidos -> un cliente (en la tabla orders hay
                          una columna customer_id: la llave foránea)
            @OneToMany    un pedido -> muchas líneas (la llave está en la tabla
                          de las líneas)

        En una relación de dos lados (Order <-> OrderLine), solo UN lado
        manda en la base de datos: el que tiene la llave foránea
        (OrderLine.order, con @ManyToOne). El otro lado declara
        mappedBy = "order": "esta lista es el reflejo de OrderLine.order". Si
        cambias solo la lista y no el campo del dueño, NO se guarda nada.
        Por eso se agrega con un método que arregla los dos lados
        (addLine).

        cascade = ALL y orphanRemoval = true en las líneas: lo que se le hace
        al pedido (guardar, borrar) se le hace a sus líneas, y una línea
        quitada de la lista se borra. Es el AGREGADO de DDD (DDDBasics,
        CAPA 5): el pedido es la raíz y es dueño de sus líneas. No pongas
        cascade en relaciones con otros agregados (el cliente): borrar un
        pedido no debe borrar al cliente.

        Y el valor por defecto que hay que cambiar SIEMPRE:
        - @ManyToOne y @OneToOne son EAGER por defecto: cargar un pedido
          carga su cliente aunque no lo uses (y en listas, eso es N+1 sin
          tocar nada: CAPA 6). Se ponen fetch = LAZY;
        - @OneToMany y @ManyToMany ya son LAZY.
    */
    @Entity(name = "PurchaseOrder")
    @Table(name = "orders")
    public static class PurchaseOrder {
        @Id
        @GeneratedValue(strategy = GenerationType.SEQUENCE)
        private Long id;

        @ManyToOne(fetch = FetchType.LAZY, optional = false)   // LAZY: el defecto (EAGER) es una trampa
        @JoinColumn(name = "customer_id")
        private Customer customer;

        @Enumerated(EnumType.STRING)                         // texto, nunca la posición
        @Column(nullable = false, length = 20)
        private OrderStatus status = OrderStatus.OPEN;

        @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
        private List<OrderLine> lines = new ArrayList<>();

        protected PurchaseOrder() {
        }

        public PurchaseOrder(Customer customer) {
            this.customer = customer;
        }

        public void addLine(String sku, int quantity, long unitCents) {
            if (status != OrderStatus.OPEN) {
                throw new IllegalStateException("El pedido ya no se puede modificar");   // la raíz protege sus reglas
            }
            lines.add(new OrderLine(this, sku, quantity, new Money(unitCents)));          // los dos lados
        }

        public void pay() {
            status = OrderStatus.PAID;
        }

        public long totalCents() {
            return lines.stream().mapToLong(line -> line.quantity() * line.unitPrice().cents()).sum();
        }

        public Long getId() {
            return id;
        }

        public Customer getCustomer() {
            return customer;
        }

        public OrderStatus getStatus() {
            return status;
        }

        public List<OrderLine> getLines() {
            return Collections.unmodifiableList(lines);      // nadie la cambia sin pasar por la raíz
        }
    }

    @Entity(name = "OrderLine")
    @Table(name = "order_lines")
    public static class OrderLine {
        @Id
        @GeneratedValue(strategy = GenerationType.SEQUENCE)
        private Long id;

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "order_id")                      // el DUEÑO de la relación: aquí está la llave
        private PurchaseOrder order;

        @Column(nullable = false, length = 20)
        private String sku;

        private int quantity;

        @Embedded
        private Money unitPrice;                            // columna amount_cents

        protected OrderLine() {
        }

        OrderLine(PurchaseOrder order, String sku, int quantity, Money unitPrice) {
            this.order = order;
            this.sku = sku;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
        }

        public String sku() {
            return sku;
        }

        public int quantity() {
            return quantity;
        }

        public Money unitPrice() {
            return unitPrice;
        }
    }

    public record OrderSummary(long orderId, String customer, int lines, long totalCents) {
    }

    @Service
    public static class OrderService {
        private final EntityManager entityManager;

        public OrderService(EntityManager entityManager) {
            this.entityManager = entityManager;
        }

        @Transactional
        public long createOrder(String customerName, int lines) {
            Customer customer = new Customer(customerName);
            entityManager.persist(customer);
            PurchaseOrder order = new PurchaseOrder(customer);
            for (int i = 1; i <= lines; i++) {
                order.addLine("SKU-" + i, i, 1_000);
            }
            entityManager.persist(order);                   // las líneas van por cascada
            return order.getId();
        }

        /*
            =============================================================
            CAPA 3) EL CONTEXTO DE PERSISTENCIA: IDENTIDAD Y DIRTY
                    CHECKING
            =============================================================

            Dentro de una transacción, Hibernate lleva un CONTEXTO DE
            PERSISTENCIA (la "sesión"): un mapa de las entidades que cargó.
            Dos consecuencias que hay que conocer:

            1) IDENTIDAD: pedir dos veces el mismo id en la misma
               transacción da el MISMO objeto, y la segunda vez sin ir a la
               base de datos (es una caché de primer nivel). La prueba lo
               comprueba: un find repetido no manda otra consulta.

            2) DIRTY CHECKING: al confirmar, Hibernate compara cada entidad
               cargada con cómo estaba al cargarla, y manda un UPDATE por las
               que cambiaron. NO HACE FALTA llamar a save(): renameCustomer
               solo cambia el nombre, y se guarda. Es cómodo, y sorprende:
               cambiar una entidad cargada en una transacción "solo para
               calcular algo" la guarda.

            Los cambios no se mandan al instante: se juntan y se envían en el
            FLUSH (al confirmar, o antes de una consulta que los necesite).
        */
        @Transactional
        public boolean sameInstanceTwice(long customerId) {
            return entityManager.find(Customer.class, customerId) == entityManager.find(Customer.class, customerId);
        }

        @Transactional
        public void renameCustomer(long customerId, String newName) {
            entityManager.find(Customer.class, customerId).rename(newName);   // sin save: dirty checking
        }

        /*
            =============================================================
            CAPA 5) CARGA PEREZOSA Y LAZYINITIALIZATIONEXCEPTION
            =============================================================

            Con LAZY, order.getCustomer() no es el cliente: es un PROXY
            (una subclase generada; por eso las entidades no pueden ser
            final) que va a la base de datos la PRIMERA vez que lo usas. Y
            order.getLines() es una colección que se llena al recorrerla.

            Para ir a la base de datos necesita la sesión (CAPA 3), que vive
            lo que dura la transacción. Si la entidad salió del servicio y
            alguien la recorre DESPUÉS, falla. Medido:

                LazyInitializationException: Cannot lazily initialize
                collection of role '...PurchaseOrder.lines' with key '1'
                (no session)

            Es la excepción más famosa de JPA. Las soluciones correctas:
            cargar lo que se va a necesitar DENTRO del servicio (CAPA 7), y
            devolver un DTO, no la entidad (CAPA 9).

            La "solución" que Spring Boot trae activada por defecto en
            aplicaciones web, spring.jpa.open-in-view=true, deja la sesión
            abierta toda la petición HTTP para que la vista pueda seguir
            cargando cosas. Esconde el problema y abre consultas N+1 desde
            el controller o al convertir a JSON. Boot avisa con un WARN al
            arrancar; la práctica recomendada es ponerla en false.
        */
        @Transactional(readOnly = true)
        public PurchaseOrder findOrder(long orderId) {
            return entityManager.find(PurchaseOrder.class, orderId);   // sale sin las líneas cargadas
        }

        /*
            =============================================================
            CAPA 6) EL PROBLEMA N+1, MEDIDO
            =============================================================

            La pantalla "mis pedidos" muestra, de cada pedido, el cliente y
            cuántas líneas tiene. El código "natural":

                List<PurchaseOrder> orders = select o from PurchaseOrder o;  // 1 consulta
                for (o : orders) {
                    o.getCustomer().getName();     // 1 consulta POR pedido
                    o.getLines().size();           // otra POR pedido
                }

            Con 10 pedidos (cada uno de un cliente distinto): 1 + 10 + 10 =
            21 consultas, medido con las estadísticas de Hibernate. Con 1,000
            pedidos, 2,001. Cada una es un viaje a la base de datos (~1 ms
            en la misma red: jdbc/JdbcAndTransactions, CAPA 1). Es el
            problema de rendimiento más común de las aplicaciones con JPA,
            y no se ve en el código: parece un for normal.

            (Si dos pedidos son del MISMO cliente, la segunda vez no hay
            consulta: el contexto de persistencia ya lo tiene, CAPA 3. Por
            eso el N+1 a veces "no aparece" en pruebas con pocos datos.)
        */
        @Transactional(readOnly = true)
        public List<OrderSummary> summariesNaive() {
            List<PurchaseOrder> orders = entityManager
                    .createQuery("select o from PurchaseOrder o order by o.id", PurchaseOrder.class)
                    .getResultList();
            return orders.stream().map(JpaEntities::summarize).toList();   // cada pedido: 2 consultas más
        }

        /*
            =============================================================
            CAPA 7) RESOLVER EL N+1
            =============================================================

            Decirle a Hibernate, EN LA CONSULTA, qué relaciones traer junto:

                select o from PurchaseOrder o
                join fetch o.customer
                left join fetch o.lines

            "join fetch" trae el cliente y las líneas en la MISMA consulta
            (un JOIN de SQL). Medido: 1 consulta en vez de 21.

            Otras herramientas:
            - @EntityGraph en un repositorio de Spring Data: lo mismo, sin
              escribir el JPQL (repositories/SpringDataRepositories);
            - hibernate.default_batch_fetch_size=50 (o @BatchSize): cuando
              se recorren las relaciones perezosas, Hibernate las pide de 50
              en 50 con "where id in (...)": 1 + N/50 consultas. Un buen
              valor global de seguridad;
            - una PROYECCIÓN: si solo se necesitan unos datos, consultar
              directamente un DTO, sin entidades (repositories/
              SpringDataRepositories; v11_patterns/part4_modern/DTO, CAPA 6).

            Cuidado: "join fetch" de DOS colecciones a la vez multiplica las
            filas (producto cartesiano). Una colección por consulta.
        */
        @Transactional(readOnly = true)
        public List<OrderSummary> summariesJoinFetch() {
            List<PurchaseOrder> orders = entityManager.createQuery("""
                    select o from PurchaseOrder o
                    join fetch o.customer
                    left join fetch o.lines
                    order by o.id""", PurchaseOrder.class).getResultList();
            return orders.stream().map(JpaEntities::summarize).toList();   // ya todo cargado: 0 consultas más
        }
    }

    private static OrderSummary summarize(PurchaseOrder order) {
        return new OrderSummary(order.getId(), order.getCustomer().getName(), order.getLines().size(),
                order.totalCents());
    }

    /*
        =================================================================
        CAPA 8) EL ESQUEMA: QUIÉN CREA LAS TABLAS
        =================================================================

        Hibernate puede crear las tablas a partir de las entidades:

            spring.jpa.hibernate.ddl-auto=
                none          no toca el esquema (por defecto con PostgreSQL)
                validate      revisa que las tablas coincidan; si no, no arranca
                update        intenta modificarlas (NUNCA en producción: no
                              borra columnas, no migra datos, y puede fallar a
                              medias)
                create-drop   las crea al arrancar y las borra al cerrar

        Las pruebas de esta guía usan create-drop, para no depender de nada.
        En una aplicación real, el esquema se VERSIONA con migraciones
        (part3_data/.../migrations/Migrations, con Flyway), y Hibernate se
        pone en validate: si una entidad no coincide con la tabla, la
        aplicación no arranca, en vez de fallar en la primera consulta.
    */

    /*
        =================================================================
        CAPA 9) ENTIDADES FUERA DE LA CAPA DE DATOS
        =================================================================

        No devuelvas entidades en un controller ni las conviertas a JSON:
        - Jackson recorre TODAS sus propiedades: dispara la carga de cada
          relación perezosa (N+1 al serializar) o lanza
          LazyInitializationException;
        - con relaciones de dos lados (pedido -> líneas -> pedido), recursión
          infinita;
        - el contrato de tu API queda atado a las tablas (DTO, CAPA 8), y
          expone campos internos (DTO, CAPA 1).

        El servicio devuelve DTOs (OrderSummary, arriba), armados DENTRO de
        la transacción con lo que se cargó a propósito.
    */

    /*
        =================================================================
        CAPA 10) ERRORES COMUNES
        =================================================================

        1) No mirar el SQL que genera Hibernate (CAPA 1).

        2) Entidades como records o final; @Enumerated ORDINAL; IDENTITY
           cuando se insertan muchas filas (CAPA 2).

        3) Llamar a save() "por si acaso", o no saber que cambiar una
           entidad cargada la guarda (CAPA 3).

        4) Cambiar solo el lado mappedBy de una relación; cascade hacia otros
           agregados; dejar @ManyToOne en EAGER (CAPA 4).

        5) Resolver LazyInitializationException con open-in-view o con EAGER
           (CAPA 5).

        6) Recorrer relaciones perezosas en un ciclo: N+1 (CAPAS 6 y 7).

        7) ddl-auto=update en producción (CAPA 8).

        8) Entidades en las respuestas de la API (CAPA 9).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - JPA es la especificación (jakarta.persistence); Hibernate, la
          implementación.
        - Entidad: constructor sin argumentos, no final, no record; enums
          STRING; ids por SEQUENCE; value objects como @Embeddable records.
        - Contexto de persistencia: un objeto por id, y los cambios se
          guardan solos al confirmar (dirty checking).
        - Relaciones: el dueño tiene la llave; mappedBy en el otro lado;
          cascade solo dentro del agregado; @ManyToOne LAZY.
        - LAZY fuera de la transacción: LazyInitializationException. Cargar
          lo necesario adentro y devolver DTOs; open-in-view=false.
        - N+1: 1 + 2N consultas (21 medidas con 10 pedidos); join fetch, 1.
        - Esquema con migraciones; Hibernate en validate.
    */
}
