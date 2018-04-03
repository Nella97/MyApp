package net.finarx.twc.buskiosk;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import net.finarx.twc.buskiosk.media.PlayableContentHandler;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.StringTokenizer;

/**
 * Created by  on Ornella */
public class AppConfig {

    //static String CONFIGFILE_PATH = "config.txt";
    static String REQUEST_METHOD_PLAYLIST = "app_playlist";
    static String REQUEST_METHOD_TICKER = "app_ticker";
    static String DEFAULT_HOSTNAME = "dibosys-disp-8001";
    static String DEFAULT_SERVER_URL = "http://dev.dibosys.de";

    static String DEFAULT_CONFIG_FILENAME = "config.txt";
    static int DEFAULT_INTERVAL_IN_MIN = 1 * 60; // h * min


    public String getAppHostname() {
        return appHostname;
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public String getPlaylistName() {
        return playlistName;
    }

    public String getTickerName() {
        return tickerName;
    }

    public int getUpdateIntervalInMin() {
        return updateIntervalInMin;
    }

    private String serverUrl;
    private String appHostname;
    private String playlistName;
    private String tickerName;
    private int updateIntervalInMin;

    private boolean restartOnCrash=true;
    private boolean shuffle=false;

    private static AppConfig sharedIns;

    public AppConfig(Context context){
        if (sharedIns==null) sharedIns=this;
        this.loadFromConfigFile(context);
    }


    public boolean isRestartOnCrash() {
        return restartOnCrash;
    }

    public boolean isShuffle() {
        return shuffle;
    }

    public static AppConfig getSharedInstance(){
        return sharedIns;
    }


    public String getAppVersion(){
        int versionCode = BuildConfig.VERSION_CODE;
        String versionName = BuildConfig.VERSION_NAME;
        return versionName;
    }

    private void loadFromConfigFile(Context context){

        File f = context.getExternalFilesDir(null);
        File extFile = f;//new File(new File(Environment.getDataDirectory(), "Android/data"), context.getPackageName());


        File configFile = new File(extFile, DEFAULT_CONFIG_FILENAME);

        if (extFile!=null){

            if (!extFile.exists()) extFile.mkdirs();

            InputStream is = null;


            //copy default config file to external storage
            if (!configFile.exists()){
                is =  context.getResources().openRawResource(R.raw.config);
                FileOutputStream out = null;

                try {
                    out= new FileOutputStream(configFile);
                    StreamUtils.copy(is, out);
                } catch (Exception e){
                    e.printStackTrace();
                }
                finally {
                    try {
                        is.close();
                        if (out!=null) out.close();
                    } catch (Exception e){
                        e.printStackTrace();
                    }
                }
            } else {
                Log.d("AppConfig", "Loaded existing config file.");
            }

            try{
                is = new FileInputStream(configFile);
            }catch (Exception e){
                e.printStackTrace();
            }

            if (is!=null){
                BufferedReader reader = new BufferedReader(new InputStreamReader(is));

                try{
                    String mLine = reader.readLine();
                    int index = 0;
                    while (mLine != null) {
                        if (index==0)this.appHostname = mLine;
                        if (index==1)this.serverUrl = mLine;
                        if (index==2)this.playlistName=mLine;
                        if (index==3)this.tickerName=mLine;
                        if (index==4)this.updateIntervalInMin= Integer.parseInt(mLine);
                        if (index==5)this.restartOnCrash= Integer.parseInt(mLine) > 0;
                        if (index==6)this.shuffle = Integer.parseInt(mLine) > 0;

                        index++;
                        mLine = reader.readLine();
                    }
                    reader.close();

                }catch (Exception e){
                    e.printStackTrace();
                }

                try {
                    is.close();
                } catch (Exception e){
                    e.printStackTrace();
                }
            }

        }

        if (serverUrl==null||serverUrl.length()==0)serverUrl=DEFAULT_SERVER_URL;
        if (appHostname==null || appHostname.length()==0) appHostname=DEFAULT_HOSTNAME;
        if (tickerName== null || tickerName.length()==0) tickerName = REQUEST_METHOD_TICKER;
        if (playlistName== null || playlistName.length()==0) playlistName = REQUEST_METHOD_PLAYLIST;
        if (updateIntervalInMin <= 0) updateIntervalInMin = DEFAULT_INTERVAL_IN_MIN;
    }
}
