Write-Host "Building the project with Groovy build file..." -ForegroundColor Cyan
./gradlew clean --build-file build.gradle
if ($LASTEXITCODE -ne 0) {
    Write-Host "Clean failed with exit code $LASTEXITCODE" -ForegroundColor Red
    exit $LASTEXITCODE
}

./gradlew build --build-file build.gradle
if ($LASTEXITCODE -ne 0) {
    Write-Host "Build failed with exit code $LASTEXITCODE" -ForegroundColor Red
    exit $LASTEXITCODE
}

Write-Host "Build completed successfully!" -ForegroundColor Green

Write-Host "Now trying to run the simple test..." -ForegroundColor Cyan
./gradlew runSimpleTest --build-file build.gradle
if ($LASTEXITCODE -ne 0) {
    Write-Host "Simple test failed with exit code $LASTEXITCODE" -ForegroundColor Red
    exit $LASTEXITCODE
}

Write-Host "Simple test completed successfully!" -ForegroundColor Green 