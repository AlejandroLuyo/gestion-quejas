package com.cibertec.gestion_quejas.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
public class GeminiService {

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.model}")
    private String modelo;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ResultadoCsmate evaluarConsulta(String contactReason, String descripcionCliente,
                                           String producto, String paisDestino,
                                           String estadoPedido, String velocidadProcesamiento) {

        // Regla dura: reembolsos siempre escalan a un agente, sin pasar por el LLM
        if ("refund_request".equals(contactReason)) {
            return new ResultadoCsmate(false, null, "refund_request");
        }

        String prompt = construirPromptInicial(contactReason, descripcionCliente, producto,
                paisDestino, estadoPedido, velocidadProcesamiento);

        Map<String, Object> esquema = Map.of(
                "type", "OBJECT",
                "properties", Map.of(
                        "puede_resolver", Map.of("type", "BOOLEAN"),
                        "respuesta", Map.of("type", "STRING")
                ),
                "required", List.of("puede_resolver", "respuesta")
        );

        try {
            String jsonRespuesta = llamarGemini(prompt, esquema);
            Map<String, Object> datos = objectMapper.readValue(jsonRespuesta, Map.class);
            boolean puedeResolver = (Boolean) datos.get("puede_resolver");
            String respuesta = (String) datos.get("respuesta");
            return new ResultadoCsmate(puedeResolver, respuesta, puedeResolver ? null : "ia_no_pudo_resolver");
        } catch (Exception e) {
            return new ResultadoCsmate(false, null, "error_ia");
        }
    }

    public ResultadoTurno evaluarTurno(String contactReason, String historialConversacion, String nuevoMensajeCliente,
                                       String producto, String paisDestino,
                                       String estadoPedido, String velocidadProcesamiento) {

        String prompt = construirPromptTurno(contactReason, historialConversacion, nuevoMensajeCliente,
                producto, paisDestino, estadoPedido, velocidadProcesamiento);

        Map<String, Object> esquema = Map.of(
                "type", "OBJECT",
                "properties", Map.of(
                        "estado", Map.of(
                                "type", "STRING",
                                "enum", List.of("continuar", "cerrar_satisfecho", "escalar")
                        ),
                        "respuesta", Map.of("type", "STRING")
                ),
                "required", List.of("estado", "respuesta")
        );

        try {
            String jsonRespuesta = llamarGemini(prompt, esquema);
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

    private String construirPromptInicial(String contactReason, String descripcionCliente, String producto,
                                          String paisDestino, String estadoPedido, String velocidadProcesamiento) {
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
                - Si la consulta requiere una acción que no puedes ejecutar (modificar datos, decisiones
                  subjetivas, o no tienes información suficiente), indica que no puedes resolverlo.
                - Nunca prometas reembolsos, descuentos, ni cambios que no puedas garantizar.
                - Responde siempre en español, en tono cordial y profesional.
                """.formatted(contactReason, descripcionCliente, producto,
                paisDestino, estadoPedido, velocidadProcesamiento);
    }

    private String construirPromptTurno(String contactReason, String historialConversacion, String nuevoMensajeCliente,
                                        String producto, String paisDestino,
                                        String estadoPedido, String velocidadProcesamiento) {
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
            SIEMPRE responde con estado "cerrar_satisfecho". Esta regla tiene prioridad absoluta
            sobre cualquier otra consideración.

            Para los demás casos, decide cuál de estas situaciones aplica:
            - "continuar": el cliente sigue con dudas relacionadas que puedes responder.
            - "cerrar_satisfecho": el cliente confirma que ya no tiene más preguntas o quedó conforme.
            - "escalar": el cliente pregunta algo que no puedes resolver tú.

            Si decides "continuar", responde con un mensaje útil.
            Si decides "cerrar_satisfecho", responde con un mensaje breve de despedida.
            Si decides "escalar", explica brevemente que lo derivarás con un agente.

            Reglas adicionales:
            - Nunca prometas reembolsos, descuentos, ni cambios que no puedas garantizar.
            - Responde siempre en español, en tono cordial y profesional.
            """.formatted(contactReason, producto, paisDestino, estadoPedido, velocidadProcesamiento,
                historialConversacion, nuevoMensajeCliente);
    }

    private String llamarGemini(String prompt, Map<String, Object> esquema) {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                + modelo + ":generateContent";

        Map<String, Object> cuerpo = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of(
                        "responseMimeType", "application/json",
                        "responseSchema", esquema
                )
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-goog-api-key", apiKey);

        HttpEntity<Map<String, Object>> peticion = new HttpEntity<>(cuerpo, headers);
        ResponseEntity<Map> respuesta = restTemplate.postForEntity(url, peticion, Map.class);

        List<Map> candidatos = (List<Map>) respuesta.getBody().get("candidates");
        Map contenido = (Map) candidatos.get(0).get("content");
        List<Map> partes = (List<Map>) contenido.get("parts");
        return (String) partes.get(0).get("text");
    }
}