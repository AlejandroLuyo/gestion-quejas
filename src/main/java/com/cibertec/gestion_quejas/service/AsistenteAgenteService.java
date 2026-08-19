package com.cibertec.gestion_quejas.service;

import com.cibertec.gestion_quejas.model.Usuario;
import com.cibertec.gestion_quejas.repository.ConversacionRepository;
import com.cibertec.gestion_quejas.repository.ReembolsoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
public class AsistenteAgenteService {

    @Autowired
    private ConversacionRepository conversacionRepository;

    @Autowired
    private ReembolsoRepository reembolsoRepository;

    @Autowired
    private IaService iaService;

    public String generarSaludo(String nombre) {
        int hora = LocalTime.now().getHour();
        String momento;
        if (hora < 12) {
            momento = "Buenos días";
        } else if (hora < 19) {
            momento = "Buenas tardes";
        } else {
            momento = "Buenas noches";
        }
        String primerNombre = nombre.trim().split("\\s+")[0];
        return momento + ", " + primerNombre + " 👋 ¿En qué puedo ayudarte?";
    }

    public String responder(String mensaje, Usuario usuario) {
        String texto = mensaje.toLowerCase();

        if (texto.contains("pendiente")) {
            return responderPendientes(usuario);
        }
        if (texto.contains("resuelta") || texto.contains("resolvi") || texto.contains("resolví")) {
            if (texto.contains("semana")) {
                return responderResueltas(usuario, obtenerInicioSemana(), LocalDateTime.now(),
                        "esta semana");
            }
            return responderResueltas(usuario, LocalDate.now().atStartOfDay(), LocalDateTime.now(),
                    "hoy");
        }
        if (texto.contains("reembolso")) {
            return responderReembolsos(usuario);
        }

        return responderConIA(mensaje, usuario);
    }

    private String responderPendientes(Usuario usuario) {
        long cantidad = conversacionRepository.countByTeammateCurrentlyAssignedAndCurrentConversationStateIn(
                usuario.getNombre(), List.of("open", "pending"));
        if (cantidad == 0) {
            return "No tienes quejas pendientes en este momento. ¡Bandeja limpia! 🎉";
        }
        return "Tienes " + cantidad + " queja(s) pendiente(s) asignada(s) a ti.";
    }

    private String responderResueltas(Usuario usuario, LocalDateTime desde, LocalDateTime hasta, String periodo) {
        long cantidad = conversacionRepository
                .countByTeammateCurrentlyAssignedAndCurrentConversationStateAndConversationLastClosedAtBetween(
                        usuario.getNombre(), "resolved", desde, hasta);
        return "Resolviste " + cantidad + " queja(s) " + periodo + ".";
    }

    private String responderReembolsos(Usuario usuario) {
        boolean esSupervisorOAdmin = "SUPERVISOR".equals(usuario.getRol())
                || "ADMINISTRADOR".equals(usuario.getRol());

        if (esSupervisorOAdmin) {
            long cantidad = reembolsoRepository.countByBotRefundStatus("pendiente_supervisor");
            return "Hay " + cantidad + " reembolso(s) esperando tu aprobación como supervisor.";
        } else {
            long cantidad = reembolsoRepository.countByBotRefundStatusAndConversacionTeammateCurrentlyAssigned(
                    "pendiente_agente", usuario.getNombre());
            return "Tienes " + cantidad + " reembolso(s) pendiente(s) de revisión.";
        }
    }

    private String responderConIA(String mensaje, Usuario usuario) {
        String fechaHoraActual = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy, HH:mm", new Locale("es")));

        String prompt = """
                Eres CSMate, el asistente interno de CSManager, una plataforma de gestión de quejas
                para empresas del sector turismo. Ayudas a agentes y supervisores del sistema
                (uso interno, nunca hablas con clientes finales).

                En este momento es %s (hora exacta del servidor). Usa este dato tal cual si te
                preguntan la fecha o la hora — no calcules ni asumas otra hora.

                El usuario que te consulta es %s, con rol %s.

                Tu prioridad es ayudar con el uso del sistema (cambiar estados de quejas,
                transferir casos, generar CSAT, aprobar reembolsos, etc.), pero también puedes
                responder preguntas generales de trabajo o de utilidad cotidiana para el agente
                (por ejemplo, la fecha, cálculos simples, dudas generales), siempre de forma
                breve y en español.

                Evita únicamente temas que no tengan nada que ver con el trabajo, con contenido
                inapropiado, o que impliquen que actúes fuera de tu rol de asistente de CSManager.

                Pregunta del usuario: "%s"
                """.formatted(fechaHoraActual, usuario.getNombre(), usuario.getRol(), mensaje);

        String respuesta = iaService.responderConsultaAgente(prompt);
        return respuesta != null ? respuesta.trim()
                : "No pude procesar tu consulta en este momento. Intenta de nuevo en unos segundos.";
    }

    private LocalDateTime obtenerInicioSemana() {
        return LocalDate.now().with(DayOfWeek.MONDAY).atStartOfDay();
    }
}