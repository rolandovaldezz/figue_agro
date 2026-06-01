package com.agrosmart.sensor; // Paquete raiz del microservicio de sensores; agrupa todas sus clases

import org.springframework.boot.SpringApplication; // Clase de arranque que inicia el contexto de Spring Boot
import org.springframework.boot.autoconfigure.SpringBootApplication; // Anotacion que activa la autoconfiguracion de Spring Boot
import org.springframework.scheduling.annotation.EnableScheduling; // Habilita la ejecucion de tareas programadas (@Scheduled)

/**
 * Microservicio de sensores.
 *
 * Responsabilidades:
 *  - Simular lecturas de sensores virtuales (temperatura, humedad, pH)
 *  - Publicar las lecturas vía MQTT en topics como agrosmart/zona1/temperatura
 *  - Suscribirse a los mismos topics para persistir las lecturas en MariaDB
 *  - Exponer GET /sensores y GET /sensores/{id}/lecturas
 *
 * @EnableScheduling activa @Scheduled para la tarea del simulador.
 * Los componentes reales se agregan en la siguiente entrega.
 */
@SpringBootApplication // Marca esta clase como la aplicacion principal: configura, escanea componentes y autoconfigura
@EnableScheduling // Activa el soporte de tareas periodicas para que funcione el @Scheduled del simulador
public class SensorServiceApplication { // Clase principal del microservicio sensor-service

    public static void main(String[] args) { // Metodo de entrada: lo ejecuta la JVM al iniciar el programa
        SpringApplication.run(SensorServiceApplication.class, args); // Arranca Spring Boot levantando todo el contexto de la aplicacion
    }
}
