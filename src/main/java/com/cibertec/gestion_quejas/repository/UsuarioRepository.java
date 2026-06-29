package com.cibertec.gestion_quejas.repository;

import com.cibertec.gestion_quejas.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByEmail(String email);
    List<Usuario> findByRolAndActivoTrue(String rol);
    Optional<Usuario> findByNombre(String nombre);
}