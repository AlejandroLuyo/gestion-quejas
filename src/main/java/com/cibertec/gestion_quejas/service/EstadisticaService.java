package com.cibertec.gestion_quejas.service;

import com.cibertec.gestion_quejas.model.Conversacion;
import com.cibertec.gestion_quejas.repository.ConversacionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class EstadisticaService {

    @Autowired
    private ConversacionRepository conversacionRepository;

    public Map<String, Object> obtenerEstadisticas() {
        List<Conversacion> todas = conversacionRepository.findAll();
        Map<String, Object> stats = new HashMap<>();

        long total = todas.size();
        long abiertas = todas.stream().filter(c -> "open".equals(c.getCurrentConversationState())).count();
        long pendientes = todas.stream().filter(c -> "pending".equals(c.getCurrentConversationState())).count();
        long resueltas = todas.stream().filter(c -> "resolved".equals(c.getCurrentConversationState())).count();

        long porEmail = todas.stream().filter(c -> "email".equals(c.getChannel())).count();
        long porWhatsapp = todas.stream().filter(c -> "whatsapp".equals(c.getChannel())).count();
        long porTicket = todas.stream().filter(c -> "ticket".equals(c.getChannel())).count();

        double frtPromedio = todas.stream()
                .filter(c -> c.getFirstResponseTimeSeconds() != null)
                .mapToInt(Conversacion::getFirstResponseTimeSeconds)
                .average().orElse(0);

        Map<String, Long> porContactReason = todas.stream()
                .filter(c -> c.getContactReason() != null)
                .collect(Collectors.groupingBy(Conversacion::getContactReason, Collectors.counting()));

        List<String> crLabels = new ArrayList<>(porContactReason.keySet());
        List<Long> crData = crLabels.stream().map(porContactReason::get).collect(Collectors.toList());

        double contactRate = total > 0 ? Math.round((double) total / (total + 100) * 1000.0) / 10.0 : 0;

        stats.put("total", total);
        stats.put("abiertas", abiertas);
        stats.put("pendientes", pendientes);
        stats.put("resueltas", resueltas);
        stats.put("porEmail", porEmail);
        stats.put("porWhatsapp", porWhatsapp);
        stats.put("porTicket", porTicket);
        stats.put("frtPromedio", Math.round(frtPromedio));
        stats.put("contactRate", contactRate);
        stats.put("contactReasonLabels", crLabels);
        stats.put("contactReasonData", crData);

        return stats;
    }
}