package main.entity;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

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

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
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

    public void setDeleted(boolean deleted) {isDeleted = deleted;}
    public void setAuthor(String author) {this.author = author;}
    public void setTitle(String title) {this.title = title;}
    public void setArtist(String artist) {this.artist = artist;}
    public void setStatus(Status status) {this.status = status;}
}