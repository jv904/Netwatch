package com.netwatch.netwatch_api.security.dto;

import java.util.List;
import java.util.UUID;

import com.netwatch.netwatch_api.security.AlertService.BulkAction;
import com.netwatch.netwatch_api.security.DismissReason;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Corpos das ações sobre alertas. */
public final class AlertActionRequests {

    private AlertActionRequests() {
    }

    /** Reconhecer, resolver ou reabrir, com observação opcional. */
    public record NoteRequest(@Size(max = 2000) String note) {
    }

    /**
     * @param suppress "não alertar novamente" para o mesmo problema
     */
    public record DismissRequest(
            @NotNull(message = "Informe o motivo do descarte.") DismissReason reason,
            @Size(max = 2000) String note,
            Boolean suppress) {

        public boolean suppressRepeats() {
            return Boolean.TRUE.equals(suppress);
        }
    }

    public record CommentRequest(
            @NotBlank(message = "Escreva o comentário.") @Size(max = 2000) String text) {
    }

    public record BulkRequest(
            @NotEmpty(message = "Selecione ao menos um alerta.") @Size(max = 500) List<UUID> ids,
            @NotNull(message = "Informe a ação.") BulkAction action,
            @Size(max = 2000) String note,
            DismissReason reason,
            Boolean suppress) {

        public boolean suppressRepeats() {
            return Boolean.TRUE.equals(suppress);
        }
    }
}
