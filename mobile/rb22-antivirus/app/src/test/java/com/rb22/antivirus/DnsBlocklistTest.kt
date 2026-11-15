package com.rb22.antivirus

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsBlocklistTest {
    @Test fun blocksKnownAdDomainAndSubdomain() {
        assertTrue(DnsBlocklist.isBlocked("doubleclick.net"))
        assertTrue(DnsBlocklist.isBlocked("securepubads.g.doubleclick.net"))
    }

    @Test fun doesNotBlockUnrelatedOrSuffixLookalikeDomains() {
        assertFalse(DnsBlocklist.isBlocked("example.com"))
        assertFalse(DnsBlocklist.isBlocked("notdoubleclick.net"))
    }
}
