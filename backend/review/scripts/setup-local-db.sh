#!/bin/bash

# Setup script for local DynamoDB development environment
echo "Setting up local DynamoDB for Review Service..."

# Wait for DynamoDB Local to be ready
echo "Waiting for DynamoDB Local to be ready..."
sleep 10

# Set AWS CLI configuration for local development
export AWS_ACCESS_KEY_ID=dummy
export AWS_SECRET_ACCESS_KEY=dummy
export AWS_DEFAULT_REGION=ap-northeast-2

# DynamoDB Local endpoint
DYNAMODB_ENDPOINT="http://localhost:8000"

# Create reviews table
echo "Creating reviews table..."
aws dynamodb create-table \
    --endpoint-url $DYNAMODB_ENDPOINT \
    --table-name reviews-dev \
    --attribute-definitions \
        AttributeName=restaurantId,AttributeType=S \
        AttributeName=reviewId,AttributeType=S \
        AttributeName=userId,AttributeType=S \
        AttributeName=createdAt,AttributeType=S \
    --key-schema \
        AttributeName=restaurantId,KeyType=HASH \
        AttributeName=reviewId,KeyType=RANGE \
    --provisioned-throughput \
        ReadCapacityUnits=5,WriteCapacityUnits=5 \
    --global-secondary-indexes \
        IndexName=UserIdIndex,KeySchema=[{AttributeName=userId,KeyType=HASH},{AttributeName=createdAt,KeyType=RANGE}],Projection={ProjectionType=ALL},ProvisionedThroughput={ReadCapacityUnits=5,WriteCapacityUnits=5}

# Wait for table to be created
echo "Waiting for table to be active..."
aws dynamodb wait table-exists --endpoint-url $DYNAMODB_ENDPOINT --table-name reviews-dev

# Create S3 bucket in LocalStack
echo "Creating S3 bucket in LocalStack..."
aws --endpoint-url=http://localhost:4566 s3 mb s3://mapzip-images-dev --region ap-northeast-2

# Insert sample data for testing
echo "Inserting sample review data..."
aws dynamodb put-item \
    --endpoint-url $DYNAMODB_ENDPOINT \
    --table-name reviews-dev \
    --item '{
        "restaurantId": {"S": "restaurant-1"},
        "reviewId": {"S": "review-1"},
        "userId": {"S": "user-1"},
        "restaurantName": {"S": "맛있는 식당"},
        "restaurantAddress": {"S": "서울시 강남구 테헤란로 123"},
        "rating": {"N": "5"},
        "content": {"S": "정말 맛있었습니다!"},
        "imageUrls": {"L": []},
        "visitDate": {"S": "2024-01-15"},
        "isVerified": {"BOOL": true},
        "createdAt": {"S": "2024-01-15T10:30:00Z"},
        "updatedAt": {"S": "2024-01-15T10:30:00Z"}
    }'

aws dynamodb put-item \
    --endpoint-url $DYNAMODB_ENDPOINT \
    --table-name reviews-dev \
    --item '{
        "restaurantId": {"S": "restaurant-2"},
        "reviewId": {"S": "review-2"},
        "userId": {"S": "user-1"},
        "restaurantName": {"S": "좋은 카페"},
        "restaurantAddress": {"S": "서울시 서초구 강남대로 456"},
        "rating": {"N": "4"},
        "content": {"S": "분위기가 좋아요"},
        "imageUrls": {"L": []},
        "visitDate": {"S": "2024-01-20"},
        "isVerified": {"BOOL": false},
        "createdAt": {"S": "2024-01-20T14:20:00Z"},
        "updatedAt": {"S": "2024-01-20T14:20:00Z"}
    }'

echo "Local DynamoDB setup completed!"
echo ""
echo "Access DynamoDB Admin UI at: http://localhost:8001"
echo "LocalStack S3 endpoint: http://localhost:4566"
echo "Review Service will be available at: http://localhost:8080"
echo ""
echo "To test the setup:"
echo "curl http://localhost:8080/api/reviews/health"