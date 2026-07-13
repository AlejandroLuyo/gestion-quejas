package com.cibertec.gestion_quejas.service;

import com.cibertec.gestion_quejas.model.Conversacion;
import com.cibertec.gestion_quejas.repository.ConversacionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.cibertec.gestion_quejas.model.Csat;
import com.cibertec.gestion_quejas.model.Usuario;
import com.cibertec.gestion_quejas.repository.CsatRepository;
import com.cibertec.gestion_quejas.repository.UsuarioRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class EstadisticaService {

    @Autowired
    private ConversacionRepository conversacionRepository;

    @Autowired
    private CsatRepository csatRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    public Map<String, Object> obtenerEstadisticas(LocalDate desde, LocalDate hasta) {

        LocalDateTime desdeTime = (desde != null) ? desde.atStartOfDay() : null;
        LocalDateTime hastaTime = (hasta != null) ? hasta.atTime(23, 59, 59) : null;

        // Conversaciones filtradas por fecha
        List<Conversacion> todas = conversacionRepository.findAll();
        if (desdeTime != null && hastaTime != null) {
            todas = todas.stream()
                    .filter(c -> c.getConversationCreatedAt() != null
                            && !c.getConversationCreatedAt().isBefore(desdeTime)
                            && !c.getConversationCreatedAt().isAfter(hastaTime))
                    .collect(Collectors.toList());
        }

        Map<String, Object> stats = new HashMap<>();

        long total     = todas.size();
        long abiertas  = todas.stream().filter(c -> "open".equals(c.getCurrentConversationState())).count();
        long pendientes= todas.stream().filter(c -> "pending".equals(c.getCurrentConversationState())).count();
        long resueltas = todas.stream().filter(c -> "resolved".equals(c.getCurrentConversationState())).count();

        long porEmail  = todas.stream()
                .filter(c -> "email".equalsIgnoreCase(c.getChannel()))
                .count();

        long porTicket = todas.stream()
                .filter(c -> "ticket".equalsIgnoreCase(c.getChannel()))
                .count();

        double frtPromedio = todas.stream()
                .filter(c -> c.getFirstResponseTimeSeconds() != null)
                .mapToInt(Conversacion::getFirstResponseTimeSeconds)
                .average().orElse(0);

        Map<String, Long> porContactReason = todas.stream()
                .filter(c -> c.getContactReason() != null)
                .collect(Collectors.groupingBy(Conversacion::getContactReason, Collectors.counting()));

        List<String> crLabels = new ArrayList<>(porContactReason.keySet());
        List<Long>   crData   = crLabels.stream().map(porContactReason::get).collect(Collectors.toList());

        double contactRate = total > 0 ? Math.round((double) total / (total + 100) * 1000.0) / 10.0 : 0;

        // IDs de conversaciones del periodo (para cruzar con CSAT)
        Set<Long> idsEnPeriodo = todas.stream()
                .map(Conversacion::getConversacionId)
                .collect(Collectors.toSet());

        // CSAT del periodo
        List<Csat> todasCsat = csatRepository.findAll().stream()
                .filter(c -> idsEnPeriodo.contains(c.getConversacion().getConversacionId()))
                .collect(Collectors.toList());

        double csatPromedio = todasCsat.stream()
                .filter(c -> c.getPuntuacion() != null)
                .mapToInt(Csat::getPuntuacion)
                .average().orElse(0);
        double csatPromedioRedondeado = Math.round(csatPromedio * 10.0) / 10.0;

        // Resueltas por IA (CSMate)
        List<Conversacion> resueltasIa = todas.stream()
                .filter(c -> "resolved".equals(c.getCurrentConversationState())
                        && "CSMate".equals(c.getTeammateCurrentlyAssigned()))
                .collect(Collectors.toList());
        long totalResueltasIa = resueltasIa.size();
        double pctIa = resueltas > 0
                ? Math.round((double) totalResueltasIa / resueltas * 1000.0) / 10.0 : 0;

        Set<Long> idsIa = resueltasIa.stream()
                .map(Conversacion::getConversacionId)
                .collect(Collectors.toSet());
        double csatIa = todasCsat.stream()
                .filter(c -> c.getPuntuacion() != null
                        && idsIa.contains(c.getConversacion().getConversacionId()))
                .mapToInt(Csat::getPuntuacion)
                .average().orElse(0);
        double csatIaRedondeado = Math.round(csatIa * 10.0) / 10.0;

        // Resoluciones por agente (excluye CSMate)
        Map<String, Long> resolPorAgente = todas.stream()
                .filter(c -> "resolved".equals(c.getCurrentConversationState())
                        && c.getTeammateCurrentlyAssigned() != null
                        && !"CSMate".equals(c.getTeammateCurrentlyAssigned()))
                .collect(Collectors.groupingBy(Conversacion::getTeammateCurrentlyAssigned, Collectors.counting()));

        List<Map<String, Object>> agentesStats = resolPorAgente.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("nombre", e.getKey());
                    m.put("resueltas", e.getValue());
                    double pct = resueltas > 0
                            ? Math.round((double) e.getValue() / resueltas * 1000.0) / 10.0 : 0;
                    m.put("porcentaje", pct);
                    String[] partes = e.getKey().trim().split("\\s+");
                    String iniciales = partes.length >= 2
                            ? ("" + partes[0].charAt(0) + partes[partes.length - 1].charAt(0)).toUpperCase()
                            : e.getKey().substring(0, Math.min(2, e.getKey().length())).toUpperCase();
                    m.put("iniciales", iniciales);
                    return m;
                })
                .collect(Collectors.toList());

        // CSAT por agente
        List<Usuario> agentes = usuarioRepository.findByRolAndActivoTrue("AGENTE");
        List<Map<String, Object>> csatPorAgente = new ArrayList<>();

        for (Usuario agente : agentes) {
            Set<Long> idsAgente = todas.stream()
                    .filter(c -> agente.getNombre().equals(c.getTeammateCurrentlyAssigned()))
                    .map(Conversacion::getConversacionId)
                    .collect(Collectors.toSet());

            List<Csat> csatAgente = todasCsat.stream()
                    .filter(c -> c.getPuntuacion() != null
                            && idsAgente.contains(c.getConversacion().getConversacionId()))
                    .collect(Collectors.toList());

            double promAgente = csatAgente.stream()
                    .mapToInt(Csat::getPuntuacion)
                    .average().orElse(-1);

            String[] partes = agente.getNombre().trim().split("\\s+");
            String iniciales = partes.length >= 2
                    ? ("" + partes[0].charAt(0) + partes[partes.length - 1].charAt(0)).toUpperCase()
                    : agente.getNombre().substring(0, Math.min(2, agente.getNombre().length())).toUpperCase();

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("nombre", agente.getNombre());
            entry.put("iniciales", iniciales);
            entry.put("encuestas", csatAgente.size());
            entry.put("promedio", promAgente >= 0 ? Math.round(promAgente * 10.0) / 10.0 : null);
            csatPorAgente.add(entry);
        }

        // Ordenar: con promedio primero (desc), sin datos al final
        csatPorAgente.sort((a, b) -> {
            Double pa = (Double) a.get("promedio");
            Double pb = (Double) b.get("promedio");
            if (pa == null && pb == null) return 0;
            if (pa == null) return 1;
            if (pb == null) return -1;
            return Double.compare(pb, pa);
        });

        stats.put("total", total);
        stats.put("abiertas", abiertas);
        stats.put("pendientes", pendientes);
        stats.put("resueltas", resueltas);
        stats.put("porEmail", porEmail);
        stats.put("porTicket", porTicket);
        stats.put("frtPromedio", Math.round(frtPromedio));
        stats.put("contactRate", contactRate);
        stats.put("contactReasonLabels", crLabels);
        stats.put("contactReasonData", crData);
        stats.put("csatPromedio", csatPromedioRedondeado);
        stats.put("totalResueltasIa", totalResueltasIa);
        stats.put("pctIa", pctIa);
        stats.put("csatIa", csatIaRedondeado);
        stats.put("agentesStats", agentesStats);
        stats.put("csatPorAgente", csatPorAgente);
        stats.put("desde", desde);
        stats.put("hasta", hasta);

        return stats;
    }
}