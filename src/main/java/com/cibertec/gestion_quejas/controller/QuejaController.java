package com.cibertec.gestion_quejas.controller;

import com.cibertec.gestion_quejas.model.Queja;
import com.cibertec.gestion_quejas.service.QuejaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/quejas")
public class QuejaController {

    @Autowired
    private QuejaService quejaService;

    @GetMapping
    public String listar(Model model) {
        List<Queja> quejas = quejaService.listarTodas();

        long pendientes = quejas.stream().filter(q -> q.getEstado().equals("PENDIENTE")).count();
        long enProceso  = quejas.stream().filter(q -> q.getEstado().equals("EN_PROCESO")).count();
        long resueltas  = quejas.stream().filter(q -> q.getEstado().equals("RESUELTO")).count();

        model.addAttribute("quejas", quejas);
        model.addAttribute("totalQuejas", quejas.size());
        model.addAttribute("pendientes", pendientes);
        model.addAttribute("enProceso", enProceso);
        model.addAttribute("resueltas", resueltas);

        return "quejas/lista";
    }

    @GetMapping("/{id}/json")
    @ResponseBody
    public Map<String, String> detalleJson(@PathVariable Long id) {
        Queja q = quejaService.buscarPorId(id);
        Map<String, String> data = new HashMap<>();
        data.put("nombreCliente", q.getNombreCliente());
        data.put("contactReason", q.getContactReason());
        data.put("estado", q.getEstado());
        data.put("origen", q.getOrigen() != null ? q.getOrigen() : "TICKET");
        data.put("descripcion", q.getDescripcion());
        data.put("fechaRegistro", q.getFechaRegistro().format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")));
        return data;
    }

    @GetMapping("/nueva")
    public String nueva(Model model) {
        model.addAttribute("queja", new Queja());
        return "quejas/formulario";
    }

    @PostMapping("/guardar")
    public String guardar(@ModelAttribute Queja queja) {
        quejaService.guardar(queja);
        return "redirect:/quejas";
    }
}