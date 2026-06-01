package com.agrosmart.alert.dto;                                        // Paquete de los DTO (objetos para transportar datos entre capas/servicios)

/**
 * Misma estructura del mensaje MQTT que publica el sensor-service.
 * El alert-service lo recibe para evaluar las reglas.
 */
public record LecturaMensaje(                                           // 'record' de Java: clase inmutable y compacta para llevar datos (con getters automaticos)
        Long sensorId,                                                  // Identificador del sensor que origino la lectura
        String tipo,                                                    // Tipo de medicion: temperatura | humedad | ph
        String zona,                                                    // Zona o area donde esta el sensor
        String unidad,                                                  // Unidad de medida del valor (por ejemplo: C, %, pH)
        Double valor,                                                   // Valor numerico medido por el sensor
        String timestamp                                                // Fecha y hora de la lectura en formato texto
) {}                                                                    // Cuerpo vacio: el record genera constructor, getters, equals, hashCode y toString
