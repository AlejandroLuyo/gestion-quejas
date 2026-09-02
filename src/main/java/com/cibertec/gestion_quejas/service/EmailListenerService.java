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

    // Reconoce "orden 14", "pedido #14", "order 14"
    private static final Pattern PATRON_ORDEN_CON_PALABRA =
            Pattern.compile("(?i)(?:orden|pedido|order)\\s*#?\\s*(?:ord-?)?(\\d+)");

    // Reconoce el código de orden solo, en su formato real: "ORD-00014" o "ORD00014"
    private static final Pattern PATRON_CODIGO_ORDEN =
            Pattern.compile("(?i)\\bORD-?\\s?(\\d{4,})\\b");

    // Corta el texto citado que Gmail agrega al responder: "El lun, 3 ago... escribió:"
    private static final Pattern PATRON_CITA_GMAIL =
            Pattern.compile("(?m)^El .+ escribi[oó]:\\s*$");

    private static final List<String> ESTADOS_ACTIVOS = List.of("open", "pending");

    private static final List<String> DOMINIOS_SISTEMA_BLOQUEADOS = List.of(
            "brevo.com", "t.brevo.com", "sendinblue.com",
            "accounts.google.com", "google.com", "microsoft.com", "outlook.com"
    );

    private static final List<String> PATRONES_REMITENTE_SISTEMA = List.of(
            "no-reply", "noreply", "no.reply", "mailer-daemon", "postmaster", "notification", "notifications"
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

            if (messageId != null && mensajeRepository.existsByEmailMessageId(messageId)) {
                return;
            }

            String remitente = ((InternetAddress) msg.getFrom()[0]).getAddress();

            if (esRemitenteDeSistema(remitente)) {
                System.out.println("Correo de sistema ignorado (no se crea conversación): " + remitente);
                return;
            }

            String asunto = msg.getSubject() != null ? msg.getSubject() : "(sin asunto)";
            String cuerpoCrudo = extraerCuerpo(msg);
            String cuerpo = limpiarTextoCitado(cuerpoCrudo);
            String textoCompleto = asunto + " " + cuerpo;

            // 1) Threading real: ¿este correo responde directamente a un mensaje que ya tenemos?
            Optional<Conversacion> hiloActivo = buscarHiloPorHeaders(msg)
                    .filter(c -> ESTADOS_ACTIVOS.contains(c.getCurrentConversationState()));

            // 2) Si no hay match por headers (correo compuesto desde cero, no una respuesta real),
            // solo se usa el asunto como respaldo cuando el propio asunto ya trae "Re:"
            // (evita que asuntos genéricos reutilizados en correos nuevos se mezclen con hilos viejos).
            if (hiloActivo.isEmpty() && esRespuestaSegunAsunto(asunto)) {
                hiloActivo = conversacionService
                        .buscarActivasPorRemitenteYCanal(remitente, "email", ESTADOS_ACTIVOS)
                        .stream()
                        .filter(c -> mismoHilo(c.getAsunto(), asunto))
                        .findFirst();
            }

            if (hiloActivo.isPresent()) {
                Conversacion conversacion = hiloActivo.get();

                if (conversacion.getOrderId() == null) {
                    Orden ordenEncontrada = buscarOrden(textoCompleto, remitente);
                    if (ordenEncontrada != null) {
                        completarConversacionSinOrden(conversacion, ordenEncontrada, cuerpo, remitente, messageId, textoCompleto);
                    } else {
                        continuarSinOrden(conversacion, cuerpo, remitente, messageId);
                    }
                } else {
                    Orden orden = ordenRepository.findById(conversacion.getOrderId()).orElse(null);
                    if (orden != null) {
                        continuarConversacionEmail(conversacion, orden, cuerpo, remitente, messageId);
                    }
                }
                return;
            }

            // No hay ningún hilo real: es un caso nuevo.
            Orden ordenEncontrada = buscarOrden(textoCompleto, remitente);

            if (ordenEncontrada == null) {
                procesarSinOrden(asunto, cuerpo, remitente, messageId);
            } else {
                iniciarConversacionEmail(ordenEncontrada, asunto, cuerpo, remitente, messageId, textoCompleto);
            }

        } catch (Exception e) {
            System.err.println("Error procesando correo individual: " + e.getMessage());
        }
    }

    private boolean mismoHilo(String asuntoGuardado, String asuntoNuevo) {
        if (asuntoGuardado == null || asuntoNuevo == null) return false;
        String limpio1 = normalizarAsunto(asuntoGuardado);
        String limpio2 = normalizarAsunto(asuntoNuevo);
        return limpio1.equalsIgnoreCase(limpio2);
    }

    private Optional<Conversacion> buscarHiloPorHeaders(Message msg) throws MessagingException {
        String referencia = null;

        String[] inReplyTo = msg.getHeader("In-Reply-To");
        if (inReplyTo != null && inReplyTo.length > 0) {
            referencia = inReplyTo[0].trim();
        } else {
            String[] references = msg.getHeader("References");
            if (references != null && references.length > 0) {
                String[] partes = references[0].trim().split("\\s+");
                if (partes.length > 0) {
                    referencia = partes[partes.length - 1];
                }
            }
        }

        if (referencia == null) {
            return Optional.empty();
        }

        return mensajeRepository.findByEmailMessageId(referencia)
                .map(Mensaje::getConversacion);
    }

    private boolean esRespuestaSegunAsunto(String asunto) {
        return asunto != null && asunto.toLowerCase().trim().startsWith("re:");
    }

    private String normalizarAsunto(String asunto) {
        return asunto.replaceAll("(?i)^(re:\\s*)+", "").trim();
    }

    private boolean esRemitenteDeSistema(String remitente) {
        if (remitente == null) return false;
        String remitenteLower = remitente.toLowerCase();
        String dominio = remitenteLower.substring(remitenteLower.indexOf('@') + 1);

        boolean dominioBloqueado = DOMINIOS_SISTEMA_BLOQUEADOS.stream()
                .anyMatch(bloqueado -> dominio.equals(bloqueado) || dominio.endsWith("." + bloqueado));

        boolean patronBloqueado = PATRONES_REMITENTE_SISTEMA.stream()
                .anyMatch(remitenteLower::contains);

        return dominioBloqueado || patronBloqueado;
    }

    private String limpiarTextoCitado(String cuerpo) {
        if (cuerpo == null) return "";

        int idx = indexOfCaseInsensitive(cuerpo, "escribió:");
        if (idx == -1) {
            idx = indexOfCaseInsensitive(cuerpo, "escribio:"); // por si la tilde se pierde en la codificación
        }
        if (idx != -1) {
            int inicioParrafo = cuerpo.lastIndexOf("\n\n", idx);
            cuerpo = inicioParrafo != -1 ? cuerpo.substring(0, inicioParrafo) : cuerpo.substring(0, idx);
        }

        String[] lineas = cuerpo.split("\\r?\\n");
        StringBuilder resultado = new StringBuilder();
        for (String linea : lineas) {
            if (linea.trim().startsWith(">")) continue;
            resultado.append(linea).append("\n");
        }

        return resultado.toString().trim();
    }

    private int indexOfCaseInsensitive(String texto, String buscado) {
        return texto.toLowerCase().indexOf(buscado.toLowerCase());
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

        String textoRespuesta = "Hola,\n\nPara poder registrar tu consulta correctamente, por favor " +
                "respóndenos indicando tu número de orden.\n\nGracias.";

        Mensaje respuestaBot = new Mensaje();
        respuestaBot.setConversacion(conversacion);
        respuestaBot.setContenido(textoRespuesta);
        respuestaBot.setRemitente("BOT");
        respuestaBot.setCanal("EMAIL");
        mensajeRepository.save(respuestaBot);

        emailService.enviarCorreo(remitente, "Re: " + asunto, textoRespuesta);
    }

    // El remitente ya tenía una conversación "sin orden" abierta y volvió a escribir
    // sin que se pudiera identificar el número de orden en este nuevo mensaje tampoco.
    private void continuarSinOrden(Conversacion conversacion, String cuerpo, String remitente, String messageId) {
        Mensaje msgCliente = new Mensaje();
        msgCliente.setConversacion(conversacion);
        msgCliente.setContenido(cuerpo);
        msgCliente.setRemitente("CLIENTE");
        msgCliente.setCanal("EMAIL");
        msgCliente.setEmailMessageId(messageId);
        mensajeRepository.save(msgCliente);

        String asuntoRespuesta = conversacion.getAsunto() != null
                ? "Re: " + conversacion.getAsunto()
                : "Re: tu consulta en CSManager";

        String textoRespuesta = "Hola,\n\nAún no pudimos identificar tu número de orden en tu mensaje. " +
                "Por favor respóndenos indicando tu número de orden en el formato ORD-00000.\n\nGracias.";

        Mensaje respuestaBot = new Mensaje();
        respuestaBot.setConversacion(conversacion);
        respuestaBot.setContenido(textoRespuesta);
        respuestaBot.setRemitente("BOT");
        respuestaBot.setCanal("EMAIL");
        mensajeRepository.save(respuestaBot);

        emailService.enviarCorreo(remitente, asuntoRespuesta, textoRespuesta);
    }

    // El remitente ya tenía una conversación "sin orden" abierta y en este mensaje
    // finalmente se pudo identificar la orden: se completa esa misma conversación.
    private void completarConversacionSinOrden(Conversacion conversacion, Orden orden, String cuerpo,
                                               String remitente, String messageId, String textoCompleto) {
        conversacion.setOrderId(orden.getOrderId());
        conversacion.setRequiereRevisionManual(false);

        boolean esReembolso = textoCompleto.toLowerCase().contains("reembolso");
        conversacion.setContactReason(esReembolso ? "refund_request" : "consulta_general");
        conversacion.setTeammateCurrentlyAssigned("CSMate");
        conversacionService.guardar(conversacion);

        Mensaje msgCliente = new Mensaje();
        msgCliente.setConversacion(conversacion);
        msgCliente.setContenido(cuerpo);
        msgCliente.setRemitente("CLIENTE");
        msgCliente.setCanal("EMAIL");
        msgCliente.setEmailMessageId(messageId);
        mensajeRepository.save(msgCliente);

        // Junta TODOS los mensajes del cliente hasta ahora (la consulta original +
        // esta respuesta con el número de orden), para que la IA tenga el contexto completo.
        List<Mensaje> historial = mensajeRepository
                .findByConversacionConversacionIdOrderByFechaEnvioAsc(conversacion.getConversacionId());
        String descripcionCompleta = historial.stream()
                .filter(m -> "CLIENTE".equals(m.getRemitente()))
                .map(Mensaje::getContenido)
                .collect(Collectors.joining("\n\n"));

        ResultadoCsmate resultado = iaService.evaluarConsulta(
                conversacion.getContactReason(),
                descripcionCompleta,
                orden.getProducto() != null ? orden.getProducto().getProductName() : "-",
                orden.getDestinationCountry() != null ? orden.getDestinationCountry() : "-",
                orden.getOrderStatus() != null ? orden.getOrderStatus() : "-",
                orden.getProcessingSpeed() != null ? orden.getProcessingSpeed() : "-",
                orden.getOrderId()
        );

        String asuntoRespuesta = conversacion.getAsunto() != null
                ? "Re: " + conversacion.getAsunto()
                : "Re: tu consulta en CSManager";

        if (resultado.isPuedeResolver()) {
            Mensaje respuestaBot = new Mensaje();
            respuestaBot.setConversacion(conversacion);
            respuestaBot.setContenido(resultado.getRespuesta());
            respuestaBot.setRemitente("BOT");
            respuestaBot.setCanal("EMAIL");
            mensajeRepository.save(respuestaBot);

            conversacion.setCurrentConversationState("pending");
            emailService.enviarCorreo(remitente, asuntoRespuesta, resultado.getRespuesta());
        } else {
            if (resultado.getRespuesta() != null && !resultado.getRespuesta().isBlank()) {
                Mensaje respuestaBot = new Mensaje();
                respuestaBot.setConversacion(conversacion);
                respuestaBot.setContenido(resultado.getRespuesta());
                respuestaBot.setRemitente("BOT");
                respuestaBot.setCanal("EMAIL");
                mensajeRepository.save(respuestaBot);
            }

            conversacion.setBotTransferReason(resultado.getMotivoEscalamiento());
            conversacion.setTeammateCurrentlyAssigned(
                    conversacionService.seleccionarAgenteConMenosCarga());
            conversacion.setCurrentConversationState("open");

            String textoCorreo = resultado.getRespuesta() != null && !resultado.getRespuesta().isBlank()
                    ? resultado.getRespuesta() + "\n\nUno de nuestros agentes revisará tu caso y te responderá a la brevedad."
                    : "Hola,\n\nGracias por tu mensaje. Uno de nuestros agentes revisará tu caso " +
                      "y te responderá a la brevedad.\n\nGracias.";
            emailService.enviarCorreo(remitente, asuntoRespuesta, textoCorreo);
        }
        conversacionService.guardar(conversacion);

        if (conversacion.getTeammateCurrentlyAssigned() != null
                && !"CSMate".equals(conversacion.getTeammateCurrentlyAssigned())) {
            asignacionService.registrarAsignacion(conversacion, conversacion.getTeammateCurrentlyAssigned());
            auditoriaService.registrarCambio(conversacion, conversacion.getTeammateCurrentlyAssigned(),
                    "ASIGNACION", null, conversacion.getTeammateCurrentlyAssigned());
        }
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
                orden.getProcessingSpeed() != null ? orden.getProcessingSpeed() : "-",
                orden.getOrderId()
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
            if (resultado.getRespuesta() != null && !resultado.getRespuesta().isBlank()) {
                Mensaje respuestaBot = new Mensaje();
                respuestaBot.setConversacion(conversacion);
                respuestaBot.setContenido(resultado.getRespuesta());
                respuestaBot.setRemitente("BOT");
                respuestaBot.setCanal("EMAIL");
                mensajeRepository.save(respuestaBot);
            }

            conversacion.setBotTransferReason(resultado.getMotivoEscalamiento());
            conversacion.setTeammateCurrentlyAssigned(
                    conversacionService.seleccionarAgenteConMenosCarga());
            conversacion.setCurrentConversationState("open");

            String textoCorreo = resultado.getRespuesta() != null && !resultado.getRespuesta().isBlank()
                    ? resultado.getRespuesta() + "\n\nUno de nuestros agentes revisará tu caso y te responderá a la brevedad."
                    : "Hola,\n\nGracias por tu mensaje. Uno de nuestros agentes revisará tu caso " +
                      "y te responderá a la brevedad.\n\nGracias.";
            emailService.enviarCorreo(remitente, "Re: " + asunto, textoCorreo);
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

        // Si ya está asignado a un agente humano, solo se guarda el mensaje
        // (el agente lo verá desde su panel). La IA no vuelve a intervenir.
        boolean yaEscaladoAHumano = conversacion.getTeammateCurrentlyAssigned() != null
                && !"CSMate".equals(conversacion.getTeammateCurrentlyAssigned());

        if (yaEscaladoAHumano) {
            return;
        }

        List<Mensaje> historial = mensajeRepository
                .findByConversacionConversacionIdOrderByFechaEnvioAsc(conversacion.getConversacionId());

        final int VENTANA_HISTORIAL = 6;
        List<Mensaje> historialVentana = historial.size() > VENTANA_HISTORIAL
                ? historial.subList(historial.size() - VENTANA_HISTORIAL, historial.size())
                : historial;

        String historialTexto = historialVentana.stream()
                .map(m -> m.getRemitente() + ": " + m.getContenido())
                .collect(Collectors.joining("\n"));

        ResultadoTurno resultado = iaService.evaluarTurno(
                conversacion.getContactReason(),
                historialTexto,
                cuerpo,
                orden.getProducto() != null ? orden.getProducto().getProductName() : "-",
                orden.getDestinationCountry() != null ? orden.getDestinationCountry() : "-",
                orden.getOrderStatus() != null ? orden.getOrderStatus() : "-",
                orden.getProcessingSpeed() != null ? orden.getProcessingSpeed() : "-",
                orden.getOrderId()
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
        String soloNumero = null;

        Matcher matcherPalabra = PATRON_ORDEN_CON_PALABRA.matcher(textoCompleto);
        if (matcherPalabra.find()) {
            soloNumero = matcherPalabra.group(1);
        } else {
            Matcher matcherCodigo = PATRON_CODIGO_ORDEN.matcher(textoCompleto);
            if (matcherCodigo.find()) {
                soloNumero = matcherCodigo.group(1);
            }
        }

        if (soloNumero != null) {
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