package com.practice.spring.production.async;

import static org.assertj.core.api.Assertions.assertThat;

import com.practice.spring.production.async.AsyncSchedulingCaching.Delivery;
import com.practice.spring.production.async.AsyncSchedulingCaching.ReceiptService;
import com.practice.spring.production.async.AsyncSchedulingCaching.TasksApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;

/**
 * AsyncSchedulingCaching, CAPA 6: la misma aplicación con spring.threads.virtual.enabled=true.
 * Es otra clase porque una propiedad distinta es otro contexto (part4_testing/.../IntegrationTests).
 * Correr desde v15_spring/:  ./mvnw -pl part6_production test -Dtest=VirtualThreadsTest
 */
@SpringBootTest(classes = TasksApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.threads.virtual.enabled=true")
class VirtualThreadsTest {

    @Autowired private ReceiptService receipts;
    @Autowired @Qualifier("applicationTaskExecutor") private TaskExecutor executor;

    @Test
    void capa6_conUnaPropiedadAsyncUsaHilosVirtuales() {
        Delivery delivery = receipts.sendReceipt("V-1").join();
        assertThat(delivery.virtualThread()).isTrue();
        assertThat(executor).isInstanceOf(SimpleAsyncTaskExecutor.class);   // sin pool: un hilo por tarea
    }
}
