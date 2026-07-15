package com.cibertec.gestion_quejas.controller;

import com.cibertec.gestion_quejas.model.Conversacion;
import com.cibertec.gestion_quejas.model.Csat;
import com.cibertec.gestion_quejas.model.Usuario;
import com.cibertec.gestion_quejas.repository.CsatRepository;
import com.cibertec.gestion_quejas.repository.UsuarioRepository;
import com.cibertec.gestion_quejas.service.AsignacionService;
import com.cibertec.gestion_quejas.service.AuditoriaService;
import com.cibertec.gestion_quejas.service.ConversacionService;
import com.cibertec.gestion_quejas.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import com.cibertec.gestion_quejas.model.Mensaje;
import com.cibertec.gestion_quejas.repository.MensajeRepository;
import java.util.ArrayList;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import java.time.LocalDate;
import java.time.LocalDateTime;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/quejas")
public class ConversacionController {

    @Autowired
    private ConversacionService conversacionService;

    @Autowired
    private MensajeRepository mensajeRepository;

    @Autowired
    private AsignacionService asignacionService;

    @Autowired
    private AuditoriaService auditoriaService;

    @Autowired
    private CsatRepository csatRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @GetMapping
    public String listar(@RequestParam(required = false, defaultValue = "todas") String vista,
                         @RequestParam(required = false, defaultValue = "fecha") String orden,
                         @RequestParam(required = false, defaultValue = "desc") String dir,
                         @RequestParam(required = false) String q,
                         @RequestParam(required = false) String desde,
                         @RequestParam(required = false) String hasta,
                         Model model,
                         java.security.Principal principal) {

        String campoOrden = switch (orden) {
            case "agente" -> "teammateCurrentlyAssigned";
            case "estado" -> "currentConversationState";
            default -> "conversationCreatedAt";
        };

        Sort sort = "asc".equalsIgnoreCase(dir)
                ? Sort.by(campoOrden).ascending()
                : Sort.by(campoOrden).descending();

        List<Conversacion> todas = conversacionService.listarTodas(sort);
        LocalDateTime desdeDate = (desde != null && !desde.isBlank())
                ? LocalDate.parse(desde).atStartOfDay() : null;
        LocalDateTime hastaDate = (hasta != null && !hasta.isBlank())
                ? LocalDate.parse(hasta).atTime(23, 59, 59) : null;

        boolean hayFiltros = (q != null && !q.isBlank()) || desdeDate != null || hastaDate != null;

        List<Conversacion> conversaciones;
        String tituloVista;

        if (hayFiltros) {
            conversaciones = conversacionService.buscarConFiltros(q, desdeDate, hastaDate, sort);
            tituloVista = "Resultados de búsqueda";
        } else {
            switch (vista) {
                case "asignadas":
                    conversaciones = conversacionService.listarAsignadasA(principal.getName(), sort);
                    tituloVista = "Asignadas a mí";
                    break;
                case "sin-asignar":
                    conversaciones = conversacionService.listarSinAsignar(sort);
                    tituloVista = "Sin asignar";
                    break;
                case "requiere-revision":
                    conversaciones = conversacionService.listarRequierenRevision(sort);
                    tituloVista = "Requiere revisión";
                    break;
                case "pendientes":
                    conversaciones = conversacionService.listarPorEstado("pending", sort);
                    tituloVista = "Pendientes";
                    break;
                case "en-proceso":
                    conversaciones = conversacionService.listarPorEstado("open", sort);
                    tituloVista = "En proceso";
                    break;
                case "resueltas":
                    conversaciones = conversacionService.listarPorEstado("resolved", sort);
                    tituloVista = "Resueltas";
                    break;
                case "resueltas-ia":
                    conversaciones = conversacionService.listarResueltasPorIA(sort);
                    tituloVista = "Resueltas por IA";
                    break;
                default:
                    conversaciones = todas;
                    tituloVista = "Vista general de quejas";
                    vista = "todas";
            }
        }

        long abiertas = todas.stream()
                .filter(c -> "open".equals(c.getCurrentConversationState())).count();
        long resueltas = todas.stream()
                .filter(c -> "resolved".equals(c.getCurrentConversationState())).count();
        long pendientes = todas.stream()
                .filter(c -> "pending".equals(c.getCurrentConversationState())).count();

        model.addAttribute("conversaciones", conversaciones);
        model.addAttribute("totalQuejas", todas.size());
        model.addAttribute("abiertas", abiertas);
        model.addAttribute("resueltas", resueltas);
        model.addAttribute("pendientes", pendientes);
        model.addAttribute("tituloVista", tituloVista);
        model.addAttribute("vista", vista);
        model.addAttribute("orden", orden);
        model.addAttribute("dir", dir);
        model.addAttribute("q", q);
        model.addAttribute("desde", desde);
        model.addAttribute("hasta", hasta);

        return "quejas/lista";
    }

    private boolean tienePermiso(Conversacion c, java.security.Principal principal) {
        if (c == null || principal == null) {
            return false;
        }
        Usuario usuario = usuarioRepository.findByNombre(principal.getName()).orElse(null);
        if (usuario == null) {
            return false;
        }
        boolean esSupervisorOAdmin = "SUPERVISOR".equals(usuario.getRol())
                || "ADMINISTRADOR".equals(usuario.getRol());
        if (esSupervisorOAdmin) {
            return true;
        }
        return principal.getName().equals(c.getTeammateCurrentlyAssigned());
    }

    @GetMapping("/{id}/json")
    @ResponseBody
    public Map<String, String> detalleJson(@PathVariable Long id) {
        Conversacion c = conversacionService.buscarPorId(id);
        Map<String, String> data = new HashMap<>();
        data.put("contactReason", c.getContactReason() != null ? c.getContactReason() : "-");
        data.put("estado", c.getCurrentConversationState() != null ? c.getCurrentConversationState() : "-");
        data.put("canal", c.getChannel() != null ? c.getChannel() : "-");
        data.put("orderId", c.getOrderId() != null ? String.valueOf(c.getOrderId()) : "-");
        data.put("agente", c.getTeammateCurrentlyAssigned() != null ? c.getTeammateCurrentlyAssigned() : "Sin asignar");
        data.put("fechaCreacion", c.getConversationCreatedAt() != null ?
                c.getConversationCreatedAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")) : "-");
        data.put("asunto", c.getAsunto() != null ? c.getAsunto() : "-");
        data.put("remitenteEmail", c.getRemitenteEmail() != null ? c.getRemitenteEmail() :
                (c.getOrden() != null && c.getOrden().getEmailCliente() != null ? c.getOrden().getEmailCliente() : "-"));

        Optional<Csat> csat = csatRepository.findByConversacionConversacionId(id);
        data.put("csatPuntuacion", csat.map(x -> String.valueOf(x.getPuntuacion())).orElse(null));

        return data;
    }

    @PostMapping("/{id}/reclasificar-reembolso")
    @ResponseBody
    public Map<String, String> reclasificarReembolso(@PathVariable Long id) {
        Map<String, String> response = new HashMap<>();
        Conversacion c = conversacionService.buscarPorId(id);
        if (c == null) {
            response.put("status", "error");
            return response;
        }
        c.setContactReason("refund_request");
        conversacionService.guardar(c);
        response.put("status", "ok");
        return response;
    }

    @PostMapping("/{id}/estado")
    @ResponseBody
    public Map<String, String> cambiarEstado(@PathVariable Long id, @RequestParam String estado,
                                             java.security.Principal principal) {
        Conversacion c = conversacionService.buscarPorId(id);
        Map<String, String> response = new HashMap<>();

        if (c != null && !tienePermiso(c, principal)) {
            response.put("status", "sin_permiso");
            response.put("mensaje", "No tienes permiso para modificar este caso.");
            return response;
        }

        if (c != null) {
            String estadoAnterior = c.getCurrentConversationState();
            c.setCurrentConversationState(estado);

            if ("resolved".equals(estado) || "open".equals(estado)) {
                boolean cambioDeAgente = !principal.getName().equals(c.getTeammateCurrentlyAssigned());
                c.setTeammateCurrentlyAssigned(principal.getName());
                if (cambioDeAgente) {
                    asignacionService.registrarAsignacion(c, principal.getName());
                }
            }

            conversacionService.guardar(c);

            auditoriaService.registrarCambio(c, principal.getName(),
                    "CAMBIO_ESTADO", estadoAnterior, estado);

            response.put("agente", c.getTeammateCurrentlyAssigned() != null ? c.getTeammateCurrentlyAssigned() : "Sin asignar");
        }
        response.put("status", "ok");
        response.put("estado", estado);
        return response;
    }

    @GetMapping("/agentes-activos")
    @ResponseBody
    public List<Map<String, String>> agentesActivos(java.security.Principal principal) {
        List<Usuario> agentes = usuarioRepository.findByRolAndActivoTrue("AGENTE");
        List<Map<String, String>> result = new ArrayList<>();
        for (Usuario u : agentes) {
            if (u.getNombre().equals(principal.getName())) continue;
            Map<String, String> m = new HashMap<>();
            m.put("nombre", u.getNombre());
            result.add(m);
        }
        return result;
    }

    @PostMapping("/{id}/transferir")
    @ResponseBody
    public Map<String, String> transferir(@PathVariable Long id,
                                          @RequestParam String agenteDestino,
                                          @RequestParam(required = false) String nota,
                                          java.security.Principal principal) {
        Map<String, String> response = new HashMap<>();
        Conversacion c = conversacionService.buscarPorId(id);
        if (c == null) {
            response.put("status", "error");
            return response;
        }

        if (!tienePermiso(c, principal)) {
            response.put("status", "sin_permiso");
            response.put("mensaje", "No tienes permiso para transferir este caso.");
            return response;
        }

        // ... resto del método igual, sin cambios ...

        String agenteAnterior = c.getTeammateCurrentlyAssigned();

        c.setCurrentConversationState("pending");
        c.setTeammateCurrentlyAssigned(agenteDestino);
        conversacionService.guardar(c);

        asignacionService.registrarAsignacion(c, agenteDestino);

        auditoriaService.registrarCambio(c, principal.getName(),
                "ASIGNACION", agenteAnterior, agenteDestino);

        if (nota != null && !nota.isBlank()) {
            Mensaje notaInterna = new Mensaje();
            notaInterna.setConversacion(c);
            notaInterna.setContenido(principal.getName() + " transfirió el caso a " + agenteDestino + ": " + nota);
            notaInterna.setRemitente("NOTA_INTERNA");
            notaInterna.setCanal("INTERNO");
            mensajeRepository.save(notaInterna);
        }

        response.put("status", "ok");
        response.put("agente", agenteDestino);
        return response;
    }

    @GetMapping("/{id}/mensajes")
    @ResponseBody
    public List<Map<String, String>> getMensajes(@PathVariable Long id) {
        List<Mensaje> mensajes = mensajeRepository.findByConversacionConversacionIdOrderByFechaEnvioAsc(id);
        List<Map<String, String>> result = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMM, HH:mm");
        for (Mensaje m : mensajes) {
            Map<String, String> msg = new HashMap<>();
            msg.put("contenido", m.getContenido());
            msg.put("remitente", m.getRemitente());
            msg.put("fechaEnvio", m.getFechaEnvio().format(formatter));
            result.add(msg);
        }
        return result;
    }

    @PostMapping("/{id}/responder")
    @ResponseBody
    public Map<String, String> responder(@PathVariable Long id, @RequestParam String contenido,
                                         java.security.Principal principal) {
        Conversacion conversacion = conversacionService.buscarPorId(id);
        Map<String, String> response = new HashMap<>();

        if (conversacion != null && !tienePermiso(conversacion, principal)) {
            response.put("status", "sin_permiso");
            response.put("mensaje", "No tienes permiso para responder este caso.");
            return response;
        }

        if (conversacion != null && !contenido.isBlank()) {
            Mensaje mensaje = new Mensaje();
            mensaje.setConversacion(conversacion);
            mensaje.setContenido(contenido);
            mensaje.setRemitente("AGENTE");
            mensaje.setCanal(conversacion.getChannel() != null ? conversacion.getChannel().toUpperCase() : "INTERNO");
            mensajeRepository.save(mensaje);

            conversacion.setTeammateCurrentlyAssigned(principal.getName());
            conversacionService.guardar(conversacion);

            if ("email".equalsIgnoreCase(conversacion.getChannel())) {
                if (conversacion.getOrden() != null && conversacion.getOrden().getEmailCliente() != null) {
                    String asunto = conversacion.getAsunto() != null
                            ? "Re: " + conversacion.getAsunto()
                            : "Re: tu consulta en CSManager";

                    Usuario agente = usuarioRepository.findByNombre(principal.getName()).orElse(null);
                    String cuerpoConFirma = contenido;
                    if (agente != null && agente.getFirma() != null && !agente.getFirma().isBlank()) {
                        cuerpoConFirma = contenido + "\n\n" + agente.getFirma();
                    }

                    emailService.enviarCorreo(
                            conversacion.getOrden().getEmailCliente(),
                            asunto,
                            cuerpoConFirma
                    );
                    response.put("status", "ok");
                } else {
                    response.put("status", "sin_destinatario");
                    response.put("mensaje", "Esta conversación no tiene un correo de cliente vinculado. Verifica manualmente antes de contactar.");
                }
            } else {
                response.put("status", "ok");
            }
        } else {
            response.put("status", "error");
        }
        return response;
    }
}