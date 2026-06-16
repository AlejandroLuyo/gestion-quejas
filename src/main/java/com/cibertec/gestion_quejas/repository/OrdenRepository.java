package com.cibertec.gestion_quejas.repository;

import com.cibertec.gestion_quejas.model.Orden;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrdenRepository extends JpaRepository<Orden, String> {
}