# Beacon ML Service

이 디렉토리는 Beacon 보안 모니터링 시스템의 Python 기반 머신러닝 서비스를 포함합니다.

## 설치

```bash
cd python-ml
pip install -r requirements.txt
```

## 실행

```bash
python app.py
```

서비스는 기본적으로 `http://localhost:5000`에서 실행됩니다.

## API 엔드포인트

### 1. 이상 탐지
- **POST** `/api/detect-anomaly`
- Request Body:
```json
{
  "sourceIp": "192.168.1.100",
  "destinationIp": "8.8.8.8",
  "protocol": "TCP",
  "bytesTransferred": 1048576,
  "packetsTransferred": 1000,
  "duration": 10
}
```
- Response:
```json
{
  "isAnomaly": true,
  "anomalyScore": 7.5,
  "anomalyReason": "비정상적으로 큰 데이터 전송"
}
```

### 2. 배치 이상 탐지
- **POST** `/api/batch-detect-anomaly`
- Request Body: 배열 형태의 트래픽 데이터

### 3. 모델 학습
- **POST** `/api/train-model`
- 머신러닝 모델 학습 트리거 (향후 구현)

### 4. 모델 상태
- **GET** `/api/model-status`
- 현재 모델의 상태 정보 반환

### 5. 헬스 체크
- **GET** `/health`
- 서비스 상태 확인

## 향후 개선 사항

1. **실제 ML 모델 통합**: scikit-learn, TensorFlow, PyTorch 등을 사용한 고급 이상 탐지 모델
2. **데이터 수집**: 역사적 트래픽 데이터를 기반으로 학습
3. **자동 재학습**: 주기적으로 모델을 업데이트
4. **더 정교한 특징 추출**: 시계열 분석, 패턴 인식 등
5. **다중 모델 앙상블**: 여러 모델의 결과를 조합하여 정확도 향상
