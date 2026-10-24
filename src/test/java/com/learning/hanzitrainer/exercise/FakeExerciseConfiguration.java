package com.learning.hanzitrainer.exercise;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
public class FakeExerciseConfiguration {

    @Bean
    @Primary
    FakeExerciseGenerator fakeExerciseGenerator() {
        return new FakeExerciseGenerator();
    }

    @Bean
    @Primary
    FakeTranslationGrader fakeTranslationGrader() {
        return new FakeTranslationGrader();
    }
}
