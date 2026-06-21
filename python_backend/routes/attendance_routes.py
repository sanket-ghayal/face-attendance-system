from flask import Blueprint, request, jsonify
from services.attendance_service import (mark_present_bulk,
    get_attendance_for_date, get_present_for_date,
    get_attendance_history, delete_attendance_record)

attendance_bp = Blueprint('attendance', __name__)

@attendance_bp.route('/attendance', methods=['GET'])
def get_attendance():
    return jsonify(get_attendance_for_date(request.args.get('date')))

@attendance_bp.route('/attendance/present', methods=['GET'])
def get_present_only():
    """Returns ONLY students marked PRESENT for the given date."""
    return jsonify(get_present_for_date(request.args.get('date')))

@attendance_bp.route('/attendance/history', methods=['GET'])
def history():
    return jsonify(get_attendance_history(int(request.args.get('days',30))))

@attendance_bp.route('/attendance/mark_bulk', methods=['POST'])
def mark_bulk():
    data = request.get_json(force=True)
    return jsonify(mark_present_bulk(data.get('student_ids',[]), data.get('date')))

@attendance_bp.route('/attendance/<student_id>/<attendance_date>', methods=['DELETE'])
def delete_record(student_id, attendance_date):
    """Admin: delete one attendance row (student_id + date)."""
    return jsonify(delete_attendance_record(student_id, attendance_date))

