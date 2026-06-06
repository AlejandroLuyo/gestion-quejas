package com.cibertec.gestion_quejas.service;

import com.cibertec.gestion_quejas.model.Conversacion;
import com.cibertec.gestion_quejas.repository.ConversacionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ConversacionService {

    @Autowired
    private ConversacionRepository conversacionRepository;

    public List<Conversacion> listarTodas() {
        return conversacionRepository.findAll();
    }

    public Conversacion guardar(Conversacion conversacion) {
        return conversacionRepository.save(conversacion);
    }

    public Conversacion buscarPorId(Long id) {
        return conversacionRepository.findById(id).orElse(null);
    }

    public void eliminar(Long id) {
        conversacionRepository.deleteById(id);
    }

    public List<Conversacion> listarPorEstado(String estado) {
        return conversacionRepository.findByCurrentConversationState(estado);
    }
}