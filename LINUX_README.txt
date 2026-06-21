================================================================
  FACE RECOGNITION ATTENDANCE SYSTEM
  with Blink Liveness Anti-Spoofing
  Linux Mint 22.3 — OpenCV LBPH (no TensorFlow / no dlib)
================================================================

FIRST TIME SETUP
─────────────────
  cd face_attendance_system
  bash install.sh

RUN (every time, 2 terminals)
─────────────────
  Terminal 1:  bash start_backend.sh
  Terminal 2:  bash start_frontend.sh

HOW IT WORKS
─────────────────
1. REGISTER STUDENT tab
   - Enter Student ID + Name + Course
   - Click "Register & Capture Face"
   - Camera opens (separate window) — 50 photos captured
     while you turn your head left/right/up as guided
   - Press Q when done, or it stops automatically

2. TAKE ATTENDANCE tab
   - Click "Train Model" (after registering all students)
   - Click "Start Recognition"
   - LIVE CAMERA FEED appears INSIDE the app window
     (no separate OpenCV window — it streams into the UI box)

   Recognition + Anti-Spoof Flow:
     🔴 Red box     = face not recognised → UNKNOWN
     🟠 Orange box  = face MATCHED → "BLINK to confirm!" (6 sec timer)
     🟢 Green box   = blink detected → PRESENT marked in database
     ⚠  If no blink within 6 seconds → "SPOOF REJECTED"
        (stops someone holding up a photo/phone screen)

   - Click "Stop Session" when finished
   - Popup shows: present count, already-marked, unknown, spoof-blocked

3. VIEW & EXPORT tab
   - Daily Attendance: pick any date, see Present/Absent list
   - Day-by-Day History: last 30 days summary
   - Export CSV button saves the daily list to a file

ANTI-SPOOF DETAILS
─────────────────
Blink detection uses TWO combined signals:
  1. Eye-cascade detector — eyes "disappear" momentarily when closed
  2. Brightness change in upper face region — closing eyelids
     changes local brightness measurably
Both must agree a blink occurred before attendance is marked.
This blocks static photos and phone-screen playback from faking
attendance, since neither can produce a real blink pattern.

DUPLICATE / DOUBLE-MARK PROTECTION
─────────────────
- Same Student ID cannot be registered twice (clear error shown)
- If a student is recognised twice in one day, second time shows
  "already marked" — does not duplicate the attendance row

TROUBLESHOOTING
─────────────────
Camera shows black / "Cannot open camera":
  ls /dev/video*
  sudo usermod -a -G video $USER   (then log out/in)

"DB error" on backend startup:
  sudo systemctl start mysql
  Check python_backend/database/db.py credentials

Recognition always says UNKNOWN:
  - Re-register with better lighting (face well-lit, no backlight)
  - Click Train Model again after re-capturing

Blink not detected / always spoof:
  - Make sure your full face (both eyes) is clearly visible
  - Increase SPOOF_TIMEOUT_SEC in face_stream.py if needed
  - Blink naturally and fully (not a fast partial blink)

PROJECT FILES
─────────────────
face_attendance_system/
├── install.sh / start_backend.sh / start_frontend.sh
├── schema.sql
├── python_backend/
│   ├── app.py
│   ├── database/db.py
│   ├── services/
│   │   ├── attendance_service.py   (students, attendance, history)
│   │   ├── face_service.py         (capture + train)
│   │   └── face_stream.py          (MJPEG + blink anti-spoof)
│   └── routes/
│       ├── student_routes.py
│       ├── attendance_routes.py
│       └── recognition_routes.py   (stream start/feed/stop/status)
└── java_frontend/
    └── src/main/java/com/attendance/
        ├── Main.java
        ├── api/ApiClient.java
        ├── ui/
        │   ├── Theme.java          (shared dark styling)
        │   ├── MainFrame.java      (sidebar navigation)
        │   ├── RegisterPanel.java
        │   ├── AttendancePanel.java (live MJPEG feed inside UI box)
        │   └── RecordsPanel.java
        └── utils/CsvExporter.java
================================================================
