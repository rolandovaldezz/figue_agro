// Declaracion del paquete: ubica esta clase dentro de com.agrosmart.gateway
package com.agrosmart.gateway;

// Importa la clase que arranca una aplicacion Spring Boot
import org.springframework.boot.SpringApplication;
// Importa la anotacion que marca esta clase como aplicacion Spring Boot
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * API Gateway de AgroSmart.
 *
 * Responsabilidades:
 *  - Punto único de entrada al sistema (https://host:8443)
 *  - Terminación TLS/SSL con certificado del keystore
 *  - Validación de JWT en cada request (excepto /api/auth/login)
 *  - Enrutamiento a los microservicios internos:
 *      /api/auth/**     → auth-service:8081
 *      /api/sensores/** → sensor-service:8082
 *      /api/alertas/**  → alert-service:8083
 *
 * Los filtros JWT y rutas se configuran en la siguiente entrega.
 */
// Anotacion que activa la autoconfiguracion de Spring Boot y el escaneo de componentes
@SpringBootApplication
// Clase principal del API Gateway (puerta unica del sistema)
public class GatewayApplication {

    // Metodo main: punto de entrada que ejecuta la aplicacion al iniciar el contenedor
    public static void main(String[] args) {
        // Arranca el contexto de Spring Boot usando esta clase y los argumentos recibidos
        SpringApplication.run(GatewayApplication.class, args);
    }
}
