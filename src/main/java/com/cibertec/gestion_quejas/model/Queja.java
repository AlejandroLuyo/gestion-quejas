package com.cibertec.gestion_quejas.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "quejas")
public class Queja {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombreCliente;

    @Column(nullable = false)
    private String descripcion;

    @Column(nullable = false)
    private String contactReason;

    @Column(nullable = false)
    private String estado; // PENDIENTE, EN_PROCESO, RESUELTO

    @Column(nullable = false)
    private String origen; // TICKET, EMAIL, WHATSAPP

    @Column(nullable = false)
    private LocalDateTime fechaRegistro;

    @PrePersist
    public void prePersist() {
        this.fechaRegistro = LocalDateTime.now();
        this.estado = "PENDIENTE";
        if (this.origen == null) this.origen = "TICKET";
    }
}