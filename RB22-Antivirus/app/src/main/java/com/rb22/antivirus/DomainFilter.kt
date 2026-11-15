package com.rb22.antivirus

/** Starter denylist only; verified, signed list updates and allowlisting are future work. */
internal object DomainFilter {
    private val domains = setOf(
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "adservice.google.com",
        "adnxs.com",
        "ads.yahoo.com",
        "adcolony.com",
        "app-measurement.com",
        "unityads.unity3d.com",
        "applovin.com"
    )
    fun blocked(host: String): Boolean {
        val h=host.trim().trimEnd('.').lowercase()
        return h.isNotEmpty() && domains.any { h==it || h.endsWith(".$it") }
    }
}
