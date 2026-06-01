package com.agrosmart.sensor.model; // Paquete de las entidades JPA (clases que se mapean a tablas de la BD)

import jakarta.persistence.*; // Anotaciones JPA/Hibernate para mapear la clase a una tabla (@Entity, @Id, @Column, etc.)
import java.time.LocalDateTime; // Tipo de fecha y hora sin zona horaria, usado para el momento de la lectura

/**
 * Entidad de la tabla `lecturas_sensor` (historial de mediciones).
 *
 * Nota: guardamos sensor_id como un Long simple (no como relación @ManyToOne)
 * para que el JSON salga limpio y no haya cargas perezosas innecesarias.
 * La columna fecha_registro la rellena sola la BD (DEFAULT CURRENT_TIMESTAMP),
 * por eso no la mapeamos aquí.
 */
@Entity // Indica que esta clase es una entidad persistente (se guarda en una tabla)
@Table(name = "lecturas_sensor") // Asocia la entidad a la tabla 'lecturas_sensor' de la base de datos
public class Lectura { // Entidad que representa una lectura individual de un sensor

    @Id // Marca este campo como la clave primaria de la tabla
    @GeneratedValue(strategy = GenerationType.IDENTITY) // La BD genera el id automaticamente (autoincremento)
    private Long id; // Identificador unico de la lectura

    @Column(name = "sensor_id", nullable = false) // Mapea a la columna 'sensor_id'; no admite valores nulos
    private Long sensorId; // Id del sensor al que pertenece esta lectura

    @Column(nullable = false) // Columna obligatoria (no admite null)
    private Double valor; // Valor numerico medido en esta lectura

    @Column(name = "timestamp_lectura", nullable = false) // Mapea a la columna 'timestamp_lectura'; obligatoria
    private LocalDateTime timestampLectura; // Fecha y hora en que se tomo la lectura

    public Lectura() {} // Constructor vacio requerido por JPA/Hibernate para instanciar la entidad

    public Lectura(Long sensorId, Double valor, LocalDateTime timestampLectura) { // Constructor para crear una lectura con sus datos
        this.sensorId = sensorId; // Asigna el id del sensor
        this.valor = valor; // Asigna el valor medido
        this.timestampLectura = timestampLectura; // Asigna la marca de tiempo de la lectura
    }

    // ---- Getters y setters ----
    public Long getId() { return id; } // Devuelve el id de la lectura
    public void setId(Long id) { this.id = id; } // Asigna el id de la lectura

    public Long getSensorId() { return sensorId; } // Devuelve el id del sensor asociado
    public void setSensorId(Long sensorId) { this.sensorId = sensorId; } // Asigna el id del sensor asociado

    public Double getValor() { return valor; } // Devuelve el valor medido
    public void setValor(Double valor) { this.valor = valor; } // Asigna el valor medido

    public LocalDateTime getTimestampLectura() { return timestampLectura; } // Devuelve la fecha y hora de la lectura
    public void setTimestampLectura(LocalDateTime t) { this.timestampLectura = t; } // Asigna la fecha y hora de la lectura
}
