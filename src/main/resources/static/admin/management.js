import part01 from "./modules/management-runtime-part-01.js?v=20260610-admin-design-system-9";
import part02 from "./modules/management-runtime-part-02.js?v=20260610-admin-design-system-9";
import part03 from "./modules/management-runtime-part-03.js?v=20260610-admin-design-system-9";
import part04 from "./modules/management-runtime-part-04.js?v=20260610-admin-design-system-9";
import part05 from "./modules/management-runtime-part-05.js?v=20260610-admin-design-system-9";
import partHistory from "./modules/management-history-part.js?v=20260610-admin-design-system-9";

const runtimeParts = [
        part01,
        part02,
        part03,
        part04,
        part05,
        partHistory
];

new Function(runtimeParts.join("\n"))();
