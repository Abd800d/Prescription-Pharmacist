"""Prints failing unit tests from Gradle XML results (used by CI annotations)."""
import glob
import xml.etree.ElementTree as ET

for f in glob.glob("app/build/test-results/testDebugUnitTest/*.xml"):
    try:
        root = ET.parse(f).getroot()
    except Exception:
        continue
    for tc in root.iter("testcase"):
        for fail in list(tc.iter("failure")) + list(tc.iter("error")):
            msg = (fail.get("message") or "").splitlines()[:2]
            name = str(tc.get("classname")) + " > " + str(tc.get("name"))
            print("FAILED " + name + ": " + " | ".join(msg)[:300])
