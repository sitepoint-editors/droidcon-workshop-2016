package io.relayr.toilet;

import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import butterknife.ButterKnife;
import butterknife.InjectView;
import io.relayr.android.RelayrSdk;
import io.relayr.java.model.User;
import io.relayr.toilet.proto.R;
import rx.Observer;
import rx.android.schedulers.AndroidSchedulers;

public class MainActivity extends AppCompatActivity {

    @InjectView(R.id.tab_layout) TabLayout tabLayout;
    @InjectView(R.id.pager) ViewPager viewPager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.inject(this);

        tabLayout.addTab(tabLayout.newTab().setText("Toilet"));
        tabLayout.addTab(tabLayout.newTab().setText("History"));
        tabLayout.setTabGravity(TabLayout.GRAVITY_FILL);

        final PagerAdapter adapter = new PagerAdapter(getSupportFragmentManager(), tabLayout.getTabCount());
        viewPager.setAdapter(adapter);
        viewPager.addOnPageChangeListener(new TabLayout.TabLayoutOnPageChangeListener(tabLayout));
        tabLayout.setOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                viewPager.setCurrentItem(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });

        if (!RelayrSdk.isUserLoggedIn()) logIn();
    }


    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.clear();

        if (RelayrSdk.isUserLoggedIn()) getMenuInflater().inflate(R.menu.demo_logged_in, menu);
        else getMenuInflater().inflate(R.menu.demo_not_logged_in, menu);

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_log_in) {
            logIn();
            return true;
        } else if (item.getItemId() == R.id.action_log_out) {
            logOut();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void logIn() {
        RelayrSdk.logIn(this)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<User>() {
                    @Override public void onCompleted() {}

                    @Override public void onError(Throwable e) {
                        Toast.makeText(MainActivity.this,
                                R.string.unsuccessfully_logged_in, Toast.LENGTH_SHORT).show();
                        e.printStackTrace();
                    }

                    @Override public void onNext(User user) {
                        Toast.makeText(MainActivity.this,
                                R.string.successfully_logged_in, Toast.LENGTH_SHORT).show();
                        invalidateOptionsMenu();
                    }
                });
    }

    private void logOut() {
        RelayrSdk.logOut();
        invalidateOptionsMenu();
        Toast.makeText(this, R.string.successfully_logged_out, Toast.LENGTH_SHORT).show();
        finish();
    }
}
