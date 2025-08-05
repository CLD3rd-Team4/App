#!/bin/bash

# proto descriptor 생성 스크립트
# gRPC-JSON transcoding을 위한 .pb 파일 생성

set -e

# 현재 스크립트 위치 기반으로 프로젝트 루트 찾기
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# 경로 설정
PROTO_DIR="$PROJECT_ROOT/src/main/proto"
OUTPUT_DIR="$PROJECT_ROOT"
GOOGLEAPIS_DIR="$PROJECT_ROOT/googleapis"

echo "🔄 Generating proto descriptor for review service..."
echo "Project root: $PROJECT_ROOT"
echo "Proto directory: $PROTO_DIR"

# googleapis 존재 확인
if [ ! -d "$GOOGLEAPIS_DIR" ]; then
    echo "📥 Cloning googleapis..."
    cd "$PROJECT_ROOT"
    git clone https://github.com/googleapis/googleapis.git
fi

# protoc 설치 확인
if ! command -v protoc &> /dev/null; then
    echo "❌ protoc이 설치되지 않았습니다. 먼저 protoc를 설치해주세요."
    echo "Windows: choco install protoc"
    echo "macOS: brew install protobuf"
    echo "또는 https://github.com/protocolbuffers/protobuf/releases 에서 다운로드"
    exit 1
fi

echo "✅ protoc version: $(protoc --version)"

# .pb 파일 생성
cd "$PROJECT_ROOT"

echo "🔨 Generating review_proto.pb..."
protoc \
    -I"$GOOGLEAPIS_DIR" \
    -I"$PROTO_DIR" \
    --include_imports \
    --include_source_info \
    --descriptor_set_out=review_proto.pb \
    "$PROTO_DIR/review.proto"

if [ -f "review_proto.pb" ]; then
    echo "✅ Successfully generated review_proto.pb"
    echo "📁 File location: $PROJECT_ROOT/review_proto.pb"
    echo "📊 File size: $(du -h review_proto.pb | cut -f1)"
else
    echo "❌ Failed to generate review_proto.pb"
    exit 1
fi

echo "🎉 Proto descriptor generation completed!"