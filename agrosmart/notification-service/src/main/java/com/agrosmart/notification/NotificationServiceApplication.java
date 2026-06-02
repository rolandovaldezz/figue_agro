package com.agrosmart.notification;                                     // Paquete raiz del microservicio de notificaciones

import org.springframework.boot.SpringApplication;                      // Clase que arranca una aplicacion Spring Boot
import org.springframework.boot.autoconfigure.SpringBootApplication;    // Anotacion que activa la autoconfiguracion de Spring Boot

/**
 * Punto de entrada del notification-service.
 *
 * Este microservicio NO usa base de datos: solo escucha las alertas que llegan
 * por MQTT (topic agrosmart/alertas) y, según su severidad, envía un correo.
 */
@SpringBootApplication                                                  // Marca esta clase como la aplicacion principal de Spring Boot
public class NotificationServiceApplication {                           // Clase principal del microservicio

    public static void main(String[] args) {                            // Metodo main: arranca la aplicacion
        SpringApplication.run(NotificationServiceApplication.class, args); // Inicia el contexto de Spring Boot
    }
}
