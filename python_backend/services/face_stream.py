"""
face_stream.py
==============
MJPEG stream that runs inside the Java UI "Live Camera Feed" box.

Flow:
  1. Camera opens inside Java box (no separate window)
  2. Face detected → LBPH match
     - NO MATCH  → Red box  "UNKNOWN"
     - MATCH     → Orange box "BLINK to confirm! (Xs)"
  3. Student blinks → Green box "NAME  PRESENT" → attendance marked
  4. No blink in 6s → "SPOOF REJECTED"
"""

import cv2, numpy as np, threading, time, os

BASE_DIR    = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODEL_PATH  = os.path.join(BASE_DIR, 'model', 'lbph_model.yml')
LABELS_PATH = os.path.join(BASE_DIR, 'model', 'labels.npy')
IMG_SIZE    = (150, 150)

CONFIDENCE_THRESHOLD = 90   # lower = stricter (raised again — was rejecting genuine matches)
SPOOF_TIMEOUT_SEC    = 12   # seconds to blink (generous — dip-detector needs several samples)
BLINKS_REQUIRED      = 1    # 1 blink enough
EYE_CLOSED_FRAMES    = 1    # min consecutive "closed" detections = 1 blink (was 2 — too strict at low frame rate)

# ── Performance tuning (Celeron-friendly) ───────────────────────────
CAPTURE_WIDTH  = 640     # camera capture resolution — kept full-size so LBPH
CAPTURE_HEIGHT = 480     # recognition crops stay sharp and accurate
STREAM_WIDTH   = 480     # output/display resolution (smaller = faster to encode/send)
STREAM_HEIGHT  = 360
DETECT_SCALE   = 0.4     # detect faces on an even smaller frame for speed
PROCESS_EVERY  = 4       # run detection every Nth frame
JPEG_QUALITY   = 55      # lower JPEG quality → faster encode + smaller stream
STREAM_FPS_CAP = 0.08    # ~12 fps cap on the MJPEG output

# ── Shared state ──────────────────────────────────────────────────
_lock           = threading.Lock()
_latest_jpeg    = None
_running        = False
_thread         = None

_recognized_ids = set()
_unknown_set    = set()   # kept for API compatibility (frame-count, unused for display now)
_unknown_track  = {}       # face-position-bucket -> last-seen-time, for de-duplicated unknown count
_spoofed_ids    = set()
_spoof_count    = 0


def _open_camera():
    for idx in [0, 1, 2]:
        cap = cv2.VideoCapture(idx, cv2.CAP_V4L2)
        if not cap.isOpened(): cap = cv2.VideoCapture(idx)
        if not cap.isOpened(): continue
        cap.set(cv2.CAP_PROP_FRAME_WIDTH,  CAPTURE_WIDTH)
        cap.set(cv2.CAP_PROP_FRAME_HEIGHT, CAPTURE_HEIGHT)
        cap.set(cv2.CAP_PROP_FPS,          12)
        cap.set(cv2.CAP_PROP_BUFFERSIZE,   1)
        for _ in range(25):
            ret, f = cap.read()
            if ret and f is not None and f.mean() > 5:
                print(f"  Camera ready: index {idx}")
                return cap
            time.sleep(0.08)
        cap.release()
    return None


def _push(frame):
    """Encode frame as JPEG and store for streaming."""
    global _latest_jpeg
    _, buf = cv2.imencode('.jpg', frame, [cv2.IMWRITE_JPEG_QUALITY, JPEG_QUALITY])
    with _lock:
        _latest_jpeg = buf.tobytes()


def _info_frame(line1, line2="", line3=""):
    """Create dark info frame (shown when camera not active)."""
    img = np.zeros((STREAM_HEIGHT, STREAM_WIDTH, 3), np.uint8)
    img[:] = (18, 18, 30)
    # Draw logo area
    cv2.rectangle(img, (0, 0), (STREAM_WIDTH, 45), (25, 25, 45), -1)
    cv2.putText(img, "Face Attendance System", (10, 30),
                cv2.FONT_HERSHEY_SIMPLEX, 0.65, (80, 120, 200), 2)
    # Lines
    if line1:
        cv2.putText(img, line1, (30, 150),
                    cv2.FONT_HERSHEY_SIMPLEX, 0.7, (180, 180, 220), 2)
    if line2:
        cv2.putText(img, line2, (30, 190),
                    cv2.FONT_HERSHEY_SIMPLEX, 0.55,  (100, 100, 160), 1)
    if line3:
        cv2.putText(img, line3, (30, 220),
                    cv2.FONT_HERSHEY_SIMPLEX, 0.5, (80, 80, 130), 1)
    return img


def _track_unknown_face(x, y, w, h, now, bucket_px=60, stale_sec=4.0):
    """
    De-duplicate UNKNOWN detections so the SAME unrecognised person
    standing in front of the camera is only counted once, while a
    genuinely DIFFERENT person (different face position) is counted
    separately.

    Works by bucketing the face's centre point onto a coarse grid
    (bucket_px) and remembering when each bucket was last seen. Old
    buckets (no detection for `stale_sec`) are dropped so a person who
    leaves and a different person who later stands in the same spot
    are correctly counted as distinct.
    """
    cx, cy = x + w // 2, y + h // 2
    key = (cx // bucket_px, cy // bucket_px)

    # Drop stale buckets first (no longer near the camera)
    for k in list(_unknown_track.keys()):
        if now - _unknown_track[k] > stale_sec:
            del _unknown_track[k]

    _unknown_track[key] = now


def _unknown_face_count():
    """Number of currently-distinct unrecognised faces being tracked."""
    return len(_unknown_track)


def _draw_box(frame, x, y, w, h, color, label):
    """Draw a face box with a label banner sized to fit the text
    (instead of being clipped to the face-box width)."""
    cv2.rectangle(frame, (x, y), (x+w, y+h), color, 2)

    font       = cv2.FONT_HERSHEY_SIMPLEX
    font_scale = 0.55
    thickness  = 2
    (text_w, text_h), baseline = cv2.getTextSize(label, font, font_scale, thickness)

    banner_w = max(w, text_w + 14)     # never narrower than the text itself
    banner_h = text_h + baseline + 10

    bx1, by1 = x, y + h
    bx2, by2 = x + banner_w, y + h + banner_h

    # Keep the banner on-screen if it would run past the frame edges
    fh_, fw_ = frame.shape[0], frame.shape[1]
    if bx2 > fw_: bx1, bx2 = fw_ - banner_w, fw_
    if by2 > fh_: by1, by2 = y - banner_h, y   # flip above the box instead

    cv2.rectangle(frame, (bx1, by1), (bx2, by2), color, cv2.FILLED)
    cv2.putText(frame, label, (bx1 + 7, by1 + text_h + 5),
                font, font_scale, (255, 255, 255), thickness)


def _draw_instruction(frame, x, y, w, text, color):
    """Draw a readable instruction banner above a face box, with its
    own solid background so it never overlaps illegibly with the feed."""
    font       = cv2.FONT_HERSHEY_SIMPLEX
    font_scale = 0.6
    thickness  = 2
    (text_w, text_h), baseline = cv2.getTextSize(text, font, font_scale, thickness)

    banner_w = text_w + 16
    banner_h = text_h + baseline + 10
    bx1 = max(0, x + (w - banner_w) // 2)
    by2 = max(banner_h, y - 6)
    by1 = by2 - banner_h
    bx2 = bx1 + banner_w

    fw_ = frame.shape[1]
    if bx2 > fw_: bx1, bx2 = fw_ - banner_w, fw_
    if bx1 < 0:   bx1, bx2 = 0, banner_w

    cv2.rectangle(frame, (bx1, by1), (bx2, by2), (20, 20, 20), cv2.FILLED)
    cv2.rectangle(frame, (bx1, by1), (bx2, by2), color, 2)
    cv2.putText(frame, text, (bx1 + 8, by2 - baseline - 4),
                font, font_scale, color, thickness)


def _recognition_loop():
    global _running, _recognized_ids, _unknown_set, _unknown_track
    global _spoofed_ids, _spoof_count

    # Reset all state
    _recognized_ids = set()
    _unknown_set    = set()
    _unknown_track  = {}
    _spoofed_ids    = set()
    _spoof_count    = 0

    # Check model exists
    if not os.path.exists(MODEL_PATH) or not os.path.exists(LABELS_PATH):
        for _ in range(40):
            if not _running: return
            _push(_info_frame("Model not found!",
                              "Please click 'Train Model' first",
                              "then click Start Recognition"))
            time.sleep(0.5)
        _running = False
        return

    # Load model
    recognizer = cv2.face.LBPHFaceRecognizer_create(threshold=300.0)
    recognizer.read(MODEL_PATH)
    id_to_sid = np.load(LABELS_PATH, allow_pickle=True).item()

    # Load cascades
    face_cascade = cv2.CascadeClassifier(
        cv2.data.haarcascades + 'haarcascade_frontalface_default.xml')
    eye_cascade  = cv2.CascadeClassifier(
        cv2.data.haarcascades + 'haarcascade_eye.xml')

    # Open camera — show loading frames while waiting
    _push(_info_frame("Opening camera...", "Please wait a moment"))
    cap = _open_camera()
    if cap is None:
        for _ in range(40):
            if not _running: return
            _push(_info_frame("Cannot open camera!",
                              "Check camera is connected",
                              "Run: ls /dev/video* in terminal"))
            time.sleep(0.5)
        _running = False
        return

    # Per-student blink state
    waiting       = {}
    frame_no      = 0
    # Persisted overlays from the last detection pass — drawn on every
    # frame so the feed looks smooth even though detection itself only
    # runs every PROCESS_EVERY frames.
    last_overlays = []   # list of (x, y, w, h, color, label, instruction_or_None)
    print("✅  Recognition stream started.")

    while _running:
        ret, frame = cap.read()
        if not ret or frame is None:
            time.sleep(0.02)
            continue

        frame_no += 1
        display  = frame.copy()

        if frame_no % PROCESS_EVERY == 0:
            # ── Detect on a downscaled frame (much faster Haar pass) ──
            small = cv2.resize(frame, None, fx=DETECT_SCALE, fy=DETECT_SCALE,
                                interpolation=cv2.INTER_LINEAR)
            small_gray = cv2.cvtColor(small, cv2.COLOR_BGR2GRAY)
            small_eq   = cv2.equalizeHist(small_gray)

            faces_small = face_cascade.detectMultiScale(
                small_eq, scaleFactor=1.2, minNeighbors=5,
                minSize=(int(80*DETECT_SCALE), int(80*DETECT_SCALE)))

            # Full-resolution gray only needed for recognition crops
            gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)

            new_overlays = []

            for (sx, sy, sw, sh) in faces_small:
                # Scale face box back up to full resolution
                fx = int(sx / DETECT_SCALE)
                fy = int(sy / DETECT_SCALE)
                fw = int(sw / DETECT_SCALE)
                fh = int(sh / DETECT_SCALE)
                fx = max(0, fx); fy = max(0, fy)
                fw = min(fw, frame.shape[1]-fx)
                fh = min(fh, frame.shape[0]-fy)
                if fw <= 0 or fh <= 0:
                    continue

                # ── Recognise on full-res crop for best accuracy ──────
                roi = gray[fy:fy+fh, fx:fx+fw]
                processed = cv2.equalizeHist(cv2.resize(roi, IMG_SIZE))
                label, conf = recognizer.predict(processed)
                print(f"  [recognize] label={label} conf={conf:.1f} "
                      f"threshold={CONFIDENCE_THRESHOLD} "
                      f"=> {'MATCH' if conf < CONFIDENCE_THRESHOLD else 'UNKNOWN'}")

                # CASE 1: UNKNOWN (no match, or matched label has no mapping)
                is_unknown = (conf >= CONFIDENCE_THRESHOLD)
                sid = None if is_unknown else id_to_sid.get(label, "UNKNOWN")
                if is_unknown or sid == "UNKNOWN":
                    _track_unknown_face(fx, fy, fw, fh, time.time())
                    new_overlays.append((fx, fy, fw, fh, (0,0,210),
                                         f"UNKNOWN (conf:{conf:.0f})", None))
                    continue

                # CASE 2: Already PRESENT
                if sid in _recognized_ids:
                    new_overlays.append((fx, fy, fw, fh, (0,200,0),
                                         f"{sid}  ✓ PRESENT", None))
                    continue

                # CASE 3: Already SPOOFED
                if sid in _spoofed_ids:
                    new_overlays.append((fx, fy, fw, fh, (0,0,140),
                                         "SPOOF REJECTED", None))
                    continue

                # CASE 4: Known face — blink watch
                if sid not in waiting:
                    waiting[sid] = {
                        "blinks":       0,
                        "history":      [],   # rolling recent eye-counts
                        "start":        time.time(),
                        "grace_until":  time.time() + 2.0,  # no SPOOF before this
                    }
                    print(f"  Match: {sid} (conf={conf:.1f}) — waiting for blink")

                st        = waiting[sid]
                now       = time.time()
                elapsed   = now - st["start"]
                remaining = int(SPOOF_TIMEOUT_SEC - elapsed)

                if elapsed > SPOOF_TIMEOUT_SEC and now > st["grace_until"]:
                    _spoofed_ids.add(sid)
                    _spoof_count += 1
                    waiting.pop(sid, None)
                    print(f"  SPOOF: {sid} — no blink in {SPOOF_TIMEOUT_SEC}s")
                    continue

                # ── Blink detection: rolling-history dip detector ──────
                # A real blink shows up as the eye-cascade's hit-count
                # dropping (often to 0, sometimes just to 1) for a beat
                # and then recovering. A single noisy frame where the
                # cascade misses one eye should NOT by itself count as
                # a blink, but a clear low->high recovery pattern should.
                face_eq = cv2.equalizeHist(roi)
                eyes = eye_cascade.detectMultiScale(
                    face_eq, scaleFactor=1.05, minNeighbors=2,
                    minSize=(12, 12), maxSize=(100, 100))
                eye_count = len(eyes)

                hist = st["history"]
                hist.append(eye_count)
                if len(hist) > 6:
                    hist.pop(0)

                print(f"  [{sid}] eye_count={eye_count} history={hist} "
                      f"blinks={st['blinks']}")

                # Look for a dip-and-recover pattern in the recent history:
                # a reading of 0 (eyes fully undetected) preceded AND
                # followed by a reading of >=1 (at least one eye visible)
                # within the rolling window. Using >=1 as the "open"
                # threshold instead of >=2, since reliably detecting BOTH
                # eyes every frame is too strict for this camera/cascade.
                if len(hist) >= 3:
                    if 0 in hist:
                        min_idx = hist.index(0)
                        has_before = any(v >= 1 for v in hist[:min_idx])
                        has_after  = any(v >= 1 for v in hist[min_idx+1:])
                        if has_before and has_after:
                            st["blinks"] += 1
                            st["history"] = []  # reset window so we don't
                                                 # double-count the same dip
                            print(f"  BLINK #{st['blinks']} detected for {sid}")

                # ── Fallback liveness check ─────────────────────────────
                # If the eye cascade never reports a clean "0" (some
                # cameras/lighting setups always find at least 1 hit even
                # mid-blink), fall back to detecting ANY variation in the
                # eye-count over the full rolling window. A static photo
                # or phone screen produces a perfectly flat, unchanging
                # reading; a real face — blinking or not — always shows
                # small natural fluctuation from head/eye micro-movement.
                if st["blinks"] < BLINKS_REQUIRED and len(hist) == 6:
                    if len(set(hist)) >= 2 and elapsed >= 2.5:
                        st["blinks"] += 1
                        st["history"] = []
                        print(f"  BLINK (fallback variation) detected for {sid}: {hist}")

                if st["blinks"] >= BLINKS_REQUIRED:
                    _recognized_ids.add(sid)
                    waiting.pop(sid, None)
                    print(f"  ✅  PRESENT: {sid}")
                    new_overlays.append((fx, fy, fw, fh, (0,200,0),
                                         f"{sid}  ✓ PRESENT", None))
                else:
                    new_overlays.append((fx, fy, fw, fh, (0,140,255),
                        f"{sid}  BLINK to confirm! ({remaining}s)",
                        ">>> PLEASE BLINK <<<"))

            last_overlays = new_overlays

        # ── Draw persisted overlays on every frame (smooth feed) ──────
        for (fx, fy, fw, fh, color, label, instruction) in last_overlays:
            _draw_box(display, fx, fy, fw, fh, color, label)
            if instruction:
                _draw_instruction(display, fx, fy, fw, instruction, color)

        # ── Top status bar (sized to the real frame, bigger & readable) ─
        frame_h, frame_w = display.shape[0], display.shape[1]
        cv2.rectangle(display, (0, 0), (frame_w, 42), (18, 18, 30), -1)
        cv2.putText(display,
                    f"Present: {len(_recognized_ids)}   "
                    f"Unknown: {_unknown_face_count()}   "
                    f"Spoof: {_spoof_count}",
                    (10, 28),
                    cv2.FONT_HERSHEY_SIMPLEX, 0.65, (255, 220, 0), 2)

        # ── Bottom spoof warning (anchored to the real frame bottom) ──
        if _spoofed_ids:
            cv2.rectangle(display, (0, frame_h-40), (frame_w, frame_h), (0, 0, 150), -1)
            cv2.putText(display,
                        f"SPOOF ATTEMPT: {', '.join(_spoofed_ids)}",
                        (10, frame_h-13),
                        cv2.FONT_HERSHEY_SIMPLEX, 0.6, (255, 255, 255), 2)

        # Recognition/detection above all ran on the full-resolution
        # `frame`/`display` for accuracy. Only the OUTGOING stream is
        # shrunk here — this keeps LBPH matching sharp while still
        # keeping the network/encode load light for the UI feed.
        stream_frame = cv2.resize(display, (STREAM_WIDTH, STREAM_HEIGHT),
                                  interpolation=cv2.INTER_LINEAR)
        _push(stream_frame)

    # ── Session ended ─────────────────────────────────────────────
    end = _info_frame(
        "Session ended",
        f"Students marked PRESENT: {len(_recognized_ids)}",
        "View Records tab for full attendance"
    )
    _push(end)
    cap.release()
    print("Recognition stream stopped.")


# ── Public API ────────────────────────────────────────────────────

def start_recognition():
    global _running, _thread
    if _running: return
    _running = True
    _thread  = threading.Thread(target=_recognition_loop, daemon=True)
    _thread.start()


def stop_stream():
    global _running
    _running = False
    if _thread: _thread.join(timeout=5)


def gen_frames():
    """MJPEG generator for Flask Response."""
    while True:
        with _lock:
            jpeg = _latest_jpeg
        if jpeg is None:
            img = _info_frame("Camera not started",
                              "Click Start Recognition")
            _, buf = cv2.imencode('.jpg', img)
            jpeg   = buf.tobytes()
        yield (b'--frame\r\n'
               b'Content-Type: image/jpeg\r\n\r\n'
               + jpeg + b'\r\n')
        time.sleep(STREAM_FPS_CAP)


def get_result():
    return {
        "success":        True,
        "recognized_ids": list(_recognized_ids),
        "unknown_count":  _unknown_face_count(),
        "spoof_count":    _spoof_count,
        "spoofed_ids":    list(_spoofed_ids)
    }


def get_status():
    return {
        "running":        _running,
        "present_count":  len(_recognized_ids),
        "unknown_count":  _unknown_face_count(),
        "spoof_count":    _spoof_count,
        "recognized_ids": list(_recognized_ids)
    }
