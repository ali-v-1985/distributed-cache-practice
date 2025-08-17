package me.valizadeh.practices.cache.service;

import me.valizadeh.practices.cache.entity.Book;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service to simulate various cache problems and their solutions
 */
@Service
public class CacheSimulationService {
    
    private static final Logger logger = LoggerFactory.getLogger(CacheSimulationService.class);
    
    private final BookCacheService bookCacheService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisBloomFilterService bloomFilterService;
    
    // Simulation statistics
    private final AtomicLong totalCacheHits = new AtomicLong(0);
    private final AtomicLong totalCacheMisses = new AtomicLong(0);
    private final AtomicLong totalDatabaseCalls = new AtomicLong(0);
    private final AtomicLong totalRedisErrors = new AtomicLong(0);
    private final AtomicInteger activeSimulations = new AtomicInteger(0);
    
    public CacheSimulationService(BookCacheService bookCacheService,
                                RedisTemplate<String, Object> redisTemplate,
                                RedisBloomFilterService bloomFilterService) {
        this.bookCacheService = bookCacheService;
        this.redisTemplate = redisTemplate;
        this.bloomFilterService = bloomFilterService;
    }
    
    /**
     * Simulate Thunder Herd Problem
     * Creates many keys with the same expiry time, then causes them all to expire simultaneously
     */
    public Map<String, Object> simulateThunderHerd(int numberOfKeys, int concurrentRequests) {
        activeSimulations.incrementAndGet();
        String simulationId = "thunder-herd-" + System.currentTimeMillis();
        
        try {
            logger.info("Starting Thunder Herd simulation: {} keys, {} concurrent requests", 
                    numberOfKeys, concurrentRequests);
            
            Map<String, Object> result = new HashMap<>();
            List<String> keysBatch = new ArrayList<>();
            
            // Phase 1: Create many keys with the same TTL (setting up for thunder herd)
            long startTime = System.currentTimeMillis();
            
            for (int i = 0; i < numberOfKeys; i++) {
                String key = "thunder-herd:book:" + i;
                Book dummyBook = createDummyBook(i, "Thunder Herd Book " + i);
                
                // Set all keys to expire at the same time (after 5 seconds)
                redisTemplate.opsForValue().set(key, dummyBook, 5, TimeUnit.SECONDS);
                keysBatch.add(key);
            }
            
            result.put("phase1_setup_time_ms", System.currentTimeMillis() - startTime);
            result.put("keys_created", numberOfKeys);
            
            // Phase 2: Wait for keys to expire
            logger.info("Waiting for keys to expire...");
            Thread.sleep(6000); // Wait 6 seconds to ensure keys have expired
            
            // Phase 3: Simulate concurrent requests hitting expired keys (Thunder Herd)
            ExecutorService executor = Executors.newFixedThreadPool(concurrentRequests);
            CountDownLatch latch = new CountDownLatch(concurrentRequests);
            AtomicInteger cacheHits = new AtomicInteger(0);
            AtomicInteger cacheMisses = new AtomicInteger(0);
            AtomicInteger databaseCalls = new AtomicInteger(0);
            
            long thunderStartTime = System.currentTimeMillis();
            
            for (int i = 0; i < concurrentRequests; i++) {
                final int requestId = i;
                executor.submit(() -> {
                    try {
                        // Each request tries to access a random expired key
                        String randomKey = keysBatch.get(requestId % numberOfKeys);
                        Long bookId = (long) (requestId % numberOfKeys + 1);
                        
                        // Simulate the cache miss and database call
                        bookCacheService.getBookById(bookId);
                        
                        if (redisTemplate.hasKey(randomKey)) {
                            cacheHits.incrementAndGet();
                        } else {
                            cacheMisses.incrementAndGet();
                            databaseCalls.incrementAndGet();
                        }
                        
                    } catch (Exception e) {
                        logger.error("Error in thunder herd simulation request {}: {}", requestId, e.getMessage());
                    } finally {
                        latch.countDown();
                    }
                });
            }
            
            latch.await(30, TimeUnit.SECONDS);
            executor.shutdown();
            
            long thunderDuration = System.currentTimeMillis() - thunderStartTime;
            
            result.put("simulation_id", simulationId);
            result.put("problem_type", "Thunder Herd");
            result.put("description", "Many keys expired simultaneously, causing concurrent database requests");
            result.put("thunder_duration_ms", thunderDuration);
            result.put("concurrent_requests", concurrentRequests);
            result.put("cache_hits", cacheHits.get());
            result.put("cache_misses", cacheMisses.get());
            result.put("database_calls", databaseCalls.get());
            result.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            
            // Update global stats
            totalCacheMisses.addAndGet(cacheMisses.get());
            totalDatabaseCalls.addAndGet(databaseCalls.get());
            
            logger.info("Thunder Herd simulation completed: {} cache misses, {} database calls in {} ms",
                    cacheMisses.get(), databaseCalls.get(), thunderDuration);
            
            return result;
            
        } catch (Exception e) {
            logger.error("Error in Thunder Herd simulation: {}", e.getMessage());
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("error", "Thunder Herd simulation failed: " + e.getMessage());
            return errorResult;
        } finally {
            activeSimulations.decrementAndGet();
        }
    }
    
    /**
     * Simulate Cache Penetration Problem
     * Multiple requests for non-existent data bypass cache and hit database
     */
    public Map<String, Object> simulateCachePenetration(int numberOfNonExistentIds, int requestsPerKey) {
        activeSimulations.incrementAndGet();
        String simulationId = "penetration-" + System.currentTimeMillis();
        
        try {
            logger.info("Starting Cache Penetration simulation: {} non-existent IDs, {} requests per key", 
                    numberOfNonExistentIds, requestsPerKey);
            
            Map<String, Object> result = new HashMap<>();
            ExecutorService executor = Executors.newFixedThreadPool(20);
            CountDownLatch latch = new CountDownLatch(numberOfNonExistentIds * requestsPerKey);
            
            AtomicInteger bloomFilterBlocks = new AtomicInteger(0);
            AtomicInteger nullCacheHits = new AtomicInteger(0);
            AtomicInteger databaseCalls = new AtomicInteger(0);
            AtomicInteger totalRequests = new AtomicInteger(0);
            
            long startTime = System.currentTimeMillis();
            
            // Generate non-existent IDs (high numbers that don't exist in database)
            for (int keyIndex = 0; keyIndex < numberOfNonExistentIds; keyIndex++) {
                final Long nonExistentId = 999999L + keyIndex; // Use high IDs that don't exist
                
                for (int requestIndex = 0; requestIndex < requestsPerKey; requestIndex++) {
                    executor.submit(() -> {
                        try {
                            totalRequests.incrementAndGet();
                            
                            // Check if bloom filter would block this request
                            String cacheKey = "book:" + nonExistentId;
                            if (!bloomFilterService.mightContain(cacheKey)) {
                                bloomFilterBlocks.incrementAndGet();
                                return; // Bloom filter prevents database call
                            }
                            
                            // Try to get the non-existent book
                            Optional<Book> book = bookCacheService.getBookById(nonExistentId);
                            
                            if (book.isEmpty()) {
                                databaseCalls.incrementAndGet();
                            }
                            
                        } catch (Exception e) {
                            logger.error("Error in penetration simulation: {}", e.getMessage());
                        } finally {
                            latch.countDown();
                        }
                    });
                }
            }
            
            latch.await(60, TimeUnit.SECONDS);
            executor.shutdown();
            
            long duration = System.currentTimeMillis() - startTime;
            
            result.put("simulation_id", simulationId);
            result.put("problem_type", "Cache Penetration");
            result.put("description", "Requests for non-existent data bypass cache and hit database");
            result.put("duration_ms", duration);
            result.put("total_requests", totalRequests.get());
            result.put("non_existent_ids", numberOfNonExistentIds);
            result.put("requests_per_key", requestsPerKey);
            result.put("bloom_filter_blocks", bloomFilterBlocks.get());
            result.put("null_cache_hits", nullCacheHits.get());
            result.put("database_calls", databaseCalls.get());
            result.put("protection_effectiveness", String.format("%.2f%%", 
                    (1.0 - (double) databaseCalls.get() / totalRequests.get()) * 100));
            result.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            
            // Update global stats
            totalDatabaseCalls.addAndGet(databaseCalls.get());
            
            logger.info("Cache Penetration simulation completed: {} total requests, {} database calls blocked by bloom filter: {}",
                    totalRequests.get(), databaseCalls.get(), bloomFilterBlocks.get());
            
            return result;
            
        } catch (Exception e) {
            logger.error("Error in Cache Penetration simulation: {}", e.getMessage());
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("error", "Cache Penetration simulation failed: " + e.getMessage());
            return errorResult;
        } finally {
            activeSimulations.decrementAndGet();
        }
    }
    
    /**
     * Simulate Cache Breakdown Problem
     * Hot key expires and many concurrent requests hit the database
     */
    public Map<String, Object> simulateCacheBreakdown(int concurrentRequests) {
        activeSimulations.incrementAndGet();
        String simulationId = "breakdown-" + System.currentTimeMillis();
        
        try {
            logger.info("Starting Cache Breakdown simulation with {} concurrent requests", concurrentRequests);
            
            Map<String, Object> result = new HashMap<>();
            
            // Phase 1: Ensure popular books are cached
            bookCacheService.getPopularBooks();
            
            // Phase 2: Force expire the hot key to simulate breakdown
            String hotKey = "book:popular:all";
            redisTemplate.delete(hotKey);
            logger.info("Deleted hot key: {}", hotKey);
            
            // Phase 3: Simulate many concurrent requests for the hot key
            ExecutorService executor = Executors.newFixedThreadPool(concurrentRequests);
            CountDownLatch latch = new CountDownLatch(concurrentRequests);
            AtomicInteger cacheHits = new AtomicInteger(0);
            AtomicInteger cacheMisses = new AtomicInteger(0);
            AtomicInteger databaseCalls = new AtomicInteger(0);
            
            long startTime = System.currentTimeMillis();
            
            for (int i = 0; i < concurrentRequests; i++) {
                executor.submit(() -> {
                    try {
                        // All requests try to get popular books (hot key)
                        bookCacheService.getPopularBooks();
                        
                        if (redisTemplate.hasKey(hotKey)) {
                            cacheHits.incrementAndGet();
                        } else {
                            cacheMisses.incrementAndGet();
                            databaseCalls.incrementAndGet();
                        }
                        
                    } catch (Exception e) {
                        logger.error("Error in breakdown simulation: {}", e.getMessage());
                    } finally {
                        latch.countDown();
                    }
                });
            }
            
            latch.await(30, TimeUnit.SECONDS);
            executor.shutdown();
            
            long duration = System.currentTimeMillis() - startTime;
            
            result.put("simulation_id", simulationId);
            result.put("problem_type", "Cache Breakdown");
            result.put("description", "Hot key expired, causing multiple concurrent database requests");
            result.put("duration_ms", duration);
            result.put("hot_key", hotKey);
            result.put("concurrent_requests", concurrentRequests);
            result.put("cache_hits", cacheHits.get());
            result.put("cache_misses", cacheMisses.get());
            result.put("database_calls", databaseCalls.get());
            result.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            
            // Update global stats
            totalCacheHits.addAndGet(cacheHits.get());
            totalCacheMisses.addAndGet(cacheMisses.get());
            totalDatabaseCalls.addAndGet(databaseCalls.get());
            
            logger.info("Cache Breakdown simulation completed: {} database calls for hot key in {} ms",
                    databaseCalls.get(), duration);
            
            return result;
            
        } catch (Exception e) {
            logger.error("Error in Cache Breakdown simulation: {}", e.getMessage());
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("error", "Cache Breakdown simulation failed: " + e.getMessage());
            return errorResult;
        } finally {
            activeSimulations.decrementAndGet();
        }
    }
    
    /**
     * Simulate Cache Crash Problem
     * Redis becomes unavailable, circuit breaker should activate
     */
    public Map<String, Object> simulateCacheCrash() {
        activeSimulations.incrementAndGet();
        String simulationId = "crash-" + System.currentTimeMillis();
        
        try {
            logger.info("Starting Cache Crash simulation");
            
            Map<String, Object> result = new HashMap<>();
            
            // Clear all caches to simulate crash
            bookCacheService.invalidateAllCaches();
            
            // Simulate multiple requests when cache is "down"
            ExecutorService executor = Executors.newFixedThreadPool(10);
            CountDownLatch latch = new CountDownLatch(20);
            AtomicInteger circuitBreakerActivations = new AtomicInteger(0);
            AtomicInteger databaseFallbacks = new AtomicInteger(0);
            
            long startTime = System.currentTimeMillis();
            
            for (int i = 0; i < 20; i++) {
                final int requestId = i;
                executor.submit(() -> {
                    try {
                        // Try to get a book - should trigger circuit breaker and fallback to database
                        bookCacheService.getBookById((long) (requestId % 10 + 1));
                        databaseFallbacks.incrementAndGet();
                        
                    } catch (Exception e) {
                        logger.debug("Expected error during cache crash simulation: {}", e.getMessage());
                        circuitBreakerActivations.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }
            
            latch.await(30, TimeUnit.SECONDS);
            executor.shutdown();
            
            long duration = System.currentTimeMillis() - startTime;
            
            result.put("simulation_id", simulationId);
            result.put("problem_type", "Cache Crash");
            result.put("description", "Cache system unavailable, circuit breaker activated, fallback to database");
            result.put("duration_ms", duration);
            result.put("circuit_breaker_activations", circuitBreakerActivations.get());
            result.put("database_fallbacks", databaseFallbacks.get());
            result.put("total_requests", 20);
            result.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            
            // Update global stats
            totalRedisErrors.addAndGet(circuitBreakerActivations.get());
            totalDatabaseCalls.addAndGet(databaseFallbacks.get());
            
            logger.info("Cache Crash simulation completed: {} circuit breaker activations, {} database fallbacks",
                    circuitBreakerActivations.get(), databaseFallbacks.get());
            
            return result;
            
        } catch (Exception e) {
            logger.error("Error in Cache Crash simulation: {}", e.getMessage());
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("error", "Cache Crash simulation failed: " + e.getMessage());
            return errorResult;
        } finally {
            activeSimulations.decrementAndGet();
        }
    }
    
    /**
     * Expire hot keys to demonstrate breakdown
     */
    public void expireHotKeys() {
        try {
            redisTemplate.delete("book:popular:all");
            redisTemplate.delete("book:bestseller:all");
            logger.info("Expired hot keys for breakdown simulation");
        } catch (Exception e) {
            logger.error("Error expiring hot keys: {}", e.getMessage());
        }
    }
    
    /**
     * Get simulation statistics
     */
    public Map<String, Object> getSimulationStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("total_cache_hits", totalCacheHits.get());
        stats.put("total_cache_misses", totalCacheMisses.get());
        stats.put("total_database_calls", totalDatabaseCalls.get());
        stats.put("total_redis_errors", totalRedisErrors.get());
        stats.put("active_simulations", activeSimulations.get());
        stats.put("cache_hit_ratio", calculateCacheHitRatio());
        return stats;
    }
    
    /**
     * Reset simulation statistics
     */
    public void resetSimulationStats() {
        totalCacheHits.set(0);
        totalCacheMisses.set(0);
        totalDatabaseCalls.set(0);
        totalRedisErrors.set(0);
        activeSimulations.set(0);
        logger.info("Reset simulation statistics");
    }
    
    private double calculateCacheHitRatio() {
        long hits = totalCacheHits.get();
        long misses = totalCacheMisses.get();
        long total = hits + misses;
        return total > 0 ? (double) hits / total * 100 : 0.0;
    }
    
    private Book createDummyBook(int id, String title) {
        Book book = new Book();
        book.setId((long) id);
        book.setTitle(title);
        book.setAuthor("Simulation Author");
        book.setIsbn("SIM-" + String.format("%06d", id));
        book.setGenre("Simulation");
        book.setPublicationYear(2024);
        book.setPrice(19.99);
        book.setStockQuantity(100);
        return book;
    }
}
