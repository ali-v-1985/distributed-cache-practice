package me.valizadeh.practices.cache.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import me.valizadeh.practices.cache.config.CacheProperties;
import me.valizadeh.practices.cache.entity.Book;
import me.valizadeh.practices.cache.repository.BookRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Book Cache Service that demonstrates various cache problems and their solutions
 */
@Service
public class BookCacheService {
    
    private static final Logger logger = LoggerFactory.getLogger(BookCacheService.class);
    
    private final BookRepository bookRepository;
    private final RedisTemplate<String, Book> bookRedisTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisBloomFilterService bloomFilterService;
    private final CacheProperties cacheProperties;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    
    // Cache key prefixes
    private static final String BOOK_CACHE_PREFIX = "book:";
    private static final String BOOK_SEARCH_PREFIX = "book:search:";
    private static final String POPULAR_BOOKS_KEY = "book:popular:all";
    private static final String BESTSELLER_BOOKS_KEY = "book:bestseller:all";
    private static final String NULL_VALUE_PREFIX = "null:";
    
        public BookCacheService(BookRepository bookRepository,
                           RedisTemplate<String, Book> bookRedisTemplate,
                           RedisTemplate<String, Object> redisTemplate,
                           RedisBloomFilterService bloomFilterService,
                           CacheProperties cacheProperties) {
        this.bookRepository = bookRepository;
        this.bookRedisTemplate = bookRedisTemplate;
        this.redisTemplate = redisTemplate;
        this.bloomFilterService = bloomFilterService;
        this.cacheProperties = cacheProperties;
    }
    
    /**
     * Get book by ID with cache
     * Demonstrates: Thunder Herd Protection, Cache Penetration Protection, Cache Breakdown Protection
     */
    @CircuitBreaker(name = "redis", fallbackMethod = "getBookByIdFallback")
    public Optional<Book> getBookById(Long id) {
        String cacheKey = BOOK_CACHE_PREFIX + id;
        
        // Check bloom filter first (Cache Penetration Protection)
        if (!bloomFilterService.mightContain(cacheKey)) {
            logger.debug("Bloom filter indicates book {} doesn't exist", id);
            return Optional.empty();
        }
        
        // Check null cache (Cache Penetration Protection)
        if (cacheProperties.getPenetration().getNullCache().isEnabled() && isInNullCache(cacheKey)) {
            logger.debug("Book {} found in null cache", id);
            return Optional.empty();
        }
        
        try {
            // Try to get from cache
            Book cachedBook = bookRedisTemplate.opsForValue().get(cacheKey);
            if (cachedBook != null) {
                logger.debug("Book {} found in cache", id);
                return Optional.of(cachedBook);
            }
            
            // Cache miss - get from database
            logger.debug("Cache miss for book {}, fetching from database", id);
            Optional<Book> bookOpt = bookRepository.findById(id);
            
            if (bookOpt.isPresent()) {
                Book book = bookOpt.get();
                // Cache the book with appropriate TTL
                cacheBook(cacheKey, book);
                // Add to bloom filter
                bloomFilterService.add(cacheKey);
                return Optional.of(book);
            } else {
                // Cache null result to prevent cache penetration
                if (cacheProperties.getPenetration().getNullCache().isEnabled()) {
                    cacheNullValue(cacheKey);
                }
                return Optional.empty();
            }
            
        } catch (Exception e) {
            logger.error("Redis error when getting book {}: {}", id, e.getMessage());
            // Fallback to database
            return bookRepository.findById(id);
        }
    }
    
    /**
     * Fallback method for circuit breaker
     */
    public Optional<Book> getBookByIdFallback(Long id, Exception ex) {
        logger.warn("Circuit breaker activated for getBookById, falling back to database. Error: {}", ex.getMessage());
        return bookRepository.findById(id);
    }
    
    /**
     * Get book by ISBN with cache
     */
    @CircuitBreaker(name = "redis", fallbackMethod = "getBookByIsbnFallback")
    public Optional<Book> getBookByIsbn(String isbn) {
        String cacheKey = BOOK_CACHE_PREFIX + "isbn:" + isbn;
        
        if (!bloomFilterService.mightContain(cacheKey)) {
            logger.debug("Bloom filter indicates book with ISBN {} doesn't exist", isbn);
            return Optional.empty();
        }
        
        if (cacheProperties.getPenetration().getNullCache().isEnabled() && isInNullCache(cacheKey)) {
            logger.debug("Book with ISBN {} found in null cache", isbn);
            return Optional.empty();
        }
        
        try {
            Book cachedBook = bookRedisTemplate.opsForValue().get(cacheKey);
            if (cachedBook != null) {
                logger.debug("Book with ISBN {} found in cache", isbn);
                return Optional.of(cachedBook);
            }
            
            logger.debug("Cache miss for book with ISBN {}, fetching from database", isbn);
            Optional<Book> bookOpt = bookRepository.findByIsbn(isbn);
            
            if (bookOpt.isPresent()) {
                Book book = bookOpt.get();
                cacheBook(cacheKey, book);
                bloomFilterService.add(cacheKey);
                return Optional.of(book);
            } else {
                if (cacheProperties.getPenetration().getNullCache().isEnabled()) {
                    cacheNullValue(cacheKey);
                }
                return Optional.empty();
            }
            
        } catch (Exception e) {
            logger.error("Redis error when getting book by ISBN {}: {}", isbn, e.getMessage());
            return bookRepository.findByIsbn(isbn);
        }
    }
    
    public Optional<Book> getBookByIsbnFallback(String isbn, Exception ex) {
        logger.warn("Circuit breaker activated for getBookByIsbn, falling back to database. Error: {}", ex.getMessage());
        return bookRepository.findByIsbn(isbn);
    }
    
    /**
     * Get popular books (Hot Key - Cache Breakdown Protection)
     */
    @CircuitBreaker(name = "redis", fallbackMethod = "getPopularBooksFallback")
    public List<Book> getPopularBooks() {
        try {
            @SuppressWarnings("unchecked")
            List<Book> cachedBooks = (List<Book>) redisTemplate.opsForValue().get(POPULAR_BOOKS_KEY);
            if (cachedBooks != null) {
                logger.debug("Popular books found in cache");
                return cachedBooks;
            }
            
            logger.debug("Cache miss for popular books, fetching from database");
            List<Book> books = bookRepository.findByIsPopularTrue();
            
            // Cache without expiry for hot keys (Cache Breakdown Protection)
            if (cacheProperties.getBreakdown().isNoExpiry()) {
                redisTemplate.opsForValue().set(POPULAR_BOOKS_KEY, books);
                logger.debug("Cached popular books without expiry (hot key protection)");
            } else {
                long ttl = calculateTtlWithJitter(Duration.ofHours(1).toMillis());
                redisTemplate.opsForValue().set(POPULAR_BOOKS_KEY, books, ttl, TimeUnit.MILLISECONDS);
                logger.debug("Cached popular books with TTL: {} ms", ttl);
            }
            
            return books;
            
        } catch (Exception e) {
            logger.error("Redis error when getting popular books: {}", e.getMessage());
            return bookRepository.findByIsPopularTrue();
        }
    }
    
    public List<Book> getPopularBooksFallback(Exception ex) {
        logger.warn("Circuit breaker activated for getPopularBooks, falling back to database. Error: {}", ex.getMessage());
        return bookRepository.findByIsPopularTrue();
    }
    
    /**
     * Get bestseller books (Hot Key - Cache Breakdown Protection)
     */
    @CircuitBreaker(name = "redis", fallbackMethod = "getBestsellerBooksFallback")
    public List<Book> getBestsellerBooks() {
        try {
            @SuppressWarnings("unchecked")
            List<Book> cachedBooks = (List<Book>) redisTemplate.opsForValue().get(BESTSELLER_BOOKS_KEY);
            if (cachedBooks != null) {
                logger.debug("Bestseller books found in cache");
                return cachedBooks;
            }
            
            logger.debug("Cache miss for bestseller books, fetching from database");
            List<Book> books = bookRepository.findByIsBestsellerTrue();
            
            // Cache without expiry for hot keys (Cache Breakdown Protection)
            if (cacheProperties.getBreakdown().isNoExpiry()) {
                redisTemplate.opsForValue().set(BESTSELLER_BOOKS_KEY, books);
                logger.debug("Cached bestseller books without expiry (hot key protection)");
            } else {
                long ttl = calculateTtlWithJitter(Duration.ofHours(1).toMillis());
                redisTemplate.opsForValue().set(BESTSELLER_BOOKS_KEY, books, ttl, TimeUnit.MILLISECONDS);
                logger.debug("Cached bestseller books with TTL: {} ms", ttl);
            }
            
            return books;
            
        } catch (Exception e) {
            logger.error("Redis error when getting bestseller books: {}", e.getMessage());
            return bookRepository.findByIsBestsellerTrue();
        }
    }
    
    public List<Book> getBestsellerBooksFallback(Exception ex) {
        logger.warn("Circuit breaker activated for getBestsellerBooks, falling back to database. Error: {}", ex.getMessage());
        return bookRepository.findByIsBestsellerTrue();
    }
    
    /**
     * Search books by keyword with cache
     */
    @CircuitBreaker(name = "redis", fallbackMethod = "searchBooksFallback")
    public List<Book> searchBooks(String keyword) {
        String cacheKey = BOOK_SEARCH_PREFIX + keyword.toLowerCase();
        
        try {
            @SuppressWarnings("unchecked")
            List<Book> cachedBooks = (List<Book>) redisTemplate.opsForValue().get(cacheKey);
            if (cachedBooks != null) {
                logger.debug("Search results for '{}' found in cache", keyword);
                return cachedBooks;
            }
            
            logger.debug("Cache miss for search '{}', fetching from database", keyword);
            List<Book> books = bookRepository.searchByKeyword(keyword);
            
            // Cache search results with TTL and jitter (Thunder Herd Protection)
            long ttl = calculateTtlWithJitter(Duration.ofMinutes(30).toMillis());
            redisTemplate.opsForValue().set(cacheKey, books, ttl, TimeUnit.MILLISECONDS);
            logger.debug("Cached search results for '{}' with TTL: {} ms", keyword, ttl);
            
            return books;
            
        } catch (Exception e) {
            logger.error("Redis error when searching books with keyword '{}': {}", keyword, e.getMessage());
            return bookRepository.searchByKeyword(keyword);
        }
    }
    
    public List<Book> searchBooksFallback(String keyword, Exception ex) {
        logger.warn("Circuit breaker activated for searchBooks, falling back to database. Error: {}", ex.getMessage());
        return bookRepository.searchByKeyword(keyword);
    }
    
    /**
     * Cache a book with appropriate TTL
     */
    private void cacheBook(String cacheKey, Book book) {
        // Check if this is a hot key that should not expire
        if (cacheProperties.getBreakdown().isNoExpiry() && isHotKey(cacheKey)) {
            bookRedisTemplate.opsForValue().set(cacheKey, book);
            logger.debug("Cached book {} without expiry (hot key)", cacheKey);
        } else {
            // Apply Thunder Herd protection with jittered TTL
            long ttl = calculateTtlWithJitter(Duration.ofMinutes(10).toMillis());
            bookRedisTemplate.opsForValue().set(cacheKey, book, ttl, TimeUnit.MILLISECONDS);
            logger.debug("Cached book {} with TTL: {} ms", cacheKey, ttl);
        }
    }
    
    /**
     * Cache null value to prevent cache penetration
     */
    private void cacheNullValue(String cacheKey) {
        String nullKey = NULL_VALUE_PREFIX + cacheKey;
        redisTemplate.opsForValue().set(nullKey, "NULL", cacheProperties.getPenetration().getNullCache().getTtl(), TimeUnit.MILLISECONDS);
        logger.debug("Cached null value for key {} with TTL: {} ms", cacheKey, cacheProperties.getPenetration().getNullCache().getTtl());
    }
    
    /**
     * Check if key is in null cache
     */
    private boolean isInNullCache(String cacheKey) {
        String nullKey = NULL_VALUE_PREFIX + cacheKey;
        return redisTemplate.hasKey(nullKey);
    }
    
    /**
     * Check if a cache key is considered a hot key
     */
    private boolean isHotKey(String cacheKey) {
        return cacheKey.contains("popular") || cacheKey.contains("bestseller");
    }
    
    /**
     * Calculate TTL with jitter to prevent Thunder Herd problem
     */
    private long calculateTtlWithJitter(long baseTtl) {
        if (!cacheProperties.getThunderHerd().isEnabled()) {
            return baseTtl;
        }
        
        // Add random jitter (0 to cacheProperties.getThunderHerd().getJitterPercentage()% of base TTL)
        int jitterRange = (int) (baseTtl * cacheProperties.getThunderHerd().getJitterPercentage() / 100);
        int jitter = SECURE_RANDOM.nextInt(jitterRange + 1);
        long finalTtl = baseTtl + jitter;
        
        logger.debug("Applied jitter: base TTL {} ms, jitter {} ms, final TTL {} ms", 
                baseTtl, jitter, finalTtl);
        
        return finalTtl;
    }
    
    /**
     * Invalidate cache for a book
     */
    public void invalidateBookCache(Long id) {
        String cacheKey = BOOK_CACHE_PREFIX + id;
        try {
            bookRedisTemplate.delete(cacheKey);
            // Also remove from null cache if exists
            String nullKey = NULL_VALUE_PREFIX + cacheKey;
            redisTemplate.delete(nullKey);
            logger.debug("Invalidated cache for book {}", id);
        } catch (Exception e) {
            logger.error("Error invalidating cache for book {}: {}", id, e.getMessage());
        }
    }
    
    /**
     * Invalidate all caches (useful for testing cache crash scenarios)
     */
    public void invalidateAllCaches() {
        try {
            // This would typically be done more selectively in production
            redisTemplate.getConnectionFactory().getConnection().flushDb();
            logger.warn("Invalidated all caches - simulating cache crash");
        } catch (Exception e) {
            logger.error("Error invalidating all caches: {}", e.getMessage());
        }
    }
}
