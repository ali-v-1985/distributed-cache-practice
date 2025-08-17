package me.valizadeh.practices.cache.service;

import lombok.extern.slf4j.Slf4j;
import me.valizadeh.practices.cache.config.CacheProperties;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis-backed Bloom Filter implementation that persists across restarts
 * and supports TTL to prevent memory leaks
 */
@Slf4j
@Service
public class RedisBloomFilterService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final CacheProperties cacheProperties;
    
    private static final String BLOOM_FILTER_PREFIX = "bf:";
    private static final String BLOOM_FILTER_SET = "bloom_filter_keys";
    private static final long DEFAULT_TTL_HOURS = 24;
    
    public RedisBloomFilterService(RedisTemplate<String, Object> redisTemplate, 
                                 CacheProperties cacheProperties) {
        this.redisTemplate = redisTemplate;
        this.cacheProperties = cacheProperties;
    }
    
    /**
     * Add a key to the bloom filter with TTL
     */
    public void add(String key) {
        if (!isEnabled()) {
            return;
        }
        
        String bloomKey = BLOOM_FILTER_PREFIX + key;
        
        // Add to Redis with TTL
        redisTemplate.opsForValue().set(bloomKey, "1", Duration.ofHours(DEFAULT_TTL_HOURS));
        
        // Also maintain a set of all bloom filter keys for cleanup
        redisTemplate.opsForSet().add(BLOOM_FILTER_SET, bloomKey);
        redisTemplate.expire(BLOOM_FILTER_SET, Duration.ofHours(DEFAULT_TTL_HOURS + 1));
        
        log.debug("Added key to Redis bloom filter: {}", key);
    }
    
    /**
     * Check if key might exist in the bloom filter
     */
    public boolean mightContain(String key) {
        if (!isEnabled()) {
            return true; // If disabled, assume everything exists
        }
        
        String bloomKey = BLOOM_FILTER_PREFIX + key;
        Boolean exists = redisTemplate.hasKey(bloomKey);
        
        log.debug("Bloom filter check for key '{}': {}", key, exists);
        return exists != null && exists;
    }
    
    /**
     * Remove a key from the bloom filter (when data is deleted)
     */
    public void remove(String key) {
        if (!isEnabled()) {
            return;
        }
        
        String bloomKey = BLOOM_FILTER_PREFIX + key;
        redisTemplate.delete(bloomKey);
        redisTemplate.opsForSet().remove(BLOOM_FILTER_SET, bloomKey);
        
        log.debug("Removed key from Redis bloom filter: {}", key);
    }
    
    /**
     * Initialize bloom filter from existing database data
     */
    public void initializeFromDatabase(Set<String> existingKeys) {
        if (!isEnabled()) {
            return;
        }
        
        log.info("Initializing Redis bloom filter with {} existing keys", existingKeys.size());
        
        for (String key : existingKeys) {
            add(key);
        }
        
        log.info("Redis bloom filter initialization completed");
    }
    
    /**
     * Clean up expired entries from the bloom filter
     */
    public void cleanupExpiredEntries() {
        if (!isEnabled()) {
            return;
        }
        
        Set<Object> bloomKeys = redisTemplate.opsForSet().members(BLOOM_FILTER_SET);
        if (bloomKeys == null) return;
        
        int cleaned = 0;
        for (Object keyObj : bloomKeys) {
            String bloomKey = (String) keyObj;
            if (!redisTemplate.hasKey(bloomKey)) {
                redisTemplate.opsForSet().remove(BLOOM_FILTER_SET, bloomKey);
                cleaned++;
            }
        }
        
        log.info("Cleaned up {} expired entries from bloom filter", cleaned);
    }
    
    /**
     * Get statistics about the bloom filter
     */
    public long getApproximateElementCount() {
        if (!isEnabled()) {
            return 0;
        }
        
        Long count = redisTemplate.opsForSet().size(BLOOM_FILTER_SET);
        return count != null ? count : 0;
    }
    
    public boolean isEnabled() {
        return cacheProperties.getPenetration().getBloomFilter().isEnabled();
    }
    
    /**
     * Clear all bloom filter data (for testing)
     */
    public void clear() {
        Set<Object> bloomKeys = redisTemplate.opsForSet().members(BLOOM_FILTER_SET);
        if (bloomKeys != null && !bloomKeys.isEmpty()) {
            // Convert to Collection<String> for delete operation
            bloomKeys.forEach(key -> redisTemplate.delete((String) key));
            redisTemplate.delete(BLOOM_FILTER_SET);
            log.info("Cleared all bloom filter data");
        }
    }
    
    /**
     * Get expected false positive probability (for compatibility)
     */
    public double getExpectedFpp() {
        if (!isEnabled()) {
            return 0.0;
        }
        return cacheProperties.getPenetration().getBloomFilter().getFalsePositiveProbability();
    }
}
