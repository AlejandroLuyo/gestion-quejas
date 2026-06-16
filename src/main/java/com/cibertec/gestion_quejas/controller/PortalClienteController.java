package com.cibertec.gestion_quejas.controller;

import com.cibertec.gestion_quejas.model.Conversacion;
import com.cibertec.gestion_quejas.model.Mensaje;
import com.cibertec.gestion_quejas.model.Orden;
import com.cibertec.gestion_quejas.repository.MensajeRepository;
import com.cibertec.gestion_quejas.repository.OrdenRepository;
import com.cibertec.gestion_quejas.service.ConversacionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/admin/portal-cliente")
public class PortalClienteController {

    @Autowired
    private OrdenRepository ordenRepository;

    @Autowired
    private ConversacionService conversacionService;

    @Autowired
    private MensajeRepository mensajeRepository;

    @GetMapping
    public String mostrarFormulario() {
        return "admin/portal-cliente";
    }

    @GetMapping("/verificar-orden")
    @ResponseBody
    public Map<String, Object> verificarOrden(@RequestParam String orderId) {
        Map<String, Object> response = new HashMap<>();
        Orden orden = ordenRepository.findById(orderId).orElse(null);

        if (orden == null) {
            response.put("status", "error");
            return response;
        }

        response.put("status", "ok");
        response.put("nombreCliente", orden.getNombreCliente());
        response.put("emailCliente", orden.getEmailCliente());
        return response;
    }

    @PostMapping
    public String registrarQueja(@RequestParam String orderId,
                                 @RequestParam String contactReason,
                                 @RequestParam String mensaje,
                                 Model model) {

        Orden orden = ordenRepository.findById(orderId).orElse(null);
        if (orden == null) {
            return "redirect:/admin/portal-cliente";
        }

        Conversacion conversacion = new Conversacion();
        conversacion.setOrderId(orderId);
        conversacion.setChannel("ticket");
        conversacion.setContactReason(contactReason);
        conversacion.setLanguage("es");
        conversacion.setTeammateCurrentlyAssigned("CSMate");
        conversacionService.guardar(conversacion);

        Mensaje msg = new Mensaje();
        msg.setConversacion(conversacion);
        msg.setContenido(mensaje);
        msg.setRemitente("CLIENTE");
        msg.setCanal("TICKET");
        mensajeRepository.save(msg);

        model.addAttribute("conversacion", conversacion);
        return "admin/portal-cliente-confirmacion";
    }
}