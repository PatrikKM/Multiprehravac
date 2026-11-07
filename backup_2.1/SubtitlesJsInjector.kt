package com.example.multiprehrava

import android.webkit.WebView

object SubtitlesJsInjector {

    const val JS_MONKEY_PATCH = """
        (function() {
            if (window.__ytSubtitlesPatched) return;
            window.__ytSubtitlesPatched = true;

            function rewriteUrl(urlStr) {
                if (!urlStr || typeof urlStr !== 'string') return urlStr;
                if (urlStr.includes('/api/timedtext') && !urlStr.includes('tlang=')) {
                    try {
                        if (!urlStr.includes('lang=cs')) {
                            var separator = urlStr.includes('?') ? '&' : '?';
                            var modifiedUrl = urlStr + separator + 'tlang=cs';
                            console.log('[SubtitlesInjector] Rewriting URL: ' + modifiedUrl);
                            return modifiedUrl;
                        }
                    } catch (e) {
                        console.error('[SubtitlesInjector] Patch error:', e);
                    }
                }
                return urlStr;
            }

            var originalFetch = window.fetch;
            window.fetch = function(resource, init) {
                if (typeof resource === 'string') {
                    resource = rewriteUrl(resource);
                } else if (resource && resource.url) {
                    var newUrl = rewriteUrl(resource.url);
                    if (newUrl !== resource.url) {
                        resource = new Request(newUrl, resource);
                    }
                }
                return originalFetch.call(this, resource, init);
            };

            var originalOpen = XMLHttpRequest.prototype.open;
            XMLHttpRequest.prototype.open = function(method, url) {
                var args = Array.prototype.slice.call(arguments);
                args[1] = rewriteUrl(url);
                return originalOpen.apply(this, args);
            };

            console.log('[SubtitlesInjector] Monkey-patch successfully registered.');
        })();
    """

    fun inject(webView: WebView?) {
        webView?.evaluateJavascript(JS_MONKEY_PATCH, null)
    }
}
