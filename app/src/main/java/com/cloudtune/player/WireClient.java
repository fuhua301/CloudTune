package com.cloudtune.player;

import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Pure-Java HTTP layer, also exercised against a local HTTP server in tests. */
final class WireClient {
    static String signature(TreeMap<String,String> params,byte[] body,String salt,boolean web) throws Exception{
        ArrayList<String> parts=new ArrayList<String>();
        for(Map.Entry<String,String> e:params.entrySet())parts.add(e.getKey()+"="+e.getValue());
        if(web)Collections.sort(parts);
        MessageDigest md=MessageDigest.getInstance("MD5");md.update(salt.getBytes("UTF-8"));
        for(String p:parts)md.update(p.getBytes("UTF-8"));
        if(body!=null)md.update(body);md.update(salt.getBytes("UTF-8"));return KugouApi.hex(md.digest());
    }
    static byte[] request(String base,String path,TreeMap<String,String> input,byte[] body,String salt,boolean web,String mid,Map<String,String> extra) throws Exception{
        TreeMap<String,String> params=new TreeMap<String,String>(input);
        if(salt!=null)params.put("signature",signature(params,body,salt,web));
        StringBuilder q=new StringBuilder();for(Map.Entry<String,String> e:params.entrySet()){
            if(q.length()>0)q.append('&');q.append(URLEncoder.encode(e.getKey(),"UTF-8")).append('=').append(URLEncoder.encode(e.getValue(),"UTF-8"));
        }
        HttpURLConnection c=(HttpURLConnection)new URL(base+path+"?"+q).openConnection();
        try{
            c.setConnectTimeout(15000);c.setReadTimeout(20000);c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent","Android15-1070-11083-46-0-DiscoveryDRADProtocol-wifi");
            c.setRequestProperty("dfid","-");c.setRequestProperty("mid",mid);
            if(params.containsKey("clienttime"))c.setRequestProperty("clienttime",params.get("clienttime"));
            c.setRequestProperty("kg-rc","1");c.setRequestProperty("kg-thash","5d816a0");c.setRequestProperty("kg-rec","1");c.setRequestProperty("kg-rf","B9EDA08A64250DEFFBCADDEE00F8F25F");
            if(body!=null){c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/octet-stream");c.setFixedLengthStreamingMode(body.length);}
            if(extra!=null)for(Map.Entry<String,String> e:extra.entrySet())c.setRequestProperty(e.getKey(),e.getValue());
            if(body!=null)try(OutputStream out=c.getOutputStream()){out.write(body);}
            int status=c.getResponseCode();if(status!=200)throw new IOException("酷狗接口响应 HTTP "+status);
            InputStream stream=c.getInputStream();if("gzip".equalsIgnoreCase(c.getContentEncoding()))stream=new GZIPInputStream(stream);
            ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream in=stream){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){out.write(b,0,n);if(out.size()>16000000)throw new IOException("接口响应过大");}}
            return out.toByteArray();
        }finally{c.disconnect();}
    }
}
