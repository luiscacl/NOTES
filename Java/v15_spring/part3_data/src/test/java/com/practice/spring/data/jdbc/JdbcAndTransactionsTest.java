package com.practice.spring.data.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.practice.spring.data.jdbc.JdbcAndTransactions.Account;
import com.practice.spring.data.jdbc.JdbcAndTransactions.AccountNotFoundException;
import com.practice.spring.data.jdbc.JdbcAndTransactions.AccountRepository;
import com.practice.spring.data.jdbc.JdbcAndTransactions.AuditService;
import com.practice.spring.data.jdbc.JdbcAndTransactions.BankApplication;
import com.practice.spring.data.jdbc.JdbcAndTransactions.InventoryService;
import com.practice.spring.data.jdbc.JdbcAndTransactions.ReceiptFailedException;
import com.practice.spring.data.jdbc.JdbcAndTransactions.TransferService;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.CannotCreateTransactionException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Las comprobaciones de JdbcAndTransactions contra PostgreSQL 17 real (Docker, Testcontainers).
 * Pool de 3 conexiones para poder agotarlo en la CAPA 9.
 * Correr desde v15_spring/:  ./mvnw -pl part3_data test -Dtest=JdbcAndTransactionsTest
 */
@SpringBootTest(classes = BankApplication.class, properties = {
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:jdbc/schema.sql",
        "spring.datasource.hikari.maximum-pool-size=3",
        "spring.datasource.hikari.connection-timeout=500"})
@Testcontainers
class JdbcAndTransactionsTest {

    @Container
    @ServiceConnection                                      // la URL, el usuario y el password del contenedor
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @Autowired private JdbcClient jdbc;
    @Autowired private DataSource dataSource;
    @Autowired private AccountRepository accounts;
    @Autowired private TransferService transfers;
    @Autowired private AuditService audit;
    @Autowired private InventoryService inventory;

    @BeforeEach
    void freshData() {
        jdbc.sql("delete from account").update();
        jdbc.sql("delete from audit_log").update();
        jdbc.sql("delete from product").update();
        accounts.insert(new Account(1, "Ana", 10_000));
        accounts.insert(new Account(2, "Luis", 5_000));
        jdbc.sql("insert into product (sku, name, stock) values ('CAF-001', 'Café', 10), "
                + "('TAZ-002', 'Taza', 10), ('FIL-003', 'Filtro', 10)").update();
    }

    private long balanceOf(long id) {
        return accounts.findById(id).orElseThrow().balanceCents();
    }

    @Test
    void capa1_unPoolHikariYCadaConexionEsUnProcesoDelServidor() throws Exception {
        assertThat(dataSource).isInstanceOf(HikariDataSource.class);
        List<Integer> pids = new ArrayList<>();
        try (Connection first = dataSource.getConnection(); Connection second = dataSource.getConnection()) {
            for (Connection connection : List.of(first, second)) {
                try (ResultSet rs = connection.createStatement().executeQuery("select pg_backend_pid()")) {
                    rs.next();
                    pids.add(rs.getInt(1));
                }
            }
        }
        assertThat(pids.get(0)).isNotEqualTo(pids.get(1));   // dos conexiones, dos procesos de PostgreSQL
    }

    @Test
    void capa2_jdbcClientMapeaARecordsYUsaParametros() {
        accounts.insert(new Account(3, "O'Brien'); drop table account; --", 1));   // solo es un valor
        assertThat(accounts.findById(3)).get().extracting(Account::owner).isEqualTo("O'Brien'); drop table account; --");
        assertThat(accounts.findAll()).extracting(Account::balanceCents).containsExactly(10_000L, 5_000L, 1L);
        assertThat(accounts.findById(99)).isEmpty();
    }

    @Test
    void capa3y4_unaTransferenciaEsTodoONada() {
        transfers.transfer(1, 2, 3_000);
        assertThat(balanceOf(1)).isEqualTo(7_000);
        assertThat(balanceOf(2)).isEqualTo(8_000);

        assertThatThrownBy(() -> transfers.transfer(1, 99, 1_000))         // la cuenta destino no existe
                .isInstanceOf(AccountNotFoundException.class);
        assertThat(balanceOf(1)).isEqualTo(7_000);                          // rollback: el primer UPDATE se deshizo

        assertThatThrownBy(() -> transfers.transfer(2, 1, 50_000))         // dejaría saldo negativo
                .isInstanceOf(DataIntegrityViolationException.class);       // el CHECK de la tabla, traducido
        assertThat(balanceOf(2)).isEqualTo(8_000);
    }

    @Test
    void capa5_unaCheckedHaceCommitSalvoRollbackFor() {
        assertThatThrownBy(() -> transfers.transferWithReceipt(1, 2, 1_000)).isInstanceOf(ReceiptFailedException.class);
        assertThat(balanceOf(1)).isEqualTo(9_000);                          // ¡se quitó! COMMIT

        assertThatThrownBy(() -> transfers.transferWithReceiptRollingBack(1, 2, 1_000))
                .isInstanceOf(ReceiptFailedException.class);
        assertThat(balanceOf(1)).isEqualTo(9_000);                          // ahora sí, rollback
    }

    @Test
    void capa6_laAutoInvocacionNoTieneTransaccion() {
        assertThatThrownBy(() -> transfers.transferAndLog(1, 99, 2_000))
                .isInstanceOf(AccountNotFoundException.class);
        assertThat(balanceOf(1)).isEqualTo(8_000);                          // el dinero salió y no llegó a nadie

        assertThat(transfers.insideTransaction()).isTrue();                 // desde afuera: por el proxy
        assertThat(transfers.insideTransactionViaThis()).isFalse();         // con this: sin proxy, sin transacción
    }

    @Test
    void capa7_requiresNewSobreviveAlRollback() {
        assertThatThrownBy(() -> transfers.auditedTransfer(1, 99, 500)).isInstanceOf(AccountNotFoundException.class);
        assertThat(balanceOf(1)).isEqualTo(10_000);                         // la transferencia: rollback
        assertThat(audit.messages()).containsExactly("intento de transferir 500 de 1 a 99");   // la auditoría: quedó
    }

    @Test
    void capa8_actualizacionPerdidaYLaSentenciaAtomica() throws Exception {
        CyclicBarrier bothRead = new CyclicBarrier(2);
        Runnable waitForTheOther = () -> {
            try {
                bothRead.await(5, TimeUnit.SECONDS);        // las dos ya leyeron 10
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        };
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<?> a = pool.submit(() -> inventory.reserveNaive("CAF-001", 2, waitForTheOther));
            Future<?> b = pool.submit(() -> inventory.reserveNaive("CAF-001", 2, waitForTheOther));
            a.get(10, TimeUnit.SECONDS);
            b.get(10, TimeUnit.SECONDS);
        }
        assertThat(inventory.stockOf("CAF-001")).isEqualTo(8);              // se apartaron 4, se descontaron 2

        jdbc.sql("update product set stock = 10 where sku = 'CAF-001'").update();
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<Boolean> a = pool.submit(() -> inventory.reserveAtomic("CAF-001", 2));
            Future<Boolean> b = pool.submit(() -> inventory.reserveAtomic("CAF-001", 2));
            assertThat(a.get(10, TimeUnit.SECONDS) && b.get(10, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(inventory.stockOf("CAF-001")).isEqualTo(6);              // correcto
        assertThat(inventory.reserveAtomic("CAF-001", 7)).isFalse();        // no alcanza: 0 filas, nada cambia
        assertThat(inventory.stockOf("CAF-001")).isEqualTo(6);
    }

    @Test
    void capa9_esperarConLaTransaccionAbiertaAgotaElPool() throws Exception {
        CountDownLatch providerResponds = new CountDownLatch(1);
        CountDownLatch allHolding = new CountDownLatch(3);
        Runnable slowProvider = () -> {
            allHolding.countDown();
            try {
                providerResponds.await(10, TimeUnit.SECONDS);   // "esperando al proveedor de pagos"
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
        try (ExecutorService pool = Executors.newFixedThreadPool(3)) {
            for (String sku : List.of("CAF-001", "TAZ-002", "FIL-003")) {      // filas distintas (ver CAPA 9)
                pool.submit(() -> inventory.reserveWhileCallingProvider(sku, slowProvider));
            }
            assertThat(allHolding.await(10, TimeUnit.SECONDS)).isTrue();    // las 3 conexiones, tomadas

            assertThatThrownBy(() -> inventory.reserveAtomic("CAF-001", 1))  // la 4.a petición
                    .isInstanceOf(CannotCreateTransactionException.class)
                    .rootCause().hasMessageContaining("Connection is not available, request timed out after");
            providerResponds.countDown();
        }
    }
}
