@echo off
echo Building the project with Groovy build file...
call ./gradlew clean --build-file build.gradle
if errorlevel 1 (
    echo Clean failed
    exit /b %errorlevel%
)

call ./gradlew build --build-file build.gradle
if errorlevel 1 (
    echo Build failed
    exit /b %errorlevel%
)

echo Build completed!
echo.
echo Now trying to run the simple test...
call ./gradlew runSimpleTest --build-file build.gradle
if errorlevel 1 (
    echo Simple test failed
    exit /b %errorlevel%
)
echo Simple test completed! 