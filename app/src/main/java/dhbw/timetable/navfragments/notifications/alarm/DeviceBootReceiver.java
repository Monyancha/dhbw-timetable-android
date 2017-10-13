package dhbw.timetable.navfragments.notifications.alarm;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import dhbw.timetable.data.TimetableManager;

public class DeviceBootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(final Context context, final Intent intent) {
        if (intent.getAction().equals("android.intent.action.BOOT_COMPLETED")) {
            Log.i("BOOT", "Boot completed. Loading offline globals and resetup alarms");
            TimetableManager.getInstance().loadOfflineGlobals((Application) context.getApplicationContext(), new Runnable() {
                @Override
                public void run() {
                    AlarmSupervisor.getInstance().initialize(context.getApplicationContext());
                    AlarmSupervisor.getInstance().rescheduleAllAlarms(context.getApplicationContext());
                }
            });
        }
    }
}