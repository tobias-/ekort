package com.sourceforgery.swedbank;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;

public class CookieMonster implements CookieJar {

    private Set<CookieId> cookieJar = new LinkedHashSet<>();


    public void saveFromResponse(final HttpUrl url, final List<Cookie> cookies) {
        for (Cookie cookie : cookies) {
            CookieId o = new CookieId(cookie);
            cookieJar.remove(o);
            cookieJar.add(o);
        }
    }

    public List<Cookie> loadForRequest(final HttpUrl url) {
        return cookieJar.stream()
                        .filter(e -> e.cookie.matches(url))
                        .map(CookieId::getCookie)
                        .collect(Collectors.toList());
    }

    private static class CookieId {
        private final Cookie cookie;

        private CookieId(final Cookie cookie) {
            this.cookie = cookie;
        }

        @Override
        public boolean equals(Object other) {
            if (other == null) {
                return false;
            }
            if (other.getClass() != getClass()) {
                return false;
            }
            CookieId that = (CookieId) other;
            return that.cookie.name().equals(this.cookie.name())
                    && that.cookie.domain().equals(this.cookie.domain())
                    && that.cookie.path().equals(this.cookie.path());
        }

        @Override
        public int hashCode() {
            int hash = 17;
            hash = 31 * hash + cookie.name().hashCode();
            hash = 31 * hash + cookie.domain().hashCode();
            hash = 31 * hash + cookie.path().hashCode();
            return hash;
        }

        @Override
        public String toString() {
            return cookie.toString();
        }

        Cookie getCookie() {
            return cookie;
        }
    }
}
