import part01 from "./modules/management-runtime-part-01.js";
import part02 from "./modules/management-runtime-part-02.js";
import part03 from "./modules/management-runtime-part-03.js";
import part04 from "./modules/management-runtime-part-04.js";
import part05 from "./modules/management-runtime-part-05.js";

const runtimeParts = [
        part01,
        part02,
        part03,
        part04,
        part05
];

new Function(runtimeParts.join("\n"))();
