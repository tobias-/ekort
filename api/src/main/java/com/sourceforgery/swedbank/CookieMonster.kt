package com.sourceforgery.swedbank

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

class CookieMonster : CookieJar {

    private val cookieJar = LinkedHashSet<CookieId>()


    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        for (cookie in cookies) {
            val o = CookieId(cookie)
            cookieJar.remove(o)
            cookieJar.add(o)
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        return cookieJar
                .filter { e -> e.cookie.matches(url) }
                .map { it.cookie }
                .toList()
    }

    private class CookieId constructor(internal val cookie: Cookie) {

        override fun equals(other: Any?): Boolean {
            if (other == null) {
                return false
            }
            if (other.javaClass != javaClass) {
                return false
            }
            val that = other as CookieId?
            return that!!.cookie.name() == this.cookie.name()
                    && that.cookie.domain() == this.cookie.domain()
                    && that.cookie.path() == this.cookie.path()
        }

        override fun hashCode(): Int {
            var hash = 17
            hash = 31 * hash + cookie.name().hashCode()
            hash = 31 * hash + cookie.domain().hashCode()
            hash = 31 * hash + cookie.path().hashCode()
            return hash
        }

        override fun toString(): String {
            return cookie.toString()
        }
    }
}
