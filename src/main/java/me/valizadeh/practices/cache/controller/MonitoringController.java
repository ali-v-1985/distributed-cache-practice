package me.valizadeh.practices.cache.controller;

import io.micrometer.core.instrument.MeterRegistry;
import me.valizadeh.practices.cache.service.RedisBloomFilterService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller for monitoring cache health and metrics
 */
@RestController
@RequestMapping("/api/monitoring")
public class MonitoringController {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisBloomFilterService bloomFilterService;
    private final MeterRegistry meterRegistry;
    
    public MonitoringController(RedisTemplate<String, Object> redisTemplate,
                              RedisBloomFilterService bloomFilterService,
                              MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.bloomFilterService = bloomFilterService;
        this.meterRegistry = meterRegistry;
    }
    
    /**
     * Get Redis health and connection info
     */
    @GetMapping("/redis/health")
    public ResponseEntity<Map<String, Object>> getRedisHealth() {
        Map<String, Object> health = new HashMap<>();
        
        try {
            // Test Redis connection using RedisTemplate operations
            redisTemplate.hasKey("health:check");
            health.put("status", "UP");
            health.put("ping", "PONG");
            
            // Get basic Redis info using template operations
            Map<String, Object> redisInfo = new HashMap<>();
            
            // Get approximate key count using pattern matching
            Integer keyCount = redisTemplate.keys("*").size();
            redisInfo.put("total_keys", keyCount);
            redisInfo.put("connection_active", true);
            
            health.put("info", redisInfo);
            
        } catch (Exception e) {
            health.put("status", "DOWN");
            health.put("error", e.getMessage());
        }
        
        return ResponseEntity.ok(health);
    }
    
    /**
     * Get cache statistics and metrics
     */
    @GetMapping("/cache/stats")
    public ResponseEntity<Map<String, Object>> getCacheStats() {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            // Redis key statistics using RedisTemplate operations
            Integer keyCount = redisTemplate.keys("*").size();
            stats.put("total_keys", keyCount);
            
            // Bloom filter statistics
            Map<String, Object> bloomStats = new HashMap<>();
            bloomStats.put("enabled", bloomFilterService.isEnabled());
            bloomStats.put("approximate_element_count", bloomFilterService.getApproximateElementCount());
            bloomStats.put("expected_fpp", bloomFilterService.getExpectedFpp());
            stats.put("bloom_filter", bloomStats);
            
            // Application metrics from Micrometer
            Map<String, Object> metrics = new HashMap<>();
            
            // Cache metrics (if available)
            try {
                metrics.put("cache_gets", meterRegistry.counter("cache.gets").count());
                metrics.put("cache_puts", meterRegistry.counter("cache.puts").count());
                metrics.put("cache_evictions", meterRegistry.counter("cache.evictions").count());
            } catch (Exception e) {
                metrics.put("cache_metrics_error", "Cache metrics not available");
            }
            
            // JVM metrics
            try {
                metrics.put("jvm_memory_used", meterRegistry.get("jvm.memory.used").gauge().value());
                metrics.put("jvm_memory_max", meterRegistry.get("jvm.memory.max").gauge().value());
                metrics.put("jvm_threads", meterRegistry.get("jvm.threads.live").gauge().value());
            } catch (Exception e) {
                metrics.put("jvm_metrics_error", "JVM metrics not available: " + e.getMessage());
            }
            
            stats.put("metrics", metrics);
            
        } catch (Exception e) {
            stats.put("error", "Failed to retrieve cache stats: " + e.getMessage());
        }
        
        return ResponseEntity.ok(stats);
    }
    
    /**
     * Get system performance metrics
     */
    @GetMapping("/performance")
    public ResponseEntity<Map<String, Object>> getPerformanceMetrics() {
        Map<String, Object> performance = new HashMap<>();
        
        try {
            // System metrics
            Runtime runtime = Runtime.getRuntime();
            performance.put("jvm_total_memory", runtime.totalMemory());
            performance.put("jvm_free_memory", runtime.freeMemory());
            performance.put("jvm_used_memory", runtime.totalMemory() - runtime.freeMemory());
            performance.put("jvm_max_memory", runtime.maxMemory());
            performance.put("available_processors", runtime.availableProcessors());
            
            // Thread metrics
            Thread currentThread = Thread.currentThread();
            ThreadGroup threadGroup = currentThread.getThreadGroup();
            performance.put("active_thread_count", threadGroup.activeCount());
            
            // Redis performance metrics using RedisTemplate operations
            try {
                Map<String, Object> redisPerf = new HashMap<>();
                
                // Get basic Redis metrics
                Integer totalKeys = redisTemplate.keys("*").size();
                redisPerf.put("total_keys", totalKeys);
                redisPerf.put("connection_active", true);
                
                // Test operation performance
                long startTime = System.currentTimeMillis();
                redisTemplate.hasKey("test:performance");
                long operationTime = System.currentTimeMillis() - startTime;
                redisPerf.put("test_operation_ms", operationTime);
                
                performance.put("redis", redisPerf);
                
            } catch (Exception e) {
                performance.put("redis_performance_error", "Redis performance metrics not available: " + e.getMessage());
            }
            
        } catch (Exception e) {
            performance.put("error", "Failed to retrieve performance metrics: " + e.getMessage());
        }
        
        return ResponseEntity.ok(performance);
    }
    
    /**
     * Get cache key information
     */
    @GetMapping("/cache/keys")
    public ResponseEntity<Map<String, Object>> getCacheKeys() {
        Map<String, Object> keyInfo = new HashMap<>();
        
        try {
            // Get sample keys from different patterns
            Map<String, Object> keyPatterns = new HashMap<>();
            
            // Book cache keys
            keyPatterns.put("book_keys", redisTemplate.keys("book:*"));
            keyPatterns.put("popular_keys", redisTemplate.keys("book:popular:*"));
            keyPatterns.put("bestseller_keys", redisTemplate.keys("book:bestseller:*"));
            keyPatterns.put("search_keys", redisTemplate.keys("book:search:*"));
            keyPatterns.put("null_keys", redisTemplate.keys("null:*"));
            keyPatterns.put("thunder_herd_keys", redisTemplate.keys("thunder-herd:*"));
            
            keyInfo.put("key_patterns", keyPatterns);
            keyInfo.put("total_keys", redisTemplate.keys("*").size());
            
        } catch (Exception e) {
            keyInfo.put("error", "Failed to retrieve cache keys: " + e.getMessage());
        }
        
        return ResponseEntity.ok(keyInfo);
    }
    
    /**
     * Get Redis Bloom Filter statistics and health
     */
    @GetMapping("/bloom-filter/stats")
    public ResponseEntity<Map<String, Object>> getBloomFilterStats() {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            stats.put("enabled", bloomFilterService.isEnabled());
            stats.put("approximate_element_count", bloomFilterService.getApproximateElementCount());
            stats.put("type", "redis_backed");
            stats.put("persistent", true);
            stats.put("supports_ttl", true);
            
        } catch (Exception e) {
            stats.put("error", "Failed to retrieve bloom filter stats: " + e.getMessage());
        }
        
        return ResponseEntity.ok(stats);
    }
}
