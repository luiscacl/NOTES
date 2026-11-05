package com.practice.spring.data.repositories;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.practice.spring.data.brokenrepo.BrokenRepository;
import com.practice.spring.data.repositories.SpringDataRepositories.CatalogApplication;
import com.practice.spring.data.repositories.SpringDataRepositories.Product;
import com.practice.spring.data.repositories.SpringDataRepositories.ProductRepository;
import com.practice.spring.data.repositories.SpringDataRepositories.ProductSummary;
import com.practice.spring.data.repositories.SpringDataRepositories.Supplier;
import com.practice.spring.data.repositories.SpringDataRepositories.SupplierRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Las comprobaciones de SpringDataRepositories contra PostgreSQL 17 real.
 * Correr desde v15_spring/:  ./mvnw -pl part3_data test -Dtest=SpringDataRepositoriesTest
 */
@SpringBootTest(classes = CatalogApplication.class, properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.generate_statistics=true"})
@Testcontainers
class SpringDataRepositoriesTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @Autowired private ProductRepository products;
    @Autowired private SupplierRepository suppliers;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private EntityManager entityManager;
    @Autowired private TransactionTemplate transactions;

    private Statistics statistics;

    @BeforeEach
    void freshData() {
        products.deleteAllInBatch();
        transactions.executeWithoutResult(status -> entityManager.createQuery("delete from Supplier").executeUpdate());
        String[][] catalog = {
                {"CAF-001", "Café de Chiapas", "café", "18000"},
                {"CAF-002", "Café de Veracruz", "café", "16500"},
                {"CAF-003", "Café descafeinado", "café", "15000"},
                {"TAZ-001", "Taza de barro", "cocina", "12000"},
                {"TAZ-002", "Taza de vidrio", "cocina", "9000"},
                {"FIL-001", "Filtro de tela", "cocina", "4500"}};
        for (String[] row : catalog) {
            Supplier supplier = suppliers.save(new Supplier("Proveedor de " + row[0]));   // uno por producto
            products.save(new Product(row[0], row[1], row[2], Long.parseLong(row[3]), supplier));
        }
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
    }

    private long statements() {
        return statistics.getPrepareStatementCount();
    }

    @Test
    void capa1_elRepositorioEsUnProxyGenerado() {
        assertThat(AopUtils.isAopProxy(products)).isTrue();
        assertThat(products.count()).isEqualTo(6);
    }

    @Test
    void capa3_consultasDerivadasDelNombre() {
        assertThat(products.findBySku("TAZ-001")).get().extracting(Product::getName).isEqualTo("Taza de barro");
        assertThat(products.findByCategoryAndPriceCentsLessThanOrderByPriceCentsAsc("café", 17_000))
                .extracting(Product::getSku).containsExactly("CAF-003", "CAF-002");
        assertThat(products.existsBySku("XXX-999")).isFalse();
        assertThat(products.countByActiveTrue()).isEqualTo(6);
        assertThat(products.findFirst3ByCategoryOrderByPriceCentsDesc("cocina"))
                .extracting(Product::getSku).containsExactly("TAZ-001", "TAZ-002", "FIL-001");
        assertThat(products.findByNameContainingIgnoreCase("TAZA")).hasSize(2);
    }

    @Test
    void capa3_unNombreInvalidoFallaAlCrearElRepositorio() {
        JpaRepositoryFactory factory = new JpaRepositoryFactory(entityManager);
        assertThatThrownBy(() -> factory.getRepository(BrokenRepository.class))
                .rootCause()
                .hasMessageContaining("No property 'nmae' found for type 'Product'")
                .hasMessageContaining("Did you mean 'name'");
    }

    @Test
    void capa4_jpqlYNativo() {
        assertThat(products.activeInCategory("cocina")).extracting(Product::getSku)
                .containsExactly("FIL-001", "TAZ-002", "TAZ-001");
        assertThat(products.averagePrice("café")).isEqualTo(16_500.0);
    }

    @Test
    void capa5_pageHaceCountYSliceNo() {
        PageRequest firstTwo = PageRequest.of(0, 2, Sort.by("priceCents").descending());

        Page<Product> page = products.findByCategory("café", firstTwo);
        long pageStatements = statements();
        assertThat(page.getContent()).extracting(Product::getSku).containsExactly("CAF-001", "CAF-002");
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getTotalPages()).isEqualTo(2);

        statistics.clear();
        Slice<Product> slice = products.findSliceByCategory("café", firstTwo);
        long sliceStatements = statements();
        assertThat(slice.hasNext()).isTrue();

        assertThat(pageStatements).isEqualTo(2);                                // la página + el count
        assertThat(sliceStatements).isEqualTo(1);                               // solo la página (+1 elemento)
    }

    @Test
    void capa6_proyeccionARecords() {
        List<ProductSummary> summaries = products.findSummariesByCategoryOrderBySku("cocina");
        assertThat(summaries).containsExactly(
                new ProductSummary("FIL-001", "Filtro de tela", 4_500),
                new ProductSummary("TAZ-001", "Taza de barro", 12_000),
                new ProductSummary("TAZ-002", "Taza de vidrio", 9_000));
    }

    @Test
    void capa7_entityGraphQuitaElN1() {
        List<String> naive = transactions.execute(status -> products.findByActiveTrueOrderBySku().stream()
                .map(p -> p.getSupplier().getName()).toList());
        long naiveStatements = statements();

        statistics.clear();
        List<String> withGraph = transactions.execute(status -> products.findWithSupplierByActiveTrueOrderBySku().stream()
                .map(p -> p.getSupplier().getName()).toList());
        long graphStatements = statements();

        assertThat(withGraph).isEqualTo(naive).hasSize(6);
        assertThat(naiveStatements).isEqualTo(7);                               // 1 + 6 proveedores
        assertThat(graphStatements).isEqualTo(1);
    }

    @Test
    void capa8_elSegundoQueGuardaRecibeUnError() {
        Product anaCopy = products.findBySku("CAF-001").orElseThrow();          // Ana abre la pantalla
        Product luisCopy = products.findBySku("CAF-001").orElseThrow();         // Luis también
        assertThat(anaCopy).isNotSameAs(luisCopy);

        luisCopy.changePrice(20_000);
        Product saved = products.save(luisCopy);                                // Luis guarda primero
        assertThat(saved.getVersion()).isEqualTo(anaCopy.getVersion() + 1);

        anaCopy.changePrice(17_000);
        assertThatThrownBy(() -> products.save(anaCopy))                        // Ana, con la versión vieja
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        assertThat(products.findBySku("CAF-001").orElseThrow().getPriceCents()).isEqualTo(20_000);   // lo de Luis quedó
    }

    @Test
    void capa9_modifyingVaDirectoYLimpiaElContexto() {
        int updated = products.adjustPrices("cocina", 110);                    // +10%
        assertThat(updated).isEqualTo(3);
        assertThat(products.findBySku("TAZ-001").orElseThrow().getPriceCents()).isEqualTo(13_200);
    }
}
