package com.cibertec.gestion_quejas.controller;

import com.cibertec.gestion_quejas.model.Conversacion;
import com.cibertec.gestion_quejas.service.ConversacionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import com.cibertec.gestion_quejas.model.Mensaje;
import com.cibertec.gestion_quejas.repository.MensajeRepository;
import java.util.ArrayList;
import org.springframework.data.domain.Sort;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/quejas")
public class ConversacionController {

    @Autowired
    private ConversacionService conversacionService;

    @Autowired
    private MensajeRepository mensajeRepository;

    @GetMapping
    public String listar(@RequestParam(required = false, defaultValue = "todas") String vista,
                         @RequestParam(required = false, defaultValue = "fecha") String orden,
                         @RequestParam(required = false, defaultValue = "desc") String dir,
                         Model model,
                         java.security.Principal principal) {

        String campoOrden = switch (orden) {
            case "agente" -> "teammateCurrentlyAssigned";
            case "estado" -> "currentConversationState";
            default -> "conversationCreatedAt";
        };

        Sort sort = "asc".equalsIgnoreCase(dir)
                ? Sort.by(campoOrden).ascending()
                : Sort.by(campoOrden).descending();

        List<Conversacion> todas = conversacionService.listarTodas(sort);
        List<Conversacion> conversaciones;
        String tituloVista;

        switch (vista) {
            case "asignadas":
                conversaciones = conversacionService.listarAsignadasA(principal.getName(), sort);
                tituloVista = "Asignadas a mí";
                break;
            case "sin-asignar":
                conversaciones = conversacionService.listarSinAsignar(sort);
                tituloVista = "Sin asignar";
                break;
            case "pendientes":
                conversaciones = conversacionService.listarPorEstado("pending", sort);
                tituloVista = "Pendientes";
                break;
            case "en-proceso":
                conversaciones = conversacionService.listarPorEstado("open", sort);
                tituloVista = "En proceso";
                break;
            case "resueltas":
                conversaciones = conversacionService.listarPorEstado("resolved", sort);
                tituloVista = "Resueltas";
                break;
            default:
                conversaciones = todas;
                tituloVista = "Vista general de quejas";
                vista = "todas";
        }

        long abiertas = todas.stream()
                .filter(c -> "open".equals(c.getCurrentConversationState())).count();
        long resueltas = todas.stream()
                .filter(c -> "resolved".equals(c.getCurrentConversationState())).count();
        long pendientes = todas.stream()
                .filter(c -> "pending".equals(c.getCurrentConversationState())).count();

        model.addAttribute("conversaciones", conversaciones);
        model.addAttribute("totalQuejas", todas.size());
        model.addAttribute("abiertas", abiertas);
        model.addAttribute("resueltas", resueltas);
        model.addAttribute("pendientes", pendientes);
        model.addAttribute("tituloVista", tituloVista);
        model.addAttribute("vista", vista);
        model.addAttribute("orden", orden);
        model.addAttribute("dir", dir);

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

    @GetMapping("/{id}/mensajes")
    @ResponseBody
    public List<Map<String, String>> getMensajes(@PathVariable Long id) {
        List<Mensaje> mensajes = mensajeRepository.findByConversacionConversacionIdOrderByFechaEnvioAsc(id);
        List<Map<String, String>> result = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMM, HH:mm");
        for (Mensaje m : mensajes) {
            Map<String, String> msg = new HashMap<>();
            msg.put("contenido", m.getContenido());
            msg.put("remitente", m.getRemitente());
            msg.put("fechaEnvio", m.getFechaEnvio().format(formatter));
            result.add(msg);
        }
        return result;
    }

    @PostMapping("/{id}/responder")
    @ResponseBody
    public Map<String, String> responder(@PathVariable Long id, @RequestParam String contenido) {
        Conversacion conversacion = conversacionService.buscarPorId(id);
        Map<String, String> response = new HashMap<>();

        if (conversacion != null && !contenido.isBlank()) {
            Mensaje mensaje = new Mensaje();
            mensaje.setConversacion(conversacion);
            mensaje.setContenido(contenido);
            mensaje.setRemitente("AGENTE");
            mensaje.setCanal(conversacion.getChannel() != null ? conversacion.getChannel().toUpperCase() : "INTERNO");
            mensajeRepository.save(mensaje);
            response.put("status", "ok");
        } else {
            response.put("status", "error");
        }
        return response;
    }
}