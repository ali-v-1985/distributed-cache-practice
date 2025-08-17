@echo off
echo 🚀 Starting Distributed Cache Practice Application...
echo.

REM Check if Docker is running
docker info >nul 2>&1
if errorlevel 1 (
    echo ❌ Docker is not running. Please start Docker first.
    pause
    exit /b 1
)

echo 📋 Starting Redis service...
docker-compose up -d redis

echo ⏳ Waiting for Redis to be ready...
timeout /t 5 /nobreak >nul

echo 🏗️  Building application...
gradlew.bat build -q
if errorlevel 1 (
    echo ❌ Failed to build application.
    pause
    exit /b 1
)

echo ✅ Application built successfully!
echo.
echo 🚀 Starting Spring Boot application...
echo    Application: http://localhost:8080
echo    H2 Console: http://localhost:8080/h2-console
echo    Health check: http://localhost:8080/actuator/health
echo.
echo 📚 Cache Problem Simulation Endpoints:
echo    Thunder Herd: POST /api/cache-problems/thunder-herd/simulate
echo    Cache Penetration: POST /api/cache-problems/penetration/simulate
echo    Cache Breakdown: POST /api/cache-problems/breakdown/simulate
echo    Cache Crash: POST /api/cache-problems/crash/simulate
echo.
echo Press Ctrl+C to stop the application...

REM Start the application
gradlew.bat bootRun
