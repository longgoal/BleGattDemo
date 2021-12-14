package com.example.lihong.blegattdemo;

/**
 * Created by lihong on 2017/10/27.
 */

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import android.util.Log;


public class DeviceScanActivity extends AppCompatActivity {
    private ArrayList<BluetoothDevice> mLeDevices;//设备列表集合，在适配器的构造方法中实例化，即new一个适配，就生成该对象
    private LeDeviceListAdapter mLeDeviceListAdapter;//列表适配器，该类由自己实现
    private BluetoothAdapter mBluetoothAdapter;
    private boolean mScanning;
    private Handler mHandler;

    private static final int REQUEST_ENABLE_BT = 1;//请求打开蓝牙的请求代码
    // Stops scanning after 10 seconds.10秒后停止扫描，在Handler中使用
    private static final long SCAN_PERIOD = 10000;
    private String TAG = "DeviceScanActivity";

    // Device scan callback.系统将扫描到的结果通过该回调方法的参数传递出来
    private BluetoothAdapter.LeScanCallback  mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {
                @Override
                public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
                    Log.d(TAG,"onLeScan device name="+device.getName()+",address="+device.getAddress()+",rssi="+rssi+",scanRecord="+scanRecord);
                    //该线程用于更新UI
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mLeDeviceListAdapter.addDevice(device);
                            mLeDeviceListAdapter.notifyDataSetChanged();
                        }
                    });
                }
            };


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan_device);
       // getActionBar().setTitle(R.string.action_bar);该句代码添加上，程序就崩溃，目前还不知道原因
        mHandler = new Handler();
        ListView listView=(ListView)findViewById(R.id.list_view);

        mLeDeviceListAdapter=new  LeDeviceListAdapter(this,R.layout.list_item_device,mLeDevices);

        listView.setAdapter(mLeDeviceListAdapter);

        //在该方法中点击进入DeviceControlActivity中，将设备名字与地址传递过去；
        //并且停止扫描
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener(){
            @Override
            public void onItemClick(AdapterView<?> parent,View view,int position,long id){
                final BluetoothDevice device = mLeDeviceListAdapter.getDevice(position);
                if (device == null) return;
                final Intent intent = new Intent(DeviceScanActivity.this, DeviceControlActivity.class);
                intent.putExtra(DeviceControlActivity.EXTRAS_DEVICE_NAME, device.getName());
                 intent.putExtra(DeviceControlActivity.EXTRAS_DEVICE_ADDRESS, device.getAddress());
                 if (mScanning) {
                    mBluetoothAdapter.stopLeScan(mLeScanCallback);
                    mScanning = false;
                  }
                  startActivity(intent);
            }
        });

        // Use this check to determine whether BLE is supported on the device.  Then you can
        // selectively disable BLE-related features.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "ble_not_supported", Toast.LENGTH_SHORT).show();
            finish();
        }
        // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
        // BluetoothAdapter through BluetoothManager.
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        // Checks if Bluetooth is supported on the device.
        if (mBluetoothAdapter == null) {
            Toast.makeText(this,"error_bluetooth_not_supported", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }


    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu){
        getMenuInflater().inflate(R.menu.scan_menu,menu);
        if (!mScanning) {
            menu.findItem(R.id.menu_stop).setVisible(false);
            menu.findItem(R.id.menu_scan).setVisible(true);
            menu.findItem(R.id.menu_refresh).setActionView(null);
        } else {
            menu.findItem(R.id.menu_stop).setVisible(true);
            menu.findItem(R.id.menu_scan).setVisible(false);
            menu.findItem(R.id.menu_refresh).setActionView(
                    R.layout.actionbar_progress);
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()){
            case R.id.menu_scan:
                mLeDeviceListAdapter.clear();
                scanLeDevice(true);
                break;
            case R.id.menu_stop:
                scanLeDevice(false);
                break;
        }
        return true;
    }

    @Override
    protected void onResume(){
        super.onResume();
        Log.d(TAG,"onResume BT isEnabled="+mBluetoothAdapter.isEnabled());
        // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.
        if (!mBluetoothAdapter.isEnabled()) {
            if (!mBluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        }

        scanLeDevice(true);
    }

    //若蓝牙未打开，系统回调该方法，显示打开蓝牙对话框，请求打开蓝牙
    @Override
    protected void onActivityResult(int requestCode,int resultCode,Intent data){
        // User chose not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            finish();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    //在该方法中停止扫描，将扫描到的设备清0
    @Override
    protected void onPause(){
        super.onPause();
        Log.d(TAG,"onPause BT isEnabled="+mBluetoothAdapter.isEnabled());
        scanLeDevice(false);
        mLeDeviceListAdapter.clear();
    }


    private void scanLeDevice(final boolean enable) {
        Log.d(TAG,"scanLeDevice do enable="+enable);
        if (enable) {
            // Stops scanning after a pre-defined scan period.该段代码表示扫描10秒后停止，
            // mhandler相当于计时10秒后执行停止扫描的操作
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    Log.d(TAG,"scanLeDevice stopLeScan after 10 seconds");
                    mBluetoothAdapter.stopLeScan(mLeScanCallback);//即10秒后执行该语句
                    invalidateOptionsMenu();
                }
            }, SCAN_PERIOD);

            mScanning = true;
            Log.d(TAG,"scanLeDevice startLeScan");
            mBluetoothAdapter.startLeScan(mLeScanCallback);
        } else {
            mScanning = false;
            Log.d(TAG,"scanLeDevice stopLeScan");
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
        }
        invalidateOptionsMenu();
    }


    // Adapter for holding devices found through scanning.
    private class LeDeviceListAdapter extends ArrayAdapter<BluetoothDevice> {

        private int mResourceId;
        public LeDeviceListAdapter(Context context, int resouceId,List<BluetoothDevice> objecti){
            super(context,resouceId,objecti);
            mLeDevices=new ArrayList<BluetoothDevice>();
            mResourceId=resouceId;
        }

        //用于扫描回调方法中添加设备到列表中
        public void addDevice(BluetoothDevice device) {
            if(!mLeDevices.contains(device)) {
                mLeDevices.add(device);
            }
        }

        //该方法用于列表点击事件中，获取设备信息，传入下一个Activity中，即DeviceControlActivity
        public BluetoothDevice getDevice(int position) {
            return mLeDevices.get(position);
        }

        //用于这两种情况：1、扫描前，清空列表；2、程序停止时，清空列表
        public void clear() {
            mLeDevices.clear();
        }


        //适当时候，系统会回调以下两个方法，删除程序就崩溃
        @Override
        public int getCount() {
            return mLeDevices.size();
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            ViewHolder viewHolder;
            // General ListView optimization code.
            if (view == null) {
                view=LayoutInflater.from(getContext()).inflate(mResourceId,viewGroup,false);
                viewHolder = new ViewHolder();
                viewHolder.deviceAddress = (TextView) view.findViewById(R.id.device_address);
                viewHolder.deviceName = (TextView) view.findViewById(R.id.device_name);
                view.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) view.getTag();
            }

            BluetoothDevice device = mLeDevices.get(i);
            final String deviceName = device.getName();
            if (deviceName != null && deviceName.length() > 0)
                viewHolder.deviceName.setText(deviceName);
            else
                viewHolder.deviceName.setText("unknown_device");
            viewHolder.deviceAddress.setText(device.getAddress());

            return view;
        }
    }

    static class ViewHolder{
        TextView deviceName;
        TextView deviceAddress;
    }
}
