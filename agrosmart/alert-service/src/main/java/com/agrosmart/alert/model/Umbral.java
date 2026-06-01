package com.agrosmart.alert.model;                                      // Paquete de las entidades (clases que mapean tablas de la base de datos)

import jakarta.persistence.*;                                          // Importa las anotaciones JPA (@Entity, @Table, @Id, @Column, etc.)

/**
 * Entidad de la tabla `umbrales_alerta` (las reglas).
 *
 * Cada regla dice: para un tipo de sensor (y opcionalmente una zona), si el
 * valor baja de valor_min o sube de valor_max, se genera una alerta con cierta
 * severidad y un mensaje plantilla.
 */
@Entity                                                                // Indica a JPA/Hibernate que esta clase es una entidad persistente (una fila = un objeto)
@Table(name = "umbrales_alerta")                                       // Asocia la entidad con la tabla `umbrales_alerta` de la base de datos
public class Umbral {                                                  // Clase que representa una regla de umbral de alerta

    @Id                                                                // Marca este campo como la clave primaria de la tabla
    @GeneratedValue(strategy = GenerationType.IDENTITY)                // El id lo autogenera la base de datos (columna autoincremental)
    private Long id;                                                   // Identificador unico de la regla

    @Column(name = "tipo_sensor")                                      // Mapea este campo a la columna `tipo_sensor`
    private String tipoSensor;     // temperatura | humedad | ph        // Tipo de sensor al que aplica la regla

    private String zona;           // null = aplica a todas las zonas   // Zona a la que aplica la regla (si es null, aplica a todas)

    @Column(name = "valor_min")                                        // Mapea este campo a la columna `valor_min`
    private Double valorMin;       // si lectura < valorMin -> alerta    // Limite inferior permitido

    @Column(name = "valor_max")                                        // Mapea este campo a la columna `valor_max`
    private Double valorMax;       // si lectura > valorMax -> alerta    // Limite superior permitido

    private String severidad;      // BAJA | MEDIA | ALTA | CRITICA      // Nivel de gravedad que tendra la alerta generada

    @Column(name = "mensaje_tpl")                                      // Mapea este campo a la columna `mensaje_tpl`
    private String mensajeTpl;     // plantilla con {zona} y {valor}     // Texto plantilla del mensaje de la alerta

    private Boolean activo;                                            // Indica si la regla esta activa (true) o desactivada (false)

    // ---- Getters y setters ----
    public Long getId() { return id; }                                 // Devuelve el id de la regla
    public void setId(Long id) { this.id = id; }                       // Asigna el id de la regla

    public String getTipoSensor() { return tipoSensor; }              // Devuelve el tipo de sensor de la regla
    public void setTipoSensor(String tipoSensor) { this.tipoSensor = tipoSensor; } // Asigna el tipo de sensor de la regla

    public String getZona() { return zona; }                          // Devuelve la zona de la regla
    public void setZona(String zona) { this.zona = zona; }            // Asigna la zona de la regla

    public Double getValorMin() { return valorMin; }                  // Devuelve el limite minimo
    public void setValorMin(Double valorMin) { this.valorMin = valorMin; } // Asigna el limite minimo

    public Double getValorMax() { return valorMax; }                  // Devuelve el limite maximo
    public void setValorMax(Double valorMax) { this.valorMax = valorMax; } // Asigna el limite maximo

    public String getSeveridad() { return severidad; }               // Devuelve la severidad de la regla
    public void setSeveridad(String severidad) { this.severidad = severidad; } // Asigna la severidad de la regla

    public String getMensajeTpl() { return mensajeTpl; }             // Devuelve la plantilla del mensaje
    public void setMensajeTpl(String mensajeTpl) { this.mensajeTpl = mensajeTpl; } // Asigna la plantilla del mensaje

    public Boolean getActivo() { return activo; }                    // Devuelve si la regla esta activa
    public void setActivo(Boolean activo) { this.activo = activo; }  // Asigna si la regla esta activa
}
