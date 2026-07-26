package main.entity;


import jakarta.persistence.*;

enum SortBy { View, Comment, Vote, Word, All }

@Entity
@Table(name = "ranking")
public class Ranking {
    @Id
    @Column(name = "product_id")
    private int productId;

    @Enumerated(EnumType.STRING)
    @Column(name = "sort_by")
    private SortBy sortBy;

    @Column(name = "total_points")
    private float totalPoints;

    @Column(name = "total_cmts")
    private int totalCmts;

    @Column(name = "total_views")
    private int totalViews;

    @Column(name = "avg_vote")
    private float avgVote;

    @Column(name = "total_words")
    private int totalWords;

    public Ranking() {}

    public Ranking(int productId) {
        this.productId = productId;
        this.sortBy = SortBy.All;
        this.totalPoints = 0.0f;
        this.totalCmts = 0;
        this.totalViews = 0;
        this.avgVote = 0.0f;
        this.totalWords = 0;
    }

    public int getProductId() { return productId; }
    public SortBy getSortBy() { return sortBy; }
    public void setSortBy(SortBy sortBy) { this.sortBy = sortBy; }
    public int getTotalViews() { return totalViews; }
    public void setTotalViews(int totalViews) { this.totalViews = totalViews; }
    public int getTotalCmts() { return totalCmts; }
    public void setTotalCmts(int totalCmts) { this.totalCmts = totalCmts; }
}