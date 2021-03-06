package badcode;

import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlSubmitInput;
import com.gargoylesoftware.htmlunit.html.HtmlTextInput;
import com.gargoylesoftware.htmlunit.util.Cookie;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class Fetcher extends Thread {
    private static AtomicInteger startThread = new AtomicInteger(0);
    private static String pagePath = "\\pages\\";
    private static String infoPath = "\\info\\";
    private int threadID;

    private WebClient webClient=new WebClient();
    private static Set<Cookie> cookies = new HashSet<Cookie>();

    private Parser parser = new Parser();
    private Generator generator;
    private Crawler crawler;
    private boolean alive = true;
    private boolean doNLP = false;

    Fetcher(Crawler crawler, Generator generator, boolean NLP){
        webClient.getOptions().setJavaScriptEnabled(false);
        webClient.getOptions().setCssEnabled(false);
        webClient.getOptions().setThrowExceptionOnScriptError(false);
        webClient.getCookieManager().setCookiesEnabled(true);

        this.doNLP = NLP;
        this.generator = generator;
        this.crawler = crawler;
    }

    // 启动入口
    @Override
    public void run(){
        super.run();

        threadID = startThread.incrementAndGet();
        String url;

        while (alive) {
            try {
                url = generator.generate();

                if (url == null || url.equals("")) {
                    Thread.sleep(500);
                    continue;
                }

                if (CrawlDB.addDirtyURL(url) <= 0)
                    continue;

                String html = getXmlResponse(url);

                writeFile(html, url, pagePath);
                crawler.inject(parser.extractLink(html, new URL(url)));
                NLP(url);
            }
            catch (Exception e) {
                System.out.println("Error fetching url!");
                e.printStackTrace();
            }
        }
    }

    public void kill() {
        alive = false;
        interrupt();
    }

    private String getMainContent(String temp){
        if (!(temp.toLowerCase().startsWith("http://")
                || temp.toLowerCase().startsWith("https://"))) {
            temp += "http://";
        }

        String head = "http://183.174.228.9:8282/du/jsonp/ExtractMainContent?url=";
        head += temp;
        String mainContent = null;

        try{
            mainContent = getRawResponse(head);
            System.out.println(mainContent);
        }
        catch(Exception ee){
            System.out.println("Get ExtractMainContent @" + temp + " Error!");
            ee.printStackTrace();
        }

        return mainContent;
    }

    private String getPdoc(String temp) throws Exception {
        URL u = new URL("http://websensor.playbigdata.com/du/Service.svc/pdoc");
        WebRequest webrequest = new WebRequest(u,"POST");
        webrequest.setRequestBody(temp);

        webClient.addRequestHeader("Host","http://websensor.playbigdata.com");
        webClient.addRequestHeader("Connection","keep-alive");
        webClient.addRequestHeader("Content-Length","32673");
        for(Cookie c: cookies){
            webClient.addRequestHeader("Cookies",c.toString());
        }

        return ((HtmlPage)webClient.getPage(webrequest)).asXml();
    }

    private String getXmlResponse(String str) throws Exception {
        URL url = new URL(str);

        Page page = webClient.getPage(url);
        String re = page.getWebResponse().getContentAsString();

        cookies.addAll(webClient.getCookies(url));

        return re;
    }

    private String getRawResponse(String url) throws Exception {
        URL u = new URL(url);
        InputStream in =u.openStream();
        InputStreamReader isr = new InputStreamReader(in);
        BufferedReader bufferedReader = new BufferedReader(isr);

        String str = "", temp;
        while ((temp = bufferedReader.readLine()) != null) {
            str += temp;
        }

        bufferedReader.close();
        isr.close();
        in.close();

        return str;
    }

    private void writeFile(String html, String name, String path) throws IOException {
        File file = new File(path + name + ".html");
        if (!file.exists())
            file.createNewFile();
        (new BufferedWriter(new FileWriter(file))).write(html);
    }

    private void NLP(String url) throws Exception{
        JParser jParser = new JParser();
        String item = getMainContent(url);
        NLP.News content = jParser.getContent(item);

        if (content != null) {
            writeFile(content.content, content.title, infoPath);
            if (doNLP) {
                NLP.Words words = jParser.getWords(getPdoc(item));
                words.dump();
            }
        }
    }
}