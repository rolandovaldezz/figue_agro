package com.agrosmart.alert.mqtt;                                       // Paquete con la integracion MQTT (recepcion de lecturas)

import com.agrosmart.alert.dto.LecturaMensaje;                          // Importa el DTO que representa el mensaje de lectura recibido
import com.agrosmart.alert.service.AlertaService;                       // Importa el motor de reglas que evalua cada lectura
import com.fasterxml.jackson.databind.ObjectMapper;                     // Importa Jackson para convertir JSON (bytes) a objetos Java
import jakarta.annotation.PostConstruct;                                // Importa la anotacion para ejecutar un metodo justo despues de crear el bean
import jakarta.annotation.PreDestroy;                                   // Importa la anotacion para ejecutar un metodo antes de destruir el bean
import org.eclipse.paho.client.mqttv3.*;                                // Importa el cliente MQTT de Eclipse Paho (MqttClient, MqttMessage, etc.)
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;        // Importa la persistencia en memoria para el cliente MQTT
import org.slf4j.Logger;                                                // Importa la interfaz de logging para registrar eventos
import org.slf4j.LoggerFactory;                                         // Importa la fabrica que crea el logger
import org.springframework.beans.factory.annotation.Value;             // Importa @Value para leer propiedades de configuracion (application.properties)
import org.springframework.stereotype.Component;                       // Importa @Component para que Spring administre esta clase como bean

/**
 * Se suscribe a los topics MQTT (agrosmart/+/+) y, por cada lectura recibida,
 * llama al motor de reglas (AlertaService) para evaluar si hay que generar
 * una alerta.
 */
@Component                                                              // Registra la clase como bean de Spring para que se cree e inyecte automaticamente
public class AlertaSubscriber implements MqttCallbackExtended {         // Implementa los callbacks de Paho para reaccionar a eventos MQTT (conexion, mensajes, etc.)

    private static final Logger log = LoggerFactory.getLogger(AlertaSubscriber.class); // Logger para esta clase

    private final AlertaService alertaService;                          // Servicio motor de reglas al que se delega cada lectura
    private final ObjectMapper objectMapper;                            // Conversor JSON <-> objeto Java
    private final String brokerUrl;                                     // URL del broker MQTT al que conectarse
    private final String clientId;                                      // Identificador del cliente MQTT
    private final int qos;                                              // Calidad de servicio MQTT (0, 1 o 2)
    private final String topic;                                         // Patron de topics a los que suscribirse
    private MqttClient client;                                          // Referencia al cliente MQTT activo

    public AlertaSubscriber(                                            // Constructor: Spring inyecta dependencias y valores de configuracion
            AlertaService alertaService,                                // Inyecta el motor de reglas
            ObjectMapper objectMapper,                                  // Inyecta el conversor JSON
            @Value("${mqtt.broker-url}") String brokerUrl,              // Lee la URL del broker desde la configuracion
            @Value("${mqtt.client-id:alert-service}") String clientId,  // Lee el client-id (por defecto 'alert-service' si no esta definido)
            @Value("${mqtt.qos:1}") int qos,                            // Lee el QoS (por defecto 1 si no esta definido)
            @Value("${mqtt.topic-pattern:agrosmart/+/+}") String topic) { // Lee el patron de topics (por defecto 'agrosmart/+/+')
        this.alertaService = alertaService;                             // Guarda el servicio en el campo
        this.objectMapper = objectMapper;                               // Guarda el conversor JSON en el campo
        this.brokerUrl = brokerUrl;                                     // Guarda la URL del broker en el campo
        this.clientId = clientId;                                       // Guarda el client-id en el campo
        this.qos = qos;                                                 // Guarda el QoS en el campo
        this.topic = topic;                                             // Guarda el patron de topics en el campo
    }

    @PostConstruct                                                      // Se ejecuta automaticamente tras crear el bean: aqui se conecta al broker
    public void conectar() {                                            // Metodo que abre la conexion con el broker MQTT
        try {                                                           // Intenta conectar; captura errores de MQTT
            client = new MqttClient(brokerUrl, clientId, new MemoryPersistence()); // Crea el cliente MQTT con persistencia en memoria
            client.setCallback(this);                                   // Registra esta misma clase como receptora de los eventos MQTT
            MqttConnectOptions opts = new MqttConnectOptions();         // Crea las opciones de conexion
            opts.setAutomaticReconnect(true);                           // Habilita la reconexion automatica si se cae la conexion
            opts.setCleanSession(true);                                 // Sesion limpia: no conserva estado entre conexiones
            opts.setConnectionTimeout(10);                              // Tiempo maximo de espera de conexion (10 segundos)
            client.connect(opts);                                       // Conecta al broker con esas opciones
            log.info("MQTT subscriber (alertas) conectando a {}", brokerUrl); // Registra en el log que se esta conectando
        } catch (MqttException e) {                                     // Si falla la conexion MQTT...
            log.error("No se pudo conectar el subscriber de alertas: {}", e.getMessage()); // ...registra el error sin tumbar la app
        }
    }

    @Override                                                           // Sobrescribe el callback de Paho que se dispara al completar la conexion
    public void connectComplete(boolean reconnect, String serverURI) {  // Se invoca cuando la conexion (o reconexion) se completa
        try {                                                           // Intenta suscribirse; captura errores de MQTT
            client.subscribe(topic, qos);                               // Se suscribe al patron de topics con el QoS configurado
            log.info("Suscrito a '{}' (reconexión={})", topic, reconnect); // Registra la suscripcion e indica si fue por reconexion
        } catch (MqttException e) {                                     // Si falla la suscripcion...
            log.error("Error al suscribirse a {}: {}", topic, e.getMessage()); // ...registra el error
        }
    }

    @Override                                                           // Sobrescribe el callback que se dispara al llegar un mensaje
    public void messageArrived(String topic, MqttMessage message) {     // Se invoca cada vez que llega una lectura por MQTT
        try {                                                           // Intenta procesar el mensaje; captura cualquier excepcion
            LecturaMensaje msg = objectMapper.readValue(message.getPayload(), LecturaMensaje.class); // Convierte el JSON recibido a un objeto LecturaMensaje
            alertaService.evaluar(msg);   // motor de reglas -> posible INSERT en alertas // Pasa la lectura al motor de reglas para evaluarla
        } catch (Exception e) {                                         // Si algo falla al procesar el mensaje...
            log.error("Error procesando mensaje de {}: {}", topic, e.getMessage()); // ...registra el error sin detener la suscripcion
        }
    }

    @Override                                                           // Sobrescribe el callback que avisa cuando se pierde la conexion
    public void connectionLost(Throwable cause) {                       // Se invoca si la conexion con el broker se cae
        log.warn("Conexión MQTT perdida (alertas): {}", cause.getMessage()); // Registra la advertencia (Paho reconectara solo)
    }

    @Override                                                           // Sobrescribe el callback de confirmacion de entrega
    public void deliveryComplete(IMqttDeliveryToken token) { /* no aplica */ } // No se usa porque este cliente solo recibe, no publica

    @PreDestroy                                                         // Se ejecuta automaticamente antes de destruir el bean: aqui se cierra la conexion
    public void cerrar() {                                              // Metodo de limpieza al apagar la aplicacion
        try { if (client != null && client.isConnected()) client.disconnect(); } // Si el cliente existe y sigue conectado, lo desconecta ordenadamente
        catch (MqttException ignored) { }                              // Ignora errores al desconectar (la app ya se esta cerrando)
    }
}
