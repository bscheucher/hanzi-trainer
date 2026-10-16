package com.learning.hanzitrainer.exercise;

/**
 * Progress of {@code POST /lessons/{id}/exercises/generate} (spec 03).
 *
 * @param created  exercises stored so far
 * @param rejected exercises the LLM wrote that failed a check (spec 07) and were discarded
 * @param error    why the job failed, else null
 */
public record GenerationJobDto(long id, Status status, int created, int rejected, String error) {

    public enum Status { RUNNING, DONE, FAILED }
}
