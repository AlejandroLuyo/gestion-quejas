package com.cibertec.gestion_quejas.repository;

import com.cibertec.gestion_quejas.model.Reembolso;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface ReembolsoRepository extends JpaRepository<Reembolso, Long> {
    Optional<Reembolso> findByConversacionConversacionId(Long conversacionId);
}