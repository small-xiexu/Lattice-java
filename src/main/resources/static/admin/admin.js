import part01 from "./modules/admin-runtime-part-01.js";
import part02 from "./modules/admin-runtime-part-02.js";
import part03 from "./modules/admin-runtime-part-03.js";

const runtimeParts = [
        part01,
        part02,
        part03
];

new Function(runtimeParts.join("\n"))();
