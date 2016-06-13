package io.relayr.toilet;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.concurrent.TimeUnit;

import butterknife.ButterKnife;
import butterknife.InjectView;
import io.relayr.android.RelayrSdk;
import io.relayr.hardware.R;
import io.relayr.java.model.Device;
import io.relayr.java.model.User;
import io.relayr.java.model.action.Reading;
import rx.Observer;
import rx.Subscriber;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.subscriptions.Subscriptions;

public class TabCurrent extends Fragment {

    @InjectView(R.id.image) ImageView mStateImg;
    @InjectView(R.id.state) TextView mStateText;

    private Subscription mDataSubs = Subscriptions.empty();
    private Subscription mUserInfoSubscription = Subscriptions.empty();

    private boolean mStateDoor;
    private boolean mStatePresence;

    private Device mDevice;

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

    private void loadUserInfo() {
        mUserInfoSubscription = RelayrSdk.getUser()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Subscriber<User>() {
                    @Override public void onCompleted() {}

                    @Override
                    public void onError(Throwable e) {
                        Log.e("TabCurrent", "Problem loading user data");
                        e.printStackTrace();
                        Toast.makeText(getContext(), R.string.problem_loading_user, Toast.LENGTH_SHORT).show();
                    }

                    @Override public void onNext(User user) {
                        String hello = String.format(getString(R.string.hello), user.getName());
                        Toast.makeText(getContext(), hello, Toast.LENGTH_SHORT).show();

                        getDevice(user);
                    }
                });
    }

    private void getDevice(final User user) {
        user.getDevice(MainActivity.DEVICE_ID)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<Device>() {
                    @Override public void onCompleted() {}

                    @Override public void onError(Throwable e) {
                        Log.e("TabCurrent", "Problem loading device data");
                        e.printStackTrace();
                        Toast.makeText(getContext(), R.string.problem_loading_device_data, Toast.LENGTH_SHORT).show();
                    }

                    @Override public void onNext(Device device) {
                        if (device == null) {
                            Log.e("TabCurrent", "Device with ID " + MainActivity.DEVICE_ID + " doesn't exist.");
                            Toast.makeText(getContext(), R.string.problem_no_device, Toast.LENGTH_SHORT).show();
                        }
                        subscribeToData(device);
                    }
                });
    }

    private void subscribeToData(Device device) {
        mDevice = device;
        device.subscribeToCloudReadings()
                .timeout(25, TimeUnit.SECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Subscriber<Reading>() {
                    @Override public void onCompleted() {}

                    @Override public void onError(Throwable e) {
                        Log.e("Main", "subscribeToData error");
                        e.printStackTrace();
                        Toast.makeText(getContext(), R.string.problem_loading_data, Toast.LENGTH_SHORT).show();
                    }

                    @Override public void onNext(Reading reading) {
                        if (reading.meaning.equals("digital0"))
                            mStateDoor = (Boolean) reading.value;

                        if (reading.meaning.equals("magnetometer"))
                            mStatePresence = ((Number) reading.value).floatValue() > 200f;

                        showToiletState();
                    }
                });
    }

    private void showToiletState() {
        boolean current = mStateDoor && mStatePresence;
        mStateImg.setImageResource(current ? R.drawable.man_sitting : R.drawable.man_running);
        mStateText.setText(current ? R.string.occupied : R.string.free);
    }

    private void unSubscribeAll() {
        if (!mUserInfoSubscription.isUnsubscribed())
            mUserInfoSubscription.unsubscribe();

        if (!mDataSubs.isUnsubscribed())
            mDataSubs.unsubscribe();

        if (mDevice != null)
            RelayrSdk.getWebSocketClient().unSubscribe(mDevice.getId());
    }
}
