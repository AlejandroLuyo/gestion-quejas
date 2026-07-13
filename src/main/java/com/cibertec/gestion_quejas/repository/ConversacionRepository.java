package com.cibertec.gestion_quejas.repository;

import com.cibertec.gestion_quejas.model.Conversacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;

@Repository
public interface ConversacionRepository extends JpaRepository<Conversacion, Long> {
    List<Conversacion> findByCurrentConversationState(String state, Sort sort);
    List<Conversacion> findByTeammateCurrentlyAssigned(String teammateCurrentlyAssigned, Sort sort);
    List<Conversacion> findByTeammateCurrentlyAssignedIsNull(Sort sort);
    List<Conversacion> findByContactReason(String contactReason);
    List<Conversacion> findByRequiereRevisionManualTrue(Sort sort);
    Optional<Conversacion> findByCsatToken(String csatToken);
    boolean existsByEmailMessageId(String emailMessageId);
    long countByTeammateCurrentlyAssignedAndCurrentConversationStateIn(String teammateCurrentlyAssigned, List<String> estados);
    List<Conversacion> findByTeammateCurrentlyAssignedAndCurrentConversationState(String teammateCurrentlyAssigned, String estado, Sort sort);
    long countByTeammateCurrentlyAssignedAndCurrentConversationStateAndConversationLastClosedAtBetween(
            String teammateCurrentlyAssigned, String estado, LocalDateTime desde, LocalDateTime hasta);
    Optional<Conversacion> findFirstByOrderIdAndChannelAndCurrentConversationStateIn(
            String orderId, String channel, List<String> estados);

    @Query("SELECT c FROM Conversacion c LEFT JOIN FETCH c.orden o LEFT JOIN FETCH o.producto")
    List<Conversacion> findAllConOrden(Sort sort);

}