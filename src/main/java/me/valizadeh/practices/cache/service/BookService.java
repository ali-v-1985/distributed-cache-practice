package me.valizadeh.practices.cache.service;

import me.valizadeh.practices.cache.entity.Book;
import me.valizadeh.practices.cache.repository.BookRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Book Service for database operations without cache
 */
@Service
@Transactional
public class BookService {
    
    private static final Logger logger = LoggerFactory.getLogger(BookService.class);
    
    private final BookRepository bookRepository;
    private final RedisBloomFilterService bloomFilterService;
    
    public BookService(BookRepository bookRepository, RedisBloomFilterService bloomFilterService) {
        this.bookRepository = bookRepository;
        this.bloomFilterService = bloomFilterService;
    }
    
    public List<Book> getAllBooks() {
        logger.debug("Fetching all books from database");
        return bookRepository.findAll();
    }
    
    public List<Book> getBooksByAuthor(String author) {
        logger.debug("Fetching books by author: {}", author);
        return bookRepository.findByAuthor(author);
    }
    
    public List<Book> getBooksByGenre(String genre) {
        logger.debug("Fetching books by genre: {}", genre);
        return bookRepository.findByGenre(genre);
    }
    
    public List<Book> getBooksByPublicationYear(Integer year) {
        logger.debug("Fetching books by publication year: {}", year);
        return bookRepository.findByPublicationYear(year);
    }
    
    public List<Book> getBooksByPriceRange(Double minPrice, Double maxPrice) {
        logger.debug("Fetching books by price range: {} - {}", minPrice, maxPrice);
        return bookRepository.findByPriceRange(minPrice, maxPrice);
    }
    
    /**
     * Save a new book and add it to the bloom filter
     * @param book the book to save
     * @return the saved book
     * @throws IllegalArgumentException if book is null
     */
    public Book saveBook(Book book) {
        if (book == null) {
            throw new IllegalArgumentException("Book cannot be null");
        }
        
        logger.debug("Saving new book: {}", book.getTitle());
        Book savedBook = bookRepository.save(book);
        
        // Add to bloom filter
        bloomFilterService.add("book:" + savedBook.getId());
        if (savedBook.getIsbn() != null) {
            bloomFilterService.add("book:isbn:" + savedBook.getIsbn());
        }
        
        return savedBook;
    }
    
    public Book updateBook(Book book) {
        logger.debug("Updating book: {}", book.getTitle());
        return bookRepository.save(book);
    }
    
    public void deleteBook(Long id) {
        logger.debug("Deleting book with ID: {}", id);
        
        // Remove from bloom filter before deleting from database
        bloomFilterService.remove("book:" + id);
        
        // Also try to remove ISBN-based key if book exists
        bookRepository.findById(id).ifPresent(book -> {
            if (book.getIsbn() != null) {
                bloomFilterService.remove("book:isbn:" + book.getIsbn());
            }
        });
        
        bookRepository.deleteById(id);
    }
    
    public Long getAvailableBooksCount() {
        return bookRepository.countAvailableBooks();
    }
    
    public List<Book> getAvailableBooks() {
        return bookRepository.findAvailableBooksOrderByStock();
    }
}
