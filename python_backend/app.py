import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from flask import Flask, jsonify
from flask_cors import CORS
from database.db import init_db
from routes.student_routes     import student_bp
from routes.attendance_routes  import attendance_bp
from routes.recognition_routes import recognition_bp

app = Flask(__name__)
CORS(app)
app.register_blueprint(student_bp,     url_prefix='/api')
app.register_blueprint(attendance_bp,  url_prefix='/api')
app.register_blueprint(recognition_bp, url_prefix='/api')

@app.route('/api/health')
def health():
    return jsonify({"status":"ok","engine":"OpenCV-LBPH"})

if __name__ == '__main__':
    print("="*52)
    print("  Face Attendance — OpenCV LBPH Engine")
    print("  Stream: http://127.0.0.1:5000/api/stream/feed")
    print("="*52)
    init_db()
    print("Running on http://127.0.0.1:5000\n")
    app.run(host='127.0.0.1', port=5000, debug=False, threaded=True)
