package me.valizadeh.practices.cache.controller;

import lombok.extern.slf4j.Slf4j;
import me.valizadeh.practices.cache.service.BloomFilterMaintenanceService;
import me.valizadeh.practices.cache.service.RedisBloomFilterService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller for Bloom Filter management operations
 */
@Slf4j
@RestController
@RequestMapping("/api/bloom-filter")
public class BloomFilterController {
    
    private final BloomFilterMaintenanceService maintenanceService;
    private final RedisBloomFilterService bloomFilterService;
    
    public BloomFilterController(BloomFilterMaintenanceService maintenanceService,
                               RedisBloomFilterService bloomFilterService) {
        this.maintenanceService = maintenanceService;
        this.bloomFilterService = bloomFilterService;
    }
    
    /**
     * Trigger manual bloom filter rebuild
     */
    @PostMapping("/rebuild")
    public ResponseEntity<Map<String, Object>> rebuildBloomFilter() {
        log.info("Manual bloom filter rebuild requested via API");
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            long startTime = System.currentTimeMillis();
            maintenanceService.manualRebuild();
            long duration = System.currentTimeMillis() - startTime;
            
            response.put("status", "success");
            response.put("message", "Bloom filter rebuilt successfully");
            response.put("duration_ms", duration);
            response.put("new_element_count", bloomFilterService.getApproximateElementCount());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to rebuild bloom filter", e);
            response.put("status", "error");
            response.put("message", "Failed to rebuild bloom filter: " + e.getMessage());
            
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    /**
     * Clear all bloom filter data (for testing)
     */
    @DeleteMapping("/clear")
    public ResponseEntity<Map<String, Object>> clearBloomFilter() {
        log.info("Manual bloom filter clear requested via API");
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            bloomFilterService.clear();
            
            response.put("status", "success");
            response.put("message", "Bloom filter cleared successfully");
            response.put("element_count", 0);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to clear bloom filter", e);
            response.put("status", "error");
            response.put("message", "Failed to clear bloom filter: " + e.getMessage());
            
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    /**
     * Get detailed bloom filter statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getDetailedStats() {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            stats.put("enabled", bloomFilterService.isEnabled());
            stats.put("approximate_element_count", bloomFilterService.getApproximateElementCount());
            stats.put("type", "redis_backed_with_ttl");
            stats.put("persistent_across_restarts", true);
            stats.put("supports_cleanup", true);
            stats.put("supports_rebuild", true);
            
        } catch (Exception e) {
            stats.put("error", "Failed to retrieve bloom filter stats: " + e.getMessage());
        }
        
        return ResponseEntity.ok(stats);
    }
    
    /**
     * Test bloom filter functionality
     */
    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> testBloomFilter(@RequestParam String key) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Test might contain
            boolean mightContain = bloomFilterService.mightContain(key);
            result.put("key", key);
            result.put("might_contain", mightContain);
            
            // Add key and test again
            bloomFilterService.add(key);
            boolean mightContainAfterAdd = bloomFilterService.mightContain(key);
            result.put("might_contain_after_add", mightContainAfterAdd);
            
            result.put("status", "success");
            
        } catch (Exception e) {
            log.error("Failed to test bloom filter", e);
            result.put("status", "error");
            result.put("message", "Failed to test bloom filter: " + e.getMessage());
        }
        
        return ResponseEntity.ok(result);
    }
}
