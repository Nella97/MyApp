package net.finarx.twc.buskiosk;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;

import java.io.File;
import java.io.RandomAccessFile;
import java.text.DecimalFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class MediaHandler {

    public static Context appContext;

    public static String getExternalStoragePath()
    {
        File f = appContext.getExternalFilesDir(null);
        return f!=null? f.getAbsolutePath() : getInternalStoragePath();
        //return Environment.getExternalStorageDirectory().toString();
    }
    public static String getInternalStoragePath()
    {
        return appContext.getFilesDir().getAbsolutePath();
    }
    public static String getCacheDirectory(){
        return appContext.getCacheDir().getAbsolutePath();
    }

    /* Checks if external storage is available for read and write */
    public static boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }

    public static String getDeviceName() {
        String manufacturer = Build.MANUFACTURER;
        String model = Build.MODEL;
        if (model.startsWith(manufacturer)) {
            return capitalize(model);
        } else {
            return capitalize(manufacturer) + " " + model;
        }
    }

    private static String capitalize(String s) {
        if (s == null || s.length() == 0) {
            return "";
        }
        char first = s.charAt(0);
        if (Character.isUpperCase(first)) {
            return s;
        } else {
            return Character.toUpperCase(first) + s.substring(1);
        }
    }

    /* Checks if external storage is available to at least read */
    public static boolean isExternalStorageReadable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state) ||
                Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            return true;
        }
        return false;
    }

    public static String getAvailableInternalMemorySize() {
        File path = Environment.getDataDirectory();
        StatFs stat = new StatFs(path.getPath());
        long blockSize = stat.getBlockSize();
        long availableBlocks = stat.getAvailableBlocks();
        return ""+(availableBlocks * blockSize);
    }

    public static String getTotalInternalMemorySize() {
        File path = Environment.getDataDirectory();
        StatFs stat = new StatFs(path.getPath());
        long blockSize = stat.getBlockSize();
        long totalBlocks = stat.getBlockCount();
        return ""+(totalBlocks * blockSize);
    }

    public static String getAvailableExternalMemorySize() {
        if (isExternalStorageReadable()) {
            File path = Environment.getExternalStorageDirectory();
            StatFs stat = new StatFs(path.getPath());
            long blockSize = stat.getBlockSize();
            long availableBlocks = stat.getAvailableBlocks();
            return ""+(availableBlocks * blockSize);
        } else {
            return null;
        }
    }

    public static String getTotalExternalMemorySize() {
        if (isExternalStorageReadable()) {
            File path = Environment.getExternalStorageDirectory();
            StatFs stat = new StatFs(path.getPath());
            long blockSize = stat.getBlockSize();
            long totalBlocks = stat.getBlockCount();
            return "" + (totalBlocks * blockSize);
        } else {
            return null;
        }
    }

    public static String formatSize(long size) {
        String suffix = null;

        if (size >= 1024) {
            //suffix = "KB";
            size /= 1024;
            if (size >= 1024) {
                //suffix = "MB";
                size /= 1024;
            }
        }

        StringBuilder resultBuffer = new StringBuilder(Long.toString(size));

        int commaOffset = resultBuffer.length() - 3;
        while (commaOffset > 0) {
            resultBuffer.insert(commaOffset, ',');
            commaOffset -= 3;
        }

        if (suffix != null) resultBuffer.append(suffix);
        return resultBuffer.toString();
    }

    public String getTotalRAM() {

        RandomAccessFile reader = null;
        String load = null;
        DecimalFormat twoDecimalForm = new DecimalFormat("#.##");
        double totRam = 0;
        String lastValue = "";
        try {
            reader = new RandomAccessFile("/proc/meminfo", "r");
            load = reader.readLine();

            // Get the Number value from the string
            Pattern p = Pattern.compile("(\\d+)");
            Matcher m = p.matcher(load);
            String value = "";
            while (m.find()) {
                value = m.group(1);
                // System.out.println("Ram : " + value);
            }
            reader.close();

            totRam = Double.parseDouble(value);
            // totRam = totRam / 1024;

            double mb = totRam / 1024.0;
            double gb = totRam / 1048576.0;
            double tb = totRam / 1073741824.0;

            /*
            if (tb > 1) {
                lastValue = twoDecimalForm.format(tb);//.concat(" TB");
            } else if (gb > 1) {
                lastValue = twoDecimalForm.format(gb);//.concat(" GB");
            } else if (mb > 1) {
                lastValue = twoDecimalForm.format(mb);//.concat(" MB");
            } else {
                lastValue = twoDecimalForm.format(totRam);//.concat(" KB");
            }
            */
            lastValue = "" + (long)totRam;


        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            // Streams.close(reader);
        }

        return lastValue;
    }

    public static boolean isNetworkAvailable()
    {
        ConnectivityManager connectivityManager
                = (ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

}
