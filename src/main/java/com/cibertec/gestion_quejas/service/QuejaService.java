package com.cibertec.gestion_quejas.service;

import com.cibertec.gestion_quejas.model.Queja;
import com.cibertec.gestion_quejas.repository.QuejaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class QuejaService {

    @Autowired
    private QuejaRepository quejaRepository;

    public List<Queja> listarTodas() {
        return quejaRepository.findAll();
    }

    public Queja guardar(Queja queja) {
        return quejaRepository.save(queja);
    }

    public Queja buscarPorId(Long id) {
        return quejaRepository.findById(id).orElse(null);
    }

    public void eliminar(Long id) {
        quejaRepository.deleteById(id);
    }
}