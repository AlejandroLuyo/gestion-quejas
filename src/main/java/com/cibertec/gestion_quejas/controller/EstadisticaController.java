package com.cibertec.gestion_quejas.controller;

import com.cibertec.gestion_quejas.service.EstadisticaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Map;

@Controller
@RequestMapping("/estadisticas")
public class EstadisticaController {

    @Autowired
    private EstadisticaService estadisticaService;

    @GetMapping
    public String index(Model model) {
        Map<String, Object> stats = estadisticaService.obtenerEstadisticas();
        stats.forEach(model::addAttribute);
        return "estadisticas/index";
    }
}