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
- Thymeleaf 기반 운영 대시보드(`/dashboard`, `/threats`, `/network` 등)

## 기술 스택

- Backend: Java 21, Spring Boot 4.0.3, Spring Security, JPA, Flyway, WebSocket, Actuator
- DB: MySQL (필수), Redis (선택)
- ML: Python 3.10+, Flask (`python-ml`)
- IDS: Suricata (환경에 따라 EVE 파일 폴링 및 Syslog 연동)

## 아키텍처 개요

```text
[Client Agents] --(HTTP API)--> [Spring Boot Beacon] --(JPA)--> [MySQL]
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
- `SERVER_SSL_ENABLED` (`true` 시 HTTPS 사용)

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

기본 주소: `http://localhost:8080`

### 4) ML 서비스 실행

```bash
cd python-ml
python -m venv .venv
source .venv/bin/activate   # Windows: .venv\Scripts\Activate.ps1
pip install -r requirements.txt
python app.py
```

기본 주소: `http://localhost:5000`

### 5) 상태 확인

- 백엔드 헬스: `GET /actuator/health`
- ML 헬스: `GET http://localhost:5000/health`

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
- 외부 접속 불가: 방화벽/포트(`8080`, `514/udp`) 및 SSL 설정 확인

## 라이선스

프로젝트 라이선스는 `LICENSE` 파일을 따릅니다.
