package com.cibertec.gestion_quejas.controller;

import com.cibertec.gestion_quejas.model.Conversacion;
import com.cibertec.gestion_quejas.model.Mensaje;
import com.cibertec.gestion_quejas.model.Orden;
import com.cibertec.gestion_quejas.repository.MensajeRepository;
import com.cibertec.gestion_quejas.repository.OrdenRepository;
import com.cibertec.gestion_quejas.service.ConversacionService;
import com.cibertec.gestion_quejas.service.IaService;
import com.cibertec.gestion_quejas.service.ResultadoCsmate;
import com.cibertec.gestion_quejas.service.ResultadoTurno;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/portal-cliente")
public class PortalClienteController {

    @Autowired
    private OrdenRepository ordenRepository;

    @Autowired
    private ConversacionService conversacionService;

    @Autowired
    private MensajeRepository mensajeRepository;

    @Autowired
    private IaService iaService;

    @Value("${app.base-url}")
    private String baseUrl;

    @GetMapping
    public String mostrarFormulario() {
        return "admin/portal-cliente";
    }

    @GetMapping("/verificar-orden")
    @ResponseBody
    public Map<String, Object> verificarOrden(@RequestParam String orderId) {
        Map<String, Object> response = new HashMap<>();
        Orden orden = ordenRepository.findById(orderId).orElse(null);

        if (orden == null) {
            response.put("status", "error");
            return response;
        }

        response.put("status", "ok");
        response.put("nombreCliente", orden.getNombreCliente());
        response.put("emailCliente", orden.getEmailCliente());
        return response;
    }

    @PostMapping
    public String registrarQueja(@RequestParam String orderId,
                                 @RequestParam String contactReason,
                                 @RequestParam String mensaje,
                                 Model model) {

        Orden orden = ordenRepository.findById(orderId).orElse(null);
        if (orden == null) {
            return "redirect:/admin/portal-cliente";
        }

        Conversacion conversacion = new Conversacion();
        conversacion.setOrderId(orderId);
        conversacion.setChannel("ticket");
        conversacion.setContactReason(contactReason);
        conversacion.setLanguage("es");
        conversacion.setTeammateCurrentlyAssigned("CSMate");
        conversacionService.guardar(conversacion);

        Mensaje msgCliente = new Mensaje();
        msgCliente.setConversacion(conversacion);
        msgCliente.setContenido(mensaje);
        msgCliente.setRemitente("CLIENTE");
        msgCliente.setCanal("TICKET");
        mensajeRepository.save(msgCliente);

        ResultadoCsmate resultado = iaService.evaluarConsulta(
                contactReason,
                mensaje,
                orden.getProducto().getProductName(),
                orden.getDestinationCountry(),
                orden.getOrderStatus(),
                orden.getProcessingSpeed()
        );

        if (resultado.isPuedeResolver()) {
            Mensaje msgBot = new Mensaje();
            msgBot.setConversacion(conversacion);
            msgBot.setContenido(resultado.getRespuesta());
            msgBot.setRemitente("BOT");
            msgBot.setCanal("TICKET");
            mensajeRepository.save(msgBot);

            conversacion.setCurrentConversationState("pending");
        } else {
            conversacion.setBotTransferReason(resultado.getMotivoEscalamiento());
            conversacion.setTeammateCurrentlyAssigned(conversacionService.seleccionarAgenteConMenosCarga());
        }

        conversacionService.guardar(conversacion);

        model.addAttribute("conversacion", conversacion);
        return "admin/portal-cliente-confirmacion";
    }

    // Recibe cada mensaje nuevo del cliente mientras la conversación sigue abierta con CSMate
    @PostMapping("/{id}/mensaje")
    @ResponseBody
    public Map<String, Object> continuarConversacion(@PathVariable Long id, @RequestParam String mensaje) {
        Map<String, Object> response = new HashMap<>();
        Conversacion conversacion = conversacionService.buscarPorId(id);

        if (conversacion == null) {
            response.put("status", "error");
            return response;
        }

        Mensaje msgCliente = new Mensaje();
        msgCliente.setConversacion(conversacion);
        msgCliente.setContenido(mensaje);
        msgCliente.setRemitente("CLIENTE");
        msgCliente.setCanal("TICKET");
        mensajeRepository.save(msgCliente);

        Orden orden = ordenRepository.findById(conversacion.getOrderId()).orElse(null);

        List<Mensaje> historial = mensajeRepository
                .findByConversacionConversacionIdOrderByFechaEnvioAsc(id);
        String historialTexto = historial.stream()
                .map(m -> m.getRemitente() + ": " + m.getContenido())
                .collect(Collectors.joining("\n"));

        ResultadoTurno resultado = iaService.evaluarTurno(
                conversacion.getContactReason(),
                historialTexto,
                mensaje,
                orden.getProducto().getProductName(),
                orden.getDestinationCountry(),
                orden.getOrderStatus(),
                orden.getProcessingSpeed()
        );

        String contenidoBot = resultado.getRespuesta();

        if (resultado.getEstado() == ResultadoTurno.Estado.CERRAR_SATISFECHO) {
            conversacion.setCurrentConversationState("resolved");
            String token = conversacionService.generarTokenCsat(conversacion);
            String linkRelativo = "/csat/responder?token=" + token;
            String linkAbsoluto = baseUrl + linkRelativo;
            contenidoBot = contenidoBot + "\n\nPor favor califica tu experiencia aquí: " + linkAbsoluto;
            response.put("link", linkRelativo);
        } else if (resultado.getEstado() == ResultadoTurno.Estado.ESCALAR) {
            String agente = conversacionService.seleccionarAgenteConMenosCarga();
            conversacion.setTeammateCurrentlyAssigned(agente);
            conversacion.setBotTransferReason(resultado.getMotivoEscalamiento());
            conversacion.setCurrentConversationState("open");
            conversacionService.guardar(conversacion);
        }

        Mensaje msgBot = new Mensaje();
        msgBot.setConversacion(conversacion);
        msgBot.setContenido(contenidoBot);
        msgBot.setRemitente("BOT");
        msgBot.setCanal("TICKET");
        mensajeRepository.save(msgBot);

        response.put("status", "ok");
        response.put("estadoConversacion", resultado.getEstado().toString());
        response.put("respuestaBot", contenidoBot);
        return response;
    }

    @GetMapping("/probar-ia")
    @ResponseBody
    public String probarIa() {
        try {
            ResultadoCsmate r = iaService.evaluarConsulta(
                    "status_information",
                    "Quiero saber el estado de mi orden",
                    "Visa Turismo",
                    "Estados Unidos",
                    "in_progress",
                    "standard"
            );
            return "OK - puede_resolver: " + r.isPuedeResolver() + " | respuesta: " + r.getRespuesta();
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }
}