package net.finarx.twc.buskiosk;

import android.util.Log;
import android.widget.Toast;

import net.finarx.twc.buskiosk.proxy.AbstractProxyServer;
import net.finarx.twc.buskiosk.proxy.UsbProxyServer;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.StringTokenizer;


public class USBContentUpdateManager extends ContentUpdateManager {

    public int getDefaultTimerInterval() {
        return 5;
    }



    public int getDefaultUpdateInterval(){
        return 60;
    }

    public boolean canConnect(){
        try {
            ArrayList<String> storagePaths = new ArrayList<>();
            storagePaths.add("/mnt/usb_storage/USB_DISK1/udisk0");
            //storagePaths.add("/mnt/usb_storage");
            //storagePaths.add("/storage/UsbDriveA");
            //storagePaths.add("/storage/USBstorage1");
            //this.printUSBPaths("/mnt");

            String path = storagePaths.get(0);

            Process process = Runtime.getRuntime().exec("ls " + path);
            BufferedReader buffer = new BufferedReader(new InputStreamReader(process.getInputStream()));

            ArrayList<String > listOfFiles = new ArrayList<>();

            try {
                String line;
                while ((line = buffer.readLine()) != null) {
                    listOfFiles.add(line);
                    //Log.d("Dibosys" ,"USB: "+ line);
                }
            } catch (Exception e){

            }

            if (listOfFiles.size()>0) {
                UsbProxyServer.usbPath = path;
            } else {
                UsbProxyServer.usbPath = "";
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return UsbProxyServer.usbPath!=null && UsbProxyServer.usbPath.length()>0;
    }

    public DownloadPlayableContentQueueHandler setupDownloadQueueHandler()
    {
        DownloadPlayableContentQueueHandler queueHandler= super.setupDownloadQueueHandler();
        queueHandler.justUseInternalStorage = true;
        return queueHandler;
    }


    public void printUSBPaths(String path){

        try{
            Process process = Runtime.getRuntime().exec("ls " + path);
            BufferedReader buffer = new BufferedReader(new InputStreamReader(process.getInputStream()));

            ArrayList<String > listOfNewPaths = new ArrayList<>();

            String line;
            while ((line = buffer.readLine()) != null) {
                String nPath = path + "/"+ line;
                listOfNewPaths.add(nPath);
                //Log.d("Dibosys" ,"USB: "+ nPath);
            }

            for (String npath : listOfNewPaths){
                this.printUSBPaths(npath);
            }

        }catch (Exception e){

        }
    }

    public void beginUpdate(){
        super.beginUpdate();
    }

    public void didFail(Error e) {
        super.didFail(e);
    }

    public void callCompletionHanlders(boolean success, Error e) {
        super.callCompletionHanlders(success, e);
    }

    public AbstractProxyServer getServerInstance()
    {
        return UsbProxyServer.getSharedInstance(this.getContext());
    }

}
