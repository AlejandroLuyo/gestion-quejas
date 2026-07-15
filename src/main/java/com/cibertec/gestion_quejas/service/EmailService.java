package com.cibertec.gestion_quejas.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class EmailService {

    @Value("${brevo.api.key}")
    private String brevoApiKey;

    @Value("${email.imap.username}")
    private String remitenteEmail;

    private static final String BREVO_API_URL = "https://api.brevo.com/v3/smtp/email";

    private final RestTemplate restTemplate = new RestTemplate();

    public void enviarCorreo(String destinatario, String asunto, String cuerpo) {
        try {
            Map<String, Object> cuerpoPeticion = Map.of(
                    "sender", Map.of("name", "CSManager", "email", remitenteEmail),
                    "to", java.util.List.of(Map.of("email", destinatario)),
                    "subject", asunto,
                    "textContent", cuerpo
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("api-key", brevoApiKey);
            headers.set("accept", "application/json");

            HttpEntity<Map<String, Object>> peticion = new HttpEntity<>(cuerpoPeticion, headers);
            restTemplate.postForEntity(BREVO_API_URL, peticion, Map.class);

        } catch (Exception e) {
            System.err.println("Error al enviar correo a " + destinatario + " via Brevo: "
                    + e.getClass().getName() + " - " + e.getMessage());
            e.printStackTrace();
        }
    }
}