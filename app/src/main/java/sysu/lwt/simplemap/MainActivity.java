package sysu.lwt.simplemap;

import android.app.Service;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;
import android.os.Vibrator;
import android.support.v7.app.AppCompatActivity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.baidu.mapapi.SDKInitializer;
import com.baidu.mapapi.map.BaiduMap;
import com.baidu.mapapi.map.BitmapDescriptor;
import com.baidu.mapapi.map.BitmapDescriptorFactory;
import com.baidu.mapapi.map.MapStatus;
import com.baidu.mapapi.map.MapStatusUpdate;
import com.baidu.mapapi.map.MapStatusUpdateFactory;
import com.baidu.mapapi.map.MapView;
import com.baidu.mapapi.map.MyLocationConfiguration;
import com.baidu.mapapi.map.MyLocationData;
import com.baidu.mapapi.model.LatLng;
import com.baidu.mapapi.utils.CoordinateConverter;

/**
 * Created by 12136 on 2017/3/10.
 */

public class MainActivity extends AppCompatActivity {
    private MapView mMapView;
    private LocationManager locationManager;
    private static final int TWO_MINUTES = 1000 * 60 * 2;
    private Location currentBestLocation = null;
    private boolean isFirstLocate = true;
    private BaiduMap baiduMap;
    //自定义定位图标
    private BitmapDescriptor bitmapDescriptor;
    // 位置是否发生了更新
    private boolean ischanging = true;

    @Override
    protected void onCreate(Bundle onSaveInstanceState) {
        super.onCreate(onSaveInstanceState);
        //在使用SDK各组件之前初始化context信息，传入ApplicationContext
        SDKInitializer.initialize(getApplicationContext());
        setContentView(R.layout.main_layout);
        mMapView = (MapView) findViewById(R.id.baidumap);
        baiduMap = mMapView.getMap();
        // 位置提供器
        setLocationManager();
        //将显示位置的功能开启
        baiduMap.setMyLocationEnabled(true);
        // 传感器提供器
        setSensorManager();
        // 初始化箭头图标
        Bitmap bitmap = Bitmap.createScaledBitmap(BitmapFactory.decodeResource(getResources(),
                R.mipmap.pointer), 100, 100, true);
        bitmapDescriptor = BitmapDescriptorFactory
                .fromBitmap(bitmap);

        // 定位图标
        final ToggleButton toggleButton = (ToggleButton) findViewById(R.id.tb_center);
        baiduMap.setOnMapTouchListener(new BaiduMap.OnMapTouchListener() {
            @Override
            public void onTouch(MotionEvent motionEvent) {
                switch (motionEvent.getAction()) {
                    case MotionEvent.ACTION_MOVE:
                        toggleButton.setChecked(false);
                        ischanging = false;
                        //System.out.println("ischanging: false");
                        break;
                    default:
                        //System.out.println("ischanging: true");
                        break;
                }
            }
        });
        toggleButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (toggleButton.isChecked()) {
                    ischanging = true;
                }
                else {
                    ischanging = false;
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //在activity执行onDestroy时执行mMapView.onDestroy()，实现地图生命周期管理
        mMapView.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        //在activity执行onResume时执行mMapView. onResume()，实现地图生命周期管理
        mMapView.onResume();
        // 在前台时注册
        try {
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, NetWorkLocationListener);
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, GPSLocationListener);
        } catch (SecurityException e) {
            e.printStackTrace();
        }
        // 注册传感器
        // register magnetic and accelerometer sensor into sensor manager (onResume
        mSensorManager.registerListener(mSensorEventListener, mMagneticSensor,
                SensorManager.SENSOR_DELAY_GAME);
        mSensorManager.registerListener(mSensorEventListener, mAccelerometerSensor,
                SensorManager.SENSOR_DELAY_GAME);
    }

    @Override
    protected void onPause() {
        super.onPause();
        //在activity执行onPause时执行mMapView. onPause()，实现地图生命周期管理
        mMapView.onPause();
        // 离开前台时取消注册
        if(locationManager!=null){
            try {
                locationManager.removeUpdates(GPSLocationListener);
                locationManager.removeUpdates(NetWorkLocationListener);
            } catch (SecurityException e) {
                e.printStackTrace();
            }
        }
        // unregister sensors
        mSensorManager.unregisterListener(mSensorEventListener);
    }

    private SensorManager mSensorManager;
    private Sensor mMagneticSensor;
    private Sensor mAccelerometerSensor;
    private Vibrator vibrator;
    private static int UPTATE_INTERVAL_TIME = 500;
    private float newRotationDegree;
    private void setSensorManager() {
        // 获取传感器实例
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        // 获取地磁传感器和加速度传感器信息
        mMagneticSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        mAccelerometerSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        // 震动
        vibrator = (Vibrator) getSystemService(Service.VIBRATOR_SERVICE);
    }

    // sensor event listener
    private SensorEventListener mSensorEventListener = new SensorEventListener() {
        float[] accValues = null;
        float[] magValues = null;
        long lastShakeTime = 0;

        @Override
        public void onSensorChanged(SensorEvent event) {
            switch (event.sensor.getType()) {
                case Sensor.TYPE_ACCELEROMETER:
                    // 设置摇一摇时间检测间隔
                    long currentUpdateTime = System.currentTimeMillis();
                    long timeInterval = currentUpdateTime - lastShakeTime;
                    if(timeInterval < UPTATE_INTERVAL_TIME){
                        accValues = event.values;
                        break;
                    }
                    lastShakeTime = currentUpdateTime;
                    //accValues[0]:X轴，accValues[1]：Y轴，accValues[2]：Z轴
                    accValues = event.values;
                    if((Math.abs(accValues[0])>15||Math.abs(accValues[1])>15||Math.abs(accValues[2])>15)){
                        //摇动手机后，显示toast
                        Toast.makeText(MainActivity.this, "是哪个刁民在摇朕的手机？", Toast.LENGTH_SHORT).show();
                        //摇动手机后，再伴随震动提示~~
                        vibrator.vibrate(200);
                    }
                    break;
                case Sensor.TYPE_MAGNETIC_FIELD:
                    // do something about values of magnetic field
                    magValues = event.values;
                    break;
                default:
                    break;
            }
            float[] R = new float[9];
            float[] values = new float[3];
            if (accValues != null && magValues != null) {
                SensorManager.getRotationMatrix(R, null, accValues, magValues);
                SensorManager.getOrientation(R, values);
                //经过SensorManager.getOrientation(R, values);得到的values值为弧度
                //转换为角度
                newRotationDegree = (float) Math.toDegrees(values[0]);
                setLocationManager();
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };

    private void setLocationManager() {
        String provider;
        // 获取位置服务
        locationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
        // 通过GPS定位，较精确，也比较耗电
        LocationProvider gpsProvider = locationManager.getProvider(LocationManager.GPS_PROVIDER);
        // 通过网络定位，较不准确，省电
        LocationProvider netProvider = locationManager.getProvider(LocationManager.NETWORK_PROVIDER);
        // 为了实现快速的定位，优先获取NetWork的位置提供器
        // 当然可能出现网络丢失的情况，所以还是要考虑GPS
        if (netProvider != null) {
            provider = LocationManager.NETWORK_PROVIDER;
        } else if (gpsProvider != null) {
            provider = LocationManager.GPS_PROVIDER;
        } else {
            //当前没有可用的位置提供器时，弹出Toast提示
            Toast.makeText(this,"没有可用的位置提供器",Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            // 首先尝试获取最近一次的位置信息，如果最近一次位置信息不为空，则优先使用，
            // 实现快速定位
            Location location = locationManager.getLastKnownLocation(provider);
            if (location != null) {
                NavigateTo(location);
            }
            else {
                // 如果getLastKnownLocation返回空，则需要重新获取
                if (provider.equals(LocationManager.NETWORK_PROVIDER))
                    locationManager.requestLocationUpdates(provider, 0, 0, NetWorkLocationListener);
                else
                    locationManager.requestLocationUpdates(provider, 0, 0, GPSLocationListener);
            }
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    LocationListener GPSLocationListener = new LocationListener() {
        private boolean isRemove = false;//判断网络监听是否移除

        @Override
        public void onLocationChanged(Location location) {
            // isBetterLocation判断获取到的location是不是更好的location
            // 如果是，那么当然是用比较好的location替换当前的location
            if (isBetterLocation(location, currentBestLocation)) {
                Toast.makeText(MainActivity.this, "GPS定位更新", Toast.LENGTH_SHORT).show();
                currentBestLocation = location;
                NavigateTo(currentBestLocation);
            }
            // 获得GPS服务后，移除network监听  
            if (location != null && !isRemove) {
                try {
                    locationManager.removeUpdates(NetWorkLocationListener);
                    isRemove = true;
                } catch (SecurityException e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void onStatusChanged(String s, int i, Bundle bundle) {
            if (LocationProvider.OUT_OF_SERVICE == i) {
                Toast.makeText(MainActivity.this,"GPS服务丢失,切换至网络定位",
                        Toast.LENGTH_SHORT).show();
                // GPS信号丢失后尝试获取NetWork定位
                try {
                    locationManager.requestLocationUpdates(
                            LocationManager.NETWORK_PROVIDER, 0, 0,
                            NetWorkLocationListener);
                } catch (SecurityException e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void onProviderEnabled(String s) {}

        @Override
        public void onProviderDisabled(String s) {}
    };


    LocationListener NetWorkLocationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            if (isBetterLocation(location, currentBestLocation)) {
                Toast.makeText(MainActivity.this, "NETWORK定位更新", Toast.LENGTH_SHORT).show();
                currentBestLocation = location;
                NavigateTo(currentBestLocation);
            }
        }

        @Override
        public void onStatusChanged(String s, int i, Bundle bundle) {
            // 同样的，如果遇到网络信号丢失，那么尝试切换GPS定位
            if (LocationProvider.OUT_OF_SERVICE == i) {
                Toast.makeText(MainActivity.this,"NETWORK服务丢失,切换至GPS定位",
                        Toast.LENGTH_SHORT).show();
                try {
                    locationManager.requestLocationUpdates(
                            LocationManager.GPS_PROVIDER, 0, 0,
                            GPSLocationListener);
                } catch (SecurityException e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void onProviderEnabled(String s) {}

        @Override
        public void onProviderDisabled(String s) {}
    };

    /** Determines whether one Location reading is better than the current Location fix
     * @param location  The new Location that you want to evaluate
     * @param currentBestLocation  The current Location fix, to which you want to compare the new one
     */
    // Android官方的比较当前的location和获取到的location那个比较好的方法
    protected boolean isBetterLocation(Location location, Location currentBestLocation) {
        if (currentBestLocation == null) {
            // A new location is always better than no location
            return true;
        }

        // Check whether the new location fix is newer or older
        long timeDelta = location.getTime() - currentBestLocation.getTime();
        boolean isSignificantlyNewer = timeDelta > TWO_MINUTES;
        boolean isSignificantlyOlder = timeDelta < -TWO_MINUTES;
        boolean isNewer = timeDelta > 0;

        // If it's been more than two minutes since the current location, use the new location
        // because the user has likely moved
        if (isSignificantlyNewer) {
            return true;
            // If the new location is more than two minutes older, it must be worse
        } else if (isSignificantlyOlder) {
            return false;
        }

        // Check whether the new location fix is more or less accurate
        int accuracyDelta = (int) (location.getAccuracy() - currentBestLocation.getAccuracy());
        boolean isLessAccurate = accuracyDelta > 0;
        boolean isMoreAccurate = accuracyDelta < 0;
        boolean isSignificantlyLessAccurate = accuracyDelta > 200;

        // Check if the old and new location are from the same provider
        boolean isFromSameProvider = isSameProvider(location.getProvider(),
                currentBestLocation.getProvider());

        // Determine location quality using a combination of timeliness and accuracy
        if (isMoreAccurate) {
            return true;
        } else if (isNewer && !isLessAccurate) {
            return true;
        } else if (isNewer && !isSignificantlyLessAccurate && isFromSameProvider) {
            return true;
        }
        return false;
    }

    /** Checks whether two providers are the same */
    private boolean isSameProvider(String provider1, String provider2) {
        if (provider1 == null) {
            return provider2 == null;
        }
        return provider1.equals(provider2);
    }

    private void NavigateTo(Location location) {
        //封装设备当前位置并且显示在地图上
        //由于设备在地图上显示的位置会根据我们当前位置而改变，所以写到if外面
        // 将GPS设备采集的原始GPS坐标转换成百度坐标
        CoordinateConverter converter = new CoordinateConverter();
        converter.from(CoordinateConverter.CoordType.GPS);
        // sourceLatLng待转换坐标
        converter.coord(new LatLng(location.getLatitude(), location.getLongitude()));
        LatLng desLatLng = converter.convert();
        //System.out.println("纬度：" + desLatLng.latitude);
        //System.out.println("经度：" + desLatLng.longitude);

        MyLocationData data = new MyLocationData.Builder()
                .latitude(desLatLng.latitude)
                .longitude(desLatLng.longitude)
                .direction(newRotationDegree)
                .build();
        baiduMap.setMyLocationData(data);
        // 设置箭头
        MyLocationConfiguration config = new MyLocationConfiguration(
                MyLocationConfiguration.LocationMode.NORMAL, true, bitmapDescriptor);
        baiduMap.setMyLocationConfigeration(config);

        // 使坐标居中
        if (ischanging) {
            MapStatus mapStatus = new MapStatus.Builder().target(desLatLng).build();
            MapStatusUpdate mapStatusUpdate = MapStatusUpdateFactory.newMapStatus(mapStatus);
            baiduMap.setMapStatus(mapStatusUpdate);
        }
        //如果是第一次创建，就获取位置信息并且将地图移到当前位置
        //为防止地图被反复移动，所以就只在第一次创建时执行
        if(isFirstLocate){
            //LatLng对象主要用来存放经纬度
            //zoomTo是用来设置百度地图的缩放级别，范围为3~19，数值越大越精确
            LatLng ll = new LatLng(location.getLatitude(),location.getLongitude());
            MapStatusUpdate update = MapStatusUpdateFactory.newLatLng(ll);
            baiduMap.animateMapStatus(update);
            update = MapStatusUpdateFactory.zoomTo(15f);
            baiduMap.animateMapStatus(update);
            isFirstLocate = false;
        }
    }
}

