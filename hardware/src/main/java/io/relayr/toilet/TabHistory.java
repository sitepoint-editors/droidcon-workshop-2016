package io.relayr.toilet;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import butterknife.ButterKnife;
import butterknife.InjectView;
import io.relayr.hardware.R;
import io.relayr.java.helper.HistoryHelper;
import io.relayr.java.model.history.History;
import io.relayr.java.model.history.HistoryPoint;
import io.relayr.java.model.history.HistoryResult;
import rx.Observable;
import rx.Observer;
import rx.android.schedulers.AndroidSchedulers;

public class TabHistory extends Fragment {

    private static final int MAX_POINTS = 5000;
    private static final int MAX_TIME = 25;

    @InjectView(R.id.chart_door) LineChart mChartDoor;
    @InjectView(R.id.chart_door_lock) LineChart mChartDoorLock;
    @InjectView(R.id.chart_presence) LineChart mChartPresence;
    private List<String> mXAxis;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.tab_history, container, false);
        ButterKnife.inject(this, view);

        mXAxis = new ArrayList<>(MAX_POINTS);
        for (int i = 0; i < MAX_POINTS; i++) mXAxis.add("");

        return view;
    }

    @Override public void onResume() {
        super.onResume();
        initGraph(mChartDoor, 0, 100);
        initGraph(mChartDoorLock, 0, 100);
        initGraph(mChartPresence, 0, 100);

        getData();
        Observable.interval(20, TimeUnit.SECONDS)
                .subscribe(new Observer<Long>() {
                    @Override public void onCompleted() {}

                    @Override public void onError(Throwable e) {
                        Log.e("HISTORY", "Problem refreshing");
                        e.printStackTrace();
                    }

                    @Override public void onNext(Long aLong) {
                        getData();
                    }
                });
    }

    private void initGraph(LineChart chart, int min, int max) {
        chart.setDescription("");
        chart.setTouchEnabled(false);
        chart.setDragEnabled(false);
        chart.setScaleEnabled(false);
        chart.setPinchZoom(false);

        chart.getLegend().setEnabled(false);
        chart.getAxisRight().setEnabled(true);

        initAxis(chart.getAxisLeft(), min, max);
        initAxis(chart.getAxisRight(), min, max);
    }


    private void initAxis(YAxis axis, int min, int max) {
        axis.setTextColor(ContextCompat.getColor(getContext(), R.color.colorAccent));
        axis.setAxisLineColor(ContextCompat.getColor(getContext(), R.color.colorAccent));
        axis.setAxisMaxValue(max);
        axis.setAxisMinValue(min);
        axis.setStartAtZero(min == 0);
    }

    private void setData(List<HistoryPoint> readings) {
        if (readings == null) return;

        long mDiff;
        long mFirstPoint;

        List<Entry> values = new ArrayList<>();

        mFirstPoint = System.currentTimeMillis() - MAX_TIME * 60 * 1000;
        mDiff = MAX_TIME * 60 * 1000 / MAX_POINTS;

        for (int i = 0; i < readings.size(); i++) {
            final HistoryPoint reading = readings.get(i);
            final int index = (int) ((reading.getTimestamp() - mFirstPoint) / mDiff);
            if (index < 0) continue;
            if (index >= MAX_POINTS) break;

            final int value = ((Number) reading.getValue()).floatValue() > 200f ? 95 : 2;
            values.add(new Entry(value, index));
        }
        LineData data = new LineData(mXAxis, createDataSet("dig", values,
                R.color.colorAccent, R.color.colorAccent));

        mChartDoorLock.setData(data);
        mChartDoorLock.invalidate();
    }

    private void setData(LineChart chart, List<HistoryPoint> readings) {
        if (readings == null) return;

        long mDiff;
        long mFirstPoint;

        List<Entry> values = new ArrayList<>();

        mFirstPoint = System.currentTimeMillis() - MAX_TIME * 60 * 1000;
        mDiff = MAX_TIME * 60 * 1000 / MAX_POINTS;

        for (int i = 0; i < readings.size(); i++) {
            final HistoryPoint reading = readings.get(i);
            final int index = (int) ((reading.getTimestamp() - mFirstPoint) / mDiff);
            if (index < 0) continue;
            if (index >= MAX_POINTS) break;

            final int value = (Boolean) reading.getValue() ? 95 : 2;
            values.add(new Entry(value, index));
        }
        LineData data = new LineData(mXAxis, createDataSet("dig", values,
                R.color.colorAccent, R.color.colorAccent));

        chart.setData(data);
        chart.invalidate();
    }

    private LineDataSet createDataSet(String name, List<Entry> entrys, int dotColor, int lineColor) {
        LineDataSet set = new LineDataSet(entrys, name);
        set.setColor(ContextCompat.getColor(getContext(), lineColor));
        set.setCircleColor(ContextCompat.getColor(getContext(), dotColor));
        set.setLineWidth(1f);
        set.setDrawCircleHole(false);
        set.setDrawValues(false);
        set.setCircleRadius(2);
        set.setValueTextColor(ContextCompat.getColor(getContext(), R.color.colorAccent));
        set.setFillColor(ContextCompat.getColor(getContext(), dotColor));
        return set;
    }

    public void getData() {
        HistoryHelper.init(MainActivity.DEVICE_ID)
                .getLatest(MAX_TIME, TimeUnit.MINUTES)
                .timeout(15, TimeUnit.SECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<History>() {
                    @Override public void onCompleted() {}

                    @Override public void onError(Throwable e) {
                        Log.e("HISTORY", "Problem");
                        e.printStackTrace();
                    }

                    @Override public void onNext(History history) {
                        if (history.getResults() == null || history.getResults().isEmpty()) return;
                        for (HistoryResult result : history.getResults()) {
                            if (result.getMeaning().equals("digital0"))
                                setData(mChartDoor, result.getPoints());
                            if (result.getMeaning().equals("magnetometer"))
                                setData(result.getPoints());
                            if (result.getMeaning().equals("digital1"))
                                setData(mChartPresence, result.getPoints());
                        }

                    }
                });
    }
}
