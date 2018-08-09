package com.example.home.secretary_kim;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

public class BluetoothService {

    private static final int REQUEST_CONNECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;
    private static final String TAG = "BluetoothService";
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private BluetoothAdapter btAdapter;
    private Activity mActivity;
    private Handler mHandler;

    public static final int STATE_NONE = 1;
    public static final int STATE_LISTEN = 2;
    public static final int STATE_CONNECTING = 3;
    public static final int STATE_CONNECTED = 4;
    public static final int STATE_FAIL = 7;

    private int mState;

    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;

    public int mMode;

    public BluetoothService(Activity activity, Handler handler){
        mActivity = activity;
        mHandler = handler;

        btAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    public boolean getDeviceState(){
        Log.d(TAG, "Check the Bluetooth support");

        if(btAdapter==null){
            Log.d(TAG, "Bluetooth is not available");
            return false;
        }
        else{
            Log.d(TAG, "Bluetooth is available");
            return true;
        }
    }

    public void enableBluetooth(){
        Log.d(TAG, "Check the enable Bluetooth");

        if (btAdapter.isEnabled()) {
            Log.d(TAG, "Bluetooth Enable now");

            scanDevice();
        }
        else{
            Log.d(TAG, "Bluetooth Enable Request");

            Intent i = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            mActivity.startActivityForResult(i, REQUEST_ENABLE_BT);
        }
    }

    public void scanDevice(){
        Log.d(TAG, "Scan Device");

        Intent serverIntent = new Intent(mActivity, DeviceListActivity.class);
        mActivity.startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
    }

    private synchronized void setState(int state){
        Log.d(TAG, "setState()" + mState + "->" + state);
        mState = state;

        mHandler.obtainMessage(BluetoothActivity.MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
    }

    public synchronized int getState(){
        return mState;
    }

    public synchronized void start(){
        Log.d(TAG, "start");

        if(mConnectThread == null){

        }
        else{
            mConnectThread.cancel();
            mConnectThread = null;
        }
    }

    public void getDeviceInfo(Intent data){
        String address = data.getExtras().getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
        BluetoothDevice device = btAdapter.getRemoteDevice(address);

        Log.d(TAG, "Get Device Info \n" + "adress : " + address);

        connect(device);
    }

    public synchronized void connect(BluetoothDevice device){
        Log.d(TAG, "connect to : " + device);

        if(mState == STATE_CONNECTING){
            if(mConnectThread == null){

            }
            else{
                mConnectThread.cancel();
                mConnectThread = null;
            }
        }

        if(mConnectedThread == null){

        }
        else{
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        mConnectThread = new ConnectThread(device);

        mConnectThread.start();
        setState(STATE_CONNECTING);
    }

    public synchronized void connected(BluetoothSocket socket, BluetoothDevice device){
        Log.d(TAG, "connected");

        if(mConnectThread == null){

        }
        else{
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if(mConnectedThread == null){

        }
        else{
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        mConnectedThread = new ConnectedThread(socket);
        mConnectedThread.start();

        setState(STATE_CONNECTED);
    }

    public synchronized void stop(){
        Log.d(TAG, "stop");

        if(mConnectThread != null){
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if(mConnectedThread != null){
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        setState(STATE_NONE);
    }

    private class ConnectThread extends Thread{
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device) {
            mmDevice = device;
            BluetoothSocket tmp = null;

            try {
                tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) {
                Log.d(TAG, "create() failed", e);
            }
            mmSocket = tmp;

        }

        public void run(){
            Log.i(TAG, "BEGIN mConnectThread");
            setName("ConnectThread");

            btAdapter.cancelDiscovery();

            try {
                mmSocket.connect();
                Log.d(TAG, "Connect Success");
            } catch (IOException e) {
                connectionFailed();
                Log.d(TAG, "Connect Fail");

                try {
                    mmSocket.close();
                } catch (IOException e1) {
                    Log.e(TAG,"unable to close() socket during connection failure", e1);
                }

                BluetoothService.this.start();
                return;
            }

            synchronized (BluetoothService.this){
                mConnectThread = null;
            }

            connected(mmSocket, mmDevice);
        }

        public void cancel(){
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }

    private class ConnectedThread extends Thread{
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket){
            Log.d(TAG, "create ConnectedThread");
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "temp sockets not created", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run(){
            Log.i(TAG, "BEGIN mConnectedThread");
            byte[] buffer = new byte[1024]; //얘는 크기가 왜 1024일까
            int bytes = 0;  //이건 또 뭘까

            while(true){
                try {
                    int tmp = mmInStream.available();

                    if(tmp > 0) {
                        byte[] packetBytes = new byte[tmp];

                        mmInStream.read(packetBytes);

                        for (int i = 0; i < tmp; i++) {
                            if (packetBytes[i] == '\n') {
                                byte[] encodedBytes = new byte[bytes];
                                System.arraycopy(buffer, 0, encodedBytes, 0,
                                        encodedBytes.length);

                                String recvMessage = new String(encodedBytes, "UTF-8");
                                bytes = 0;

                                Log.d(TAG, "recv message: " + recvMessage);
                            } else {
                                buffer[bytes++] = packetBytes[i];
                            }
                        }
                    }
                    /*
                    bytes = mmInStream.read(buffer);
                    Log.d(TAG, "bytes : " + bytes); //반환값 확인 좀 해보자
                    */
                } catch (IOException e) {
                    Log.e(TAG, "disconnected", e);
                    connectionLost();
                    break;
                }
            }
        }

        public void write(byte[] buffer, int mode){
            try {
                mmOutStream.write(buffer);
                mMode = mode;

                if(mode == BluetoothActivity.MODE_REQUEST){
                    mHandler.obtainMessage(BluetoothActivity.MESSAGE_WRITE, -1, -1,buffer).sendToTarget();
                }
            } catch (IOException e) {
                Log.e(TAG, "Exception during write", e);
            }
        }

        public void cancel(){
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }

    public void write(byte[] out, int mode){
        ConnectedThread r;

        synchronized (this){
            if(mState != STATE_CONNECTED)
                return;
            r = mConnectedThread;
        }

        r.write(out, mode);
    }

    private void connectionFailed() {
        setState(STATE_FAIL);
    }

    private void connectionLost() {
        setState(STATE_LISTEN);
    }
}
