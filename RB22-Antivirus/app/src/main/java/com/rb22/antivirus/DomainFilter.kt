package com.rb22.antivirus

/** Expanded starter list; DNS filtering still cannot block same-host video ads. */
internal object DomainFilter {
    private val domains = setOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "adservice.google.com", "adnxs.com", "ads.yahoo.com", "adcolony.com",
        "app-measurement.com", "unityads.unity3d.com", "applovin.com",
        "ironsrc.com", "supersonicads.com", "vungle.com", "inmobi.com",
        "chartboost.com", "mopub.com", "startapp.com", "tapjoy.com",
        "fyber.com", "mintegral.com", "liftoff.io", "bigo.sg",
        "admob.com", "ads.google.com", "pagead2.googlesyndication.com",
        "securepubads.g.doubleclick.net", "adform.net", "criteo.com",
        "criteo.net", "outbrain.com", "taboola.com", "zedo.com",
        "rubiconproject.com", "openx.net", "pubmatic.com", "sharethrough.com",
        "smartadserver.com", " casalemedia.com", "33across.com",
        "advertising.com", "bidswitch.net", "contextweb.com", "serving-sys.com",
        "teads.tv", "undertone.com", "yieldmo.com", "smaato.net",
        "appsflyer.com", "adjust.com", "branch.io", "kochava.com",
        "flurry.com", "amplitude.com", "mixpanel.com", "segment.io",
        "scorecardresearch.com", "quantserve.com", "hotjar.com",
        "fullstory.com", "mouseflow.com", "clarity.ms", "newrelic.com",
        "bugsnag.com", "sentry.io", "analytics.google.com"
    )
    fun blocked(host: String): Boolean {
        val h = host.trim().trimEnd('.').lowercase()
        return h.isNotEmpty() && domains.any { h == it || h.endsWith(".$it") }
    }
}
