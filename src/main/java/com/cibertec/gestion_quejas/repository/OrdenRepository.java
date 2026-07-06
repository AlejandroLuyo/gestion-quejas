package com.cibertec.gestion_quejas.repository;

import com.cibertec.gestion_quejas.model.Orden;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrdenRepository extends JpaRepository<Orden, String> {
    List<Orden> findByEmailClienteIgnoreCase(String emailCliente);

    @Query("SELECT o FROM Orden o ORDER BY o.orderId DESC")
    List<Orden> listarTodasOrdenadasPorIdDesc(Pageable pageable);
}