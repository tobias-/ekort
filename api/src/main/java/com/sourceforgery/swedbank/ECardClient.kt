package com.sourceforgery.swedbank

import okhttp3.FormBody

import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.FormElement
import java.io.IOException
import java.util.Collections

var debugLevel = HttpLoggingInterceptor.Level.NONE
var logger: HttpLoggingInterceptor.Logger = HttpLoggingInterceptor.Logger { message -> System.err.println(message) }

enum class Status {
    NOT_STARTED,
    INIT_EKORT_COMPLETE,
    STARTED,
    PORTAL_INIT_COMPLETE,
    LOGIN_1_COMPLETE,
    LOGIN_2_COMPLETE,
    ERROR,
    STARTED_POLLING,
    POLL_COMPLETE,
    PRE_CLIENT_COMPLETE,
    LOGIN_3_COMPLETE,
    SELECT_ISSUER_STARTED,
    SELECT_ISSUER_COMPLETE
}


@SuppressWarnings("unused")
class ECardClient(private val loginPersonNumber: String) {
    companion object {
        private val POLL_TIMEOUT = 120 * 1000
        // This decodes "html" looking like [foo]bar[/foo]
        private val SWEDBANK_HTML_DECODER = Regex("\\[([^]]+)]([^\\[]*)\\[/([^]]+)]")
        private val WINDOW_OPEN_FINDER = Regex("window\\.open\\('([^']+)", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.MULTILINE))

    }

    private val okhttpClient: OkHttpClient
    private val loggingInterceptor: HttpLoggingInterceptor = HttpLoggingInterceptor({ logger.log(it) })
    private val cookieMonster = CookieMonster()
    private var loginResult: Document? = null
    private var status: Status = Status.NOT_STARTED
        private set

    init {
        okhttpClient = OkHttpClient.Builder().cookieJar(cookieMonster)
                .addNetworkInterceptor(loggingInterceptor)
                .addNetworkInterceptor(
                        { chain ->
                            chain.proceed(chain.request()
                                    .newBuilder()
                                    .header("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/59.0.3071.104 Safari/537.36")
                                    .build())
                        }).build()
    }

    fun getAccounts(preClientDoc: Document): List <Account> {
        val accounts = ArrayList<Account>()
        for (element in preClientDoc.select(".sektion-innehall td a")) {
            val url = HttpUrl.parse(element.attr("abs:href")) ?: throw IOException("Couldn't find any link here")
            val queryParameter = url.queryParameter("ai_flow_id_")
            if (queryParameter != null && queryParameter == "KONTROLLERA_EKORTSTATUS_PRECLIENT") {
                val name = element.text()
                val personNumber = element.parent().nextElementSibling().text()
                var innehall = element.parent()
                while (!innehall.parent().classNames().contains("sidspalt2a")) {
                    innehall = innehall.parent()
                }
                val bankName = innehall.previousElementSibling().select(".sektion-rubrik").text()
                accounts.add(Account(url, name, personNumber, bankName))
            }
        }
        return accounts
    }


    @Suppress("unused")
    fun loginWithoutPoll() {
        try {
            if (status != Status.NOT_STARTED) {
                throw IllegalStateException("${this.javaClass.simpleName} cannot be restarted. Status was already ${status}")
            }
            status = Status.STARTED
            val initEkort = this.initEkort()
            status = Status.INIT_EKORT_COMPLETE
            val portalInit = this.portalInit(initEkort)
            status = Status.PORTAL_INIT_COMPLETE
            val loginStep1 = this.loginStep1(portalInit)
            status = Status.LOGIN_1_COMPLETE
            val loginStep2 = this.loginStep2(loginStep1)
            status = Status.LOGIN_2_COMPLETE
            val error = getError(loginStep2)
            if (error != "") {
                throw IOException(error)
            }
            this.loginResult = loginStep2
        } catch (e: Exception) {
            status = Status.ERROR
            throw e
        }
    }

    @Suppress("unused")
    fun pollAndGetAccounts(): List<Account> {
        if (status != Status.LOGIN_2_COMPLETE) {
            throw IllegalStateException("Expected ${Status.LOGIN_2_COMPLETE} but was ${status}")
        }
        try {
            status = Status.STARTED_POLLING
            poll()
            status = Status.POLL_COMPLETE
            val loginStep2Doc = this.loginResult ?: throw IllegalStateException("Must be run after loginWithoutPoll")
            val loginStep3 = loginStep3(loginStep2Doc)
            status = Status.LOGIN_3_COMPLETE
            val preClient = preClient(loginStep3)
            status = Status.PRE_CLIENT_COMPLETE
            return getAccounts(preClient)
        } catch (e: Exception) {
            status = Status.ERROR
            throw e
        }
    }


    private fun getError(doc: Document): String {
        return doc.select("#content .error .content").text()
    }

    private fun poll() {
        val req = Request.Builder().url("https://internetbank.swedbank.se/idp/portal/identifieringidp/busresponsecheck/main-dapPortalWindowId")
                .get()
                .build()
        val started = System.currentTimeMillis()
        do {
            val doc = jsoup(req)
            val swedbankHtml = doc.select("div").text()
            val data = LinkedHashMap<String, String>()
            for (matcher in SWEDBANK_HTML_DECODER.findAll(swedbankHtml)) {
                if (matcher.groupValues[1] != matcher.groupValues[3]) {
                    throw IOException("Weird swedbankHtml: " + swedbankHtml)
                }
                data.put(matcher.groupValues[1], matcher.groupValues[2])
            }
            val status = Integer.parseInt(data["responsechecker.status"])
            if (status == 1) {
                // Logged in. Done
                return
            } else if (status == 0) {
                throw RuntimeException("Timeout")
            } else if (status < 0) {
                throw RuntimeException("Went to hell. Status " + status)
            }
            Thread.sleep(5000)
        } while (System.currentTimeMillis() - started < POLL_TIMEOUT)
        throw RuntimeException("Timeout")
    }

    @SuppressWarnings("unused")
    private fun initEkort(): Document {
        val req = Request.Builder().url("https://internetbank.swedbank.se/bviPrivat/privat?ai_TDEApplName=TDEApplKort&ai_flow_id_=KONTROLLERA_EKORTSTATUS_PRECLIENT")
                .get()
                .build()
        return jsoup(req)
    }

    private fun portalInit(initEkortDoc: Document): Document {
        val formBody = FormBody.Builder()
        val form1 = initEkortDoc.select("form[name=form1]").first() as FormElement
        for (keyVal in form1.formData()) {
            formBody.add(keyVal.key(), keyVal.value())
        }
        val req = Request.Builder().url(form1.attr("abs:action"))
                .post(formBody.build())
                .build()
        return jsoup(req)
    }

    private fun loginStep1(initPortalDoc: Document): Document {
        val formBody = FormBody.Builder()
        val form1 = initPortalDoc.select("form[name=form1]").first() as FormElement
        form1.getElementById("form1:customer_plugin_java").`val`("")
        form1.getElementById("form1:customer_plugin_flash").`val`("")
        form1.getElementById("form1:customer_plugin_adobe_reader").`val`("")
        for (keyVal in form1.formData()) {
            formBody.add(keyVal.key(), keyVal.value())
        }
        val req = Request.Builder().url(form1.attr("abs:action"))
                .post(formBody.build())
                .build()
        return jsoup(req)
    }

    private fun loginStep2(loginStep1Doc: Document): Document {
        val authForm = loginStep1Doc.select("form[name=auth]").first() as FormElement
        val elements = LinkedHashMap<String, String>()
        for (keyVal in authForm.formData()) {
            elements.put(keyVal.key(), keyVal.value())
        }
        elements.put("auth:metod_2", "MOBILBID")
        elements.put("auth:kundnummer", loginPersonNumber)
        elements.remove("auth:avbryt_knapp")
        val formBody = FormBody.Builder()
        for ((key, value) in elements) {
            formBody.add(key, value)
        }
        val req = Request.Builder().url(authForm.attr("abs:action"))
                .post(formBody.build())
                .build()
        return jsoup(req)

    }

    private fun loginStep3(loginStep2Doc: Document): Document {
        val form = loginStep2Doc.select("form[name=form]").first() as FormElement
        val elements = LinkedHashMap<String, String>()
        for (keyVal in form.formData()) {
            elements.put(keyVal.key(), keyVal.value())
        }
        elements.put("form:returnCode", "1")
        elements.remove("form:avbryt_knapp")
        elements.remove("form:timeout_knapp")
        val formBody = FormBody.Builder()
        for ((key, value) in elements) {
            formBody.add(key, value)
        }

        val req = Request.Builder().url(form.attr("abs:action"))
                .post(formBody.build())
                .build()
        return jsoup(req)
    }

    private fun preClient(loginStep3Doc: Document): Document {
        val redirectForm = loginStep3Doc.select("form[name=redirectForm]").first() as FormElement
        val formBody = FormBody.Builder()
        for (keyVal in redirectForm.formData()) {
            formBody.add(keyVal.key(), keyVal.value())
        }

        val req = Request.Builder().url(redirectForm.attr("abs:action"))
                .post(formBody.build())
                .build()
        return jsoup(req)
    }

    private fun selectIssuer(url: HttpUrl): ECardAPI {
        if (status != Status.PRE_CLIENT_COMPLETE) {
            throw IllegalStateException("Cannot select issuer when status is ${status}, only when it's ${Status.PRE_CLIENT_COMPLETE}")
        }
        try {
            status = Status.SELECT_ISSUER_STARTED
            val selectCardReq = Request.Builder().url(url)
                    .header("Referer", "https://internetbank.swedbank.se/bviPrivat/privat?_new_flow_=false&ai_TDEApplName=TDEApplKort&ai_flow_id_=KONTROLLERA_EKORTSTATUS_PRECLIENT")
                    .header("Accept-Encoding", "gzip, deflate, br")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8")
                    .get()
                    .build()

            val thinOpenerUrl = findWindowOpen(jsoup(selectCardReq))
            status = Status.SELECT_ISSUER_COMPLETE
            return ECardAPI.afterLogin(okhttpClient, cookieMonster, loggingInterceptor, thinOpenerUrl)
        } catch (e: Exception) {
            status = Status.ERROR
            throw e
        }
    }


    private fun findWindowOpen(doc: Document): HttpUrl {
        for (script in doc.getElementsByTag("script")) {
            val matcher = WINDOW_OPEN_FINDER.find(script.html())
            if (matcher != null) {
                return HttpUrl.parse(matcher.groupValues[1]) ?: throw RuntimeException("Invalid url ${matcher.groupValues[1]}")
            }
        }
        throw RuntimeException("Didn't find script tag with window.open")
    }

    private fun jsoup(request: Request): Document {
        loggingInterceptor.level = debugLevel
        val response = okhttpClient.newCall(request).execute()
        val string = response.body()?.string()
        if (!response.isSuccessful) {
            throw IOException("Failed with " + response.code() + " and " + string)
        }
        return Jsoup.parse(string, response.request().url().toString())
    }

    @Suppress("unused")
    inner class Account(val url: HttpUrl, val name: String, val personNumber: String, val bankName: String) {
        fun selectIssuer(): ECardAPI {
            return this@ECardClient.selectIssuer(url)
        }
    }
}
