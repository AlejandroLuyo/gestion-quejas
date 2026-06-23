package com.cibertec.gestion_quejas.controller;

import com.cibertec.gestion_quejas.model.Conversacion;
import com.cibertec.gestion_quejas.model.Reembolso;
import com.cibertec.gestion_quejas.repository.OrdenRepository;
import com.cibertec.gestion_quejas.repository.ReembolsoRepository;
import com.cibertec.gestion_quejas.service.ConversacionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Controller
@RequestMapping("/reembolso")
public class ReembolsoController {

    @Autowired
    private ConversacionService conversacionService;

    @Autowired
    private ReembolsoRepository reembolsoRepository;

    @Autowired
    private OrdenRepository ordenRepository;

    @GetMapping("/lista")
    public String vistaLista(
            @RequestParam(required = false, defaultValue = "todos") String estado,
            @RequestParam(required = false, defaultValue = "todos") String agente,
            Model model) {

        List<Reembolso> todos = reembolsoRepository.findAll();

        List<Reembolso> filtrados = todos.stream()
                .filter(r -> estado.equals("todos") || estado.equals(r.getBotRefundStatus())
                        || (estado.equals("aprobado") && "aprobado".equals(r.getRefundResult()))
                        || (estado.equals("denegado") && "denegado".equals(r.getRefundResult()))
                        || (estado.equals("rechazado_supervisor") && "rechazado_supervisor".equals(r.getRefundResult())))
                .filter(r -> agente.equals("todos") ||
                        agente.equals(r.getConversacion().getTeammateCurrentlyAssigned()))
                .toList();

        long pendientes = todos.stream()
                .filter(r -> "pendiente_supervisor".equals(r.getBotRefundStatus())).count();
        long aprobados = todos.stream()
                .filter(r -> "aprobado".equals(r.getRefundResult())).count();
        long denegados = todos.stream()
                .filter(r -> "denegado".equals(r.getRefundResult())
                        || "rechazado_supervisor".equals(r.getRefundResult())).count();

        List<String> agentes = todos.stream()
                .map(r -> r.getConversacion().getTeammateCurrentlyAssigned())
                .filter(a -> a != null)
                .distinct()
                .sorted()
                .toList();

        model.addAttribute("reembolsos", filtrados);
        model.addAttribute("totalReembolsos", todos.size());
        model.addAttribute("pendientes", pendientes);
        model.addAttribute("aprobados", aprobados);
        model.addAttribute("denegados", denegados);
        model.addAttribute("agentes", agentes);
        model.addAttribute("estadoFiltro", estado);
        model.addAttribute("agenteFiltro", agente);

        return "reembolsos/lista";
    }

    // Devuelve los datos del reembolso + datos de la orden para el panel
    @GetMapping("/{conversacionId}")
    @ResponseBody
    public Map<String, Object> obtener(@PathVariable Long conversacionId) {
        Map<String, Object> response = new HashMap<>();
        Conversacion conv = conversacionService.buscarPorId(conversacionId);
        if (conv == null) {
            response.put("status", "error");
            return response;
        }

        // Datos de la orden para validación y monto sugerido
        var orden = ordenRepository.findById(conv.getOrderId()).orElse(null);
        response.put("orderStatus", orden != null ? orden.getOrderStatus() : null);
        response.put("precio", orden != null ? orden.getPrecio() : null);

        // Datos del reembolso si ya existe
        Optional<Reembolso> existing = reembolsoRepository.findByConversacionConversacionId(conversacionId);
        if (existing.isPresent()) {
            Reembolso r = existing.get();
            response.put("reembolsoId", r.getReembolsoId());
            response.put("botRefundStatus", r.getBotRefundStatus());
            response.put("refundResult", r.getRefundResult());
            response.put("refundGranted", r.getRefundGranted());
            response.put("refundAmount", r.getRefundAmount());
            response.put("refundPercent", r.getRefundPercent());
            response.put("refundReasonCategory", r.getRefundReasonCategory());
            response.put("agentNotes", r.getAgentNotes());
            response.put("supervisorNotes", r.getSupervisorNotes());
        }

        response.put("status", "ok");
        return response;
    }

    // Agente envía solicitud al supervisor
    @PostMapping("/{conversacionId}/enviar")
    @ResponseBody
    public Map<String, String> enviarASupervisor(
            @PathVariable Long conversacionId,
            @RequestParam String reasonCategory,
            @RequestParam Double amount,
            @RequestParam Double precio,
            @RequestParam(required = false) String agentNotes) {

        Map<String, String> response = new HashMap<>();
        Conversacion conv = conversacionService.buscarPorId(conversacionId);
        if (conv == null) {
            response.put("status", "error");
            return response;
        }

        Reembolso r = reembolsoRepository
                .findByConversacionConversacionId(conversacionId)
                .orElse(new Reembolso());

        r.setConversacion(conv);
        r.setFlaggedForRefundRequest(true);
        r.setRefundReasonCategory(reasonCategory);
        r.setRefundAmount(amount);
        r.setRefundPercent(precio > 0 ? Math.round((amount / precio) * 10000.0) / 100.0 : null);
        r.setRefundGranted(true);
        r.setBotRefundStatus("pendiente_supervisor");
        r.setAgentNotes(agentNotes);
        reembolsoRepository.save(r);

        response.put("status", "ok");
        return response;
    }

    // Agente deniega el reembolso
    @PostMapping("/{conversacionId}/denegar")
    @ResponseBody
    public Map<String, String> denegar(@PathVariable Long conversacionId) {
        Map<String, String> response = new HashMap<>();
        Conversacion conv = conversacionService.buscarPorId(conversacionId);
        if (conv == null) {
            response.put("status", "error");
            return response;
        }

        Reembolso r = reembolsoRepository
                .findByConversacionConversacionId(conversacionId)
                .orElse(new Reembolso());

        r.setConversacion(conv);
        r.setFlaggedForRefundRequest(true);
        r.setRefundGranted(false);
        r.setBotRefundStatus("cerrado");
        r.setRefundResult("denegado");
        reembolsoRepository.save(r);

        response.put("status", "ok");
        return response;
    }

    // Supervisor aprueba y envía a finanzas
    @PostMapping("/{conversacionId}/aprobar")
    @ResponseBody
    public Map<String, String> aprobar(
            @PathVariable Long conversacionId,
            @RequestParam Double montoFinal,
            @RequestParam Double precio,
            @RequestParam(required = false) String supervisorNotes) {

        Map<String, String> response = new HashMap<>();
        Reembolso r = reembolsoRepository
                .findByConversacionConversacionId(conversacionId)
                .orElse(null);
        if (r == null) {
            response.put("status", "error");
            return response;
        }

        r.setRefundAmount(montoFinal);
        r.setRefundPercent(precio > 0 ? Math.round((montoFinal / precio) * 10000.0) / 100.0 : null);
        r.setSupervisorNotes(supervisorNotes);
        r.setBotRefundStatus("cerrado");
        r.setRefundResult("aprobado");
        reembolsoRepository.save(r);

        response.put("status", "ok");
        return response;
    }

    // Supervisor rechaza la solicitud del agente
    @PostMapping("/{conversacionId}/rechazar")
    @ResponseBody
    public Map<String, String> rechazar(
            @PathVariable Long conversacionId,
            @RequestParam(required = false) String supervisorNotes) {

        Map<String, String> response = new HashMap<>();
        Reembolso r = reembolsoRepository
                .findByConversacionConversacionId(conversacionId)
                .orElse(null);
        if (r == null) {
            response.put("status", "error");
            return response;
        }

        r.setSupervisorNotes(supervisorNotes);
        r.setBotRefundStatus("rechazado_supervisor");
        r.setRefundResult("rechazado_supervisor");
        reembolsoRepository.save(r);

        response.put("status", "ok");
        return response;
    }

    @GetMapping("/detalle/{reembolsoId}")
    @ResponseBody
    public Map<String, Object> detallePorId(@PathVariable Long reembolsoId) {
        Map<String, Object> response = new HashMap<>();
        Reembolso r = reembolsoRepository.findById(reembolsoId).orElse(null);
        if (r == null) { response.put("status", "error"); return response; }

        Conversacion conv = r.getConversacion();
        var orden = ordenRepository.findById(conv.getOrderId()).orElse(null);
        double precio = (orden != null && orden.getPrecio() != null) ? orden.getPrecio() : 0.0;

        Map<String, String> convData = new HashMap<>();
        convData.put("orderId", conv.getOrderId() != null ? conv.getOrderId() : "-");
        convData.put("agente", conv.getTeammateCurrentlyAssigned() != null
                ? conv.getTeammateCurrentlyAssigned() : "Sin asignar");
        convData.put("fecha", conv.getConversationCreatedAt() != null
                ? conv.getConversationCreatedAt().format(
                java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy")) : "-");

        response.put("conversacion", convData);
        response.put("precio", precio);
        response.put("botRefundStatus", r.getBotRefundStatus() != null ? r.getBotRefundStatus() : "");
        response.put("refundResult", r.getRefundResult() != null ? r.getRefundResult() : "");
        response.put("refundAmount", r.getRefundAmount() != null ? r.getRefundAmount() : 0.0);
        response.put("refundPercent", r.getRefundPercent() != null ? r.getRefundPercent() : 0.0);
        response.put("refundReasonCategory", r.getRefundReasonCategory());
        response.put("agentNotes", r.getAgentNotes());
        response.put("supervisorNotes", r.getSupervisorNotes());
        response.put("status", "ok");
        return response;
    }

    @PostMapping("/{reembolsoId}/aprobar-por-id")
    @ResponseBody
    public Map<String, String> aprobarPorId(
            @PathVariable Long reembolsoId,
            @RequestParam Double montoFinal,
            @RequestParam Double precio,
            @RequestParam(required = false) String supervisorNotes) {

        Map<String, String> response = new HashMap<>();
        Reembolso r = reembolsoRepository.findById(reembolsoId).orElse(null);
        if (r == null) { response.put("status", "error"); return response; }

        r.setRefundAmount(montoFinal);
        r.setRefundPercent(precio > 0 ? Math.round((montoFinal / precio) * 10000.0) / 100.0 : 0.0);
        r.setSupervisorNotes(supervisorNotes);
        r.setBotRefundStatus("cerrado");
        r.setRefundResult("aprobado");
        reembolsoRepository.save(r);

        response.put("status", "ok");
        return response;
    }

    @PostMapping("/{reembolsoId}/rechazar-por-id")
    @ResponseBody
    public Map<String, String> rechazarPorId(
            @PathVariable Long reembolsoId,
            @RequestParam(required = false) String supervisorNotes) {

        Map<String, String> response = new HashMap<>();
        Reembolso r = reembolsoRepository.findById(reembolsoId).orElse(null);
        if (r == null) { response.put("status", "error"); return response; }

        r.setSupervisorNotes(supervisorNotes);
        r.setBotRefundStatus("rechazado_supervisor");
        r.setRefundResult("rechazado_supervisor");
        reembolsoRepository.save(r);

        response.put("status", "ok");
        return response;
    }

    @PostMapping("/{conversacionId}/cerrar-definitivo")
    @ResponseBody
    public Map<String, String> cerrarDefinitivo(@PathVariable Long conversacionId) {
        Map<String, String> response = new HashMap<>();
        Reembolso r = reembolsoRepository
                .findByConversacionConversacionId(conversacionId)
                .orElse(null);
        if (r == null) { response.put("status", "error"); return response; }

        r.setBotRefundStatus("cerrado");
        r.setRefundResult("denegado");
        reembolsoRepository.save(r);

        response.put("status", "ok");
        return response;
    }

}