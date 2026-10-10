package com.learning.hanzitrainer.extraction;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
public class FakeExtractorConfiguration {

    @Bean
    @Primary
    FakeVocabularyExtractor fakeVocabularyExtractor() {
        return new FakeVocabularyExtractor();
    }
}
