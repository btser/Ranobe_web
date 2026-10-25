package main.entity;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "volumes")
public class Volume {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "volume_id")
    private int volumeId;

    @OneToMany(mappedBy = "volume", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Chapter> chapters = new ArrayList<>();

    @com.fasterxml.jackson.annotation.JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(name = "is_deleted")
    private boolean isDeleted = false;

    @Column(name = "sort_order")
    private int sortOrder = 0;

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    private String title;

    public Volume() {}
    
    public Volume(int volumeId, Product product) {
        this.volumeId = volumeId;
        this.product = product;
    }

    @Column(name = "cover_image", columnDefinition = "TEXT")
    private String coverImage;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    public String getSummary() { return summary; }
    public String getCoverImage() { return coverImage; }
    public boolean isDeleted() { return isDeleted; }
    public int getVolumeId() { return volumeId; }
    public Product getProduct() { return product; }
    public List<Chapter> getChapters() { return chapters; }
    public String getTitle() { return this.title;}
    /** Đuôi tiêu đề dùng trong link. */
    public String getSlug() { return main.util.Slugs.slug(title, "tap"); }

    public void addChapter(Chapter chapter) {this.chapters.add(chapter);}
    public void setTitle(String title) { this.title = title;}
    public void setVolumeId(int volumeId) { this.volumeId = volumeId; }
    public void setProduct(Product product) { this.product = product; }
    public void setChapters(List<Chapter> chapters) { this.chapters = chapters; }
    public void setDeleted(boolean deleted) { isDeleted = deleted; }
    public void setCoverImage(String coverImage) { this.coverImage = coverImage; }
    public void setSummary(String summary) { this.summary = summary; }
}