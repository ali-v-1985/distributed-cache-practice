#!/bin/bash

echo "🚀 Starting Distributed Cache Practice Application..."
echo ""

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    echo "❌ Docker is not running. Please start Docker first."
    exit 1
fi

echo "📋 Starting Redis service..."
docker-compose up -d redis

echo "⏳ Waiting for Redis to be ready..."
sleep 5

echo "🏗️  Building application..."
if ./gradlew build -q; then
    echo "✅ Application built successfully!"
else
    echo "❌ Failed to build application. Check the output above."
    exit 1
fi

echo ""
echo "🚀 Starting Spring Boot application..."
echo "   Application: http://localhost:8080"
echo "   H2 Console: http://localhost:8080/h2-console"
echo "   Health check: http://localhost:8080/actuator/health"
echo ""
echo "📚 Cache Problem Simulation Endpoints:"
echo "   Thunder Herd: POST /api/cache-problems/thunder-herd/simulate"
echo "   Cache Penetration: POST /api/cache-problems/penetration/simulate"
echo "   Cache Breakdown: POST /api/cache-problems/breakdown/simulate"
echo "   Cache Crash: POST /api/cache-problems/crash/simulate"
echo ""
echo "Press Ctrl+C to stop the application..."

# Start the application
./gradlew bootRun
