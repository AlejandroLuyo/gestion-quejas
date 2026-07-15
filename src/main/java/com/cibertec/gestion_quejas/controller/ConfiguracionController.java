package com.cibertec.gestion_quejas.controller;

import com.cibertec.gestion_quejas.model.Usuario;
import com.cibertec.gestion_quejas.service.UsuarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

@Controller
@RequestMapping("/configuracion")
public class ConfiguracionController {

    @Autowired
    private UsuarioService usuarioService;

    @GetMapping
    public String ver(Model model, Principal principal) {
        Usuario usuario = usuarioService.buscarPorNombre(principal.getName());
        model.addAttribute("usuario", usuario);
        return "auth/configuracion";
    }

    @PostMapping("/guardar")
    @ResponseBody
    public ResponseEntity<Map<String, String>> guardar(
            @RequestParam String email,
            @RequestParam(required = false) String password,
            @RequestParam String idioma,
            @RequestParam String zonaHoraria,
            @RequestParam(required = false) String firma,
            Principal principal) {
        usuarioService.actualizarPerfil(principal.getName(), email, password, idioma, zonaHoraria, firma);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}