package com.agrosmart.sensor.dto; // Paquete de los DTO (objetos para transportar datos entre componentes)

/**
 * Mensaje que viaja por MQTT (en formato JSON) cada vez que el simulador
 * genera una lectura. Tanto el publicador como el suscriptor usan esta forma.
 *
 * Ejemplo del JSON publicado en el topic agrosmart/zona1/temperatura:
 *   {"sensorId":1,"tipo":"temperatura","zona":"zona1","unidad":"C",
 *    "valor":24.5,"timestamp":"2026-05-30T18:00:00.123"}
 */
public record LecturaMensaje( // 'record' de Java: clase inmutable con los datos del mensaje MQTT (genera getters, equals, etc.)
        Long sensorId, // Identificador del sensor que origina la lectura
        String tipo, // Tipo de medicion: temperatura, humedad o ph
        String zona, // Zona del cultivo a la que pertenece el sensor (ej. zona1)
        String unidad, // Unidad de medida del valor (C, %, pH)
        Double valor, // Valor numerico medido por el sensor
        String timestamp // Marca de tiempo de la lectura en formato texto (ISO-8601)
) {} // Cuerpo vacio: el record genera automaticamente el constructor y los accesores
