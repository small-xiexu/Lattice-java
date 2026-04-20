(function (window) {
    function findOwnedElements(root, selector) {
        return Array.from(root.querySelectorAll(selector)).filter(function (element) {
            return element.closest("[data-tab-group]") === root;
        });
    }

    function activateGroup(root, tabName) {
        const triggers = findOwnedElements(root, "[data-tab-trigger]");
        const panels = findOwnedElements(root, "[data-tab-panel]");
        if (triggers.length === 0 || panels.length === 0) {
            return;
        }
        const nextTab = tabName || triggers[0].dataset.tabTrigger;
        triggers.forEach(function (trigger) {
            const active = trigger.dataset.tabTrigger === nextTab;
            trigger.classList.toggle("active", active);
            trigger.setAttribute("aria-selected", active ? "true" : "false");
        });
        panels.forEach(function (panel) {
            const active = panel.dataset.tabPanel === nextTab;
            panel.classList.toggle("active", active);
            panel.hidden = !active;
        });
    }

    function initTabGroup(root) {
        const triggers = findOwnedElements(root, "[data-tab-trigger]");
        if (triggers.length === 0) {
            return;
        }
        triggers.forEach(function (trigger) {
            trigger.addEventListener("click", function () {
                activateGroup(root, trigger.dataset.tabTrigger);
            });
        });
        findOwnedElements(root, "[data-tab-open]").forEach(function (trigger) {
            trigger.addEventListener("click", function () {
                activateGroup(root, trigger.dataset.tabOpen);
            });
        });
        const initialTrigger = triggers.find(function (trigger) {
            return trigger.classList.contains("active");
        }) || triggers[0];
        activateGroup(root, initialTrigger.dataset.tabTrigger);
    }

    document.addEventListener("DOMContentLoaded", function () {
        document.querySelectorAll("[data-tab-group]").forEach(initTabGroup);
    });

    window.AdminTabs = {
        activate: function (groupName, tabName) {
            const root = Array.from(document.querySelectorAll("[data-tab-group]")).find(function (entry) {
                return entry.dataset.tabGroup === groupName;
            });
            if (!root) {
                return;
            }
            activateGroup(root, tabName);
        }
    };
})(window);
