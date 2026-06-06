package com.cibertec.gestion_quejas.repository;

import com.cibertec.gestion_quejas.model.Mensaje;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MensajeRepository extends JpaRepository<Mensaje, Long> {
    List<Mensaje> findByConversacionConversacionIdOrderByFechaEnvioAsc(Long conversacionId);
}