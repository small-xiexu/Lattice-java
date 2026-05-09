import part01 from "./modules/ask-runtime-part-01.js";
import part02 from "./modules/ask-runtime-part-02.js";

const runtimeParts = [
        part01,
        part02
];

new Function(runtimeParts.join("\n"))();
