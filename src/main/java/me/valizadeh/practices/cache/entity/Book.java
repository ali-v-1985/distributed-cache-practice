package me.valizadeh.practices.cache.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = {"id", "isbn"})
@Entity
@Table(name = "books")
public class Book {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String title;
    
    @Column(nullable = false)
    private String author;
    
    @Column(unique = true, nullable = false)
    private String isbn;
    
    @Column(nullable = false)
    private String genre;
    
    @Column(name = "publication_year")
    private Integer publicationYear;
    
    @Column(nullable = false)
    private Double price;
    
    @Column(name = "stock_quantity")
    private Integer stockQuantity;
    
    @Column(name = "is_popular")
    private Boolean isPopular = false;
    
    @Column(name = "is_bestseller")
    private Boolean isBestseller = false;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    public Book(String title, String author, String isbn, String genre, 
                Integer publicationYear, Double price, Integer stockQuantity) {
        this.title = title;
        this.author = author;
        this.isbn = isbn;
        this.genre = genre;
        this.publicationYear = publicationYear;
        this.price = price;
        this.stockQuantity = stockQuantity;
        this.isPopular = false;
        this.isBestseller = false;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
