Write-Host "Building the project..." -ForegroundColor Cyan
./gradlew clean
if ($LASTEXITCODE -ne 0) {
    Write-Host "Clean failed with exit code $LASTEXITCODE" -ForegroundColor Red
    exit $LASTEXITCODE
}

./gradlew build
if ($LASTEXITCODE -ne 0) {
    Write-Host "Build failed with exit code $LASTEXITCODE" -ForegroundColor Red
    exit $LASTEXITCODE
}

Write-Host "Build completed successfully!" -ForegroundColor Green 