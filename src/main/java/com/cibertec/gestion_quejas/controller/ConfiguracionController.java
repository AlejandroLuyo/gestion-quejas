package com.cibertec.gestion_quejas.controller;

import com.cibertec.gestion_quejas.model.Usuario;
import com.cibertec.gestion_quejas.service.FeatureFlagService;
import com.cibertec.gestion_quejas.service.UsuarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
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

    @Autowired
    private FeatureFlagService featureFlagService;

    @GetMapping
    public String ver(Model model, Principal principal, Authentication authentication) {
        Usuario usuario = usuarioService.buscarPorNombre(principal.getName());
        model.addAttribute("usuario", usuario);

        boolean esAdministrador = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(rol -> rol.equals("ROLE_ADMINISTRADOR"));

        if (esAdministrador) {
            model.addAttribute("flags", featureFlagService.listarTodos());
        }

        return "auth/configuracion";
    }

    @PostMapping("/guardar")
    @ResponseBody
    public ResponseEntity<Map<String, String>> guardar(
            @RequestParam String email,
            @RequestParam(required = false) String password,
            @RequestParam String idioma,
            @RequestParam String zonaHoraria,
            Principal principal) {
        usuarioService.actualizarPerfil(principal.getName(), email, password, idioma, zonaHoraria);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}