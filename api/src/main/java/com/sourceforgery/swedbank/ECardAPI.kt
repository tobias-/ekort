package com.sourceforgery.swedbank;

import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.util.Collections

class ECardAPI private constructor(private val okhttpClient: OkHttpClient,
                                   private val cookieMonster: CookieMonster,
                                   private val loggingInterceptor: HttpLoggingInterceptor,
                                   val webServletUrl: HttpUrl) {

    private var realCards: List<RealCard> = emptyList()
    private var sessionId: String? = null
    private var msgNo = 0

    companion object {
        private fun queryToHashMap(httpUrl: HttpUrl): LinkedHashMap<String, String> {
            val result = LinkedHashMap<String, String>()
            for (i in 0..httpUrl.querySize() - 1) {
                result.put(httpUrl.queryParameterName(i), httpUrl.queryParameterValue(i))
            }
            return result
        }

        fun afterLogin(okhttpClient: OkHttpClient,
                       cookieMonster: CookieMonster,
                       loggingInterceptor: HttpLoggingInterceptor,
                       thinOpenerUrl: HttpUrl): ECardAPI {
            val map = queryToHashMap(thinOpenerUrl)
            map.put("Request", "GetActiveCards")
            val webServletUrl = thinOpenerUrl.newBuilder().encodedPath("/servlet/WebServlet").query(null).build()
            val api = ECardAPI(okhttpClient, cookieMonster, loggingInterceptor, webServletUrl)
            val result = api.executeWebServlet(map)
            val realCards = Collections.unmodifiableList(RealCard.from(result))
            api.listProfile(realCards[0])
            return api
        }


        fun unpack(serializedCookies: String, webServletUrl: HttpUrl): ECardAPI {
            val loggingInterceptor: HttpLoggingInterceptor = HttpLoggingInterceptor({ logger.log(it) })
            val cookieMonster = CookieMonster()
            cookieMonster.deserializeAllCookies(serializedCookies)
            val okhttpClient = OkHttpClient.Builder().cookieJar(cookieMonster)
                    .addNetworkInterceptor(loggingInterceptor)
                    .addNetworkInterceptor(
                            { chain ->
                                chain.proceed(chain.request()
                                        .newBuilder()
                                        .header("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/59.0.3071.104 Safari/537.36")
                                        .build())
                            }).build()
            return ECardAPI(okhttpClient, cookieMonster, loggingInterceptor, webServletUrl)
        }
    }


    private fun executeWebServlet(map: Map<String, Any>): Map <String, String> {
        loggingInterceptor.level = debugLevel
        val thinClientBody = FormBody.Builder()

        if (sessionId != null) {
            thinClientBody.add("SessionId", sessionId)
            thinClientBody.add("Version", "FLEXWEBCARD-SWEDBANK_2_4_44_0")
            thinClientBody.add("MsgNo", "" + msgNo++)
        }
        for ((key, value) in map) {
            thinClientBody.add(key, value.toString())
        }

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

    fun serializeCookies(): String {
        return cookieMonster.serializeAllCookies()
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
