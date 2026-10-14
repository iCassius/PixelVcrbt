package com.antigravity.pixelvcrbt;
import org.json.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Anonymous read-only GitHub query. No download, install, auth token or device data. */
final class UpdateChecker {
    static final String RELEASES="https://github.com/iCassius/PixelVcrbt/releases";
    static final class Result {final String text,url;Result(String text,String url){this.text=text;this.url=url;}}
    static Result check(String current,long currentCode,String packageName){
        ModuleVersion installed=ModuleVersion.parse(current);if(installed==null)return new Result("版本格式未知",null);
        try{
            JSONObject feed=new JSONObject(read("https://raw.githubusercontent.com/iCassius/PixelVcrbt/main/update.json"));
            if(feed.getInt("schema")!=1)throw new IOException("Unknown feed");
            JSONObject latest=null;
            for(String channel:new String[]{"stable","experimental"}){
                JSONObject candidate=feed.optJSONObject(channel);if(candidate==null)continue;
                ModuleVersion version=ModuleVersion.parse(candidate.optString("versionName"));
                if(version==null||!installed.accepts(version)||!candidate.optString("tag").equals("v"+version.name)||
                    !candidate.optString("apk").equals("PixelVcrbt-v"+version.name+".apk")||candidate.optLong("versionCode",0)<=0||
                    !candidate.optString("sha256").matches("[0-9a-f]{64}")||!candidate.optString("packageName").matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+"))continue;
                if(latest==null||candidate.getLong("versionCode")>latest.getLong("versionCode"))latest=candidate;
            }
            if(latest==null)return new Result("暂无可用版本",null);
            String url=RELEASES+"/tag/"+latest.getString("tag");long code=latest.getLong("versionCode");
            if(code>currentCode&&!packageName.equals(latest.optString("packageName")))return new Result("新版需迁移安装",url);
            return new Result(code>currentCode?"有新版本 "+latest.getString("versionName"):code==currentCode?"已是最新":"当前版本较新",code>currentCode?url:null);
        }catch(Exception ignored){android.util.Log.i("PixelVcrbtUpdate","feed_unavailable fallback=github_releases");}
        return github(installed);
    }
    private static String read(String url)throws Exception{
        HttpURLConnection connection=(HttpURLConnection)new URL(url).openConnection();
        try{
            connection.setConnectTimeout(5000);connection.setReadTimeout(5000);connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent","PixelVcrbt-update-check");
            int status=connection.getResponseCode();if(status!=200)throw new IOException("HTTP "+status);
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            try(InputStream in=connection.getInputStream()){byte[] chunk=new byte[8192];int n;while((n=in.read(chunk))!=-1){if(bytes.size()+n>4*1024*1024)throw new IOException("Response too large");bytes.write(chunk,0,n);}}
            return new String(bytes.toByteArray(),StandardCharsets.UTF_8);
        }finally{connection.disconnect();}
    }
    private static Result github(ModuleVersion installed){
        HttpURLConnection connection=null;
        try{
            connection=(HttpURLConnection)new URL("https://api.github.com/repos/iCassius/PixelVcrbt/releases?per_page=100").openConnection();
            connection.setConnectTimeout(5000);connection.setReadTimeout(5000);connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent","PixelVcrbt-update-check");
            connection.setRequestProperty("Accept","application/vnd.github+json");
            connection.setRequestProperty("X-GitHub-Api-Version","2022-11-28");
            int status=connection.getResponseCode();if(status!=200){android.util.Log.i("PixelVcrbtUpdate","github_http_status="+status);return new Result("检查失败，点击重试",null);}
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            try(InputStream in=connection.getInputStream()){
                byte[] chunk=new byte[8192];int n;while((n=in.read(chunk))!=-1){if(bytes.size()+n>4*1024*1024)throw new IOException("Response too large");bytes.write(chunk,0,n);}
            }
            JSONArray releases=new JSONArray(new String(bytes.toByteArray(),StandardCharsets.UTF_8));ModuleVersion latest=null;String tag=null;
            for(int i=0;i<releases.length();i++){
                JSONObject release=releases.getJSONObject(i);if(release.optBoolean("draft",true))continue;
                String candidateTag=release.optString("tag_name");ModuleVersion candidate=ModuleVersion.parse(candidateTag);
                if(candidate==null||!installed.accepts(candidate))continue;
                // Require an uploaded APK belonging to this exact version, not a source-only release.
                JSONArray assets=release.optJSONArray("assets");boolean apk=false;
                if(assets!=null)for(int j=0;j<assets.length();j++){JSONObject a=assets.getJSONObject(j);
                    apk|=("PixelVcrbt-"+candidateTag+".apk").equals(a.optString("name"))&&"uploaded".equals(a.optString("state"))&&a.optLong("size",0)>0;
                }
                if(apk&&(latest==null||candidate.compareTo(latest)>0)){latest=candidate;tag=candidateTag;}
            }
            if(latest==null)return new Result("暂无可用版本",null);
            int comparison=latest.compareTo(installed);
            return new Result(comparison>0?"有新版本 "+latest.name:comparison==0?"已是最新":"当前版本较新",comparison>0?RELEASES+"/tag/"+tag:null);
        }catch(Exception e){android.util.Log.i("PixelVcrbtUpdate","check_failed="+e.getClass().getSimpleName());return new Result("检查失败，点击重试",null);}
        finally{if(connection!=null)connection.disconnect();}
    }
}
