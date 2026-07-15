package com.cibertec.gestion_quejas.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "csat")
public class Csat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "csat_id")
    private Long csatId;

    @OneToOne
    @JoinColumn(name = "conversacion_id", nullable = false)
    private Conversacion conversacion;

    @Column(name = "puntuacion", nullable = false)
    private Integer puntuacion;

    @Column(name = "comentario")
    private String comentario;

    @Column(name = "fecha_respuesta", nullable = false)
    private LocalDateTime fechaRespuesta;

    @PrePersist
    public void prePersist() {
        this.fechaRespuesta = LocalDateTime.now();
    }
}