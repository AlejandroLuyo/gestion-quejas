package com.cibertec.gestion_quejas.controller;

import com.cibertec.gestion_quejas.model.Conversacion;
import com.cibertec.gestion_quejas.model.Csat;
import com.cibertec.gestion_quejas.repository.CsatRepository;
import com.cibertec.gestion_quejas.service.ConversacionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

@Controller
@RequestMapping("/csat")
public class CsatController {

    @Autowired
    private ConversacionService conversacionService;

    @Autowired
    private CsatRepository csatRepository;

    @GetMapping("/generar/{conversacionId}")
    @ResponseBody
    public Map<String, String> generarLink(@PathVariable Long conversacionId) {
        Conversacion conv = conversacionService.buscarPorId(conversacionId);
        Map<String, String> response = new HashMap<>();

        if (conv == null) {
            response.put("status", "error");
            return response;
        }

        String token = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        conv.setCsatToken(token);
        conversacionService.guardar(conv);

        response.put("status", "ok");
        response.put("link", "/csat/responder?token=" + token);
        return response;
    }

    @GetMapping("/responder")
    public String mostrarEncuesta(@RequestParam String token, Model model) {
        Conversacion conv = conversacionService.buscarPorToken(token);
        if (conv == null) {
            return "redirect:/";
        }
        boolean yaRespondio = csatRepository
                .findByConversacionConversacionId(conv.getConversacionId())
                .isPresent();
        model.addAttribute("token", token);
        model.addAttribute("yaRespondio", yaRespondio);
        model.addAttribute("orderId", conv.getOrderId());
        return "csat/encuesta";
    }

    @PostMapping("/guardar")
    @ResponseBody
    public Map<String, String> guardarEncuesta(
            @RequestParam String token,
            @RequestParam Integer puntuacion,
            @RequestParam(required = false) String comentario) {

        Map<String, String> response = new HashMap<>();
        Conversacion conv = conversacionService.buscarPorToken(token);

        if (conv == null) {
            response.put("status", "error");
            return response;
        }

        if (csatRepository.findByConversacionConversacionId(conv.getConversacionId()).isPresent()) {
            response.put("status", "ya_respondido");
            return response;
        }

        Csat csat = new Csat();
        csat.setConversacion(conv);
        csat.setPuntuacion(puntuacion);
        csat.setComentario(comentario);
        csatRepository.save(csat);

        response.put("status", "ok");
        return response;
    }
}