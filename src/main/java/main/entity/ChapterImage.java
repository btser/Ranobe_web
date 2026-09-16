package main.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "chapter_images")
public class ChapterImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "image_id")
    private int imageId;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chapter_id")
    private Chapter chapter;

    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

    @Column(name = "sort_order")
    private int sortOrder = 0;

    public ChapterImage() {}

    public ChapterImage(Chapter chapter, String imageUrl, int sortOrder) {
        this.chapter = chapter;
        this.imageUrl = imageUrl;
        this.sortOrder = sortOrder;
    }

    public int getImageId() { return imageId; }
    public Chapter getChapter() { return chapter; }
    public String getImageUrl() { return imageUrl; }
    public int getSortOrder() { return sortOrder; }

    public void setImageId(int imageId) { this.imageId = imageId; }
    public void setChapter(Chapter chapter) { this.chapter = chapter; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}