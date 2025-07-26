#!/bin/bash

# ECR에 이미지 푸시하는 스크립트

set -e

# 설정값들
AWS_REGION="ap-northeast-2"
ECR_REPOSITORY="mapzip/config-server"
IMAGE_TAG="${1:-latest}"

echo "🚀 ECR에 이미지를 푸시합니다..."
echo "📍 리전: $AWS_REGION"
echo "📦 저장소: $ECR_REPOSITORY"
echo "🏷️  태그: $IMAGE_TAG"

# AWS CLI 설치 확인
if ! command -v aws &> /dev/null; then
    echo "❌ AWS CLI가 설치되지 않았습니다."
    echo "설치 방법: https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html"
    exit 1
fi

# AWS 자격증명 확인
if ! aws sts get-caller-identity &> /dev/null; then
    echo "❌ AWS 자격증명이 설정되지 않았습니다."
    echo "aws configure를 실행하여 자격증명을 설정하세요."
    exit 1
fi

# ECR 로그인
echo "🔐 ECR에 로그인합니다..."
aws ecr get-login-password --region $AWS_REGION | docker login --username AWS --password-stdin $(aws sts get-caller-identity --query Account --output text).dkr.ecr.$AWS_REGION.amazonaws.com

# ECR 저장소 존재 확인 및 생성
echo "📋 ECR 저장소를 확인합니다..."
if ! aws ecr describe-repositories --repository-names $ECR_REPOSITORY --region $AWS_REGION &> /dev/null; then
    echo "📦 ECR 저장소를 생성합니다..."
    aws ecr create-repository --repository-name $ECR_REPOSITORY --region $AWS_REGION
fi

# 이미지 태그 설정
ECR_REGISTRY=$(aws sts get-caller-identity --query Account --output text).dkr.ecr.$AWS_REGION.amazonaws.com
FULL_IMAGE_NAME="$ECR_REGISTRY/$ECR_REPOSITORY:$IMAGE_TAG"

# 이미지 태그 변경
echo "🏷️  이미지 태그를 변경합니다..."
docker tag mapzip/config-server:latest $FULL_IMAGE_NAME

# 이미지 푸시
echo "⬆️  이미지를 푸시합니다..."
docker push $FULL_IMAGE_NAME

echo "✅ ECR 푸시가 완료되었습니다!"
echo "🔗 이미지 URI: $FULL_IMAGE_NAME"
