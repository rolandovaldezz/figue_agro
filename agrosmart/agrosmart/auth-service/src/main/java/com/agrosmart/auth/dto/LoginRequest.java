package com.agrosmart.auth.dto; // Paquete de los DTOs (objetos simples para transportar datos entre el cliente y el servidor)

import jakarta.validation.constraints.NotBlank; // Validación: el campo no puede ser null ni estar vacío/solo espacios

/** Datos que llegan en el cuerpo del POST /auth/login. */
public record LoginRequest( // record = clase inmutable y compacta de Java que solo guarda datos; genera constructor, getters y equals automáticamente
        @NotBlank String username, // Nombre de usuario; obligatorio (no puede venir vacío)
        @NotBlank String password // Contraseña en texto plano que se validará; obligatoria (no puede venir vacía)
) {}
