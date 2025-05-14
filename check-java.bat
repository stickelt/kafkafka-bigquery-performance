@echo off
echo Checking Java version...
java -version
echo.
echo Checking Gradle version...
.\gradlew --version
echo.
echo Checking if Java compiler works...
mkdir -p temp
echo public class Test { public static void main(String[] args) { System.out.println("Hello, World!"); } } > temp\Test.java
javac temp\Test.java
if %ERRORLEVEL% EQU 0 (
    echo Java compiler is working!
    java -cp temp Test
) else (
    echo Java compiler failed!
)
