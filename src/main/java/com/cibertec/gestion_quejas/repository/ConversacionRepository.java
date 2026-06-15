package com.cibertec.gestion_quejas.repository;

import com.cibertec.gestion_quejas.model.Conversacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Sort;
import java.util.Optional;
import java.util.List;

@Repository
public interface ConversacionRepository extends JpaRepository<Conversacion, Long> {
    List<Conversacion> findByCurrentConversationState(String state, Sort sort);
    List<Conversacion> findByTeammateCurrentlyAssigned(String teammateCurrentlyAssigned, Sort sort);
    List<Conversacion> findByTeammateCurrentlyAssignedIsNull(Sort sort);
    List<Conversacion> findByContactReason(String contactReason);
    Optional<Conversacion> findByCsatToken(String csatToken);
}