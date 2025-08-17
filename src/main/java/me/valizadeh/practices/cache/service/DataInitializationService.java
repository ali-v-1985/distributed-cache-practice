package me.valizadeh.practices.cache.service;

import me.valizadeh.practices.cache.entity.Book;
import me.valizadeh.practices.cache.repository.BookRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * Service to initialize sample data for cache testing
 */
@Service
public class DataInitializationService implements CommandLineRunner {
    
    private static final Logger logger = LoggerFactory.getLogger(DataInitializationService.class);
    
    private final BookRepository bookRepository;
    private final BloomFilterService bloomFilterService;
    
    public DataInitializationService(BookRepository bookRepository, BloomFilterService bloomFilterService) {
        this.bookRepository = bookRepository;
        this.bloomFilterService = bloomFilterService;
    }
    
    @Override
    public void run(String... args) throws Exception {
        if (bookRepository.count() == 0) {
            logger.info("Initializing sample book data...");
            initializeSampleData();
            logger.info("Sample data initialization completed");
        } else {
            logger.info("Sample data already exists, skipping initialization");
            // Add existing books to bloom filter
            addExistingBooksToBloomFilter();
        }
    }
    
    private void initializeSampleData() {
        List<Book> sampleBooks = Arrays.asList(
            // Popular books (for cache breakdown testing)
            createBook("The Art of Cache Design", "Alex Johnson", "978-0123456789", "Technology", 2023, 29.99, 150, true, false),
            createBook("Distributed Systems Fundamentals", "Sarah Chen", "978-0234567890", "Technology", 2022, 34.99, 200, true, false),
            createBook("Redis in Action", "Mike Thompson", "978-0345678901", "Technology", 2021, 39.99, 120, true, false),
            
            // Bestseller books (for cache breakdown testing)
            createBook("Clean Code", "Robert Martin", "978-0456789012", "Programming", 2008, 45.99, 300, false, true),
            createBook("System Design Interview", "Alex Xu", "978-0567890123", "Technology", 2020, 35.99, 250, false, true),
            createBook("Designing Data-Intensive Applications", "Martin Kleppmann", "978-0678901234", "Technology", 2017, 49.99, 180, false, true),
            
            // Regular books
            createBook("Java Concurrency in Practice", "Brian Goetz", "978-0789012345", "Programming", 2006, 42.99, 100, false, false),
            createBook("Effective Java", "Joshua Bloch", "978-0890123456", "Programming", 2017, 38.99, 140, false, false),
            createBook("Spring Boot in Action", "Craig Walls", "978-0901234567", "Programming", 2015, 36.99, 90, false, false),
            createBook("Microservices Patterns", "Chris Richardson", "978-1012345678", "Architecture", 2018, 44.99, 110, false, false),
            
            // Books for search testing
            createBook("Learning Docker", "Pethuru Raj", "978-1123456789", "DevOps", 2020, 32.99, 80, false, false),
            createBook("Kubernetes Up and Running", "Kelsey Hightower", "978-1234567890", "DevOps", 2019, 41.99, 95, false, false),
            createBook("The Phoenix Project", "Gene Kim", "978-1345678901", "Management", 2013, 28.99, 160, false, false),
            createBook("Site Reliability Engineering", "Google", "978-1456789012", "Operations", 2016, 47.99, 130, false, false),
            
            // Fiction books
            createBook("The Hitchhiker's Guide to the Galaxy", "Douglas Adams", "978-1567890123", "Science Fiction", 1979, 15.99, 220, false, false),
            createBook("1984", "George Orwell", "978-1678901234", "Dystopian", 1949, 13.99, 300, false, false),
            createBook("Dune", "Frank Herbert", "978-1789012345", "Science Fiction", 1965, 18.99, 180, false, false),
            
            // Books with various publication years for year-based searching
            createBook("The Pragmatic Programmer", "Andrew Hunt", "978-1890123456", "Programming", 1999, 37.99, 140, false, false),
            createBook("Code Complete", "Steve McConnell", "978-1901234567", "Programming", 2004, 43.99, 110, false, false),
            createBook("The Mythical Man-Month", "Frederick Brooks", "978-2012345678", "Management", 1975, 31.99, 90, false, false),
            
            // Low stock books
            createBook("Advanced Cache Strategies", "Dr. Elizabeth Wilson", "978-2123456789", "Technology", 2024, 55.99, 5, false, false),
            createBook("Cache Performance Tuning", "Michael Rodriguez", "978-2234567890", "Technology", 2023, 48.99, 3, false, false)
        );
        
        bookRepository.saveAll(sampleBooks);
        logger.info("Saved {} sample books to database", sampleBooks.size());
        
        // Add all books to bloom filter
        for (Book book : sampleBooks) {
            bloomFilterService.add("book:" + book.getId());
            if (book.getIsbn() != null) {
                bloomFilterService.add("book:isbn:" + book.getIsbn());
            }
        }
        
        logger.info("Added {} books to bloom filter", sampleBooks.size());
    }
    
    private void addExistingBooksToBloomFilter() {
        List<Book> existingBooks = bookRepository.findAll();
        for (Book book : existingBooks) {
            bloomFilterService.add("book:" + book.getId());
            if (book.getIsbn() != null) {
                bloomFilterService.add("book:isbn:" + book.getIsbn());
            }
        }
        logger.info("Added {} existing books to bloom filter", existingBooks.size());
    }
    
    private Book createBook(String title, String author, String isbn, String genre, 
                           Integer publicationYear, Double price, Integer stockQuantity,
                           Boolean isPopular, Boolean isBestseller) {
        Book book = new Book(title, author, isbn, genre, publicationYear, price, stockQuantity);
        book.setIsPopular(isPopular);
        book.setIsBestseller(isBestseller);
        return book;
    }
}
