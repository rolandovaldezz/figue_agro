package com.agrosmart.sensor.model; // Paquete de las entidades JPA (clases mapeadas a tablas de la BD)

import jakarta.persistence.*; // Anotaciones JPA/Hibernate para mapear la clase a una tabla (@Entity, @Id, @Column, etc.)

/** Entidad de la tabla `sensores` (catálogo de sensores virtuales). */
@Entity // Indica que esta clase es una entidad persistente (se guarda en una tabla)
@Table(name = "sensores") // Asocia la entidad a la tabla 'sensores' de la base de datos
public class Sensor { // Entidad que representa un sensor del catalogo

    @Id // Marca este campo como la clave primaria de la tabla
    @GeneratedValue(strategy = GenerationType.IDENTITY) // La BD genera el id automaticamente (autoincremento)
    private Long id; // Identificador unico del sensor

    private String nombre; // Nombre descriptivo del sensor
    private String tipo;     // temperatura | humedad | ph
    private String zona;     // zona1 | zona2 | ...
    private String unidad;   // C | % | pH

    @Column(name = "valor_min_esperado") // Mapea a la columna 'valor_min_esperado'
    private Double valorMinEsperado; // Valor minimo considerado normal para este sensor

    @Column(name = "valor_max_esperado") // Mapea a la columna 'valor_max_esperado'
    private Double valorMaxEsperado; // Valor maximo considerado normal para este sensor

    private Boolean activo; // Indica si el sensor esta activo (true) o desactivado (false)

    // ---- Getters y setters ----
    public Long getId() { return id; } // Devuelve el id del sensor
    public void setId(Long id) { this.id = id; } // Asigna el id del sensor

    public String getNombre() { return nombre; } // Devuelve el nombre del sensor
    public void setNombre(String nombre) { this.nombre = nombre; } // Asigna el nombre del sensor

    public String getTipo() { return tipo; } // Devuelve el tipo de medicion del sensor
    public void setTipo(String tipo) { this.tipo = tipo; } // Asigna el tipo de medicion del sensor

    public String getZona() { return zona; } // Devuelve la zona del sensor
    public void setZona(String zona) { this.zona = zona; } // Asigna la zona del sensor

    public String getUnidad() { return unidad; } // Devuelve la unidad de medida del sensor
    public void setUnidad(String unidad) { this.unidad = unidad; } // Asigna la unidad de medida del sensor

    public Double getValorMinEsperado() { return valorMinEsperado; } // Devuelve el valor minimo esperado
    public void setValorMinEsperado(Double v) { this.valorMinEsperado = v; } // Asigna el valor minimo esperado

    public Double getValorMaxEsperado() { return valorMaxEsperado; } // Devuelve el valor maximo esperado
    public void setValorMaxEsperado(Double v) { this.valorMaxEsperado = v; } // Asigna el valor maximo esperado

    public Boolean getActivo() { return activo; } // Devuelve si el sensor esta activo
    public void setActivo(Boolean activo) { this.activo = activo; } // Asigna el estado activo del sensor
}
