package net.finarx.twc.buskiosk;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.media.MediaPlayer;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import com.crashlytics.android.Crashlytics;
import io.fabric.sdk.android.Fabric;
import net.finarx.twc.buskiosk.Crashlog.TopExceptionHandler;
import net.finarx.twc.buskiosk.media.PlayableContentHandler;
import net.finarx.twc.buskiosk.media.PlaylistHandler;
import net.finarx.twc.buskiosk.media.TickerHandler;
import net.finarx.twc.buskiosk.proxy.DibosysProxyServer;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;

import com.crashlytics.android.Crashlytics;
import io.fabric.sdk.android.Fabric;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class FullscreenMainActivity extends AppCompatActivity implements ContentUpdateManager.ContentUpdateCompletionHandler {


    private ArrayList<PlayableContentHandler> playableContentHandlers;

    private PlayableContentHandler nextPlaylistItem;

    private VideoView videoView;
    private TickerTextView tickerView;

    private boolean shuffle = false;

    private ProgressDialog usbUpdateDialog;

    public static int DEFAULT_TICKER_TEXT_SIZE = 30;

    /**
     * Whether or not the system UI should be auto-hidden after
     * {@link #AUTO_HIDE_DELAY_MILLIS} milliseconds.
     */
    private static final boolean AUTO_HIDE = true;

    /**
     * If {@link #AUTO_HIDE} is set, the number of milliseconds to wait after
     * user interaction before hiding the system UI.
     */
    private static final int AUTO_HIDE_DELAY_MILLIS = 3000;

    /**
     * Some older devices needs a small delay between UI widget updates
     * and a change of the status and navigation bar.
     */
    private static final int UI_ANIMATION_DELAY = 300;
    private final Handler mHideHandler = new Handler();

    private USBContentUpdateManager usbHandler;

    private View mContentView;
    private final Runnable mHidePart2Runnable = new Runnable() {
        @SuppressLint("InlinedApi")
        @Override
        public void run() {
            // Delayed removal of status and navigation bar

            // Note that some of these constants are new as of API 16 (Jelly Bean)
            // and API 19 (KitKat). It is safe to use them, as they are inlined
            // at compile-time and do nothing on earlier devices.
            mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }
    };


    private View mControlsView;
    private final Runnable mShowPart2Runnable = new Runnable() {
        @Override
        public void run() {
            // Delayed display of UI elements
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.show();
            }
            //mControlsView.setVisibility(View.VISIBLE);
        }
    };
    private boolean mVisible;
    private final Runnable mHideRunnable = new Runnable() {
        @Override
        public void run() {
            hide();
        }
    };
    /**
     * Touch listener to use for in-layout UI controls to delay hiding the
     * system UI. This is to prevent the jarring behavior of controls going away
     * while interacting with activity UI.
     */
    private final View.OnTouchListener mDelayHideTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            if (AUTO_HIDE) {
                delayedHide(AUTO_HIDE_DELAY_MILLIS);
            }
            return false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Fabric.with(this, new Crashlytics());

        Thread.setDefaultUncaughtExceptionHandler(new TopExceptionHandler(this));

        new AppConfig(this.getApplicationContext());

        if (AppConfig.getSharedInstance().isRestartOnCrash()){
            AlarmBroadcastReceiver alarm = new AlarmBroadcastReceiver();
            alarm.SetAlarm(this.getApplicationContext());
        }
        if (AppConfig.getSharedInstance().isShuffle()){
            this.shuffle = true;
        }

        MediaHandler.appContext = this.getApplicationContext();
        DibosysProxyServer.cacheDir = MediaHandler.getCacheDirectory();
        DownloadPlayableContentQueueHandler.getSharedInstance(this.getApplicationContext());

        setContentView(R.layout.activity_fullscreen_main);

        mVisible = true;
        mControlsView = findViewById(R.id.fullscreen_content);
        mContentView = mControlsView;

        // Set up the user interaction to manually show or hide the system UI.
        if (mContentView!=null){
            mContentView.setKeepScreenOn(true);
            mContentView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    toggle();
                }
            });
        }


        this.tickerView = (TickerTextView) findViewById(R.id.ticker_view);
        if (this.tickerView != null)
        {
            this.tickerView.setSelected(true);
            this.tickerView.startScroll();
        }


        videoView = (VideoView)findViewById(R.id.video_view);
        if (videoView!=null){
            videoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    FullscreenMainActivity.this.continuePlayback();
                }
            });
            videoView.setOnErrorListener(new MediaPlayer.OnErrorListener() {

                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    Log.d("video", "setOnErrorListener: can't play this video.");
                    FullscreenMainActivity.this.continuePlayback();
                    return true;
                }
            });
        }

        ContentUpdateManager.getSharedInstance().addContentUpdateCompletionHandler(this);
        ContentUpdateManager.getSharedInstance().setContext(this.getApplicationContext());
        this.usbHandler = new USBContentUpdateManager();
        this.usbHandler.addContentUpdateCompletionHandler(this);
        this.usbHandler.setContext(this.getApplicationContext());
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        // Trigger the initial hide() shortly after the activity has been
        // created, to briefly hint to the user that UI controls
        // are available.
        delayedHide(100);

        this.reloadTicker();
        this.reloadPlayableContent();
    }

    private void showErrorMessage(String msg){
        TextView errorTextView = (TextView)findViewById(R.id.errorTextView);
        if (errorTextView!= null) {
            errorTextView.setText(msg);
            errorTextView.setVisibility(View.VISIBLE);
        }
    }

    private void hideErrorMessage(){
        TextView errorTextView = (TextView)findViewById(R.id.errorTextView);
        if (errorTextView!= null) {
            errorTextView.setVisibility(View.GONE);
        }
    }

    private boolean reloadPlayableContent(){
        Log.d("MainActivity", "Reload playable content.");
        PlaylistHandler plh = PlaylistHandler.getSharedInstance();

        // always clear older files
        //if (this.playableContentHandlers==null || this.playableContentHandlers.size()==0){
        plh.reloadContentFromFile();
        //}

        AbstractList<PlayableContentHandler> vids = plh.getPlayableContentHandlers();

        if (vids != null && vids.size()>0){

            ArrayList<PlayableContentHandler> reloadedPCHs = new ArrayList<>();

            reloadedPCHs.addAll(vids);

            if (this.playableContentHandlers!=null){
                ArrayList<PlayableContentHandler> oldPCHs = new ArrayList<>(this.playableContentHandlers);

                for (PlayableContentHandler pch : oldPCHs){
                    if (!pch.isValid()){
                        this.playableContentHandlers.remove(pch);
                    }
                }
            }

            if (this.playableContentHandlers!=null && this.playableContentHandlers.size()>0){
                PlayableContentHandler lastPCH = this.playableContentHandlers.get(this.playableContentHandlers.size()-1);
                boolean hasFoundLastPCH = false;
                for (PlayableContentHandler pch : vids){
                    if (hasFoundLastPCH){
                        this.playableContentHandlers.add(pch);
                    }
                    if (pch.getName().equals(lastPCH.getName())){
                        hasFoundLastPCH = true;
                    }
                }
            } else {
                this.playableContentHandlers = reloadedPCHs;
            }

            boolean hasContentToPlay = this.nextPlaylistItem!=null || (this.playableContentHandlers!=null && this.playableContentHandlers.size()>0);


            if (this.nextPlaylistItem==null && hasContentToPlay){
                this.nextPlaylistItem = this.playableContentHandlers.get(0);
                this.playableContentHandlers.remove(0);
            }

            if (!hasContentToPlay){
                String errMsg = ("Keine Daten zur Wiedergabe vorhanden.");
                this.showErrorMessage(errMsg);
            } else {
                this.hideErrorMessage();
            }
            if (this.nextPlaylistItem!=null && hasContentToPlay) {
                return this.continuePlayback();
            } else {
                return false;
            }
        } else {
            Log.d("MainActivity", "Reload playable Content, but has no Files");
        }
        boolean hasContentToPlay = this.nextPlaylistItem!=null || (this.playableContentHandlers!=null && this.playableContentHandlers.size()>0);

        if (!hasContentToPlay){
            String errMsg = ("Keine Daten zur Wiedergabe vorhanden.");
            this.showErrorMessage(errMsg);
        } else {
            this.hideErrorMessage();
        }
        return false;
    }

    private void reloadTicker(){
        //this.downloadTickerContent();
        TickerHandler th = new TickerHandler();
        String tickerText = th.getTickerText();
        th.save();
        long size=0;
        double speed=0;
        if (th.getTickerChannel()!=null){
             size = th.getTickerChannel().getSize();
             speed = th.getTickerChannel().getSpeed();
        }

        this.setTickerContent(tickerText, size, speed);
    }

    private void setTickerContent(final String tickerText, final long size, final double speed)
    {
        this.runOnUiThread(new Runnable() {
        @Override
        public void run() {
            int textLength = tickerText.length();
            if (tickerView!=null){
                if (textLength > 0){
                    if (tickerView.getVisibility() == View.GONE){
                        tickerView.setVisibility(View.VISIBLE);
                        Log.d("Dibosys", "Show Ticker View");
                    }
                    tickerView.pauseScroll();
                    tickerView.setTextSize(size==0? DEFAULT_TICKER_TEXT_SIZE : (float)size);
                    tickerView.setTickerSpeed(speed==0? TickerTextView.DEFAULT_TICKER_SPEED : speed);

                    tickerView.setText(tickerText);
                    tickerView.computeScroll();
                    tickerView.startScroll();
                } else {
                    if (tickerView.getVisibility() != View.GONE){
                        tickerView.setVisibility(View.GONE);
                        Log.d("Dibosys", "Hide Ticker View");
                    }
                }
            }
        }
    });

    }

    private boolean continuePlayback(){
        Log.d("MainActivity", "Continue Playback");
        PlaylistHandler ph = PlaylistHandler.getSharedInstance();
        boolean hasContentToPlay = ph.getPlayableContentHandlers()!=null && ph.getPlayableContentHandlers().size()>0;


        if (!hasContentToPlay){
            return this.reloadPlayableContent();
        }


        if (videoView!=null && !videoView.isPlaying() && this.playableContentHandlers!=null && (this.playableContentHandlers.size()>0||(this.nextPlaylistItem!=null&&this.videoView.isPlaying()==false))){
            if (this.nextPlaylistItem==null){
                return this.reloadPlayableContent();
            }
            boolean startedPlaying = false;
            if (this.nextPlaylistItem!=null && videoView.isPlaying()==false){
                Log.d("MainActivity", "Start Video");
                videoView.setVideoPath(this.nextPlaylistItem.getPath());
                videoView.start();
                startedPlaying = true;
                this.nextPlaylistItem = null;
            }

            if (this.playableContentHandlers==null || this.playableContentHandlers.size()<=0){
                if (ph.getPlayableContentHandlers().size()>0){
                    ArrayList<PlayableContentHandler> toShuffle = new ArrayList<>(ph.getPlayableContentHandlers());
                    if (this.shuffle) Collections.shuffle(toShuffle);
                    this.playableContentHandlers = toShuffle;
                }
            }

            if (this.playableContentHandlers!=null && this.playableContentHandlers.size()>0 && this.nextPlaylistItem==null){
                this.nextPlaylistItem = this.playableContentHandlers.get(0);
                this.playableContentHandlers.remove(0);
                /*
                while (this.nextPlaylistItem != null && !this.nextPlaylistItem.isValid() && this.playableContentHandlers!=null && this.playableContentHandlers.size()>0){
                    this.playableContentHandlers.remove(0);
                    this.nextPlaylistItem = this.playableContentHandlers.size()>0? this.playableContentHandlers.get(0) : null;
                }*/
                //if(this.playableContentHandlers.size()>0)
            }

            if (!startedPlaying){
                return this.continuePlayback();
            } else {
                return true;
            }
        }
        return hasContentToPlay;
    }

    private void toggle() {
        if (mVisible) {
            hide();
        } else {
            show();
        }
    }

    private void hide() {
        // Hide UI first
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }
        //mControlsView.setVisibility(View.GONE);
        mVisible = false;

        // Schedule a runnable to remove the status and navigation bar after a delay
        mHideHandler.removeCallbacks(mShowPart2Runnable);
        mHideHandler.postDelayed(mHidePart2Runnable, UI_ANIMATION_DELAY);
    }

    @SuppressLint("InlinedApi")
    private void show() {
        // Show the system bar
        mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        mVisible = true;

        // Schedule a runnable to display UI elements after a delay
        mHideHandler.removeCallbacks(mHidePart2Runnable);
        mHideHandler.postDelayed(mShowPart2Runnable, UI_ANIMATION_DELAY);
    }

    /**
     * Schedules a call to hide() in [delay] milliseconds, canceling any
     * previously scheduled calls.
     */
    private void delayedHide(int delayMillis) {
        mHideHandler.removeCallbacks(mHideRunnable);
        mHideHandler.postDelayed(mHideRunnable, delayMillis);
    }


    /***************************  UPDATE HANDLER   ***************************/

    @Override
    public void startingUpdate(ContentUpdateManager handler){
        if (handler==this.usbHandler){
            Toast.makeText(this.getApplicationContext(), "USB erkannt. Update wird gestartet..." , Toast.LENGTH_SHORT).show();
            if(!this.isFinishing()){
                this.usbUpdateDialog = ProgressDialog.show(this, "Update", "Daten werden kopiert...");
            }
        }
    }

    @Override
    public void updateDidSucceed(ContentUpdateManager handler) {
        Log.d("MainActivityUpdate", "Update complete");
        if (handler == this.usbHandler){
            Toast.makeText(this, "Daten wurden geladen." , Toast.LENGTH_SHORT).show();
            if (this.usbUpdateDialog!=null){
                this.usbUpdateDialog.dismiss();
            }
        }
    }

    @Override
    public void newContentAvailable(ContentUpdateManager handler, String path) {
        Log.d("MainActivity", "Received new content: "+path);
        this.reloadPlayableContent();
    }

    @Override
    public void receivedTickerData(ContentUpdateManager handler, TickerHandler th) {
        this.reloadTicker();
    }

    @Override
    public void receivedPlalist(ContentUpdateManager handler, PlaylistHandler plh) {
        this.reloadPlayableContent();
    }

    @Override
    public void updateDidFail(ContentUpdateManager handler, Error e) {
        Log.d("MainActivityUpdate", "Update failed " + e.getMessage());
        this.reloadPlayableContent();
        if (handler == this.usbHandler){
            Toast.makeText(this.getApplicationContext(), e!=null? e.getMessage() : "Cancelled for Unknown Error", Toast.LENGTH_SHORT);
            if (this.usbUpdateDialog!=null){
                this.usbUpdateDialog.dismiss();
            }
        }
    }

}
