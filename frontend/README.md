# Map.zip PWA - 정적 배포 가이드

이 프로젝트는 S3 정적 웹사이트 호스팅을 위해 Next.js의 정적 export 기능을 사용합니다.

## 빌드 및 배포

### 1. 로컬 개발
\`\`\`bash
npm run dev
\`\`\`

### 2. 정적 빌드
\`\`\`bash
npm run build
\`\`\`

이 명령어는 `out/` 디렉토리에 정적 파일들을 생성합니다.

### 3. S3 배포 (AWS CLI 필요)
\`\`\`bash
# AWS CLI 설정 후
npm run deploy
\`\`\`

또는 수동으로:
\`\`\`bash
aws s3 sync out/ s3://your-bucket-name --delete
\`\`\`

### 4. S3 버킷 설정

#### 정적 웹사이트 호스팅 활성화
- S3 콘솔에서 버킷 선택
- Properties > Static website hosting 활성화
- Index document: `index.html`
- Error document: `404.html`

#### 버킷 정책 설정
\`\`\`json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "PublicReadGetObject",
      "Effect": "Allow",
      "Principal": "*",
      "Action": "s3:GetObject",
      "Resource": "arn:aws:s3:::your-bucket-name/*"
    }
  ]
}
\`\`\`

## 주요 변경사항

### 1. Next.js 설정 (`next.config.mjs`)
- `output: 'export'` - 정적 export 활성화
- `trailingSlash: true` - URL 끝에 슬래시 추가
- `images: { unoptimized: true }` - 이미지 최적화 비활성화

### 2. 라우팅
- 모든 링크에 trailing slash 추가 (`/schedule/`)
- 동적 라우팅 페이지에 `generateStaticParams` 함수 추가

### 3. 클라이언트 사이드 렌더링
- `useEffect`로 클라이언트 사이드 체크 (`isClient` state)
- 로컬스토리지 접근을 클라이언트에서만 수행

### 4. 빌드 스크립트
- `npm run build` - Next.js 정적 빌드
- `npm run deploy` - S3 동기화

## 파일 구조
\`\`\`
out/
├── index.html
├── schedule/
│   ├── index.html
│   ├── create/
│   │   └── index.html
│   └── edit/
│       ├── 1/
│       │   └── index.html
│       └── ...
├── visited/
│   └── index.html
├── recommendations/
│   └── index.html
├── _next/
│   └── static/
└── ...
\`\`\`

## 주의사항

1. **API Routes 사용 불가** - 모든 데이터는 클라이언트에서 처리
2. **서버 사이드 기능 제한** - getServerSideProps, middleware 등 사용 불가
3. **이미지 최적화 비활성화** - Next.js Image 컴포넌트의 최적화 기능 사용 불가
4. **환경변수** - 빌드 시점에 포함되므로 민감한 정보 주의

## 성능 최적화

- 모든 페이지가 사전 생성되어 빠른 로딩
- CDN을 통한 전 세계 배포 가능
- 서버 비용 없이 호스팅 가능
