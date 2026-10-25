package main.entity;


import jakarta.persistence.*;

@Entity
@Table(name = "categories")
public class Category {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "category_id")
    private int categoryId;

    @Column(name = "is_deleted")
    private boolean isDeleted = false;

    @Column(name = "category_name")
    private String categoryName;

    public Category() {}

    public Category(int categoryId, String categoryName) {
        this.categoryId = categoryId;
        this.categoryName = categoryName;
    }

    public int getCategoryId() { return categoryId; }
    // So sánh theo id để Set<Category> và contains() hoạt động giữa các lần truy vấn
    @Override
    public boolean equals(Object o) {
        return o instanceof Category && ((Category) o).categoryId == this.categoryId;
    }

    @Override
    public int hashCode() { return Integer.hashCode(categoryId); }

    public String getCategoryName() { return categoryName; }
    public boolean isDeleted() { return isDeleted; }
}