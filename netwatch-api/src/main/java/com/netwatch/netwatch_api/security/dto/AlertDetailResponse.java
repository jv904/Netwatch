package com.netwatch.netwatch_api.security.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.netwatch.netwatch_api.security.AlertNote;
import com.netwatch.netwatch_api.security.AlertStatus;

/** Um alerta com a linha do tempo (comentários e mudanças de situação). */
public record AlertDetailResponse(AlertResponse alert, List<Note> timeline) {

    public record Note(UUID id, Instant createdAt, AlertNote.Kind kind, AlertStatus fromStatus, AlertStatus toStatus,
            String text) {

        public static Note from(AlertNote n) {
            return new Note(n.getId(), n.getCreatedAt(), n.getKind(), n.getFromStatus(), n.getToStatus(), n.getText());
        }
    }
}
