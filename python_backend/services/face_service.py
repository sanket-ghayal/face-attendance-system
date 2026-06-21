"""
face_service.py - Face capture and model training only.
Recognition is handled by face_stream.py (MJPEG stream inside Java UI).
"""
import os, cv2, numpy as np, time

BASE_DIR    = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATASET_DIR = os.path.join(BASE_DIR, 'dataset')
MODEL_DIR   = os.path.join(BASE_DIR, 'model')
MODEL_PATH  = os.path.join(MODEL_DIR, 'lbph_model.yml')
LABELS_PATH = os.path.join(MODEL_DIR, 'labels.npy')

os.makedirs(DATASET_DIR, exist_ok=True)
os.makedirs(MODEL_DIR,   exist_ok=True)

IMG_SIZE      = (150, 150)
CAPTURE_COUNT = 50


def _open_camera():
    for idx in [0, 1, 2]:
        cap = cv2.VideoCapture(idx, cv2.CAP_V4L2)
        if not cap.isOpened(): cap = cv2.VideoCapture(idx)
        if not cap.isOpened(): continue
        cap.set(cv2.CAP_PROP_FRAME_WIDTH,  640)
        cap.set(cv2.CAP_PROP_FRAME_HEIGHT, 480)
        cap.set(cv2.CAP_PROP_FPS, 15)
        cap.set(cv2.CAP_PROP_BUFFERSIZE, 1)
        for _ in range(40):
            ret, f = cap.read()
            if ret and f is not None and f.mean() > 5:
                return cap
            time.sleep(0.08)
        cap.release()
    return None


def capture_faces(student_id):
    student_dir = os.path.join(DATASET_DIR, student_id)
    if os.path.exists(student_dir):
        for f in os.listdir(student_dir): os.remove(os.path.join(student_dir, f))
    os.makedirs(student_dir, exist_ok=True)

    cap          = _open_camera()
    face_cascade = cv2.CascadeClassifier(
        cv2.data.haarcascades + 'haarcascade_frontalface_default.xml')

    if cap is None:
        return {"success": False, "message": "Cannot open camera"}

    guides = {0:"Look STRAIGHT",10:"Turn LEFT",20:"Turn RIGHT",30:"Tilt UP",40:"Look STRAIGHT"}
    count  = 0

    cv2.namedWindow(f"Capture - {student_id}", cv2.WINDOW_NORMAL)
    cv2.resizeWindow(f"Capture - {student_id}", 640, 480)

    while count < CAPTURE_COUNT:
        ret, frame = cap.read()
        if not ret or frame is None:
            time.sleep(0.03); continue

        gray    = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
        gray_eq = cv2.equalizeHist(gray)
        faces   = face_cascade.detectMultiScale(
            gray_eq, scaleFactor=1.2, minNeighbors=5, minSize=(80,80))

        display = frame.copy()
        guide   = guides.get(count, guides[max(k for k in guides if k <= count)])
        cv2.putText(display, guide, (10,58), cv2.FONT_HERSHEY_SIMPLEX, 0.65, (255,200,0), 2)
        cv2.putText(display, f"Captured: {count}/{CAPTURE_COUNT}  (Q=stop)",
                    (10,30), cv2.FONT_HERSHEY_SIMPLEX, 0.8, (0,210,0), 2)

        bar_w = int((count / CAPTURE_COUNT) * 620)
        cv2.rectangle(display, (10,460), (630,475), (50,50,50), -1)
        cv2.rectangle(display, (10,460), (10+bar_w,475), (0,200,0), -1)

        if len(faces) > 0:
            x, y, w, h = faces[0]
            cv2.rectangle(display, (x,y), (x+w,y+h), (0,210,0), 2)
            crop = cv2.equalizeHist(cv2.resize(gray[y:y+h,x:x+w], IMG_SIZE))
            cv2.imwrite(os.path.join(student_dir, f"img_{count:03d}.jpg"), crop)
            count += 1
            cv2.waitKey(120)
        else:
            cv2.putText(display, "No face — move closer", (10,85),
                        cv2.FONT_HERSHEY_SIMPLEX, 0.6, (0,0,255), 2)

        cv2.imshow(f"Capture - {student_id}", display)
        if cv2.waitKey(1) & 0xFF in (ord('q'), ord('Q'), 27): break

    cap.release()
    cv2.destroyAllWindows()

    if count == 0:
        return {"success": False, "message": "No face detected. Improve lighting."}
    return {"success": True, "message": f"Captured {count} images for {student_id}", "count": count}


def train_model():
    if not os.path.isdir(DATASET_DIR) or not os.listdir(DATASET_DIR):
        return {"success": False, "message": "Dataset empty. Register a student first."}

    faces, labels, id_to_sid = [], [], {}
    counter = 0
    trained = []

    for sid in sorted(os.listdir(DATASET_DIR)):
        sdir = os.path.join(DATASET_DIR, sid)
        if not os.path.isdir(sdir): continue
        id_to_sid[counter] = sid
        loaded = 0
        for f in sorted(os.listdir(sdir)):
            if not f.lower().endswith(('.jpg','.jpeg','.png')): continue
            img = cv2.imread(os.path.join(sdir, f), cv2.IMREAD_GRAYSCALE)
            if img is None: continue
            faces.append(cv2.resize(img, IMG_SIZE))
            labels.append(counter)
            loaded += 1
        if loaded:
            trained.append(sid)
            print(f"  {sid}: {loaded} images")
        counter += 1

    if not faces:
        return {"success": False, "message": "No images found in dataset."}

    recognizer = cv2.face.LBPHFaceRecognizer_create(
        radius=2, neighbors=8, grid_x=8, grid_y=8, threshold=300.0)
    recognizer.train(faces, np.array(labels))
    recognizer.save(MODEL_PATH)
    np.save(LABELS_PATH, id_to_sid)

    msg = f"Trained {len(trained)} student(s), {len(faces)} total images"
    print(f"✅  {msg}")
    return {"success": True, "message": msg, "students_trained": len(trained)}
