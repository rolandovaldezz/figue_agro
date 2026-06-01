package com.agrosmart.alert;                                            // Paquete raiz del microservicio de alertas

import org.springframework.boot.SpringApplication;                      // Clase de Spring Boot que arranca (bootstrap) la aplicacion
import org.springframework.boot.autoconfigure.SpringBootApplication;    // Anotacion que activa la autoconfiguracion y el escaneo de componentes

/**
 * Microservicio de alertas.
 *
 * Responsabilidades:
 *  - Suscribirse a los topics MQTT (agrosmart/+/+)
 *  - Evaluar cada lectura contra las reglas de umbrales_alerta
 *  - Persistir alertas en MariaDB
 *  - Exponer GET /alertas y POST /alertas/{id}/resolver
 *
 * Los componentes reales se agregan en la siguiente entrega.
 */
@SpringBootApplication                                                  // Marca esta clase como la app Spring Boot: combina @Configuration, @EnableAutoConfiguration y @ComponentScan
public class AlertServiceApplication {                                  // Clase principal que representa al microservicio alert-service

    public static void main(String[] args) {                           // Metodo de entrada de Java: es lo primero que se ejecuta al iniciar
        SpringApplication.run(AlertServiceApplication.class, args);     // Levanta el contexto de Spring, el servidor web y todos los beans
    }
}
