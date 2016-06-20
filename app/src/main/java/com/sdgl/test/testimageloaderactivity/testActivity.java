package com.sdgl.test.testimageloaderactivity;

import android.content.SharedPreferences;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.SimpleAdapter;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.HashMap;

public class testActivity extends AppCompatActivity {

    public ArrayList<HashMap<String,Object>> listItem;
    public SimpleAdapter simpleAdapter;
    public SharedPreferences sp;
    private SharedPreferences.Editor editor;
    public JSONArray jsonArray ;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.test_order_layout);

        initData();

    }

    private void initData() {


    }
}
