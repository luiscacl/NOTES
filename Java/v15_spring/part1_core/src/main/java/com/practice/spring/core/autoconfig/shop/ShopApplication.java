package com.practice.spring.core.autoconfig.shop;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Apoyo de la guía autoconfig/AutoConfigurationUsage (CAPAS 6 a 8): una aplicación
 * cualquiera que NO menciona a AuditAutoConfiguration y la descubre por el archivo .imports.
 * Vive en su propio subpaquete para que su escaneo de componentes no alcance a la
 * autoconfiguración (AutoConfigurationUsage, CAPA 7).
 */
@SpringBootApplication
public class ShopApplication {
}
