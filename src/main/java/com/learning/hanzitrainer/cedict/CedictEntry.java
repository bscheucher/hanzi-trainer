package com.learning.hanzitrainer.cedict;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

/** Read-only reference dictionary entry imported from CC-CEDICT. */
@Entity
@Table(name = "cedict_entry")
public class CedictEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String simplified;

    @Column(nullable = false)
    private String traditional;

    @Column(name = "pinyin_numbered", nullable = false)
    private String pinyinNumbered;

    @Column(nullable = false)
    private String pinyin;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "text[]")
    private List<String> definitions;

    protected CedictEntry() {
    }

    public CedictEntry(String simplified, String traditional, String pinyinNumbered, String pinyin,
                       List<String> definitions) {
        this.simplified = simplified;
        this.traditional = traditional;
        this.pinyinNumbered = pinyinNumbered;
        this.pinyin = pinyin;
        this.definitions = definitions;
    }

    public Long getId() { return id; }
    public String getSimplified() { return simplified; }
    public String getTraditional() { return traditional; }
    public String getPinyinNumbered() { return pinyinNumbered; }
    public String getPinyin() { return pinyin; }
    public List<String> getDefinitions() { return definitions; }
}
