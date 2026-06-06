package com.cibertec.gestion_quejas.model;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "orden")
public class Orden {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "producto_id")
    private Long productoId;

    @Column(name = "order_status")
    private String orderStatus;

    @Column(name = "date_entered_order_status")
    private String dateEnteredOrderStatus;

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
}