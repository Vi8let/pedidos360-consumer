package com.pedidos360.consumer.repository;

import com.pedidos360.consumer.model.AuditoriaSeguridad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AuditoriaSeguridadRepository extends JpaRepository<AuditoriaSeguridad, Long> {

    /**
     * Verifica la existencia previa de un evento para garantizar idempotencia.
     */
    boolean existsByEventoId(UUID eventoId);
}
