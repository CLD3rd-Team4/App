#!/bin/bash

# Proto Descriptor 생성 스크립트
# Envoy HTTP-gRPC 변환을 위한 File Descriptor Set (.pb) 생성

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
PROTO_DIR="$PROJECT_ROOT/src/main/proto"
TARGET_DIR="$PROJECT_ROOT/target/generated-sources/protobuf"

echo "=== Proto Descriptor 생성 시작 ==="
echo "프로젝트 루트: $PROJECT_ROOT"
echo "Proto 디렉토리: $PROTO_DIR"
echo "출력 디렉토리: $TARGET_DIR"

# 출력 디렉토리 생성
mkdir -p "$TARGET_DIR"

# protoc가 설치되어 있는지 확인
if ! command -v protoc &> /dev/null; then
    echo "❌ protoc가 설치되어 있지 않습니다."
    echo "설치 방법:"
    echo "  macOS: brew install protobuf"
    echo "  Ubuntu: sudo apt-get install protobuf-compiler"
    echo "  Windows: choco install protoc"
    exit 1
fi

echo "✅ protoc 버전: $(protoc --version)"

# Google API proto 파일 경로 확인
GOOGLE_PROTO_PATH=""
if [ -d "/usr/local/include" ]; then
    GOOGLE_PROTO_PATH="/usr/local/include"
elif [ -d "/usr/include" ]; then
    GOOGLE_PROTO_PATH="/usr/include"
elif [ -d "$HOME/.local/include" ]; then
    GOOGLE_PROTO_PATH="$HOME/.local/include"
else
    echo "❌ Google API proto 파일을 찾을 수 없습니다."
    echo "google/api/annotations.proto가 필요합니다."
    echo "설치 방법: https://github.com/googleapis/api-common-protos"
    exit 1
fi

echo "✅ Google Proto 경로: $GOOGLE_PROTO_PATH"

# Proto Descriptor Set 생성
echo "📦 Proto Descriptor Set 생성 중..."

protoc \
    --proto_path="$PROTO_DIR" \
    --proto_path="$GOOGLE_PROTO_PATH" \
    --descriptor_set_out="$TARGET_DIR/review_service_descriptor.pb" \
    --include_source_info \
    --include_imports \
    "$PROTO_DIR/review.proto"

if [ $? -eq 0 ]; then
    echo "✅ Descriptor 파일 생성 완료: $TARGET_DIR/review_service_descriptor.pb"
    
    # ArgoCD 디렉토리에 복사
    ARGOCD_DIR="$PROJECT_ROOT/../../../Infra/argocd/service-review"
    if [ -d "$ARGOCD_DIR" ]; then
        cp "$TARGET_DIR/review_service_descriptor.pb" "$ARGOCD_DIR/"
        echo "✅ ArgoCD 디렉토리에 복사 완료: $ARGOCD_DIR/review_service_descriptor.pb"
    else
        echo "⚠️  ArgoCD 디렉토리를 찾을 수 없습니다: $ARGOCD_DIR"
    fi
    
    # 파일 정보 출력
    echo ""
    echo "=== 생성된 파일 정보 ==="
    ls -la "$TARGET_DIR/review_service_descriptor.pb"
    echo ""
    echo "🎉 Proto Descriptor 생성이 완료되었습니다!"
    echo ""
    echo "📝 사용 방법:"
    echo "1. Kubernetes EnvoyFilter에서 이 파일을 사용하여 HTTP-gRPC 변환 설정"
    echo "2. ConfigMap으로 descriptor 파일을 마운트"
    echo "3. Istio Gateway와 연동하여 외부 HTTP 요청을 내부 gRPC로 변환"
    
else
    echo "❌ Descriptor 파일 생성 실패"
    exit 1
fi