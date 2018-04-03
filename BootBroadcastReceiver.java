package net.finarx.twc.buskiosk;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;


public class BootBroadcastReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent)
    {
        //Toast.makeText(context, intent.getAction() , Toast.LENGTH_SHORT).show();
        if (intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED))
        {
            Log.d("Dibosys","Start Activity on Boot");
            Toast.makeText(context, "Starting Service" , Toast.LENGTH_SHORT).show();

            Intent i = new Intent(context, FullscreenMainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(i);

            /*
            AlarmBroadcastReceiver alarm = new AlarmBroadcastReceiver();
            alarm.SetAlarm(context);
            */
        }
    }

}
