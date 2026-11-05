package com.practice.spring.web.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.practice.spring.web.rest.RestControllers.DiagnosticsController;
import com.practice.spring.web.rest.RestControllers.ProductCatalog;
import com.practice.spring.web.rest.RestControllers.ProductController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * Las comprobaciones de RestControllers SIN SERVIDOR (CAPA 8, forma 1): @WebMvcTest arranca
 * solo la capa web con ProductController, y MockMvcTester simula las peticiones.
 * El servicio se importa tal cual (es en memoria); con uno real se usaría un falso (part4_testing).
 */
@WebMvcTest(ProductController.class)
@Import(ProductCatalog.class)
class RestControllersSliceTest {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private ApplicationContext context;

    @Test
    void capa8_soloLaCapaWebYSinPuerto() {
        assertThat(context.getBeansOfType(ProductController.class)).hasSize(1);
        assertThat(context.getBeansOfType(DiagnosticsController.class)).isEmpty();   // otros controllers no se cargan

        assertThat(mvc.get().uri("/api/products/1"))
                .hasStatusOk()
                .bodyJson().extractingPath("$.name").isEqualTo("Café de Chiapas");
        assertThat(mvc.get().uri("/api/products/999")).hasStatus(HttpStatus.NOT_FOUND);
    }
}
