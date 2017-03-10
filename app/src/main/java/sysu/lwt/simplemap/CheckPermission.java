package sysu.lwt.simplemap;

import android.Manifest;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.widget.Toast;

import com.tbruyelle.rxpermissions.RxPermissions;

import rx.functions.Action1;


/**
 * Created by 12136 on 2017/3/10.
 */

public class CheckPermission extends AppCompatActivity{
    @Override
    protected void onCreate(Bundle onSaveInstanceState) {
        super.onCreate(onSaveInstanceState);
        // 使用RxPermissions进行动态权限申请
            RxPermissions rxPermissions = new RxPermissions(this);
            // 获取位置权限和存储权限, 同时请求
            rxPermissions
                    .request(Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    .subscribe(new Action1<Boolean>() {
                        @Override
                        public void call(Boolean granted) {
                            if (granted) {
                                Toast.makeText(CheckPermission.this, "Permissions are Granted", Toast.LENGTH_SHORT).show();
                                Intent intent = new Intent(CheckPermission.this, MainActivity.class);
                                startActivity(intent);
                                finish();
                            } else {
                                Toast.makeText(CheckPermission.this, "App will finish in 3 secs...", Toast.LENGTH_SHORT).show();
                                Handler mHandler = new Handler();
                                mHandler.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        finish();
                                    }
                                }, 3000);
                            }
                        }
                    });
    }
}
