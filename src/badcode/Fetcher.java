package badcode;

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
    private int threadID;

    private WebClient webClient=new WebClient();
    private static Set<Cookie> cookies = new HashSet<Cookie>();
    private static int txtnum = 1;

    private Parser parser = new Parser();
    private Generator generator;
    private Crawler crawler;
    private boolean alive = true;

    Fetcher(Crawler crawler, Generator generator){
        webClient.getOptions().setJavaScriptEnabled(false);
        webClient.getOptions().setCssEnabled(false);
        webClient.getOptions().setThrowExceptionOnScriptError(false);
        webClient.getCookieManager().setCookiesEnabled(true);

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

                // TODO 获取HTML
                // 函数入口
                // 返回的HTML String
                // 调用
                // crawler.inject(parser.filterURL(HTMLString));
            }
            catch (Exception e) {
                System.out.println("Error fetching url!");
                e.printStackTrace();
            }
        }
    }

    public void kill() {
        alive = false;
    }

    public String getMainContent(String temp){
        if (!(temp.toLowerCase().startsWith("http://")
                || temp.toLowerCase().startsWith("https://"))) {
            temp += "http://";
        }

        String head = "http://183.174.228.9:8282/du/jsonp/ExtractMainContent?";
        head += temp;
        String mainContent="";

        try{
            URL url = new URL(head);
            mainContent = getURLSource(url);
            System.out.println(mainContent);
        }
        catch(Exception ee){
            System.out.println("Get ExtractMainContent @" + temp + " Error!");
            ee.printStackTrace();
        }

        return mainContent;
    }

    public static String getURLSource(URL url) throws Exception    {
        HttpURLConnection conn = (HttpURLConnection)url.openConnection();

        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5 * 1000);

        return new String(readInputStream(conn.getInputStream()));
    }

    public static byte[] readInputStream(InputStream inputStream) throws Exception {
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        byte[]  buffer = new byte[1204];
        int len;
        while ((len = inputStream.read(buffer)) != -1){
            outStream.write(buffer,0,len);
        }
        inputStream.close();
        return outStream.toByteArray();
    }

    public String getPdoc(String temp) throws Exception {
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

    public String getXmlResponse(URL url, WebClient client)throws IOException {
        StringBuffer temp = new StringBuffer();
        URLConnection uc = url.openConnection();
        uc.setConnectTimeout(10000);
        uc.setDoOutput(true);
        InputStream in = new BufferedInputStream(uc.getInputStream());
        Reader rd = new InputStreamReader(in,"UTF-8");

        int c = 0;
        while ((c = rd.read()) != -1) {
            temp.append((char) c);
        }

        in.close();

        cookies.addAll(client.getCookies(url));

        return temp.toString();
    }

    void writeFile(String string) throws IOException {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String txtname = txtnum++ +".txt";
        PrintWriter out = new PrintWriter(new FileWriter(txtname));
        out.println(df.format(new Date())+'\n'+string);
        out.close();
    }

}