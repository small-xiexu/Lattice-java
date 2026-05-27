from pathlib import Path

from openpyxl import Workbook
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import getSampleStyleSheet
from reportlab.platypus import Paragraph, SimpleDocTemplate, Spacer


ROOT = Path(__file__).resolve().parents[1]
PDF_PATH = ROOT / "sources" / "03_pdf" / "incident-response-reference-lite.pdf"
XLSX_PATH = ROOT / "sources" / "04_office" / "incident-response-checklists-lite.xlsx"


def build_pdf() -> None:
    PDF_PATH.parent.mkdir(parents=True, exist_ok=True)
    styles = getSampleStyleSheet()
    story = []
    story.append(Paragraph("Incident Response Reference Lite", styles["Title"]))
    story.append(Spacer(1, 12))
    sections = [
        (
            "Introduction",
            "This document summarizes a minimal incident response flow for knowledge base acceptance. "
            "The goal is to verify that PDF content can be extracted, searched, and used for grounded answers.",
        ),
        (
            "Response Process Quick Reference",
            "The core phases are Initiate, Assess, Contain, Remediate, and Retrospect. "
            "Initiate opens the situation room and assigns roles. Assess determines whether the event is real and estimates severity. "
            "Contain limits spread and impact. Remediate returns the environment to normal operations. Retrospect reviews the incident and records follow-up actions.",
        ),
        (
            "Incident Severities",
            "High severity indicates broad or critical impact and usually requires immediate escalation. "
            "Medium severity indicates meaningful impact that may expand if left untreated. "
            "Low severity indicates limited impact and a clear recovery path.",
        ),
        (
            "Situation Room Roles",
            "Situation Lead coordinates timing, escalation, and major decisions. "
            "Technical Lead verifies technical facts and drives diagnosis and remediation. "
            "Messenger communicates updates. Scribe records the timeline, actions, and decisions.",
        ),
        (
            "Recoverability Levels",
            "Regular means recovery time is predictable with existing resources. "
            "Normal means recovery time is not predictable with existing resources. "
            "Supplemented means additional resources are needed but timing is still predictable. "
            "Extended means additional resources are required and timing remains uncertain. "
            "Not Recoverable means data has been lost or compromised and normal restoration is not possible.",
        ),
    ]
    for title, content in sections:
        story.append(Paragraph(title, styles["Heading2"]))
        story.append(Spacer(1, 6))
        story.append(Paragraph(content, styles["BodyText"]))
        story.append(Spacer(1, 12))
    doc = SimpleDocTemplate(str(PDF_PATH), pagesize=A4)
    doc.build(story)
    pdf_bytes = PDF_PATH.read_bytes()
    header_end = pdf_bytes.find(b"\n")
    if header_end >= 0 and b"\x00" not in pdf_bytes[:1024]:
        PDF_PATH.write_bytes(pdf_bytes[:header_end + 1] + b"%\x00\n" + pdf_bytes[header_end + 1:])


def build_xlsx() -> None:
    XLSX_PATH.parent.mkdir(parents=True, exist_ok=True)
    workbook = Workbook()
    sheet = workbook.active
    sheet.title = "incident_checklist"
    rows = [
        ("category", "item", "role", "severity", "recoverability", "notes"),
        ("startup", "Confirm service initialization window", "Technical Lead", "Medium", "Normal", "Check whether startup probe needs a larger window"),
        ("traffic", "Verify readiness before routing traffic", "Technical Lead", "Medium", "Normal", "Readiness should block traffic until dependencies are ready"),
        ("health", "Restart only when liveness indicates non-recoverable unhealthy state", "Technical Lead", "High", "Extended", "Avoid aggressive restarts for transient dependency failures"),
        ("containment", "Start containment when impact is broad or continued retries may expand damage", "Situation Lead", "High", "Extended", "Pause risky changes and isolate the affected service before deep diagnosis"),
        ("coordination", "Open situation room and assign roles", "Situation Lead", "High", "Extended", "Escalate quickly when impact is broad"),
        ("communication", "Send status updates to stakeholders", "Messenger", "High", "Supplemented", "Keep external updates aligned with internal facts"),
        ("recording", "Record timeline and key actions", "Scribe", "High", "Extended", "Maintain incident timeline for retrospect"),
        ("recovery", "Validate remediation and return to normal operations", "Technical Lead", "Medium", "Supplemented", "Continue checking for additional compromise"),
    ]
    for row in rows:
        sheet.append(row)
    workbook.save(XLSX_PATH)


if __name__ == "__main__":
    build_pdf()
    build_xlsx()
