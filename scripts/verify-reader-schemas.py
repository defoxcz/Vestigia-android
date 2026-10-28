#!/usr/bin/env python3
"""Read-only SQL verification using public reference packages outside the repository.
Run JVM tests with VESTIGIA_READER_PROBES first; see docs/readers.md.
"""
import base64
import json
import sqlite3
import sys
import unicodedata
from pathlib import Path

root, probes = map(Path, sys.argv[1:3])
assets = Path(__file__).resolve().parent.parent / 'app/src/main/assets/research-aids.json'
files = {m['id']: m['file'] for m in json.loads(assets.read_text())['aids']}
count = 0
for row in probes.read_text().splitlines():
    aid, language, source, key, title, summary, search, fts, labels, facet, options, fields, images, image_sql, extra_sql = [base64.b64decode(v).decode() for v in row.split('\t')]
    with sqlite3.connect(f'file:{root / files[aid]}?mode=ro', uri=True) as db:
        db.execute('PRAGMA query_only=ON')
        db.create_function('vestigia_fold', 1, lambda v: ''.join(c for c in unicodedata.normalize('NFD', v or '') if not unicodedata.combining(c)).lower())
        total = db.execute(f'SELECT count(*) FROM {source}').fetchone()[0]
        page = db.execute(f'SELECT {key}, {title}, {summary}, {labels} FROM {source} ORDER BY 2 COLLATE NOCASE, 1 LIMIT 24').fetchall()
        assert total > 0 and page, (aid, language)
        for record in page[:3]:
            detail = db.execute(f'SELECT {key},{title},{summary},{labels},{fields} FROM {source} WHERE {key}=? LIMIT 1', (record[0],)).fetchone()
            assert detail
            for query in extra_sql.splitlines():
                db.execute(query, (record[0],)).fetchall()
            if image_sql:
                db.execute(image_sql, (record[0],)).fetchall()
        db.execute(f'SELECT count(*) FROM {source} WHERE vestigia_fold(COALESCE({search},\'\')) LIKE ? ESCAPE \'\\\'', ("%xyz%",)).fetchone()
        if fts:
            db.execute(f'SELECT count(*) FROM {source} WHERE {fts}', ('"test"*',)).fetchone()
        db.execute(f'SELECT count(*) FROM {source} WHERE {images}').fetchone()
        if options:
            values = db.execute(options).fetchall()
            if values:
                condition = facet if '?' in facet else facet + '=?'
                db.execute(f'SELECT count(*) FROM {source} WHERE {condition}', (values[0][0],)).fetchone()
        print(f'{aid}/{language}: {total} records, page/detail/search/filter/images OK', flush=True)
        count += 1
assert count == 26
