// Injected into the forum app page (dumbcourse, /dumb) on API 26+ devices. Mirrors the page's own
// soft-key bar (#softkeys, dumbcourse ui/softkeys.ts) into the native Yapchik bar: every time the
// page relabels its bar - any screen, any layer - the live left / center / right labels go to
// SoftkeyBridge.onSoftkeys, along with the page theme and its "Soft-key bar" preference. Native
// presses come back as clicks on the page's own [data-sk] buttons (MainActivity.pressSoftkey), so
// the page's softLeft / centerKey / softRight do the work. ES5 only: keypad-phone WebViews are old.
(function () {
  var B = window.SoftkeyBridge;
  if (!B || window.__jtechSoftkeys) return;
  window.__jtechSoftkeys = true;

  var root = document.documentElement;

  // While the native bar is active, MainActivity sets data-native-softkeys on <html>: hide the
  // page's bar and give back the space it reserves (every with-softkeys offset uses --sk-h).
  var css = document.createElement("style");
  css.textContent =
    "html[data-native-softkeys] #softkeys{display:none!important}" +
    "html[data-native-softkeys]{--sk-h:0px!important}";
  (document.head || root).appendChild(css);

  var bar = null;
  var barObserver = null;
  var last = null;

  function label(which) {
    var el = bar && bar.querySelector('[data-sk="' + which + '"]');
    if (!el) return "";
    return (el.textContent || "").replace(/\s+/g, " ").replace(/^ | $/g, "");
  }

  // dumbcourse keeps its preferences in localStorage "dc:prefs" (storage.ts PREFIX + prefs.ts KEY).
  function pref() {
    try {
      var p = JSON.parse(window.localStorage.getItem("dc:prefs") || "{}");
      var v = p && p.softkeys;
      return v === "on" || v === "off" ? v : "auto";
    } catch (e) {
      return "auto";
    }
  }

  function report() {
    var l = label("left");
    var c = label("center");
    var r = label("right");
    var light = /(^|\s)light(\s|$)/.test(root.className);
    var p = pref();
    var key = [l, c, r, light, p].join("\u0000");
    if (key === last) return;
    last = key;
    B.onSoftkeys(l, c, r, light, p);
  }

  // The bar is re-rendered (innerHTML) on every relabel; the node itself only changes if the
  // shell is re-mounted, which replaces <body>'s children.
  function attach() {
    var b = document.getElementById("softkeys");
    if (b === bar) return;
    if (barObserver) barObserver.disconnect();
    barObserver = null;
    bar = b;
    if (bar) {
      barObserver = new MutationObserver(report);
      barObserver.observe(bar, { childList: true, subtree: true, characterData: true });
    }
  }

  function update() {
    attach();
    report();
  }

  // applyPrefs() rewrites <html>'s classes on every preference change (theme, soft-key bar),
  // after saving the preference, so this is where both are picked up.
  new MutationObserver(update).observe(root, { attributes: true, attributeFilter: ["class"] });
  if (document.body) new MutationObserver(update).observe(document.body, { childList: true });
  update();
})();
