import part01 from "./modules/ask-runtime-part-01.js?v=20260610-ask-evidence-disclosure-1";
import part02 from "./modules/ask-runtime-part-02.js?v=20260610-ask-evidence-disclosure-1";

const runtimeParts = [
        part01,
        part02
];

new Function(runtimeParts.join("\n"))();
