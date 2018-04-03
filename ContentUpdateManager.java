package net.finarx.twc.buskiosk;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;

import net.finarx.twc.buskiosk.background.BasicBackgroundTask;
import net.finarx.twc.buskiosk.media.PlayableContentHandler;
import net.finarx.twc.buskiosk.media.PlaylistHandler;
import net.finarx.twc.buskiosk.media.TickerHandler;
import net.finarx.twc.buskiosk.proxy.AbstractProxyServer;
import net.finarx.twc.buskiosk.proxy.DibosysProxyServer;
import net.finarx.twc.buskiosk.proxy.UsbProxyServer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;


public class ContentUpdateManager implements DownloadPlayableContentQueueHandler.DownloadCompleteHandler, AbstractProxyServer.ReceivedTelemetricResponseHandler, AbstractProxyServer.ReceivedTickerHandler, AbstractProxyServer.ReceivedPlaylistHandler{

    private static String PREF_KEY_LAST_UPDATE = "LastUpdateDate";

    interface ContentUpdateCompletionHandler{
        public void startingUpdate(ContentUpdateManager handler);
        public void updateDidSucceed(ContentUpdateManager handler);
        public void newContentAvailable(ContentUpdateManager handler, String path);
        public void receivedTickerData(ContentUpdateManager handler, TickerHandler th);
        public void receivedPlalist(ContentUpdateManager handler, PlaylistHandler plh);
        public void updateDidFail(ContentUpdateManager handler, Error e);
    }

    private long lastMillis;

    private PlaylistHandler receivedPlaylistHandler;

    public long lastUpdateMillis(){
        return lastMillis;
    }

    private ArrayList<ContentUpdateCompletionHandler> handlers;

    public void addContentUpdateCompletionHandler(ContentUpdateCompletionHandler h){
        if (h!=null) this.handlers.add(h);
    }

    public void removeContentUpdateCompletionHandler(ContentUpdateCompletionHandler h){
        if (h!=null) this.handlers.remove(h);
    }

    private static ContentUpdateManager sharedInstance;
    private static int TIMER_INTERVAL = 30;

    private DownloadPlayableContentQueueHandler queueHandler;

    public int getDefaultTimerInterval() {
        return TIMER_INTERVAL;
    }

    public int getDefaultUpdateInterval(){
        AppConfig c = AppConfig.getSharedInstance();
        if (c==null){
            return 1*60*60;
        }
        return AppConfig.getSharedInstance().getUpdateIntervalInMin() * 60;
    }

    private boolean isHandlingUpdate;

    public Context getContext() {
        return context;
    }

    public void setContext(Context context) {
        this.context = context;
    }

    private Context context;

    private Timer timer;

    public ContentUpdateManager(){
        if (sharedInstance==null) sharedInstance = this;
        this.setup();
    }

    /*
    public long lastUpdateMillis(){
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this.context);
        long lastUpdateMilli = preferences.getLong(PREF_KEY_LAST_UPDATE, 0);
        return lastUpdateMilli;
    }
    */

    private void setup(){

        if (this.timer==null){
            handlers = new ArrayList<>();
            final Handler handler = new Handler();

            TimerTask myTimerTask = new TimerTask() {

                @Override
                public void run() {
                    // post a runnable to the handler
                    handler.post(new Runnable() {

                        @Override
                        public void run() {
                            ContentUpdateManager.this.timerMethod(ContentUpdateManager.this.timer);
                        }
                    });
                }

            };

            this.timer = new Timer();

            timer.schedule(myTimerTask, 100, this.getDefaultTimerInterval() * 1000);
        }
    }

    public AbstractProxyServer getServerInstance()
    {
        return DibosysProxyServer.getSharedInstance(this.context);
    }

    public static ContentUpdateManager getSharedInstance(){
        if (sharedInstance==null) new ContentUpdateManager();
        return sharedInstance;
    }

    public void timerMethod(Timer timer)
    {
        long lastUpdateMilli = this.lastUpdateMillis();

        long currTime= System.currentTimeMillis();

        // TODO JUST A MARKER
        boolean outOfTime = lastUpdateMilli==0 || (currTime - lastUpdateMilli >= (this.getDefaultUpdateInterval() * 1000));

        if (this.canConnect() && !this.isHandlingUpdate){
            PlaylistHandler plh = PlaylistHandler.getSharedInstance();

            AbstractList<PlayableContentHandler> vids = plh.getPlayableContentHandlers();
            if (vids==null || vids.size()==0){
                plh.reloadContentFromFile();
                vids = plh.getPlayableContentHandlers();
            }
            boolean hasContentToPlay = !(vids==null || vids.size()==0);

            //always send telemetric data
            BasicBackgroundTask task = this.getServerInstance().sendTelemtriyDataAsync( DibosysProxyServer.getSharedInstance(this.context).getTelemetryData() , this);
            if (task!=null){
                task.execute();
            }

            if (outOfTime || !hasContentToPlay){
                this.beginUpdate();
            }
        }

    }

    public boolean canConnect(){
        return MediaHandler.isNetworkAvailable();
    }

    public void beginUpdate(){
        Log.d("Dibosys" ,"Perform Update.");
        this.isHandlingUpdate = true;
        BasicBackgroundTask task = this.getServerInstance().getTickerAsync(this);

        for (ContentUpdateCompletionHandler handler : this.handlers) handler.startingUpdate(this);

        if (task!=null){
            task.execute();
        } else {
            this.didReceiveTicker(null, null);
        }
    }

    @Override
    public void didReceiveTicker(String path, String content) {
        if (content==null && path != null){
            File txtFile = new File(path);

            try {
                BufferedReader br = new BufferedReader(new FileReader(txtFile));
                String line;

                String pref = "";
                while ((line = br.readLine()) != null) {
                    content+=pref + line;
                    pref = "\n";
                }
                br.close();
            }
            catch (IOException e) {
                //You'll need to add proper error handling here
            }
        }
        if (content!=null){
            Log.d("Dibosys", "Update- Received Ticker");
            TickerHandler th = new TickerHandler();
            th.parse(content);
            th.save();
            for (ContentUpdateCompletionHandler h : this.handlers) h.receivedTickerData(this, th);

        } else {
            Log.d("Dibosys", "Update- No Ticker! Update still running.");
            TickerHandler th = new TickerHandler();
            th.parse("");
            th.save();
            for (ContentUpdateCompletionHandler h : this.handlers) h.receivedTickerData(this, th);
            //this.didFail(new Error("No Ticker Found"));
        }
        BasicBackgroundTask task = this.getServerInstance().getPlaylistAsync(this);
        if (task!=null){
            task.execute();
        } else {
            this.didReceivePlaylist(null, null);
        }
    }

    @Override
    public void didReceivePlaylist(String path, String content) {
        if (content==null && path != null){
            File txtFile = new File(path);

            try {
                BufferedReader br = new BufferedReader(new FileReader(txtFile));
                String line;

                String pref = "";
                while ((line = br.readLine()) != null) {
                    content+=pref + line;
                    pref = "\n";
                }
                br.close();
            }
            catch (IOException e) {
                //You'll need to add proper error handling here
            }
        }
        if (content!=null){
            Log.d("Dibosys", "Update- Received Playlist");
            PlaylistHandler ph = PlaylistHandler.getSharedInstance();
            if (ph.getPlayableContentHandlers().size()==0){
                PlaylistHandler newHandler = new PlaylistHandler(content, true);
                ph.reloadContentFromFile();
                Log.d("Dibosys", "Notify About Playlist Changes -Pre");
                for (ContentUpdateCompletionHandler h : this.handlers) h.receivedPlalist(this, newHandler);
                this.receivedPlaylistHandler = null;
            } else {
                Log.d("Dibosys", "Keep Changes Until complete Update.");
                ph = new PlaylistHandler(content, false);
                this.receivedPlaylistHandler = ph;
            }
            if (this.queueHandler==null){
                this.queueHandler = this.setupDownloadQueueHandler();
            }
            this.queueHandler.removeDownloadCompleteHandler(this);
            this.queueHandler.addDownloadCompleteHandler(this);
            this.queueHandler.startDownloadQueue(ph);

        } else {
            Log.d("Dibosys", "Update- No Playlist! Update Cancelled.");
            this.didFail(new Error("No Playlist Found"));
        }

        /*
        BasicBackgroundTask task = this.getServerInstance().sendTelemtriyDataAsync( DibosysProxyServer.getSharedInstance(this.context).getTelemetryData() , this);
        if (task!=null){
            task.execute();
        } else {
            this.didReceiveTememetryResponse(null, null);
        }*/
    }

    @Override
    public void didReceiveTememetryResponse(String path, OutputStream output) {
        Log.d("Dibosys", "Receive telemetetric Response " + output.toString());
    }


    public DownloadPlayableContentQueueHandler setupDownloadQueueHandler()
    {
        DownloadPlayableContentQueueHandler queueHandler=null;
            queueHandler = new DownloadPlayableContentQueueHandler(this.context);
            queueHandler.setProxyServer(this.getServerInstance());
        return queueHandler;
    }

    @Override
    public void didFail(Error e) {
        this.isHandlingUpdate = false;
        this.callCompletionHanlders(false, e);
    }

    /**########################################### DOWNLOAD MOVIE FILES #############################***/
    @Override
    public void downloadCompleted() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this.context);
        SharedPreferences.Editor editor = preferences.edit();
        this.lastMillis = System.currentTimeMillis();
        editor.putLong(PREF_KEY_LAST_UPDATE,this.lastMillis);
        editor.apply();
        this.isHandlingUpdate = false;
        this.callCompletionHanlders(true, null);
    }

    @Override
    public void downloadCancelled(Error e) {
        this.isHandlingUpdate = false;
        this.callCompletionHanlders(false, e);
    }

    public void downloadedAFile(String path)
    {
        if (path != null){
            for (ContentUpdateCompletionHandler h : this.handlers) h.newContentAvailable(this, path);
        }
    }


    /**########################################### COMPLETION CALL #############################***/

    public void callCompletionHanlders(boolean success, Error e) {
        if (success){
            Log.d("Dibosys", "Update- Complete");

            if (this.receivedPlaylistHandler!=null){
                this.receivedPlaylistHandler.save();
                this.receivedPlaylistHandler = null;

                PlaylistHandler plh = PlaylistHandler.getSharedInstance();
                plh.reloadContentFromFile();
                plh.cleanUpPlayableContent();

                Log.d("Dibosys", "Notify About Playlist Changes");
                for (ContentUpdateCompletionHandler h : this.handlers) h.receivedPlalist(this, plh);
            } else {
                PlaylistHandler plh = PlaylistHandler.getSharedInstance();
                plh.reloadContentFromFile();
                plh.cleanUpPlayableContent();
            }

            for (ContentUpdateCompletionHandler h : this.handlers) h.updateDidSucceed(this);
        } else {
            e = e!=null? e : new Error("Unknown");
            Log.d("Dibosys", "Update- Failed with e:" + e.getMessage());
            for (ContentUpdateCompletionHandler h : this.handlers) h.updateDidFail(this, e);
        }
    }
}
