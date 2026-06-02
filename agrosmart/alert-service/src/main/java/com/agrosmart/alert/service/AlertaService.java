package com.agrosmart.alert.service;                                    // Paquete de la capa de servicio (logica de negocio)

import com.agrosmart.alert.dto.AlertaMensaje;                           // Importa el DTO de la alerta que se publica por MQTT para notificar
import com.agrosmart.alert.dto.LecturaMensaje;                          // Importa el DTO de la lectura recibida por MQTT
import com.agrosmart.alert.model.Alerta;                                // Importa la entidad Alerta (tabla `alertas`)
import com.agrosmart.alert.model.Umbral;                                // Importa la entidad Umbral (tabla `umbrales_alerta`)
import com.agrosmart.alert.mqtt.AlertaPublisher;                        // Importa el publicador MQTT de alertas (avisa al notification-service)
import com.agrosmart.alert.repository.AlertaRepository;                 // Importa el repositorio para guardar/consultar alertas
import com.agrosmart.alert.repository.UmbralRepository;                 // Importa el repositorio para consultar las reglas (umbrales)
import com.fasterxml.jackson.databind.ObjectMapper;                     // Importa Jackson para convertir la alerta a JSON
import org.slf4j.Logger;                                                // Importa la interfaz de logging para registrar eventos
import org.slf4j.LoggerFactory;                                         // Importa la fabrica que crea el logger
import org.springframework.beans.factory.annotation.Value;             // Importa @Value para leer propiedades de configuracion
import org.springframework.http.HttpStatus;                            // Importa los codigos de estado HTTP (por ejemplo 404 NOT_FOUND)
import org.springframework.stereotype.Service;                         // Importa @Service para marcar la clase como servicio de negocio
import org.springframework.transaction.annotation.Transactional;       // Importa @Transactional para manejar transacciones de base de datos
import org.springframework.web.server.ResponseStatusException;         // Importa la excepcion que devuelve un estado HTTP al cliente

import java.time.LocalDateTime;                                        // Importa el tipo para fecha y hora actuales
import java.util.List;                                                  // Importa List para manejar colecciones de reglas

/**
 * Motor de reglas. Aquí está el corazón del alert-service:
 * compara cada lectura recibida por MQTT contra las reglas (umbrales_alerta)
 * y, si se viola alguna, GENERA una alerta (INSERT en la tabla alertas).
 */
@Service                                                                // Marca la clase como bean de servicio (logica de negocio) para que Spring la gestione
public class AlertaService {                                            // Clase que contiene el motor de reglas de las alertas

    private static final Logger log = LoggerFactory.getLogger(AlertaService.class); // Logger para esta clase

    private final UmbralRepository umbralRepository;                    // Repositorio para leer las reglas (umbrales)
    private final AlertaRepository alertaRepository;                    // Repositorio para leer y guardar alertas
    private final AlertaPublisher alertaPublisher;                      // Publicador MQTT para avisar de cada alerta nueva
    private final ObjectMapper objectMapper;                           // Conversor de objetos Java a JSON (para el mensaje MQTT)
    private final String topicAlertas;                                 // Topic MQTT donde se publican las alertas

    public AlertaService(UmbralRepository umbralRepository,             // Constructor: Spring inyecta los repositorios y dependencias
                         AlertaRepository alertaRepository,
                         AlertaPublisher alertaPublisher,
                         ObjectMapper objectMapper,
                         @Value("${mqtt.topic-alertas:agrosmart/alertas}") String topicAlertas) { // Topic de alertas (por defecto agrosmart/alertas)
        this.umbralRepository = umbralRepository;                       // Guarda el repositorio de umbrales en el campo
        this.alertaRepository = alertaRepository;                       // Guarda el repositorio de alertas en el campo
        this.alertaPublisher = alertaPublisher;                         // Guarda el publicador MQTT en el campo
        this.objectMapper = objectMapper;                              // Guarda el conversor JSON en el campo
        this.topicAlertas = topicAlertas;                              // Guarda el topic de alertas en el campo
    }

    /** Evalúa una lectura contra todas las reglas de su tipo de sensor. */
    @Transactional                                                      // Ejecuta el metodo dentro de una transaccion (se confirma o se revierte en bloque)
    public void evaluar(LecturaMensaje msg) {                           // Recibe una lectura y decide si genera alertas
        List<Umbral> reglas = umbralRepository.findByTipoSensorAndActivoTrue(msg.tipo()); // SELECT // Busca las reglas activas para el tipo de sensor de la lectura

        for (Umbral u : reglas) {                                       // Recorre cada regla aplicable
            // Si la regla es de una zona concreta, debe coincidir.
            if (u.getZona() != null && !u.getZona().equalsIgnoreCase(msg.zona())) continue; // Si la regla es de una zona y no coincide con la de la lectura, la salta

            boolean bajo = u.getValorMin() != null && msg.valor() < u.getValorMin(); // Es true si la lectura esta por debajo del minimo permitido
            boolean alto = u.getValorMax() != null && msg.valor() > u.getValorMax(); // Es true si la lectura esta por encima del maximo permitido
            if (!bajo && !alto) continue;   // dentro del umbral: no pasa nada // Si el valor esta dentro del rango, no genera alerta y sigue con la siguiente regla

            // Evita crear la misma alerta cada 5 s mientras siga activa.
            if (alertaRepository.existsBySensorIdAndUmbralIdAndResueltaFalse(msg.sensorId(), u.getId())) { // Comprueba si ya existe una alerta activa igual
                continue;                                               // Si ya existe, no la duplica y pasa a la siguiente regla
            }

            Alerta a = new Alerta();                                    // Crea una nueva alerta a registrar
            a.setSensorId(msg.sensorId());                              // Guarda el sensor que origino la alerta
            a.setUmbralId(u.getId());                                   // Guarda la regla (umbral) que se violo
            a.setTipoAlerta(msg.tipo());                                // Guarda el tipo de medicion (temperatura, humedad, ph)
            a.setValorDetectado(msg.valor());                           // Guarda el valor que rompio el umbral
            a.setSeveridad(u.getSeveridad());                           // Hereda la severidad definida en la regla
            a.setMensaje(render(u.getMensajeTpl(), msg));               // Construye el mensaje a partir de la plantilla y los datos de la lectura
            a.setFechaGeneracion(LocalDateTime.now());                  // Marca la fecha y hora actual de generacion
            a.setResuelta(false);                                       // La alerta nace activa (no resuelta)

            alertaRepository.save(a);   // <-- INSERT INTO alertas       // Persiste la alerta en la base de datos
            log.info("Alerta generada [{}] sensor={} valor={}", u.getSeveridad(), msg.sensorId(), msg.valor()); // Registra en el log la alerta generada
            publicarAlerta(a, msg);     // Avisa por MQTT (el notification-service decidira si manda correo)
        }
    }

    /** Publica la alerta recién creada en MQTT para que el notification-service la procese. */
    private void publicarAlerta(Alerta a, LecturaMensaje msg) {         // Convierte la alerta en mensaje y la publica en el broker
        try {                                                           // Intenta serializar y publicar; nunca debe tumbar el flujo principal
            AlertaMensaje notif = new AlertaMensaje(                     // Arma el mensaje con los datos relevantes de la alerta
                    a.getSensorId(),                                    // Sensor que la origino
                    msg.zona(),                                         // Zona (la trae la lectura recibida)
                    a.getTipoAlerta(),                                  // Tipo de medicion
                    a.getSeveridad(),                                   // Severidad (la usara el notification-service para filtrar)
                    a.getMensaje(),                                     // Texto ya armado de la alerta
                    a.getValorDetectado(),                             // Valor que rompio el umbral
                    a.getFechaGeneracion() != null ? a.getFechaGeneracion().toString() : null); // Fecha en texto ISO
            alertaPublisher.publicar(topicAlertas, objectMapper.writeValueAsString(notif)); // Serializa a JSON y publica en el topic de alertas
        } catch (Exception e) {                                         // Si falla la serializacion/publicacion...
            log.error("No se pudo publicar la alerta para notificación: {}", e.getMessage()); // ...lo registra sin afectar el guardado de la alerta
        }
    }

    /** Marca una alerta como resuelta (UPDATE). */
    @Transactional                                                      // Ejecuta el metodo dentro de una transaccion
    public Alerta resolver(Long id) {                                   // Marca como resuelta la alerta con el id indicado
        Alerta a = alertaRepository.findById(id)                        // Busca la alerta por su id
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alerta no encontrada")); // Si no existe, lanza un error HTTP 404
        a.setResuelta(true);                                            // Marca la alerta como resuelta
        a.setFechaResolucion(LocalDateTime.now());                      // Registra la fecha y hora de resolucion
        return alertaRepository.save(a);   // <-- UPDATE                 // Guarda los cambios (actualiza la fila) y devuelve la alerta
    }

    /** Sustituye {zona} y {valor} en la plantilla del mensaje. */
    private String render(String plantilla, LecturaMensaje msg) {       // Metodo auxiliar que arma el texto del mensaje de la alerta
        if (plantilla == null) {                                        // Si no hay plantilla definida en la regla...
            return "Alerta de " + msg.tipo() + " en " + msg.zona() + ": " + msg.valor(); // ...arma un mensaje por defecto con los datos de la lectura
        }
        return plantilla                                                // Si hay plantilla, parte de ella y reemplaza los marcadores
                .replace("{zona}", String.valueOf(msg.zona()))          // Sustituye {zona} por la zona de la lectura
                .replace("{valor}", String.valueOf(msg.valor()));       // Sustituye {valor} por el valor de la lectura
    }
}
