# MapZip Config Server

MapZip 마이크로서비스 아키텍처의 중앙 설정 서버입니다.

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

1. **Maven으로 빌드 및 실행**
   ```bash
   cd ~/mapzip/app/backend/config
   ./mvnw clean package
   java -jar target/app.jar
   ```

2. **Docker로 실행**
   ```bash
   docker build -t mapzip/config:latest .
   docker run -p 8888:8888 mapzip/config:latest
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
aws eks update-kubeconfig --region ap-northeast-2 --name mapzip-dev-eks

# 네임스페이스 생성
kubectl create namespace platform

# 배포
cd k8s
kubectl apply -f configmap.yaml
kubectl apply -f deployment.yaml
kubectl apply -f service.yaml

# 배포 상태 확인
kubectl get pods -n platform -l app=config
```

### Kubernetes 서비스 접근
- 클러스터 내부: `config.platform.svc.cluster.local:8888`
- 외부 접근: LoadBalancer를 통한 접근 가능

## 🔧 CI/CD

통합 CI/CD 파이프라인(`backend-ci-cd.yaml`)을 통한 자동 배포가 설정되어 있습니다.

### 트리거 조건
- `dev` 브랜치에 푸시
- `backend/config/**` 경로의 파일 변경

### 배포 과정
1. 변경사항 감지
2. Maven 빌드 (`mvn -B package -DskipTests`)
3. Docker 이미지 빌드 및 ECR 푸시 (`mapzip-dev-ecr-config:커밋해시`)
4. ArgoCD YAML 업데이트 (`argocd/platform/config.yaml`)
5. ArgoCD를 통한 자동 배포

## 📁 프로젝트 구조

```
config/
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
├── .mvn/
│   └── wrapper/
├── Dockerfile
├── pom.xml
├── mvnw
├── mvnw.cmd
└── README.md
```

## ⚙️ 설정 관리

### Config Repository 구조
```
backend/config-repo/
├── application.yml          # 공통 설정
├── auth/
│   └── auth.yml            # Auth 서비스 설정
├── gateway/
│   └── gateway.yml         # Gateway 서비스 설정
├── review/
│   └── review.yml          # Review 서비스 설정
├── recommend/
│   └── recommend.yml       # Recommend 서비스 설정
└── schedule/
    └── schedule.yml        # Schedule 서비스 설정
```

### 설정 접근 방법
- URL 패턴: `http://config.platform.svc.cluster.local:8888/{application}/{profile}`
- 예시: `http://config.platform.svc.cluster.local:8888/auth/dev`

## 🔍 트러블슈팅

### 일반적인 문제들

1. **Config Server가 시작되지 않는 경우**
   ```bash
   # 로그 확인
   kubectl logs -n platform deployment/config
   
   # 포트 확인
   netstat -tulpn | grep 8888
   ```

2. **설정 파일을 찾을 수 없는 경우**
   - config-repo 디렉토리 경로 확인
   - Git 저장소 URL 및 인증 정보 확인

3. **Maven 빌드 실패**
   ```bash
   # Maven 캐시 정리
   ./mvnw clean
   
   # Docker 캐시 정리
   docker system prune -f
   ```

## 🔐 보안 설정

### 암호화 키 설정
```yaml
encrypt:
  key: ${ENCRYPT_KEY}  # Kubernetes Secret으로 관리
```

### 민감한 정보 암호화
```bash
# 설정값 암호화
curl -X POST http://localhost:8888/encrypt -d "민감한정보"

# 설정값 복호화
curl -X POST http://localhost:8888/decrypt -d "암호화된값"
```

## 📚 참고 자료

- [Spring Cloud Config 공식 문서](https://spring.io/projects/spring-cloud-config)
- [AWS EKS 사용자 가이드](https://docs.aws.amazon.com/eks/)
- [Docker 공식 문서](https://docs.docker.com/)
- [ArgoCD 공식 문서](https://argo-cd.readthedocs.io/)

## 🤝 기여하기

1. 이슈 생성
2. 기능 브랜치 생성 (`git checkout -b feature/amazing-feature`)
3. 변경사항 커밋 (`git commit -m 'Add amazing feature'`)
4. 브랜치에 푸시 (`git push origin feature/amazing-feature`)
5. Pull Request 생성
