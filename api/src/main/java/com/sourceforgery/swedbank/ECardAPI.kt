package com.sourceforgery.swedbank

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.io.InputStreamReader
import java.util.Collections

abstract class ECardAPI {
    abstract fun serializeState(): String


    fun getPastTransactions(start: Int): List<PastTransaction> {
        val req2 = LinkedHashMap<String, Any>()
        req2.put("Start", start)
        req2.put("Request", "GetPastTransactions")
        req2.put("Next", 100)

        return PastTransaction.Companion.from(executeWebServlet(req2))
    }

    fun createCard(transactionLimit: Int, cumulativeLimit: Int, validForMonths: Int): CPN {
        val req1 = LinkedHashMap<String, Any>()
        req1.put("TransLimit", transactionLimit)
        req1.put("CumulativeLimit", cumulativeLimit)
        req1.put("ValidFor", validForMonths)
        req1.put("Request", "GetCPN")
        return CPN(executeWebServlet(req1))
    }


    fun getActiveECards(start: Int): List<ActiveECard> {
        val req1 = LinkedHashMap<String, Any>()
        req1.put("Request", "GetActiveAccounts")
        req1.put("Start", start)
        req1.put("Next", 100)
        return ActiveECard.Companion.from(executeWebServlet(req1))
    }

    fun closeCard(creditCardNumber: String) {
        val req1 = LinkedHashMap<String, Any>()
        req1.put("Request", "CancelCPN")
        req1.put("CPNPAN", creditCardNumber)
        executeWebServlet(req1)
    }


    internal abstract fun executeWebServlet(request: Map<String, Any>): Map<String, String>


    companion object {
        internal val NONSENSE_BUT_VALID_URL = HttpUrl.parse("https://example.com/") ?: throw RuntimeException("Newspeak? Jvm is broken")

        internal fun queryToHashMap(httpUrl: HttpUrl): MutableMap<String, String> {
            val result = LinkedHashMap<String, String>()
            for (i in 0..httpUrl.querySize() - 1) {
                result.put(httpUrl.queryParameterName(i), httpUrl.queryParameterValue(i))
            }
            return result
        }

        internal fun queryToHashMap(encodedQuery: String): MutableMap<String, String> {
            return queryToHashMap(
                    NONSENSE_BUT_VALID_URL.newBuilder()
                            .encodedQuery(encodedQuery)
                            .build()
            )
        }

        fun afterLogin(okhttpClient: OkHttpClient,
                       cookieMonster: CookieMonster,
                       loggingInterceptor: HttpLoggingInterceptor,
                       thinOpenerUrl: HttpUrl): ECardAPIImpl {
            val map = queryToHashMap(thinOpenerUrl)
            map.put("Request", "GetActiveCards")
            val webServletUrl = thinOpenerUrl.newBuilder().encodedPath("/servlet/WebServlet").query(null).build()
            val api = ECardAPIImpl(okhttpClient, cookieMonster, loggingInterceptor, webServletUrl)
            val result = api.executeWebServlet(map)
            val realCards = Collections.unmodifiableList(RealCard.from(result))
            api.realCard = realCards[0]
            api.listProfile()
            return api
        }


        fun unpack(serializedState: String): ECardAPIImpl {
            val loggingInterceptor: HttpLoggingInterceptor = HttpLoggingInterceptor({ logger.log(it) })
            val cookieMonster = CookieMonster()
            val deserializedState = Gson().fromJson(serializedState, SerializedState::class.java)
            cookieMonster.deserializeAllCookies(deserializedState.cookies)
            val okhttpClient = OkHttpClient.Builder().cookieJar(cookieMonster)
                    .addNetworkInterceptor(loggingInterceptor)
                    .addNetworkInterceptor(
                            { chain ->
                                chain.proceed(chain.request()
                                        .newBuilder()
                                        .header("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/59.0.3071.104 Safari/537.36")
                                        .build())
                            }).build()
            return ECardAPIImpl(
                    okhttpClient,
                    cookieMonster,
                    loggingInterceptor,
                    HttpUrl.parse(deserializedState.webServletUrl)!!,
                    deserializedState.realCard,
                    deserializedState.sessionId
            )
        }
    }

}

internal class SerializedState(val cookies: Set<CookieMonster.CookieId>, webServletUrl: HttpUrl, val realCard: RealCard, val sessionId: String) {
    val webServletUrl = webServletUrl.toString()
}

class ECardAPIMock : ECardAPI() {
    override fun executeWebServlet(request: Map<String, Any>): Map<String, String> {
        ECardAPIMock::class.java.getResourceAsStream("/replies.json").use {
            val fromJson = Gson().fromJson<Map<String, String>>(InputStreamReader(it, "UTF-8"), object : TypeToken<Map<String, String>>() {}.type)
            return ECardAPI.queryToHashMap(fromJson[request["Request"] as String] as String)
        }
    }

    override fun serializeState(): String {
        return ""
    }

}

class ECardAPIImpl internal constructor(private val okhttpClient: OkHttpClient,
                                        private val cookieMonster: CookieMonster,
                                        private val loggingInterceptor: HttpLoggingInterceptor,
                                        val webServletUrl: HttpUrl,
                                        var realCard: RealCard? = null,
                                        var sessionId: String? = null) : ECardAPI() {
    private var msgNo = 0

    @Synchronized
    override fun executeWebServlet(request: Map<String, Any>): Map <String, String> {
        loggingInterceptor.level = debugLevel
        val thinClientBody = FormBody.Builder()

        if (sessionId != null) {
            thinClientBody.add("SessionId", sessionId)
            thinClientBody.add("Version", "FLEXWEBCARD-SWEDBANK_2_4_44_0")
            thinClientBody.add("MsgNo", "" + msgNo++)
            if (realCard != null) {
                thinClientBody.add("CardType", realCard!!.cardType.toString())
                thinClientBody.add("VCardId", realCard!!.vCardId.toString())
            }
        }

        for ((key, value) in request) {
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
        val responseBody = ECardAPI.NONSENSE_BUT_VALID_URL
                .newBuilder()
                .encodedQuery(body.string())
                .build()
        val resultMap = ECardAPI.queryToHashMap(responseBody)
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

    // This seems to be a select card, not only list profiles. YMMV?
    internal fun listProfile() {
        val req1 = LinkedHashMap<String, Any>()
        req1.put("Request", "ListProfileIds")
        req1.put("ProfileType", "")
        executeWebServlet(req1)
    }

    override fun serializeState(): String {
        val localRealCard = realCard ?: throw IllegalStateException("No state to serialize")
        val localSessionId = sessionId ?: throw IllegalStateException("No state to serialize")
        return Gson().toJson(SerializedState(cookieMonster.cookieJar, webServletUrl, localRealCard, localSessionId))
    }


}
