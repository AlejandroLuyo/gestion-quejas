package com.cibertec.gestion_quejas.service;

import com.cibertec.gestion_quejas.model.Asignacion;
import com.cibertec.gestion_quejas.model.Conversacion;
import com.cibertec.gestion_quejas.model.Usuario;
import com.cibertec.gestion_quejas.repository.AsignacionRepository;
import com.cibertec.gestion_quejas.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AsignacionService {

    @Autowired
    private AsignacionRepository asignacionRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    public void registrarAsignacion(Conversacion conversacion, String nombreUsuario) {
        if (nombreUsuario == null || nombreUsuario.isBlank()) {
            return;
        }
        Usuario usuario = usuarioRepository.findByNombre(nombreUsuario).orElse(null);
        if (usuario == null) {
            return;
        }

        Asignacion asignacion = new Asignacion();
        asignacion.setConversacion(conversacion);
        asignacion.setUsuario(usuario);
        asignacion.setActivo(true);
        asignacionRepository.save(asignacion);
    }
}