package com.agrosmart.auth.repository; // Paquete de los repositorios (la capa que habla con la base de datos)

import com.agrosmart.auth.model.Usuario; // Entidad Usuario: la clase que representa una fila de la tabla `usuarios`
import org.springframework.data.jpa.repository.JpaRepository; // Interfaz base de Spring Data JPA que aporta los métodos CRUD ya hechos
import java.util.Optional; // Contenedor que puede tener un valor o estar vacío; evita devolver null y los NullPointerException

/**
 * Repositorio JPA de usuarios.
 *
 * Al extender JpaRepository ya tenemos GRATIS los métodos save() (INSERT/UPDATE),
 * findAll() (SELECT *), findById(), delete(), etc. — sin escribir SQL.
 *
 * Además, Spring Data genera la consulta automáticamente a partir del NOMBRE
 * del método: findByUsername(...) se traduce a:
 *     SELECT * FROM usuarios WHERE username = ?
 */
public interface UsuarioRepository extends JpaRepository<Usuario, Long> { // Interfaz de acceso a datos; <Usuario, Long> = entidad gestionada y tipo de su clave primaria (id)

    Optional<Usuario> findByUsername(String username); // Busca un usuario por su username; devuelve Optional (vacío si no existe). Spring crea el SELECT solo

    boolean existsByUsername(String username); // Devuelve true si ya hay un usuario con ese username (para evitar duplicados al registrar)

    boolean existsByEmail(String email); // Devuelve true si ya hay un usuario con ese email (para evitar duplicados al registrar)
}
