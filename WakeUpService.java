package net.finarx.twc.buskiosk;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/**
 * Created by Julien on 01.06.16.
 */
public class WakeUpService extends Service {

    AlarmBroadcastReceiver alarm = new AlarmBroadcastReceiver();

    public void onCreate()
    {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        alarm.SetAlarm(this);
        return START_STICKY;
    }

    @Override
    public void onStart(Intent intent, int startId)
    {
        alarm.SetAlarm(this);
    }

    @Override
    public IBinder onBind(Intent intent)
    {
        return null;
    }
}
