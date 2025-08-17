package me.valizadeh.practices.cache.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "app.cache")
public class CacheProperties {
    
    private ThunderHerd thunderHerd = new ThunderHerd();
    private Penetration penetration = new Penetration();
    private Breakdown breakdown = new Breakdown();
    private Crash crash = new Crash();
    
    @Data
    public static class ThunderHerd {
        private boolean enabled = true;
        private int jitterPercentage = 20;
    }
    
    @Data
    public static class Penetration {
        private BloomFilter bloomFilter = new BloomFilter();
        private NullCache nullCache = new NullCache();
        
        @Data
        public static class BloomFilter {
            private boolean enabled = true;
            private int expectedInsertions = 100000;
            private double falsePositiveProbability = 0.01;
        }
        
        @Data
        public static class NullCache {
            private boolean enabled = true;
            private long ttl = 300000;
        }
    }
    
    @Data
    public static class Breakdown {
        private List<String> hotKeys;
        private boolean noExpiry = true;
    }
    
    @Data
    public static class Crash {
        private CircuitBreaker circuitBreaker = new CircuitBreaker();
        private boolean clusterEnabled = false;
        
        @Data
        public static class CircuitBreaker {
            private boolean enabled = true;
            private int failureRateThreshold = 50;
            private long waitDurationInOpenState = 10000;
            private int slidingWindowSize = 10;
        }
    }
}
