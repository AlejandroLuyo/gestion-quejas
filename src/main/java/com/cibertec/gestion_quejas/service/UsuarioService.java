package com.cibertec.gestion_quejas.service;

import com.cibertec.gestion_quejas.model.Usuario;
import com.cibertec.gestion_quejas.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UsuarioService {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public List<Usuario> listarTodos() {
        return usuarioRepository.findAll();
    }

    public Usuario buscarPorId(Long id) {
        return usuarioRepository.findById(id).orElse(null);
    }

    public void guardar(Usuario usuario, String passwordPlano) {
        usuario.setPasswordHash(passwordEncoder.encode(passwordPlano));
        usuarioRepository.save(usuario);
    }

    public void toggleActivo(Long id) {
        Usuario usuario = buscarPorId(id);
        if (usuario != null) {
            usuario.setActivo(!usuario.getActivo());
            usuarioRepository.save(usuario);
        }
    }
    public void actualizar(Long id, String email, String passwordPlano, String rol) {
        Usuario usuario = buscarPorId(id);
        if (usuario == null) return;
        usuario.setEmail(email);
        usuario.setRol(rol);
        if (passwordPlano != null && !passwordPlano.isBlank()) {
            usuario.setPasswordHash(passwordEncoder.encode(passwordPlano));
        }
        usuarioRepository.save(usuario);
    }
    public Usuario buscarPorNombre(String nombre) {
        return usuarioRepository.findByNombre(nombre).orElse(null);
    }

    public void actualizarPerfil(String nombreActual, String email, String passwordPlano,
                                 String idioma, String zonaHoraria) {
        Usuario usuario = usuarioRepository.findByNombre(nombreActual).orElse(null);
        if (usuario == null) return;
        usuario.setEmail(email);
        usuario.setIdioma(idioma);
        usuario.setZonaHoraria(zonaHoraria);
        if (passwordPlano != null && !passwordPlano.isBlank()) {
            usuario.setPasswordHash(passwordEncoder.encode(passwordPlano));
        }
        usuarioRepository.save(usuario);
    }

}