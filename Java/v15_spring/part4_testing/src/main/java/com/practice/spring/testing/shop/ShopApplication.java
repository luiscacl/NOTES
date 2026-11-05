package com.practice.spring.testing.shop;

import java.time.Clock;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * La clase principal de la tienda que prueban las guías de part4_testing. Limpia a propósito:
 * las pruebas de una sola capa (@WebMvcTest, @DataJpaTest) la usan como configuración, así que
 * todo lo que se ponga aquí se carga también en ellas (UnitAndSliceTests, CAPA 8).
 */
@SpringBootApplication
public class ShopApplication {

    @Bean
    Clock clock() {
        return Clock.systemUTC();                           // el tiempo es una dependencia: las pruebas ponen uno fijo
    }
}
