from flask import Blueprint, request, jsonify, Response
from services.face_service  import capture_faces, train_model
from services.face_stream   import (start_recognition, stop_stream,
                                    gen_frames, get_result, get_status)
from services.attendance_service import mark_present_bulk

recognition_bp = Blueprint('recognition', __name__)


@recognition_bp.route('/capture', methods=['POST'])
def capture():
    data = request.get_json(force=True)
    sid  = data.get('student_id','').strip()
    if not sid:
        return jsonify({"success":False,"message":"student_id required"}), 400
    return jsonify(capture_faces(sid))


@recognition_bp.route('/train', methods=['POST'])
def train():
    return jsonify(train_model())


@recognition_bp.route('/stream/start', methods=['POST'])
def stream_start():
    start_recognition()
    return jsonify({"success": True})


@recognition_bp.route('/stream/feed')
def stream_feed():
    """MJPEG stream — Java reads this URL and shows it inside the camera box."""
    return Response(gen_frames(),
                    mimetype='multipart/x-mixed-replace; boundary=frame')


@recognition_bp.route('/stream/stop', methods=['POST'])
def stream_stop():
    stop_stream()
    result = get_result()
    if result["recognized_ids"]:
        result["attendance"] = mark_present_bulk(result["recognized_ids"])
    return jsonify(result)


@recognition_bp.route('/stream/status', methods=['GET'])
def stream_status():
    return jsonify(get_status())
