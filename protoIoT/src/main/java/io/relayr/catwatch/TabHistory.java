package io.relayr.catwatch;

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
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import butterknife.ButterKnife;
import butterknife.InjectView;
import io.relayr.java.helper.HistoryHelper;
import io.relayr.java.helper.observer.SimpleObserver;
import io.relayr.java.model.AccelGyroscope;
import io.relayr.java.model.history.History;
import io.relayr.java.model.history.HistoryPoint;
import io.relayr.java.model.history.HistoryResult;
import io.relayr.catwatch.proto.R;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;

public class TabHistory extends Fragment {

    private static final int MAX_POINTS = 5000;
    private static final int MAX_TIME = 30 * 60 * 1000;//in milliseconds
    private static final int DIFF = MAX_TIME / MAX_POINTS;

    @InjectView(R.id.chart) LineChart mChart;

    private List<String> mXAxis;
    private Gson mGson = new Gson();

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
        initGraph(mChart, 0, 1);

        getData();
        Observable.interval(20, TimeUnit.SECONDS)
                .subscribe(new SimpleObserver<Long>() {
                    @Override public void error(Throwable e) {
                        Log.e("TabHistory", "Problem refreshing");
                        e.printStackTrace();
                    }

                    @Override public void success(Long aLong) {
                        getData();
                    }
                });
    }

    private void getData() {
        HistoryHelper.init(Constants.PROTO_IOT__DEVICE_ID)
                .getLatest(30, TimeUnit.MINUTES)
                .timeout(Constants.DEFAULT_TIMEOUT, TimeUnit.SECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new SimpleObserver<History>() {
                    @Override public void error(Throwable e) {
                        Log.e("TabHistory", "Problem loading data");
                        e.printStackTrace();
                    }

                    @Override public void success(History history) {
                        if (history.getResults() == null || history.getResults().isEmpty()) return;
                        for (HistoryResult result : history.getResults()) {
                            if (result.getMeaning().equals("acceleration"))
                                setData(result.getPoints());
                        }

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

        List<Entry> values = new ArrayList<>();

        long mFirstPoint;
        mFirstPoint = System.currentTimeMillis() - MAX_TIME;

        for (int i = 0; i < readings.size(); i++) {
            final HistoryPoint reading = readings.get(i);
            final int index = (int) ((reading.getTimestamp() - mFirstPoint) / DIFF);
            if (index < 0) continue;
            if (index >= MAX_POINTS) break;

            final AccelGyroscope.Acceleration acceleration = mGson.fromJson(reading.getValue().toString(), AccelGyroscope.Acceleration.class);

            final int value = Math.abs(acceleration.x) > 6 ? 1 : 0;
            values.add(new Entry(value, index));
        }
        LineData data = new LineData(mXAxis, createDataSet("dig", values,
                R.color.colorAccent, R.color.colorAccent));

        mChart.setData(data);
        mChart.invalidate();
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
}
