package com.agrosmart.alert.mqtt;                                       // Paquete con la integracion MQTT del alert-service

import jakarta.annotation.PostConstruct;                                // Marca un metodo que se ejecuta justo despues de crear el bean
import jakarta.annotation.PreDestroy;                                   // Marca un metodo que se ejecuta antes de destruir el bean (limpieza)
import org.eclipse.paho.client.mqttv3.*;                                // Cliente MQTT Eclipse Paho (MqttClient, MqttMessage, opciones, etc.)
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;        // Persistencia en memoria del cliente (no escribe en disco)
import org.slf4j.Logger;                                                // Interfaz de logging para registrar mensajes
import org.slf4j.LoggerFactory;                                         // Fabrica que crea el Logger de esta clase
import org.springframework.beans.factory.annotation.Value;             // Inyecta valores de configuracion (application.yml)
import org.springframework.stereotype.Component;                       // Marca la clase como componente gestionado por Spring

import java.nio.charset.StandardCharsets;                               // Constantes de codificacion (aqui UTF-8)

/**
 * Publica las alertas generadas en el broker MQTT (Mosquitto), en el topic
 * 'agrosmart/alertas'. El notification-service está suscrito a ese topic y se
 * encarga de mandar el correo. Así el alert-service queda DESACOPLADO del envío
 * de notificaciones (no le importa quién ni cómo se avisa).
 */
@Component                                                              // Registra la clase como bean de Spring para inyectarla donde se necesite
public class AlertaPublisher {                                          // Componente que publica las alertas en el broker MQTT

    private static final Logger log = LoggerFactory.getLogger(AlertaPublisher.class); // Logger para registrar eventos del publicador

    private final String brokerUrl;                                     // URL del broker MQTT (Mosquitto)
    private final String clientId;                                      // Identificador base del cliente MQTT
    private final int qos;                                              // Calidad de servicio MQTT (0, 1 o 2)
    private MqttClient client;                                          // Cliente MQTT que mantiene la conexion con el broker

    public AlertaPublisher(                                             // Constructor: Spring inyecta los valores de configuracion
            @Value("${mqtt.broker-url}") String brokerUrl,              // Lee mqtt.broker-url de la configuracion
            @Value("${mqtt.client-id:alert-service}") String clientId,  // Lee mqtt.client-id; por defecto 'alert-service'
            @Value("${mqtt.qos:1}") int qos) {                          // Lee mqtt.qos; por defecto 1
        this.brokerUrl = brokerUrl;                                     // Guarda la URL del broker
        this.clientId = clientId;                                       // Guarda el id del cliente
        this.qos = qos;                                                 // Guarda la calidad de servicio
    }

    @PostConstruct                                                      // Se ejecuta tras crear el bean: establece la conexion con el broker
    public void conectar() {                                            // Conecta el cliente MQTT al arrancar
        try {                                                           // Intenta conectar y captura posibles errores MQTT
            // Sufijo '-pub' para no chocar con el client-id del subscriber del mismo servicio.
            client = new MqttClient(brokerUrl, clientId + "-pub", new MemoryPersistence()); // Crea el cliente con sufijo '-pub' y persistencia en memoria
            MqttConnectOptions opts = new MqttConnectOptions();         // Crea las opciones de conexion
            opts.setAutomaticReconnect(true);                           // Reconecta automaticamente si se cae la conexion
            opts.setCleanSession(true);                                 // Inicia una sesion limpia (sin estado previo)
            opts.setConnectionTimeout(10);                              // Tiempo maximo de espera de conexion en segundos
            client.connect(opts);                                       // Establece la conexion con el broker
            log.info("MQTT publisher (alertas) conectado a {}", brokerUrl); // Registra que el publicador se conecto
        } catch (MqttException e) {                                     // Si falla la conexion MQTT
            log.error("No se pudo conectar el publisher de alertas ({}): {}", brokerUrl, e.getMessage()); // Registra el error sin detener la app
        }
    }

    public void publicar(String topic, String payload) {               // Publica un mensaje (payload) en el topic indicado
        try {                                                           // Intenta publicar y captura posibles errores MQTT
            if (client == null || !client.isConnected()) {              // Si no hay cliente o esta desconectado
                conectar();                                             // Intenta (re)conectar antes de publicar
            }
            MqttMessage msg = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8)); // Crea el mensaje con el texto en UTF-8
            msg.setQos(qos);                                            // Asigna la calidad de servicio al mensaje
            client.publish(topic, msg);                                 // Publica el mensaje en el topic del broker
            log.debug("Alerta publicada en {} -> {}", topic, payload);  // Registra en modo debug lo publicado
        } catch (MqttException e) {                                     // Si falla la publicacion
            log.error("Error publicando alerta en {}: {}", topic, e.getMessage()); // Registra el error indicando el topic
        }
    }

    @PreDestroy                                                         // Se ejecuta antes de destruir el bean: cierra la conexion MQTT
    public void cerrar() {                                              // Limpieza al apagar la aplicacion
        try {                                                           // Intenta desconectar de forma segura
            if (client != null && client.isConnected()) client.disconnect(); // Si esta conectado, lo desconecta
        } catch (MqttException ignored) { }                            // Ignora errores al desconectar
    }
}
