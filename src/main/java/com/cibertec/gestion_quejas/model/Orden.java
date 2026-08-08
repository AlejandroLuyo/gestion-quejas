package com.cibertec.gestion_quejas.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Data
@Entity
@Table(name = "orden")
public class Orden {

    @Id
    @Column(name = "order_id", length = 20)
    private String orderId;

    @ManyToOne
    @JoinColumn(name = "producto_id")
    private Producto producto;

    @Column(name = "order_status")
    private String orderStatus;

    @Column(name = "nombre_cliente")
    private String nombreCliente;

    @Column(name = "email_cliente")
    private String emailCliente;

    @Column(name = "processing_speed")
    private String processingSpeed;

    @Column(name = "destination_country")
    private String destinationCountry;

    @Column(name = "user_nationality")
    private String userNationality;

    @Column(name = "continent")
    private String continent;

    @Column(name = "country")
    private String country;

    @Column(name = "precio")
    private Double precio;

    @Column(name = "date_entered_order_status")
    private LocalDateTime dateEnteredOrderStatus;

    // --- SLA (no persistido) ---
    public LocalDateTime getSlaVencimiento() {
        if (dateEnteredOrderStatus == null || processingSpeed == null) return null;
        return switch (processingSpeed) {
            case "standard" -> dateEnteredOrderStatus.plusHours(24);
            case "rush" -> dateEnteredOrderStatus.plusHours(4);
            case "super_rush" -> dateEnteredOrderStatus.plusMinutes(15);
            default -> null;
        };
    }

    public boolean isSlaVencido() {
        LocalDateTime limite = getSlaVencimiento();
        return limite != null && LocalDateTime.now().isAfter(limite);
    }
}