package com.rb22.antivirus

/** Small starter list. It is not a complete threat-intelligence feed. */
internal object DnsBlocklist {
    private val blocked = setOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "adservice.google.com", "adnxs.com", "adsrvr.org", "adform.net",
        "adroll.com", "adsafeprotected.com", "advertising.com", "amazon-adsystem.com",
        "criteo.com", "criteo.net", "outbrain.com", "taboola.com",
        "scorecardresearch.com", "quantserve.com", "moatads.com", "rubiconproject.com",
        "pubmatic.com", "openx.net", "casalemedia.com", "lijit.com",
        "branch.io", "appsflyer.com", "adjust.com", "kochava.com",
        "unityads.unity3d.com", "ads.unity.com", "applovin.com", "ironsrc.com",
        "vungle.com", "chartboost.com", "inmobi.com", "mopub.com",
        "tapjoy.com", "adcolony.com", "fyber.com", "startapp.com",
        "admob.com", "amazon-adsystem.com", "ad.gt", "ads-twitter.com",
        "analytics.google.com", "pixel.facebook.com", "connect.facebook.net",
        "hotjar.com", "mixpanel.com", "segment.io", "segment.com",
        "amplitude.com", "newrelic.com", "scorecardresearch.com"
    ).filter { it.isNotBlank() && ' ' !in it }.toSet()

    fun isBlocked(host: String): Boolean {
        val normalized = host.trim().trimEnd('.').lowercase()
        return normalized.isNotEmpty() && blocked.any { normalized == it || normalized.endsWith(".$it") }
    }
}
