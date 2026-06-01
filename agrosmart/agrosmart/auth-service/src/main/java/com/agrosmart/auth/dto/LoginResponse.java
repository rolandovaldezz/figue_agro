package com.agrosmart.auth.dto; // Paquete de los DTOs (objetos simples para transportar datos entre el cliente y el servidor)

/** Respuesta del login: el token JWT y datos básicos del usuario. */
public record LoginResponse( // record inmutable que se convertirá a JSON y se devolverá al cliente tras un login correcto
        String token, // Token JWT firmado que el cliente usará para autenticarse en peticiones siguientes
        String username, // Nombre de usuario que inició sesión
        String rol, // Rol del usuario (ADMIN o AGRICULTOR), útil para que el frontend muestre opciones según el permiso
        String nombreCompleto // Nombre completo del usuario, para mostrarlo en la interfaz
) {}
