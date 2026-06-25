
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

enum Status { PUBLIC, PRIVATE }

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

    @ManyToOne
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User uploader;

    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "product_id")
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

    public int getProductId() { return productId; }
    public String getTitle() { return title; }
    public Category getCategory() { return category; }
    public List<Volume> getVolumes() { return volumes; }
    
    public void addVolume(Volume volume) {
        this.volumes.add(volume);
    }
}