package com.netwatch.netwatch_api.security;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertNoteRepository extends JpaRepository<AlertNote, UUID> {

    List<AlertNote> findByAlertIdOrderByCreatedAtAsc(UUID alertId);

    /** Mudanças de situação (ou comentários) registradas no período [from, to). */
    List<AlertNote> findByKindAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(AlertNote.Kind kind,
            java.time.Instant from, java.time.Instant to);
}
