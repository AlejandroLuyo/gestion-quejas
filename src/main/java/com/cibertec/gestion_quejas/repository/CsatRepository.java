package com.cibertec.gestion_quejas.repository;

import com.cibertec.gestion_quejas.model.Csat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CsatRepository extends JpaRepository<Csat, Long> {
    Optional<Csat> findByConversacionConversacionId(Long conversacionId);
}