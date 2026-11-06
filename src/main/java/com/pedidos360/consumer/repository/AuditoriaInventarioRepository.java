package com.pedidos360.consumer.repository;

import com.pedidos360.consumer.model.AuditoriaInventario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AuditoriaInventarioRepository extends JpaRepository<AuditoriaInventario, Long> {

    /**
     * Verifica la existencia previa de un evento para garantizar idempotencia.
     */
    boolean existsByEventoId(UUID eventoId);
}

