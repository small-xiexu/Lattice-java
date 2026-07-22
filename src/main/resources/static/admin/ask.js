import part01 from "./modules/ask-runtime-part-01.js?v=20260612-compact-markdown-table-1";
import part02 from "./modules/ask-runtime-part-02.js?v=20260612-compact-markdown-table-1";

const runtimeParts = [
        part01,
        part02
];

new Function(runtimeParts.join("\n"))();

installAnswerTableCitationPlacement();

function installAnswerTableCitationPlacement() {
    const syncFromEvent = function (event) {
        const marker = event && event.target && typeof event.target.closest === "function"
                ? event.target.closest(".answer-table-shell .citation-marker")
                : null;
        if (marker) {
            syncAnswerTableCitationPlacement(marker);
        }
    };
    document.addEventListener("mouseover", syncFromEvent, true);
    document.addEventListener("focusin", syncFromEvent, true);
    window.addEventListener("resize", function () {
        document.querySelectorAll(".answer-table-shell .citation-marker:hover, .answer-table-shell .citation-marker:focus-within")
                .forEach(syncAnswerTableCitationPlacement);
    });
    document.addEventListener("scroll", function () {
        document.querySelectorAll(".answer-table-shell .citation-marker:hover, .answer-table-shell .citation-marker:focus-within")
                .forEach(syncAnswerTableCitationPlacement);
    }, true);
}

function syncAnswerTableCitationPlacement(marker) {
    if (!marker || typeof marker.getBoundingClientRect !== "function") {
        return;
    }
    const popover = marker.querySelector(".citation-popover");
    if (!popover) {
        return;
    }
    marker.classList.add("citation-marker-in-table");
    const markerRect = marker.getBoundingClientRect();
    const viewportWidth = Math.max(document.documentElement.clientWidth || 0, window.innerWidth || 0);
    const viewportHeight = Math.max(document.documentElement.clientHeight || 0, window.innerHeight || 0);
    const popoverWidth = Math.min(460, Math.floor(viewportWidth * 0.86));
    const left = clamp(markerRect.left, 12, Math.max(12, viewportWidth - popoverWidth - 12));
    const spaceAbove = markerRect.top;
    const spaceBelow = viewportHeight - markerRect.bottom;
    const placeBelow = spaceBelow >= 220 || spaceBelow > spaceAbove;
    const top = placeBelow
            ? Math.min(markerRect.bottom + 8, viewportHeight - 24)
            : Math.max(12, markerRect.top - 8);
    marker.style.setProperty("--citation-popover-fixed-left", left + "px");
    marker.style.setProperty("--citation-popover-fixed-top", top + "px");
    marker.classList.toggle("citation-marker-fixed-below", placeBelow);
}

function clamp(value, min, max) {
    return Math.min(Math.max(Number(value || 0), min), max);
}
