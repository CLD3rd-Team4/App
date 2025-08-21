# Github Repository

https://github.com/CLD3rd-Team4/Infra <br>
https://github.com/CLD3rd-Team4/App

<br><br>

# 🚙 Map.zip

### 장거리 이동 스케줄 맞춤형 맛집 추천 서비스

- 개요
    
    장거리 이동시 이동 스케줄과 사용자 정의 요구사항, 식사시간, 목표 도착시간, 식사 반경 등을 입력하면 원하는 식사시간에 사용자가 위치하게 될 장소를 예측하여 해당 지역의 맛집을 추천해주는 서비스
    
- 기획 의도
    
    단순 지역 맛집을 추천하는것이 아니라 사용자 입력 데이터와 AI, 경로탐색 API를 통해 장거리 이동의 모든 과정을 예측하고 최적화하여 사용자에게 계획된 즐거움을 제공한다.
    
- 개발 목표
    - 사용자에게 제시하는 목표
        - 장거리 이동 중 식사 계획에 소요되는 고민과 시간을 최소화
        - 이동 스케줄과 개인 취향에 최적화된 맛집을 추천받아 만족도 높은 식사 경험 제공
        - 검증된 리뷰 기반 추천으로 신뢰할 수 있는 음식점 선택 가능
    - 앱 구현에 필요한 기술적 목표
        - PWA로 패키징하여 모바일 앱처럼 사용 가능하게 제작
        - 오래걸리는 API에 Kafka를 사용해 비동기 처리
        - 크롤링을 사용하지 않으면서 최대한의 정보 제공이 가능한 데이터 수집
        - AI 프롬프트 최적화
        
- 핵심기능
    - 스케줄 기반 식사 예정 지점 도출
        - 출발지 ~ 도착지 경로 중 식사 시간 기준 위치 계산
    - 반경 기반 맛집 탐색
        - 사용자가 설정한 식사반경과 도출된 좌표를 기반으로 반경 내 음식점 리스트 확보
    - AI 사용자 맞춤 맛집 추천
        - 사용자가 입력한 요구사항(식사/간식, 동승자 정보, 이동목적)과 리뷰 데이터를 기반으로 AWS bedrock(claude-3-sonnet)에서 식당 3개씩 추천
    - 리뷰 작성
        - 영수증 OCR 검증을 통해 해당 식당 방문자만 리뷰 작성 가능
        
- 사용기술
    - **Frontend** : React
    - **Backend** : Java Springboot, PostgreSQL, Valkey
    - **AWS** : Cloudfront, VPC, AuroraDB, ElastiCache, MSK, SSM Parameter Store,  S3, S2S VPN, Client VPN, ECR, EKS, Bedrock, CloudWatch, SNS, Lambda, DynamoDB
    - **IaC, CI/CD** : Terraform, Github Actions, ArgoCD
    - 협업 : Github, Notion, Jira, Slack, Terraform Cloud, [Draw.io](http://Draw.io), Figma

<br><br>

# 💻 페이지별 기능 소개
<img width="1090" height="609" alt="Image" src="https://github.com/user-attachments/assets/8494473f-aca7-4e61-b9cd-13e32e0b9521" />
<img width="1089" height="612" alt="Image" src="https://github.com/user-attachments/assets/bf2b73c7-9f1d-4d84-99c7-b4b48c9dc487" />
<img width="1091" height="612" alt="Image" src="https://github.com/user-attachments/assets/4b316572-1bff-454a-b124-1c7b9f31477e" />
<img width="1090" height="613" alt="Image" src="https://github.com/user-attachments/assets/43b03a70-b3a8-4476-89ab-30379751bdb2" />
<img width="1090" height="611" alt="Image" src="https://github.com/user-attachments/assets/23b27e5a-ff37-411d-8e0c-9dfc9fe597ac" />
<img width="1089" height="613" alt="Image" src="https://github.com/user-attachments/assets/53e3d5cf-aa0a-491e-80ee-752bb1a8c5b5" />
<img width="1090" height="611" alt="Image" src="https://github.com/user-attachments/assets/dce21012-2248-4fed-97c9-a4b8cdb013a8" />


<br><br>


# 🛠 아키텍처
<img width="2048" height="1570" alt="Image" src="https://github.com/user-attachments/assets/db4bb7f2-91dc-4114-a9fa-b826e2d71ffd" />
## 인프라 및 아키텍처 개요

### 📦 프론트엔드 & 백엔드 호스팅
- 프론트엔드는 S3 - CloudFront로 정적 호스팅
- 백엔드는 EKS에서 마이크로서비스 형태로 호스팅

---

### ⚙️ 안정적인 인프라 구성
- Cluster Autoscaler, Horizontal Pod Autoscaler를 통해 리소스 자동 스케일링
- Rolling Update 및 헬스체크 설정으로 무중단 배포
- VPC 내부에 퍼블릭, 프라이빗 서브넷을 각 3개의 AZ에 구성하여 고가용성 확보

---

### 🧱 MSA (Microservice Architecture)
- Spring Cloud 기반 Gateway, Config 서버 사용
- 인증, 게이트웨이, 설정, 리뷰, 추천, 스케줄 등 서버와 DB를 모듈별로 분리하여 독립성/확장성/장애 격리 확보
- Istio를 활용한 서버 간 네트워킹, 분산 추적, HTTP-JSON 필터링 적용
- gRPC를 이용한 서비스 간 내부 API 호출

---

### ☁️ 하이브리드 클라우드 구성
- 로깅 서버를 온프레미스 Kubernetes에 구축하여 로그 데이터 관리
- S2S VPN 연결을 통한 하이브리드 통신
- Istio 멀티클러스터 설정으로 클라우드/온프레미스 연동

---

### 🗄️ 데이터베이스 설계
- **Valkey (Redis Fork)**: TTL 기능 필요 시 사용, Serverless 옵션은 비중 낮은 서비스에 적용
- **PostgreSQL**: 향후 벡터 DB 기반 RAG 시스템 구축을 고려해 채택
- **DynamoDB**: 확장성이 뛰어난 NoSQL DB
- **MSK (Kafka)**: 비동기 처리가 필요한 서비스에 메시지 큐로 사용
- DB별 자동 백업 및 Point-in-Time 복구 설정
- 읽기 전용 복제본(Read Replica) 구성으로 부하 분산

---

### 📊 모니터링 & 로깅
- Prometheus 메트릭 수집 + CloudWatch 연동
- Grafana 대시보드 구성
- DB별 Slack 알림 설정
- EFK(Elasticsearch, Fluentd, Kibana) 스택으로 로그 수집 및 시각화

---

### 🔐 보안
- 프론트엔드 및 백엔드 HTTPS 적용
- 내부 리소스 접근을 위한 Client VPN 구성
- SSM Parameter Store로 민감 정보 관리
- Spring Config 서버의 암호화 기능 활용
- Terraform Cloud 및 GitHub Actions Secret을 통한 민감 변수 관리
- Sealed Secret을 이용한 EKS 내 Secret GitOps 방식 배포
- AWS IAM 리소스 최소 권한 원칙 적용
- GitHub Actions용 OIDC 설정
- 백엔드 Gateway에 XSS 필터링 적용

---

### 🧪 환경 분리 전략
- Terraform Workspace를 `prod`, `staging`, `dev`로 구분
- GitHub 브랜치와 연동하여 환경별 변수 자동 설정

 
<br><br>

# 🔎 MSA 아키텍처
<img width="1088" height="609" alt="image" src="https://github.com/user-attachments/assets/44a6aa1f-2d13-4945-bcf8-86eda47405f4" />

<br><br>

# ⚙CI/CD 파이프라인

- **Backend CI/CD**
    - **변경 감지**: Git diff로 변경된 마이크로서비스만 식별
    - **병렬 빌드**: Matrix 전략으로 여러 서비스 동시 빌드
    - **컨테이너화**: Maven 빌드 → Docker 이미지 생성 → ECR 푸시
    - **GitOps 연동**: 성공한 서비스만 Infra 레포의 ArgoCD YAML 업데이트
- **Frontend CI/CD**
    - **정적 사이트 생성**: Next.js 빌드로 정적 파일 생성
    - **AWS 배포**: S3 업로드 + CloudFront 캐시 무효화
    - **환경 변수 주입**: 빌드 시 API URL, 카카오맵 키 등 설정
- **Config CI/CD**
    - **설정 변경 감지**: 개별 서비스 설정 vs 공통 설정 구분
    - **중복 배포 방지**: Backend 변경과 겹치는 서비스 제외
    - **무중단 재시작**: kubectl로 Kubernetes 배포 롤링 재시작
- **Protocol Buffer CI/CD**
    - **Proto 파일 수집**: 모든 .pb 파일을 하나의 이미지로 패키징
    - **InitContainer 업데이트**: 서비스 시작 전 최신 Proto 파일 복사
    - **자동 배포**: ArgoCD YAML의 InitContainer 이미지 태그 업데이트
- **Jira 연동**
    - **이슈 동기화**: GitHub 이슈 → Jira 티켓 자동 생성
    - **브랜치 생성**: Jira 티켓 번호로 개발 브랜치 자동 생성
    - **상태 동기화**: GitHub 이슈 종료 시 Jira 티켓도 완료 처리

<br><br>

# 📊모니터링
<img width="1092" height="614" alt="image" src="https://github.com/user-attachments/assets/34e4f78a-0444-4812-924b-8477afd6f09d" />
<img width="1087" height="612" alt="image" src="https://github.com/user-attachments/assets/a67745af-805b-4cd6-bf4b-8afe401eb312" />
<img width="1083" height="608" alt="image" src="https://github.com/user-attachments/assets/06551a84-ca92-473e-89cd-75fa6de6c8e3" />
<img width="1087" height="611" alt="image" src="https://github.com/user-attachments/assets/caf318dd-9536-42a3-ba88-338579a3cee8" />
<img width="1092" height="610" alt="image" src="https://github.com/user-attachments/assets/7da9bed3-c18f-4209-b210-4cbe23478eec" />
<img width="1091" height="609" alt="image" src="https://github.com/user-attachments/assets/28de1246-4a06-482b-9ae8-4363563f91de" />
<img width="1088" height="607" alt="image" src="https://github.com/user-attachments/assets/e87899b4-8e77-4196-860c-8dc1687d70b3" />

<br><br>


# 📝 k6 성능테스트
수행 로직이 많은 추천 결과 요청 조회 외에는 모두 준수한 성능을 보였다.
<img width="1085" height="609" alt="image" src="https://github.com/user-attachments/assets/02d91c50-4890-4ee2-9530-8c6bc060e963" />
<img width="1086" height="605" alt="image" src="https://github.com/user-attachments/assets/59f95d6b-5441-4016-806d-8bbc964a00c7" />
<img width="1082" height="606" alt="image" src="https://github.com/user-attachments/assets/e12454c1-c7be-41d5-9a65-48bb93f9cb01" />
<img width="1086" height="606" alt="image" src="https://github.com/user-attachments/assets/3dc95642-0d63-4a18-95ef-5edf06194c29" />

부하테스트는 비용과 환경 문제로 특정 API에서만 실시하였고 빠른 스케일링 설정으로 트래픽에 유연하게 대응하도록 하였다.
<img width="1084" height="606" alt="image" src="https://github.com/user-attachments/assets/87559223-758d-4692-ab7b-6105e6054d87" />

<br><br>

# 🎈 **결과**

- 기대효과
    - 사용자의 이동 스케줄과 개인 취향을 고려해, 만족스러운 식사 경험을 제공한다.
    - MSA 구조와 하이브리드 클라우드 기반 인프라로 확장성과 장애 대응 능력을 확보하고 Kafka와 gRPC 등을 활용해 빠르고 효율적인 내부 통신으로 원활한 경험을 제공한다.
    - 자동화된 CI/CD와 GitOps 배포로 개발 및 디버깅을 가속화하며, 실시간 모니터링 체계로 안정적이고 신뢰성 높은 서비스를 제공한다.
- 향후 프로젝트 계획
    - 데이터베이스 분리로 인한 데이터 일관성 처리
    - Prometheus, alertmanager를 slack과 연동
    - AI 추천 로직 RAG 시스템으로 최적화

<br><br>


# 👩🏻‍💻 역할 분담
| 이름   | 담당 역할 |
|--------|-----------|
| **박시윤** | - EKS 내부 프로그램 설치, 멀티 클러스터, Istio 설정<br>- 모니터링 및 분산 추적<br>- Gateway 서버 구축<br>- K6 테스트<br>- 협업 툴 연동 및 프론트엔드, 백엔드 CI/CD 구축 |
| **한동연** | - EKS 클러스터 인프라 구축<br>- Spring Authorization 서버 기반 인증 서버 프론트, 백엔드 구현<br>- Redis 기반 토큰 관리 |
| **서예은** | - Route53, HTTPS, ECR, S2S VPN 인프라 구축<br>- 추천 서버 구축<br>- MSK, Bedrock 연동<br>- 프론트엔드 초안 작성 |
| **양정모** | - Aurora DB RDS 클러스터 인프라 구축<br>- 정적 웹사이트 호스팅 S3 리소스 구성<br>- 스케줄 서버 CRUD 작성 및 추천 서버 Tmap 로직 기여 (Backend) |
| **조성욱** | - 네트워크 (VPC, 서브넷, IGW) 인프라 구축<br>- CloudWatch 알람, SNS 토픽, Lambda 함수 기반 실시간 Slack 알람 시스템 개발<br>- Config Server 구축<br>- Config CI/CD 구축 |
| **조성민** | - MSK, DynamoDB, ElastiCache 인프라 구축<br>- Review 서버 백엔드, 프론트엔드, 모니터링 구축<br>- Google Vision API 연동<br>- K6 테스트 |



