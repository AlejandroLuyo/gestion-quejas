package com.cibertec.gestion_quejas.controller;

import com.cibertec.gestion_quejas.service.GeminiService;
import com.cibertec.gestion_quejas.service.ResultadoCsmate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PruebaIaController {

    private final GeminiService geminiService;

    public PruebaIaController(GeminiService geminiService) {
        this.geminiService = geminiService;
    }

    @GetMapping("/admin/probar-ia")
    public String probar(@RequestParam(defaultValue = "status_information") String motivo) {
        ResultadoCsmate resultado = geminiService.evaluarConsulta(
                motivo,
                "Hice mi pedido hace 5 días y no me han dado información",
                "Visa Electrónica - Turismo",
                "Estados Unidos",
                "in_progress",
                "standard"
        );

        return "puede_resolver: " + resultado.isPuedeResolver()
                + " | respuesta: " + resultado.getRespuesta()
                + " | motivo_escalamiento: " + resultado.getMotivoEscalamiento();
    }
}