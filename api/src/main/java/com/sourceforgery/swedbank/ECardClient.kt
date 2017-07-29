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
import java.io.UncheckedIOException
import java.io.UnsupportedEncodingException
import java.net.URLDecoder
import java.time.Clock
import java.time.Duration
import java.util.*

@SuppressWarnings("unused")
class ECardClient private constructor(private val loginPersonNumber: String) {
    companion object {
        private val POLL_TIMEOUT = Duration.ofSeconds(120)
        private val PATTERN = Regex("checkResponse.{0,50}dapPortalWindowId", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.MULTILINE))
        // This decodes "html" looking like [foo]bar[/foo]
        private val SWEDBANK_HTML_DECODER = Regex("\\[([^]]+)]([^\\[]*)\\[/([^]]+)]")
        private val WINDOW_OPEN_FINDER = Regex("window\\.open\\('([^']+)", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.MULTILINE))
        var debugLevel = HttpLoggingInterceptor.Level.NONE

        fun login(loginPersonNumber: String): List<Account> {
            val eCardClient = ECardClient(loginPersonNumber)
            return eCardClient.getAccounts(eCardClient.preClient(eCardClient.internalLogin()))
        }

        var logger: HttpLoggingInterceptor.Logger = object : HttpLoggingInterceptor.Logger {
            override fun log(message: String?) {
                System.err.println(message)
            }
        }
    }

    private val okhttpClient: OkHttpClient

    private val clock = Clock.systemUTC()
    private val loggingInterceptor: HttpLoggingInterceptor = HttpLoggingInterceptor({ ECardClient.logger.log(it) })
    private var msgNo = 0
    private var sessionId: String? = null
    private var webServletUrl: HttpUrl? = null
    private var realCards: List<RealCard> = emptyList()

    init {
        okhttpClient = OkHttpClient.Builder().cookieJar(CookieMonster())
                .addNetworkInterceptor(loggingInterceptor)
                .addNetworkInterceptor(
                        { chain ->
                            chain.proceed(chain.request()
                                    .newBuilder()
                                    .header("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/59.0.3071.104 Safari/537.36")
                                    .build())
                        }).build()
    }

    private fun getAccounts(preClientDoc: Document): List <Account> {
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

    private fun internalLogin(): Document {
        return Optional.of(Document("http://internet.swedbank.se"))
                .map({this.initEkort()})
                .map(this::portalInit)
                .map(this::loginStep1)
                .map(this::loginStep2)
                .map(this::poll)
                .map(this::loginStep3)
                .get()
    }


    private fun getError(doc: Document): String {
        return doc.select("#content .error .content").text()
    }

    private fun poll(loginStep2Doc: Document): Document {
        try {
            if (PATTERN.find(loginStep2Doc.html()) != null) {
                throw IOException("Document did not contain expected string. Error is " + getError(loginStep2Doc))
            }

            val req = Request.Builder().url("https://internetbank.swedbank.se/idp/portal/identifieringidp/busresponsecheck/main-dapPortalWindowId")
                    .get()
                    .build()
            val started = clock.instant()
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
                    return loginStep2Doc
                } else if (status == 0) {
                    throw RuntimeException("Timeout")
                } else if (status < 0) {
                    throw RuntimeException("Went to hell. Status " + status)
                }
                Thread.sleep(5000)
            } while (Duration.between(started, clock.instant()) < POLL_TIMEOUT)
            throw RuntimeException("Timeout")
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        } catch (e: InterruptedException) {
            throw RuntimeException(e)
        }
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
        val authForm = loginStep1Doc.select("form[name=auth").first() as FormElement
        val elements = LinkedHashMap<String, String>()
        for (keyVal in authForm.formData()) {
            elements.put(keyVal.key(), keyVal.value())
        }
        elements.put("auth:metod_2", "MOBILBID")
        elements.put("auth:kundnummer", loginPersonNumber)
        elements.remove("auth:avbryt_knapp")
        val formBody = FormBody.Builder()
        elements.forEach({ t, u -> formBody.add(t, u) })
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
        elements.forEach({ t, u -> formBody.add(t, u) })

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
        if (webServletUrl != null) {
            throw IllegalArgumentException("Already selected a different card")
        }
        val selectCardReq = Request.Builder().url(url)
                .header("Referer", "https://internetbank.swedbank.se/bviPrivat/privat?_new_flow_=false&ai_TDEApplName=TDEApplKort&ai_flow_id_=KONTROLLERA_EKORTSTATUS_PRECLIENT")
                .header("Accept-Encoding", "gzip, deflate, br")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8")
                .get()
                .build()

        val thinOpenerUrl = findWindowOpen(jsoup(selectCardReq))
        val map = queryToHashMap(thinOpenerUrl)
        map.put("Request", "GetActiveCards")
        webServletUrl = thinOpenerUrl.newBuilder().encodedPath("/servlet/WebServlet").query(null).build()
        val result = executeWebServlet(map)
        realCards = Collections.unmodifiableList(RealCard.Companion.from(result))

        //Map<String, String> result = executeWebServlet(Collections.singletonMap("Request", "GetCards"))

        listProfile(realCards[0])

        return ECardAPI()
    }

    // This seems to be a select card, not only list profiles. YMMV?
    private fun listProfile(realCard: RealCard) {
        val req1 = LinkedHashMap<String, Any>()
        req1.put("CardType", realCard.cardType)
        req1.put("VCardId", realCard.vCardId)

        req1.put("Request", "ListProfileIds")
        req1.put("ProfileType", "")
        executeWebServlet(req1)
    }

    private fun executeWebServlet(map: Map<String, Any>): Map <String, String> {
        loggingInterceptor.level = debugLevel
        val thinClientBody = FormBody.Builder()

        if (sessionId != null) {
            thinClientBody.add("SessionId", sessionId)
            thinClientBody.add("Version", "FLEXWEBCARD-SWEDBANK_2_4_44_0")
            thinClientBody.add("MsgNo", "" + msgNo++)
        }
        map.forEach({ k, v -> thinClientBody.add(k, v.toString()) })
        thinClientBody.add("Locale", "sv")
        thinClientBody.add("IssuerId", "1")

        val thinClientReq = Request.Builder().url(webServletUrl)
                .post(thinClientBody.build())
                .build()

        try {
            Thread.sleep(2000)
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
        val execute = okhttpClient.newCall(thinClientReq).execute()

        val body = execute.body() ?: throw IOException("No body!?")
        val responseBody = (HttpUrl.parse("https://example.com/") ?: throw RuntimeException("Newspeak? Jvm is broken"))
                .newBuilder()
                .encodedQuery(body.string())
                .build()
        val resultMap = queryToHashMap(responseBody)
        resultMap.remove("Eof")
        if ("Error" == resultMap["Action"]) {
            resultMap.remove("Action")
            throw IOException("" + resultMap.remove("ErrMsg") + " with result " + resultMap)
        }

        if (sessionId == null) {
            sessionId = resultMap["SessionId"]
        }
        return resultMap
    }

    private fun queryToHashMap(httpUrl: HttpUrl): LinkedHashMap<String, String> {
        val result = LinkedHashMap<String, String>()
        for (i in 0..httpUrl.querySize()) {
            result.put(httpUrl.queryParameterName(i), httpUrl.queryParameterValue(i))
        }
        return result
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
        try {
            loggingInterceptor.level = debugLevel
            val response = okhttpClient.newCall(request).execute()
            val string = response.body()?.string()
            if (!response.isSuccessful) {
                throw IOException("Failed with " + response.code() + " and " + string)
            }
            return Jsoup.parse(string, response.request().url().toString())
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }
    }

    @Suppress("unused")
    inner class Account(val url: HttpUrl, val name: String, val personNumber: String, val bankName: String) {
        fun selectIssuer(): ECardAPI {
            return this@ECardClient.selectIssuer(url)
        }
    }

    @Suppress("unused")
    inner class ECardAPI {

        fun getPastTransactions(realCard: RealCard, start: Int): List<PastTransaction> {
            val req2 = LinkedHashMap<String, Any>()
            req2.put("CardType", realCard.cardType)
            req2.put("VCardId", realCard.vCardId)

            req2.put("Start", start)
            req2.put("Request", "GetPastTransactions")
            req2.put("Next", 100)

            return PastTransaction.Companion.from(executeWebServlet(req2))
        }

        fun createCard(realCard: RealCard, transactionLimit: Int, cumulativeLimit: Int, validForMonths: Int): CPN {
            val req1 = LinkedHashMap<String, Any>()
            req1.put("CardType", realCard.cardType)
            req1.put("VCardId", realCard.vCardId)
            req1.put("Request", "GetCPN")
            req1.put("TransLimit", transactionLimit)
            req1.put("CumulativeLimit", cumulativeLimit)
            req1.put("ValidFor", validForMonths)
            return CPN(executeWebServlet(req1))
        }

        fun getActiveECards(realCard: RealCard, start: Int): List<ActiveECard> {
            val req1 = LinkedHashMap<String, Any>()
            req1.put("CardType", realCard.cardType)
            req1.put("VCardId", realCard.vCardId)
            req1.put("Request", "GetActiveAccounts")
            req1.put("Start", start)
            req1.put("Next", 100)
            return ActiveECard.Companion.from(executeWebServlet(req1))
        }

        fun closeCard(realCard: RealCard, creditCardNumber: String) {
            val req1 = LinkedHashMap<String, Any>()
            req1.put("CardType", realCard.cardType)
            req1.put("VCardId", realCard.vCardId)
            req1.put("Request", "CancelCPN")
            req1.put("CPNPAN", creditCardNumber)
            executeWebServlet(req1)
        }

        fun getCards(): List<RealCard> {
            return realCards
        }
    }
}
