package com.learning.hanzitrainer.textbook;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTextbookRequest(@NotBlank @Size(max = 255) String title, @Size(max = 100) String edition) {
}
