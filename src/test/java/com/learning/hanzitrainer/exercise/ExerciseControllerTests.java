package com.learning.hanzitrainer.exercise;

import com.learning.hanzitrainer.common.BadRequestException;
import com.learning.hanzitrainer.common.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExerciseController.class)
class ExerciseControllerTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final ExerciseDto TONE = new ExerciseDto(3L, ExerciseType.TONE,
            JSON.valueToTree(Map.of("hanzi", "朋友", "syllables", List.of("peng", "you"))), List.of(5L));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExerciseService exerciseService;

    @MockitoBean
    private PracticeService practiceService;

    @MockitoBean
    private GenerationJobService generationJobs;

    @Test
    void generateStartsAJobWith202() throws Exception {
        GenerateExercisesRequest request = new GenerateExercisesRequest(List.of(ExerciseType.FILL_BLANK), 5);
        when(generationJobs.start(7L, request))
                .thenReturn(new GenerationJobDto(1, GenerationJobDto.Status.RUNNING, 0, 0, null));

        mockMvc.perform(post("/api/lessons/7/exercises/generate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"types\": [\"FILL_BLANK\"], \"count\": 5}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.created").value(0))
                .andExpect(jsonPath("$.rejected").value(0));
    }

    @Test
    void generateRequestIsValidated() throws Exception {
        for (String body : List.of("{\"types\": [], \"count\": 5}", "{\"types\": [\"FILL_BLANK\"]}",
                "{\"types\": [\"FILL_BLANK\"], \"count\": 21}", "{\"types\": [\"FILL_BLANK\"], \"count\": 0}")) {
            mockMvc.perform(post("/api/lessons/7/exercises/generate").contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(generationJobs);
    }

    @Test
    void generationJobIsPolledAndUnknownIs404() throws Exception {
        when(generationJobs.get(1)).thenReturn(new GenerationJobDto(1, GenerationJobDto.Status.FAILED, 2, 1, "boom"));
        when(generationJobs.get(9)).thenThrow(new NotFoundException("Generation job", 9));

        mockMvc.perform(get("/api/generation-jobs/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.created").value(2))
                .andExpect(jsonPath("$.error").value("boom"));
        mockMvc.perform(get("/api/generation-jobs/9")).andExpect(status().isNotFound());
    }

    @Test
    void sessionReturnsExercisesWithoutAnswers() throws Exception {
        when(practiceService.createSession(new PracticeSessionRequest(7L, List.of(ExerciseType.TONE), 10)))
                .thenReturn(new PracticeSessionDto(List.of(TONE)));

        mockMvc.perform(post("/api/practice/sessions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lessonId\": 7, \"types\": [\"TONE\"], \"size\": 10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exercises[0].id").value(3))
                .andExpect(jsonPath("$.exercises[0].type").value("TONE"))
                .andExpect(jsonPath("$.exercises[0].prompt.syllables[1]").value("you"))
                .andExpect(jsonPath("$.exercises[0].wordIds[0]").value(5))
                .andExpect(jsonPath("$.exercises[0].answer").doesNotExist());
    }

    @Test
    void sessionRequestIsValidated() throws Exception {
        mockMvc.perform(post("/api/practice/sessions").contentType(MediaType.APPLICATION_JSON).content("{\"size\": 5}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/practice/sessions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lessonId\": 7, \"size\": 0}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/practice/sessions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lessonId\": 7, \"types\": [\"SINGING\"]}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(practiceService);
    }

    @Test
    void answerReturnsTheGradeAndTheExpectedAnswer() throws Exception {
        when(exerciseService.answer(eq(3L), any())).thenReturn(new AnswerResult(false, 0.5,
                JSON.valueToTree(Map.of("tones", List.of(2, 5))), "you: neutral tone, not tone 3.", 2));

        mockMvc.perform(post("/api/exercises/3/answer").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"response\": {\"tones\": [2, 3]}, \"durationMs\": 4200}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correct").value(false))
                .andExpect(jsonPath("$.score").value(0.5))
                .andExpect(jsonPath("$.expected.tones[1]").value(5))
                .andExpect(jsonPath("$.feedback").value("you: neutral tone, not tone 3."))
                .andExpect(jsonPath("$.suggestedRating").value(2));
    }

    @Test
    void answerNeedsAResponseAndADuration() throws Exception {
        mockMvc.perform(post("/api/exercises/3/answer").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"response\": {\"selected\": \"friend\"}}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/exercises/3/answer").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"durationMs\": 100}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(exerciseService);
    }

    @Test
    void answerOfTheWrongShapeIs400() throws Exception {
        when(exerciseService.answer(eq(3L), any()))
                .thenThrow(new BadRequestException("TONE response needs \"tones\" with 2 number(s)"));

        mockMvc.perform(post("/api/exercises/3/answer").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"response\": {\"selected\": \"x\"}, \"durationMs\": 100}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad request"))
                .andExpect(jsonPath("$.detail").value("TONE response needs \"tones\" with 2 number(s)"));
    }

    @Test
    void listFiltersByTypeAndUnknownExerciseIs404() throws Exception {
        when(exerciseService.list(7L, ExerciseType.TONE)).thenReturn(List.of(TONE));
        when(exerciseService.answer(eq(9L), any())).thenThrow(new NotFoundException("Exercise", 9));

        mockMvc.perform(get("/api/lessons/7/exercises").param("type", "TONE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("TONE"));
        mockMvc.perform(post("/api/exercises/9/answer").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"response\": {}, \"durationMs\": 1}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteIs204() throws Exception {
        mockMvc.perform(delete("/api/exercises/3")).andExpect(status().isNoContent());
        verify(exerciseService).delete(3);
    }
}
