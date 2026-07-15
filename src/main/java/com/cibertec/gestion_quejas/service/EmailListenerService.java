package com.cibertec.gestion_quejas.service;

import com.cibertec.gestion_quejas.model.Conversacion;
import com.cibertec.gestion_quejas.model.Mensaje;
import com.cibertec.gestion_quejas.model.Orden;
import com.cibertec.gestion_quejas.repository.MensajeRepository;
import com.cibertec.gestion_quejas.repository.OrdenRepository;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.search.FlagTerm;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class EmailListenerService {

    @Value("${email.imap.host}")
    private String imapHost;

    @Value("${email.imap.port}")
    private String imapPort;

    @Value("${email.imap.username}")
    private String imapUsername;

    @Value("${email.imap.password}")
    private String imapPassword;

    @Value("${app.base-url}")
    private String baseUrl;

    @Value("${email.polling.enabled}")
    private boolean pollingEnabled;

    @Autowired
    private OrdenRepository ordenRepository;

    @Autowired
    private AsignacionService asignacionService;

    @Autowired
    private AuditoriaService auditoriaService;

    @Autowired
    private ConversacionService conversacionService;

    @Autowired
    private MensajeRepository mensajeRepository;

    @Autowired
    private IaService iaService;

    @Autowired
    private EmailService emailService;

    private static final Pattern PATRON_ORDEN =
            Pattern.compile("(?i)(?:orden|pedido|order)\\s*#?\\s*(?:ord-?)?(\\d+)");

    private static final List<String> ESTADOS_ACTIVOS = List.of("open", "pending");

    private static final List<String> DOMINIOS_SISTEMA_BLOQUEADOS = List.of(
            "brevo.com", "t.brevo.com", "sendinblue.com"
    );
    @Scheduled(fixedDelay = 60000)
    public void revisarBandejaEntrada() {
        if (!pollingEnabled) {
            return; // Polling desactivado manualmente (ahorro de recursos en la nube)
        }
        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");
        props.put("mail.imaps.host", imapHost);
        props.put("mail.imaps.port", imapPort);

        try {
            Session session = Session.getInstance(props);
            Store store = session.getStore("imaps");
            store.connect(imapHost, imapUsername, imapPassword);

            Folder inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_WRITE);

            Message[] mensajesNuevos = inbox.search(
                    new FlagTerm(new Flags(Flags.Flag.SEEN), false));

            for (Message msg : mensajesNuevos) {
                procesarCorreo(msg);
                msg.setFlag(Flags.Flag.SEEN, true);
            }

            inbox.close(false);
            store.close();
        } catch (Exception e) {
            System.err.println("Error revisando bandeja de entrada: " + e.getMessage());
        }
    }

    private void procesarCorreo(Message msg) {
        try {
            String messageId = (msg.getHeader("Message-ID") != null && msg.getHeader("Message-ID").length > 0)
                    ? msg.getHeader("Message-ID")[0] : null;

            // Dedupe a nivel de MENSAJE individual (ya no a nivel de conversación,
            // porque ahora una misma conversación puede recibir varios correos/turnos)
            if (messageId != null && mensajeRepository.existsByEmailMessageId(messageId)) {
                return;
            }

            String remitente = ((InternetAddress) msg.getFrom()[0]).getAddress();

            if (esRemitenteDeSistema(remitente)) {
                System.out.println("Correo de sistema ignorado (no se crea conversación): " + remitente);
                return;
            }


            String asunto = msg.getSubject() != null ? msg.getSubject() : "(sin asunto)";
            String cuerpo = extraerCuerpo(msg);
            String textoCompleto = asunto + " " + cuerpo;

            Orden ordenEncontrada = buscarOrden(textoCompleto, remitente);

            if (ordenEncontrada == null) {
                procesarSinOrden(asunto, cuerpo, remitente, messageId);
                return;
            }

            Optional<Conversacion> conversacionActiva = conversacionService
                    .buscarActivaPorOrdenYCanal(ordenEncontrada.getOrderId(), "email", ESTADOS_ACTIVOS);

            if (conversacionActiva.isPresent()) {
                continuarConversacionEmail(conversacionActiva.get(), ordenEncontrada, cuerpo, remitente, messageId);
            } else {
                iniciarConversacionEmail(ordenEncontrada, asunto, cuerpo, remitente, messageId, textoCompleto);
            }

        } catch (Exception e) {
            System.err.println("Error procesando correo individual: " + e.getMessage());
        }
    }

    private boolean esRemitenteDeSistema(String remitente) {
        if (remitente == null) return false;
        String dominio = remitente.substring(remitente.indexOf('@') + 1).toLowerCase();
        return DOMINIOS_SISTEMA_BLOQUEADOS.stream()
                .anyMatch(bloqueado -> dominio.equals(bloqueado) || dominio.endsWith("." + bloqueado));
    }

    private void procesarSinOrden(String asunto, String cuerpo, String remitente, String messageId) {
        Conversacion conversacion = new Conversacion();
        conversacion.setChannel("email");
        conversacion.setContactReason("consulta_general");
        conversacion.setRequiereRevisionManual(true);
        conversacion.setAsunto(asunto);
        conversacion.setRemitenteEmail(remitente);
        conversacionService.guardar(conversacion);

        Mensaje primerMensaje = new Mensaje();
        primerMensaje.setConversacion(conversacion);
        primerMensaje.setContenido(asunto + "\n\n" + cuerpo);
        primerMensaje.setRemitente("CLIENTE");
        primerMensaje.setCanal("EMAIL");
        primerMensaje.setEmailMessageId(messageId);
        mensajeRepository.save(primerMensaje);

        emailService.enviarCorreo(remitente,
                "Re: " + asunto,
                "Hola,\n\nPara poder registrar tu consulta correctamente, por favor " +
                        "respóndenos indicando tu número de orden.\n\nGracias.");
    }

    private void iniciarConversacionEmail(Orden orden, String asunto, String cuerpo,
                                          String remitente, String messageId, String textoCompleto) {
        Conversacion conversacion = new Conversacion();
        conversacion.setChannel("email");
        conversacion.setOrderId(orden.getOrderId());
        conversacion.setRequiereRevisionManual(false);
        conversacion.setAsunto(asunto);
        conversacion.setRemitenteEmail(remitente);

        boolean esReembolso = textoCompleto.toLowerCase().contains("reembolso");
        conversacion.setContactReason(esReembolso ? "refund_request" : "consulta_general");
        conversacion.setTeammateCurrentlyAssigned("CSMate");
        conversacionService.guardar(conversacion);

        Mensaje primerMensaje = new Mensaje();
        primerMensaje.setConversacion(conversacion);
        primerMensaje.setContenido(asunto + "\n\n" + cuerpo);
        primerMensaje.setRemitente("CLIENTE");
        primerMensaje.setCanal("EMAIL");
        primerMensaje.setEmailMessageId(messageId);
        mensajeRepository.save(primerMensaje);

        ResultadoCsmate resultado = iaService.evaluarConsulta(
                conversacion.getContactReason(),
                cuerpo,
                orden.getProducto() != null ? orden.getProducto().getProductName() : "-",
                orden.getDestinationCountry() != null ? orden.getDestinationCountry() : "-",
                orden.getOrderStatus() != null ? orden.getOrderStatus() : "-",
                orden.getProcessingSpeed() != null ? orden.getProcessingSpeed() : "-"
        );

        if (resultado.isPuedeResolver()) {
            Mensaje respuestaBot = new Mensaje();
            respuestaBot.setConversacion(conversacion);
            respuestaBot.setContenido(resultado.getRespuesta());
            respuestaBot.setRemitente("BOT");
            respuestaBot.setCanal("EMAIL");
            mensajeRepository.save(respuestaBot);

            conversacion.setCurrentConversationState("pending");
            emailService.enviarCorreo(remitente, "Re: " + asunto, resultado.getRespuesta());
        } else {
            conversacion.setBotTransferReason(resultado.getMotivoEscalamiento());
            conversacion.setTeammateCurrentlyAssigned(
                    conversacionService.seleccionarAgenteConMenosCarga());
            conversacion.setCurrentConversationState("open");
        }
        conversacionService.guardar(conversacion);

        if (conversacion.getTeammateCurrentlyAssigned() != null) {
            asignacionService.registrarAsignacion(conversacion, conversacion.getTeammateCurrentlyAssigned());
            auditoriaService.registrarCambio(conversacion, conversacion.getTeammateCurrentlyAssigned(),
                    "ASIGNACION", null, conversacion.getTeammateCurrentlyAssigned());
        }
    }

    private void continuarConversacionEmail(Conversacion conversacion, Orden orden,
                                            String cuerpo, String remitente, String messageId) {
        Mensaje msgCliente = new Mensaje();
        msgCliente.setConversacion(conversacion);
        msgCliente.setContenido(cuerpo);
        msgCliente.setRemitente("CLIENTE");
        msgCliente.setCanal("EMAIL");
        msgCliente.setEmailMessageId(messageId);
        mensajeRepository.save(msgCliente);

        List<Mensaje> historial = mensajeRepository
                .findByConversacionConversacionIdOrderByFechaEnvioAsc(conversacion.getConversacionId());
        String historialTexto = historial.stream()
                .map(m -> m.getRemitente() + ": " + m.getContenido())
                .collect(Collectors.joining("\n"));

        ResultadoTurno resultado = iaService.evaluarTurno(
                conversacion.getContactReason(),
                historialTexto,
                cuerpo,
                orden.getProducto() != null ? orden.getProducto().getProductName() : "-",
                orden.getDestinationCountry() != null ? orden.getDestinationCountry() : "-",
                orden.getOrderStatus() != null ? orden.getOrderStatus() : "-",
                orden.getProcessingSpeed() != null ? orden.getProcessingSpeed() : "-"
        );

        String contenidoBot = resultado.getRespuesta();

        if (resultado.getEstado() == ResultadoTurno.Estado.CERRAR_SATISFECHO) {
            conversacion.setCurrentConversationState("resolved");
            String token = conversacionService.generarTokenCsat(conversacion);
            String linkAbsoluto = baseUrl + "/csat/responder?token=" + token;
            contenidoBot = contenidoBot + "\n\nPor favor califica tu experiencia aquí: " + linkAbsoluto;
        } else if (resultado.getEstado() == ResultadoTurno.Estado.ESCALAR) {
            String agenteAnterior = conversacion.getTeammateCurrentlyAssigned();
            conversacion.setTeammateCurrentlyAssigned(
                    conversacionService.seleccionarAgenteConMenosCarga());
            conversacion.setBotTransferReason(resultado.getMotivoEscalamiento());
            conversacion.setCurrentConversationState("open");
            // Mensaje por defecto si la IA no generó respuesta (ej. error de conexión con Groq)
            if (contenidoBot == null || contenidoBot.isBlank()) {
                contenidoBot = "Gracias por tu mensaje. Uno de nuestros agentes revisará tu caso y te responderá a la brevedad.";
            }

            if (conversacion.getTeammateCurrentlyAssigned() != null) {
                asignacionService.registrarAsignacion(conversacion, conversacion.getTeammateCurrentlyAssigned());
                auditoriaService.registrarCambio(conversacion, conversacion.getTeammateCurrentlyAssigned(),
                        "ASIGNACION", agenteAnterior, conversacion.getTeammateCurrentlyAssigned());
            }
        }

        Mensaje msgBot = new Mensaje();
        msgBot.setConversacion(conversacion);
        msgBot.setContenido(contenidoBot);
        msgBot.setRemitente("BOT");
        msgBot.setCanal("EMAIL");
        mensajeRepository.save(msgBot);

        conversacionService.guardar(conversacion);

        String asuntoRespuesta = conversacion.getAsunto() != null
                ? "Re: " + conversacion.getAsunto()
                : "Re: tu consulta en CSManager";
        emailService.enviarCorreo(remitente, asuntoRespuesta, contenidoBot);
    }

    private Orden buscarOrden(String textoCompleto, String remitente) {
        Matcher matcher = PATRON_ORDEN.matcher(textoCompleto);
        if (matcher.find()) {
            String soloNumero = matcher.group(1);
            String codigoOrden = "ORD-" + String.format("%05d", Integer.parseInt(soloNumero));
            Orden orden = ordenRepository.findById(codigoOrden).orElse(null);
            if (orden != null && orden.getEmailCliente() != null
                    && orden.getEmailCliente().equalsIgnoreCase(remitente)) {
                return orden;
            }
            return null;
        }

        List<Orden> ordenesPorEmail = ordenRepository.findByEmailClienteIgnoreCase(remitente);
        if (ordenesPorEmail.size() == 1) {
            return ordenesPorEmail.get(0);
        }
        return null;
    }

    private String extraerCuerpo(Message msg) throws Exception {
        Object contenido = msg.getContent();
        if (contenido instanceof String texto) {
            return texto;
        }
        if (contenido instanceof Multipart multipart) {
            StringBuilder resultado = new StringBuilder();
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart parte = multipart.getBodyPart(i);
                if (parte.isMimeType("text/plain")) {
                    resultado.append(parte.getContent().toString());
                }
            }
            return resultado.toString();
        }
        return "";
    }
}