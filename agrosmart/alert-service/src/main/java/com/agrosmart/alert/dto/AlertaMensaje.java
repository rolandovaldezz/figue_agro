package com.agrosmart.alert.dto;                        // Paquete de los DTOs (objetos simples para transportar datos)

/**
 * Mensaje que el alert-service PUBLICA por MQTT en el topic 'agrosmart/alertas'
 * cada vez que genera una alerta. El notification-service lo recibe y, según la
 * severidad, envía el correo. Se transporta como JSON.
 */
public record AlertaMensaje(            // record inmutable: una alerta lista para notificar
        Long sensorId,                  // Sensor que originó la alerta
        String zona,                    // Zona del sensor (zona1, zona2, ...)
        String tipo,                    // Tipo de medición (temperatura, humedad, ph)
        String severidad,               // Gravedad (BAJA | MEDIA | ALTA | CRITICA)
        String mensaje,                 // Texto descriptivo ya armado de la alerta
        Double valor,                   // Valor detectado que rompió el umbral
        String fecha                    // Fecha/hora de generación (texto ISO)
) {}
