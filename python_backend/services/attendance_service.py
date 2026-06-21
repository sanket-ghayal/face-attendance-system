from datetime import date
from database.db import get_connection


def register_student(student_id, name, course=''):
    conn = cur = None
    try:
        conn = get_connection()
        cur  = conn.cursor(dictionary=True)
        cur.execute("SELECT student_id, name FROM students WHERE student_id=%s", (student_id,))
        existing = cur.fetchone()
        if existing:
            return {"success": False, "duplicate": True,
                    "message": f"ID '{student_id}' already registered as '{existing['name']}'"}
        cur.execute("INSERT INTO students (student_id,name,course) VALUES (%s,%s,%s)",
                    (student_id, name, course))
        cur.fetchall()
        return {"success": True, "student_id": student_id}
    except Exception as e:
        return {"success": False, "message": str(e)}
    finally:
        try:
            if cur: cur.close()
            if conn: conn.close()
        except: pass


def get_all_students():
    conn = cur = None
    try:
        conn = get_connection()
        cur  = conn.cursor(dictionary=True)
        cur.execute("SELECT student_id,name,course,registered_at FROM students ORDER BY name")
        rows = cur.fetchall()
        for r in rows:
            if r.get('registered_at'): r['registered_at'] = str(r['registered_at'])
        return rows
    except Exception as e:
        print(f"get_all_students error: {e}")
        return []
    finally:
        try:
            if cur: cur.close()
            if conn: conn.close()
        except: pass


def mark_present(student_id, attendance_date=None):
    target = attendance_date or date.today().isoformat()
    conn = cur = None
    try:
        conn = get_connection()
        cur  = conn.cursor(dictionary=True)
        cur.execute("SELECT status, marked_at FROM attendance WHERE student_id=%s AND date=%s",
                    (student_id, target))
        existing = cur.fetchone()
        if existing and existing['status'] == 'PRESENT':
            return {"success": True, "already_marked": True, "student_id": student_id,
                    "date": target, "marked_at": str(existing['marked_at'])}
        cur.execute("""
            INSERT INTO attendance (student_id,date,status)
            VALUES (%s,%s,'PRESENT')
            ON DUPLICATE KEY UPDATE status='PRESENT', marked_at=NOW()
        """, (student_id, target))
        cur.fetchall()
        return {"success": True, "already_marked": False, "student_id": student_id, "date": target}
    except Exception as e:
        return {"success": False, "message": str(e)}
    finally:
        try:
            if cur: cur.close()
            if conn: conn.close()
        except: pass


def mark_present_bulk(student_ids, attendance_date=None):
    target  = attendance_date or date.today().isoformat()
    marked, already, errors = [], [], []
    for sid in student_ids:
        r = mark_present(sid, target)
        if not r["success"]:          errors.append(sid)
        elif r.get("already_marked"): already.append(sid)
        else:                         marked.append(sid)
    return {"success": True, "marked": marked, "already": already,
            "errors": errors, "date": target}


def get_attendance_for_date(query_date=None):
    target = query_date or date.today().isoformat()
    conn = cur = None
    try:
        conn = get_connection()
        cur  = conn.cursor(dictionary=True)
        cur.execute("""
            SELECT s.student_id, s.name, s.course,
                   COALESCE(a.status,'ABSENT') AS status,
                   CASE WHEN a.marked_at IS NOT NULL
                        THEN CONCAT(LPAD(HOUR(a.marked_at),2,'0'),':',
                                    LPAD(MINUTE(a.marked_at),2,'0'),':',
                                    LPAD(SECOND(a.marked_at),2,'0'))
                        ELSE '--' END AS marked_at
            FROM students s
            LEFT JOIN attendance a ON s.student_id=a.student_id AND a.date=%s
            ORDER BY s.name
        """, (target,))
        rows = cur.fetchall()
        print(f"  [DB] {len(rows)} rows for {target}")
        return rows
    except Exception as e:
        print(f"  [DB] ERROR: {e}")
        return []
    finally:
        try:
            if cur: cur.close()
            if conn: conn.close()
        except: pass


def get_present_for_date(query_date=None):
    """Return ONLY students marked PRESENT on the given date (no absentees)."""
    target = query_date or date.today().isoformat()
    conn = cur = None
    try:
        conn = get_connection()
        cur  = conn.cursor(dictionary=True)
        cur.execute("""
            SELECT s.student_id, s.name, s.course,
                   a.status,
                   CONCAT(LPAD(HOUR(a.marked_at),2,'0'),':',
                          LPAD(MINUTE(a.marked_at),2,'0'),':',
                          LPAD(SECOND(a.marked_at),2,'0')) AS marked_at
            FROM attendance a
            JOIN students s ON s.student_id = a.student_id
            WHERE a.date = %s AND a.status = 'PRESENT'
            ORDER BY a.marked_at ASC
        """, (target,))
        rows = cur.fetchall()
        print(f"  [DB] {len(rows)} PRESENT rows for {target}")
        return rows
    except Exception as e:
        print(f"  [DB] ERROR: {e}")
        return []
    finally:
        try:
            if cur: cur.close()
            if conn: conn.close()
        except: pass


def delete_attendance_record(student_id, attendance_date):
    """Admin action: remove a single attendance row for one student/date."""
    conn = cur = None
    try:
        conn = get_connection()
        cur  = conn.cursor()
        cur.execute("DELETE FROM attendance WHERE student_id=%s AND date=%s",
                    (student_id, attendance_date))
        cur.fetchall() if cur.with_rows else None
        affected = cur.rowcount
        if affected == 0:
            return {"success": False,
                    "message": f"No attendance record found for {student_id} on {attendance_date}"}
        return {"success": True, "student_id": student_id,
                "date": attendance_date, "deleted": affected}
    except Exception as e:
        return {"success": False, "message": str(e)}
    finally:
        try:
            if cur: cur.close()
            if conn: conn.close()
        except: pass


def get_attendance_history(days=30):
    conn = cur = None
    try:
        conn = get_connection()
        cur  = conn.cursor(dictionary=True)
        cur.execute("""
            SELECT a.date,
                   COUNT(DISTINCT a.student_id) AS present_count,
                   (SELECT COUNT(*) FROM students) AS total_students,
                   GROUP_CONCAT(s.name ORDER BY s.name SEPARATOR ', ') AS present_names
            FROM attendance a
            JOIN students s ON a.student_id=s.student_id
            WHERE a.status='PRESENT'
              AND a.date >= DATE_SUB(CURDATE(), INTERVAL %s DAY)
            GROUP BY a.date ORDER BY a.date DESC
        """, (days,))
        rows = cur.fetchall()
        for r in rows: r['date'] = str(r['date'])
        return rows
    except Exception as e:
        print(f"get_attendance_history error: {e}")
        return []
    finally:
        try:
            if cur: cur.close()
            if conn: conn.close()
        except: pass
