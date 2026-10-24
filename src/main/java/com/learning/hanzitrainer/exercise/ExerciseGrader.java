package com.learning.hanzitrainer.exercise;

import com.learning.hanzitrainer.common.BadRequestException;
import com.learning.hanzitrainer.common.ServiceUnavailableException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Grading per type (spec 07): deterministic wherever possible, handwriting from the Hanzi Writer quiz result, and
 * free translations by the LLM through {@link TranslationGrader}.
 */
@Component
public class ExerciseGrader {

    /** A character with more mistakes than this fails (spec 07). */
    static final int MAX_MISTAKES_PER_CHARACTER = 3;

    private final ExerciseCodec codec;
    private final TranslationGrader translationGrader;

    public ExerciseGrader(ExerciseCodec codec, TranslationGrader translationGrader) {
        this.codec = codec;
        this.translationGrader = translationGrader;
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
            case ExercisePrompt.MeaningToHanzi p -> meaningToHanzi(p, response, durationMs, exercise.getAnswer());
            case ExercisePrompt.FillBlank p ->
                    fillBlank(p, (ExerciseAnswer.FillBlank) codec.answer(exercise), response, durationMs,
                            exercise.getAnswer());
            case ExercisePrompt.WordOrder p ->
                    wordOrder(p, (ExerciseAnswer.WordOrder) codec.answer(exercise), response, durationMs,
                            exercise.getAnswer());
            case ExercisePrompt.Translation p ->
                    translation(p, (ExerciseAnswer.Translation) codec.answer(exercise), response, durationMs,
                            exercise.getAnswer());
        };
    }

    private static AnswerResult fillBlank(ExercisePrompt.FillBlank prompt, ExerciseAnswer.FillBlank answer,
                                          JsonNode response, long durationMs, JsonNode expected) {
        JsonNode selected = response == null ? null : response.get("selected");
        if (selected == null || !selected.isString() || !prompt.options().contains(selected.asString())) {
            throw new BadRequestException("FILL_BLANK response needs \"selected\", one of the options");
        }
        boolean correct = answer.correct().equals(selected.asString());
        String feedback = correct ? null : "The sentence is " + answer.fullSentence();
        double score = correct ? 1 : 0;
        return new AnswerResult(correct, score, expected, feedback, SuggestedRating.of(score, durationMs));
    }

    /** {@code order} must use exactly the prompt's tokens; it is correct if it is one of the accepted orders. */
    private static AnswerResult wordOrder(ExercisePrompt.WordOrder prompt, ExerciseAnswer.WordOrder answer,
                                          JsonNode response, long durationMs, JsonNode expected) {
        JsonNode order = response == null ? null : response.get("order");
        if (order == null || !order.isArray()) {
            throw new BadRequestException("WORD_ORDER response needs an array \"order\"");
        }
        List<String> given = new ArrayList<>();
        for (JsonNode token : order) {
            if (!token.isString()) {
                throw new BadRequestException("\"order\" must contain the tokens as strings");
            }
            given.add(token.asString());
        }
        if (!given.stream().sorted().toList().equals(prompt.tokens().stream().sorted().toList())) {
            throw new BadRequestException("\"order\" must use each token of the exercise exactly once");
        }
        boolean correct = answer.accepted().contains(given);
        String feedback = correct ? null : "A correct order: " + String.join(" ", answer.accepted().getFirst());
        double score = correct ? 1 : 0;
        return new AnswerResult(correct, score, expected, feedback, SuggestedRating.of(score, durationMs));
    }

    /**
     * The reference itself (ignoring punctuation and spaces) is correct without asking the LLM. {@code expected}
     * carries the reference and the grader's {@code corrected} sentence.
     */
    private AnswerResult translation(ExercisePrompt.Translation prompt, ExerciseAnswer.Translation answer,
                                     JsonNode response, long durationMs, JsonNode expected) {
        JsonNode text = response == null ? null : response.get("text");
        if (text == null || !text.isString() || text.asString().isBlank()) {
            throw new BadRequestException("TRANSLATION response needs a non-empty string \"text\"");
        }
        String given = text.asString().strip();
        ObjectNode withCorrection = (ObjectNode) expected.deepCopy();
        if (normalise(given).equals(normalise(answer.reference()))) {
            withCorrection.put("corrected", given);
            return new AnswerResult(true, 1, withCorrection, null, SuggestedRating.of(1, durationMs));
        }
        translationGrader.unavailableReason().ifPresent(reason -> {
            throw new ServiceUnavailableException(reason);
        });
        TranslationGrader.TranslationGrade grade = translationGrader.grade(prompt.source(), answer.reference(), given);
        double score = Math.clamp(grade.score(), 0, 1);
        withCorrection.put("corrected", grade.corrected() == null ? answer.reference() : grade.corrected());
        return new AnswerResult(grade.correct(), score, withCorrection, grade.feedback(),
                SuggestedRating.of(score, durationMs));
    }

    /** Punctuation and spaces are ignored when comparing a translation with the reference. */
    private static String normalise(String chinese) {
        return Punctuation.removePunctuationAndSpaces(chinese);
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

    /**
     * Skipping is wrong; otherwise a character fails with more than 3 mistakes, i.e. once the outline was shown
     * ({@code showHintAfterMisses: 3}), and the score is the share of characters that did not fail.
     */
    private static AnswerResult meaningToHanzi(ExercisePrompt.MeaningToHanzi prompt, JsonNode response,
                                               long durationMs, JsonNode expected) {
        if (response == null || !response.isObject()) {
            throw new BadRequestException("MEANING_TO_HANZI response needs \"strokeMistakes\" and \"skipped\"");
        }
        JsonNode hanzi = response.get("hanzi");
        if (hanzi != null && !hanzi.isNull() && !hanzi.isString()) {
            throw new BadRequestException("\"hanzi\" must be a string");
        }
        if (hanzi != null && !hanzi.isNull() && !prompt.hanzi().equals(hanzi.asString())) {
            throw new BadRequestException("Response is for " + hanzi.asString() + ", not " + prompt.hanzi());
        }
        JsonNode skipped = response.get("skipped");
        if (skipped == null || !skipped.isBoolean()) {
            throw new BadRequestException("MEANING_TO_HANZI response needs a boolean \"skipped\"");
        }
        if (skipped.asBoolean()) {
            return new AnswerResult(false, 0, expected, "Skipped: " + prompt.hanzi() + " (" + prompt.pinyin() + ").",
                    SuggestedRating.writing(0, durationMs, 0, 0));
        }
        int hintsUsed = nonNegative(response.get("hintsUsed"), "hintsUsed");
        JsonNode mistakes = response.get("strokeMistakes");
        if (mistakes == null || !mistakes.isArray() || mistakes.size() != prompt.characterCount()) {
            throw new BadRequestException("MEANING_TO_HANZI response needs \"strokeMistakes\" with "
                    + prompt.characterCount() + " number(s)");
        }
        int[] chars = prompt.hanzi().codePoints().toArray();
        List<String> failed = new ArrayList<>();
        int total = 0;
        for (int i = 0; i < mistakes.size(); i++) {
            int count = nonNegative(mistakes.get(i), "strokeMistakes");
            total += count;
            if (count > MAX_MISTAKES_PER_CHARACTER) {
                failed.add(Character.toString(chars[i]));
            }
        }
        double score = (double) (chars.length - failed.size()) / chars.length;
        boolean correct = failed.isEmpty();
        String feedback = correct ? null : String.join(" ", failed) + " needed the outline (more than "
                + MAX_MISTAKES_PER_CHARACTER + " mistakes). Write it again a few times.";
        return new AnswerResult(correct, score, expected, feedback,
                SuggestedRating.writing(score, durationMs, total, hintsUsed));
    }

    /** A missing or null value counts as 0. */
    private static int nonNegative(JsonNode value, String field) {
        if (value == null || value.isNull()) {
            return 0;
        }
        if (!value.isInt() || value.asInt() < 0) {
            throw new BadRequestException("\"" + field + "\" must be a whole number ≥ 0");
        }
        return value.asInt();
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
