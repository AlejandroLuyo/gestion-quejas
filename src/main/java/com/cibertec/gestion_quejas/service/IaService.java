package com.cibertec.gestion_quejas.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import com.cibertec.gestion_quejas.model.Conversacion;
import com.cibertec.gestion_quejas.model.Mensaje;
import com.cibertec.gestion_quejas.model.Orden;
import com.cibertec.gestion_quejas.repository.MensajeRepository;
import com.cibertec.gestion_quejas.repository.OrdenRepository;
import org.springframework.web.client.RestTemplate;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class IaService {

    @Value("${ia.api.key}")
    private String apiKey;

    @Value("${ia.api.model}")
    private String modelo;

    @Value("${ia.api.url}")
    private String apiUrl;

    @Autowired
    private ConversacionService conversacionService;

    @Autowired
    private MensajeRepository mensajeRepository;

    @Autowired
    private OrdenRepository ordenRepository;

    private final RestTemplate restTemplate = crearRestTemplateConTimeout();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final int MAX_INTENTOS_HERRAMIENTAS = 4;
    private static final String HERRAMIENTA_RESPUESTA_INICIAL = "entregar_respuesta_inicial";
    private static final String HERRAMIENTA_RESPUESTA_TURNO = "entregar_respuesta_turno";

    private static RestTemplate crearRestTemplateConTimeout() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(30_000);
        return new RestTemplate(factory);
    }

    // ==================== MÉTODOS PÚBLICOS ====================

    public ResultadoCsmate evaluarConsulta(String contactReason, String descripcionCliente,
                                           String producto, String paisDestino,
                                           String estadoPedido, String velocidadProcesamiento,
                                           String orderId) {
        if ("refund_request".equals(contactReason)) {
            return new ResultadoCsmate(false, null, "refund_request");
        }

        List<Map<String, Object>> mensajes = construirMensajesInicial(contactReason, descripcionCliente,
                producto, paisDestino, estadoPedido, velocidadProcesamiento);
        List<Map<String, Object>> herramientas = List.of(
                definicionHerramientaConsultarEstadoOrden(),
                definicionHerramientaEntregarRespuestaInicial()
        );

        try {
            String jsonRespuesta = llamarGroqConHerramientas(mensajes, herramientas, orderId,
                    HERRAMIENTA_RESPUESTA_INICIAL);
            Map<String, Object> datos = objectMapper.readValue(jsonRespuesta, Map.class);
            boolean puedeResolver = (Boolean) datos.get("puede_resolver");
            String respuesta = (String) datos.get("respuesta");
            return new ResultadoCsmate(puedeResolver, respuesta,
                    puedeResolver ? null : "ia_no_pudo_resolver");
        } catch (Exception e) {
            System.err.println("ERROR AL LLAMAR A GROQ: " + e.getClass().getName() + " - " + e.getMessage());
            e.printStackTrace();
            return new ResultadoCsmate(false, null, "error_ia");
        }
    }

    public ResultadoTurno evaluarTurno(String contactReason, String historialConversacion,
                                       String nuevoMensajeCliente, String producto,
                                       String paisDestino, String estadoPedido,
                                       String velocidadProcesamiento, String orderId) {
        List<Map<String, Object>> mensajes = construirMensajesTurno(contactReason, historialConversacion,
                nuevoMensajeCliente, producto, paisDestino, estadoPedido, velocidadProcesamiento);
        List<Map<String, Object>> herramientas = List.of(
                definicionHerramientaConsultarEstadoOrden(),
                definicionHerramientaEntregarRespuestaTurno()
        );

        try {
            String jsonRespuesta = llamarGroqConHerramientas(mensajes, herramientas, orderId,
                    HERRAMIENTA_RESPUESTA_TURNO);
            Map<String, Object> datos = objectMapper.readValue(jsonRespuesta, Map.class);
            String estadoTexto = (String) datos.get("estado");
            String respuesta = (String) datos.get("respuesta");

            ResultadoTurno.Estado estado = switch (estadoTexto) {
                case "cerrar_satisfecho" -> ResultadoTurno.Estado.CERRAR_SATISFECHO;
                case "escalar" -> ResultadoTurno.Estado.ESCALAR;
                default -> ResultadoTurno.Estado.CONTINUAR;
            };

            return new ResultadoTurno(estado, respuesta,
                    estado == ResultadoTurno.Estado.ESCALAR ? "ia_no_pudo_resolver" : null);
        } catch (Exception e) {
            System.err.println("ERROR AL LLAMAR A GROQ: " + e.getClass().getName() + " - " + e.getMessage());
            e.printStackTrace();
            return new ResultadoTurno(ResultadoTurno.Estado.ESCALAR, null, "error_ia");
        }
    }

    public String responderConsultaAgente(String prompt) {
        try {
            return llamarGroq(prompt, "text");
        } catch (Exception e) {
            System.err.println("ERROR AL LLAMAR A GROQ: " + e.getClass().getName() + " - " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    @Async
    public void evaluarConsultaAsincrono(Long conversacionId, String contactReason,
                                         String descripcionCliente, String producto,
                                         String paisDestino, String estadoPedido,
                                         String velocidadProcesamiento, String orderId) {
        try {
            ResultadoCsmate resultado = evaluarConsulta(contactReason, descripcionCliente,
                    producto, paisDestino, estadoPedido, velocidadProcesamiento, orderId);

            Conversacion conversacion = conversacionService.buscarPorId(conversacionId);
            if (conversacion == null) return;

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
                conversacion.setTeammateCurrentlyAssigned(
                        conversacionService.seleccionarAgenteConMenosCarga());
                conversacion.setCurrentConversationState("open");
            }
            conversacionService.guardar(conversacion);
        } catch (Exception e) {
            Conversacion conversacion = conversacionService.buscarPorId(conversacionId);
            if (conversacion != null) {
                conversacion.setBotTransferReason("error_ia");
                conversacion.setTeammateCurrentlyAssigned(
                        conversacionService.seleccionarAgenteConMenosCarga());
                conversacion.setCurrentConversationState("open");
                conversacionService.guardar(conversacion);
            }
        }
    }

    // ==================== HERRAMIENTAS (FUNCTION CALLING) ====================

    private Map<String, Object> definicionHerramientaConsultarEstadoOrden() {
        return Map.of(
                "type", "function",
                "function", Map.of(
                        "name", "consultar_estado_orden",
                        "description", "Consulta el estado actual, el plazo de entrega, y el precio pagado de la orden del cliente en el sistema. Úsala siempre que necesites saber el estado, el plazo, si está vencida, o cuánto pagó el cliente.",
                        "parameters", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "order_id", Map.of(
                                                "type", "string",
                                                "description", "Identificador de la orden a consultar"
                                        )
                                ),
                                "required", List.of("order_id")
                        )
                )
        );
    }

    private Map<String, Object> definicionHerramientaEntregarRespuestaInicial() {
        return Map.of(
                "type", "function",
                "function", Map.of(
                        "name", HERRAMIENTA_RESPUESTA_INICIAL,
                        "description", "Entrega la respuesta final para el cliente sobre su consulta inicial. Debes llamar esta herramienta como último paso, una vez que ya tengas toda la información necesaria (hayas usado o no otras herramientas antes).",
                        "parameters", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "puede_resolver", Map.of(
                                                "type", "boolean",
                                                "description", "true si puedes resolver la consulta tú mismo con una respuesta clara y útil; false si el caso requiere que lo revise un agente humano"
                                        ),
                                        "respuesta", Map.of(
                                                "type", "string",
                                                "description", "El texto de respuesta para el cliente, en español, tono cordial y profesional"
                                        )
                                ),
                                "required", List.of("puede_resolver", "respuesta")
                        )
                )
        );
    }

    private Map<String, Object> definicionHerramientaEntregarRespuestaTurno() {
        return Map.of(
                "type", "function",
                "function", Map.of(
                        "name", HERRAMIENTA_RESPUESTA_TURNO,
                        "description", "Entrega la respuesta final para el cliente en este turno de la conversación. Debes llamar esta herramienta como último paso, una vez que ya tengas toda la información necesaria (hayas usado o no otras herramientas antes).",
                        "parameters", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "estado", Map.of(
                                                "type", "string",
                                                "enum", List.of("continuar", "cerrar_satisfecho", "escalar"),
                                                "description", "El estado de la conversación tras este turno"
                                        ),
                                        "respuesta", Map.of(
                                                "type", "string",
                                                "description", "El texto de respuesta para el cliente, en español, tono cordial y profesional"
                                        )
                                ),
                                "required", List.of("estado", "respuesta")
                        )
                )
        );
    }

    private String traducirVelocidad(String codigo) {
        return switch (codigo) {
            case "standard" -> "Standard (24 horas)";
            case "rush" -> "Rush (4 horas)";
            case "super_rush" -> "Super Rush (15 minutos)";
            default -> codigo;
        };
    }

    private String traducirEstado(String codigo) {
        return switch (codigo) {
            case "in_progress" -> "en proceso";
            case "approved" -> "aprobada";
            case "rejected" -> "rechazada";
            default -> codigo;
        };
    }

    /**
     * Ejecuta la consulta real a la BD. Ignora cualquier order_id sugerido por
     * la IA y siempre usa el orderId real de la conversación, por seguridad.
     */
    private String ejecutarConsultarEstadoOrden(String orderIdReal) {
        if (orderIdReal == null) {
            return "{\"error\": \"No hay una orden asociada a esta conversación.\"}";
        }

        Orden orden = ordenRepository.findById(orderIdReal).orElse(null);
        if (orden == null) {
            return "{\"error\": \"No se encontró la orden en el sistema.\"}";
        }

        DateTimeFormatter formato = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        boolean vencido = orden.isSlaVencido();
        java.time.LocalDateTime slaVencimiento = orden.getSlaVencimiento();
        String plazoTexto = slaVencimiento != null ? slaVencimiento.format(formato) : "no disponible";
        String tiempoTexto = calcularTiempoTexto(slaVencimiento, vencido);

        Map<String, Object> resultado = new HashMap<>();
        resultado.put("order_id", orden.getOrderId());
        resultado.put("estado_actual", traducirEstado(orden.getOrderStatus()));
        resultado.put("velocidad_procesamiento", traducirVelocidad(orden.getProcessingSpeed()));
        resultado.put("plazo_maximo_entrega", plazoTexto);
        resultado.put("esta_vencido", vencido);
        resultado.put("tiempo_restante_o_demora", tiempoTexto);
        resultado.put("precio_pagado", "S/ " + orden.getPrecio());

        try {
            return objectMapper.writeValueAsString(resultado);
        } catch (Exception e) {
            return "{\"error\": \"No se pudo procesar la información de la orden.\"}";
        }
    }

    /**
     * Calcula en Java (no en el modelo) cuánto tiempo falta o cuánto lleva vencida
     * la orden, para evitar que la IA intente restar fechas por su cuenta.
     */
    private String calcularTiempoTexto(java.time.LocalDateTime slaVencimiento, boolean vencido) {
        if (slaVencimiento == null) {
            return "no disponible";
        }

        java.time.LocalDateTime ahora = java.time.LocalDateTime.now();
        java.time.Duration duracion = vencido
                ? java.time.Duration.between(slaVencimiento, ahora)
                : java.time.Duration.between(ahora, slaVencimiento);

        long horas = duracion.toHours();
        long minutos = duracion.toMinutesPart();

        String cantidad = horas > 0
                ? horas + " hora" + (horas != 1 ? "s" : "") + " y " + minutos + " minuto" + (minutos != 1 ? "s" : "")
                : minutos + " minuto" + (minutos != 1 ? "s" : "");

        return vencido ? "vencida hace " + cantidad : cantidad + " restantes";
    }

    // ==================== CONSTRUCCIÓN DE PROMPTS ====================

    private List<Map<String, Object>> construirMensajesInicial(String contactReason, String descripcionCliente,
                                                               String producto, String paisDestino,
                                                               String estadoPedido, String velocidadProcesamiento) {
        String prompt = """
                Eres CSMate, el asistente de atención al cliente de una empresa de trámites de visa.
                Un cliente envió la siguiente consulta:

                Motivo de contacto: %s
                Descripción del cliente: "%s"

                Datos generales de su pedido (referencia rápida, pueden no estar actualizados):
                - Producto: %s
                - País de destino: %s
                - Estado del pedido: %s
                - Velocidad de procesamiento: %s

                Tienes disponible la herramienta "consultar_estado_orden" para obtener el estado real
                y actualizado de la orden, incluyendo el plazo de entrega correcto y si está vencida.
                SIEMPRE que el cliente pregunte por el estado, el plazo, la demora, cuándo estará
                lista su orden, o cuánto pagó/costó su orden, usa esa herramienta en vez de calcular
                o inventar el dato tú mismo.
                Cuando necesites decir cuánto tiempo falta o cuánto lleva de demora la orden,
                usa siempre el campo "tiempo_restante_o_demora" que te da la herramienta —
                nunca calcules la diferencia de tiempo tú mismo.
                No es necesario pedirle al cliente su número de orden para usar esta herramienta:
                el sistema ya identifica automáticamente la orden correcta de esta conversación.

                REGLA IMPORTANTE: si la herramienta indica que "esta_vencido" es true, debes decírselo
                explícitamente al cliente (reconociendo la demora, sin mostrar la fecha límite como si
                fuera un plazo futuro o vigente), y preguntarle explícitamente si prefiere que su caso
                se escale a un agente humano para revisión prioritaria, o si está de acuerdo en esperar
                y dar por resuelta la consulta por ahora. No prometas acciones que no puedas garantizar
                (como "ya se está revisando su caso"); deja la decisión en manos del cliente.

                Decide si puedes responder directamente al cliente con información clara y útil,
                o si el caso requiere que lo revise un agente humano.

                Reglas generales:
                - Si tienes información suficiente (con o sin la herramienta), da una respuesta clara y útil.
                - Si la consulta requiere una acción que no puedes ejecutar, indica que no puedes resolverlo.
                - Nunca prometas reembolsos, descuentos, ni cambios que no puedas garantizar.
                - Responde siempre en español, en tono cordial y profesional.
                
                REGLA GENERAL DE HONESTIDAD (muy importante): NO tienes información real sobre el
                  proceso operativo interno del negocio después de la compra (por ejemplo: revisión de
                  documentos por un equipo, envío a una embajada o consulado, preparación de un
                  "expediente", aprobación consular, recojo o envío físico del documento), ni sobre
                  requisitos específicos de documentación (qué documentos exactos se piden, formatos,
                  tamaños de foto, vigencias mínimas, etc.), ni sobre políticas o costos adicionales que
                  no estén en los datos reales que tienes disponibles. NUNCA inventes ninguno de esos
                  detalles, aunque te parezcan plausibles o típicos para este tipo de trámite. Si el
                  cliente pregunta por el proceso general, los pasos siguientes, requisitos específicos
                  de documentos, u otro detalle operativo que no puedas confirmar con datos reales o con
                  una herramienta, responde honestamente que no cuentas con ese detalle exacto, y
                  ofrécele escalar su caso a un agente que pueda explicárselo, o pregúntale si tiene
                  alguna otra duda puntual que sí puedas ayudarle a resolver (por ejemplo, sobre su orden).
                
                LIMITACIÓN CONOCIDA: la gestión de documentos (subir la foto del pasaporte, descargar
                el entregable ya procesado, etc.) se realiza desde la página principal del sistema, no
                desde este chat. Este chat no tiene forma de verificar ni resolver errores técnicos de
                esas acciones. Si el cliente reporta un error al subir o descargar un documento, NO
                inventes causas técnicas específicas (formato, tamaño máximo, navegador, etc.), ya que
                no tienes esa información real. En su lugar, ofrécele dos alternativas y deja que el
                cliente elija: (1) intentar la acción nuevamente más tarde desde la página principal,
                por si fue un error temporal, o (2) escalar su caso a un agente para que le brinde otra
                solución. No afirmes que el sistema carece de esta funcionalidad ni que se está
                trabajando para incorporarla, ya que la funcionalidad sí existe.

                Cuando ya tengas todo lo necesario, llama a la herramienta "%s" con tu respuesta final.
                No respondas con texto plano ni con JSON escrito directamente: usa siempre esa herramienta
                para entregar tu respuesta.
                """.formatted(contactReason, descripcionCliente, producto, paisDestino,
                estadoPedido, velocidadProcesamiento, HERRAMIENTA_RESPUESTA_INICIAL);

        List<Map<String, Object>> mensajes = new ArrayList<>();
        Map<String, Object> mensajeUsuario = new HashMap<>();
        mensajeUsuario.put("role", "user");
        mensajeUsuario.put("content", prompt);
        mensajes.add(mensajeUsuario);
        return mensajes;
    }

    private List<Map<String, Object>> construirMensajesTurno(String contactReason, String historialConversacion,
                                                             String nuevoMensajeCliente, String producto,
                                                             String paisDestino, String estadoPedido,
                                                             String velocidadProcesamiento) {
        String prompt = """
                Eres CSMate, el asistente de atención al cliente de una empresa de trámites de visa.
                Estás en medio de una conversación con un cliente sobre el siguiente caso:

                Motivo de contacto: %s
                Datos generales de su pedido (referencia rápida, pueden no estar actualizados):
                - Producto: %s
                - País de destino: %s
                - Estado del pedido: %s
                - Velocidad de procesamiento: %s

                Historial de la conversación hasta ahora:
                %s

                El cliente acaba de escribir: "%s"

                Tienes disponible la herramienta "consultar_estado_orden" para obtener el estado real
                y actualizado de la orden, incluyendo el plazo de entrega correcto y si está vencida.
                
                SIEMPRE que el cliente pregunte (en este turno o en cualquier turno anterior sin resolver)
                por el estado, el plazo, la demora, cuándo estará lista su orden, o cuánto pagó/costó su
                orden, usa esa herramienta en vez de calcular o inventar el dato o una explicación
                genérica tú mismo.   
                  
                Cuando necesites decir cuánto tiempo falta o cuánto lleva de demora la orden,
                usa siempre el campo "tiempo_restante_o_demora" que te da la herramienta —
                nunca calcules la diferencia de tiempo tú mismo.
                No es necesario pedirle al cliente su número de orden para usar esta herramienta:
                el sistema ya identifica automáticamente la orden correcta de esta conversación.
                

                REGLA IMPORTANTE: si la herramienta indica que "esta_vencido" es true, debes decírselo
                explícitamente al cliente (reconociendo la demora, sin mostrar la fecha límite como si
                fuera un plazo futuro o vigente) y no inventes motivos genéricos de la demora
                (verificaciones, alta demanda, etc.) si no los conoces realmente. Si es la primera vez
                que se lo mencionas en esta conversación, pregúntale explícitamente si prefiere que su
                caso se escale a un agente humano para revisión prioritaria, o si está de acuerdo en
                esperar y dar por resuelta la consulta por ahora. No prometas acciones que no puedas
                garantizar (como "ya se está revisando su caso"); deja la decisión en manos del cliente.

                REGLA CRÍTICA 1 (prioridad máxima): Si el cliente pide explícitamente hablar con un agente,
                ser transferido a una persona, o usa frases como "quiero un agente", "que me asigne un agente",
                "necesito hablar con alguien", "transfiéranme", "quiero hablar con una persona",
                SIEMPRE responde con estado "escalar", incluso si el mensaje también contiene palabras de agradecimiento o despedida.

                REGLA CRÍTICA 2: Si el mensaje del cliente expresa que ya no tiene más preguntas,
                que está satisfecho, que se despide, o cualquier variante de "gracias, hasta luego",
                "no tengo más preguntas", "eso es todo", "muchas gracias", "listo", "ok gracias",
                Y no aplica la Regla Crítica 1, responde con estado "cerrar_satisfecho".

                REGLA CRÍTICA 3: Si en el historial de la conversación el asistente ya le preguntó al
                cliente si prefiere escalar su caso a un agente o dejarlo así, interpreta la respuesta
                del cliente a esa pregunta: si acepta o pide que se escale (ej. "sí, escálalo", "por
                favor", "sí quiero hablar con alguien"), responde con estado "escalar"; si rechaza o se
                muestra conforme con esperar (ej. "no, está bien", "no hace falta", "entendido, gracias"),
                responde con estado "cerrar_satisfecho".
                
                REGLA GENERAL DE HONESTIDAD (muy importante): NO tienes información real sobre el
                 proceso operativo interno del negocio después de la compra (por ejemplo: revisión de
                 documentos por un equipo, envío a una embajada o consulado, preparación de un
                 "expediente", aprobación consular, recojo o envío físico del documento), ni sobre
                 requisitos específicos de documentación (qué documentos exactos se piden, formatos,
                 tamaños de foto, vigencias mínimas, etc.), ni sobre políticas o costos adicionales que
                 no estén en los datos reales que tienes disponibles. NUNCA inventes ninguno de esos
                 detalles, aunque te parezcan plausibles o típicos para este tipo de trámite. Si el
                 cliente pregunta por el proceso general, los pasos siguientes, requisitos específicos
                 de documentos, u otro detalle operativo que no puedas confirmar con datos reales o con
                 una herramienta, responde honestamente que no cuentas con ese detalle exacto, y
                 ofrécele escalar su caso a un agente que pueda explicárselo, o pregúntale si tiene
                 alguna otra duda puntual que sí puedas ayudarle a resolver (por ejemplo, sobre su orden).
                
                LIMITACIÓN CONOCIDA: la gestión de documentos (subir la foto del pasaporte, descargar
                el entregable ya procesado, etc.) se realiza desde la página principal del sistema, no
                desde este chat. Este chat no tiene forma de verificar ni resolver errores técnicos de
                esas acciones. Si el cliente reporta un error al subir o descargar un documento, NO
                inventes causas técnicas específicas (formato, tamaño máximo, navegador, etc.), ya que
                no tienes esa información real. En su lugar, ofrécele dos alternativas y deja que el
                cliente elija: (1) intentar la acción nuevamente más tarde desde la página principal,
                por si fue un error temporal, o (2) escalar su caso a un agente para que le brinde otra
                solución. No afirmes que el sistema carece de esta funcionalidad ni que se está
                trabajando para incorporarla, ya que la funcionalidad sí existe.

                Para los demás casos, decide cuál aplica:
                - "continuar": el cliente sigue con dudas que puedes responder.
                - "cerrar_satisfecho": el cliente confirma que ya no tiene más preguntas.
                - "escalar": el cliente pregunta algo que no puedes resolver, o pide explícitamente un agente.

                Nunca prometas reembolsos ni cambios. Responde en español, tono cordial y profesional.

                Cuando ya tengas todo lo necesario, llama a la herramienta "%s" con tu respuesta final.
                No respondas con texto plano ni con JSON escrito directamente: usa siempre esa herramienta
                para entregar tu respuesta.
                """.formatted(contactReason, producto, paisDestino, estadoPedido,
                velocidadProcesamiento, historialConversacion, nuevoMensajeCliente,
                HERRAMIENTA_RESPUESTA_TURNO);

        List<Map<String, Object>> mensajes = new ArrayList<>();
        Map<String, Object> mensajeUsuario = new HashMap<>();
        mensajeUsuario.put("role", "user");
        mensajeUsuario.put("content", prompt);
        mensajes.add(mensajeUsuario);
        return mensajes;
    }

    // ==================== LLAMADAS A GROQ ====================

    /**
     * Ciclo de llamadas a Groq con soporte de herramientas (function calling).
     * Ejecuta las funciones intermedias (ej. consultar_estado_orden) y le devuelve
     * el resultado al modelo, hasta que llame a la herramienta de respuesta final
     * (nombreHerramientaFinal), cuyos argumentos se devuelven como el JSON resultado.
     */
    private String llamarGroqConHerramientas(List<Map<String, Object>> mensajes,
                                             List<Map<String, Object>> herramientas,
                                             String orderIdReal,
                                             String nombreHerramientaFinal) {
        for (int intento = 0; intento < MAX_INTENTOS_HERRAMIENTAS; intento++) {
            Map<String, Object> cuerpo = new HashMap<>();
            cuerpo.put("model", modelo);
            cuerpo.put("messages", mensajes);
            cuerpo.put("tools", herramientas);
            cuerpo.put("temperature", 0.3);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            HttpEntity<Map<String, Object>> peticion = new HttpEntity<>(cuerpo, headers);
            ResponseEntity<Map> respuesta = restTemplate.postForEntity(apiUrl, peticion, Map.class);

            List<Map> choices = (List<Map>) respuesta.getBody().get("choices");
            Map message = (Map) choices.get(0).get("message");
            List<Map> toolCalls = (List<Map>) message.get("tool_calls");

            if (toolCalls == null || toolCalls.isEmpty()) {
                // Respaldo por si el modelo respondiera en texto plano sin usar herramientas.
                return (String) message.get("content");
            }

            mensajes.add(message);

            for (Map toolCall : toolCalls) {
                String toolCallId = (String) toolCall.get("id");
                Map function = (Map) toolCall.get("function");
                String nombreFuncion = function != null ? (String) function.get("name") : null;

                if (nombreHerramientaFinal.equals(nombreFuncion)) {
                    return (String) function.get("arguments");
                }

                String resultadoFuncion = "consultar_estado_orden".equals(nombreFuncion)
                        ? ejecutarConsultarEstadoOrden(orderIdReal)
                        : "{\"error\": \"Función no reconocida.\"}";

                Map<String, Object> mensajeHerramienta = new HashMap<>();
                mensajeHerramienta.put("role", "tool");
                mensajeHerramienta.put("tool_call_id", toolCallId);
                mensajeHerramienta.put("content", resultadoFuncion);
                mensajes.add(mensajeHerramienta);
            }
        }

        throw new IllegalStateException(
                "CSMate no devolvió una respuesta final tras " + MAX_INTENTOS_HERRAMIENTAS + " intentos con herramientas.");
    }

    private String llamarGroq(String prompt, String responseFormat) {
        Map<String, Object> cuerpo = Map.of(
                "model", modelo,
                "messages", List.of(Map.of("role", "user", "content", prompt)),
                "response_format", Map.of("type", responseFormat),
                "temperature", 0.3
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        HttpEntity<Map<String, Object>> peticion = new HttpEntity<>(cuerpo, headers);
        ResponseEntity<Map> respuesta = restTemplate.postForEntity(apiUrl, peticion, Map.class);

        List<Map> choices = (List<Map>) respuesta.getBody().get("choices");
        Map message = (Map) choices.get(0).get("message");
        return (String) message.get("content");
    }
}