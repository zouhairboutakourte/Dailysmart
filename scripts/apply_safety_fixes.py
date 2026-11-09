from pathlib import Path
import sys

project = Path(sys.argv[1])

database = project / "app/src/main/java/com/dailysmart/app/data/local/DailySmartDatabase.kt"
dao = project / "app/src/main/java/com/dailysmart/app/data/local/dao/TaskDao.kt"
manifest = project / "app/src/main/AndroidManifest.xml"

db_text = database.read_text(encoding="utf-8")
fallback = "                .fallbackToDestructiveMigration()\n"
if fallback in db_text:
    db_text = db_text.replace(fallback, "")
database.write_text(db_text, encoding="utf-8")

dao_text = dao.read_text(encoding="utf-8")
old_query = "ORDER BY isCompleted ASC, priority DESC, dueDateMillis ASC, id DESC"
new_query = "ORDER BY isCompleted ASC, CASE priority WHEN 'HIGH' THEN 3 WHEN 'MEDIUM' THEN 2 ELSE 1 END DESC, dueDateMillis ASC, id DESC"
if old_query in dao_text:
    dao_text = dao_text.replace(old_query, new_query)
elif new_query not in dao_text:
    raise SystemExit("Task priority query did not match the expected source; refusing to continue.")
dao.write_text(dao_text, encoding="utf-8")

manifest_text = manifest.read_text(encoding="utf-8")
manifest_text = manifest_text.replace('android:allowBackup="true"', 'android:allowBackup="false"')
manifest.write_text(manifest_text, encoding="utf-8")

if ".fallbackToDestructiveMigration()" in database.read_text(encoding="utf-8"):
    raise SystemExit("Destructive Room migration fallback is still present.")
if new_query not in dao.read_text(encoding="utf-8"):
    raise SystemExit("Explicit priority sorting was not applied.")
if 'android:allowBackup="true"' in manifest.read_text(encoding="utf-8"):
    raise SystemExit("Automatic Android backup is still enabled.")

print("DailySmart safety fixes applied.")
