package com.cibertec.gestion_quejas.service;

import com.cibertec.gestion_quejas.model.FeatureFlag;
import com.cibertec.gestion_quejas.repository.FeatureFlagRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class FeatureFlagService {

    private final FeatureFlagRepository repository;

    public FeatureFlagService(FeatureFlagRepository repository) {
        this.repository = repository;
    }

    public boolean isEnabled(String key) {
        return repository.findById(key)
                .map(FeatureFlag::isEnabled)
                .orElse(false);
    }

    public List<FeatureFlag> listarTodos() {
        return repository.findAll();
    }

    @Transactional
    public void toggle(String key, boolean value) {
        FeatureFlag flag = repository.findById(key)
                .orElseThrow(() -> new RuntimeException("Flag no encontrado: " + key));
        flag.setEnabled(value);
        flag.setUpdatedAt(LocalDateTime.now());
        repository.save(flag);
    }

    @Transactional
    public void guardarEstados(Map<String, Boolean> estados) {
        for (Map.Entry<String, Boolean> entry : estados.entrySet()) {
            repository.findById(entry.getKey()).ifPresent(flag -> {
                flag.setEnabled(entry.getValue());
                flag.setUpdatedAt(LocalDateTime.now());
                repository.save(flag);
            });
        }
    }
}