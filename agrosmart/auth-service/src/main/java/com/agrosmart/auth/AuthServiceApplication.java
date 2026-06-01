package com.agrosmart.auth; // Paquete raíz del microservicio de autenticación; organiza y agrupa todas sus clases

import org.springframework.boot.SpringApplication; // Clase de Spring Boot que arranca (lanza) la aplicación
import org.springframework.boot.autoconfigure.SpringBootApplication; // Anotación que activa la autoconfiguración de Spring Boot

/**
 * Microservicio de autenticación.
 *
 * Responsabilidades:
 *  - Registro de usuarios (POST /register)
 *  - Login con username/password (POST /login)
 *  - Emisión de JWT firmados
 *
 * Los endpoints reales se agregan en la siguiente entrega.
 */
@SpringBootApplication // Marca esta clase como la app principal: activa autoconfiguración, escaneo de componentes y configuración automática
public class AuthServiceApplication { // Clase principal del microservicio auth-service

    public static void main(String[] args) { // Método main: punto de entrada de Java, lo primero que se ejecuta al iniciar
        SpringApplication.run(AuthServiceApplication.class, args); // Levanta el contexto de Spring, el servidor web embebido (Tomcat) y deja la app escuchando peticiones
    }
}
