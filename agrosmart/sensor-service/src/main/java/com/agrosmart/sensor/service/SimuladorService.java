package com.agrosmart.sensor.service; // Paquete de la capa de servicios (logica de negocio del microservicio)

import com.agrosmart.sensor.dto.LecturaMensaje; // DTO con la forma del mensaje que se publica por MQTT
import com.agrosmart.sensor.model.Sensor; // Entidad Sensor: cada sensor para el que se genera una lectura
import com.agrosmart.sensor.mqtt.MqttPublisher; // Componente que publica los mensajes en el broker MQTT
import com.agrosmart.sensor.repository.SensorRepository; // Repositorio para obtener los sensores de la BD
import com.fasterxml.jackson.databind.ObjectMapper; // Conversor de objetos Java a JSON (Jackson)
import org.slf4j.Logger; // Interfaz de logging para registrar mensajes
import org.slf4j.LoggerFactory; // Fabrica que crea el Logger de esta clase
import org.springframework.beans.factory.annotation.Value; // Inyecta valores de configuracion (application.properties)
import org.springframework.scheduling.annotation.Scheduled; // Permite ejecutar un metodo de forma periodica
import org.springframework.stereotype.Service; // Marca la clase como servicio gestionado por Spring

import java.time.LocalDateTime; // Fecha y hora actual usada como marca de tiempo de la lectura
import java.util.List; // Tipo de coleccion para la lista de sensores
import java.util.Random; // Generador de numeros aleatorios para simular las mediciones

/**
 * Simulador de sensores virtuales.
 *
 * Cada `simulator.interval-ms` (5 s por defecto) genera una lectura por cada
 * sensor activo y la PUBLICA por MQTT. No guarda en BD directamente: de eso se
 * encarga el LecturaSubscriber al recibir el mensaje (arquitectura desacoplada).
 */
@Service // Registra la clase como bean de servicio de Spring
public class SimuladorService { // Servicio que simula y publica lecturas de los sensores

    private static final Logger log = LoggerFactory.getLogger(SimuladorService.class); // Logger para registrar eventos del simulador
    private final Random random = new Random(); // Generador aleatorio para producir valores simulados

    private final SensorRepository sensorRepository; // Repositorio para leer los sensores de la BD
    private final MqttPublisher publisher; // Publicador MQTT al que se envian las lecturas
    private final ObjectMapper objectMapper; // Conversor que serializa la lectura a JSON
    private final boolean enabled; // Indica si la simulacion esta activada
    private final String topicBase; // Prefijo base de los topics MQTT (ej. 'agrosmart')

    public SimuladorService( // Constructor: Spring inyecta dependencias y valores de configuracion
            SensorRepository sensorRepository, // Repositorio de sensores inyectado
            MqttPublisher publisher, // Publicador MQTT inyectado
            ObjectMapper objectMapper, // Conversor JSON inyectado
            @Value("${simulator.enabled:true}") boolean enabled, // Lee simulator.enabled; por defecto true (activado)
            @Value("${mqtt.topic-base:agrosmart}") String topicBase) { // Lee mqtt.topic-base; por defecto 'agrosmart'
        this.sensorRepository = sensorRepository; // Guarda el repositorio de sensores
        this.publisher = publisher; // Guarda el publicador MQTT
        this.objectMapper = objectMapper; // Guarda el conversor JSON
        this.enabled = enabled; // Guarda si la simulacion esta activada
        this.topicBase = topicBase; // Guarda el prefijo base de los topics
    }

    @Scheduled(fixedRateString = "${simulator.interval-ms:5000}") // Ejecuta este metodo periodicamente (cada 5000 ms por defecto)
    public void simular() { // Tarea programada que genera y publica una lectura por cada sensor activo
        if (!enabled) return; // Si la simulacion esta desactivada, no hace nada

        List<Sensor> sensores; // Lista donde se guardaran los sensores activos
        try { // Intenta leer los sensores de la BD
            sensores = sensorRepository.findByActivoTrue();   // <-- SELECT
        } catch (Exception e) { // Si falla la consulta (por ejemplo, BD no disponible)
            // Aún sin base de datos conectada: avisamos y reintentamos al siguiente ciclo.
            log.warn("No se pudieron leer los sensores (¿BD conectada?): {}", e.getMessage()); // Registra advertencia con el error
            return; // Sale del ciclo actual; se reintentara en la siguiente ejecucion programada
        }

        for (Sensor s : sensores) { // Recorre cada sensor activo para generar su lectura
            double valor = generarValor(s); // Genera un valor simulado para este sensor
            LecturaMensaje msg = new LecturaMensaje( // Construye el mensaje con los datos de la lectura
                    s.getId(), s.getTipo(), s.getZona(), s.getUnidad(), // Id, tipo, zona y unidad del sensor
                    valor, LocalDateTime.now().toString()); // Valor generado y marca de tiempo actual como texto
            try { // Intenta serializar y publicar el mensaje
                String json = objectMapper.writeValueAsString(msg); // Convierte el mensaje a una cadena JSON
                String topic = topicBase + "/" + s.getZona() + "/" + s.getTipo(); // Arma el topic, ej. agrosmart/zona1/temperatura
                publisher.publicar(topic, json); // Publica el JSON en el topic MQTT correspondiente
            } catch (Exception e) { // Si falla la serializacion o la publicacion
                log.error("Error simulando el sensor {}: {}", s.getId(), e.getMessage()); // Registra el error indicando el sensor afectado
            }
        }
    }

    /**
     * Genera un valor realista: el 80% del tiempo dentro del rango esperado,
     * y el 20% fuera (para que el alert-service genere alertas en la demo).
     */
    private double generarValor(Sensor s) { // Calcula un valor simulado para el sensor recibido
        double min = s.getValorMinEsperado() != null ? s.getValorMinEsperado() : 0; // Valor minimo esperado; si es nulo usa 0
        double max = s.getValorMaxEsperado() != null ? s.getValorMaxEsperado() : 100; // Valor maximo esperado; si es nulo usa 100
        double rango = Math.max(max - min, 1); // Amplitud del rango (al menos 1 para evitar division/escala nula)
        double valor; // Variable donde se almacenara el valor generado
        if (random.nextDouble() < 0.8) { // El 80% de las veces genera un valor normal
            valor = min + random.nextDouble() * rango;                 // dentro de rango
        } else if (random.nextBoolean()) { // Del 20% restante, la mitad de las veces genera un valor alto
            valor = max + random.nextDouble() * rango * 0.4;           // por encima
        } else { // La otra mitad genera un valor bajo
            valor = min - random.nextDouble() * rango * 0.4;           // por debajo
        }
        return Math.round(valor * 100.0) / 100.0; // Redondea el valor a dos decimales antes de devolverlo
    }
}
