package com.cibertec.gestion_quejas.controller;

import com.cibertec.gestion_quejas.model.Usuario;
import com.cibertec.gestion_quejas.repository.UsuarioRepository;
import com.cibertec.gestion_quejas.service.AsistenteAgenteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/asistente")
public class AsistenteAgenteController {

    @Autowired
    private AsistenteAgenteService asistenteAgenteService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @GetMapping("/saludo")
    public Map<String, String> saludo(Principal principal) {
        Usuario usuario = usuarioRepository.findByNombre(principal.getName()).orElse(null);
        Map<String, String> response = new HashMap<>();

        String nombre = (usuario != null) ? usuario.getNombre() : principal.getName();
        response.put("saludo", asistenteAgenteService.generarSaludo(nombre));
        return response;
    }

    @PostMapping("/consultar")
    public Map<String, String> consultar(@RequestParam String mensaje, Principal principal) {
        Map<String, String> response = new HashMap<>();

        Usuario usuario = usuarioRepository.findByNombre(principal.getName()).orElse(null);
        if (usuario == null || mensaje == null || mensaje.isBlank()) {
            response.put("respuesta", "No pude identificar tu usuario. Intenta recargar la página.");
            return response;
        }

        String respuesta = asistenteAgenteService.responder(mensaje, usuario);
        response.put("respuesta", respuesta);
        return response;
    }
}