#!/usr/bin/env python3
"""Create small read-only test databases from public reference packages.
Input is a directory of verified public DBs, not a user's research library.
Generated assets are ignored by Git and included in the test APK only.
"""
import json
import sqlite3
import sys
from pathlib import Path
root=Path(sys.argv[1])
project=Path(__file__).resolve().parent.parent
out=project/'app/src/androidTest/assets/reference-fixtures'
out.mkdir(parents=True,exist_ok=True)
for m in json.loads((project/'app/src/main/assets/research-aids.json').read_text())['aids']:
    dest=out/m['file']
    if dest.exists():dest.unlink()
    with sqlite3.connect(f'file:{root/m["file"]}?mode=ro',uri=True) as source,sqlite3.connect(dest) as target:
        tables=source.execute("SELECT name,sql FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'").fetchall()
        shadows={row[1] for row in source.execute('PRAGMA table_list') if row[2]=='shadow'}
        for name,sql in tables:
            if name in shadows:continue
            target.execute(sql)
        for name,sql in tables:
            if name in shadows:continue
            rows=source.execute(f'SELECT * FROM "{name}" LIMIT 200').fetchall()
            if name=='templates':rows=rows[:12]
            if rows:
                target.executemany(f'INSERT INTO "{name}" VALUES ({",".join("?" for _ in rows[0])})',rows)
        target.execute('PRAGMA user_version='+str(source.execute('PRAGMA user_version').fetchone()[0]))
        target.commit()
    print(m['id'],dest.stat().st_size)
