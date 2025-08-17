package me.valizadeh.practices.cache.repository;

import me.valizadeh.practices.cache.entity.Book;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BookRepository extends JpaRepository<Book, Long> {
    
    Optional<Book> findByIsbn(String isbn);
    
    List<Book> findByAuthor(String author);
    
    List<Book> findByGenre(String genre);
    
    List<Book> findByIsPopularTrue();
    
    List<Book> findByIsBestsellerTrue();
    
    @Query("SELECT b FROM Book b WHERE b.title LIKE %:keyword% OR b.author LIKE %:keyword%")
    List<Book> searchByKeyword(@Param("keyword") String keyword);
    
    @Query("SELECT b FROM Book b WHERE b.publicationYear = :year")
    List<Book> findByPublicationYear(@Param("year") Integer year);
    
    @Query("SELECT b FROM Book b WHERE b.price BETWEEN :minPrice AND :maxPrice")
    List<Book> findByPriceRange(@Param("minPrice") Double minPrice, @Param("maxPrice") Double maxPrice);
    
    @Query("SELECT COUNT(b) FROM Book b WHERE b.stockQuantity > 0")
    Long countAvailableBooks();
    
    @Query("SELECT b FROM Book b WHERE b.stockQuantity > 0 ORDER BY b.stockQuantity DESC")
    List<Book> findAvailableBooksOrderByStock();
}
