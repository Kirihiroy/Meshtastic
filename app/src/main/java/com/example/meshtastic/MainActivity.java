package com.example.meshtastic;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import com.example.meshtastic.ui.connection.ConnectionFragment;
import com.example.meshtastic.ui.nodes.NodesFragment;
import com.example.meshtastic.ui.settings.SettingsFragment;
import com.example.meshtastic.ui.status.StatusFragment;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.nav_connection) {
                showFragment(new ConnectionFragment());
                return true;
            } else if (item.getItemId() == R.id.nav_status) {
                showFragment(new StatusFragment());
                return true;
            } else if (item.getItemId() == R.id.nav_nodes) {
                showFragment(new NodesFragment());
                return true;
            } else if (item.getItemId() == R.id.nav_settings) {
                showFragment(new SettingsFragment());
                return true;
            }
            return false;
        });

        // Стартовый экран
        if (savedInstanceState == null) {
            bottomNav.setSelectedItemId(R.id.nav_connection);
        }
    }

    private void showFragment(androidx.fragment.app.Fragment fragment) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.replace(R.id.fragment_container, fragment);
        transaction.commit();
    }
}