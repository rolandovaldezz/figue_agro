package com.agrosmart.auth.dto;                         // Paquete de los DTOs (objetos simples para transportar datos)

import java.time.LocalDateTime;                          // Tipo para las fechas de registro y último acceso

/**
 * Datos de un usuario que SÍ son seguros de exponer en la lista (GET /usuarios).
 * IMPORTANTE: no incluye el password_hash; nunca se devuelve la contraseña.
 */
public record UsuarioResumen(           // record inmutable con los datos públicos de un usuario
        Long id,                        // Identificador del usuario
        String username,                // Nombre de usuario
        String email,                   // Correo
        String nombreCompleto,          // Nombre completo (puede ser null)
        String rol,                     // Rol (ADMIN | AGRICULTOR)
        Boolean activo,                 // Si la cuenta está activa
        LocalDateTime fechaRegistro,    // Fecha de alta
        LocalDateTime ultimoAcceso      // Fecha del último login (null si nunca ha entrado)
) {}
