package com.cloudtune.player;
import com.sun.net.httpserver.*;
import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
class WireTests {
    static void run() throws Exception{
        final AtomicReference<byte[]> received=new AtomicReference<byte[]>();
        final AtomicReference<String> contentType=new AtomicReference<String>(),length=new AtomicReference<String>(),route=new AtomicReference<String>(),mid=new AtomicReference<String>(),query=new AtomicReference<String>();
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/wire",new HttpHandler(){public void handle(HttpExchange e)throws IOException{
            ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int n;while((n=e.getRequestBody().read(buffer))!=-1)b.write(buffer,0,n);received.set(b.toByteArray());
            contentType.set(e.getRequestHeaders().getFirst("Content-Type"));length.set(e.getRequestHeaders().getFirst("Content-length"));route.set(e.getRequestHeaders().getFirst("x-router"));mid.set(e.getRequestHeaders().getFirst("mid"));query.set(e.getRequestURI().getRawQuery());
            byte[] ok="{\"status\":1}".getBytes("UTF-8");e.sendResponseHeaders(200,ok.length);e.getResponseBody().write(ok);e.close();
        }});
        server.createContext("/redirect",new HttpHandler(){public void handle(HttpExchange e)throws IOException{e.getResponseHeaders().add("Location","/wire");e.sendResponseHeaders(302,-1);e.close();}});
        server.start();String base="http://127.0.0.1:"+server.getAddress().getPort();
        try{
            TreeMap<String,String> p=new TreeMap<String,String>();p.put("appid","1005");p.put("clienttime","1700000000");p.put("mid","123456789012345678901234567890");p.put("text","汉字 & +");
            byte[] body={0,1,2,127,(byte)128,(byte)255};
            WireClient.request(base,"/wire",p,body,null,false,p.get("mid"),null);
            ProtocolTests.check(Arrays.equals(body,received.get()),"actual HTTP sends binary AES body unchanged");
            ProtocolTests.check(contentType.get().equals("application/octet-stream"),"binary content type");
            ProtocolTests.check(length.get().equals("6"),"fixed content length agrees with byte body");
            ProtocolTests.check(mid.get().equals(p.get("mid")),"device header matches query");
            ProtocolTests.check(URLDecoder.decode(query.get(),"UTF-8").contains("text=汉字 & +"),"query encoding roundtrip");
            ProtocolTests.check(!p.containsKey("signature"),"transport does not mutate caller params");
            Map<String,String> headers=new HashMap<String,String>();headers.put("x-router","usercenter.kugou.com");headers.put("Content-Type","application/json");
            byte[] json="{\"test\":\"中文\"}".getBytes("UTF-8");
            WireClient.request(base,"/wire",p,json,KugouApi.ANDROID_SALT,false,p.get("mid"),headers);
            ProtocolTests.check(Arrays.equals(json,received.get()),"signed JSON body remains byte-exact");
            ProtocolTests.check(contentType.get().equals("application/json")&&route.get().equals("usercenter.kugou.com"),"session diagnostic uses correct gateway route");
            ProtocolTests.check(query.get().contains("signature="+WireClient.signature(p,json,KugouApi.ANDROID_SALT,false)),"wire signature matches exact request");
            ProtocolTests.check(!WireClient.signature(p,body,KugouApi.ANDROID_SALT,false).equals(WireClient.signature(p,new String(body,"UTF-8").getBytes("UTF-8"),KugouApi.ANDROID_SALT,false)),"non-UTF8 bytes are not damaged during signing");
            boolean redirect=false;try{WireClient.request(base,"/redirect",p,null,null,false,p.get("mid"),null);}catch(IOException ex){redirect=true;}
            ProtocolTests.check(redirect,"credential-bearing requests never follow redirects");
        }finally{server.stop(0);}
    }
}
