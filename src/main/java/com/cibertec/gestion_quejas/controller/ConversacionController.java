package com.cibertec.gestion_quejas.controller;

import com.cibertec.gestion_quejas.model.Conversacion;
import com.cibertec.gestion_quejas.service.ConversacionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/quejas")
public class ConversacionController {

    @Autowired
    private ConversacionService conversacionService;

    @GetMapping
    public String listar(Model model) {
        List<Conversacion> conversaciones = conversacionService.listarTodas();

        long abiertas = conversaciones.stream()
                .filter(c -> "open".equals(c.getCurrentConversationState())).count();
        long resueltas = conversaciones.stream()
                .filter(c -> "resolved".equals(c.getCurrentConversationState())).count();
        long pendientes = conversaciones.stream()
                .filter(c -> "pending".equals(c.getCurrentConversationState())).count();

        model.addAttribute("conversaciones", conversaciones);
        model.addAttribute("totalQuejas", conversaciones.size());
        model.addAttribute("abiertas", abiertas);
        model.addAttribute("resueltas", resueltas);
        model.addAttribute("pendientes", pendientes);

        return "quejas/lista";
    }

    @GetMapping("/{id}/json")
    @ResponseBody
    public Map<String, String> detalleJson(@PathVariable Long id) {
        Conversacion c = conversacionService.buscarPorId(id);
        Map<String, String> data = new HashMap<>();
        data.put("contactReason", c.getContactReason() != null ? c.getContactReason() : "-");
        data.put("estado", c.getCurrentConversationState() != null ? c.getCurrentConversationState() : "-");
        data.put("canal", c.getChannel() != null ? c.getChannel() : "-");
        data.put("orderId", c.getOrderId() != null ? c.getOrderId() : "-");
        data.put("agente", c.getTeammateCurrentlyAssigned() != null ? c.getTeammateCurrentlyAssigned() : "Sin asignar");
        data.put("fechaCreacion", c.getConversationCreatedAt() != null ?
                c.getConversationCreatedAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")) : "-");
        return data;
    }

    @PostMapping("/{id}/estado")
    @ResponseBody
    public Map<String, String> cambiarEstado(@PathVariable Long id, @RequestParam String estado) {
        Conversacion c = conversacionService.buscarPorId(id);
        if (c != null) {
            c.setCurrentConversationState(estado);
            conversacionService.guardar(c);
        }
        Map<String, String> response = new HashMap<>();
        response.put("status", "ok");
        response.put("estado", estado);
        return response;
    }
}