package com.practice.spring.shop;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;

/**
 * La tienda. Un paquete por funcionalidad (catalog, orders, security) y lo común en shared.
 * Ver final_project/README.md.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ShopApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShopApplication.class, args);
    }

    /** La hora entra como dependencia: las pruebas pueden fijarla (v14_architecture/.../Hexagonal, CAPA 3). */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
