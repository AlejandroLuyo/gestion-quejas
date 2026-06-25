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
import com.cibertec.gestion_quejas.repository.MensajeRepository;
import org.springframework.web.client.RestTemplate;

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

    private final RestTemplate restTemplate = crearRestTemplateConTimeout();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static RestTemplate crearRestTemplateConTimeout() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(30_000);
        return new RestTemplate(factory);
    }

    public ResultadoCsmate evaluarConsulta(String contactReason, String descripcionCliente,
                                           String producto, String paisDestino,
                                           String estadoPedido, String velocidadProcesamiento) {
        if ("refund_request".equals(contactReason)) {
            return new ResultadoCsmate(false, null, "refund_request");
        }

        String prompt = construirPromptInicial(contactReason, descripcionCliente, producto,
                paisDestino, estadoPedido, velocidadProcesamiento);

        try {
            String jsonRespuesta = llamarGroq(prompt, "json_object");
            Map<String, Object> datos = objectMapper.readValue(jsonRespuesta, Map.class);
            boolean puedeResolver = (Boolean) datos.get("puede_resolver");
            String respuesta = (String) datos.get("respuesta");
            return new ResultadoCsmate(puedeResolver, respuesta,
                    puedeResolver ? null : "ia_no_pudo_resolver");
        } catch (Exception e) {
            return new ResultadoCsmate(false, null, "error_ia");
        }
    }

    public ResultadoTurno evaluarTurno(String contactReason, String historialConversacion,
                                       String nuevoMensajeCliente, String producto,
                                       String paisDestino, String estadoPedido,
                                       String velocidadProcesamiento) {
        String prompt = construirPromptTurno(contactReason, historialConversacion,
                nuevoMensajeCliente, producto, paisDestino, estadoPedido, velocidadProcesamiento);

        try {
            String jsonRespuesta = llamarGroq(prompt, "json_object");
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
            return new ResultadoTurno(ResultadoTurno.Estado.ESCALAR, null, "error_ia");
        }
    }

    @Async
    public void evaluarConsultaAsincrono(Long conversacionId, String contactReason,
                                         String descripcionCliente, String producto,
                                         String paisDestino, String estadoPedido,
                                         String velocidadProcesamiento) {
        try {
            ResultadoCsmate resultado = evaluarConsulta(contactReason, descripcionCliente,
                    producto, paisDestino, estadoPedido, velocidadProcesamiento);

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

    private String construirPromptInicial(String contactReason, String descripcionCliente,
                                          String producto, String paisDestino,
                                          String estadoPedido, String velocidadProcesamiento) {
        return """
                Eres CSMate, el asistente de atención al cliente de una empresa de trámites de visa.
                Un cliente envió la siguiente consulta:

                Motivo de contacto: %s
                Descripción del cliente: "%s"

                Datos de su pedido:
                - Producto: %s
                - País de destino: %s
                - Estado del pedido: %s
                - Velocidad de procesamiento: %s

                Decide si puedes responder directamente al cliente con información clara y útil,
                o si el caso requiere que lo revise un agente humano.

                Reglas:
                - Si tienes información suficiente, da una respuesta clara y útil.
                - Si la consulta requiere una acción que no puedes ejecutar, indica que no puedes resolverlo.
                - Nunca prometas reembolsos, descuentos, ni cambios que no puedas garantizar.
                - Responde siempre en español, en tono cordial y profesional.

                Responde ÚNICAMENTE con un JSON válido con esta estructura exacta, sin texto adicional:
                {"puede_resolver": true o false, "respuesta": "texto de respuesta al cliente"}
                """.formatted(contactReason, descripcionCliente, producto,
                paisDestino, estadoPedido, velocidadProcesamiento);
    }

    private String construirPromptTurno(String contactReason, String historialConversacion,
                                        String nuevoMensajeCliente, String producto,
                                        String paisDestino, String estadoPedido,
                                        String velocidadProcesamiento) {
        return """
                Eres CSMate, el asistente de atención al cliente de una empresa de trámites de visa.
                Estás en medio de una conversación con un cliente sobre el siguiente caso:

                Motivo de contacto: %s
                Datos de su pedido:
                - Producto: %s
                - País de destino: %s
                - Estado del pedido: %s
                - Velocidad de procesamiento: %s

                Historial de la conversación hasta ahora:
                %s

                El cliente acaba de escribir: "%s"

                REGLA CRÍTICA: Si el mensaje del cliente expresa que ya no tiene más preguntas,
                que está satisfecho, que se despide, o cualquier variante de "gracias, hasta luego",
                "no tengo más preguntas", "eso es todo", "muchas gracias", "listo", "ok gracias",
                SIEMPRE responde con estado "cerrar_satisfecho". Esta regla tiene prioridad absoluta.

                Para los demás casos, decide cuál aplica:
                - "continuar": el cliente sigue con dudas que puedes responder.
                - "cerrar_satisfecho": el cliente confirma que ya no tiene más preguntas.
                - "escalar": el cliente pregunta algo que no puedes resolver.

                Nunca prometas reembolsos ni cambios. Responde en español, tono cordial y profesional.

                Responde ÚNICAMENTE con un JSON válido con esta estructura exacta, sin texto adicional:
                {"estado": "continuar" o "cerrar_satisfecho" o "escalar", "respuesta": "texto para el cliente"}
                """.formatted(contactReason, producto, paisDestino, estadoPedido,
                velocidadProcesamiento, historialConversacion, nuevoMensajeCliente);
    }

    private String llamarGroq(String prompt, String responseFormat) {
        String url = apiUrl;
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
        ResponseEntity<Map> respuesta = restTemplate.postForEntity(url, peticion, Map.class);

        List<Map> choices = (List<Map>) respuesta.getBody().get("choices");
        Map message = (Map) choices.get(0).get("message");
        return (String) message.get("content");
    }
}