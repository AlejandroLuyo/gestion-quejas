package com.cibertec.gestion_quejas.service;

import com.cibertec.gestion_quejas.model.Auditoria;
import com.cibertec.gestion_quejas.model.Conversacion;
import com.cibertec.gestion_quejas.model.Usuario;
import com.cibertec.gestion_quejas.repository.AuditoriaRepository;
import com.cibertec.gestion_quejas.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AuditoriaService {

    @Autowired
    private AuditoriaRepository auditoriaRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    public void registrarCambio(Conversacion conversacion, String nombreUsuario,
                                String accion, String valorAnterior, String valorNuevo) {
        if (nombreUsuario == null || nombreUsuario.isBlank()) {
            return;
        }
        Usuario usuario = usuarioRepository.findByNombre(nombreUsuario).orElse(null);
        if (usuario == null) {
            return;
        }

        Auditoria auditoria = new Auditoria();
        auditoria.setConversacion(conversacion);
        auditoria.setUsuario(usuario);
        auditoria.setAccion(accion);
        auditoria.setValorAnterior(valorAnterior);
        auditoria.setValorNuevo(valorNuevo);
        auditoriaRepository.save(auditoria);
    }
}