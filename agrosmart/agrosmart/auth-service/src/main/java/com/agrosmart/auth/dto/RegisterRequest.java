package com.agrosmart.auth.dto; // Paquete de los DTOs (objetos simples para transportar datos entre el cliente y el servidor)

import jakarta.validation.constraints.Email; // Validación: comprueba que el texto tenga formato de correo electrónico válido
import jakarta.validation.constraints.NotBlank; // Validación: el campo no puede ser null ni estar vacío/solo espacios
import jakarta.validation.constraints.Size; // Validación: controla la longitud mínima/máxima del texto

/** Datos para registrar un usuario nuevo (POST /auth/register). */
public record RegisterRequest( // record inmutable con los datos que el cliente envía para crear una cuenta nueva
        @NotBlank String username, // Nombre de usuario deseado; obligatorio
        @Email @NotBlank String email, // Correo del usuario; obligatorio y con formato de email válido
        @NotBlank @Size(min = 6, message = "La contraseña debe tener al menos 6 caracteres") String password, // Contraseña; obligatoria y de al menos 6 caracteres
        String nombreCompleto, // Nombre completo del usuario; opcional (sin validación obligatoria)
        String rol            // opcional; si viene null se asigna AGRICULTOR
) {}
