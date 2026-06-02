package com.agrosmart.notification.dto;                 // Paquete de los DTOs del notification-service

import com.fasterxml.jackson.annotation.JsonIgnoreProperties; // Ignora campos JSON extra sin fallar

/**
 * Petición que llega desde el frontend al pulsar "Enviar por correo" en una alerta.
 * Lleva el correo destino elegido por el usuario y los datos de la alerta a enviar.
 */
@JsonIgnoreProperties(ignoreUnknown = true)             // Tolera campos extra en el JSON
public record EnvioManual(               // record con la petición de envío manual
        String destinatario,            // Correo al que se quiere mandar la alerta
        AlertaMensaje alerta            // Datos de la alerta (severidad, tipo, zona, mensaje, valor, etc.)
) {}
