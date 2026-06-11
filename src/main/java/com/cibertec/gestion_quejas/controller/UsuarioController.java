package com.cibertec.gestion_quejas.controller;

import com.cibertec.gestion_quejas.model.Usuario;
import com.cibertec.gestion_quejas.service.UsuarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/admin/usuarios")
public class UsuarioController {

    @Autowired
    private UsuarioService usuarioService;

    @GetMapping
    public String listar(Model model) {
        model.addAttribute("usuarios", usuarioService.listarTodos());
        return "admin/usuarios";
    }

    @GetMapping("/nuevo")
    public String nuevo(Model model) {
        model.addAttribute("usuario", new Usuario());
        return "admin/usuario-form";
    }

    @PostMapping("/guardar")
    public String guardar(@ModelAttribute Usuario usuario,
                          @RequestParam String password) {
        usuarioService.guardar(usuario, password);
        return "redirect:/admin/usuarios";
    }

    @PostMapping("/{id}/toggle")
    public String toggleActivo(@PathVariable Long id) {
        usuarioService.toggleActivo(id);
        return "redirect:/admin/usuarios";
    }
}