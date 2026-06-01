package com.agrosmart.sensor.mqtt; // Paquete con la integracion MQTT (publicacion y suscripcion de mensajes)

import jakarta.annotation.PostConstruct; // Marca un metodo que se ejecuta justo despues de crear el bean
import jakarta.annotation.PreDestroy; // Marca un metodo que se ejecuta antes de destruir el bean (limpieza)
import org.eclipse.paho.client.mqttv3.*; // Cliente MQTT Eclipse Paho (MqttClient, MqttMessage, opciones, etc.)
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence; // Almacenamiento en memoria del cliente (no escribe en disco)
import org.slf4j.Logger; // Interfaz de logging para registrar mensajes
import org.slf4j.LoggerFactory; // Fabrica que crea el Logger de esta clase
import org.springframework.beans.factory.annotation.Value; // Inyecta valores de configuracion (application.properties)
import org.springframework.stereotype.Component; // Marca la clase como componente gestionado por Spring

import java.nio.charset.StandardCharsets; // Constantes de codificacion de caracteres (aqui UTF-8)

/**
 * Publica mensajes en el broker MQTT (Mosquitto).
 *
 * Usa el cliente Eclipse Paho directamente. Se conecta al arrancar y reintenta
 * solo (setAutomaticReconnect). El simulador llama a publicar(...) cada 5 s.
 */
@Component // Registra la clase como bean de Spring para inyectarla donde se necesite
public class MqttPublisher { // Componente encargado de publicar mensajes en el broker MQTT

    private static final Logger log = LoggerFactory.getLogger(MqttPublisher.class); // Logger para registrar eventos del publicador

    private final String brokerUrl; // URL del broker MQTT (Mosquitto)
    private final String clientId; // Identificador base del cliente MQTT
    private final int qos; // Calidad de servicio MQTT (0, 1 o 2) para la entrega de mensajes
    private MqttClient client; // Cliente MQTT que mantiene la conexion con el broker

    public MqttPublisher( // Constructor: Spring inyecta los valores de configuracion
            @Value("${mqtt.broker-url}") String brokerUrl, // Lee mqtt.broker-url de la configuracion
            @Value("${mqtt.client-id:sensor-service}") String clientId, // Lee mqtt.client-id; por defecto 'sensor-service'
            @Value("${mqtt.qos:1}") int qos) { // Lee mqtt.qos; por defecto 1
        this.brokerUrl = brokerUrl; // Guarda la URL del broker
        this.clientId = clientId; // Guarda el id del cliente
        this.qos = qos; // Guarda la calidad de servicio
    }

    @PostConstruct // Se ejecuta tras crear el bean: establece la conexion con el broker
    public void conectar() { // Metodo que conecta el cliente MQTT al arrancar (o cuando hace falta)
        try { // Intenta conectar y captura posibles errores MQTT
            // MemoryPersistence evita escribir en disco (útil dentro del contenedor).
            client = new MqttClient(brokerUrl, clientId + "-pub", new MemoryPersistence()); // Crea el cliente MQTT con sufijo '-pub' y persistencia en memoria
            MqttConnectOptions opts = new MqttConnectOptions(); // Crea las opciones de conexion
            opts.setAutomaticReconnect(true); // Reconecta automaticamente si se cae la conexion
            opts.setCleanSession(true); // Inicia una sesion limpia (sin estado previo guardado)
            opts.setConnectionTimeout(10); // Tiempo maximo de espera de conexion en segundos
            client.connect(opts); // Establece la conexion con el broker usando esas opciones
            log.info("MQTT publisher conectado a {}", brokerUrl); // Registra que el publicador se conecto correctamente
        } catch (MqttException e) { // Si falla la conexion MQTT
            log.error("No se pudo conectar el publisher MQTT ({}): {}", brokerUrl, e.getMessage()); // Registra el error sin detener la aplicacion
        }
    }

    public void publicar(String topic, String payload) { // Publica un mensaje (payload) en el topic indicado
        try { // Intenta publicar y captura posibles errores MQTT
            if (client == null || !client.isConnected()) { // Si no hay cliente o esta desconectado
                conectar(); // Intenta (re)conectar antes de publicar
            }
            MqttMessage msg = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8)); // Crea el mensaje MQTT con el texto codificado en UTF-8
            msg.setQos(qos); // Asigna la calidad de servicio configurada al mensaje
            client.publish(topic, msg); // Publica el mensaje en el topic del broker
            log.debug("Publicado en {} -> {}", topic, payload); // Registra en modo debug lo que se publico
        } catch (MqttException e) { // Si falla la publicacion
            log.error("Error publicando en {}: {}", topic, e.getMessage()); // Registra el error indicando el topic
        }
    }

    @PreDestroy // Se ejecuta antes de destruir el bean: cierra la conexion MQTT
    public void cerrar() { // Metodo de limpieza al apagar la aplicacion
        try { // Intenta desconectar de forma segura
            if (client != null && client.isConnected()) client.disconnect(); // Si el cliente existe y esta conectado, lo desconecta
        } catch (MqttException ignored) { } // Ignora cualquier error al desconectar
    }
}
