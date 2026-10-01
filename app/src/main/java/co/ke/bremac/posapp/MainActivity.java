package co.ke.bremac.posapp;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppSession session = AppSession.get(this);
        Class<?> target = session.isSignedIn() ? HomeActivity.class : LoginActivity.class;
        startActivity(new Intent(this, target));
        finish();
    }
}
