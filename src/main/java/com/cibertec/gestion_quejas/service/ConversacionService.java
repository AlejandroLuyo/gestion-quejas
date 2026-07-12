package com.cibertec.gestion_quejas.service;

import com.cibertec.gestion_quejas.model.Conversacion;
import com.cibertec.gestion_quejas.model.Usuario;
import com.cibertec.gestion_quejas.repository.ConversacionRepository;
import com.cibertec.gestion_quejas.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Sort;
import java.time.LocalDateTime;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ConversacionService {

    @Autowired
    private ConversacionRepository conversacionRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    public List<Conversacion> listarTodas(Sort sort) {
        return conversacionRepository.findAll(sort);
    }

    public List<Conversacion> listarPorEstado(String estado, Sort sort) {
        return conversacionRepository.findByCurrentConversationState(estado, sort);
    }

    public List<Conversacion> listarAsignadasA(String nombre, Sort sort) {
        return conversacionRepository.findByTeammateCurrentlyAssigned(nombre, sort);
    }

    public List<Conversacion> listarSinAsignar(Sort sort) {
        return conversacionRepository.findByTeammateCurrentlyAssignedIsNull(sort);
    }

    public List<Conversacion> listarRequierenRevision(Sort sort) {
        return conversacionRepository.findByRequiereRevisionManualTrue(sort);
    }

    public List<Conversacion> listarResueltasPorIA(Sort sort) {
        return conversacionRepository.findByTeammateCurrentlyAssignedAndCurrentConversationState("CSMate", "resolved", sort);
    }

    @Transactional
    public Conversacion guardar(Conversacion conversacion) {
        return conversacionRepository.save(conversacion);
    }

    public Conversacion buscarPorId(Long id) {
        return conversacionRepository.findById(id).orElse(null);
    }

    public Conversacion buscarPorToken(String token) {
        return conversacionRepository.findByCsatToken(token).orElse(null);
    }

    public boolean existePorEmailMessageId(String emailMessageId) {
        return conversacionRepository.existsByEmailMessageId(emailMessageId);
    }

    public void eliminar(Long id) {
        conversacionRepository.deleteById(id);
    }

    public String seleccionarAgenteConMenosCarga() {
        List<Usuario> agentes = usuarioRepository.findByRolAndActivoTrue("AGENTE");
        List<String> estadosActivos = List.of("open", "pending");

        Comparator<Usuario> porCargaYNombre = Comparator
                .comparingLong((Usuario a) -> conversacionRepository
                        .countByTeammateCurrentlyAssignedAndCurrentConversationStateIn(a.getNombre(), estadosActivos))
                .thenComparing(Usuario::getNombre);

        return agentes.stream()
                .min(porCargaYNombre)
                .map(Usuario::getNombre)
                .orElse(null);
    }

    @Transactional
    public String generarTokenCsat(Conversacion conversacion) {
        String token = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        conversacion.setCsatToken(token);
        guardar(conversacion);
        return token;
    }

    public List<Conversacion> buscarConFiltros(String texto, LocalDateTime desde, LocalDateTime hasta, Sort sort) {
        String textoBusqueda = (texto != null && !texto.isBlank()) ? texto.trim() : null;
        return conversacionRepository.buscarConFiltros(textoBusqueda, desde, hasta, sort);
    }

    public Optional<Conversacion> buscarActivaPorOrdenYCanal(String orderId, String channel, List<String> estados) {
        return conversacionRepository.findFirstByOrderIdAndChannelAndCurrentConversationStateIn(orderId, channel, estados);
    }

}