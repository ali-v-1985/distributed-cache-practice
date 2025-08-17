package me.valizadeh.practices.cache.service;

import lombok.extern.slf4j.Slf4j;
import me.valizadeh.practices.cache.repository.BookRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service to maintain bloom filter consistency and prevent memory leaks
 */
@Slf4j
@Service
public class BloomFilterMaintenanceService {
    
    private final RedisBloomFilterService redisBloomFilterService;
    private final BookRepository bookRepository;
    
    public BloomFilterMaintenanceService(RedisBloomFilterService redisBloomFilterService,
                                       BookRepository bookRepository) {
        this.redisBloomFilterService = redisBloomFilterService;
        this.bookRepository = bookRepository;
    }
    
    /**
     * Initialize bloom filter when application starts
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initializeBloomFilterOnStartup() {
        log.info("Initializing bloom filter from database on application startup");
        
        try {
            // Get all existing book IDs and ISBNs
            Set<String> existingKeys = bookRepository.findAll().stream()
                .flatMap(book -> Set.of(
                    "book:" + book.getId(),
                    "book:isbn:" + book.getIsbn()
                ).stream())
                .collect(Collectors.toSet());
            
            // Initialize Redis bloom filter
            redisBloomFilterService.initializeFromDatabase(existingKeys);
            
            log.info("Bloom filter initialization completed with {} keys", existingKeys.size());
            
        } catch (Exception e) {
            log.error("Failed to initialize bloom filter from database", e);
        }
    }
    
    /**
     * Clean up expired bloom filter entries every hour
     */
    @Scheduled(fixedRate = 3600000) // 1 hour = 3,600,000 ms
    public void cleanupExpiredEntries() {
        log.debug("Starting scheduled bloom filter cleanup");
        
        try {
            redisBloomFilterService.cleanupExpiredEntries();
        } catch (Exception e) {
            log.error("Failed to cleanup expired bloom filter entries", e);
        }
    }
    
    /**
     * Rebuild bloom filter from database every 6 hours to ensure consistency
     */
    @Scheduled(fixedRate = 21600000) // 6 hours = 21,600,000 ms
    public void rebuildBloomFilter() {
        log.info("Starting scheduled bloom filter rebuild");
        
        try {
            // Clear existing bloom filter
            redisBloomFilterService.clear();
            
            // Reinitialize from database
            initializeBloomFilterOnStartup();
            
            log.info("Bloom filter rebuild completed successfully");
            
        } catch (Exception e) {
            log.error("Failed to rebuild bloom filter", e);
        }
    }
    
    /**
     * Manual bloom filter rebuild (can be triggered via API)
     */
    public void manualRebuild() {
        log.info("Manual bloom filter rebuild requested");
        rebuildBloomFilter();
    }
}
