package com.cibertec.gestion_quejas.model;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "producto")
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "producto_id")
    private Long productoId;

    @Column(name = "product_name")
    private String productName;

    @Column(name = "product_type_group")
    private String productTypeGroup;

    @Column(name = "full_refund_eligible")
    private Boolean fullRefundEligible;

    @Column(name = "activo")
    private Boolean activo = true;
}