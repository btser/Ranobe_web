package main.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.SQLRestriction;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "chapters")
@SQLRestriction("is_deleted = false")
public class Chapter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "chapter_id")
    private int chapterId;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "volume_id")
    private Volume volume;

    @Column(columnDefinition = "TEXT")
    private String context;
    @com.fasterxml.jackson.annotation.JsonIgnore
    @OneToMany(mappedBy = "chapter", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ChapterImage> images = new ArrayList<>();

    public List<ChapterImage> getImages() { return images; }
    public void setImages(List<ChapterImage> images) { this.images = images; }
    public void addImage(ChapterImage image) { this.images.add(image); }

    @Column(name = "is_deleted")
    private boolean isDeleted = false;

    @Column(name = "sort_order")
    private int sortOrder = 0;

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    private String title;

    public Chapter() {}

    public Chapter(int chapterId, Volume volume, String context) {
        this.chapterId = chapterId;
        this.volume = volume;
        this.context = context;
    }
    public String getTitle() { return this.title;}
    public int getChapterId() { return chapterId; }
    public Volume getVolume() { return volume; }
    public String getContext() { return context; }

    public void setVolume(Volume volume) { this.volume = volume; }
    public void setTitle(String title) { this.title = title;}
    public void setContext(String context) { this.context = context; }
    public boolean isDeleted() { return isDeleted; }
    public void setDeleted(boolean deleted) { isDeleted = deleted; }
}