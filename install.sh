#!/usr/bin/env bash
set -e
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
ok()   { echo -e "${GREEN}✔  $*${NC}"; }
warn() { echo -e "${YELLOW}⚠  $*${NC}"; }

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VENV_DIR="$SCRIPT_DIR/python_backend/venv"

echo "=================================================="
echo " Face Attendance System — Linux Mint 22.3 Setup"
echo " Engine: OpenCV LBPH  |  NO TensorFlow  |  NO dlib"
echo "=================================================="

echo ""
echo "[1/5] Installing system packages..."
sudo apt-get update -qq
sudo apt-get install -y \
    python3.11 python3.11-dev python3.11-venv \
    build-essential pkg-config \
    libgl1 libglib2.0-0t64 \
    libsm6 libxrender1 libxext6 \
    mysql-server \
    openjdk-17-jdk maven \
    v4l-utils
ok "System packages installed"

echo ""
echo "[2/5] Creating Python 3.11 virtual environment..."
[ -d "$VENV_DIR" ] && rm -rf "$VENV_DIR"
python3.11 -m venv "$VENV_DIR"
source "$VENV_DIR/bin/activate"
pip install --upgrade pip wheel setuptools -q
ok "Venv ready — $(python --version)"

echo ""
echo "[3/5] Installing Python packages..."
pip install numpy==1.24.4 -q;                        ok "numpy"
pip install opencv-contrib-python==4.8.1.78 -q;       ok "opencv-contrib-python (LBPH)"
pip install flask==2.3.3 flask-cors==4.0.0 -q;        ok "flask"
pip install mysql-connector-python==8.1.0 -q;         ok "mysql-connector-python"
pip install Pillow==10.0.1 -q;                        ok "Pillow"
ok "All Python packages installed"

echo ""
echo "[4/5] Setting up MySQL..."
sudo systemctl enable mysql 2>/dev/null || true
sudo systemctl start mysql
sudo mysql -e "
CREATE DATABASE IF NOT EXISTS face_attendance CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'attendance_user'@'localhost' IDENTIFIED BY 'attendance123';
GRANT ALL PRIVILEGES ON face_attendance.* TO 'attendance_user'@'localhost';
FLUSH PRIVILEGES;
"
sudo mysql face_attendance < "$SCRIPT_DIR/schema.sql"
ok "MySQL ready  |  user: attendance_user  |  password: attendance123"

echo ""
echo "[5/5] Verifying Java and Maven..."
java -version 2>&1 | head -1; ok "Java OK"
mvn -version 2>&1 | head -1;  ok "Maven OK"

cat > "$SCRIPT_DIR/start_backend.sh" << 'EOF'
#!/usr/bin/env bash
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VENV="$SCRIPT_DIR/python_backend/venv"
if [ ! -d "$VENV" ]; then
    echo "❌  Virtual environment not found. Run: bash install.sh"
    exit 1
fi
source "$VENV/bin/activate"
echo "Python: $(python --version)"
cd "$SCRIPT_DIR/python_backend"
python app.py
EOF
chmod +x "$SCRIPT_DIR/start_backend.sh"

cat > "$SCRIPT_DIR/start_frontend.sh" << 'EOF'
#!/usr/bin/env bash
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/java_frontend"
mvn -q compile exec:java -Dexec.mainClass="com.attendance.Main"
EOF
chmod +x "$SCRIPT_DIR/start_frontend.sh"

echo ""
echo "=================================================="
echo -e "${GREEN}✅  INSTALLATION COMPLETE!${NC}"
echo "=================================================="
echo ""
echo "  Terminal 1:  bash start_backend.sh"
echo "  Terminal 2:  bash start_frontend.sh"
echo ""
