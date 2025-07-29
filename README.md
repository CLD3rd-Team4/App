  인프라 주요 변경 이력 (ElastiCache, DynamoDB, MSK 중심)

  ---

  1. ElastiCache (Valkey) 모듈 (./modules/elasticache)

  1-1. 모듈의 역할
   - AWS의 인메모리 데이터 스토어 서비스인 ElastiCache (Valkey 엔진) 클러스터를 생성하고 관리합니다.
   - 빠른 데이터 접근이 필요한 서비스(예: 인증 토큰 저장, 캐싱)를 위해 사용됩니다.
   - 클러스터 노드, 파라미터 그룹, 네트워크 설정(보안 그룹, 서브넷 그룹)을 관리합니다.

  1-2. 루트 모듈(`main.tf`)에서의 사용법
   - module "elasticache_auth", module "elasticache_recommend" 와 같이 서비스별로 모듈을 호출합니다.
   - name_prefix, cluster_name, node_type 등 필요한 값을 변수로 전달하여 클러스터를 구성합니다.

  1-3. (중요) Valkey 엔진 사용
   - ElastiCache 모듈은 Valkey 엔진을 사용하도록 설정되어 있습니다.
   - AWS 정책상 Valkey 엔진은 aws_elasticache_cluster가 아닌 aws_elasticache_replication_group 리소스를 통해 생성해야 합니다. 모듈 내부 코드가 이에 맞게 작성되어 있습니다.

  ---

  2. DynamoDB 모듈 (./modules/dynamodb)

  2-1. 모듈의 역할
   - AWS의 NoSQL 데이터베이스 서비스인 DynamoDB 테이블을 생성하고 관리합니다.
   - 스키마가 유연하고 확장성이 중요한 데이터(예: 리뷰, 사용자 프로필)를 저장하는 데 사용됩니다.
   - 테이블 이름, 파티션 키, 정렬 키, 속성 등을 정의합니다.

  2-2. 루트 모듈(`main.tf`)에서의 사용법
   - module "dynamodb" 블록을 통해 모듈을 호출합니다.
   - table_name, attributes, hash_key 등의 변수를 통해 테이블 스키마를 정의합니다.

  ---

  3. MSK (Managed Streaming for Kafka) 모듈 (./modules/msk)

  3-1. 모듈의 역할
   - AWS의 완전관리형 Apache Kafka 서비스인 MSK 클러스터를 생성하고 관리합니다.
   - 서비스 간 비동기 메시지 통신, 이벤트 스트리밍, 실시간 데이터 파이프라인 구축을 위해 사용됩니다.
   - 클러스터 이름, 브로커 노드 수, Kafka 버전, 네트워크 설정 등을 관리합니다.

  3-2. 루트 모듈(`main.tf`)에서의 사용법
   - module "msk" 블록을 통해 모듈을 호출합니다.
   - cluster_name, kafka_version, number_of_broker_nodes 등 클러스터 구성을 위한 변수를 전달합니다.
   - MSK는 Private Subnet에 배포되며, EKS 클러스터나 다른 서비스들이 접근할 수 있도록 보안 그룹이 설정되어 있습니다.

  ---

  4. 팀원 유의사항 (필독)

  4-1. 모듈화 및 일관성
   - 모든 신규 서비스(ElastiCache, DynamoDB, MSK)는 재사용성과 관리 용이성을 위해 Terraform 모듈로 구성되었습니다.
   - 각 모듈은 variables.tf를 통해 필요한 설정을 주입받으며, outputs.tf를 통해 생성된 리소스의 정보(예: 클러스터 엔드포인트, 테이블 ARN)를 외부에 제공합니다.

  4-2. 명명 규칙 (Naming Convention)
   - 모든 리소스는 name_prefix 변수(예: mapzip-dev-)를 사용하여 일관된 명명 규칙을 따릅니다. 이를 통해 리소스의 용도와 환경(dev, prod)을 쉽게 식별할 수 있습니다.

  4-3. 네트워크 및 보안
   - 각 모듈은 main.tf에서 전달받은 VPC ID와 서브넷 ID를 사용하여 기존 네트워크 환경 내에 배포됩니다.
   - 서비스 간 통신은 보안 그룹(Security Group)을 통해 제어됩니다. 각 모듈은 필요한 보안 그룹 ID를 변수로 전달받거나 직접 생성하여 사용합니다.
