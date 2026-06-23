package com.cibertec.gestion_quejas.model;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "reembolso")
public class Reembolso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reembolso_id")
    private Long reembolsoId;

    @ManyToOne
    @JoinColumn(name = "conversacion_id", nullable = false)
    private Conversacion conversacion;

    @Column(name = "flagged_for_refund_request")
    private Boolean flaggedForRefundRequest;

    @Column(name = "bot_refund_status")
    private String botRefundStatus;
    // pendiente_agente → pendiente_supervisor → cerrado

    @Column(name = "refund_result")
    private String refundResult;
    // aprobado | denegado | rechazado_supervisor

    @Column(name = "refund_granted")
    private Boolean refundGranted;

    @Column(name = "refund_amount")
    private Double refundAmount;

    @Column(name = "refund_percent")
    private Double refundPercent;

    @Column(name = "refund_reason_category")
    private String refundReasonCategory;
    // amenaza_legal | redes_sociales | error_empresa

    @Column(name = "agent_notes", length = 1000)
    private String agentNotes;

    @Column(name = "supervisor_notes", length = 1000)
    private String supervisorNotes;
}