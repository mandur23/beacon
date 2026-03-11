from flask import Flask, request, jsonify
from flask_cors import CORS
import numpy as np
from datetime import datetime
import logging

app = Flask(__name__)
CORS(app)

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

class AnomalyDetector:
    def __init__(self):
        self.model_trained = False
        self.threshold = 0.7
        
    def detect_anomaly(self, traffic_data):
        """
        트래픽 데이터를 분석하여 이상 징후를 탐지합니다.
        """
        try:
            source_ip = traffic_data.get('sourceIp', '')
            destination_ip = traffic_data.get('destinationIp', '')
            protocol = traffic_data.get('protocol', '')
            bytes_transferred = traffic_data.get('bytesTransferred', 0)
            packets_transferred = traffic_data.get('packetsTransferred', 0)
            duration = traffic_data.get('duration', 0)
            
            anomaly_score = 0.0
            anomaly_reasons = []
            
            # 비정상적인 대용량 전송 감지
            if bytes_transferred > 10 * 1024 * 1024 * 1024:  # 10GB
                anomaly_score += 0.4
                anomaly_reasons.append("비정상적으로 큰 데이터 전송")
            
            # 짧은 시간에 과도한 패킷 전송
            if duration > 0:
                packets_per_second = packets_transferred / duration
                if packets_per_second > 1000:
                    anomaly_score += 0.3
                    anomaly_reasons.append("초당 패킷 수 과다")
            
            # 비정상적인 포트 사용 (상위 계층 분석 필요)
            # 여기서는 간단한 규칙 기반 탐지
            
            # 내부 IP에서 외부로 대량 전송
            if self._is_internal_ip(source_ip) and bytes_transferred > 1024 * 1024 * 100:  # 100MB
                anomaly_score += 0.2
                anomaly_reasons.append("내부에서 외부로 대량 데이터 전송")
            
            # ICMP 프로토콜 과다 사용
            if protocol == 'ICMP' and packets_transferred > 100:
                anomaly_score += 0.25
                anomaly_reasons.append("ICMP 패킷 과다 (DDoS 의심)")
            
            is_anomaly = anomaly_score >= self.threshold
            
            return {
                'isAnomaly': is_anomaly,
                'anomalyScore': round(min(anomaly_score, 10.0), 2),
                'anomalyReason': ', '.join(anomaly_reasons) if anomaly_reasons else 'None'
            }
            
        except Exception as e:
            logger.error(f"Error detecting anomaly: {str(e)}")
            return {
                'isAnomaly': False,
                'anomalyScore': 0.0,
                'anomalyReason': f'Error: {str(e)}'
            }
    
    def _is_internal_ip(self, ip):
        """내부 IP인지 확인"""
        if not ip:
            return False
        parts = ip.split('.')
        if len(parts) != 4:
            return False
        try:
            first_octet = int(parts[0])
            second_octet = int(parts[1])
            # 10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16
            if first_octet == 10:
                return True
            if first_octet == 172 and 16 <= second_octet <= 31:
                return True
            if first_octet == 192 and second_octet == 168:
                return True
        except:
            pass
        return False

detector = AnomalyDetector()

@app.route('/api/detect-anomaly', methods=['POST'])
def detect_anomaly():
    """단일 트래픽 데이터 이상 탐지"""
    try:
        data = request.json
        result = detector.detect_anomaly(data)
        logger.info(f"Anomaly detection result: {result}")
        return jsonify(result)
    except Exception as e:
        logger.error(f"Error in detect_anomaly endpoint: {str(e)}")
        return jsonify({
            'isAnomaly': False,
            'anomalyScore': 0.0,
            'anomalyReason': f'Server error: {str(e)}'
        }), 500

@app.route('/api/batch-detect-anomaly', methods=['POST'])
def batch_detect_anomaly():
    """배치 트래픽 데이터 이상 탐지"""
    try:
        data_list = request.json
        results = []
        for data in data_list:
            result = detector.detect_anomaly(data)
            results.append(result)
        return jsonify(results)
    except Exception as e:
        logger.error(f"Error in batch_detect_anomaly endpoint: {str(e)}")
        return jsonify({'error': str(e)}), 500

@app.route('/api/train-model', methods=['POST'])
def train_model():
    """모델 학습 (향후 ML 모델 통합 시 사용)"""
    try:
        # 향후 실제 ML 모델 학습 로직 구현
        detector.model_trained = True
        return jsonify({
            'success': True,
            'message': 'Model training initiated',
            'timestamp': datetime.now().isoformat()
        })
    except Exception as e:
        logger.error(f"Error in train_model endpoint: {str(e)}")
        return jsonify({
            'success': False,
            'message': f'Training failed: {str(e)}'
        }), 500

@app.route('/api/model-status', methods=['GET'])
def model_status():
    """모델 상태 확인"""
    return jsonify({
        'available': True,
        'trained': detector.model_trained,
        'threshold': detector.threshold,
        'version': '1.0.0',
        'timestamp': datetime.now().isoformat()
    })

@app.route('/health', methods=['GET'])
def health_check():
    """헬스 체크"""
    return jsonify({
        'status': 'healthy',
        'timestamp': datetime.now().isoformat()
    })

if __name__ == '__main__':
    logger.info("Starting Beacon ML Service on port 5000...")
    app.run(host='0.0.0.0', port=5000, debug=True)
