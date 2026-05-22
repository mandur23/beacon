# Beacon

Raspberry Pi 허브 + Spring Boot + Suricata + Python ML로 구성된 통합 보안 모니터링 시스템입니다.  
네트워크 이벤트, 에이전트 상태, 트래픽 이상 징후를 한 곳에서 수집/분석/조회할 수 있습니다.

![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.3-green)
![Python](https://img.shields.io/badge/Python-3.10+-blue)
![MySQL](https://img.shields.io/badge/MySQL-8.0-blue)

## 핵심 기능

- Suricata 이벤트 및 Syslog 기반 보안 이벤트 수집
- 트래픽 로그 저장/분석 및 이상 탐지 점수화
- JWT 인증 기반 API 보호 및 대시보드 접근 제어
- 에이전트 등록/하트비트/오프라인 상태 관리
- Python ML 서비스 연동(단건/배치 이상 탐지, 모델 상태 확인)
- 로컬 Ollama 기반 AI 분석/채팅(무료 모델 선택, 다운로드 진행률/로그 표시)
- Thymeleaf 기반 운영 대시보드(`/dashboard`, `/threats`, `/network` 등)

## 기술 스택

- Backend: Java 21, Spring Boot 4.0.3, Spring Security, JPA, Flyway, WebSocket, Actuator
- DB: MySQL (필수), Redis (선택)
- ML: Python 3.10+, Flask (`python-ml`)
- IDS: Suricata (환경에 따라 EVE 파일 폴링 및 Syslog 연동)

## 아키텍처 개요

```text
[Client Agents] --(HTTPS API)--> [Spring Boot Beacon] --(JPA)--> [MySQL]
         |                             |
         |                             +--(HTTP)--> [Python ML Service]
         |
[Suricata EVE/Syslog] -------------> [Event Ingestion]
```

## 빠른 시작

### 1) 필수 요구사항

- JDK 21
- MySQL 8+
- Python 3.10+
- (선택) Redis
- (운영 시 권장) Suricata

### 2) 환경변수 설정

루트의 `.env.example`을 복사해 `.env`를 만들고 값을 채웁니다.

```bash
cp .env.example .env
```

Windows PowerShell:

```powershell
Copy-Item .env.example .env
```

필수값:

- `BEACON_DB_PASSWORD`
- `BEACON_JWT_SECRET` (최소 32바이트)

주요값:

- `BEACON_DB_URL`, `BEACON_DB_USERNAME`
- `ML_SERVICE_URL` (기본 `http://localhost:5000`)
- `SYSLOG_PORT` (기본 `514`)
- `SERVER_SSL_ENABLED` (기본 `true`, HTTPS 사용)
- `SERVER_SSL_KEYSTORE_PATH`, `SERVER_SSL_KEYSTORE_PASSWORD`, `SERVER_SSL_KEY_ALIAS`
- `SERVER_SSL_CERTIFICATE`, `SERVER_SSL_CERTIFICATE_PRIVATE_KEY` (PEM 사용 시)

HTTPS 기본 기동을 위해 `src/main/resources/keystore.p12`가 필요합니다.

Windows(PowerShell)에서 개발용 인증서 생성:

```powershell
& "C:\Program Files\Java\jdk-21\bin\keytool.exe" -genkeypair `
  -alias 1 -keyalg RSA -keysize 2048 -storetype PKCS12 `
  -keystore "src/main/resources/keystore.p12" `
  -storepass changeit -keypass changeit -validity 3650 `
  -dname "CN=localhost, OU=Dev, O=Beacon, L=Seoul, ST=Seoul, C=KR" `
  -ext "SAN=dns:localhost,ip:127.0.0.1" -noprompt
```

브라우저 경고를 줄이려면(선택) 현재 사용자 루트 저장소에 인증서 설치:

```powershell
& "C:\Program Files\Java\jdk-21\bin\keytool.exe" -exportcert -alias 1 `
  -keystore "src/main/resources/keystore.p12" -storepass changeit -rfc `
  -file "$env:TEMP\beacon-localhost.cer"
certutil -user -addstore Root "$env:TEMP\beacon-localhost.cer"
```

### 3) 백엔드 실행

macOS/Linux:

```bash
./gradlew clean build
./gradlew bootRun
```

Windows:

```powershell
.\gradlew.bat clean build
.\gradlew.bat bootRun
```

기본 주소: `https://localhost:8080`

Windows HTTPS 자동 실행:

```powershell
powershell -ExecutionPolicy Bypass -File .\start-dev.ps1
```

`start-dev.ps1`는 `mkcert -install`(최초 1회 신뢰 루트 등록), `keystore.p12` 생성, `.env`의 SSL 값 동기화 후 서버를 실행합니다.

운영에서 공인 인증서 사용:

- PKCS12 파일이 있으면 `SERVER_SSL_KEYSTORE_PATH`를 해당 파일 경로로 지정
- Let's Encrypt PEM을 쓰면 아래처럼 지정

```bash
SERVER_SSL_ENABLED=true
SERVER_SSL_CERTIFICATE=/etc/letsencrypt/live/example.com/fullchain.pem
SERVER_SSL_CERTIFICATE_PRIVATE_KEY=/etc/letsencrypt/live/example.com/privkey.pem
```

참고: `SERVER_SSL_CERTIFICATE`/`SERVER_SSL_CERTIFICATE_PRIVATE_KEY` 또는 외부 `SERVER_SSL_KEYSTORE_PATH`가 지정되면, 개발용 `mkcert` 자동 생성은 건너뜁니다.

Windows AI 서버 내부 실행(권장):

```powershell
powershell -ExecutionPolicy Bypass -File .\start-ai-server.ps1
```

`start-ai-server.ps1`는 Docker를 사용하지 않고 로컬 Ollama 자동 설치/실행 모드로 동작하며, 모델 다운로드 위치를 프로젝트 내부 `.runtime/ollama`로 고정한 뒤 서버를 실행합니다.

### 4) Python ML 서비스 실행

```bash
cd python-ml
python -m venv .venv
source .venv/bin/activate   # Windows: .venv\Scripts\Activate.ps1
python -m pip install -r requirements.txt
python app.py
```

기본 주소: `http://localhost:5000`

### 5) AI(Ollama) 모델 준비

- 접속 경로: `/reports`
- AI 패널에서 모델을 선택하고 **`선택 모델 다운로드`** 버튼으로 다운로드/적용
- 다운로드 중에는 진행률(%)과 로그가 실시간 표시됩니다.
- `/api/ai/bootstrap`는 관리자 권한(`ROLE_ADMIN`)이 필요합니다.

기본 카탈로그(무료 로컬 모델):

- `llama3.2:3b`
- `qwen2.5:3b`
- `phi3:mini`
- `mistral:7b-instruct`
- `gemma2:2b`

### 6) 상태 확인

- 백엔드 헬스: `GET /actuator/health`
- ML 헬스: `GET http://localhost:5000/health`
- AI 상태: `GET /api/ai/status`

## 주요 API 경로

인증/사용자:

- `/api/auth` (`login`, `register`, `verify`)
- `/api/users`

보안/트래픽:

- `/api/security-events`
- `/api/traffic`
- `/api/threats`
- `/api/network/topology`

에이전트/방화벽:

- `/api/agents`
- `/api/admin/agents`
- `/api/firewall`
- `/api/event-blocking-policies`

머신러닝/리포트/시스템:

- `/api/ml`
- `/api/ai` (`status`, `catalog`, `bootstrap`, `summary`, `chat`, `traffic-analysis`)
- `/api/reports`
- `/api/system/stats`

웹 라우트:

- `/dashboard`, `/threats`, `/network`, `/access`, `/firewall`, `/reports`, `/agents`, `/login`

## 디렉토리 구조

```text
.
├─ src/main/java/com/example/beacon
│  ├─ controller
│  ├─ service
│  ├─ repository
│  ├─ entity
│  └─ config
├─ src/main/resources
│  ├─ application.yaml
│  ├─ db/migration
│  ├─ templates
│  └─ static
└─ python-ml
   ├─ app.py
   ├─ requirements.txt
   └─ README.md
```

## 개발 메모

- DB 스키마는 Flyway로 관리되며 앱 시작 시 마이그레이션됩니다.
- JWT 비밀키가 비어 있거나 짧으면 앱이 시작되지 않습니다.
- Suricata 연동은 환경변수(`SURICATA_EVE_ENABLED`, `SURICATA_EVE_PATH`)로 제어됩니다.

## 트러블슈팅

- `Access denied for user`: `.env`의 DB 계정/비밀번호 확인
- `Connection refused localhost:5000`: ML 서비스 실행 여부 확인
- `JWT secret` 관련 기동 실패: `BEACON_JWT_SECRET` 길이(32바이트 이상) 확인
- AI 모델 다운로드가 시작되지 않음: `/api/ai/bootstrap` 권한(관리자) 및 `OLLAMA_AUTO_INSTALL/ALLOW_AUTO_INSTALL` 값 확인
- AI 모델 전환 후 반영이 늦음: `/api/ai/status`의 `inProgress`, `downloadPercent`, `message` 확인
- 외부 접속 불가: 방화벽/포트(`8080`, `514/udp`) 및 SSL 설정 확인

## 라이선스

프로젝트 라이선스는 `LICENSE` 파일을 따릅니다.
