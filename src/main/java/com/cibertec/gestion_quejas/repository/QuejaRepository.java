package com.cibertec.gestion_quejas.repository;

import com.cibertec.gestion_quejas.model.Queja;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface QuejaRepository extends JpaRepository<Queja, Long> {
}