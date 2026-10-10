package com.learning.hanzitrainer.word;

import com.learning.hanzitrainer.cedict.CedictEntry;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/** Master vocabulary: one row per (hanzi, pinyin_numbered), shared across lessons. */
@Entity
@Table(name = "word")
public class Word {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String hanzi;

    @Column(nullable = false)
    private String pinyin;

    @Column(name = "pinyin_numbered", nullable = false)
    private String pinyinNumbered;

    @Column(name = "meaning_en")
    private String meaningEn;

    @Column(name = "meaning_de")
    private String meaningDe;

    @Column(name = "part_of_speech")
    private String partOfSpeech;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cedict_entry_id")
    private CedictEntry cedictEntry;

    @Column(nullable = false)
    private boolean verified;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Word() {
    }

    public Word(String hanzi, String pinyin, String pinyinNumbered) {
        this.hanzi = hanzi;
        this.pinyin = pinyin;
        this.pinyinNumbered = pinyinNumbered;
    }

    public Long getId() { return id; }
    public String getHanzi() { return hanzi; }
    public String getPinyin() { return pinyin; }
    public String getPinyinNumbered() { return pinyinNumbered; }
    public String getMeaningEn() { return meaningEn; }
    public void setMeaningEn(String meaningEn) { this.meaningEn = meaningEn; }
    public String getMeaningDe() { return meaningDe; }
    public void setMeaningDe(String meaningDe) { this.meaningDe = meaningDe; }
    public String getPartOfSpeech() { return partOfSpeech; }
    public void setPartOfSpeech(String partOfSpeech) { this.partOfSpeech = partOfSpeech; }
    public CedictEntry getCedictEntry() { return cedictEntry; }
    public void setCedictEntry(CedictEntry cedictEntry) { this.cedictEntry = cedictEntry; }
    public boolean isVerified() { return verified; }
    public void setVerified(boolean verified) { this.verified = verified; }
    public Instant getCreatedAt() { return createdAt; }
}
