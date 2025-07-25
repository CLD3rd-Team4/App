# MapZip Config Server

MapZip 마이크로서비스 아키텍처의 중앙 설정 서버입니다.

## 🧪 CI/CD 테스트
- 테스트 일시: 2025-07-25
- 테스트 목적: GitHub Actions 워크플로우 동작 확인

## 🏗️ 아키텍처

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Gateway       │    │   Auth Service  │    │  Review Service │
│   (Port: 8080)  │    │   (Port: 8081)  │    │   (Port: 8083)  │
└─────────┬───────┘    └─────────┬───────┘    └─────────┬───────┘
          │                      │                      │
          └──────────────────────┼──────────────────────┘
                                 │
                    ┌─────────────▼───────────┐
                    │    Config Server       │
                    │    (Port: 8888)        │
                    └─────────────────────────┘
```

## 🚀 빠른 시작

### 로컬 개발 환경

1. **애플리케이션 빌드 및 실행**
   ```bash
   cd ~/mapzip/app/backend/config/config-server
   ./scripts/build.sh
   ```

2. **Docker로 실행**
   ```bash
   docker run -p 8888:8888 mapzip/config-server:latest
   ```

3. **Docker Compose로 실행**
   ```bash
   cd ~/mapzip/app/backend
   docker-compose up config-server
   ```

### 설정 확인

Config Server가 실행되면 다음 URL에서 설정을 확인할 수 있습니다:

- 헬스체크: http://localhost:8888/actuator/health
- Gateway 설정: http://localhost:8888/gateway/default
- Auth 설정: http://localhost:8888/auth/default

## 🐳 Docker 배포

### 1. 로컬 빌드
```bash
./scripts/build.sh
```

### 2. ECR에 푸시
```bash
./scripts/push-to-ecr.sh [태그명]
```

## ☸️ Kubernetes 배포

### 전제조건
- AWS CLI 설치 및 설정
- kubectl 설치
- EKS 클러스터 접근 권한

### 배포 명령어
```bash
# kubeconfig 업데이트
aws eks update-kubeconfig --region ap-northeast-2 --name mapzip-cluster

# 네임스페이스 생성
kubectl create namespace mapzip

# 배포
cd k8s
kubectl apply -f configmap.yaml
kubectl apply -f deployment.yaml
kubectl apply -f service.yaml

# 배포 상태 확인
kubectl get pods -n mapzip -l app=config-server
```

## 🔧 CI/CD

GitHub Actions를 통한 자동 배포가 설정되어 있습니다.

### 트리거 조건
- `main` 브랜치에 푸시
- `backend/config/config-server/**` 경로의 파일 변경

### 필요한 GitHub Secrets
```
AWS_ACCESS_KEY_ID=your-access-key
AWS_SECRET_ACCESS_KEY=your-secret-key
```

### 배포 과정
1. 코드 체크아웃
2. Java 17 설정
3. 테스트 실행
4. Docker 이미지 빌드
5. ECR에 푸시
6. EKS에 배포
7. 배포 검증

## 📁 프로젝트 구조

```
config-server/
├── src/
│   └── main/
│       ├── java/
│       │   └── com/mapzip/config/
│       │       └── ConfigServerApplication.java
│       └── resources/
│           └── application.yml
├── k8s/
│   ├── deployment.yaml
│   ├── service.yaml
│   └── configmap.yaml
├── scripts/
│   ├── build.sh
│   └── push-to-ecr.sh
├── Dockerfile
├── build.gradle
└── README.md
```

## 🔍 트러블슈팅

### 일반적인 문제들

1. **Config Server가 시작되지 않는 경우**
   ```bash
   # 로그 확인
   kubectl logs -n mapzip deployment/config-server
   
   # 포트 확인
   netstat -tulpn | grep 8888
   ```

2. **설정 파일을 찾을 수 없는 경우**
   - config-repo 디렉토리 경로 확인
   - Git 저장소 URL 및 인증 정보 확인

3. **Docker 빌드 실패**
   ```bash
   # Gradle 캐시 정리
   ./gradlew clean
   
   # Docker 캐시 정리
   docker system prune -f
   ```

## 📚 참고 자료

- [Spring Cloud Config 공식 문서](https://spring.io/projects/spring-cloud-config)
- [AWS EKS 사용자 가이드](https://docs.aws.amazon.com/eks/)
- [Docker 공식 문서](https://docs.docker.com/)

## 🤝 기여하기

1. 이슈 생성
2. 기능 브랜치 생성 (`git checkout -b feature/amazing-feature`)
3. 변경사항 커밋 (`git commit -m 'Add amazing feature'`)
4. 브랜치에 푸시 (`git push origin feature/amazing-feature`)
5. Pull Request 생성
