package dhbw.timetable.navfragments.today;

import android.app.Activity;
import android.content.Intent;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.util.LinkedHashSet;

import dhbw.timetable.ActivityHelper;
import dhbw.timetable.CourseDetailsActivity;
import dhbw.timetable.R;
import dhbw.timetable.data.AgendaAppointment;
import dhbw.timetable.data.TimetableManager;

/**
 * Created by Hendrik Ulbrich (C) 2017
 */
class AgendaAppointmentAdapter extends RecyclerView.Adapter<AgendaAppointmentAdapter.MyViewHolder> {

    private LinkedHashSet<AgendaAppointment> appointments;

    class MyViewHolder extends RecyclerView.ViewHolder {
        TextView time, title;

        MyViewHolder(View view) {
            super(view);
            time = view.findViewById(R.id.courseTime);
            title = view.findViewById(R.id.courseTitle);
        }
    }

    AgendaAppointmentAdapter(LinkedHashSet<AgendaAppointment> appointments) {
        this.appointments = appointments;
    }

    @Override
    public MyViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        final View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.agenda_list_row, parent, false);
        final RecyclerView mRecyclerView = (RecyclerView) parent;

        view.setOnClickListener(child -> {
            if (!TimetableManager.getInstance().isRunning()) {
                int itemPos = mRecyclerView.getChildLayoutPosition(child);
                AgendaAppointment item = (AgendaAppointment) appointments.toArray()[itemPos];
                if (!item.isBreak()) {
                    Activity activity = ActivityHelper.getActivity();
                    if (activity != null) {
                        Intent detailsIntent = new Intent(activity, CourseDetailsActivity.class);
                        detailsIntent.putExtra("startTime", item.getStartTime());
                        detailsIntent.putExtra("endTime", item.getEndTime());
                        detailsIntent.putExtra("title", item.getTitle());
                        detailsIntent.putExtra("info", item.getInfo());
                        activity.startActivity(detailsIntent);
                    }
                }
            } else {
                Toast.makeText(view.getContext(), "I'm currently busy, sorry!", Toast.LENGTH_SHORT).show();
            }
        });

        return new MyViewHolder(view);
    }

    @Override
    public void onBindViewHolder(MyViewHolder holder, int position) {
        AgendaAppointment a = (AgendaAppointment) appointments.toArray()[position];
        // AgendaAppointment a = appointments.get(position);
        holder.time.setText(a.getStartTime());
        holder.title.setText(a.getTitle());

        holder.time.setTextAppearance(holder.time.getContext(),
                (position == 0 || position == (appointments.size() - 1)) ?
                        R.style.AgendaTimeMain : R.style.AgendaTime);

        float scale = holder.title.getResources().getDisplayMetrics().density;
        int dpAsPixels = (int) (10 * scale + 0.5f);
        holder.title.setBackgroundResource(a.isBreak() ?
                R.drawable.break_background : R.drawable.course_background);
        holder.title.setPadding(dpAsPixels, 0, dpAsPixels, 0);
    }

    @Override
    public int getItemCount() {
        return appointments.size();
    }
}