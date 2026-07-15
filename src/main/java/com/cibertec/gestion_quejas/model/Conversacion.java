package com.cibertec.gestion_quejas.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "conversacion")
public class Conversacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "conversacion_id")
    private Long conversacionId;

    @Column(name = "order_id", length = 20)
    private String orderId;

    @ManyToOne
    @JoinColumn(name = "order_id", insertable = false, updatable = false)
    private Orden orden;

    @Column(name = "channel")
    private String channel;

    @Column(name = "contact_reason")
    private String contactReason;

    @Column(name = "current_conversation_state")
    private String currentConversationState;

    @Column(name = "language")
    private String language;

    @Column(name = "has_attachment")
    private Boolean hasAttachment;

    @Column(name = "trigger_event")
    private String triggerEvent;

    @Column(name = "conversation_created_at")
    private LocalDateTime conversationCreatedAt;

    @Column(name = "conversation_last_closed_at")
    private LocalDateTime conversationLastClosedAt;

    @Column(name = "first_response_time_seconds")
    private Integer firstResponseTimeSeconds;

    @Column(name = "handling_time_seconds")
    private Integer handlingTimeSeconds;

    @Column(name = "times_in_min")
    private Integer timesInMin;

    @Column(name = "teammate_currently_assigned")
    private String teammateCurrentlyAssigned;

    @Column(name = "teammate_currently_assigned_id")
    private String teammateCurrentlyAssignedId;

    @Column(name = "team_currently_assigned")
    private String teamCurrentlyAssigned;

    @Column(name = "team_currently_assigned_id")
    private String teamCurrentlyAssignedId;

    @Column(name = "teammate_first_replied")
    private String teammateFirstReplied;

    @Column(name = "teammate_replies")
    private Integer teammateReplies;

    @Column(name = "bot_transfer_reason")
    private String botTransferReason;

    @Column(name = "csat_token")
    private String csatToken;

    @Column(name = "requiere_revision_manual")
    private Boolean requiereRevisionManual = false;

    @Column(name = "email_message_id", unique = true)
    private String emailMessageId;

    @PrePersist
    public void prePersist() {
        this.conversationCreatedAt = LocalDateTime.now();
        if (this.currentConversationState == null)
            this.currentConversationState = "open";
    }
}