-- Chinese learning app – initial schema (PostgreSQL)
-- Single-user for now; add an app_user table + user_id FKs later if needed.

-- ============================================================
-- Textbooks, lessons, scanned pages
-- ============================================================
CREATE TABLE textbook (
    id          BIGSERIAL PRIMARY KEY,
    title       VARCHAR(255) NOT NULL,
    edition     VARCHAR(100),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE lesson (
    id           BIGSERIAL PRIMARY KEY,
    textbook_id  BIGINT       NOT NULL REFERENCES textbook (id) ON DELETE CASCADE,
    lesson_no    INT          NOT NULL,
    title        VARCHAR(255),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (textbook_id, lesson_no)
);

CREATE TABLE page_image (
    id                 BIGSERIAL PRIMARY KEY,
    lesson_id          BIGINT       NOT NULL REFERENCES lesson (id) ON DELETE CASCADE,
    page_no            INT,
    storage_path       VARCHAR(500) NOT NULL,          -- keep the original for re-extraction
    mime_type          VARCHAR(50)  NOT NULL,
    extraction_status  VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
        CHECK (extraction_status IN ('PENDING', 'RUNNING', 'DONE', 'FAILED')),
    extraction_model   VARCHAR(100),                   -- which LLM produced raw_extraction
    raw_extraction     JSONB,                          -- unreviewed LLM output
    error_message      TEXT,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ============================================================
-- Reference dictionary (imported from CC-CEDICT)
-- ============================================================
CREATE TABLE cedict_entry (
    id               BIGSERIAL PRIMARY KEY,
    simplified       VARCHAR(50)  NOT NULL,
    traditional      VARCHAR(50)  NOT NULL,
    pinyin_numbered  VARCHAR(200) NOT NULL,   -- as in CEDICT: "xue2 xi2"
    pinyin           VARCHAR(200) NOT NULL,   -- converted: "xuéxí"
    definitions      TEXT[]       NOT NULL
);
CREATE INDEX idx_cedict_simplified ON cedict_entry (simplified);

-- ============================================================
-- Vocabulary (one row per word+reading, shared across lessons)
-- ============================================================
CREATE TABLE word (
    id               BIGSERIAL PRIMARY KEY,
    hanzi            VARCHAR(50)  NOT NULL,
    pinyin           VARCHAR(200) NOT NULL,     -- tone marks
    pinyin_numbered  VARCHAR(200) NOT NULL,     -- for search / tone exercises
    meaning_en       TEXT,
    meaning_de       TEXT,
    part_of_speech   VARCHAR(50),
    cedict_entry_id  BIGINT REFERENCES cedict_entry (id),
    verified         BOOLEAN      NOT NULL DEFAULT FALSE,  -- matched CEDICT or confirmed by you
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (hanzi, pinyin)                      -- polyphones like 了 le / liǎo stay separate
);

CREATE TABLE lesson_word (
    lesson_id         BIGINT NOT NULL REFERENCES lesson (id) ON DELETE CASCADE,
    word_id           BIGINT NOT NULL REFERENCES word (id)   ON DELETE CASCADE,
    source_page_id    BIGINT REFERENCES page_image (id) ON DELETE SET NULL,
    example_sentence  TEXT,
    PRIMARY KEY (lesson_id, word_id)
);

-- ============================================================
-- Exercises (LLM- or code-generated, stored for reuse)
-- ============================================================
CREATE TABLE exercise (
    id            BIGSERIAL PRIMARY KEY,
    lesson_id     BIGINT       NOT NULL REFERENCES lesson (id) ON DELETE CASCADE,
    type          VARCHAR(30)  NOT NULL
        CHECK (type IN ('HANZI_TO_MEANING', 'MEANING_TO_HANZI', 'TONE',
                        'FILL_BLANK', 'WORD_ORDER', 'TRANSLATION')),
    prompt        JSONB        NOT NULL,   -- what the player renders
    answer        JSONB        NOT NULL,   -- answer key for deterministic grading
    generated_by  VARCHAR(100) NOT NULL,   -- 'code' or model name
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE exercise_word (
    exercise_id  BIGINT NOT NULL REFERENCES exercise (id) ON DELETE CASCADE,
    word_id      BIGINT NOT NULL REFERENCES word (id)     ON DELETE CASCADE,
    PRIMARY KEY (exercise_id, word_id)
);

-- ============================================================
-- Spaced repetition (FSRS)
-- ============================================================
CREATE TABLE review_card (
    id            BIGSERIAL PRIMARY KEY,
    word_id       BIGINT      NOT NULL REFERENCES word (id) ON DELETE CASCADE,
    card_type     VARCHAR(20) NOT NULL
        CHECK (card_type IN ('RECOGNITION', 'WRITING', 'TONE')),
    state         VARCHAR(20) NOT NULL DEFAULT 'NEW'
        CHECK (state IN ('NEW', 'LEARNING', 'REVIEW', 'RELEARNING')),
    stability     DOUBLE PRECISION,
    difficulty    DOUBLE PRECISION,
    due           TIMESTAMPTZ NOT NULL DEFAULT now(),
    reps          INT         NOT NULL DEFAULT 0,
    lapses        INT         NOT NULL DEFAULT 0,
    last_review   TIMESTAMPTZ,
    UNIQUE (word_id, card_type)   -- recognising 学 and writing 学 are tracked separately
);
CREATE INDEX idx_review_card_due ON review_card (due);

CREATE TABLE review_log (
    id              BIGSERIAL PRIMARY KEY,
    card_id         BIGINT      NOT NULL REFERENCES review_card (id) ON DELETE CASCADE,
    rating          SMALLINT    NOT NULL CHECK (rating BETWEEN 1 AND 4),  -- Again/Hard/Good/Easy
    state_before    VARCHAR(20) NOT NULL,
    elapsed_days    INT         NOT NULL,
    scheduled_days  INT         NOT NULL,
    reviewed_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
