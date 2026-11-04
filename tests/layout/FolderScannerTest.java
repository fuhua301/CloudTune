package com.cloudtune.player;

import android.content.*;
import android.content.pm.ProviderInfo;
import android.database.*;
import android.net.Uri;
import android.os.*;
import android.provider.DocumentsContract;
import android.view.*;
import android.widget.TextView;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={28,35},manifest=Config.NONE,shadows=LocalSettingsTest.Metadata.class)
public class FolderScannerTest {
    static class Node {
        String id,name,mime;File file;long modified=1000;
        Node(String i,String n,String m,File f){id=i;name=n;mime=m;file=f;}
    }
    public static class Provider extends ContentProvider {
        final Map<String,ArrayList<Node>> children=new HashMap<String,ArrayList<Node>>();final Map<String,Node> nodes=new HashMap<String,Node>();int opens;boolean denied;
        void add(String parent,Node node){nodes.put(node.id,node);if(!children.containsKey(parent))children.put(parent,new ArrayList<Node>());children.get(parent).add(node);}
        public boolean onCreate(){return true;}
        public Cursor query(Uri uri,String[] projection,String selection,String[] arguments,String sort){
            if(denied)throw new SecurityException("revoked");String id=DocumentsContract.getDocumentId(uri);MatrixCursor cursor=new MatrixCursor(projection);
            List<Node> all="children".equals(uri.getLastPathSegment())?children.containsKey(id)?children.get(id):Collections.<Node>emptyList():Collections.singletonList(nodes.get(id));
            for(Node node:all){if(node==null)continue;Object[] row=new Object[projection.length];for(int i=0;i<projection.length;i++){
                String key=projection[i];row[i]=DocumentsContract.Document.COLUMN_DOCUMENT_ID.equals(key)?node.id:DocumentsContract.Document.COLUMN_DISPLAY_NAME.equals(key)?node.name:DocumentsContract.Document.COLUMN_MIME_TYPE.equals(key)?node.mime:DocumentsContract.Document.COLUMN_SIZE.equals(key)?node.file==null?0:node.file.length():node.modified;
            }cursor.addRow(row);}return cursor;
        }
        public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{if(denied)throw new SecurityException();opens++;return ParcelFileDescriptor.open(nodes.get(DocumentsContract.getDocumentId(uri)).file,ParcelFileDescriptor.MODE_READ_ONLY);}
        public String getType(Uri uri){Node n=nodes.get(DocumentsContract.getDocumentId(uri));return n==null?null:n.mime;}
        public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException();}public int delete(Uri uri,String where,String[] args){throw new UnsupportedOperationException();}public int update(Uri uri,ContentValues values,String where,String[] args){throw new UnsupportedOperationException();}
    }
    File file(Context c,String name,String bytes)throws Exception{File f=new File(c.getCacheDir(),name);Files.write(f.toPath(),bytes.getBytes("UTF-8"));return f;}
    FolderScanner.Progress progress=new FolderScanner.Progress(){public void update(int checked,int imported){}};
    @Test public void recursivelyImportsCachesChangesAndHandlesRevokedAccess()throws Exception{
        Context c=RuntimeEnvironment.getApplication();Provider provider=new Provider();ProviderInfo info=new ProviderInfo();info.authority="folder.test";provider.attachInfo(c,info);ShadowContentResolver.registerProviderInternal("folder.test",provider);
        provider.add("root",new Node("sub","唱片",DocumentsContract.Document.MIME_TYPE_DIR,null));
        provider.add("root",new Node("a","歌曲A.mp3","audio/mpeg",file(c,"a.mp3","song one")));
        provider.add("root",new Node("txt","说明.txt","text/plain",file(c,"readme.txt","text only")));
        provider.add("sub",new Node("b","歌曲B.flac","application/octet-stream",file(c,"b.flac","song two")));
        provider.add("sub",new Node("duplicate","重复.mp3","audio/mpeg",file(c,"dup.mp3","song one")));
        Uri tree=DocumentsContract.buildTreeDocumentUri("folder.test","root");FolderScanner.add(c,tree);FolderScanner.add(c,tree);assertEquals(1,FolderScanner.folders(c).size());
        FolderScanner.Result first=FolderScanner.scan(c,new AtomicBoolean(),progress);assertEquals(0,first.failed);assertEquals(3,first.checked);assertEquals(2,first.imported);assertEquals(2,LocalMusic.read(c).size());int opened=provider.opens;
        FolderScanner.Result second=FolderScanner.scan(c,new AtomicBoolean(),progress);assertEquals(3,second.unchanged);assertEquals(0,second.imported);assertEquals(opened,provider.opens);
        provider.add("sub",new Node("new","新音乐.ogg","audio/ogg",file(c,"new.ogg","new music")));FolderScanner.Result added=FolderScanner.scan(c,new AtomicBoolean(),progress);assertEquals(1,added.imported);assertEquals(3,LocalMusic.read(c).size());
        Node modified=provider.nodes.get("a");Files.write(modified.file.toPath(),"changed music".getBytes("UTF-8"));modified.modified+=100;FolderScanner.Result changed=FolderScanner.scan(c,new AtomicBoolean(),progress);assertEquals(1,changed.imported);assertEquals(4,LocalMusic.read(c).size());
        provider.denied=true;assertTrue(FolderScanner.scan(c,new AtomicBoolean(),progress).failed>0);assertEquals(4,LocalMusic.read(c).size());provider.denied=false;
        assertTrue(FolderScanner.scan(c,new AtomicBoolean(true),progress).cancelled);assertEquals(4,LocalMusic.read(c).size());
        FolderScanner.remove(c,tree);assertTrue(FolderScanner.folders(c).isEmpty());assertEquals(4,LocalMusic.read(c).size());
    }
    public static class App extends MainActivity {
        int requests;
        public void onCreate(Bundle state){setTheme(android.R.style.Theme_Material_Light_NoActionBar);super.onCreate(state);}
        @Override void scanFolders(boolean notify){requests++;}
    }
    TextView find(View view,String text){if(view instanceof TextView&&text.equals(((TextView)view).getText().toString()))return (TextView)view;if(view instanceof ViewGroup){ViewGroup g=(ViewGroup)view;for(int i=0;i<g.getChildCount();i++){TextView f=find(g.getChildAt(i),text);if(f!=null)return f;}}return null;}
    @Test public void startupRequestsScanAndPickerUsesPersistentFolderGrant(){
        org.robolectric.android.controller.ActivityController<App> controller=Robolectric.buildActivity(App.class).setup().visible();App app=controller.get();assertTrue(app.requests>0);app.showPlayer();TextView home=find(app.root,"‹  主页");assertNotNull(home);home.performClick();assertEquals("home",app.screen);
        app.pickFolder();Intent intent=((ShadowActivity)org.robolectric.shadow.api.Shadow.extract(app)).getNextStartedActivityForResult().intent;assertEquals(Intent.ACTION_OPEN_DOCUMENT_TREE,intent.getAction());assertTrue((intent.getFlags()&Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)!=0);assertTrue((intent.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION)!=0);
        assertTrue(FolderScanner.audio("A.FLAC","application/octet-stream"));assertFalse(FolderScanner.audio("picture.jpg","image/jpeg"));controller.pause().stop().destroy();
    }
}
