package me.valizadeh.practices.cache.controller;

import me.valizadeh.practices.cache.entity.Book;
import me.valizadeh.practices.cache.service.BookCacheService;
import me.valizadeh.practices.cache.service.BookService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/books")
public class BookController {
    
    private static final Logger logger = LoggerFactory.getLogger(BookController.class);
    
    private final BookCacheService bookCacheService;
    private final BookService bookService;
    
    public BookController(BookCacheService bookCacheService, BookService bookService) {
        this.bookCacheService = bookCacheService;
        this.bookService = bookService;
    }
    
    /**
     * Get book by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<Book> getBookById(@PathVariable Long id) {
        logger.info("Request to get book by ID: {}", id);
        
        Optional<Book> book = bookCacheService.getBookById(id);
        if (book.isPresent()) {
            return ResponseEntity.ok(book.get());
        } else {
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * Get book by ISBN
     */
    @GetMapping("/isbn/{isbn}")
    public ResponseEntity<Book> getBookByIsbn(@PathVariable String isbn) {
        logger.info("Request to get book by ISBN: {}", isbn);
        
        Optional<Book> book = bookCacheService.getBookByIsbn(isbn);
        if (book.isPresent()) {
            return ResponseEntity.ok(book.get());
        } else {
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * Get all books
     */
    @GetMapping
    public ResponseEntity<List<Book>> getAllBooks() {
        logger.info("Request to get all books");
        
        List<Book> books = bookService.getAllBooks();
        return ResponseEntity.ok(books);
    }
    
    /**
     * Get popular books (Hot Key - demonstrates Cache Breakdown protection)
     */
    @GetMapping("/popular")
    public ResponseEntity<List<Book>> getPopularBooks() {
        logger.info("Request to get popular books");
        
        List<Book> books = bookCacheService.getPopularBooks();
        return ResponseEntity.ok(books);
    }
    
    /**
     * Get bestseller books (Hot Key - demonstrates Cache Breakdown protection)
     */
    @GetMapping("/bestsellers")
    public ResponseEntity<List<Book>> getBestsellerBooks() {
        logger.info("Request to get bestseller books");
        
        List<Book> books = bookCacheService.getBestsellerBooks();
        return ResponseEntity.ok(books);
    }
    
    /**
     * Search books by keyword
     */
    @GetMapping("/search")
    public ResponseEntity<List<Book>> searchBooks(@RequestParam String keyword) {
        logger.info("Request to search books with keyword: {}", keyword);
        
        if (keyword == null || keyword.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        
        List<Book> books = bookCacheService.searchBooks(keyword.trim());
        return ResponseEntity.ok(books);
    }
    
    /**
     * Get books by author
     */
    @GetMapping("/author/{author}")
    public ResponseEntity<List<Book>> getBooksByAuthor(@PathVariable String author) {
        logger.info("Request to get books by author: {}", author);
        
        List<Book> books = bookService.getBooksByAuthor(author);
        return ResponseEntity.ok(books);
    }
    
    /**
     * Get books by genre
     */
    @GetMapping("/genre/{genre}")
    public ResponseEntity<List<Book>> getBooksByGenre(@PathVariable String genre) {
        logger.info("Request to get books by genre: {}", genre);
        
        List<Book> books = bookService.getBooksByGenre(genre);
        return ResponseEntity.ok(books);
    }
    
    /**
     * Get books by publication year
     */
    @GetMapping("/year/{year}")
    public ResponseEntity<List<Book>> getBooksByYear(@PathVariable Integer year) {
        logger.info("Request to get books by publication year: {}", year);
        
        List<Book> books = bookService.getBooksByPublicationYear(year);
        return ResponseEntity.ok(books);
    }
    
    /**
     * Get books by price range
     */
    @GetMapping("/price")
    public ResponseEntity<List<Book>> getBooksByPriceRange(
            @RequestParam Double minPrice, 
            @RequestParam Double maxPrice) {
        logger.info("Request to get books by price range: {} - {}", minPrice, maxPrice);
        
        if (minPrice < 0 || maxPrice < 0 || minPrice > maxPrice) {
            return ResponseEntity.badRequest().build();
        }
        
        List<Book> books = bookService.getBooksByPriceRange(minPrice, maxPrice);
        return ResponseEntity.ok(books);
    }
    
    /**
     * Create a new book
     */
    @PostMapping
    public ResponseEntity<Book> createBook(@RequestBody Book book) {
        logger.info("Request to create new book: {}", book.getTitle());
        
        Book savedBook = bookService.saveBook(book);
        return ResponseEntity.ok(savedBook);
    }
    
    /**
     * Update an existing book
     */
    @PutMapping("/{id}")
    public ResponseEntity<Book> updateBook(@PathVariable Long id, @RequestBody Book book) {
        logger.info("Request to update book with ID: {}", id);
        
        book.setId(id);
        Book updatedBook = bookService.updateBook(book);
        
        // Invalidate cache after update
        bookCacheService.invalidateBookCache(id);
        
        return ResponseEntity.ok(updatedBook);
    }
    
    /**
     * Delete a book
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBook(@PathVariable Long id) {
        logger.info("Request to delete book with ID: {}", id);
        
        bookService.deleteBook(id);
        
        // Invalidate cache after deletion
        bookCacheService.invalidateBookCache(id);
        
        return ResponseEntity.noContent().build();
    }
}
