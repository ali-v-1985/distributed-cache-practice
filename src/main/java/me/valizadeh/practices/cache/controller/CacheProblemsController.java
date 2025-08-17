package me.valizadeh.practices.cache.controller;

import me.valizadeh.practices.cache.service.BloomFilterService;
import me.valizadeh.practices.cache.service.BookCacheService;
import me.valizadeh.practices.cache.service.CacheSimulationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller to demonstrate and simulate various cache problems
 */
@RestController
@RequestMapping("/api/cache-problems")
public class CacheProblemsController {
    
    private static final Logger logger = LoggerFactory.getLogger(CacheProblemsController.class);
    
    private final CacheSimulationService cacheSimulationService;
    private final BookCacheService bookCacheService;
    private final BloomFilterService bloomFilterService;
    
    public CacheProblemsController(CacheSimulationService cacheSimulationService,
                                 BookCacheService bookCacheService,
                                 BloomFilterService bloomFilterService) {
        this.cacheSimulationService = cacheSimulationService;
        this.bookCacheService = bookCacheService;
        this.bloomFilterService = bloomFilterService;
    }
    
    /**
     * Simulate Thunder Herd problem
     * Demonstrates what happens when many keys expire at the same time
     */
    @PostMapping("/thunder-herd/simulate")
    public ResponseEntity<Map<String, Object>> simulateThunderHerd(
            @RequestParam(defaultValue = "100") int numberOfKeys,
            @RequestParam(defaultValue = "10") int concurrentRequests) {
        
        logger.info("Simulating Thunder Herd problem with {} keys and {} concurrent requests", 
                numberOfKeys, concurrentRequests);
        
        Map<String, Object> result = cacheSimulationService.simulateThunderHerd(numberOfKeys, concurrentRequests);
        return ResponseEntity.ok(result);
    }
    
    /**
     * Simulate Cache Penetration problem
     * Demonstrates what happens when requesting non-existent data
     */
    @PostMapping("/penetration/simulate")
    public ResponseEntity<Map<String, Object>> simulateCachePenetration(
            @RequestParam(defaultValue = "50") int numberOfNonExistentIds,
            @RequestParam(defaultValue = "10") int requestsPerKey) {
        
        logger.info("Simulating Cache Penetration problem with {} non-existent IDs and {} requests per key", 
                numberOfNonExistentIds, requestsPerKey);
        
        Map<String, Object> result = cacheSimulationService.simulateCachePenetration(numberOfNonExistentIds, requestsPerKey);
        return ResponseEntity.ok(result);
    }
    
    /**
     * Simulate Cache Breakdown problem
     * Demonstrates what happens when a hot key expires
     */
    @PostMapping("/breakdown/simulate")
    public ResponseEntity<Map<String, Object>> simulateCacheBreakdown(
            @RequestParam(defaultValue = "100") int concurrentRequests) {
        
        logger.info("Simulating Cache Breakdown problem with {} concurrent requests to hot key", 
                concurrentRequests);
        
        Map<String, Object> result = cacheSimulationService.simulateCacheBreakdown(concurrentRequests);
        return ResponseEntity.ok(result);
    }
    
    /**
     * Simulate Cache Crash problem
     * Demonstrates what happens when Redis goes down
     */
    @PostMapping("/crash/simulate")
    public ResponseEntity<Map<String, Object>> simulateCacheCrash() {
        logger.info("Simulating Cache Crash problem");
        
        Map<String, Object> result = cacheSimulationService.simulateCacheCrash();
        return ResponseEntity.ok(result);
    }
    
    /**
     * Get cache statistics and health
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getCacheStats() {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            // Bloom filter stats
            Map<String, Object> bloomStats = new HashMap<>();
            bloomStats.put("enabled", bloomFilterService.isEnabled());
            bloomStats.put("approximateElementCount", bloomFilterService.getApproximateElementCount());
            bloomStats.put("expectedFpp", bloomFilterService.getExpectedFpp());
            stats.put("bloomFilter", bloomStats);
            
            // Cache simulation stats
            stats.putAll(cacheSimulationService.getSimulationStats());
            
        } catch (Exception e) {
            logger.error("Error getting cache stats: {}", e.getMessage());
            stats.put("error", "Failed to retrieve cache stats: " + e.getMessage());
        }
        
        return ResponseEntity.ok(stats);
    }
    
    /**
     * Reset all cache simulations and clear caches
     */
    @PostMapping("/reset")
    public ResponseEntity<Map<String, String>> resetCaches() {
        logger.info("Resetting all caches and simulations");
        
        try {
            bookCacheService.invalidateAllCaches();
            cacheSimulationService.resetSimulationStats();
            
            Map<String, String> result = new HashMap<>();
            result.put("status", "success");
            result.put("message", "All caches and simulations have been reset");
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.error("Error resetting caches: {}", e.getMessage());
            
            Map<String, String> result = new HashMap<>();
            result.put("status", "error");
            result.put("message", "Failed to reset caches: " + e.getMessage());
            
            return ResponseEntity.ok(result);
        }
    }
    
    /**
     * Force expire hot keys to demonstrate breakdown
     */
    @PostMapping("/breakdown/expire-hot-keys")
    public ResponseEntity<Map<String, String>> expireHotKeys() {
        logger.info("Forcing expiration of hot keys");
        
        try {
            cacheSimulationService.expireHotKeys();
            
            Map<String, String> result = new HashMap<>();
            result.put("status", "success");
            result.put("message", "Hot keys have been expired");
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            logger.error("Error expiring hot keys: {}", e.getMessage());
            
            Map<String, String> result = new HashMap<>();
            result.put("status", "error");
            result.put("message", "Failed to expire hot keys: " + e.getMessage());
            
            return ResponseEntity.ok(result);
        }
    }
}
