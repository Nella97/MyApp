package net.finarx.twc.buskiosk;

import android.content.Context;

import android.util.Log;

import net.finarx.twc.buskiosk.background.BasicBackgroundTask;
import net.finarx.twc.buskiosk.media.PlayableContentHandler;
import net.finarx.twc.buskiosk.media.PlaylistHandler;
import net.finarx.twc.buskiosk.proxy.AbstractProxyServer;
import net.finarx.twc.buskiosk.proxy.DibosysProxyServer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

/**
 * Created by Julien on 11.05.16.
 */
public class DownloadPlayableContentQueueHandler {

    interface DownloadCompleteHandler{
        public void downloadCompleted();
        public void downloadedAFile(String path);
        public void downloadCancelled(Error e);
    }

    private static ArrayList<BasicBackgroundTask> downloadQueue = new ArrayList<>();

    private static BasicBackgroundTask currentDownloadTask = null;

    private ArrayList<DownloadCompleteHandler> handlers = new ArrayList<>();

    private Context context;

    public boolean justUseInternalStorage;

    private static DownloadPlayableContentQueueHandler sharedInstance;

    private AbstractProxyServer proxyServer;

    public void setProxyServer(AbstractProxyServer server){
        this.proxyServer = server;
    }

    public void addDownloadCompleteHandler(DownloadCompleteHandler h){
        if (h!=null)this.handlers.add(h);
    }
    public void removeDownloadCompleteHandler(DownloadCompleteHandler h){
        if (h!=null)this.handlers.remove(h);
    }

    public DownloadPlayableContentQueueHandler(Context context){
        if (sharedInstance==null) sharedInstance=this;
        this.context = context;
    }

    public static DownloadPlayableContentQueueHandler getSharedInstance(Context context){
        if (sharedInstance==null) new DownloadPlayableContentQueueHandler(context);
        return sharedInstance;
    }

    public boolean isDownloadingFiles(){
        return currentDownloadTask != null;
    }

    /**
     * Starts the download queue just if isDownloadingFiles is false
     */
    public void startDownloadQueue()
    {
        PlaylistHandler plh = PlaylistHandler.getSharedInstance();
        this.startDownloadQueue(plh);
    }

    public void startDownloadQueue(PlaylistHandler plh)
    {
        AbstractList<PlayableContentHandler> playableContentFiles = plh.getPlayableContentHandlers();

        ArrayList<String> validFiles = new ArrayList<>();

        for (PlayableContentHandler pch : playableContentFiles)
        {
            String fullname= pch.getName() + "." + pch.getMediaType();
            boolean outofdate = !plh.getPlaylistFileNames().contains(fullname);

            if (pch.isValid() && !outofdate) {
                // add to download queue
                validFiles.add(fullname);
            }
        }

        for (String filename : plh.getPlaylistFileNames())
        {
            if (!validFiles.contains(filename)){
                //add to download queue
                this.addFileToDownloadQeue(filename, this.context);
            }
        }

        this.continueDownloadProcess();
    }

    private BasicBackgroundTask getBackgroundTask(String id){
        for (BasicBackgroundTask dt : downloadQueue){
            if (dt.getIdentifier().equals(id)) return dt;
        }
        return null;
    }

    private void addFileToDownloadQeue(final String filename, Context context)
    {
        BasicBackgroundTask dt = getBackgroundTask(filename);
        if (dt==null){
            // add new download task

            String contentIdentifier = filename;//.substring(0, filename.lastIndexOf('.'));

            AbstractProxyServer.ReceivedPlayableContentHandler handler = new AbstractProxyServer.ReceivedPlayableContentHandler() {
                @Override
                public void didReceivePlayableContent(String path){
                    File playableContentSRCcache = new File(path);
                    File playableContentDST;

                    if (MediaHandler.isExternalStorageWritable() && justUseInternalStorage==false) {
                        playableContentDST = new File(MediaHandler.getExternalStoragePath()+ "/"+PlaylistHandler.PLAYABLECONTENT_FOLDER + "/" + filename);
                    } else {
                        playableContentDST = new File(MediaHandler.getInternalStoragePath() + "/"+PlaylistHandler.PLAYABLECONTENT_FOLDER + "/" + filename);
                    }

                    File parent = playableContentDST.getParentFile();
                    if(!parent.exists()){
                        if (playableContentDST.mkdirs()){
                            Log.d("Dibosys", "Could make dirs " + parent.getAbsolutePath());
                        } else {
                            Log.d("Dibosys", "Could not make dirs " + parent.getAbsolutePath());
                        }
                        try {
                            //playableContentDST.createNewFile();
                        } catch (Exception e){
                            e.printStackTrace();
                        }
                        //throw new IllegalStateException("Couldn't create dir: " + parent);
                    }

                    try {
                        copyFile(playableContentSRCcache, playableContentDST);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    currentDownloadTask = null;
                    Log.d("Dibosys", "Downloaded File "+ path);
                    DownloadPlayableContentQueueHandler.this.downloadedOneFile(path);
                    DownloadPlayableContentQueueHandler.this.continueDownloadProcess();
                }
                @Override
                public void didFail(Error e){
                    Log.d("Dibosys", "Download Failed with code " + e.getMessage());
                    currentDownloadTask = null;
                    // call delegated handlers
                    DownloadPlayableContentQueueHandler.this.downloadProcessCancelled(e);
                }
            };

            AbstractProxyServer proxy = this.proxyServer!=null? this.proxyServer : DibosysProxyServer.getSharedInstance(this.context);
            dt =  proxy.getPlayableContentAsync(contentIdentifier, handler);
            dt.setIdentifier(filename);
            downloadQueue.add(dt);
        } else {
            // otherwise do nothing, we will continue if one of the others are finished
        }
    }

    public static void copyFile(File src, File dst) throws IOException
    {
        FileChannel inChannel = new FileInputStream(src).getChannel();

        File parent = dst.getParentFile();
        if(!parent.exists() && !parent.mkdirs()){
            //dst.createNewFile();
            //throw new IllegalStateException("Couldn't create dir: " + parent);
        }
        if (!dst.exists()) dst.createNewFile();

        FileChannel outChannel = new FileOutputStream(dst).getChannel();

        try
        {
            inChannel.transferTo(0, inChannel.size(), outChannel);
        }
        catch (Exception e){
            Log.d("Dibosys", "DownloadExc - Could not copy File");
            e.printStackTrace();
        }
        finally
        {
            if (inChannel != null)
                inChannel.close();
            if (outChannel != null)
                outChannel.close();
        }
    }


    /**
     * Be sure that the current download task is NULL !!
     * otherwise nothing will happen
     */
    public void continueDownloadProcess()
    {
        if (!this.isDownloadingFiles()){

            if (downloadQueue != null && downloadQueue.size()>0){
                BasicBackgroundTask dt = downloadQueue.get(0);
                downloadQueue.remove(0);
                currentDownloadTask = dt;
                dt.execute();
            } else {
                // download complete
                // save download date
                this.downloadProcessCompete();
            }
        }
    }

    private void downloadedOneFile(String path){
        for (DownloadCompleteHandler dh : handlers) dh.downloadedAFile(path);
    }

    private void downloadProcessCompete(){
        for (DownloadCompleteHandler dh : handlers) dh.downloadCompleted();

    }

    private void downloadProcessCancelled(Error e){
        for (DownloadCompleteHandler dh : DownloadPlayableContentQueueHandler.this.handlers) dh.downloadCancelled(e);
    }

}
