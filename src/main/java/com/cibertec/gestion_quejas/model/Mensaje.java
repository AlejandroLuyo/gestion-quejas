package com.cibertec.gestion_quejas.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "mensajes")
public class Mensaje {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "mensaje_id")
    private Long mensajeId;

    @ManyToOne
    @JoinColumn(name = "conversacion_id", nullable = false)
    private Conversacion conversacion;

    @Column(name = "contenido", columnDefinition = "TEXT")
    private String contenido;

    @Column(name = "remitente", nullable = false)
    private String remitente; // AGENTE, CLIENTE, BOT

    @Column(name = "canal")
    private String canal; // WHATSAPP, EMAIL, TICKET, INTERNO

    @Column(name = "email_message_id", unique = true)
    private String emailMessageId;

    @Column(name = "fecha_envio", nullable = false)
    private LocalDateTime fechaEnvio;

    @PrePersist
    public void prePersist() {
        this.fechaEnvio = LocalDateTime.now();
    }
}