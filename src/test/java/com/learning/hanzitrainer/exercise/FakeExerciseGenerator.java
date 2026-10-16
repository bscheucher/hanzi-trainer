package com.learning.hanzitrainer.exercise;

import com.learning.hanzitrainer.word.WordDto;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/** Test double for {@link ExerciseGenerator}; tests decide what each call returns and see what it was asked. */
public class FakeExerciseGenerator implements ExerciseGenerator {

    /** The arguments of one call. */
    public record Call(ExerciseType type, List<WordDto> targetWords, Set<String> allowedWords, int count) {
    }

    private final List<Call> calls = new CopyOnWriteArrayList<>();
    private volatile Function<Call, List<GeneratedExercise>> behaviour = call -> List.of();

    @Override
    public List<GeneratedExercise> generate(ExerciseType type, List<WordDto> targetWords, Set<String> allowedWords,
                                            int count) {
        Call call = new Call(type, targetWords, allowedWords, count);
        calls.add(call);
        return behaviour.apply(call);
    }

    @Override
    public String modelName() {
        return "fake-model";
    }

    public void willAnswer(Function<Call, List<GeneratedExercise>> answer) {
        behaviour = answer;
    }

    public List<Call> calls() {
        return calls;
    }

    public void reset() {
        calls.clear();
        behaviour = call -> List.of();
    }
}
