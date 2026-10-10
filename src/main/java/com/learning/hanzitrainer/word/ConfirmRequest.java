package com.learning.hanzitrainer.word;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** @param sourcePageId page the items were reviewed from; marked confirmed on success */
public record ConfirmRequest(Long sourcePageId, @NotEmpty List<ConfirmItem> items) {
}
