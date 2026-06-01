package com.agrosmart.sensor.mqtt; // Paquete con la integracion MQTT (publicacion y suscripcion de mensajes)

import com.agrosmart.sensor.dto.LecturaMensaje; // DTO con la forma del mensaje JSON que llega por MQTT
import com.agrosmart.sensor.model.Lectura; // Entidad Lectura que se guardara en la base de datos
import com.agrosmart.sensor.repository.LecturaRepository; // Repositorio para persistir las lecturas recibidas
import com.fasterxml.jackson.databind.ObjectMapper; // Conversor JSON <-> objetos Java (Jackson)
import jakarta.annotation.PostConstruct; // Marca un metodo que se ejecuta justo despues de crear el bean
import jakarta.annotation.PreDestroy; // Marca un metodo que se ejecuta antes de destruir el bean (limpieza)
import org.eclipse.paho.client.mqttv3.*; // Cliente MQTT Eclipse Paho (MqttClient, MqttMessage, callbacks, etc.)
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence; // Almacenamiento en memoria del cliente (no escribe en disco)
import org.slf4j.Logger; // Interfaz de logging para registrar mensajes
import org.slf4j.LoggerFactory; // Fabrica que crea el Logger de esta clase
import org.springframework.beans.factory.annotation.Value; // Inyecta valores de configuracion (application.properties)
import org.springframework.stereotype.Component; // Marca la clase como componente gestionado por Spring

import java.time.LocalDateTime; // Fecha y hora usada como marca de tiempo de la lectura

/**
 * Se suscribe a TODOS los topics (agrosmart/+/+) y PERSISTE cada lectura
 * recibida en la tabla lecturas_sensor.
 *
 * Es decir: el simulador publica -> Mosquitto -> este suscriptor guarda en BD.
 * Así la persistencia queda desacoplada vía MQTT (la misma arquitectura que
 * usa el alert-service para evaluar reglas).
 */
@Component // Registra la clase como bean de Spring para que se cree e inyecte automaticamente
public class LecturaSubscriber implements MqttCallbackExtended { // Suscriptor MQTT; implementa los callbacks de Paho (incluida reconexion)

    private static final Logger log = LoggerFactory.getLogger(LecturaSubscriber.class); // Logger para registrar eventos de este suscriptor

    private final LecturaRepository lecturaRepository; // Repositorio para guardar las lecturas en la BD
    private final ObjectMapper objectMapper; // Conversor JSON usado para leer el mensaje recibido
    private final String brokerUrl; // URL del broker MQTT (Mosquitto)
    private final String clientId; // Identificador base del cliente MQTT
    private final int qos; // Calidad de servicio MQTT (0, 1 o 2) para la entrega de mensajes
    private final String topic; // Patron de topics a los que se suscribe
    private MqttClient client; // Cliente MQTT que mantiene la conexion con el broker

    public LecturaSubscriber( // Constructor: Spring inyecta dependencias y valores de configuracion
            LecturaRepository lecturaRepository, // Repositorio de lecturas inyectado
            ObjectMapper objectMapper, // Conversor JSON inyectado
            @Value("${mqtt.broker-url}") String brokerUrl, // Lee mqtt.broker-url de la configuracion
            @Value("${mqtt.client-id:sensor-service}") String clientId, // Lee mqtt.client-id; por defecto 'sensor-service'
            @Value("${mqtt.qos:1}") int qos, // Lee mqtt.qos; por defecto 1
            @Value("${mqtt.topic-base:agrosmart}") String topicBase) { // Lee mqtt.topic-base; por defecto 'agrosmart'
        this.lecturaRepository = lecturaRepository; // Guarda el repositorio de lecturas
        this.objectMapper = objectMapper; // Guarda el conversor JSON
        this.brokerUrl = brokerUrl; // Guarda la URL del broker
        this.clientId = clientId; // Guarda el id del cliente
        this.qos = qos; // Guarda la calidad de servicio
        this.topic = topicBase + "/+/+";   // cualquier zona, cualquier tipo
    }

    @PostConstruct // Se ejecuta tras crear el bean: establece la conexion con el broker
    public void conectar() { // Metodo que conecta el cliente MQTT al arrancar
        try { // Intenta conectar y captura posibles errores MQTT
            client = new MqttClient(brokerUrl, clientId + "-sub", new MemoryPersistence()); // Crea el cliente MQTT con sufijo '-sub' y persistencia en memoria
            client.setCallback(this); // Registra esta clase como manejadora de los eventos MQTT
            MqttConnectOptions opts = new MqttConnectOptions(); // Crea las opciones de conexion
            opts.setAutomaticReconnect(true); // Reconecta automaticamente si se cae la conexion
            opts.setCleanSession(true); // Inicia una sesion limpia (sin estado previo guardado)
            opts.setConnectionTimeout(10); // Tiempo maximo de espera de conexion en segundos
            client.connect(opts);  // la suscripción se hace en connectComplete()
            log.info("MQTT subscriber (lecturas) conectando a {}", brokerUrl); // Registra que se esta conectando al broker
        } catch (MqttException e) { // Si falla la conexion MQTT
            log.error("No se pudo conectar el subscriber de lecturas: {}", e.getMessage()); // Registra el error sin detener la aplicacion
        }
    }

    /** Se llama al conectar y en cada reconexión: (re)suscribimos aquí. */
    @Override // Sobrescribe el callback de Paho que avisa cuando la conexion se completa
    public void connectComplete(boolean reconnect, String serverURI) { // Se invoca al conectar (y al reconectar)
        try { // Intenta suscribirse al topic
            client.subscribe(topic, qos); // Se suscribe al patron de topics con la QoS configurada
            log.info("Suscrito a '{}' (reconexión={})", topic, reconnect); // Registra la suscripcion e indica si fue una reconexion
        } catch (MqttException e) { // Si falla la suscripcion
            log.error("Error al suscribirse a {}: {}", topic, e.getMessage()); // Registra el error de suscripcion
        }
    }

    /** Llega un mensaje -> lo convertimos y lo guardamos (INSERT). */
    @Override // Sobrescribe el callback que se ejecuta cuando llega un mensaje
    public void messageArrived(String topic, MqttMessage message) { // Recibe el topic y el mensaje MQTT entrante
        try { // Intenta procesar y guardar la lectura
            LecturaMensaje msg = objectMapper.readValue(message.getPayload(), LecturaMensaje.class); // Convierte el JSON recibido en un objeto LecturaMensaje
            LocalDateTime ts; // Variable para la marca de tiempo de la lectura
            try { ts = LocalDateTime.parse(msg.timestamp()); } // Intenta interpretar la fecha que viene en el mensaje
            catch (Exception e) { ts = LocalDateTime.now(); } // Si la fecha es invalida, usa la hora actual como respaldo

            Lectura lectura = new Lectura(msg.sensorId(), msg.valor(), ts); // Crea la entidad Lectura con los datos del mensaje
            lecturaRepository.save(lectura);   // <-- INSERT INTO lecturas_sensor
            log.debug("Lectura guardada: sensor={} valor={}", msg.sensorId(), msg.valor()); // Registra en modo debug la lectura guardada
        } catch (Exception e) { // Si ocurre cualquier error al procesar el mensaje
            log.error("Error procesando mensaje de {}: {}", topic, e.getMessage()); // Registra el error indicando el topic
        }
    }

    @Override // Sobrescribe el callback que avisa cuando se pierde la conexion
    public void connectionLost(Throwable cause) { // Se invoca al perder la conexion con el broker
        log.warn("Conexión MQTT perdida (lecturas): {}", cause.getMessage()); // Registra una advertencia con la causa de la perdida
    }

    @Override // Sobrescribe el callback de confirmacion de entrega (solo aplica al publicar)
    public void deliveryComplete(IMqttDeliveryToken token) { /* no aplica al suscribir */ } // Vacio: este componente solo suscribe, no publica

    @PreDestroy // Se ejecuta antes de destruir el bean: cierra la conexion MQTT
    public void cerrar() { // Metodo de limpieza al apagar la aplicacion
        try { if (client != null && client.isConnected()) client.disconnect(); } // Si el cliente existe y esta conectado, lo desconecta
        catch (MqttException ignored) { } // Ignora cualquier error al desconectar
    }
}
