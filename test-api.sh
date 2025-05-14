#!/bin/bash

# Check if the service is running
echo "Testing health endpoint..."
curl -s http://localhost:8080/api/health
echo

# Generate and send test data to the write API endpoint
echo "Testing write API endpoint with 10 messages..."
curl -s -X POST -H "Content-Type: application/json" -d '{"messageCount":10, "batchSize":5}' http://localhost:8080/api/test-write-api
echo

# Generate and send test data to the legacy endpoint
echo "Testing legacy endpoint with 10 messages..."
curl -s -X POST -H "Content-Type: application/json" -d '{"messageCount":10, "batchSize":5}' http://localhost:8080/api/test-legacy
echo 