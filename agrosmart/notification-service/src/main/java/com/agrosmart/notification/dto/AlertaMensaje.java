package com.agrosmart.notification.dto;                 // Paquete de los DTOs del notification-service

import com.fasterxml.jackson.annotation.JsonIgnoreProperties; // Permite ignorar campos JSON que no esten en este record

/**
 * Alerta recibida por MQTT (topic 'agrosmart/alertas') que publica el alert-service.
 * Es el mismo formato que su AlertaMensaje. Se ignoran campos desconocidos para
 * tolerar cambios futuros en el emisor sin romper la deserialización.
 */
@JsonIgnoreProperties(ignoreUnknown = true)             // Si llega un campo extra en el JSON, no falla: simplemente lo ignora
public record AlertaMensaje(            // record inmutable con los datos de la alerta a notificar
        Long sensorId,                  // Sensor que originó la alerta
        String zona,                    // Zona del sensor
        String tipo,                    // Tipo de medición (temperatura, humedad, ph)
        String severidad,               // Gravedad (BAJA | MEDIA | ALTA | CRITICA)
        String mensaje,                 // Texto descriptivo de la alerta
        Double valor,                   // Valor detectado que rompió el umbral
        String fecha                    // Fecha/hora de generación (texto ISO)
) {}
