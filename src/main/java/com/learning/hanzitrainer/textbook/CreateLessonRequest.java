package com.learning.hanzitrainer.textbook;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateLessonRequest(@NotNull @Positive Integer lessonNo, @Size(max = 255) String title) {
}
