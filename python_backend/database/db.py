import mysql.connector
from mysql.connector import pooling
import os

DB_CONFIG = {
    'host':       os.getenv('DB_HOST',     'localhost'),
    'port':       int(os.getenv('DB_PORT', '3306')),
    'user':       os.getenv('DB_USER',     'attendance_user'),
    'password':   os.getenv('DB_PASSWORD', 'attendance123'),
    'database':   os.getenv('DB_NAME',     'face_attendance'),
    'charset':    'utf8mb4',
    'autocommit': True,
    'connection_timeout': 10,
}

_pool = None

def _get_pool():
    global _pool
    if _pool is None:
        _pool = pooling.MySQLConnectionPool(
            pool_name='att_pool', pool_size=10,
            pool_reset_session=True, **DB_CONFIG)
    return _pool

def get_connection():
    return _get_pool().get_connection()

def init_db():
    conn = cur = None
    try:
        conn = get_connection()
        cur  = conn.cursor()
        cur.execute("SELECT COUNT(*) FROM students")
        cur.fetchall()
        print("✅  DB initialized OK")
    except Exception as e:
        print(f"❌  DB error: {e}")
        raise
    finally:
        try:
            if cur:  cur.close()
            if conn: conn.close()
        except: pass
