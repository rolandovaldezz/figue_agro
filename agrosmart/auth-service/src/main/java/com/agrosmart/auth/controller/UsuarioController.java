package com.agrosmart.auth.controller;                  // Paquete de los controladores REST

import com.agrosmart.auth.dto.UsuarioResumen;            // DTO con los datos públicos de un usuario (sin contraseña)
import com.agrosmart.auth.repository.UsuarioRepository;  // Repositorio para leer los usuarios de la base de datos
import org.springframework.web.bind.annotation.GetMapping;     // Anotación para mapear peticiones HTTP GET
import org.springframework.web.bind.annotation.RequestMapping; // Anotación para el prefijo de ruta común
import org.springframework.web.bind.annotation.RestController; // Marca la clase como controlador REST (devuelve JSON)

import java.util.List;                                   // Tipo de coleccion para devolver varios usuarios

/**
 * Listado de usuarios. GET /usuarios
 *
 * OJO con las rutas: el API Gateway enruta /api/usuarios/** hasta aquí y SÍ
 * valida el JWT antes (a diferencia de /api/auth/** que es público). Por eso
 * solo un usuario autenticado puede ver la lista.
 *
 * Nunca se devuelve la contraseña: se usa el DTO UsuarioResumen.
 */
@RestController                                          // Controlador REST: cada método devuelve datos (JSON) directamente
@RequestMapping("/usuarios")                             // Todas las rutas de esta clase empiezan por /usuarios
public class UsuarioController {                         // Controlador que expone el listado de usuarios

    private final UsuarioRepository usuarioRepository;   // Acceso a la tabla `usuarios`

    public UsuarioController(UsuarioRepository usuarioRepository) { // Constructor: Spring inyecta el repositorio
        this.usuarioRepository = usuarioRepository;      // Guarda el repositorio para usarlo
    }

    /** Devuelve TODOS los usuarios registrados (datos públicos, sin contraseña). */
    @GetMapping                                          // Atiende GET /usuarios
    public List<UsuarioResumen> listar() {               // Devuelve la lista de usuarios resumidos
        return usuarioRepository.findAll().stream()      // <-- SELECT * FROM usuarios; recorre cada uno
                .map(u -> new UsuarioResumen(            // Convierte cada entidad Usuario en un DTO seguro
                        u.getId(),                       // id
                        u.getUsername(),                 // username
                        u.getEmail(),                    // email
                        u.getNombreCompleto(),           // nombre completo
                        u.getRol(),                      // rol
                        u.getActivo(),                   // activo/inactivo
                        u.getFechaRegistro(),            // fecha de alta
                        u.getUltimoAcceso()))            // último acceso
                .toList();                               // Junta todo en una lista para devolverla como JSON
    }
}
