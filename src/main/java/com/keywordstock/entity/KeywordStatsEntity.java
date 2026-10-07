package com.keywordstock.entity;

import javax.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "keyword_stats")
public class KeywordStatsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String keyword;

    @Column(name = "stat_date", nullable = false)
    private LocalDate statDate;

    private Long mentions = 0L;

    private Double score = 0.0;

    @Column(columnDefinition = "CLOB")
    private String extra;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public LocalDate getStatDate() { return statDate; }
    public void setStatDate(LocalDate statDate) { this.statDate = statDate; }
    public Long getMentions() { return mentions; }
    public void setMentions(Long mentions) { this.mentions = mentions; }
    public Double getScore() { return score; }
    public void setScore(Double score) { this.score = score; }
    public String getExtra() { return extra; }
    public void setExtra(String extra) { this.extra = extra; }
}
