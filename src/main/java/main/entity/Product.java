package main.entity;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import java.time.LocalDateTime;

@Entity
@Table(name = "products")
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_id")
    private int productId;

    private String title;
    private String author;
    private String artist;

    @Column(name = "cover_image", columnDefinition = "TEXT")
    private String coverImage;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Enumerated(EnumType.STRING)
    private Status status;

    @Column(name = "is_deleted")
    private boolean isDeleted = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User uploader;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL)
    private List<Volume> volumes = new ArrayList<>();

    public Product() {}

    public Product(int productId, String title, String author, String artist, Status status, Category category, User uploader) {
        this.productId = productId;
        this.title = title;
        this.author = author;
        this.artist = artist;
        this.status = status;
        this.category = category;
        this.uploader = uploader;
    }

    public void addVolume(Volume volume) {
        this.volumes.add(volume);
    }

    

    public int getProductId() { return productId; }
    public String getTitle() { return title; }
    public Category getCategory() { return category; }
    public List<Volume> getVolumes() { return volumes; }
    public boolean isDeleted() { return isDeleted;}
    public String getAuthor() { return author; }
    public String getArtist() { return artist; }
    public Status getStatus() { return status; }
    public User getUploader() { return uploader; }
    public String getCoverImage() { return coverImage; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }


    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public void setDeleted(boolean deleted) {isDeleted = deleted;}
    public void setAuthor(String author) {this.author = author;}
    public void setTitle(String title) {this.title = title;}
    public void setArtist(String artist) {this.artist = artist;}
    public void setStatus(Status status) {this.status = status;}
    public void setUploader(User uploader) {this.uploader = uploader;}
    public void setCategory(Category category) {this.category = category;}
    public void setCoverImage(String coverImage) { this.coverImage = coverImage; }
}