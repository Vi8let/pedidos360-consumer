package com.pedidos360.consumer.repository;

import com.pedidos360.consumer.model.HistoricoBajas;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface HistoricoBajasRepository extends JpaRepository<HistoricoBajas, Long> {

    /**
     * Verifica la existencia previa de un evento para garantizar idempotencia.
     */
    boolean existsByEventoId(UUID eventoId);
}
