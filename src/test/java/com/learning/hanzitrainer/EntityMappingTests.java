package com.learning.hanzitrainer;

import com.learning.hanzitrainer.cedict.CedictEntry;
import com.learning.hanzitrainer.exercise.Exercise;
import com.learning.hanzitrainer.exercise.ExerciseType;
import com.learning.hanzitrainer.exercise.ExerciseWord;
import com.learning.hanzitrainer.exercise.ExerciseWordId;
import com.learning.hanzitrainer.page.ExtractionStatus;
import com.learning.hanzitrainer.page.PageImage;
import com.learning.hanzitrainer.review.CardState;
import com.learning.hanzitrainer.review.CardType;
import com.learning.hanzitrainer.review.ReviewCard;
import com.learning.hanzitrainer.review.ReviewLog;
import com.learning.hanzitrainer.textbook.Lesson;
import com.learning.hanzitrainer.textbook.Textbook;
import com.learning.hanzitrainer.word.LessonWord;
import com.learning.hanzitrainer.word.LessonWordId;
import com.learning.hanzitrainer.word.Word;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Round-trips every entity through PostgreSQL, including the JSONB and TEXT[] columns. */
@SpringBootTest
@Transactional
@Import(TestcontainersConfiguration.class)
class EntityMappingTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private EntityManager em;

    @Test
    void allEntitiesRoundTrip() {
        Textbook textbook = persist(new Textbook("HSK Standard Course 1", "1st"));
        Lesson lesson = persist(new Lesson(textbook, 1, "你好"));

        PageImage page = new PageImage(lesson, 12, "lesson-1/a.jpg", "image/jpeg");
        page.setExtractionStatus(ExtractionStatus.DONE);
        page.setRawExtraction(JSON.readTree("{\"pageType\":\"VOCABULARY_LIST\",\"words\":[{\"hanzi\":\"朋友\"}]}"));
        persist(page);

        CedictEntry entry = persist(new CedictEntry("朋友", "朋友", "peng2 you5", "péngyou",
                List.of("friend", "CL:個|个[ge4],位[wei4]")));

        Word word = new Word("朋友", "péngyou", "peng2 you5");
        word.setMeaningEn("friend");
        word.setCedictEntry(entry);
        word.setVerified(true);
        persist(word);

        LessonWord lessonWord = new LessonWord(lesson, word);
        lessonWord.setSourcePage(page);
        lessonWord.setExampleSentence("他是我的朋友。");
        persist(lessonWord);

        Exercise exercise = persist(new Exercise(lesson, ExerciseType.HANZI_TO_MEANING,
                JSON.readTree("{\"hanzi\":\"朋友\",\"options\":[\"friend\",\"teacher\"]}"),
                JSON.readTree("{\"correct\":\"friend\"}"), "code"));
        persist(new ExerciseWord(exercise, word));

        ReviewCard card = new ReviewCard(word, CardType.WRITING);
        card.setState(CardState.LEARNING);
        card.setStep(1);
        persist(card);
        persist(new ReviewLog(card, (short) 3, CardState.NEW, 0, 0, Instant.now()));

        em.flush();
        em.clear();

        PageImage loadedPage = em.find(PageImage.class, page.getId());
        assertThat(loadedPage.getExtractionStatus()).isEqualTo(ExtractionStatus.DONE);
        assertThat(loadedPage.getRawExtraction().get("words").get(0).get("hanzi").asString()).isEqualTo("朋友");

        assertThat(em.find(CedictEntry.class, entry.getId()).getDefinitions())
                .containsExactly("friend", "CL:個|个[ge4],位[wei4]");

        Word loadedWord = em.find(Word.class, word.getId());
        assertThat(loadedWord.getCedictEntry().getId()).isEqualTo(entry.getId());

        LessonWord loadedLessonWord = em.find(LessonWord.class, new LessonWordId(lesson.getId(), word.getId()));
        assertThat(loadedLessonWord.getSourcePage().getId()).isEqualTo(page.getId());

        assertThat(em.find(Exercise.class, exercise.getId()).getAnswer().get("correct").asString()).isEqualTo("friend");
        assertThat(em.find(ExerciseWord.class, new ExerciseWordId(exercise.getId(), word.getId()))).isNotNull();

        ReviewCard loadedCard = em.find(ReviewCard.class, card.getId());
        assertThat(loadedCard.getState()).isEqualTo(CardState.LEARNING);
        assertThat(loadedCard.getStep()).isEqualTo(1);
        assertThat(em.createQuery("select l from ReviewLog l", ReviewLog.class).getSingleResult().getRating())
                .isEqualTo((short) 3);
    }

    private <T> T persist(T entity) {
        em.persist(entity);
        return entity;
    }
}
