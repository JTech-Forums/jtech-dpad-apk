// Injected into the forum app page (dumbcourse, /dumb). Mirrors the page's own
// soft-key bar (#softkeys, dumbcourse ui/softkeys.ts) into the native Yapchik bar: every time the
// page relabels its bar - any screen, any layer - the live left / center / right labels go to
// SoftkeyBridge.onSoftkeys, along with the page theme, its "Soft-key bar" preference and the bar's
// size (so the native bar is exactly as big as the page's, on every phone and Text size). Native
// presses come back as clicks on the page's own [data-sk] buttons (MainActivity.pressSoftkey), so
// the page's softLeft / centerKey / softRight do the work. ES5 only: keypad-phone WebViews are old.
(function () {
  var B = window.SoftkeyBridge;
  if (!B || window.__jtechSoftkeys) return;
  window.__jtechSoftkeys = true;

  var root = document.documentElement;

  // While the native bar is active, MainActivity sets data-native-softkeys on <html>: hide the
  // page's bar and give back the space it reserves (every with-softkeys offset uses --sk-h, all
  // of them inside <body>). Zeroed on <body>, not <html>, so <html> keeps the page's own --sk-h
  // and the bar's size can still be measured (see size()).
  var css = document.createElement("style");
  css.textContent =
    "html[data-native-softkeys] #softkeys{display:none!important}" +
    "html[data-native-softkeys] body{--sk-h:0px!important}";
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

  function cssPx(v) {
    var n = parseFloat(v);
    return n > 0 ? Math.round(n * 10) / 10 : 0;
  }

  // The page's bar height, label size and label padding in CSS px (one CSS px is one dp in a
  // WebView). The height is var(--sk-h) measured on a probe that is a child of <html>, so it
  // gets the page's value even while the native bar zeroes it on <body>; the probe never touches
  // <body>, whose children are observed below. Falls back to dumbcourse's rem values.
  var probe = null;
  function size() {
    var rem = cssPx(window.getComputedStyle(root).fontSize) || 15;
    var h = 0;
    try {
      if (!probe) {
        probe = document.createElement("div");
        probe.style.cssText =
          "position:absolute;left:0;top:0;width:1px;padding:0;border:0;visibility:hidden";
        probe.style.height = "var(--sk-h)";
      }
      root.appendChild(probe);
      h = cssPx(probe.getBoundingClientRect().height);
      root.removeChild(probe);
    } catch (e) {
      h = 0;
    }
    var sk = bar && bar.querySelector(".sk-left");
    var cs = sk ? window.getComputedStyle(sk) : null;
    return [
      h || cssPx(rem * 1.9),
      (cs && cssPx(cs.fontSize)) || cssPx(rem * 0.8),
      (cs && cssPx(cs.paddingLeft)) || cssPx(rem * 0.5)
    ];
  }

  function report() {
    var l = label("left");
    var c = label("center");
    var r = label("right");
    var light = /(^|\s)light(\s|$)/.test(root.className);
    var p = pref();
    var s = size();
    var key = [l, c, r, light, p, s.join(",")].join("\u0000");
    if (key === last) return;
    last = key;
    B.onSoftkeys(l, c, r, light, p, s[0], s[1], s[2]);
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

  // applyPrefs() rewrites <html>'s classes and its font size (Text size) on every preference
  // change, after saving the preference, so this is where all of them are picked up.
  new MutationObserver(update).observe(root, { attributes: true, attributeFilter: ["class", "style"] });
  if (document.body) new MutationObserver(update).observe(document.body, { childList: true });
  update();
})();
