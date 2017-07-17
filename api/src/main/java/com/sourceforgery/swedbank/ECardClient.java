package com.sourceforgery.swedbank;

import static java.time.Duration.between;
import static java.util.Collections.unmodifiableList;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Connection.KeyVal;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.FormElement;

import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.OkHttpClient.Builder;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import okhttp3.logging.HttpLoggingInterceptor.Level;

@SuppressWarnings("unused")
public class ECardClient {
    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(120);
    private static final Pattern PATTERN = Pattern.compile("checkResponse.{0,50}dapPortalWindowId", Pattern.DOTALL | Pattern.MULTILINE);
    // This decodes "html" looking like [foo]bar[/foo]
    private static final Pattern SWEDBANK_HTML_DECODER = Pattern.compile("\\[([^]]+)\\]([^\\[]*)\\[\\/([^\\]]+)\\]");
    private static final Pattern WINDOW_OPEN_FINDER = Pattern.compile("window\\.open\\('([^']+)", Pattern.DOTALL | Pattern.MULTILINE);

    private static Level debugLevel = Level.NONE;

    private final OkHttpClient okhttpClient;
    private final String personNumber;

    private final Clock clock = Clock.systemUTC();
    private HttpLoggingInterceptor loggingInterceptor;
    private String sessionId;
    private HttpUrl webServletUrl;
    private int msgNo = 0;
    private List<RealCard> realCards;

    private ECardClient(final String loginPersonNumber) {
        this.personNumber = loginPersonNumber;
        loggingInterceptor = new HttpLoggingInterceptor(System.err::println);

        okhttpClient = new Builder().cookieJar(new CookieMonster())
                                    .addNetworkInterceptor(loggingInterceptor)
                                    .addNetworkInterceptor(chain -> chain.proceed(chain.request().newBuilder().header("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/59.0.3071.104 Safari/537.36").build()))
                                    .build();
    }

    public static List<Account> login(final String loginPersonNumber) {
        final ECardClient eCardClient = new ECardClient(loginPersonNumber);
        return eCardClient.getAccounts(eCardClient.preClient(eCardClient.internalLogin()));
    }


    private List<Account> getAccounts(final Document preClientDoc) {
        List<Account> accounts = new ArrayList<>();
        for (Element element : preClientDoc.select(".sektion-innehall td a")) {
            HttpUrl url = HttpUrl.parse(element.attr("abs:href"));
            String queryParameter = url.queryParameter("ai_flow_id_");
            if (queryParameter != null && queryParameter.equals("KONTROLLERA_EKORTSTATUS_PRECLIENT")) {
                String name = element.text();
                String personNumber = element.parent().nextElementSibling().text();
                Element innehall = element.parent();
                while (!innehall.parent().classNames().contains("sidspalt2a")) {
                    innehall = innehall.parent();
                }
                String bankName = innehall.previousElementSibling().select(".sektion-rubrik").text();
                accounts.add(new Account(url, name, personNumber, bankName));
            }
        }
        return accounts;
    }

    @SuppressWarnings("ConstantConditions")
    private Document internalLogin() {
        return Optional.of(new Document("http://internet.swedbank.se"))
                       .map(this::initEkort)
                       .map(this::portalInit)
                       .map(this::loginStep1)
                       .map(this::loginStep2)
                       .map(this::poll)
                       .map(this::loginStep3)
                       .get();
    }

    public static void setDebugLevel(Level level) {
        ECardClient.debugLevel = level;
    }

    private String getError(final Document doc) {
        return doc.select("#content .error .content").text();
    }

    private Document poll(final Document loginStep2Doc) {
        try {
            if (!PATTERN.matcher(loginStep2Doc.html()).find()) {
                throw new IOException("Document did not contain expected string. Error is " + getError(loginStep2Doc));
            }

            Request req = new Request.Builder().url("https://internetbank.swedbank.se/idp/portal/identifieringidp/busresponsecheck/main-dapPortalWindowId")
                                               .get()
                                               .build();
            Instant started = clock.instant();
            do {
                Document doc = jsoup(req);
                String swedbankHtml = doc.select("div").text();
                Matcher matcher = SWEDBANK_HTML_DECODER.matcher(swedbankHtml);
                Map<String, String> data = new LinkedHashMap<>();
                while (matcher.find()) {
                    if (!matcher.group(1).equals(matcher.group(3))) {
                        throw new IOException("Weird swedbankHtml: " + swedbankHtml);
                    }
                    data.put(matcher.group(1), matcher.group(2));
                }
                int status = Integer.parseInt(data.get("responsechecker.status"));
                if (status == 1) {
                    // Logged in. Done
                    return loginStep2Doc;
                } else if (status == 0) {
                    throw new RuntimeException("Timeout");
                } else if (status < 0) {
                    throw new RuntimeException("Went to hell. Status " + status);
                }
                Thread.sleep(5000);
            } while (between(started, clock.instant()).compareTo(POLL_TIMEOUT) < 0);
            throw new RuntimeException("Timeout");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unused")
    private Document initEkort(final Document document) {
        Request req = new Request.Builder().url("https://internetbank.swedbank.se/bviPrivat/privat?ai_TDEApplName=TDEApplKort&ai_flow_id_=KONTROLLERA_EKORTSTATUS_PRECLIENT")
                                           .get()
                                           .build();
        return jsoup(req);
    }

    private Document portalInit(final Document initEkortDoc) {
        FormBody.Builder formBody = new FormBody.Builder();
        FormElement form1 = (FormElement) initEkortDoc.select("form[name=form1]").first();
        for (KeyVal keyVal : form1.formData()) {
            formBody.add(keyVal.key(), keyVal.value());
        }
        Request req = new Request.Builder().url(form1.attr("abs:action"))
                                           .post(formBody.build())
                                           .build();
        return jsoup(req);
    }

    private Document loginStep1(final Document initPortalDoc) {
        FormBody.Builder formBody = new FormBody.Builder();
        FormElement form1 = (FormElement) initPortalDoc.select("form[name=form1]").first();
        form1.getElementById("form1:customer_plugin_java").val("");
        form1.getElementById("form1:customer_plugin_flash").val("");
        form1.getElementById("form1:customer_plugin_adobe_reader").val("");
        for (KeyVal keyVal : form1.formData()) {
            formBody.add(keyVal.key(), keyVal.value());
        }
        Request req = new Request.Builder().url(form1.attr("abs:action"))
                                           .post(formBody.build())
                                           .build();
        return jsoup(req);
    }

    private Document loginStep2(final Document loginStep1Doc) {
        FormElement authForm = (FormElement) loginStep1Doc.select("form[name=auth").first();
        Map<String, String> elements = new LinkedHashMap<>();
        for (KeyVal keyVal : authForm.formData()) {
            elements.put(keyVal.key(), keyVal.value());
        }
        elements.put("auth:metod_2", "MOBILBID");
        elements.put("auth:kundnummer", personNumber);
        elements.remove("auth:avbryt_knapp");
        FormBody.Builder formBody = new FormBody.Builder();
        elements.forEach(formBody::add);
        Request req = new Request.Builder().url(authForm.attr("abs:action"))
                                           .post(formBody.build())
                                           .build();
        return jsoup(req);

    }

    private Document loginStep3(final Document loginStep2Doc) {
        FormElement form = (FormElement) loginStep2Doc.select("form[name=form]").first();
        Map<String, String> elements = new LinkedHashMap<>();
        for (KeyVal keyVal : form.formData()) {
            elements.put(keyVal.key(), keyVal.value());
        }
        elements.put("form:returnCode", "1");
        elements.remove("form:avbryt_knapp");
        elements.remove("form:timeout_knapp");
        FormBody.Builder formBody = new FormBody.Builder();
        elements.forEach(formBody::add);

        Request req = new Request.Builder().url(form.attr("abs:action"))
                                           .post(formBody.build())
                                           .build();
        return jsoup(req);
    }

    private Document preClient(final Document loginStep3Doc) {
        FormElement redirectForm = (FormElement) loginStep3Doc.select("form[name=redirectForm]").first();
        FormBody.Builder formBody = new FormBody.Builder();
        for (KeyVal keyVal : redirectForm.formData()) {
            formBody.add(keyVal.key(), keyVal.value());
        }

        Request req = new Request.Builder().url(redirectForm.attr("abs:action"))
                                           .post(formBody.build())
                                           .build();
        return jsoup(req);
    }

    private ECardAPI selectIssuer(final HttpUrl url) throws IOException {
        if (webServletUrl != null) {
            throw new IllegalArgumentException("Already selected a different card");
        }
        Request selectCardReq = new Request.Builder().url(url)
                                                     .header("Referer", "https://internetbank.swedbank.se/bviPrivat/privat?_new_flow_=false&ai_TDEApplName=TDEApplKort&ai_flow_id_=KONTROLLERA_EKORTSTATUS_PRECLIENT")
                                                     .header("Accept-Encoding", "gzip, deflate, br")
                                                     .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8")
                                                     .get()
                                                     .build();

        HttpUrl thinOpenerUrl = findWindowOpen(jsoup(selectCardReq));
        LinkedHashMap<String, String> map = queryToHashMap(thinOpenerUrl);
        map.put("Request", "GetActiveCards");
        webServletUrl = thinOpenerUrl.newBuilder().encodedPath("/servlet/WebServlet").query(null).build();
        Map<String, String> result = executeWebServlet(map);
        realCards = unmodifiableList(RealCard.from(result));

        //Map<String, String> result = executeWebServlet(Collections.singletonMap("Request", "GetCards"));

        listProfile(realCards.get(0));

        return new ECardAPI();
    }

    // This seems to be a select card, not only list profiles. YMMV?
    private void listProfile(final RealCard realCard) throws IOException {
        Map<String, Object> req1 = new LinkedHashMap<>();
        req1.put("CardType", realCard.cardType);
        req1.put("VCardId", realCard.vCardId);

        req1.put("Request", "ListProfileIds");
        req1.put("ProfileType", "");
        executeWebServlet(req1);
    }

    @SuppressWarnings("TypeParameterExplicitlyExtendsObject")
    private Map<String, String> executeWebServlet(final Map<String, ? extends Object> map) throws IOException {
        loggingInterceptor.setLevel(debugLevel);
        FormBody.Builder thinClientBody = new FormBody.Builder();

        if (sessionId != null) {
            thinClientBody.add("SessionId", sessionId);
            thinClientBody.add("Version", "FLEXWEBCARD-SWEDBANK_2_4_44_0");
            thinClientBody.add("MsgNo", "" + msgNo++);
        }
        map.forEach((k, v) -> thinClientBody.add(k, v.toString()));
        thinClientBody.add("Locale", "sv");
        thinClientBody.add("IssuerId", "1");

        Request thinClientReq = new Request.Builder().url(webServletUrl)
                                                     .post(thinClientBody.build())
                                                     .build();

        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        Response execute = okhttpClient.newCall(thinClientReq).execute();

        HttpUrl responseBody = HttpUrl.parse("https://example.com/")
                                      .newBuilder()
                                      .encodedQuery(execute.body().string())
                                      .build();
        LinkedHashMap<String, String> resultMap = queryToHashMap(responseBody);
        resultMap.remove("Eof");
        if ("Error".equals(resultMap.get("Action"))) {
            resultMap.remove("Action");
            throw new IOException("" + resultMap.remove("ErrMsg") + " with result " + resultMap);
        }

        if (sessionId == null) {
            sessionId = resultMap.get("SessionId");
        }
        return resultMap;
    }

    private LinkedHashMap<String, String> queryToHashMap(final HttpUrl httpUrl) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        for (int i = 0; i < httpUrl.querySize(); i++) {
            result.put(httpUrl.queryParameterName(i), httpUrl.queryParameterValue(i));
        }
        return result;
    }

    private String decode(String encoded) {
        try {
            return URLDecoder.decode(encoded.replace("%2B", "漢変字"), "UTF-8").replace("漢変字", "+");
        } catch (UnsupportedEncodingException e) {
            // Won't happen
            throw new RuntimeException("JVM corrupt", e);
        }
    }

    private HttpUrl findWindowOpen(Document doc) {
        for (Element script : doc.getElementsByTag("script")) {
            Matcher matcher = WINDOW_OPEN_FINDER.matcher(script.html());
            if (matcher.find()) {
                return HttpUrl.parse(matcher.group(1));
            }
        }
        throw new RuntimeException("Didn't find script tag with window.open");
    }

    private Document jsoup(final Request request) {
        try {
            loggingInterceptor.setLevel(debugLevel);
            Response response = okhttpClient.newCall(request).execute();
            String string = response.body().string();
            if (!response.isSuccessful()) {
                throw new IOException("Failed with " + response.code() + " and " + string);
            }
            return Jsoup.parse(string, response.request().url().toString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public class Account {
        private final HttpUrl url;
        private final String name;
        private final String personNumber;
        private final String bankName;

        private Account(final HttpUrl url, final String name, final String personNumber, final String bankName) {
            this.url = url;
            this.name = name;
            this.personNumber = personNumber;
            this.bankName = bankName;
        }

        public String getName() {
            return name;
        }

        public String getPersonNumber() {
            return personNumber;
        }

        public String getBankName() {
            return bankName;
        }

        public ECardAPI selectIssuer() throws IOException {
            return ECardClient.this.selectIssuer(url);
        }
    }

    public class ECardAPI {

        public List<PastTransaction> getPastTransactions(RealCard realCard, int start) throws IOException {
            Map<String, Object> req2 = new LinkedHashMap<>();
            req2.put("CardType", realCard.cardType);
            req2.put("VCardId", realCard.vCardId);

            req2.put("Start", start);
            req2.put("Request", "GetPastTransactions");
            req2.put("Next", 100);

            return PastTransaction.from(executeWebServlet(req2));
        }

        public CPN createCard(RealCard realCard, int transactionLimit, int cumulativeLimit, int validForMonths) throws IOException {
            Map<String, Object> req1 = new LinkedHashMap<>();
            req1.put("CardType", realCard.cardType);
            req1.put("VCardId", realCard.vCardId);
            req1.put("Request", "GetCPN");
            req1.put("TransLimit", transactionLimit);
            req1.put("CumulativeLimit", cumulativeLimit);
            req1.put("ValidFor", validForMonths);
            return CPN.from(executeWebServlet(req1));
        }

        public List<ActiveECard> getActiveECards(RealCard realCard, int start) throws IOException {
            Map<String, Object> req1 = new LinkedHashMap<>();
            req1.put("CardType", realCard.cardType);
            req1.put("VCardId", realCard.vCardId);
            req1.put("Request", "GetActiveAccounts");
            req1.put("Start", start);
            req1.put("Next", 100);
            return ActiveECard.from(executeWebServlet(req1));
        }

        public void closeCard(RealCard realCard, String creditCardNumber) throws IOException {
            Map<String, Object> req1 = new LinkedHashMap<>();
            req1.put("CardType", realCard.cardType);
            req1.put("VCardId", realCard.vCardId);
            req1.put("Request", "CancelCPN");
            req1.put("CPNPAN", creditCardNumber);
            executeWebServlet(req1);
        }

        public List<RealCard> getCards() {
            return realCards;
        }

    }


}
