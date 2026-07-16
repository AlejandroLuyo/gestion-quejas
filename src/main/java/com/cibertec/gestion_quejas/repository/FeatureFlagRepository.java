package com.cibertec.gestion_quejas.repository;

import com.cibertec.gestion_quejas.model.FeatureFlag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FeatureFlagRepository extends JpaRepository<FeatureFlag, String> {
}