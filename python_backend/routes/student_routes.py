from flask import Blueprint, request, jsonify
from services.attendance_service import register_student, get_all_students

student_bp = Blueprint('students', __name__)

@student_bp.route('/students', methods=['GET'])
def list_students():
    return jsonify(get_all_students())

@student_bp.route('/students', methods=['POST'])
def add_student():
    data = request.get_json(force=True)
    sid  = data.get('student_id','').strip()
    name = data.get('name','').strip()
    if not sid or not name:
        return jsonify({"success":False,"message":"student_id and name required"}), 400
    return jsonify(register_student(sid, name, data.get('course','').strip()))
