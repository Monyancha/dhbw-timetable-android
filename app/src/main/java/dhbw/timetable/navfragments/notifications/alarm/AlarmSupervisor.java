package dhbw.timetable.navfragments.notifications.alarm;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import dhbw.timetable.ActivityHelper;
import dhbw.timetable.R;
import dhbw.timetable.data.Appointment;
import dhbw.timetable.data.DateHelper;
import dhbw.timetable.data.TimelessDate;
import dhbw.timetable.data.TimetableManager;

/**
 * Created by Hendrik Ulbrich (C) 2017
 */
public final class AlarmSupervisor {
    private static final AlarmSupervisor INSTANCE = new AlarmSupervisor();
    private static final int SNOOZE_DURATION = 1000 * 60 * 5; // ms = 5min

    private AlarmManager manager;
    private Map<TimelessDate, PendingIntent> alarms = new HashMap<>();
    private Ringtone ringtone;
    private boolean rescheduling;

    private AlarmSupervisor() {}

    public static AlarmSupervisor getInstance() {
        return INSTANCE;
    }

    public void initialize(Context context) {
        manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    }

    public void setRingtone(Activity activity, Uri notification) {
        ringtone = RingtoneManager.getRingtone(activity, notification);
    }

    public Ringtone getRingtone() {
        return ringtone;
    }

    void playRingtone() {
        ringtone.play();
    }

    void stopRingtone() {
        ringtone.stop();
    }

    public void rescheduleAllAlarms(Context context) {
        if(rescheduling) {
            Log.i("ALARM", "Request denied. Already rescheduling...");
            return;
        }
        rescheduling = true;
        Log.i("ALARM", "Rescheduling all alarms...");
        cancelAllAlarms();

        SharedPreferences sharedPref = context.getSharedPreferences(
                context.getString(R.string.preference_file_key), Context.MODE_PRIVATE);
        if(sharedPref.getBoolean("alarmOnFirstEvent", false)) {
            Map<TimelessDate, ArrayList<Appointment>> globals = TimetableManager.getInstance().getGlobals();
            ArrayList<Appointment> appointmentsOfWeek;
            TimelessDate tempDay;
            Appointment firstAppointment;
            for (TimelessDate week : globals.keySet()) {
                appointmentsOfWeek = globals.get(week);
                for (int day = 0; day < 5; day++) {
                    tempDay = (TimelessDate) week.clone();
                    DateHelper.AddDays(tempDay, day);
                    firstAppointment = DateHelper.GetFirstAppointmentOfDay(appointmentsOfWeek, tempDay);
                    // Only if there are appointments
                    if (firstAppointment != null) {
                        // apply shifting
                        int shifter = 0;
                        if (sharedPref.getBoolean("shift", false)) {
                            switch (sharedPref.getString("alarmFirstShift", "15min")) {
                                case "15min":
                                    shifter = 1000 * 60 * 15;
                                    break;
                                case "30min":
                                    shifter = 1000 * 60 * 30;
                                    break;
                                case "45min":
                                    shifter = 1000 * 60 * 45;
                                    break;
                                case "1h":
                                    shifter = 1000 * 60 * 60;
                                    break;
                                case "1,5h":
                                    shifter = 1000 * 60 * 90;
                                    break;
                                case "2h":
                                    shifter = 1000 * 60 * 120;
                                    break;
                            }
                        }
                        GregorianCalendar afterShift = (GregorianCalendar) firstAppointment.getStartDate().clone();
                        afterShift.setTimeInMillis(afterShift.getTimeInMillis() + shifter);
                        scheduleAlarm(firstAppointment.getStartDate(), context);
                    }
                }
            }
            Log.i("ALARM", "Rescheduled " + alarms.size() + " alarms");
        } else {
            Log.i("ALARM", "Not needed. Alarm not active.");
        }
        rescheduling = false;
    }

    private void scheduleAlarm(GregorianCalendar date, Context context) {
        Log.d("ALARM", "Scheduling alarm...");
        PendingIntent p = PendingIntent.getBroadcast(context,
                0, new Intent(context, AlarmReceiver.class), 0);
        alarms.put(new TimelessDate(date), p);
        manager.setExact(AlarmManager.RTC_WAKEUP,
                date.getTimeInMillis(),
                p);

        Log.d("ALARM", "Alarm ready for "
                + new SimpleDateFormat("HH:mm dd.MM.yyyy", Locale.GERMANY).format(date.getTime()));
    }

    public void cancelAlarm(GregorianCalendar date) {
        Log.i("ALARM", "Canceling " + new SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).format(date.getTime()) +  "...");
        PendingIntent p = alarms.get(new TimelessDate(date));
        if(p == null) {
            Log.e("ALARM", "Unable to find intent for "
                    + new SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).format(date.getTime()));
            return;
        }
        manager.cancel(p);
        Log.i("ALARM", "Alarm canceled");
    }

    void snooze(Context context) {
        Log.i("ALARM", "Snoozing current alarm...");
        TimelessDate today = new TimelessDate();
        PendingIntent p = alarms.get(today);
        if(p == null) {
            Log.e("ALARM", "Unable to find todays intent for "
                    + new SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).format(today.getTime()));
            return;
        }
        manager.cancel(p);

        // Reschedule
        GregorianCalendar later = (GregorianCalendar) Calendar.getInstance() ;
        later.setTimeInMillis(later.getTimeInMillis() + SNOOZE_DURATION);
        scheduleAlarm(later, context);

        Log.i("ALARM", "Alarm snoozed");
    }

    void dispose() {
        Log.i("ALARM", "Disposing current alarm...");
        TimelessDate today = new TimelessDate();
        PendingIntent p = alarms.get(today);
        if(p == null) {
            Log.e("ALARM", "Unable to find todays intent for "
                    + new SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).format(today.getTime()));
            return;
        }
        manager.cancel(p);
        Log.i("ALARM", "Alarm disposed");
    }

    private void cancelAllAlarms() {
        Log.i("ALARM", "Canceling all alarms...");
        for(TimelessDate d : alarms.keySet()) alarms.get(d).cancel();
        alarms.clear();
        Log.i("ALARM", "All alarms canceled");
    }

    @Deprecated
    boolean isShowing() {
        return ActivityHelper.getActivity() instanceof AlarmActivity;
    }
}
