package com.cibertec.gestion_quejas.controller;

import com.cibertec.gestion_quejas.service.EstadisticaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@Controller
@RequestMapping("/estadisticas")
public class EstadisticaController {

    @Autowired
    private EstadisticaService estadisticaService;

    @GetMapping
    public String index(
            @RequestParam(required = false) String desde,
            @RequestParam(required = false) String hasta,
            Model model) {

        LocalDate desdeDate = (desde != null && !desde.isBlank()) ? LocalDate.parse(desde) : null;
        LocalDate hastaDate = (hasta != null && !hasta.isBlank()) ? LocalDate.parse(hasta) : null;

        Map<String, Object> stats = estadisticaService.obtenerEstadisticas(desdeDate, hastaDate);
        stats.forEach(model::addAttribute);
        return "estadisticas/index";
    }
}