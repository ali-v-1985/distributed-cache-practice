package me.valizadeh.practices.cache.service;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import me.valizadeh.practices.cache.config.CacheProperties;
import java.nio.charset.Charset;

/**
 * Bloom Filter Service for Cache Penetration Protection
 * Helps prevent cache penetration by checking if a key might exist before hitting the database
 */
@Slf4j
@Service
public class BloomFilterService {
    
    private final CacheProperties cacheProperties;
    
    public BloomFilterService(CacheProperties cacheProperties) {
        this.cacheProperties = cacheProperties;
    }
    
    private BloomFilter<String> bloomFilter;
    
    @PostConstruct
    public void init() {
        if (cacheProperties.getPenetration().getBloomFilter().isEnabled()) {
            var config = cacheProperties.getPenetration().getBloomFilter();
            bloomFilter = BloomFilter.create(
                Funnels.stringFunnel(Charset.defaultCharset()),
                config.getExpectedInsertions(),
                config.getFalsePositiveProbability()
            );
            log.info("Bloom filter initialized with {} expected insertions and {} false positive probability",
                    config.getExpectedInsertions(), config.getFalsePositiveProbability());
        }
    }
    
    /**
     * Add a key to the bloom filter
     * @param key the key to add
     */
    public void add(String key) {
        if (cacheProperties.getPenetration().getBloomFilter().isEnabled() && bloomFilter != null) {
            bloomFilter.put(key);
            log.debug("Added key to bloom filter: {}", key);
        }
    }
    
    /**
     * Check if a key might exist in the bloom filter
     * @param key the key to check
     * @return true if the key might exist, false if it definitely doesn't exist
     */
    public boolean mightContain(String key) {
        if (!cacheProperties.getPenetration().getBloomFilter().isEnabled() || bloomFilter == null) {
            return true; // If bloom filter is disabled, assume key might exist
        }
        
        boolean result = bloomFilter.mightContain(key);
        log.debug("Bloom filter check for key '{}': {}", key, result);
        return result;
    }
    
    /**
     * Get the approximate number of elements added to the bloom filter
     * @return approximate count of elements
     */
    public long getApproximateElementCount() {
        if (cacheProperties.getPenetration().getBloomFilter().isEnabled() && bloomFilter != null) {
            return bloomFilter.approximateElementCount();
        }
        return 0;
    }
    
    /**
     * Get the expected false positive probability
     * @return false positive probability
     */
    public double getExpectedFpp() {
        if (cacheProperties.getPenetration().getBloomFilter().isEnabled() && bloomFilter != null) {
            return bloomFilter.expectedFpp();
        }
        return 0.0;
    }
    
    /**
     * Check if bloom filter is enabled
     * @return true if enabled, false otherwise
     */
    public boolean isEnabled() {
        return cacheProperties.getPenetration().getBloomFilter().isEnabled();
    }
}
