package com.agrosmart.notification.mqtt;                                // Paquete con la integracion MQTT del notification-service

import com.agrosmart.notification.dto.AlertaMensaje;                     // DTO con los datos de la alerta recibida
import com.agrosmart.notification.service.EmailService;                  // Servicio que envia el correo
import com.fasterxml.jackson.databind.ObjectMapper;                     // Jackson para convertir el JSON recibido a objeto Java
import jakarta.annotation.PostConstruct;                                // Ejecuta un metodo justo despues de crear el bean
import jakarta.annotation.PreDestroy;                                   // Ejecuta un metodo antes de destruir el bean (limpieza)
import org.eclipse.paho.client.mqttv3.*;                                // Cliente MQTT Eclipse Paho
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;        // Persistencia en memoria del cliente MQTT
import org.slf4j.Logger;                                                // Interfaz de logging
import org.slf4j.LoggerFactory;                                         // Fabrica que crea el logger
import org.springframework.beans.factory.annotation.Value;             // Inyecta propiedades de configuracion
import org.springframework.stereotype.Component;                       // Marca la clase como bean de Spring

import java.util.Arrays;                                                // Utilidad para convertir el arreglo de severidades en stream
import java.util.Set;                                                   // Conjunto de severidades que SI se notifican
import java.util.stream.Collectors;                                     // Recolecta el stream en un Set

/**
 * Se suscribe al topic 'agrosmart/alertas' del broker MQTT. Por cada alerta
 * recibida, revisa si su severidad está en la lista configurada y, si es así,
 * pide al EmailService que envíe el correo.
 */
@Component                                                              // Registra la clase como bean de Spring
public class AlertaSubscriber implements MqttCallbackExtended {         // Implementa los callbacks de Paho para reaccionar a eventos MQTT

    private static final Logger log = LoggerFactory.getLogger(AlertaSubscriber.class); // Logger de la clase

    private final EmailService emailService;                            // Servicio que envia el correo
    private final ObjectMapper objectMapper;                            // Conversor JSON -> objeto Java
    private final String brokerUrl;                                     // URL del broker MQTT
    private final String clientId;                                      // Identificador del cliente MQTT
    private final int qos;                                              // Calidad de servicio MQTT
    private final String topic;                                         // Topic de alertas al que suscribirse
    private final Set<String> severidades;                              // Severidades que SI generan correo (en mayusculas)
    private MqttClient client;                                          // Cliente MQTT activo

    public AlertaSubscriber(                                            // Constructor: Spring inyecta dependencias y configuracion
            EmailService emailService,                                  // Servicio de correo
            ObjectMapper objectMapper,                                  // Conversor JSON
            @Value("${mqtt.broker-url}") String brokerUrl,              // URL del broker
            @Value("${mqtt.client-id:notification-service}") String clientId, // Client-id (por defecto notification-service)
            @Value("${mqtt.qos:1}") int qos,                            // QoS (por defecto 1)
            @Value("${mqtt.topic-alertas:agrosmart/alertas}") String topic,   // Topic de alertas
            @Value("${notify.severidades:ALTA,CRITICA}") String severidadesCsv) { // Lista de severidades a notificar (separadas por coma)
        this.emailService = emailService;                              // Guarda el servicio de correo
        this.objectMapper = objectMapper;                              // Guarda el conversor JSON
        this.brokerUrl = brokerUrl;                                     // Guarda la URL del broker
        this.clientId = clientId;                                       // Guarda el client-id
        this.qos = qos;                                                 // Guarda el QoS
        this.topic = topic;                                             // Guarda el topic de alertas
        // Convierte "ALTA,CRITICA" en un conjunto {ALTA, CRITICA} en mayusculas y sin espacios
        this.severidades = Arrays.stream(severidadesCsv.split(","))     // Separa la lista por comas
                .map(String::trim).map(String::toUpperCase)            // Quita espacios y pasa a mayusculas
                .filter(s -> !s.isEmpty())                             // Descarta entradas vacias
                .collect(Collectors.toSet());                         // Junta todo en un Set
    }

    @PostConstruct                                                      // Se ejecuta tras crear el bean: conecta al broker
    public void conectar() {                                            // Abre la conexion con el broker MQTT
        try {                                                           // Intenta conectar; captura errores
            client = new MqttClient(brokerUrl, clientId, new MemoryPersistence()); // Crea el cliente MQTT
            client.setCallback(this);                                   // Registra esta clase como receptora de eventos
            MqttConnectOptions opts = new MqttConnectOptions();         // Opciones de conexion
            opts.setAutomaticReconnect(true);                           // Reconexion automatica si se cae
            opts.setCleanSession(true);                                 // Sesion limpia
            opts.setConnectionTimeout(10);                              // Tiempo maximo de espera de conexion (10 s)
            client.connect(opts);                                       // Conecta al broker
            log.info("MQTT subscriber (notificaciones) conectando a {}; severidades a notificar: {}", brokerUrl, severidades); // Log informativo
        } catch (MqttException e) {                                     // Si falla la conexion...
            log.error("No se pudo conectar el subscriber de notificaciones: {}", e.getMessage()); // ...lo registra sin tumbar la app
        }
    }

    @Override                                                           // Callback que se dispara al completar la conexion
    public void connectComplete(boolean reconnect, String serverURI) {  // Se invoca cuando la conexion (o reconexion) se completa
        try {                                                           // Intenta suscribirse
            client.subscribe(topic, qos);                               // Se suscribe al topic de alertas
            log.info("Suscrito a '{}' (reconexión={})", topic, reconnect); // Registra la suscripcion
        } catch (MqttException e) {                                     // Si falla la suscripcion...
            log.error("Error al suscribirse a {}: {}", topic, e.getMessage()); // ...lo registra
        }
    }

    @Override                                                           // Callback que se dispara al llegar un mensaje
    public void messageArrived(String topic, MqttMessage message) {     // Se invoca por cada alerta recibida
        try {                                                           // Intenta procesar el mensaje
            AlertaMensaje a = objectMapper.readValue(message.getPayload(), AlertaMensaje.class); // Convierte el JSON en objeto AlertaMensaje
            String sev = a.severidad() == null ? "" : a.severidad().toUpperCase(); // Normaliza la severidad a mayusculas
            if (!severidades.contains(sev)) {                           // Si la severidad NO esta en la lista a notificar...
                log.debug("Alerta {} ignorada (no esta en {})", sev, severidades); // ...la ignora (no manda correo)
                return;                                                 // Termina sin enviar
            }
            emailService.enviarAlerta(a);                               // Severidad valida: envia el correo
        } catch (Exception e) {                                         // Si algo falla (parseo o envio)...
            log.error("Error procesando/enviando alerta de {}: {}", topic, e.getMessage()); // ...lo registra sin detener la suscripcion
        }
    }

    @Override                                                           // Callback que avisa cuando se pierde la conexion
    public void connectionLost(Throwable cause) {                       // Se invoca si la conexion se cae
        log.warn("Conexión MQTT perdida (notificaciones): {}", cause.getMessage()); // Registra la advertencia (Paho reconecta solo)
    }

    @Override                                                           // Callback de confirmacion de entrega (no se usa: solo recibimos)
    public void deliveryComplete(IMqttDeliveryToken token) { /* no aplica */ } // Este cliente solo recibe, no publica

    @PreDestroy                                                         // Se ejecuta antes de destruir el bean: cierra la conexion
    public void cerrar() {                                              // Limpieza al apagar la app
        try { if (client != null && client.isConnected()) client.disconnect(); } // Desconecta si sigue conectado
        catch (MqttException ignored) { }                              // Ignora errores al desconectar
    }
}
