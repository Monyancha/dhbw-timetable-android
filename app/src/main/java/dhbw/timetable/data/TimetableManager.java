package dhbw.timetable.data;

import android.app.Activity;
import android.app.Application;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.util.Log;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;

import dhbw.timetable.ActivityHelper;
import dhbw.timetable.R;
import dhbw.timetable.dialogs.ErrorDialog;
import dhbw.timetable.navfragments.notifications.alarm.AlarmSupervisor;
import dhbw.timetable.rapla.data.event.BackportAppointment;
import dhbw.timetable.rapla.data.time.TimelessDate;
import dhbw.timetable.rapla.date.DateUtilities;
import dhbw.timetable.rapla.parser.DataImporter;

import static dhbw.timetable.ActivityHelper.getActivity;

/**
 * Created by Hendrik Ulbrich (C) 2017
 */
public final class TimetableManager {

    private final static TimetableManager INSTANCE = new TimetableManager();

    private final Map<TimelessDate, ArrayList<BackportAppointment>> globalTimetables = new HashMap<>();
    private final Map<TimelessDate, ArrayList<BackportAppointment>> localTimetables = new HashMap<>();
    private boolean busy = false;
    private AsyncTask<Void, Void, Void> currentTask;

    private TimetableManager() {
    }

    public static TimetableManager getInstance() {
        return INSTANCE;
    }

    private static String getActiveTimetable(Application a) {
        SharedPreferences sharedPref = a.getSharedPreferences(
                a.getString(R.string.preference_file_key), Context.MODE_PRIVATE);
        for (String key : sharedPref.getAll().keySet()) {
            // filter for timetables
            if (key.startsWith("t#")) {
                return sharedPref.getString(key, "undefined");
            }
        }
        return "undefined";
    }

    private boolean areAppointmentsEqual(ArrayList<BackportAppointment> l1, ArrayList<BackportAppointment> l2) {
        if (l1.size() != l2.size()) return false;
        BackportAppointment a1, a2;
        for (int i = 0; i < l1.size(); i++) {
            a1 = l1.get(i);
            a2 = l2.get(i);
            if (!a1.toString().equals(a2.toString())) return false;
        }
        return true;
    }

    private boolean notificationNeeded(Application application, SharedPreferences sharedPref) {
        if (!secureFile(application)) {
            Log.i("TTM", "No offline globals to compare.");
            return false;
        }
        String changeCrit = sharedPref.getString("onChangeCrit", "None");
        Log.i("TTM", "Searching for changes. Criteria: " + changeCrit);
        Map<TimelessDate, ArrayList<BackportAppointment>> offlineTimetables;
        switch (changeCrit) {
            case "None":
                return false;
            case "Every change":
                offlineTimetables = loadOfflineGlobalsIntoList(application);

                for (TimelessDate date : offlineTimetables.keySet()) {
                    // Can only compare if available
                    if (globalTimetables.containsKey(date)) {
                        Log.d("COMP", "Comparing week " + DateUtilities.GERMAN_STD_SDATEFORMAT.format(date.getTime()));
                        if (!areAppointmentsEqual(offlineTimetables.get(date),
                                globalTimetables.get(date))) {
                            return true;
                        }
                    }
                }
                return false;
            case "One week ahead":
                TimelessDate thisWeek = new TimelessDate();
                TimelessDate nextWeek = new TimelessDate();
                DateUtilities.Backport.NextWeek(nextWeek);

                offlineTimetables = loadOfflineGlobalsIntoList(application);

                if (offlineTimetables.containsKey(thisWeek)) {
                    if (!areAppointmentsEqual(offlineTimetables.get(thisWeek),
                            globalTimetables.get(thisWeek))) {
                        return true;
                    }
                }
                if (offlineTimetables.containsKey(nextWeek)) {
                    if (!areAppointmentsEqual(offlineTimetables.get(nextWeek), globalTimetables.get(nextWeek))) {
                        return true;
                    }
                }
                return false;
        }
        Log.e("TTM", "Error! Wrong change crit: " + changeCrit);
        return false;
    }

    private static Uri getTone(SharedPreferences sharedPreferences) {
        String tone = sharedPreferences.getString("onChangeTone", "None");
        switch (tone) {
            case "None":
                return null;
            case "Default":
                return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        }
        Log.w("TONE", "Warning, invalid ringtone for on change notification: " + tone);
        return null;
    }

    private void handleChangePolicies(Application application) {
        SharedPreferences sharedPref = application.getSharedPreferences(
                application.getString(R.string.preference_file_key), Context.MODE_PRIVATE);
        Uri sound = getTone(sharedPref);
        if (notificationNeeded(application, sharedPref)) {
            Log.i("TTM", "Changes found. Would fire!");
            switch (sharedPref.getString("onChangeForm", "Banner")) {
                case "None":
                    if (sound != null) {
                        Ringtone r = RingtoneManager.getRingtone(application.getApplicationContext(), sound);
                        r.play();
                    }
                    break;
                case "Banner":
                    fireBanner(sound);
                    break;
            }

        } else {
            Log.i("TTM", "Change check negative -> Won't fire any notification for change.");
        }
    }

    private static void fireBanner(Uri sound) {
        final Activity curr = getActivity();
        if (curr != null) {
            NotificationCompat.Builder mBuilder =
                    new NotificationCompat.Builder(curr)
                            .setSmallIcon(R.mipmap.ic_launcher)
                            .setAutoCancel(true)
                            .setSound(sound)
                            .setLargeIcon(BitmapFactory.decodeResource(curr.getResources(), R.mipmap.ic_launcher_large))
                            .setContentTitle(curr.getResources().getString(R.string.app_name))
                            .setContentText("Your timetable changed!");

            if (sound != null) {
                mBuilder.setSound(sound);
            }

            // Creates an explicit intent for an Activity in your app
            Intent resultIntent = new Intent(curr, curr.getClass());

            // The stack builder object will contain an artificial back stack for the
            // started Activity.
            // This ensures that navigating backward from the Activity leads out of
            // your application to the Home screen.
            TaskStackBuilder stackBuilder = TaskStackBuilder.create(curr);
            // Adds the back stack for the Intent (but not the Intent itself)
            stackBuilder.addParentStack(curr.getClass());
            // Adds the Intent that starts the Activity to the top of the stack
            stackBuilder.addNextIntent(resultIntent);
            PendingIntent resultPendingIntent = stackBuilder
                    .getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
            mBuilder.setContentIntent(resultPendingIntent);
            NotificationManager mNotificationManager = (NotificationManager)
                    curr.getSystemService(Context.NOTIFICATION_SERVICE);
            // mId allows you to update the notification later on.
            if (mNotificationManager != null) {
                mNotificationManager.notify(1337, mBuilder.build());
            }
        }
    }

    private static boolean secureFile(Application application) {
        try {
            FileInputStream fis = application.openFileInput(application.getResources().getString(R.string.TIMETABLES_FILE));
            fis.close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @Deprecated
    public boolean isBusy() {
        return busy;
    }

    public Map<TimelessDate, ArrayList<BackportAppointment>> getGlobals() {
        return globalTimetables;
    }

    public AsyncTask<Void, Void, Void> getTask() {
        return currentTask;
    }

    public boolean isRunning() {
        return currentTask != null && (currentTask.getStatus() == AsyncTask.Status.RUNNING
                || currentTask.getStatus() == AsyncTask.Status.PENDING);
    }

    public ArrayList<BackportAppointment> getGlobalsAsList() {
        ArrayList<BackportAppointment> weeks = new ArrayList<>();
        for (ArrayList<BackportAppointment> c : globalTimetables.values()) {
            weeks.addAll(c);
        }
        return weeks;
    }

    public LinkedHashSet<BackportAppointment> getGlobalsAsSet() {
        LinkedHashSet<BackportAppointment> weeks = new LinkedHashSet<>();
        for (ArrayList<BackportAppointment> week : globalTimetables.values()) weeks.addAll(week);
        return weeks;
    }

    private ArrayList<BackportAppointment> getLocalsAsList() {
        ArrayList<BackportAppointment> weeks = new ArrayList<>();
        for (ArrayList<BackportAppointment> week : localTimetables.values()) weeks.addAll(week);
        return weeks;
    }

    /**
     * Downloads timetable contents from only on day into existing GLOBAL_TIMETABLES and writes
     * complete global data to file system
     */
    public void reorderSpecialGlobals(final Application application, final Runnable onSuccess, final ErrorCallback errorCallback, final TimelessDate date) {
        TimetableManager.this.busy = true;
        // DO NOT CLEAR GLOBALS ONLY LOCALS
        globalTimetables.keySet().removeAll(localTimetables.keySet());
        localTimetables.clear();
        currentTask = new AsyncTask<Void, Void, Void>() {
            boolean success = false;
            String errMSG = "";

            @Override
            protected Void doInBackground(Void... noArgs) {
                if (!isOnline()) {
                    errMSG = "No internet. Maybe there is a problem with your internet or with the rapla server.";
                    return null;
                }

                // Get the first timetable
                String timetable = getActiveTimetable(application);
                if (timetable.equals("undefined")) {
                    Log.w("TTM", "There is currently no timetable specified.");
                    return null;
                }
                Log.i("TTM", "Loading SPECIAL online globals for " + timetable);

                // Same start and end date
                TimelessDate startDate = (TimelessDate) date.clone();
                DateUtilities.Backport.Normalize(startDate);

                TimelessDate endDate = (TimelessDate) startDate.clone();

                Log.i("TTM", "REORDER algorithm for " + DateUtilities.GERMAN_STD_SDATEFORMAT.format(startDate.getTime()));

                // Run download algorithm for ArrayList LOCAL_TIMETABLES
                try {
                    Map<TimelessDate, ArrayList<BackportAppointment>> temp2 = DataImporter.Backport.ImportWeekRange(startDate, endDate, timetable);

                    for (GregorianCalendar cal : temp2.keySet()) {
                        localTimetables.put(new TimelessDate(cal), temp2.get(cal));
                    }

                    success = true;
                    globalTimetables.putAll(localTimetables);
                } catch (Exception e) {
                    StringWriter sw = new StringWriter();
                    PrintWriter pw = new PrintWriter(sw);
                    e.printStackTrace(pw);

                    errMSG = e.getMessage() + "\n" + sw.toString();
                    e.printStackTrace();
                }

                return null;
            }

            @Override
            protected void onPostExecute(Void result) {
                if (!success) {
                    Log.w("TTM", "Unable to receive online data");
                    errorCallback.onError(errMSG);
                    TimetableManager.this.busy = false;
                    return;
                }

                Log.i("TTM", "Successfully REORDERED SPECIAL global timetables");
                Log.d("TTM", TimetableManager.getInstance().serialRepresentation());
                // Update UI
                Log.i("TTM", "Updating UI...");
                onSuccess.run();
                Log.i("TTM", "Updated UI!");

                TimetableManager.this.busy = false;
            }
        };
        currentTask.execute();
    }

    /**
     * Downloads timetable contents into cleared GLOBAL_TIMETABLES and writes data to file system
     */
    public void updateGlobals(final Application application, final Runnable updater, final ErrorCallback errorCallback) {
        TimetableManager.this.busy = true;
        globalTimetables.clear();
        localTimetables.clear();
        currentTask = new AsyncTask<Void, Void, Void>() {
            boolean success = false, timetablePresent = true;
            String errMSG;

            @Override
            protected Void doInBackground(Void... noArgs) {
                if (!isOnline()) {
                    errMSG = "No internet. Maybe there is a problem with your internet or with the rapla server.";
                    return null;
                }


                // Get the first timetable
                String timetable = getActiveTimetable(application);
                if (timetable.equals("undefined")) {
                    Log.w("TTM", "There is currently no timetable specified.");
                    timetablePresent = false;
                    return null;
                }
                Log.i("TTM", "Loading online globals for " + timetable);

                // Get sync range from Preferences
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(application);

                TimelessDate startDate = new TimelessDate();
                DateUtilities.Backport.SubtractDays(startDate, Integer.parseInt(prefs.getString("sync_range_past", "1")) * 7);
                DateUtilities.Backport.Normalize(startDate);

                TimelessDate endDate = new TimelessDate();
                DateUtilities.Backport.AddDays(endDate, Integer.parseInt(prefs.getString("sync_range_future", "1")) * 7);
                DateUtilities.Backport.Normalize(endDate);

                Log.i("TTM", "Running algorithm from " + DateUtilities.GERMAN_STD_SDATEFORMAT.format(startDate.getTime()) + " to " + DateUtilities.GERMAN_STD_SDATEFORMAT.format(endDate.getTime()));

                // Run download algorithm for ArrayList LOCAL_TIMETABLES
                try {
                    Map<TimelessDate, ArrayList<BackportAppointment>> temp2 = DataImporter.Backport.ImportWeekRange(startDate, endDate, timetable);

                    for (GregorianCalendar cal : temp2.keySet()) {
                        globalTimetables.put(new TimelessDate(cal), temp2.get(cal));
                    }

                    success = true;
                } catch (Exception e) {
                    StringWriter sw = new StringWriter();
                    PrintWriter pw = new PrintWriter(sw);
                    e.printStackTrace(pw);

                    errMSG = e.getMessage() + "\n" + sw.toString();
                    e.printStackTrace();
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void result) {
                if (!success) {
                    TimetableManager.this.busy = false;
                    Log.w("TTM", "Unable to receive online data");
                    // If user is on board
                    if (timetablePresent) {
                        errorCallback.onError(errMSG);
                    }
                    return;
                }

                Log.i("TTM", "Successfully updated global timetables [" + globalTimetables.size() + "][" + getGlobalsAsList().size() + "]:");
                Log.d("TTM", serialRepresentation());
                // Update UI
                Log.i("TTM", "Updating UI...");
                updater.run();
                Log.i("TTM", "Updated UI!");

                AlarmSupervisor.getInstance().rescheduleAllAlarms(application.getApplicationContext());

                handleChangePolicies(application);

                // Update offline globals
                try {
                    FileOutputStream outputStream = application.openFileOutput(
                            application.getResources().getString(R.string.TIMETABLES_FILE), Context.MODE_PRIVATE);
                    outputStream.write(serialRepresentation().getBytes());
                    outputStream.close();
                } catch (IOException e) {
                    StringWriter sw = new StringWriter();
                    PrintWriter pw = new PrintWriter(sw);
                    e.printStackTrace(pw);

                    errMSG = e.getMessage() + "\n" + sw.toString();
                    e.printStackTrace();

                    // let user know about this error
                    Activity activity = ActivityHelper.getActivity();
                    if (activity != null) {
                        ErrorDialog.newInstance("ERROR", "Unable to update offline data", errMSG)
                                .show(activity.getFragmentManager(), "OFFERROR");
                    }
                }

                TimetableManager.this.busy = false;
            }
        };
        currentTask.execute();
    }

    /**
     * Loads the last downloaded timetables into GLOBAL_TIMETABLES
     */
    public void loadOfflineGlobals(Application application, Runnable updater) {
        // If no OfflineGlobals were found, try to load them from online
        if (!secureFile(application)) {
            Log.i("TTM", "No offline globals were found, checking online.");
            if (!TimetableManager.getInstance().isBusy()) {
                updateGlobals(application, updater, string -> {
                });
            } else {
                Log.i("ASYNC", "Tried to sync while manager was busy");
            }
            return;
        }
        Log.i("TTM", "Loading offline globals...");
        globalTimetables.clear();
        try {
            FileInputStream fis = application.openFileInput(
                    application.getResources().getString(R.string.TIMETABLES_FILE));
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(fis));

            String line;
            BackportAppointment a;
            while ((line = bufferedReader.readLine()) != null) {
                if (line.isEmpty()) continue;

                String[] aData = line.split("\t");
                String[] date = aData[0].split("\\.");
                TimelessDate g = new TimelessDate();
                g.set(Calendar.DAY_OF_MONTH, Integer.parseInt(date[0]));
                g.set(Calendar.MONTH, Integer.parseInt(date[1]) - 1);
                g.set(Calendar.YEAR, Integer.parseInt(date[2]));

                a = new BackportAppointment(aData[1], g, aData[2], aData[3], aData[4]);

                TimetableManager.getInstance().insertAppointment(globalTimetables, new TimelessDate(g), a);
            }
            Log.i("TTM", "Success!");
            bufferedReader.close();
        } catch (Exception e) {
            e.printStackTrace();

            Log.e("TTM", "FAILED!");
        }
        Log.i("TTM", "Updating UI...");
        updater.run();
        Log.i("TTM", "Done");
    }

    private boolean isOnline() {
        try {
            int timeoutMs = 1500;
            Socket sock = new Socket();
            SocketAddress sockaddr = new InetSocketAddress("8.8.8.8", 53);

            sock.connect(sockaddr, timeoutMs);
            sock.close();

            return true;
        } catch (IOException e) {
            return false;
        }
    }

    Map<TimelessDate, ArrayList<BackportAppointment>> getLocals() {
        return localTimetables;
    }

    public void insertAppointment(Map<TimelessDate, ArrayList<BackportAppointment>> globals, GregorianCalendar date, BackportAppointment a) {
        TimelessDate week = new TimelessDate(date);
        DateUtilities.Backport.Normalize(week);

        if (!globals.containsKey(week)) globals.put(week, new ArrayList<>());
        globals.get(week).add(a);
    }

    private Map<TimelessDate, ArrayList<BackportAppointment>> loadOfflineGlobalsIntoList(
            Application application) {
        Log.i("TTM", "Accessing offline globals...");
        Map<TimelessDate, ArrayList<BackportAppointment>> offlineAppointments = new HashMap<>();
        try {
            FileInputStream fis = application.openFileInput(
                    application.getResources().getString(R.string.TIMETABLES_FILE));
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(fis));

            String line;
            BackportAppointment a;
            while ((line = bufferedReader.readLine()) != null) {
                if (line.isEmpty()) continue;

                String[] aData = line.split("\t");
                String[] date = aData[0].split("\\.");
                TimelessDate g = new TimelessDate();
                g.set(Calendar.DAY_OF_MONTH, Integer.parseInt(date[0]));
                g.set(Calendar.MONTH, Integer.parseInt(date[1]) - 1);
                g.set(Calendar.YEAR, Integer.parseInt(date[2]));

                a = new BackportAppointment(aData[1], g, aData[2], aData[3], aData[4]);

                TimetableManager.getInstance().insertAppointment(offlineAppointments, (TimelessDate) g.clone(), a);
            }
            Log.i("TTM", "Success!");
            bufferedReader.close();
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);

            String errMSG = e.getMessage() + "\n" + sw.toString();
            e.printStackTrace();

            // let user know about this error
            Activity activity = ActivityHelper.getActivity();
            if (activity != null) {
                ErrorDialog.newInstance("ERROR", "Unable to import offline data. Is it corrupt?", errMSG)
                        .show(activity.getFragmentManager(), "OFFLOADERROR");
            }
            Log.e("TTM", "FAILED!");
        }
        return offlineAppointments;
    }

    private String serialRepresentation() {
        StringBuilder sb = new StringBuilder();
        for (TimelessDate week : globalTimetables.keySet()) {
            for (BackportAppointment a : globalTimetables.get(week)) {
                sb.append(a.toString()).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}
