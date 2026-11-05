package com.practice.spring.data.repositories;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/*
    =====================================================================
    SPRING DATA JPA: REPOSITORIOS QUE SPRING ESCRIBE POR TI
    =====================================================================

    Ruta de spring/: guía 11  (part3_data)
    Anterior:  part3_data/.../jpa/JpaEntities
    Siguiente: part3_data/.../migrations/Migrations

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo.
    - Las comprobaciones son pruebas: SpringDataRepositoriesTest, contra
      PostgreSQL 17 real, contando sentencias SQL donde importa. Desde
      v15_spring/:
          ./mvnw -pl part3_data test -Dtest=SpringDataRepositoriesTest
    - Versión: Spring Data JPA 4.1. Mensajes y conteos: corridas reales.

    Lo que ya se vio: el patrón Repository y cómo Spring lo genera
    (v11_patterns/part4_modern/Repository, CAPA 9), las entidades y el N+1
    (jpa/JpaEntities), las proyecciones como DTO (v11_patterns/
    part4_modern/DTO, CAPA 6), las actualizaciones perdidas (jdbc/
    JdbcAndTransactions, CAPA 8), Interface Segregation (v10_design/
    part2_solid/InterfaceSegregation).

    Importancia: MUY ALTA. Es como se accede a datos en casi todo proyecto
    Spring Boot.

    Índice:
        CAPA 1)  Una interface, y Spring escribe la clase
        CAPA 2)  Qué interface extender
        CAPA 3)  Consultas a partir del nombre del método
        CAPA 4)  @Query: JPQL y SQL nativo
        CAPA 5)  Paginación: Page o Slice
        CAPA 6)  Proyecciones: traer solo lo que se necesita
        CAPA 7)  @EntityGraph contra el N+1
        CAPA 8)  Bloqueo optimista con @Version
        CAPA 9)  save(), transacciones y actualizaciones masivas
        CAPA 10) Errores comunes
        RESUMEN FINAL
*/
public class SpringDataRepositories {

    /*
        =================================================================
        CAPA 1) UNA INTERFACE, Y SPRING ESCRIBE LA CLASE
        =================================================================

        Con Spring Data, un repositorio es SOLO una interface:

            interface ProductRepository extends JpaRepository<Product, Long> {
                Optional<Product> findBySku(String sku);
            }

        Al arrancar, Spring Data encuentra las interfaces que extienden
        Repository, y para cada una crea un PROXY (part1_core/.../container/
        IoCContainer, CAPA 8) que la implementa: los métodos comunes (save,
        findById, delete...) los atiende una clase de Spring
        (SimpleJpaRepository), y los tuyos (findBySku) los convierte en una
        consulta, leyendo el NOMBRE del método (CAPA 3). La prueba comprueba
        que lo inyectado es un proxy.

        Es el patrón Repository (v11_patterns/part4_modern/Repository) con la
        implementación generada. Todo lo de esa guía sigue valiendo: un
        repositorio por agregado, nombres del negocio, Optional para uno,
        listas con límite.

        Spring Data busca los repositorios desde el paquete de la clase
        @SpringBootApplication hacia abajo (como el escaneo de componentes).
        Detalle de ESTE proyecto: por defecto no considera interfaces
        ANIDADAS dentro de otra clase (como las de esta guía); por eso
        CatalogApplication lleva @EnableJpaRepositories(
        considerNestedRepositories = true). En un proyecto normal, cada
        repositorio es su propio archivo y no hace falta.
    */
    @SpringBootApplication
    @EnableJpaRepositories(considerNestedRepositories = true)
    public static class CatalogApplication {
    }

    @Entity(name = "Supplier")                              // nombre explícito: clases anidadas (JpaEntities, CAPA 2)
    @Table(name = "suppliers")
    public static class Supplier {
        @Id
        @GeneratedValue(strategy = GenerationType.SEQUENCE)
        private Long id;

        @Column(nullable = false)
        private String name;

        protected Supplier() {
        }

        public Supplier(String name) {
            this.name = name;
        }

        public Long getId() {
            return id;
        }

        public String getName() {
            return name;
        }
    }

    @Entity(name = "Product")
    @Table(name = "products")
    public static class Product {
        @Id
        @GeneratedValue(strategy = GenerationType.SEQUENCE)
        private Long id;

        @Column(nullable = false, unique = true, length = 20)
        private String sku;

        @Column(nullable = false)
        private String name;

        @Column(nullable = false, length = 30)
        private String category;

        private long priceCents;

        private boolean active = true;

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        private Supplier supplier;

        @Version                                            // CAPA 8: bloqueo optimista
        private Long version;

        protected Product() {
        }

        public Product(String sku, String name, String category, long priceCents, Supplier supplier) {
            this.sku = sku;
            this.name = name;
            this.category = category;
            this.priceCents = priceCents;
            this.supplier = supplier;
        }

        public void changePrice(long newPriceCents) {
            this.priceCents = newPriceCents;
        }

        public Long getId() {
            return id;
        }

        public String getSku() {
            return sku;
        }

        public String getName() {
            return name;
        }

        public long getPriceCents() {
            return priceCents;
        }

        public Supplier getSupplier() {
            return supplier;
        }

        public Long getVersion() {
            return version;
        }
    }

    /*
        =================================================================
        CAPA 2) QUÉ INTERFACE EXTENDER
        =================================================================

            Repository<T, ID>          vacía: solo marca. Declaras SOLO los
                                       métodos que quieres exponer
            CrudRepository / ListCrudRepository   save, findById, findAll,
                                       delete, count... (List* devuelve List
                                       en vez de Iterable)
            PagingAndSortingRepository  + findAll(Pageable), findAll(Sort)
            JpaRepository              todo lo anterior + flush, saveAll en
                                       lote, getReferenceById...

        JpaRepository es lo más común, pero expone TODO: deleteAll(),
        findAll() sin límite... a cualquiera que reciba el repositorio. Para
        un agregado importante, la opción más cuidadosa es extender
        Repository y declarar solo lo necesario (Interface Segregation:
        SupplierRepository, abajo). Para catálogos simples, JpaRepository
        está bien.
    */
    public interface SupplierRepository extends Repository<Supplier, Long> {   // solo lo que se usa
        Supplier save(Supplier supplier);

        Optional<Supplier> findById(Long id);
    }

    /*
        =================================================================
        CAPA 3) CONSULTAS A PARTIR DEL NOMBRE DEL MÉTODO
        =================================================================

        Spring Data LEE el nombre del método y arma la consulta:

            findBySku(String sku)                where sku = ?
            findByCategoryAndPriceCentsLessThanOrderByPriceCentsAsc(c, max)
                                                 where category = ? and price_cents < ?
                                                 order by price_cents asc
            existsBySku(String sku)              select ... limit 1 (sí/no)
            countByActiveTrue()                  select count(*) where active = true
            findFirst3ByCategoryOrderByPriceCentsDesc(c)   los 3 más caros
            findByNameContainingIgnoreCase(t)    where name like %t%, sin distinguir mayúsculas

        Palabras clave: And, Or, Between, LessThan, GreaterThanEqual, Like,
        Containing, StartingWith, In, IsNull, True/False, IgnoreCase,
        OrderBy...Asc/Desc, First/Top, Distinct.

        Y lo mejor: se revisan AL ARRANCAR. Un nombre con una propiedad que
        no existe (findByNmae) hace que la aplicación no arranque. Medido:

            No property 'nmae' found for type 'Product'; Did you mean 'name'

        Pasó de verdad al escribir esta guía: el repositorio de prueba con ese
        error quedó por accidente dentro del paquete que escanea la
        aplicación, y la aplicación COMPLETA no arrancó ("Error creating bean
        with name '...BrokenRepository' ... No property 'nmae' found...").
        Ninguna prueba llegó a correr. Mejor ahí que en producción.

        Ventaja: cero código, y el error aparece al desplegar, no en
        producción. Límite: con más de 2 o 3 condiciones el nombre se vuelve
        ilegible; ahí, @Query (CAPA 4).
    */
    public interface ProductRepository extends JpaRepository<Product, Long> {
        Optional<Product> findBySku(String sku);

        List<Product> findByCategoryAndPriceCentsLessThanOrderByPriceCentsAsc(String category, long maxPriceCents);

        boolean existsBySku(String sku);

        long countByActiveTrue();

        List<Product> findFirst3ByCategoryOrderByPriceCentsDesc(String category);

        List<Product> findByNameContainingIgnoreCase(String text);

        /*
            =============================================================
            CAPA 4) @QUERY: JPQL Y SQL NATIVO
            =============================================================

            Cuando el nombre no alcanza, se escribe la consulta:

            - JPQL (el lenguaje de JPA): se escribe sobre ENTIDADES y
              propiedades, no sobre tablas. Se revisa al arrancar, igual que
              los nombres derivados.
            - nativeQuery = true: SQL de la base de datos, para lo que JPQL
              no tiene (funciones de PostgreSQL, ventanas, CTEs). No se revisa
              al arrancar y te ata a esa base de datos.

            Parámetros con nombre (:category con @Param, o el nombre del
            parámetro si se compila con -parameters, como hace Boot). Nunca
            concatenados (inyección SQL).
        */
        @Query("select p from Product p where p.category = :category and p.active = true order by p.priceCents")
        List<Product> activeInCategory(@Param("category") String category);

        @Query(value = "select coalesce(avg(price_cents), 0) from products where category = :category", nativeQuery = true)
        double averagePrice(@Param("category") String category);

        /*
            =============================================================
            CAPA 5) PAGINACIÓN: PAGE O SLICE
            =============================================================

            Nunca "todos" (v11_patterns/part4_modern/Repository, CAPA 8).
            Se pasa un Pageable: número de página, tamaño y orden:

                PageRequest.of(0, 20, Sort.by("priceCents").descending())

            Y el tipo de retorno decide cuánto trabajo hace:
            - Page<T>: la página + el TOTAL de elementos y de páginas. Para
              saber el total, Spring Data hace una SEGUNDA consulta (count).
              Medido: 2 sentencias.
            - Slice<T>: la página + si HAY SIGUIENTE (pide un elemento de
              más para saberlo). Medido: 1 sentencia. Para "cargar más" o
              scroll infinito, sin el costo del count (que en tablas grandes
              puede ser lo más caro).

            (Para exponerlo en una API REST, no devuelvas Page tal cual: su
            JSON no es estable entre versiones. Spring Data trae PagedModel,
            o se arma un DTO con los datos de la página.)
        */
        Page<Product> findByCategory(String category, Pageable pageable);

        Slice<Product> findSliceByCategory(String category, Pageable pageable);

        /*
            =============================================================
            CAPA 6) PROYECCIONES: TRAER SOLO LO QUE SE NECESITA
            =============================================================

            Para una lista que muestra sku, nombre y precio, cargar la
            entidad completa (con su versión, su proveedor perezoso, el
            contexto de persistencia vigilándola para el dirty checking) es
            trabajo de más. Una PROYECCIÓN devuelve un DTO directamente:

                List<ProductSummary> findSummariesByCategory(String category);

            con ProductSummary un record cuyos componentes se llaman como
            las propiedades. Spring Data hace un select de SOLO esas columnas
            y devuelve records, no entidades (no hay dirty checking, no hay
            LazyInitializationException). Es la proyección de v11_patterns/
            part4_modern/DTO, CAPA 6, sin escribir la consulta.
        */
        List<ProductSummary> findSummariesByCategoryOrderBySku(String category);

        /*
            =============================================================
            CAPA 7) @ENTITYGRAPH CONTRA EL N+1
            =============================================================

            Listar productos y mostrar el nombre del proveedor: el N+1 de
            jpa/JpaEntities, CAPA 6 (1 + uno por producto). @EntityGraph le
            dice a Spring Data qué relaciones traer en la misma consulta
            (un join fetch, sin escribir el JPQL):

                @EntityGraph(attributePaths = "supplier")

            Medido con 6 productos de 6 proveedores: sin el grafo, 7
            sentencias; con el grafo, 1.
        */
        List<Product> findByActiveTrueOrderBySku();

        @EntityGraph(attributePaths = "supplier")
        List<Product> findWithSupplierByActiveTrueOrderBySku();

        /*
            =============================================================
            CAPA 9) SAVE(), TRANSACCIONES Y ACTUALIZACIONES MASIVAS
            =============================================================

            - save(entidad): si es NUEVA (id null, o version null) hace
              persist; si no, merge (copia el estado sobre la entidad
              administrada y DEVUELVE esa otra instancia). Usa siempre lo que
              devuelve save, no el objeto que le pasaste.
            - Dentro de una transacción, una entidad cargada se guarda sola al
              confirmar (dirty checking, JpaEntities, CAPA 3): save() no hace
              falta.
            - Los métodos que HEREDAS de JpaRepository (save, findById...) ya
              son transaccionales (los de lectura, readOnly); los que TÚ
              declaras (findBy...) no traen transacción propia. En los dos
              casos, el CASO DE USO que llama a varios va en un servicio con
              su propio @Transactional
              (jdbc/JdbcAndTransactions, CAPA 4).

            Actualizaciones masivas con @Modifying + @Query: un UPDATE que
            toca muchas filas en una sentencia, en vez de cargar cada entidad.
            Pero va DIRECTO a la base de datos, sin pasar por el contexto de
            persistencia: las entidades ya cargadas quedan VIEJAS.
            clearAutomatically = true limpia el contexto después, para que la
            siguiente lectura vea lo nuevo. Necesita una transacción.
        */
        @Modifying(clearAutomatically = true)
        @Transactional
        @Query("update Product p set p.priceCents = p.priceCents * :factor / 100 where p.category = :category")
        int adjustPrices(@Param("category") String category, @Param("factor") long percent);
    }

    public record ProductSummary(String sku, String name, long priceCents) {
    }

    /*
        =================================================================
        CAPA 8) BLOQUEO OPTIMISTA CON @VERSION
        =================================================================

        La actualización perdida de jdbc/JdbcAndTransactions, CAPA 8, pero
        entre PERSONAS: Ana abre la pantalla de un producto (precio 100) y
        se va a comer; Luis lo abre, lo cambia a 120 y guarda; Ana vuelve,
        cambia la descripción y guarda... con el precio 100 que tenía en su
        pantalla. El cambio de Luis desaparece. No hay transacción que lo
        evite: cada uno guardó en la suya, minutos aparte.

        @Version agrega una columna version que Hibernate incrementa en cada
        UPDATE, y el UPDATE incluye la condición:

            update products set ..., version = 2 where id = ? and version = 1

        Si alguien ya la cambió (version ya no es 1), el UPDATE no toca
        ninguna fila, y Spring lanza ObjectOptimisticLockingFailureException.
        Ana recibe un error en vez de pisar el trabajo de Luis. La prueba lo
        hace con dos copias del mismo producto.

        "Optimista" porque no bloquea nada al leer: apuesta a que casi nunca
        habrá conflicto, y lo detecta al guardar. En la API se traduce a
        409 Conflict (part2_web/.../errors/ErrorHandling), y el cliente
        recarga y reintenta. En las APIs REST, la versión suele viajar en el
        DTO o en el encabezado ETag / If-Match.
    */

    /*
        =================================================================
        CAPA 10) ERRORES COMUNES
        =================================================================

        1) Escribir a mano la clase del repositorio (CAPA 1).

        2) JpaRepository en todo, exponiendo deleteAll y findAll sin límite
           (CAPA 2).

        3) Nombres derivados de 6 condiciones; consultas nativas sin
           necesidad (CAPAS 3 y 4).

        4) Page cuando no se muestra el total (un count de más en cada
           página) (CAPA 5).

        5) Cargar entidades completas para mostrar una lista (CAPA 6).

        6) Listas que recorren relaciones perezosas sin @EntityGraph ni join
           fetch (CAPA 7).

        7) Pantallas de edición sin @Version: el último que guarda pisa a los
           demás (CAPA 8).

        8) Ignorar lo que devuelve save(); @Modifying sin limpiar el
           contexto (CAPA 9).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Repositorio = interface; Spring Data genera un proxy al arrancar.
        - Extender JpaRepository, o Repository con solo lo necesario.
        - Consultas por nombre del método, revisadas al arrancar ("No
          property 'nmae'... Did you mean 'name'").
        - @Query en JPQL (revisado) o nativo (no revisado).
        - Pageable: Page hace count (2 sentencias), Slice no (1).
        - Proyecciones a records: solo las columnas necesarias.
        - @EntityGraph: 7 sentencias -> 1, medido.
        - @Version: el segundo que guarda recibe un error en vez de pisar al
          primero.
        - save() devuelve la instancia a usar; @Modifying con
          clearAutomatically.
    */
}
