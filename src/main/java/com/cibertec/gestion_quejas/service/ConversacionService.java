package com.cibertec.gestion_quejas.service;

import com.cibertec.gestion_quejas.model.Conversacion;
import com.cibertec.gestion_quejas.repository.ConversacionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Sort;
import java.util.List;

@Service
public class ConversacionService {

    @Autowired
    private ConversacionRepository conversacionRepository;

    public List<Conversacion> listarTodas(Sort sort) {
        return conversacionRepository.findAll(sort);
    }
    public List<Conversacion> listarPorEstado(String estado, Sort sort) {
        return conversacionRepository.findByCurrentConversationState(estado, sort);
    }

    public List<Conversacion> listarAsignadasA(String nombre, Sort sort) {
        return conversacionRepository.findByTeammateCurrentlyAssigned(nombre, sort);
    }

    public List<Conversacion> listarSinAsignar(Sort sort) {
        return conversacionRepository.findByTeammateCurrentlyAssignedIsNull(sort);
    }

    @Transactional
    public Conversacion guardar(Conversacion conversacion) {
        return conversacionRepository.save(conversacion);
    }

    public Conversacion buscarPorId(Long id) {
        return conversacionRepository.findById(id).orElse(null);
    }

    public Conversacion buscarPorToken(String token) {
        return conversacionRepository.findByCsatToken(token).orElse(null);
    }

    public void eliminar(Long id) {
        conversacionRepository.deleteById(id);
    }

}