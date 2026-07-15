package com.cibertec.gestion_quejas.controller;

import com.cibertec.gestion_quejas.service.FeatureFlagService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Controller
@RequestMapping("/config/features")
public class FeatureFlagController {

    private final FeatureFlagService service;

    public FeatureFlagController(FeatureFlagService service) {
        this.service = service;
    }

    @GetMapping
    public String verConfiguracion(Model model) {
        model.addAttribute("flags", service.listarTodos());
        return "config/features";
    }

    @PostMapping("/toggle")
    public String toggle(@RequestParam String key, @RequestParam boolean value) {
        service.toggle(key, value);
        return "redirect:/config/features";
    }

    // Guarda varios flags a la vez, usado desde la vista de Configuración
    // junto con el botón "Guardar cambios"
    @PostMapping("/guardar")
    @ResponseBody
    public ResponseEntity<Map<String, String>> guardarVarios(@RequestBody Map<String, Boolean> estados) {
        service.guardarEstados(estados);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}