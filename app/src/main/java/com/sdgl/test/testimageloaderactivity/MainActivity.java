package com.sdgl.test.testimageloaderactivity;

import android.app.Activity;
import android.content.Intent;
import android.media.Image;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;

public class MainActivity extends Activity {

    private ImageLoader imageLoader = new ImageLoader(this,R.mipmap.main_index_my_normal,R.mipmap.main_index_my_normal);

    private ImageButton iv_photo = null;
    private Button btn_list = null;
    private Button btn_grad = null;
    private Button btn_test = null;
    private String url = "http://192.168.14.150:8080/quickframe/upload/head_image/cea0591f-a4d0-4adf-a11b-3d734f791228.jpg";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        iv_photo = (ImageButton) findViewById(R.id.iv_photo);
        btn_list = (Button) findViewById(R.id.btn_list);
        btn_grad = (Button) findViewById(R.id.btn_gird);
        btn_test = (Button) findViewById(R.id.btn_test);
    }

    @Override
    protected void onResume() {
        imageLoader.loadImage(iv_photo,url);
        super.onResume();
    }
    public View.OnClickListener listener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch (v.getId()){
                case R.id.btn_test:
                    Intent intent = new Intent(MainActivity.this,testActivity.class);
                    startActivity(intent);
                    break;
            }
        }
    };
}
