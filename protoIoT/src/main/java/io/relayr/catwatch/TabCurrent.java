package io.relayr.catwatch;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import butterknife.ButterKnife;
import butterknife.InjectView;
import io.relayr.android.RelayrSdk;
import io.relayr.java.helper.observer.SimpleObserver;
import io.relayr.java.model.AccelGyroscope;
import io.relayr.java.model.Device;
import io.relayr.java.model.User;
import io.relayr.java.model.action.Command;
import io.relayr.java.model.action.Reading;
import io.relayr.catwatch.proto.R;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.subscriptions.Subscriptions;

public class TabCurrent extends Fragment {

    @InjectView(R.id.image) ImageView mStateImg;
    @InjectView(R.id.state) TextView mStateText;

    private Subscription mUserSubs = Subscriptions.empty();
    private Subscription mDataSubs = Subscriptions.empty();

    private Device mDevice;
    private Gson mGson = new Gson();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.tab_current, container, false);
        ButterKnife.inject(this, view);
        return view;
    }

    @Override public void onResume() {
        super.onResume();
        if (RelayrSdk.isUserLoggedIn()) loadUserInfo();
    }

    @Override public void onPause() {
        super.onPause();
        if (RelayrSdk.isUserLoggedIn()) unSubscribeAll();
    }

    //Before starting load user object. User object can be used to get all other entities.
    private void loadUserInfo() {
        mUserSubs = RelayrSdk.getUser()
                .timeout(Constants.DEFAULT_TIMEOUT, TimeUnit.SECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new SimpleObserver<User>() {
                    @Override public void error(Throwable e) {
                        Log.e("TabCurrent", "loadUserInfo error");
                        e.printStackTrace();
                        Toast.makeText(getContext(), R.string.problem_loading_user, Toast.LENGTH_SHORT).show();
                    }

                    @Override public void success(User user) {
                        String hello = String.format(getString(R.string.hello), user.getName());
                        Toast.makeText(getContext(), hello, Toast.LENGTH_SHORT).show();
                        loadProtoIotDevice(user);
                    }
                });
    }

    //Use user object to get ProtoIoT smartphone device owned by user.
    private void loadProtoIotDevice(final User user) {
        user.getDevice(Constants.PROTO_IOT__DEVICE_ID)
                .timeout(Constants.DEFAULT_TIMEOUT, TimeUnit.SECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new SimpleObserver<Device>() {
                    @Override public void error(Throwable e) {
                        Log.e("TabCurrent", "loadProtoIotDevice error");
                        e.printStackTrace();
                        Toast.makeText(getContext(), R.string.problem_loading_device_data, Toast.LENGTH_SHORT).show();
                    }

                    @Override public void success(Device device) {
                        if (device == null) {
                            Log.e("TabCurrent", "loadProtoIotDevice - device doesn't exist.");
                            Toast.makeText(getContext(), R.string.problem_no_device, Toast.LENGTH_SHORT).show();
                        } else {
                            mDevice = device;
                            subscribeToProtoIotData();
                        }
                    }
                });
    }

    //Use fetched device object to subscribe to devices data.
    private void subscribeToProtoIotData() {
        if (mDevice == null) return;

        mDevice.subscribeToCloudReadings()
                .timeout(Constants.DEFAULT_TIMEOUT, TimeUnit.SECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new SimpleObserver<Reading>() {
                    @Override public void error(Throwable e) {
                        Toast.makeText(getContext(), R.string.problem_loading_data, Toast.LENGTH_SHORT).show();
                        Log.e("TabCurrent", "subscribeToProtoIotData error");
                        e.printStackTrace();

                        if (e instanceof TimeoutException) subscribeToProtoIotData();
                    }

                    @Override public void success(Reading reading) {
                        if (reading.meaning.equals("acceleration")) {
                            final AccelGyroscope.Acceleration accel = mGson.fromJson(reading.value.toString(), AccelGyroscope.Acceleration.class);
                            showCatState(Math.abs(accel.x) > 6);
                        }
                    }
                });
    }

    //Use this method to send a command back to ProtoIoT smartphone app
    private void sendCommandToProtoIot() {
        final String commandName = "vibration"; //possible commands: vibration, flashlight, playSound
        final boolean commandValue = true; //possible values: true, false :)

        mDevice.sendCommand(new Command("", commandName, commandValue))
                .subscribe(new SimpleObserver<Void>() {
                    @Override public void error(Throwable e) {
                        Log.e("TabCurrent", "sendCommandToProtoIot error");
                        e.printStackTrace();
                    }

                    @Override public void success(Void aVoid) {
                        Log.i("TabCurrent", "sendCommandToProtoIot: " + commandName + " -> " + commandValue);
                    }
                });
    }

    private void showCatState(boolean current) {
        mStateImg.setImageResource(current ? R.drawable.cat_won : R.drawable.cat_warning);
        mStateText.setText(current ? R.string.evil : R.string.innocence);
    }

    private void unSubscribeAll() {
        if (!mUserSubs.isUnsubscribed())
            mUserSubs.unsubscribe();

        if (!mDataSubs.isUnsubscribed())
            mDataSubs.unsubscribe();

        if (mDevice != null)
            RelayrSdk.getWebSocketClient().unSubscribe(mDevice.getId());
    }
}
