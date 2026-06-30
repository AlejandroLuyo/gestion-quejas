package com.cibertec.gestion_quejas.repository;

import com.cibertec.gestion_quejas.model.Reembolso;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReembolsoRepository extends JpaRepository<Reembolso, Long> {
    Optional<Reembolso> findByConversacionConversacionId(Long conversacionId);

    @Query("SELECT r FROM Reembolso r WHERE " +
            "(:texto IS NULL OR CAST(r.conversacion.orderId AS string) LIKE %:texto% OR " +
            " LOWER(r.conversacion.teammateCurrentlyAssigned) LIKE LOWER(CONCAT('%', :texto, '%')) OR " +
            " LOWER(r.refundReasonCategory) LIKE LOWER(CONCAT('%', :texto, '%'))) AND " +
            "(:desde IS NULL OR r.conversacion.conversationCreatedAt >= :desde) AND " +
            "(:hasta IS NULL OR r.conversacion.conversationCreatedAt <= :hasta)")
    List<Reembolso> buscarConFiltros(
            @Param("texto") String texto,
            @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta);
}