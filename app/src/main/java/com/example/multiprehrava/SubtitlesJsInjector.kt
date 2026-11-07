package com.example.multiprehrava

import android.webkit.WebView

object SubtitlesJsInjector {

    const val JS_FORCE_CZECH_AI_WATCHER = """
        (function() {
            if (window.__ytSubtitlePersistentWatcher) return;
            window.__ytSubtitlePersistentWatcher = true;

            function forceCzechAiSubtitles() {
                var ccBtn = document.querySelector('.ytp-subtitles-button');
                if (ccBtn && ccBtn.getAttribute('aria-pressed') !== 'true') {
                    ccBtn.click();
                }

                setTimeout(function() {
                    var settingsBtn = document.querySelector('.ytp-settings-button');
                    if (settingsBtn) {
                        settingsBtn.click();
                        setTimeout(function() {
                            var menuItems = document.querySelectorAll('.ytp-menuitem');
                            for (var i = 0; i < menuItems.length; i++) {
                                if (menuItems[i].textContent.includes('Titulky') || menuItems[i].textContent.includes('Subtitles')) {
                                    menuItems[i].click();
                                    setTimeout(function() {
                                        var subItems = document.querySelectorAll('.ytp-menuitem');
                                        for (var j = 0; j < subItems.length; j++) {
                                            if (subItems[j].textContent.includes('Automatický překlad') || subItems[j].textContent.includes('Auto-translate') || subItems[j].textContent.includes('Čeština')) {
                                                subItems[j].click();
                                                break;
                                            }
                                        }
                                    }, 200);
                                    break;
                                }
                            }
                        }, 200);
                    }
                }, 200);
            }

            var observer = new MutationObserver(function() {
                var videoElement = document.querySelector('video');
                if (videoElement && !videoElement.paused) {
                    forceCzechAiSubtitles();
                }
            });

            observer.observe(document.body, { childList: true, subtree: true });

            forceCzechAiSubtitles();
        })();
    """

    fun inject(webView: WebView?) {
        webView?.evaluateJavascript(JS_FORCE_CZECH_AI_WATCHER, null)
    }
}
