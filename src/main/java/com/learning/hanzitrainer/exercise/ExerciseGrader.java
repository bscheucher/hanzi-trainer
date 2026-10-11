package com.learning.hanzitrainer.exercise;

import com.learning.hanzitrainer.common.BadRequestException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/** Deterministic grading for the code-generated types (spec 07). */
@Component
public class ExerciseGrader {

    private final ExerciseCodec codec;

    public ExerciseGrader(ExerciseCodec codec) {
        this.codec = codec;
    }

    /**
     * @param response the type-specific client response, e.g. {@code {"selected": "friend"}}
     * @throws BadRequestException if the response does not have the shape of the exercise type
     */
    public AnswerResult grade(Exercise exercise, JsonNode response, long durationMs) {
        return switch (codec.prompt(exercise)) {
            case ExercisePrompt.HanziToMeaning p ->
                    hanziToMeaning(p, (ExerciseAnswer.HanziToMeaning) codec.answer(exercise), response, durationMs,
                            exercise.getAnswer());
            case ExercisePrompt.Tone p ->
                    tone(p, (ExerciseAnswer.Tone) codec.answer(exercise), response, durationMs, exercise.getAnswer());
        };
    }

    private static AnswerResult hanziToMeaning(ExercisePrompt.HanziToMeaning prompt, ExerciseAnswer.HanziToMeaning answer,
                                               JsonNode response, long durationMs, JsonNode expected) {
        JsonNode selected = response == null ? null : response.get("selected");
        if (selected == null || !selected.isString()) {
            throw new BadRequestException("HANZI_TO_MEANING response needs a string \"selected\"");
        }
        boolean correct = answer.correct().equals(selected.asString());
        String feedback = correct ? null : prompt.hanzi() + " means “" + answer.correct() + "”.";
        double score = correct ? 1 : 0;
        return new AnswerResult(correct, score, expected, feedback, SuggestedRating.of(score, durationMs));
    }

    private static AnswerResult tone(ExercisePrompt.Tone prompt, ExerciseAnswer.Tone answer, JsonNode response,
                                     long durationMs, JsonNode expected) {
        List<Integer> given = tones(response, answer.tones().size());
        List<String> wrong = new ArrayList<>();
        for (int i = 0; i < given.size(); i++) {
            if (!given.get(i).equals(answer.tones().get(i))) {
                wrong.add(prompt.syllables().get(i) + ": " + label(answer.tones().get(i)) + ", not "
                        + label(given.get(i)));
            }
        }
        double score = (double) (given.size() - wrong.size()) / given.size();
        boolean correct = wrong.isEmpty();
        String feedback = correct ? null : String.join("; ", wrong) + ".";
        return new AnswerResult(correct, score, expected, feedback, SuggestedRating.of(score, durationMs));
    }

    private static List<Integer> tones(JsonNode response, int expectedCount) {
        JsonNode tones = response == null ? null : response.get("tones");
        if (tones == null || !tones.isArray() || tones.size() != expectedCount) {
            throw new BadRequestException("TONE response needs \"tones\" with " + expectedCount + " number(s)");
        }
        List<Integer> result = new ArrayList<>();
        for (JsonNode tone : tones) {
            if (!tone.isInt() || tone.asInt() < 1 || tone.asInt() > 5) {
                throw new BadRequestException("Tones are 1–4, or 5 for neutral");
            }
            result.add(tone.asInt());
        }
        return result;
    }

    private static String label(int tone) {
        return tone == 5 ? "neutral tone" : "tone " + tone;
    }
}
