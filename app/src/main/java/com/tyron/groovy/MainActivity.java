package com.tyron.groovy;

import android.os.Build;
import android.os.Bundle;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    //language=Groovy
    private static final String SAMPLE_SCRIPT = "println \"Hello World!\"";

    @RequiresApi(api = Build.VERSION_CODES.O_MR1)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        GrooidShell grooidShell = new GrooidShell(getClassLoader());
        grooidShell.evaluate(SAMPLE_SCRIPT);


    }
}