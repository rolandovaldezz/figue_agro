package com.agrosmart.auth.model; // Paquete de los modelos/entidades (las clases que representan las tablas de la base de datos)

import jakarta.persistence.*; // Importa todas las anotaciones de JPA (@Entity, @Table, @Id, @Column, etc.) para mapear la clase a una tabla
import java.time.LocalDateTime; // Tipo de Java para guardar fecha y hora (sin zona horaria)

/**
 * Entidad que representa una fila de la tabla `usuarios`.
 *
 * Hibernate usa esta clase para convertir filas de la BD en objetos Java
 * (y viceversa). Cada campo = una columna. Los nombres en camelCase se
 * traducen solos a snake_case (passwordHash -> password_hash), pero aquí lo
 * dejamos explícito con @Column para que se entienda mejor.
 */
@Entity // Marca la clase como entidad JPA: Hibernate la asocia a una tabla y gestiona sus filas como objetos
@Table(name = "usuarios") // Indica que esta entidad se mapea a la tabla llamada `usuarios`
public class Usuario { // Clase que modela a un usuario del sistema

    @Id // Señala que este campo es la clave primaria (identificador único de la fila)
    @GeneratedValue(strategy = GenerationType.IDENTITY) // La BD genera el id automáticamente (columna autoincremental)
    private Long id; // Identificador único del usuario, generado por la base de datos

    @Column(nullable = false, unique = true) // Columna obligatoria (NOT NULL) y sin valores repetidos (UNIQUE)
    private String username; // Nombre de usuario para iniciar sesión; único en toda la tabla

    @Column(nullable = false, unique = true) // Columna obligatoria y única
    private String email; // Correo electrónico del usuario; único en toda la tabla

    @Column(name = "password_hash", nullable = false) // Se mapea a la columna password_hash; obligatoria
    private String passwordHash;   // contraseña cifrada con bcrypt

    @Column(name = "nombre_completo") // Se mapea a la columna nombre_completo (puede ser null)
    private String nombreCompleto; // Nombre completo del usuario para mostrarlo en la interfaz

    @Column(nullable = false) // Columna obligatoria
    private String rol;            // ADMIN | AGRICULTOR

    @Column(nullable = false) // Columna obligatoria
    private Boolean activo; // Indica si la cuenta está activa (true) o deshabilitada (false)

    @Column(name = "fecha_registro") // Se mapea a la columna fecha_registro
    private LocalDateTime fechaRegistro; // Fecha y hora en que se creó la cuenta

    @Column(name = "ultimo_acceso") // Se mapea a la columna ultimo_acceso
    private LocalDateTime ultimoAcceso; // Fecha y hora del último login del usuario

    // ---- Getters y setters ----
    public Long getId() { return id; } // Devuelve el id del usuario
    public void setId(Long id) { this.id = id; } // Asigna el id del usuario

    public String getUsername() { return username; } // Devuelve el username
    public void setUsername(String username) { this.username = username; } // Asigna el username

    public String getEmail() { return email; } // Devuelve el email
    public void setEmail(String email) { this.email = email; } // Asigna el email

    public String getPasswordHash() { return passwordHash; } // Devuelve el hash bcrypt de la contraseña
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; } // Asigna el hash de la contraseña

    public String getNombreCompleto() { return nombreCompleto; } // Devuelve el nombre completo
    public void setNombreCompleto(String nombreCompleto) { this.nombreCompleto = nombreCompleto; } // Asigna el nombre completo

    public String getRol() { return rol; } // Devuelve el rol (ADMIN o AGRICULTOR)
    public void setRol(String rol) { this.rol = rol; } // Asigna el rol

    public Boolean getActivo() { return activo; } // Devuelve si la cuenta está activa
    public void setActivo(Boolean activo) { this.activo = activo; } // Asigna el estado activo/inactivo

    public LocalDateTime getFechaRegistro() { return fechaRegistro; } // Devuelve la fecha de registro
    public void setFechaRegistro(LocalDateTime fechaRegistro) { this.fechaRegistro = fechaRegistro; } // Asigna la fecha de registro

    public LocalDateTime getUltimoAcceso() { return ultimoAcceso; } // Devuelve la fecha del último acceso
    public void setUltimoAcceso(LocalDateTime ultimoAcceso) { this.ultimoAcceso = ultimoAcceso; } // Asigna la fecha del último acceso
}
