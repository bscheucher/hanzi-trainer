package com.learning.hanzitrainer.common;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** {@code @Async} runs on Boot's task executor, which uses virtual threads (spring.threads.virtual.enabled). */
@Configuration(proxyBeanMethods = false)
@EnableAsync
public class AsyncConfig {
}
