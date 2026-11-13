package com.cemail.app.data.net

/**
 * Tries IPv4 addresses before IPv6 ones. On some networks the phone has an IPv6 address and a route that does not
 * really work (the log showed `Failed to connect to gmail.googleapis.com/[2001:4860:…]:443` again and again): the
 * connection to the IPv6 address fails or waits until the timeout, while the IPv4 one would have worked at once.
 */
object Ipv4First : okhttp3.Dns {
    override fun lookup(hostname: String): List<java.net.InetAddress> =
        okhttp3.Dns.SYSTEM.lookup(hostname).sortedBy { it is java.net.Inet6Address }
}
