(function (window) {
    function findOwnedElements(root, selector) {
        return Array.from(root.querySelectorAll(selector)).filter(function (element) {
            return element.closest("[data-tab-group]") === root;
        });
    }

    function ensureTabSemantics(root, triggers, panels) {
        triggers.forEach(function (trigger, index) {
            const panel = panels.find(function (entry) {
                return entry.dataset.tabPanel === trigger.dataset.tabTrigger;
            });
            const triggerId = trigger.id || root.dataset.tabGroup + "-tab-" + index;
            const panelId = panel && panel.id ? panel.id : root.dataset.tabGroup + "-panel-" + index;
            trigger.id = triggerId;
            trigger.setAttribute("role", trigger.getAttribute("role") || "tab");
            trigger.setAttribute("tabindex", trigger.classList.contains("active") ? "0" : "-1");
            if (panel) {
                panel.id = panelId;
                trigger.setAttribute("aria-controls", panelId);
                panel.setAttribute("role", panel.getAttribute("role") || "tabpanel");
                panel.setAttribute("aria-labelledby", triggerId);
            }
        });
    }

    function syncUrlState(root, tabName) {
        const queryKey = root.dataset.tabQueryKey;
        if (!queryKey || typeof window === "undefined" || !window.location || typeof URLSearchParams !== "function") {
            return;
        }
        const params = new URLSearchParams(window.location.search || "");
        if (tabName) {
            params.set(queryKey, tabName);
        }
        else {
            params.delete(queryKey);
        }
        const nextQuery = params.toString();
        const nextUrl = window.location.pathname + (nextQuery ? "?" + nextQuery : "") + (window.location.hash || "");
        if (window.history && typeof window.history.replaceState === "function") {
            window.history.replaceState(null, "", nextUrl);
        }
    }

    function activateGroup(root, tabName, options) {
        const triggers = findOwnedElements(root, "[data-tab-trigger]");
        const panels = findOwnedElements(root, "[data-tab-panel]");
        if (triggers.length === 0 || panels.length === 0) {
            return;
        }
        ensureTabSemantics(root, triggers, panels);
        const nextTab = tabName || triggers[0].dataset.tabTrigger;
        triggers.forEach(function (trigger) {
            const active = trigger.dataset.tabTrigger === nextTab;
            trigger.classList.toggle("active", active);
            trigger.setAttribute("aria-selected", active ? "true" : "false");
            trigger.setAttribute("tabindex", active ? "0" : "-1");
        });
        panels.forEach(function (panel) {
            const active = panel.dataset.tabPanel === nextTab;
            panel.classList.toggle("active", active);
            panel.hidden = !active;
            panel.setAttribute("aria-hidden", active ? "false" : "true");
        });
        if (!options || options.syncUrl !== false) {
            syncUrlState(root, nextTab);
        }
    }

    function resolveInitialTab(root, triggers) {
        const queryKey = root.dataset.tabQueryKey;
        if (!queryKey || typeof window === "undefined" || !window.location || typeof URLSearchParams !== "function") {
            return null;
        }
        const params = new URLSearchParams(window.location.search || "");
        const requestedTab = String(params.get(queryKey) || "").trim();
        if (!requestedTab) {
            return null;
        }
        return triggers.some(function (trigger) {
            return trigger.dataset.tabTrigger === requestedTab;
        }) ? requestedTab : null;
    }

    function focusTrigger(triggers, index) {
        const nextTrigger = triggers[index];
        if (nextTrigger && typeof nextTrigger.focus === "function") {
            nextTrigger.focus();
        }
    }

    function initTabGroup(root) {
        const triggers = findOwnedElements(root, "[data-tab-trigger]");
        const panels = findOwnedElements(root, "[data-tab-panel]");
        if (triggers.length === 0) {
            return;
        }
        ensureTabSemantics(root, triggers, panels);
        triggers.forEach(function (trigger) {
            trigger.addEventListener("click", function () {
                activateGroup(root, trigger.dataset.tabTrigger);
            });
            trigger.addEventListener("keydown", function (event) {
                const currentIndex = triggers.indexOf(trigger);
                if (currentIndex < 0) {
                    return;
                }
                if (event.key === "ArrowRight" || event.key === "ArrowDown") {
                    const nextIndex = (currentIndex + 1) % triggers.length;
                    activateGroup(root, triggers[nextIndex].dataset.tabTrigger);
                    focusTrigger(triggers, nextIndex);
                    event.preventDefault();
                }
                else if (event.key === "ArrowLeft" || event.key === "ArrowUp") {
                    const nextIndex = (currentIndex - 1 + triggers.length) % triggers.length;
                    activateGroup(root, triggers[nextIndex].dataset.tabTrigger);
                    focusTrigger(triggers, nextIndex);
                    event.preventDefault();
                }
                else if (event.key === "Home") {
                    activateGroup(root, triggers[0].dataset.tabTrigger);
                    focusTrigger(triggers, 0);
                    event.preventDefault();
                }
                else if (event.key === "End") {
                    const lastIndex = triggers.length - 1;
                    activateGroup(root, triggers[lastIndex].dataset.tabTrigger);
                    focusTrigger(triggers, lastIndex);
                    event.preventDefault();
                }
            });
        });
        findOwnedElements(root, "[data-tab-open]").forEach(function (trigger) {
            trigger.addEventListener("click", function () {
                activateGroup(root, trigger.dataset.tabOpen);
            });
        });
        const requestedTab = resolveInitialTab(root, triggers);
        const initialTrigger = triggers.find(function (trigger) {
            return trigger.classList.contains("active");
        }) || triggers[0];
        activateGroup(root, requestedTab || initialTrigger.dataset.tabTrigger, {syncUrl: false});
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
