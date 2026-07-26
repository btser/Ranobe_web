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

    @Column(name = "product_id")
    private int productId;

    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "volume_id")
    private List<Chapter> chapters = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(name = "is_deleted")
    private boolean isDeleted = false;

    public Volume() {}

    public Volume(int volumeId, int productId) {
        this.volumeId = volumeId;
        this.productId = productId;
    }
    
    public boolean isDeleted() { return isDeleted;}
    public int getVolumeId() { return volumeId; }
    public int getProductId() { return productId; }
    public List<Chapter> getChapters() { return chapters; }

    public void addChapter(Chapter chapter) {
        this.chapters.add(chapter);
    }

    public void setDeleted(boolean deleted) {isDeleted = deleted;}
}