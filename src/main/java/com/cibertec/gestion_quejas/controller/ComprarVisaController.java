package com.cibertec.gestion_quejas.controller;

import com.cibertec.gestion_quejas.model.Orden;
import com.cibertec.gestion_quejas.model.Producto;
import com.cibertec.gestion_quejas.repository.OrdenRepository;
import com.cibertec.gestion_quejas.repository.ProductoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.PageRequest;
import java.util.List;

import java.time.LocalDate;
import java.util.Map;

@Controller
@RequestMapping("/admin/comprar-visa")
public class ComprarVisaController {

    private static final Map<String, Double> PRECIOS = Map.of(
            "standard", 80.0,
            "rush", 120.0,
            "super_rush", 150.0
    );

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private OrdenRepository ordenRepository;

    @GetMapping
    public String mostrarFormulario(@RequestParam(required = false) String ordenGenerada, Model model) {
        model.addAttribute("productos", productoRepository.findByActivoTrue());
        model.addAttribute("ordenGenerada", ordenGenerada);
        return "admin/comprar-visa";
    }

    @PostMapping
    public String generar(@RequestParam Long productoId,
                          @RequestParam String destinationCountry,
                          @RequestParam String userNationality,
                          @RequestParam String nombreCliente,
                          @RequestParam String emailCliente,
                          @RequestParam String processingSpeed) {

        Producto producto = productoRepository.findById(productoId).orElse(null);

        Orden orden = new Orden();
        orden.setOrderId(generarSiguienteCodigoOrden());
        orden.setProducto(producto);
        orden.setDestinationCountry(destinationCountry);
        orden.setUserNationality(userNationality);
        orden.setNombreCliente(nombreCliente);
        orden.setEmailCliente(emailCliente);
        orden.setProcessingSpeed(processingSpeed);
        orden.setPrecio(PRECIOS.getOrDefault(processingSpeed, 80.0));
        orden.setCountry(destinationCountry);
        orden.setContinent("");
        orden.setOrderStatus("in_progress");
        orden.setDateEnteredOrderStatus(LocalDate.now().toString());
        ordenRepository.save(orden);

        return "redirect:/admin/comprar-visa?ordenGenerada=" + orden.getOrderId();
    }
    private String generarSiguienteCodigoOrden() {
        List<Orden> ultimas = ordenRepository.listarTodasOrdenadasPorIdDesc(PageRequest.of(0, 1));
        int siguienteNumero = 1;
        if (!ultimas.isEmpty()) {
            String soloNumero = ultimas.get(0).getOrderId().replaceAll("\\D+", "");
            siguienteNumero = Integer.parseInt(soloNumero) + 1;
        }
        return "ORD-" + String.format("%05d", siguienteNumero);
    }
}