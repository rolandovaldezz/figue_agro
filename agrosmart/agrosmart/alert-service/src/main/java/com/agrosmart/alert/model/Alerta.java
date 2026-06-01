package com.agrosmart.alert.model;                                      // Paquete de las entidades (clases que mapean tablas de la base de datos)

import jakarta.persistence.*;                                          // Importa las anotaciones JPA (@Entity, @Table, @Id, @Column, etc.)
import java.time.LocalDateTime;                                        // Importa el tipo para almacenar fecha y hora sin zona horaria

/** Entidad de la tabla `alertas` (alertas generadas). */
@Entity                                                                // Indica a JPA/Hibernate que esta clase es una entidad persistente (una fila = un objeto)
@Table(name = "alertas")                                               // Asocia la entidad con la tabla `alertas` de la base de datos
public class Alerta {                                                  // Clase que representa una alerta generada por el motor de reglas

    @Id                                                                // Marca este campo como la clave primaria de la tabla
    @GeneratedValue(strategy = GenerationType.IDENTITY)                // El id lo autogenera la base de datos (columna autoincremental)
    private Long id;                                                   // Identificador unico de la alerta

    @Column(name = "sensor_id", nullable = false)                      // Mapea a la columna `sensor_id`; no admite valores nulos
    private Long sensorId;                                             // Sensor que origino la alerta

    @Column(name = "lectura_id")                                       // Mapea a la columna `lectura_id`
    private Long lecturaId;        // puede ir null (no compartimos el id de la lectura) // Id de la lectura asociada (puede quedar nulo)

    @Column(name = "umbral_id")                                        // Mapea a la columna `umbral_id`
    private Long umbralId;                                             // Regla (umbral) que se violo y disparo esta alerta

    @Column(name = "tipo_alerta")                                      // Mapea a la columna `tipo_alerta`
    private String tipoAlerta;     // temperatura | humedad | ph        // Tipo de medicion que provoco la alerta

    private String mensaje;                                           // Texto descriptivo de la alerta para mostrar al usuario

    @Column(name = "valor_detectado", nullable = false)               // Mapea a la columna `valor_detectado`; no admite nulos
    private Double valorDetectado;                                    // Valor de la lectura que rompio el umbral

    private String severidad;                                         // Nivel de gravedad heredado de la regla (BAJA | MEDIA | ALTA | CRITICA)

    @Column(name = "fecha_generacion")                                // Mapea a la columna `fecha_generacion`
    private LocalDateTime fechaGeneracion;                            // Momento en que se creo la alerta

    private Boolean resuelta;                                         // Estado de la alerta: true si ya fue resuelta, false si sigue activa

    @Column(name = "fecha_resolucion")                                // Mapea a la columna `fecha_resolucion`
    private LocalDateTime fechaResolucion;                            // Momento en que se resolvio la alerta (nulo mientras siga activa)

    // ---- Getters y setters ----
    public Long getId() { return id; }                                // Devuelve el id de la alerta
    public void setId(Long id) { this.id = id; }                      // Asigna el id de la alerta

    public Long getSensorId() { return sensorId; }                    // Devuelve el id del sensor
    public void setSensorId(Long sensorId) { this.sensorId = sensorId; } // Asigna el id del sensor

    public Long getLecturaId() { return lecturaId; }                  // Devuelve el id de la lectura asociada
    public void setLecturaId(Long lecturaId) { this.lecturaId = lecturaId; } // Asigna el id de la lectura asociada

    public Long getUmbralId() { return umbralId; }                    // Devuelve el id del umbral que disparo la alerta
    public void setUmbralId(Long umbralId) { this.umbralId = umbralId; } // Asigna el id del umbral que disparo la alerta

    public String getTipoAlerta() { return tipoAlerta; }              // Devuelve el tipo de alerta
    public void setTipoAlerta(String tipoAlerta) { this.tipoAlerta = tipoAlerta; } // Asigna el tipo de alerta

    public String getMensaje() { return mensaje; }                    // Devuelve el mensaje de la alerta
    public void setMensaje(String mensaje) { this.mensaje = mensaje; } // Asigna el mensaje de la alerta

    public Double getValorDetectado() { return valorDetectado; }      // Devuelve el valor detectado
    public void setValorDetectado(Double valorDetectado) { this.valorDetectado = valorDetectado; } // Asigna el valor detectado

    public String getSeveridad() { return severidad; }               // Devuelve la severidad de la alerta
    public void setSeveridad(String severidad) { this.severidad = severidad; } // Asigna la severidad de la alerta

    public LocalDateTime getFechaGeneracion() { return fechaGeneracion; } // Devuelve la fecha de generacion
    public void setFechaGeneracion(LocalDateTime f) { this.fechaGeneracion = f; } // Asigna la fecha de generacion

    public Boolean getResuelta() { return resuelta; }                // Devuelve si la alerta esta resuelta
    public void setResuelta(Boolean resuelta) { this.resuelta = resuelta; } // Asigna si la alerta esta resuelta

    public LocalDateTime getFechaResolucion() { return fechaResolucion; } // Devuelve la fecha de resolucion
    public void setFechaResolucion(LocalDateTime f) { this.fechaResolucion = f; } // Asigna la fecha de resolucion
}
