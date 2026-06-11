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
}