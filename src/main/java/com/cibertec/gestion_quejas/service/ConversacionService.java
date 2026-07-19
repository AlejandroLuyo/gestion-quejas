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

    private static final java.util.Map<String, String> CONTACT_REASON_LABELS = java.util.Map.ofEntries(
            java.util.Map.entry("payment_issues", "problemas de pago"),
            java.util.Map.entry("refund_request", "solicitud de reembolso"),
            java.util.Map.entry("status_information", "información de estado"),
            java.util.Map.entry("cx_modify", "modificación de orden"),
            java.util.Map.entry("deliverable_information", "información de entrega"),
            java.util.Map.entry("requirements_assistance", "asistencia de requisitos"),
            java.util.Map.entry("upload_support", "soporte de carga"),
            java.util.Map.entry("consulta_general", "consulta general")
    );

    @Autowired
    private ConversacionRepository conversacionRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    public List<Conversacion> listarTodas(Sort sort) {
        return conversacionRepository.findAllConOrden(sort);
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

    public List<Conversacion> buscarConFiltros(String texto,
                                               LocalDateTime desde,
                                               LocalDateTime hasta,
                                               Sort sort) {

        List<Conversacion> lista = conversacionRepository.findAllConOrden(sort);

        return lista.stream()
                .filter(c -> {
                    boolean coincideTexto = texto == null || texto.isBlank()
                            || (c.getOrderId() != null && c.getOrderId().toLowerCase().contains(texto.toLowerCase()))
                            || (c.getContactReason() != null && c.getContactReason().toLowerCase().contains(texto.toLowerCase()))
                            || (c.getContactReason() != null && CONTACT_REASON_LABELS
                            .getOrDefault(c.getContactReason(), "").contains(texto.toLowerCase()))
                            || (c.getTeammateCurrentlyAssigned() != null &&
                            c.getTeammateCurrentlyAssigned().toLowerCase().contains(texto.toLowerCase()));

                    boolean coincideDesde = desde == null ||
                            !c.getConversationCreatedAt().isBefore(desde);

                    boolean coincideHasta = hasta == null ||
                            !c.getConversationCreatedAt().isAfter(hasta);

                    return coincideTexto && coincideDesde && coincideHasta;
                })
                .toList();
    }

    public Optional<Conversacion> buscarActivaPorOrdenYCanal(String orderId, String channel, List<String> estados) {
        return conversacionRepository.findFirstByOrderIdAndChannelAndCurrentConversationStateIn(orderId, channel, estados);
    }

}