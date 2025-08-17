$baseUrl = "http://localhost:8080"

Write-Host "🧪 Testing Distributed Cache System" -ForegroundColor Green
Write-Host "===================================" -ForegroundColor Green
Write-Host ""

# Function to make HTTP requests
function Test-Endpoint {
    param(
        [string]$Method,
        [string]$Endpoint,
        [string]$Description
    )
    
    Write-Host "📡 $Description" -ForegroundColor Cyan
    Write-Host "   $Method $Endpoint" -ForegroundColor Gray
    
    try {
        if ($Method -eq "GET") {
            $response = Invoke-RestMethod -Uri "$baseUrl$Endpoint" -Method GET -TimeoutSec 10
        } else {
            $response = Invoke-RestMethod -Uri "$baseUrl$Endpoint" -Method POST -TimeoutSec 10
        }
        
        if ($response) {
            Write-Host "   Success" -ForegroundColor Green
            Write-Host "   Response: $($response | ConvertTo-Json -Depth 2 -Compress)" -ForegroundColor Gray
        }
    } catch {
        Write-Host "   Error: $($_.Exception.Message)" -ForegroundColor Red
    }
    Write-Host ""
}

# Test if application is running
Write-Host "🔍 Checking if application is running..." -ForegroundColor Yellow
try {
    $health = Invoke-RestMethod -Uri "$baseUrl/actuator/health" -Method GET -TimeoutSec 5
    Write-Host "✅ Application is running!" -ForegroundColor Green
    Write-Host "   Status: $($health.status)" -ForegroundColor Gray
} catch {
    Write-Host "❌ Application is not running or not responding" -ForegroundColor Red
    Write-Host "   Error: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "   Please start the application first using: start.bat" -ForegroundColor Yellow
    exit 1
}
Write-Host ""

# Test basic book operations
Write-Host "📚 Testing Basic Book Operations" -ForegroundColor Blue
Write-Host "--------------------------------" -ForegroundColor Blue
Test-Endpoint "GET" "/api/books" "Get all books"
Test-Endpoint "GET" "/api/books/1" "Get book by ID"
Test-Endpoint "GET" "/api/books/popular" "Get popular books (hot key)"
Test-Endpoint "GET" "/api/books/bestsellers" "Get bestseller books (hot key)"
Test-Endpoint "GET" "/api/books/search?keyword=java" "Search books"

# Test cache problem simulations
Write-Host "🔥 Testing Cache Problem Simulations" -ForegroundColor Magenta
Write-Host "======================================" -ForegroundColor Magenta

Write-Host "1️⃣ Thunder Herd Problem Simulation" -ForegroundColor Red
Write-Host "-----------------------------------" -ForegroundColor Red
Test-Endpoint "POST" "/api/cache-problems/thunder-herd/simulate?numberOfKeys=10&concurrentRequests=5" "Simulate Thunder Herd"

Write-Host "2️⃣ Cache Penetration Problem Simulation" -ForegroundColor Red
Write-Host "----------------------------------------" -ForegroundColor Red
Test-Endpoint "POST" "/api/cache-problems/penetration/simulate?numberOfNonExistentIds=5&requestsPerKey=3" "Simulate Cache Penetration"

Write-Host "3️⃣ Cache Breakdown Problem Simulation" -ForegroundColor Red
Write-Host "--------------------------------------" -ForegroundColor Red
Test-Endpoint "POST" "/api/cache-problems/breakdown/simulate?concurrentRequests=10" "Simulate Cache Breakdown"

Write-Host "4️⃣ Cache Crash Problem Simulation" -ForegroundColor Red
Write-Host "----------------------------------" -ForegroundColor Red
Test-Endpoint "POST" "/api/cache-problems/crash/simulate" "Simulate Cache Crash"

# Test monitoring endpoints
Write-Host "📊 Testing Monitoring Endpoints" -ForegroundColor Green
Write-Host "===============================" -ForegroundColor Green
Test-Endpoint "GET" "/api/monitoring/redis/health" "Redis health check"
Test-Endpoint "GET" "/api/monitoring/cache/stats" "Cache statistics"
Test-Endpoint "GET" "/api/cache-problems/stats" "Simulation statistics"

Write-Host "🎯 Cache Problems Testing Complete!" -ForegroundColor Green
Write-Host "===================================" -ForegroundColor Green
Write-Host ""
Write-Host "📈 Key Insights:" -ForegroundColor Yellow
Write-Host "   • Thunder Herd: Shows impact of simultaneous key expiration" -ForegroundColor Gray
Write-Host "   • Cache Penetration: Demonstrates bloom filter protection" -ForegroundColor Gray
Write-Host "   • Cache Breakdown: Illustrates hot key expiration impact" -ForegroundColor Gray
Write-Host "   • Cache Crash: Shows circuit breaker and fallback behavior" -ForegroundColor Gray
Write-Host ""
Write-Host "💡 Next Steps:" -ForegroundColor Yellow
Write-Host "   • Check Docker logs: docker-compose logs redis" -ForegroundColor Gray
Write-Host "   • View H2 database at: http://localhost:8082" -ForegroundColor Gray
Write-Host "   • Explore API endpoints in browser or Postman" -ForegroundColor Gray
