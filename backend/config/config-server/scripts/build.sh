#!/bin/bash

# Config Server 빌드 스크립트

set -e  # 에러 발생시 스크립트 중단

echo "🚀 Config Server 빌드를 시작합니다..."

# 현재 디렉토리 확인
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

cd "$PROJECT_DIR"

echo "📁 작업 디렉토리: $(pwd)"

# Gradle 권한 설정
chmod +x ./gradlew

# 테스트 실행
echo "🧪 테스트를 실행합니다..."
./gradlew test

# 빌드 실행
echo "🔨 애플리케이션을 빌드합니다..."
./gradlew bootJar

# Docker 이미지 빌드
echo "🐳 Docker 이미지를 빌드합니다..."
docker build -t mapzip/config-server:latest .

echo "✅ 빌드가 완료되었습니다!"
echo "📦 생성된 JAR: $(ls -la build/libs/*.jar)"
echo "🐳 Docker 이미지: mapzip/config-server:latest"
