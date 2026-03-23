# 🛡️ Beacon - 통합 보안 모니터링 시스템

Raspberry Pi 허브 기반으로 경유 트래픽을 캡처하고, 클라이언트 에이전트와 결합하여 네트워크 및 시스템 위협을 실시간 탐지하는 AI 보안 모니터링 시스템

![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.3-green)
![Python](https://img.shields.io/badge/Python-3.10+-blue)
![MySQL](https://img.shields.io/badge/MySQL-8.0-blue)

## 📋 목차

- [프로젝트 개요](#프로젝트-개요)
- [시스템 아키텍처](#시스템-아키텍처)
- [주요 기능](#주요-기능)
- [기술 스택](#기술-스택)
- [설치 및 실행](#설치-및-실행)
- [시스템 구성요소](#시스템-구성요소)
- [API 문서](#api-문서)
- [데이터베이스](#데이터베이스)
- [보안 기능](#보안-기능)
- [향후 개선](#향후-개선-사항)

## 🎯 프로젝트 개요

Beacon은 **Raspberry Pi 허브** 기반의 **Spring Boot**, **Suricata IDS**, **Python ML**을 결합한 **통합 네트워크 보안 모니터링 솔루션**입니다.

라즈베리파이가 네트워크 허브 중간 역할을 하며 경유하는 모든 트래픽을 캡처하고, 클라이언트 에이전트가 설치된 모니터링 대상 PC에서 추가 데이터를 수집하여 MySQL에 저장합니다.

### 핵심 가치

- 🍓 **허브 기반 수동적 캡처**: Raspberry Pi가 허브 역할을 하며 Suricata + libpcap으로 경유 트래픽 전체를 투명하게 캡처
- 📡 **능동적 에이전트 수집**: 클라이언트 PC에 설치된 BeaconGuardian 에이전트가 USB, 프로세스, 파일, 로컬 패킷 데이터를 Pi로 직접 전송
- 🔍 **실시간 침입 탐지**: Suricata IDS/IPS 규칙으로 네트워크 위협 실시간 탐지
- 🤖 **AI 기반 분석**: 머신러닝을 활용한 지능형 이상 탐지
- 📊 **통합 대시보드**: 같은 네트워크 내 PC에서 `http://[Pi IP]:8080`으로 접속하는 직관적인 웹 UI
- 🔐 **엔터프라이즈 보안**: JWT 인증, 암호화, 접근 제어 등 강력한 보안 기능

### 주요 기능

| 기능 | 설명 |
|------|------|
| 📊 **트래픽 모니터링** | 네트워크 트래픽을 실시간으로 수집하고 분석 |
| 🔐 **세션 관리** | 사용자 요청, 세션 정보를 추적하고 관리 |
| 🤖 **AI 이상 탐지** | Python ML 모델을 통한 비정상 행동 패턴 탐지 |
| 🛡️ **보안 이벤트 로깅** | 보안 위협을 자동으로 감지하고 기록 |
| 🔑 **JWT 인증** | 안전한 토큰 기반 사용자 인증 및 권한 관리 |
| 🔒 **데이터 암호화** | BCrypt를 이용한 민감 정보 암호화 저장 |
| 📈 **관리자 대시보드** | Thymeleaf 기반 실시간 모니터링 대시보드 |
| 🔔 **실시간 알림** | 위험도가 높은 이벤트 발생 시 즉시 알림 |


## 🛠️ 기술 스택

### 🍓 Raspberry Pi Hub (중앙 허브)
| 기술 | 버전 | 용도 |
|------|------|------|
| Raspberry Pi OS | Bookworm (64-bit) | 허브 운영체제 |
| Suricata | 7.x | 네트워크 IDS/IPS - 허브 경유 트래픽 캡처 및 침입 탐지 |
| libpcap | Latest | Suricata 패킷 캡처 라이브러리 (Linux) |

### Backend (Spring Boot - Raspberry Pi 실행)
| 기술 | 버전 | 용도 |
|------|------|------|
| Java | 21 | 메인 프로그래밍 언어 |
| Spring Boot | 4.0.3 | 애플리케이션 프레임워크 |
| Spring Security | 6.x | JWT 인증/인가, 암호화 |
| Spring Data JPA | 3.x | 데이터베이스 ORM |
| MySQL Connector | 8.0 | MySQL 데이터베이스 드라이버 |
| Flyway | Latest | 데이터베이스 마이그레이션 |
| Thymeleaf | 3.x | 서버사이드 템플릿 엔진 |
| jjwt | 0.12.3 | JWT 토큰 생성/검증 |
| Lombok | Latest | 보일러플레이트 코드 감소 |
| Jackson | 2.x | JSON 직렬화/역직렬화 |

### Frontend
| 기술 | 용도 |
|------|------|
| Thymeleaf | 서버사이드 렌더링 |
| HTML5/CSS3 | 마크업 및 스타일링 |
| JavaScript (ES6+) | 클라이언트 사이드 로직 |

### ML Service (Python - Raspberry Pi 실행)
| 기술 | 버전 | 용도 |
|------|------|------|
| Python | 3.10+ | ML 서비스 언어 |
| Flask | 3.0.0 | REST API 프레임워크 |
| Flask-CORS | 4.0.0 | CORS 처리 |
| NumPy | 1.26.0 | 수치 연산 |
| scikit-learn | 1.3.0 | 머신러닝 알고리즘 |
| pandas | 2.1.0 | 데이터 처리 |

### Database (Raspberry Pi 실행)
| 기술 | 버전 | 용도 |
|------|------|------|
| MySQL | 8.0 | 메인 데이터베이스 (Pi에 직접 설치) |
| Redis | Latest (선택) | 세션 캐시 |

### 📡 클라이언트 에이전트 (모니터링 대상 PC)
| 기술 | 버전 | 용도 |
|------|------|------|
| Npcap | Latest | Windows 클라이언트 패킷 캡처 라이브러리 |
| WinDivert (선택) | Latest | 패킷 인터셉트 (Windows) |

### DevOps & Tools
| 기술 | 용도 |
|------|------|
| Gradle | 빌드 도구 |
| Git | 버전 관리 |
| IntelliJ IDEA | 개발 IDE |


## 🏗️ 시스템 아키텍처

### 전체 구조

```
┌─────────────────────────────────────────────────────────────────┐
│  💻 모니터링 대상 시스템 (직원 PC, 서버 등)                        │
│                                                                 │
│  ┌───────────────────────────────────────────────────────┐     │
│  │  📡 BeaconGuardian 클라이언트 에이전트 (필수 설치)      │     │
│  │  • USB 장치 연결/제거 감지 및 로그 기록                 │     │
│  │  • 프로세스 실행 및 리소스 사용 모니터링                │     │
│  │  • 파일 시스템 변경 감지 (무결성 검사)                  │     │
│  │  • 시스템 이벤트 로그 수집                              │     │
│  │  • Npcap 기반 로컬 패킷 캡처 (Windows)                 │     │
│  │  • Heartbeat로 연결 상태 유지                           │     │
│  └───────────────────────┬───────────────────────────────┘     │
└──────────────────────────┼──────────────────────────────────────┘
                           │ HTTPS/REST API (에이전트 데이터 전송)
                           │ 네트워크 허브 경유
                           ↓
┌─────────────────────────────────────────────────────────────────┐
│  🍓 Raspberry Pi Hub (중앙 허브 - 모든 컴포넌트 단일 실행)         │
│                                                                 │
│  ┌───────────────────────────────────────────────────────┐     │
│  │  🔍 Suricata + libpcap (네트워크 IDS/IPS)              │     │
│  │  • 허브를 경유하는 모든 네트워크 트래픽 실시간 캡처     │     │
│  │  • 침입 탐지(IDS) / 침입 방지(IPS) 규칙 기반 분석      │     │
│  │  • EVE JSON 형식으로 이벤트 로그 생성                  │     │
│  │  • Syslog를 통해 Spring Boot로 이벤트 전달             │     │
│  └───────────────────────┬───────────────────────────────┘     │
│                          │ EVE JSON / Syslog (UDP 514)         │
│                          ↓                                     │
│  ┌───────────────────────────────────────────────────────┐     │
│  │  🖥️ Spring Boot Application (Port 8080)               │     │
│  │  ┌─────────────────────────────────────────────────┐  │     │
│  │  │  🔐 Security Layer                              │  │     │
│  │  │  • JWT 인증/인가 · BCrypt 암호화                 │  │     │
│  │  └─────────────────────────────────────────────────┘  │     │
│  │  ┌─────────────────────────────────────────────────┐  │     │
│  │  │  📝 Syslog Event Handler                        │  │     │
│  │  │  • Suricata EVE JSON 파싱                        │  │     │
│  │  │  • 보안 이벤트 자동 생성 및 분류                 │  │     │
│  │  └─────────────────────────────────────────────────┘  │     │
│  │  ┌─────────────────────────────────────────────────┐  │     │
│  │  │  🎮 REST API Controllers                        │  │     │
│  │  │  • /api/auth          - 인증                    │  │     │
│  │  │  • /api/security-events - 보안 이벤트           │  │     │
│  │  │  • /api/traffic       - 트래픽 분석             │  │     │
│  │  │  • /api/ml            - ML 분석 요청            │  │     │
│  │  │  • /api/agent         - 클라이언트 에이전트 수신 │  │     │
│  │  └─────────────────────────────────────────────────┘  │     │
│  │  ┌─────────────────────────────────────────────────┐  │     │
│  │  │  💼 Service Layer                               │  │     │
│  │  │  • SecurityEventService · TrafficAnalysisService│  │     │
│  │  │  • UserSessionService  · LoggingService         │  │     │
│  │  └─────────────────────────────────────────────────┘  │     │
│  └─────────────────────┬──────────────────┬───────────────┘     │
│                        │                  │                     │
│  ┌─────────────────────▼──┐  ┌────────────▼──────────────┐     │
│  │  💾 MySQL (Port 3306)  │  │  🧠 Python ML (Port 5000)  │     │
│  │  Pi에 직접 설치         │  │  Pi에서 실행 (Flask)        │     │
│  │  • users               │  │  • 이상 탐지 알고리즘       │     │
│  │  • user_sessions       │  │  • 위협 패턴 분석           │     │
│  │  • security_events     │  │  • 예측 모델 · 배치 처리    │     │
│  │  • traffic_logs        │  └───────────────────────────┘     │
│  │  • firewall_rules      │                                     │
│  └────────────────────────┘                                     │
└──────────────────────────────────────────────────────────────────┘
                           │ HTTP (http://[Pi IP]:8080)
                           ↓
              ┌─────────────────────────┐
              │  🌐 관리자 PC 웹 브라우저  │
              │  (같은 네트워크 내 접속)   │
              │  • 대시보드               │
              │  • 이벤트 로그            │
              │  • 트래픽 분석            │
              │  • 통계 및 보고서         │
              └─────────────────────────┘
```

### 데이터 흐름

```
1️⃣ 네트워크 트래픽 캡처 (허브 레벨)
   [클라이언트 ↔ 인터넷 트래픽]
     → [Suricata + libpcap이 허브 경유 패킷 캡처]
     → [EVE JSON 이벤트 생성]
     → [Syslog → Spring Boot SyslogEventHandler]
     → [MySQL traffic_logs / security_events 저장]

2️⃣ 클라이언트 에이전트 데이터 전송 (능동 수집)
   [BeaconGuardian 에이전트 (USB·프로세스·파일·로컬패킷)]
     → [HTTPS REST API → Spring Boot /api/agent]
     → [MySQL 저장]

3️⃣ AI 이상 탐지
   [Spring Boot] → [Python ML Service (Port 5000)]
     → [이상 탐지 결과] → [MySQL 업데이트]

4️⃣ 관리자 대시보드 조회
   [관리자 PC 브라우저] → [http://[Pi IP]:8080]
     → [Spring Boot Thymeleaf 렌더링] → [실시간 보안 현황 표시]
```

### 네트워크 토폴로지

```
[인터넷/외부망]
       │
       │ (업링크)
┌──────▼──────────────────────────────────┐
│  🍓 Raspberry Pi (허브/스위치 역할)       │
│  eth0: 업링크  eth1: 내부망 스위치 포트   │
│  Suricata → 내부망 트래픽 전체 캡처      │
└──────┬───────────────────────────────────┘
       │ (내부 네트워크 192.168.x.x)
  ┌────┴────┬──────────────────┐
  │         │                  │
[PC-1]    [PC-2]           [서버-1]
BeaconGuardian 에이전트 설치됨
```


## 🚀 설치 및 실행

### 사전 요구사항

```
[🍓 Raspberry Pi Hub]
✅ Raspberry Pi 4 이상 (RAM 4GB 권장)
✅ Raspberry Pi OS Bookworm 64-bit
✅ JDK 21 이상
✅ MySQL 8.0 이상
✅ Python 3.10 이상
✅ Suricata 7.x
✅ 네트워크 인터페이스 2개 이상 (업링크 + 내부망)
⭕ Redis (선택사항, 세션 캐시용)

[💻 클라이언트 에이전트 (Windows PC)]
✅ Npcap 설치 (https://npcap.com)
✅ BeaconGuardian 에이전트 실행 파일
```

### 빠른 시작 (Quick Start)

---

#### 🍓 [Raspberry Pi] 1️⃣ Suricata + libpcap 설치

```bash
# 패키지 업데이트
sudo apt update && sudo apt upgrade -y

# Suricata 설치 (libpcap 포함)
sudo apt install -y suricata

# Suricata 버전 확인
suricata --build-info | grep libpcap

# 내부망 인터페이스 확인 (예: eth1)
ip link show
```

Suricata 설정 파일에서 내부망 인터페이스 및 EVE JSON 출력 활성화:

```bash
sudo nano /etc/suricata/suricata.yaml
```

```yaml
# 모니터링할 네트워크 인터페이스 설정
af-packet:
  - interface: eth1        # 내부망 인터페이스

# EVE JSON 로그 출력 (Spring Boot가 읽어들임)
outputs:
  - eve-log:
      enabled: yes
      filetype: syslog
      prefix: "@cee: "
      types:
        - alert
        - http
        - dns
        - tls
        - flow

# Syslog 전송 설정 (Spring Boot 수신 포트)
syslog:
  enabled: yes
  facility: local5
  level: Info
```

```bash
# Suricata 서비스 시작 및 자동 시작 등록
sudo systemctl enable suricata
sudo systemctl start suricata
sudo systemctl status suricata
```

---

#### 🍓 [Raspberry Pi] 2️⃣ MySQL 설치 및 데이터베이스 설정

```bash
# MySQL 설치
sudo apt install -y mysql-server

# MySQL 보안 설정
sudo mysql_secure_installation

# MySQL 접속
sudo mysql -u root -p
```

```sql
CREATE DATABASE beacon CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'beacon'@'localhost' IDENTIFIED BY 'your_strong_password';
GRANT ALL PRIVILEGES ON beacon.* TO 'beacon'@'localhost';
FLUSH PRIVILEGES;
EXIT;
```

---

#### 🍓 [Raspberry Pi] 3️⃣ 프로젝트 클론 및 Spring Boot 설정

```bash
# 프로젝트 클론
git clone https://github.com/your-repo/beacon.git
cd beacon
```

`src/main/resources/application.yaml` 파일에서 연결 정보를 수정합니다:

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/beacon?serverTimezone=Asia/Seoul&characterEncoding=UTF-8
    username: beacon
    password: your_strong_password  # 👈 위에서 설정한 비밀번호

jwt:
  secret: your-secret-key-must-be-at-least-256-bits-long-for-hs256-algorithm-beacon-security
  expiration: 86400000  # 24시간

ml:
  python:
    service:
      url: http://localhost:5000  # Pi 로컬 ML 서비스

# Suricata Syslog 수신 포트
syslog:
  port: 514
```

```bash
# JDK 21 설치
sudo apt install -y openjdk-21-jdk

# Spring Boot 빌드 및 실행
./gradlew clean build
./gradlew bootRun
```

서버는 `http://[Pi IP]:8080`에서 실행됩니다. Pi의 IP 확인:

```bash
hostname -I
```

---

#### 🍓 [Raspberry Pi] 4️⃣ Python ML 서비스 실행

```bash
cd python-ml

# 가상환경 생성 및 활성화
python3 -m venv venv
source venv/bin/activate

# 의존성 설치
pip install -r requirements.txt

# 서비스 실행 (백그라운드)
nohup python app.py &
```

---

#### 💻 [클라이언트 PC] 5️⃣ BeaconGuardian 에이전트 설치 (Windows)

```powershell
# 1. Npcap 설치 (https://npcap.com 에서 다운로드)
#    설치 시 "WinPcap API-compatible Mode" 체크 권장

# 2. BeaconGuardian 에이전트 설정
#    config.yaml에서 Pi 서버 주소 설정
beacon_server: http://192.168.x.x:8080   # Pi IP로 변경
agent_id: PC-001
heartbeat_interval: 30  # 초
```

---

#### 🌐 [관리자 PC] 6️⃣ 웹 대시보드 접속

같은 네트워크의 PC 브라우저에서 Pi IP로 접속:

```
http://192.168.x.x:8080
```

**기본 로그인 정보:**
- **Username**: `admin`
- **Password**: `admin1234`

⚠️ **보안 주의사항**: 로그인 후 새로운 관리자 계정을 생성하고 기본 계정은 삭제하는 것을 권장합니다.

---

### 실행 확인

#### Spring Boot 서버 확인 (Pi에서)
```bash
curl http://localhost:8080/actuator/health
```

#### Python ML 서비스 확인 (Pi에서)
```bash
curl http://localhost:5000/health
```

#### Suricata 동작 확인 (Pi에서)
```bash
sudo tail -f /var/log/suricata/eve.json
sudo systemctl status suricata
```

#### 데이터베이스 마이그레이션 확인
```sql
USE beacon;
SHOW TABLES;
-- users, user_sessions, security_events, traffic_logs, firewall_rules 테이블이 생성되어야 함
```


## 📚 API 문서

### 🔐 인증 API

#### 로그인
```http
POST /api/auth/login
Content-Type: application/json

{
  "username": "admin",
  "password": "admin1234"
}
```

**응답:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "username": "admin",
  "userId": 1,
  "role": "ADMIN"
}
```

#### 회원가입
```http
POST /api/auth/register
Content-Type: application/json

{
  "username": "newuser",
  "password": "password123",
  "email": "user@example.com",
  "name": "홍길동"
}
```

#### 토큰 검증
```http
GET /api/auth/verify
Authorization: Bearer YOUR_JWT_TOKEN
```

### 🛡️ 보안 이벤트 API

#### 보안 이벤트 목록 조회
```http
GET /api/security-events?page=0&size=20&sortBy=createdAt&direction=DESC
Authorization: Bearer YOUR_JWT_TOKEN
```

#### 심각도별 조회
```http
GET /api/security-events/severity/critical?page=0&size=20
Authorization: Bearer YOUR_JWT_TOKEN
```

#### 날짜 범위 조회
```http
GET /api/security-events/range?start=2026-03-01T00:00:00&end=2026-03-31T23:59:59
Authorization: Bearer YOUR_JWT_TOKEN
```

#### 보안 이벤트 생성
```http
POST /api/security-events
Authorization: Bearer YOUR_JWT_TOKEN
Content-Type: application/json

{
  "eventType": "무차별 대입 공격",
  "severity": "critical",
  "sourceIp": "192.168.1.100",
  "destinationIp": "10.0.0.1",
  "protocol": "TCP",
  "port": 22,
  "status": "차단됨",
  "description": "SSH 포트로 5분간 1000회 이상 로그인 시도 감지",
  "blocked": true,
  "riskScore": 9.5
}
```

#### 이벤트 해결 처리
```http
PUT /api/security-events/123/resolve?handledBy=admin
Authorization: Bearer YOUR_JWT_TOKEN
```

### 📊 트래픽 분석 API

#### 트래픽 로그 조회
```http
GET /api/traffic?page=0&size=20
Authorization: Bearer YOUR_JWT_TOKEN
```

#### 이상 트래픽 조회
```http
GET /api/traffic/anomalous?page=0&size=20
Authorization: Bearer YOUR_JWT_TOKEN
```

#### IP별 트래픽 조회
```http
GET /api/traffic/ip/192.168.1.100
Authorization: Bearer YOUR_JWT_TOKEN
```

#### 트래픽 로그 생성
```http
POST /api/traffic
Authorization: Bearer YOUR_JWT_TOKEN
Content-Type: application/json

{
  "sourceIp": "192.168.1.100",
  "destinationIp": "8.8.8.8",
  "sourcePort": 54321,
  "destinationPort": 443,
  "protocol": "TCP",
  "bytesTransferred": 10485760,
  "packetsTransferred": 1000,
  "duration": 30
}
```

#### 대역폭 통계
```http
GET /api/traffic/stats/bandwidth?hours=24
Authorization: Bearer YOUR_JWT_TOKEN
```

**응답:**
```json
{
  "totalBytes": 10737418240,
  "totalMB": 10240.0,
  "totalGB": 10.0,
  "since": "2026-03-08T17:00:00",
  "hours": 24
}
```

#### 프로토콜별 통계
```http
GET /api/traffic/stats/protocols
Authorization: Bearer YOUR_JWT_TOKEN
```

### 🤖 머신러닝 API

#### 이상 탐지 (단일)
```http
POST /api/ml/detect-anomaly
Authorization: Bearer YOUR_JWT_TOKEN
Content-Type: application/json

{
  "trafficLogId": 123,
  "sourceIp": "192.168.1.100",
  "destinationIp": "8.8.8.8",
  "protocol": "TCP",
  "bytesTransferred": 10737418240,
  "packetsTransferred": 10000,
  "duration": 10
}
```

**응답:**
```json
{
  "isAnomaly": true,
  "anomalyScore": 8.5,
  "anomalyReason": "비정상적으로 큰 데이터 전송, 초당 패킷 수 과다"
}
```

#### 배치 이상 탐지
```http
POST /api/ml/batch-detect
Authorization: Bearer YOUR_JWT_TOKEN
Content-Type: application/json

[
  {
    "sourceIp": "192.168.1.100",
    "bytesTransferred": 10737418240,
    ...
  },
  {
    "sourceIp": "192.168.1.101",
    "bytesTransferred": 1048576,
    ...
  }
]
```

#### 모델 학습 트리거
```http
POST /api/ml/train-model
Authorization: Bearer YOUR_JWT_TOKEN
```

#### 모델 상태 확인
```http
GET /api/ml/model-status
Authorization: Bearer YOUR_JWT_TOKEN
```

**응답:**
```json
{
  "available": true,
  "trained": true,
  "threshold": 0.7,
  "version": "1.0.0",
  "timestamp": "2026-03-09T17:00:00"
}
```

### 📄 웹 페이지 라우트

| URL | 설명 |
|-----|------|
| `/login` | 로그인 페이지 |
| `/dashboard` | 메인 대시보드 |
| `/threats` | 위협/이벤트 로그 |
| `/network` | 네트워크 모니터링 |
| `/access` | 접근 제어 |
| `/firewall` | 방화벽/정책 |
| `/reports` | 보고서/분석 |


## 🧩 시스템 구성요소

### Spring Boot 서버 구성

```
beacon/src/main/java/com/example/beacon/
│
├── 📁 config/                    # 설정 클래스
│   ├── SecurityConfig.java       # Spring Security 설정 (JWT, 인증/인가)
│   ├── WebMvcConfig.java         # 인터셉터 등록
│   └── AppConfig.java            # Bean 설정 (ObjectMapper, RestTemplate)
│
├── 📁 controller/                # REST API 컨트롤러
│   ├── AuthController.java       # 인증 API (로그인, 회원가입, 토큰 검증)
│   ├── SecurityEventController   # 보안 이벤트 관리 API
│   ├── TrafficController.java    # 트래픽 로그 API
│   ├── MachineLearningController # Python ML 연동 API
│   └── DashboardController.java  # 웹 대시보드 페이지 라우팅
│
├── 📁 dto/                       # Data Transfer Objects
│   ├── LoginRequest.java         # 로그인 요청 DTO
│   ├── AuthResponse.java         # 인증 응답 DTO
│   ├── TrafficLogRequest.java    # 트래픽 로그 요청 DTO
│   ├── AnomalyDetectionRequest   # ML 분석 요청 DTO
│   └── AnomalyDetectionResponse  # ML 분석 결과 DTO
│
├── 📁 entity/                    # JPA 엔티티 (데이터베이스 모델)
│   ├── User.java                 # 사용자 정보
│   ├── UserSession.java          # 사용자 세션 및 위험도 점수
│   ├── SecurityEvent.java        # 보안 이벤트 로그
│   ├── TrafficLog.java           # 네트워크 트래픽 로그
│   └── FirewallRule.java         # 방화벽 규칙
│
├── 📁 repository/                # JPA 리포지토리 인터페이스
│   ├── UserRepository.java
│   ├── UserSessionRepository.java
│   ├── SecurityEventRepository.java
│   ├── TrafficLogRepository.java
│   └── FirewallRuleRepository.java
│
├── 📁 security/                  # 보안 관련 클래스
│   ├── JwtTokenProvider.java     # JWT 토큰 생성/검증
│   ├── JwtAuthenticationFilter   # JWT 인증 필터
│   └── CustomUserDetailsService  # 사용자 인증 서비스
│
├── 📁 service/                   # 비즈니스 로직
│   ├── SecurityEventService.java # 보안 이벤트 관리
│   ├── TrafficAnalysisService    # 트래픽 분석
│   ├── UserSessionService.java   # 세션 관리
│   └── LoggingService.java       # 로그 처리
│
└── 📁 interceptor/               # HTTP 인터셉터
    └── RequestLoggingInterceptor # 모든 요청 로깅
```

### Python ML 서비스 구성

```
beacon/python-ml/
│
├── app.py                        # Flask 메인 애플리케이션
├── requirements.txt              # Python 의존성
├── README.md                     # ML 서비스 문서
└── .gitignore
```

### 웹 리소스 구성

```
beacon/src/main/resources/
│
├── 📁 db/migration/              # Flyway 데이터베이스 마이그레이션
│   └── V1__Initial_Schema.sql   # 초기 스키마 및 데이터
│
├── 📁 templates/                 # Thymeleaf 템플릿
│   ├── login.html               # 로그인 페이지
│   ├── layout.html              # 레이아웃 템플릿
│   └── pages/
│       ├── dashboard.html       # 대시보드
│       ├── threats.html         # 위협 로그
│       ├── network.html         # 네트워크 모니터링
│       ├── access.html          # 접근 제어
│       ├── firewall.html        # 방화벽 정책
│       └── reports.html         # 보고서
│
├── 📁 static/                    # 정적 리소스
│   ├── css/
│   │   └── main.css             # 메인 스타일시트
│   └── js/
│       └── main.js              # 메인 JavaScript
│
└── application.yaml              # Spring Boot 설정
```


## 💾 데이터베이스

### 주요 테이블 스키마

#### 1. users (사용자 정보)
| 컬럼명 | 타입 | 설명 |
|--------|------|------|
| id | BIGINT (PK) | 사용자 ID |
| username | VARCHAR(50) | 사용자명 (고유) |
| password | VARCHAR(500) | BCrypt 암호화된 비밀번호 |
| email | VARCHAR(100) | 이메일 (고유) |
| name | VARCHAR(100) | 실명 |
| role | VARCHAR(50) | 역할 (USER, ADMIN) |
| enabled | BOOLEAN | 계정 활성화 여부 |
| mfa_enabled | BOOLEAN | MFA 활성화 여부 |
| login_attempts | INT | 로그인 실패 횟수 |
| last_login_at | DATETIME | 마지막 로그인 시각 |
| locked_until | DATETIME | 계정 잠금 해제 시각 |
| created_at | DATETIME | 생성 시각 |
| updated_at | DATETIME | 수정 시각 |

#### 2. user_sessions (사용자 세션)
| 컬럼명 | 타입 | 설명 |
|--------|------|------|
| id | BIGINT (PK) | 세션 ID |
| session_token | VARCHAR(500) | 세션 토큰 (고유) |
| user_id | BIGINT (FK) | 사용자 ID |
| ip_address | VARCHAR(45) | 클라이언트 IP |
| user_agent | VARCHAR(500) | User-Agent |
| device_type | VARCHAR(100) | 장치 유형 |
| location | VARCHAR(100) | 접속 위치 |
| created_at | DATETIME | 세션 생성 시각 |
| last_activity_at | DATETIME | 마지막 활동 시각 |
| expires_at | DATETIME | 만료 시각 |
| is_active | BOOLEAN | 활성 상태 |
| request_count | INT | 요청 횟수 |
| risk_score | DOUBLE | 위험도 점수 (0-10) |
| risk_factors | TEXT | 위험 요인 설명 |

#### 3. security_events (보안 이벤트)
| 컬럼명 | 타입 | 설명 |
|--------|------|------|
| id | BIGINT (PK) | 이벤트 ID |
| event_type | VARCHAR(100) | 이벤트 유형 |
| severity | VARCHAR(20) | 심각도 (critical, high, medium, low) |
| source_ip | VARCHAR(45) | 출발지 IP |
| destination_ip | VARCHAR(45) | 목적지 IP |
| protocol | VARCHAR(20) | 프로토콜 (TCP, UDP, ICMP 등) |
| port | INT | 포트 번호 |
| status | VARCHAR(50) | 상태 (차단됨, 조사중, 해결됨) |
| description | TEXT | 상세 설명 |
| metadata | JSON | 추가 메타데이터 |
| created_at | DATETIME | 발생 시각 |
| resolved_at | DATETIME | 해결 시각 |
| handled_by | VARCHAR(100) | 처리자 |
| blocked | BOOLEAN | 차단 여부 |
| risk_score | DOUBLE | 위험도 점수 (0-10) |

#### 4. traffic_logs (트래픽 로그)
| 컬럼명 | 타입 | 설명 |
|--------|------|------|
| id | BIGINT (PK) | 로그 ID |
| source_ip | VARCHAR(45) | 출발지 IP |
| destination_ip | VARCHAR(45) | 목적지 IP |
| source_port | INT | 출발지 포트 |
| destination_port | INT | 목적지 포트 |
| protocol | VARCHAR(20) | 프로토콜 |
| bytes_transferred | BIGINT | 전송된 바이트 |
| packets_transferred | BIGINT | 전송된 패킷 수 |
| duration | INT | 지속 시간 (초) |
| is_anomaly | BOOLEAN | 이상 여부 |
| anomaly_score | DOUBLE | 이상 점수 (0-10) |
| anomaly_reason | TEXT | 이상 사유 |
| raw_data | JSON | 원본 데이터 |
| timestamp | DATETIME | 기록 시각 |
| session_id | BIGINT (FK) | 관련 세션 ID |

#### 5. firewall_rules (방화벽 규칙)
| 컬럼명 | 타입 | 설명 |
|--------|------|------|
| id | BIGINT (PK) | 규칙 ID |
| name | VARCHAR(100) | 규칙명 |
| action | VARCHAR(20) | 동작 (allow, deny, limit) |
| source_address | VARCHAR(100) | 출발지 주소 |
| destination_address | VARCHAR(100) | 목적지 주소 |
| port | VARCHAR(50) | 포트 |
| priority | INT | 우선순위 |
| enabled | BOOLEAN | 활성화 여부 |
| hits | BIGINT | 적용 횟수 |
| description | TEXT | 설명 |
| created_at | DATETIME | 생성 시각 |
| updated_at | DATETIME | 수정 시각 |
| created_by | VARCHAR(50) | 생성자 |

### 인덱스

성능 최적화를 위한 주요 인덱스:

```sql
-- 보안 이벤트
INDEX idx_severity ON security_events(severity);
INDEX idx_created_at ON security_events(created_at);
INDEX idx_source_ip ON security_events(source_ip);

-- 트래픽 로그
INDEX idx_timestamp ON traffic_logs(timestamp);
INDEX idx_source_ip ON traffic_logs(source_ip);
INDEX idx_anomaly ON traffic_logs(is_anomaly);

-- 사용자 세션
INDEX idx_user_id ON user_sessions(user_id);
INDEX idx_session_token ON user_sessions(session_token);
INDEX idx_created_at ON user_sessions(created_at);
```

### 데이터베이스 마이그레이션

Flyway를 사용하여 데이터베이스 스키마를 관리합니다:

```bash
# 마이그레이션 파일 위치
src/main/resources/db/migration/V1__Initial_Schema.sql
```

마이그레이션은 애플리케이션 시작 시 자동으로 실행됩니다.


## 🔒 보안 기능

### 1. 인증 및 인가 (Authentication & Authorization)

#### JWT (JSON Web Token) 기반 인증
```java
// 토큰 생성
String token = jwtTokenProvider.generateToken(username, userId);

// 토큰 검증
boolean isValid = jwtTokenProvider.validateToken(token);

// 토큰에서 사용자 정보 추출
String username = jwtTokenProvider.getUsernameFromToken(token);
Long userId = jwtTokenProvider.getUserIdFromToken(token);
```

**토큰 정보:**
- 알고리즘: HS256
- 만료 시간: 24시간 (86400000ms)
- 비밀키 길이: 최소 256비트
- Payload: username, userId, issuedAt, expiration

#### 비밀번호 암호화
```java
// BCrypt를 사용한 비밀번호 해싱
String hashedPassword = passwordEncoder.encode(rawPassword);

// 비밀번호 검증
boolean matches = passwordEncoder.matches(rawPassword, hashedPassword);
```

**BCrypt 특징:**
- 솔트(Salt) 자동 생성
- 레인보우 테이블 공격 방어
- 느린 해싱으로 무차별 대입 공격 방어

#### 역할 기반 접근 제어 (RBAC)
```java
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<?> adminOnlyEndpoint() {
    // ADMIN 역할만 접근 가능
}

@PreAuthorize("hasAnyRole('ADMIN', 'USER')")
public ResponseEntity<?> authenticatedEndpoint() {
    // 인증된 사용자 모두 접근 가능
}
```

### 2. 세션 관리

#### 세션 생성 및 추적
```java
UserSession session = UserSession.builder()
    .sessionToken(UUID.randomUUID().toString())
    .userId(userId)
    .ipAddress(clientIp)
    .userAgent(userAgent)
    .expiresAt(LocalDateTime.now().plusHours(24))
    .riskScore(0.0)
    .build();
```

#### 세션 위험도 점수 계산
- **0-3점**: 안전
- **4-6점**: 주의
- **7-8점**: 경고
- **9-10점**: 위험

**위험도 계산 요소:**
- 비정상적인 접속 패턴
- 여러 지역에서 동시 접속
- 과다한 요청 빈도
- 의심스러운 User-Agent
- 알려진 악성 IP

#### 자동 세션 정리
```java
@Scheduled(cron = "0 0 * * * *")  // 매 시간 실행
public void cleanupExpiredSessions() {
    userSessionService.cleanupExpiredSessions();
}
```

### 3. 이상 탐지 (Anomaly Detection)

#### 규칙 기반 탐지
```java
// 대용량 데이터 전송
if (bytesTransferred > 10 * 1024 * 1024 * 1024) {  // 10GB
    anomalyScore += 0.4;
    reasons.add("비정상적으로 큰 데이터 전송");
}

// 과도한 패킷 전송
if (packetsPerSecond > 1000) {
    anomalyScore += 0.3;
    reasons.add("초당 패킷 수 과다");
}
```

#### ML 기반 탐지
Python 머신러닝 서비스를 통한 지능형 패턴 분석:

```python
# 이상 탐지 알고리즘
class AnomalyDetector:
    def detect_anomaly(self, traffic_data):
        # 통계적 분석
        # 시계열 패턴 분석
        # 행동 프로파일링
        return {
            'isAnomaly': True/False,
            'anomalyScore': 0.0 ~ 10.0,
            'anomalyReason': '감지된 이상 패턴 설명'
        }
```

### 4. 로깅 및 감사 (Logging & Auditing)

#### 요청 로깅
모든 HTTP 요청이 자동으로 로깅됩니다:

```json
{
  "timestamp": 1710000000000,
  "method": "POST",
  "uri": "/api/security-events",
  "queryString": null,
  "ipAddress": "192.168.1.100",
  "userAgent": "Mozilla/5.0...",
  "statusCode": 200,
  "duration": 45,
  "username": "admin",
  "error": null
}
```

#### 보안 이벤트 로깅
```java
loggingService.logSecurityEvent(
    "무차별 대입 공격",
    "critical",
    "192.168.1.100",
    "SSH 포트로 1000회 이상 로그인 시도",
    metadata
);
```

### 5. 데이터 보호

#### 민감 정보 암호화
- **비밀번호**: BCrypt (12 라운드)
- **JWT 비밀키**: 256비트 이상
- **통신**: HTTPS 권장

#### SQL 인젝션 방어
JPA/Hibernate의 Prepared Statement 사용:

```java
@Query("SELECT e FROM SecurityEvent e WHERE e.sourceIp = :ip")
List<SecurityEvent> findBySourceIp(@Param("ip") String ip);
```

#### XSS 방어
Thymeleaf 자동 이스케이프:

```html
<p th:text="${userInput}"></p>  <!-- 자동 이스케이프 -->
<p th:utext="${trustedHtml}"></p>  <!-- 이스케이프 안 함 (주의) -->
```

### 6. 방화벽 및 접근 제어

#### IP 기반 차단
```java
if (isBlacklistedIp(sourceIp)) {
    throw new SecurityException("Blocked IP address");
}
```

#### Rate Limiting
```java
// 초당 최대 요청 수 제한
@RateLimiter(maxRequests = 100, period = 1, timeUnit = TimeUnit.SECONDS)
public ResponseEntity<?> limitedEndpoint() {
    // ...
}
```

### 7. 보안 헤더

Spring Security를 통해 자동으로 적용되는 보안 헤더:

```
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
X-XSS-Protection: 1; mode=block
Strict-Transport-Security: max-age=31536000
```

### 보안 체크리스트

운영 환경 배포 전 확인 사항:

- [ ] 기본 관리자 계정 삭제 또는 비밀번호 변경
- [ ] JWT 비밀키 변경 및 안전하게 보관
- [ ] MySQL 비밀번호 강화
- [ ] HTTPS 적용 (Pi에 Let's Encrypt SSL 인증서 적용)
- [ ] 방화벽 규칙 설정 (`ufw allow 8080/tcp`, `ufw allow 514/udp`)
- [ ] Raspberry Pi SSH 기본 비밀번호 변경
- [ ] Suricata 규칙(Rules) 정기 업데이트 (`suricata-update`)
- [ ] 로그 모니터링 설정
- [ ] 백업 정책 수립 (MySQL dump, Suricata logs)
- [ ] Pi 물리적 보안 (허브 역할이므로 물리 접근 통제 필요)
- [ ] 침입 탐지 시스템 (IDS) 구성 - Suricata 완료


## 🚧 향후 개선 사항

### Phase 1: 기능 확장 (단기)

#### 데이터 수집 에이전트 개발 (BeaconGuardian)
- [x] **USB 모니터링 에이전트**
  - 실시간 USB 장치 연결/제거 감지
  - 무단 USB 사용 차단
  - 파일 전송 로그 기록

- [x] **네트워크 패킷 캡처**
  - Pi Hub: Suricata + libpcap으로 허브 경유 전체 트래픽 캡처 (완료)
  - 클라이언트: Npcap 기반 로컬 패킷 수집 (Windows)
  - 실시간 네트워크 트래픽 분석 및 프로토콜별 통계

- [x] **Suricata IDS/IPS 통합**
  - Raspberry Pi Hub에서 Suricata + libpcap 실행 (완료)
  - EVE JSON → Syslog → Spring Boot SyslogEventHandler 파이프라인
  - 네트워크 침입 탐지 규칙(Suricata Rules) 자동 적용

- [x] **프로세스 모니터링**
  - 실행 중인 프로세스 추적
  - 의심스러운 프로세스 감지
  - 리소스 사용량 모니터링

- [x] **파일 시스템 감시**
  - 중요 파일 변경 감지
  - 파일 무결성 검사
  - 랜섬웨어 행동 패턴 감지

- [x] **브라우저 히스토리 모니터링**
  - Chrome/Edge/Firefox 브라우저 기록 수집
  - 웹 방문 URL 및 제목 추적
  - 실시간 웹 활동 분석

- [x] **에이전트 자동 관리**
  - 서버 자동 등록 및 인증
  - Heartbeat를 통한 연결 상태 모니터링
  - 에이전트별 이벤트/트래픽 통계

#### 고급 ML 모델 통합
- [ ] **딥러닝 모델**
  - TensorFlow/PyTorch 통합
  - LSTM 기반 시계열 분석
  - Autoencoder를 이용한 이상 탐지

- [ ] **모델 학습 파이프라인**
  - 자동 데이터 수집 및 전처리
  - 주기적 모델 재학습
  - A/B 테스트 및 성능 비교

### Phase 2: 사용성 개선 (중기)

#### 실시간 알림 시스템
- [ ] **WebSocket 기반 실시간 알림**
  - 위험 이벤트 즉시 알림
  - 대시보드 실시간 업데이트
  - 브라우저 푸시 알림

- [ ] **다중 채널 알림**
  - 이메일 알림
  - Slack/Discord 연동
  - SMS 알림 (Twilio)
  - Telegram 봇

#### 대시보드 고도화
- [ ] **인터랙티브 차트**
  - Chart.js/D3.js 통합
  - 실시간 그래프 업데이트
  - 커스텀 대시보드 빌더

- [ ] **보고서 자동 생성**
  - PDF/Excel 보고서 내보내기
  - 스케줄링된 보고서 생성
  - 커스텀 보고서 템플릿

#### 다중 요소 인증 (MFA)
- [ ] **TOTP 기반 MFA**
  - Google Authenticator 지원
  - QR 코드 생성
  - 백업 코드

- [ ] **생체 인증**
  - WebAuthn/FIDO2 지원
  - 지문/얼굴 인식

### Phase 3: 엔터프라이즈 기능 (장기)

#### 확장성 및 성능
- [ ] **Redis 캐싱**
  - 세션 데이터 캐싱
  - 쿼리 결과 캐싱
  - 속도 제한 (Rate Limiting)

- [ ] **메시지 큐**
  - RabbitMQ/Kafka 통합
  - 비동기 이벤트 처리
  - 마이크로서비스 아키텍처 준비

- [ ] **데이터 아카이빙**
  - 오래된 로그 압축 및 아카이빙
  - 콜드 스토리지 (S3/GCS)
  - 자동 로그 정리

#### 배포 및 운영
- [ ] **컨테이너화**
  - Docker 이미지 생성
  - Docker Compose 설정
  - 환경별 설정 분리

- [ ] **Kubernetes 배포**
  - Helm 차트
  - 자동 스케일링
  - 롤링 업데이트

- [ ] **CI/CD 파이프라인**
  - GitHub Actions
  - 자동 테스트
  - 자동 배포

#### 모니터링 및 관찰성
- [ ] **성능 모니터링**
  - Prometheus 메트릭
  - Grafana 대시보드
  - APM (Application Performance Monitoring)

- [ ] **로그 집계**
  - ELK Stack 통합
  - 중앙화된 로그 관리
  - 로그 검색 및 분석

- [ ] **헬스 체크**
  - 서비스 헬스 체크
  - 데이터베이스 연결 모니터링
  - 외부 서비스 의존성 체크

### Phase 4: 고급 보안 (장기)

#### 위협 인텔리전스
- [ ] **외부 위협 DB 연동**
  - AbuseIPDB 통합
  - VirusTotal API
  - 악성 IP/도메인 블랙리스트

- [ ] **행동 분석**
  - UEBA (User and Entity Behavior Analytics)
  - 이상 사용자 프로파일링
  - 내부자 위협 탐지

#### 규정 준수
- [ ] **GDPR 준수**
  - 개인정보 처리 동의
  - 데이터 삭제 요청 처리
  - 데이터 이동권

- [ ] **감사 로그**
  - 변경 이력 추적
  - 관리자 행동 감사
  - 규정 준수 보고서

### 오픈소스 통합
- [ ] **Wazuh**: 호스트 기반 침입 탐지
- [x] **Suricata**: 네트워크 IDS/IPS (Raspberry Pi Hub에 통합 완료)
- [ ] **Osquery**: 시스템 쿼리
- [ ] **Elastic Beats**: 다양한 데이터 수집

### 커뮤니티 기여
- [ ] API 문서 자동 생성 (Swagger/OpenAPI)
- [ ] 단위 테스트 커버리지 확대
- [ ] 통합 테스트 추가
- [ ] 다국어 지원 (i18n)
- [ ] 설치 스크립트 자동화


## 📖 추가 문서

- **[QUICKSTART.md](QUICKSTART.md)**: 빠른 시작 가이드
- **[Python ML Service](python-ml/README.md)**: 머신러닝 서비스 상세 문서
- **[API Examples](docs/API_EXAMPLES.md)**: API 사용 예제 (향후 작성)
- **[Deployment Guide](docs/DEPLOYMENT.md)**: 배포 가이드 (향후 작성)
- **[Contributing Guide](docs/CONTRIBUTING.md)**: 기여 가이드 (향후 작성)

## 🐛 문제 해결 (Troubleshooting)

### 🍓 Raspberry Pi 관련

#### Suricata가 트래픽을 캡처하지 못하는 경우
```
Error: [ERRCODE: SC_ERR_AFP_CREATE] Couldn't init AF_PACKET
```

**해결:**
```bash
# 인터페이스 이름 확인
ip link show

# suricata.yaml에서 인터페이스 이름 수정
sudo nano /etc/suricata/suricata.yaml
# af-packet.interface 값을 실제 인터페이스 이름으로 변경

# Suricata 재시작
sudo systemctl restart suricata
```

#### Spring Boot가 Syslog를 수신하지 못하는 경우
```
Suricata 이벤트가 security_events 테이블에 저장 안 됨
```

**해결:**
```bash
# Suricata 로그 확인
sudo tail -f /var/log/suricata/eve.json

# Syslog 포트 514 리스닝 여부 확인
sudo netstat -ulnp | grep 514

# 방화벽에서 514 포트 허용
sudo ufw allow 514/udp
```

#### Raspberry Pi 메모리 부족
```
Spring Boot OOM (Out of Memory)
```

**해결:**
```bash
# JVM 힙 메모리 제한 설정 (Pi 4GB RAM 기준)
export JAVA_OPTS="-Xmx512m -Xms256m"
./gradlew bootRun

# 또는 application.yaml에서 스와프 설정
sudo dphys-swapfile swapoff
sudo nano /etc/dphys-swapfile  # CONF_SWAPSIZE=2048
sudo dphys-swapfile setup
sudo dphys-swapfile swapon
```

#### 외부 PC에서 Pi 대시보드 접속 불가
**해결:**
```bash
# Pi의 IP 확인
hostname -I

# 8080 포트 방화벽 허용
sudo ufw allow 8080/tcp

# Spring Boot가 모든 인터페이스에 바인드되는지 확인
# application.yaml
server:
  address: 0.0.0.0
  port: 8080
```

---

### 공통 오류

#### MySQL 연결 오류
```
Error: Access denied for user 'root'@'localhost'
```

**해결:**
1. MySQL 서버가 실행 중인지 확인
2. application.yaml의 username/password 확인
3. MySQL 사용자 권한 확인:
```sql
GRANT ALL PRIVILEGES ON beacon.* TO 'beacon'@'localhost';
FLUSH PRIVILEGES;
```

#### Python 서비스 연결 실패
```
Error: Connection refused to localhost:5000
```

**해결:**
1. Python ML 서비스가 실행 중인지 확인
2. 포트 5000이 사용 가능한지 확인
3. application.yaml의 ML 서비스 URL 확인

#### Flyway 마이그레이션 오류
```
Error: Migration checksum mismatch
```

**해결:**
```sql
-- Flyway 히스토리 초기화 (개발 환경에서만)
DROP TABLE flyway_schema_history;
-- 애플리케이션 재시작
```

#### 포트 충돌
```
Error: Port 8080 is already in use
```

**해결:**
application.yaml에서 포트 변경:
```yaml
server:
  port: 9090
```

## 🧪 테스트

### API 테스트 예제

> Pi IP를 `192.168.x.x`로 가정합니다. `hostname -I` 명령으로 실제 Pi IP를 확인하세요.

```bash
# 로그인 (관리자 PC에서 Pi로 요청)
curl -X POST http://192.168.x.x:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin1234"}'

# 보안 이벤트 조회 (JWT 토큰 필요)
curl -X GET http://192.168.x.x:8080/api/security-events \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."

# Suricata 이벤트 수동 확인 (Pi 로컬에서)
sudo cat /var/log/suricata/eve.json | python3 -m json.tool | head -50

# ML 이상 탐지 (Pi 로컬에서)
curl -X POST http://localhost:5000/api/detect-anomaly \
  -H "Content-Type: application/json" \
  -d '{
    "sourceIp": "192.168.1.100",
    "destinationIp": "8.8.8.8",
    "protocol": "TCP",
    "bytesTransferred": 10737418240,
    "packetsTransferred": 1000,
    "duration": 10
  }'
```

## 📊 성능 고려사항

### 데이터베이스 최적화
- 인덱스를 활용한 쿼리 최적화
- 오래된 로그 자동 아카이빙
- 읽기 전용 복제본 구성 (Read Replica)

### 애플리케이션 최적화
- JPA 쿼리 최적화 (N+1 문제 해결)
- 연결 풀 크기 조정
- 캐싱 전략 (Redis)

### Python ML 서비스 최적화
- 모델 로딩 캐싱
- 배치 처리
- 비동기 처리 (Celery)

## 🤝 기여하기

기여를 환영합니다! 다음 절차를 따라주세요:

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

### 코드 스타일

- Java: Google Java Style Guide
- Python: PEP 8
- SQL: 대문자 키워드, snake_case 테이블명

## 📜 라이선스

이 프로젝트는 MIT 라이선스 하에 배포됩니다. 자세한 내용은 [LICENSE](LICENSE) 파일을 참조하세요.

## 👥 팀

- **프로젝트 관리자**: [Your Name]
- **백엔드 개발**: Spring Boot
- **ML 개발**: Python/Flask
- **프론트엔드**: Thymeleaf

## 📞 문의 및 지원

- 🐛 **버그 리포트**: GitHub Issues
- 💡 **기능 제안**: GitHub Discussions
- 📧 **이메일**: beacon-support@example.com
- 💬 **커뮤니티**: [Discord](https://discord.gg/beacon) (예시)

## 🌟 감사의 말

이 프로젝트는 다음 오픈소스 프로젝트들의 도움을 받았습니다:

- Spring Boot
- Flask
- MySQL
- Thymeleaf
- scikit-learn
- 그리고 많은 오픈소스 컨트리뷰터들

---

<div align="center">

**⭐ 이 프로젝트가 도움이 되었다면 Star를 눌러주세요! ⭐**

Made with ❤️ by Beacon Team

</div>
