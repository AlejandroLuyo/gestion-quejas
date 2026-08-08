package com.cibertec.gestion_quejas.controller;

import com.cibertec.gestion_quejas.model.Usuario;
import com.cibertec.gestion_quejas.service.UsuarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

@Controller
@RequestMapping("/admin/usuarios")
public class UsuarioController {

    @Autowired
    private UsuarioService usuarioService;

    @GetMapping
    public String listar(@RequestParam(required = false, defaultValue = "fechaCreacion") String orden,
                         @RequestParam(required = false, defaultValue = "desc") String dir,
                         Model model) {
        Sort sort = "asc".equalsIgnoreCase(dir)
                ? Sort.by(orden).ascending()
                : Sort.by(orden).descending();
        model.addAttribute("usuarios", usuarioService.listarTodos(sort));
        model.addAttribute("orden", orden);
        model.addAttribute("dir", dir);
        return "admin/usuarios";
    }

    @GetMapping("/nuevo")
    public String nuevo(Model model) {
        model.addAttribute("usuario", new Usuario());
        return "admin/usuario-form";
    }

    @PostMapping("/guardar")
    public String guardar(@ModelAttribute Usuario usuario,
                          @RequestParam String password,
                          Model model) {
        String error = usuarioService.guardar(usuario, password);
        if (error != null) {
            model.addAttribute("usuario", usuario);
            model.addAttribute("error", error);
            return "admin/usuario-form";
        }
        return "redirect:/admin/usuarios";
    }

    @PostMapping("/{id}/toggle")
    public String toggleActivo(@PathVariable Long id) {
        usuarioService.toggleActivo(id);
        return "redirect:/admin/usuarios";
    }

    @PostMapping("/{id}/editar")
    @ResponseBody
    public ResponseEntity<String> editar(@PathVariable Long id,
                                         @RequestParam String email,
                                         @RequestParam(required = false) String password,
                                         @RequestParam String rol) {
        usuarioService.actualizar(id, email, password, rol);
        return ResponseEntity.ok("ok");
    }
}