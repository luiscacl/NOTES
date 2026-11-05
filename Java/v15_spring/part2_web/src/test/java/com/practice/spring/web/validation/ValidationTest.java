package com.practice.spring.web.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.practice.spring.web.validation.Validation.OrdersApplication;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

/**
 * Las comprobaciones de Validation, una por capa.
 * Correr desde v15_spring/:  ./mvnw -pl part2_web test -Dtest=ValidationTest
 */
@SpringBootTest(classes = OrdersApplication.class)
@AutoConfigureMockMvc
class ValidationTest {

    @Autowired
    private MockMvcTester mvc;

    private MvcTestResult postOrder(String json, String language) {
        return mvc.post().uri("/api/orders")
                .header("Accept-Language", language)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
                .exchange();
    }

    /** Los errores de campo de la excepción, como "campo: mensaje", ordenados. */
    private static List<String> fieldErrors(MvcTestResult result) {
        MethodArgumentNotValidException error =
                (MethodArgumentNotValidException) result.getMvcResult().getResolvedException();
        return error.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .sorted()
                .toList();
    }

    @Test
    void capa3_unPedidoValidoPasa() {
        MvcTestResult result = postOrder("""
                {"email":"ana@correo.com","lines":[{"sku":"CAF-001","quantity":2}]}""", "es");
        assertThat(result).hasStatus(HttpStatus.CREATED)
                .bodyJson().extractingPath("$.lines").isEqualTo(1);
    }

    @Test
    void capa3y4_todosLosErroresJuntosIncluidosLosAnidados() {
        MvcTestResult result = postOrder("""
                {"email":"ana","lines":[{"sku":" ","quantity":0}]}""", "en");
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(fieldErrors(result)).containsExactly(
                "email: must be a well-formed email address",
                "lines[0].quantity: must be greater than 0",
                "lines[0].sku: debe tener la forma ABC-123",              // el mensaje propio no se traduce
                "lines[0].sku: must not be blank");                       // el sku en blanco rompe DOS reglas
    }

    @Test
    void capa4_laListaVaciaTambienEsUnError() {
        MvcTestResult result = postOrder("""
                {"email":"ana@correo.com","lines":[]}""", "en");
        assertThat(fieldErrors(result)).containsExactly("lines: size must be between 1 and 20");
    }

    @Test
    void capa5_parametrosDeLaUrl() {
        assertThat(mvc.get().uri("/api/orders?page=2")).hasStatusOk().hasBodyTextEqualTo("página 2 de 20");
        MvcTestResult invalid = mvc.get().uri("/api/orders?page=0").exchange();
        assertThat(invalid).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(invalid.getMvcResult().getResolvedException()).isInstanceOf(HandlerMethodValidationException.class);
    }

    @Test
    void capa6_restriccionesPropias() {
        MvcTestResult badSku = postOrder("""
                {"email":"ana@correo.com","lines":[{"sku":"cafe","quantity":1}]}""", "es");
        assertThat(fieldErrors(badSku)).containsExactly("lines[0].sku: debe tener la forma ABC-123");

        MvcTestResult badWindow = postOrder("""
                {"email":"ana@correo.com","lines":[{"sku":"CAF-001","quantity":1}],
                 "delivery":{"from":"2026-12-10","to":"2026-12-01"}}""", "es");
        FieldError onType = ((MethodArgumentNotValidException) badWindow.getMvcResult().getResolvedException())
                .getBindingResult().getFieldError("delivery");
        assertThat(onType).isNotNull();
        assertThat(onType.getDefaultMessage()).isEqualTo("la fecha inicial debe ser anterior o igual a la final");
    }

    @Test
    void capa7_elIdiomaLoDecideLaPeticion() {
        String json = """
                {"email":"ana@correo.com","lines":[{"sku":"CAF-001","quantity":0}]}""";
        assertThat(fieldErrors(postOrder(json, "en"))).containsExactly("lines[0].quantity: must be greater than 0");
        assertThat(fieldErrors(postOrder(json, "es"))).containsExactly("lines[0].quantity: debe ser mayor que 0");
    }

    @Test
    void capa8_laValidacionDelServicioNoSeVuelve400() {
        MvcTestResult result = mvc.post().uri("/api/orders/reserve?sku=&quantity=0").exchange();
        assertThat(result.getUnresolvedException())                    // nadie la tradujo: sería un 500
                .hasRootCauseInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("reserve.quantity")
                .hasMessageContaining("reserve.sku");
    }
}
