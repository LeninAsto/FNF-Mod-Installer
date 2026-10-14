"""Check translation coverage, duplicates and Android formatting parameters."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

resources = Path(__file__).resolve().parents[1] / "app/src/main/res"

def catalog(directory):
    result = {}
    for path in sorted(directory.glob("*.xml")):
        for node in ET.parse(path).getroot():
            if node.tag != "string" or node.get("translatable") == "false":
                continue
            key = node.get("name")
            if key in result:
                raise ValueError(f"Duplicate translation: {directory.name}/{key}")
            result[key] = "".join(node.itertext())
    return result

base = catalog(resources / "values")
locales = [p for p in sorted(resources.glob("values*")) if p.name != "values" and any(p.glob("*strings.xml"))]
for directory in locales:
    translated = catalog(directory)
    missing = base.keys() - translated.keys()
    extra = translated.keys() - base.keys()
    if missing or extra:
        raise ValueError(f"{directory.name}: missing={sorted(missing)}, extra={sorted(extra)}")
    for key, source in base.items():
        placeholders = lambda text: sorted(re.findall(r"%(?:\d+\$)?[sd]", text))
        if placeholders(source) != placeholders(translated[key]):
            raise ValueError(f"Formatting parameter mismatch: {directory.name}/{key}")
    print(f"{directory.name}: {len(base)} strings complete")
print(f"Validated {len(locales) + 1} language variants.")
