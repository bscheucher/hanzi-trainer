package com.learning.hanzitrainer.exercise;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Reads and writes the {@code prompt} / {@code answer} JSON of an exercise, with its type as discriminator. */
@Component
public class ExerciseCodec {

    private final JsonMapper json;

    public ExerciseCodec(JsonMapper json) {
        this.json = json;
    }

    public JsonNode toJson(Object value) {
        return json.valueToTree(value);
    }

    public ExercisePrompt prompt(Exercise exercise) {
        Class<? extends ExercisePrompt> shape = switch (exercise.getType()) {
            case HANZI_TO_MEANING -> ExercisePrompt.HanziToMeaning.class;
            case TONE -> ExercisePrompt.Tone.class;
            case MEANING_TO_HANZI -> ExercisePrompt.MeaningToHanzi.class;
            case FILL_BLANK -> ExercisePrompt.FillBlank.class;
            case WORD_ORDER -> ExercisePrompt.WordOrder.class;
            case TRANSLATION -> ExercisePrompt.Translation.class;
        };
        return json.treeToValue(exercise.getPrompt(), shape);
    }

    public ExerciseAnswer answer(Exercise exercise) {
        Class<? extends ExerciseAnswer> shape = switch (exercise.getType()) {
            case HANZI_TO_MEANING -> ExerciseAnswer.HanziToMeaning.class;
            case TONE -> ExerciseAnswer.Tone.class;
            case MEANING_TO_HANZI -> ExerciseAnswer.MeaningToHanzi.class;
            case FILL_BLANK -> ExerciseAnswer.FillBlank.class;
            case WORD_ORDER -> ExerciseAnswer.WordOrder.class;
            case TRANSLATION -> ExerciseAnswer.Translation.class;
        };
        return json.treeToValue(exercise.getAnswer(), shape);
    }
}
