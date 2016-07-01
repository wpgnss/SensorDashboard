package wiznet.novita_app;


import android.app.Fragment;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;


/**
 * Created by Daniel on 2016-06-03.
 */

public class Room_1 extends Fragment implements View.OnClickListener{

    private Thread connectionThread = null;
    private boolean once;
    String TAG = "Novita_Room_1";
    private Handler mHandler;
    private static Socket socket = null;
    private byte[] readmsg;
    private byte d_onstatus = (byte)0x01;
    private byte d_offstatus = (byte)0x00;
    private byte g_recv_status = d_offstatus;

    private byte g_current_status = d_offstatus;
    private boolean g_auto = false;

    String device_on_msg = "8801010001000000000055";
    String device_off_msg = "8801010000000000000055";

    private Handler autoHandler = null;
   // private String ip = "192.168.1.101";
   private String ip = "222.98.173.194";
    private int port = 5001;

//    10초동안 데이터 수신이 없을 시 Disconnect로 간주하기 위한 Timer
    private long g_wait_time = 10 * 1000;

    Button button_power;
    Button button_auto;

    TextView sensor1;
    TextView sensor2;
    TextView sensor3;
    ImageView room1_iscon;

    LinearLayout room1_title;

    CountDownTimer countDownTimer;

    public Room_1() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, final ViewGroup container,
                             Bundle savedInstanceState) {

        View view;
        view = inflater.inflate(R.layout.room_1, container, false);

        button_power = (Button) view.findViewById(R.id.button_room1_power);
        button_auto = (Button) view.findViewById(R.id.button_room1_toggle);
        sensor1 = (TextView) view.findViewById(R.id.room1_sensor1);
        sensor2 = (TextView) view.findViewById(R.id.room1_sensor2);
        sensor3 = (TextView) view.findViewById(R.id.room1_sensor3);
        room1_title = (LinearLayout) view.findViewById(R.id.room1_title);
        room1_iscon = (ImageView) view.findViewById(R.id.iscon_room1);

        updateButtonState(button_power, false);
        updateButtonState(button_auto, false);
        mHandler = new Handler();
        once = true;
        Toast.makeText(getActivity().getApplicationContext(), "연결 시도중...", Toast.LENGTH_SHORT).show();
        // 앱 시작시 TCP 연결 시도
        Connect();

        button_power.setOnClickListener(this);
        button_auto.setOnClickListener(this);
        room1_iscon.setOnClickListener(this);


        if(countDownTimer!=null){
            countDownTimer.cancel();
        }
        countDownTimer = new CountDownTimer(g_wait_time, 1000) {
            @Override
            public void onFinish()
            {
                //Message를 수신 후 10초동안 아무런 Message가 없을 때 DisConnect 이미지 출력(실제 소켓과 무관)
                if(socket != null)
                    room1_iscon.setImageResource(R.drawable.connect);
                else
                    room1_iscon.setImageResource(R.drawable.disconnect);
            }
            @Override
            public void onTick(long millisUntilFinished) {
                //1초(Tick)마다 수행하는 함수
            }
        };

        return view;
    }

    /*   onClick Event 화면의 버튼을 눌렀을 때 동작   */
    @Override
    public void onClick(View v) {

        switch (v.getId()) {
            //ON/OFF Button
            case R.id.button_room1_power:

                byte[] temp;

                if(g_recv_status == d_offstatus) {
                    temp = hexStringToByteArray(device_on_msg);
                    Log.d(TAG,"power on");
                } else {
                    temp = hexStringToByteArray(device_off_msg);
                    Log.d(TAG,"power off");
                }

                try {
                    sendBytes(temp);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;

            // Auto Button
            case R.id.button_room1_toggle:

                if( g_auto ) {
                    updateButtonState(button_auto, false);
                    if(autoHandler != null) {
                        autoHandler.removeCallbacks(auto_run);
                        autoHandler = null;
                    }

                } else {

                    updateButtonState(button_auto, true);
                    if(autoHandler == null){
                        autoHandler = new Handler();
                        autoHandler.post(auto_run);
                    }
                }
                break;

/*
            // Connection Button
            case R.id.iscon_room1:

                if( socket == null )
                    Connect();
                else
                    Disconnect();
                break;
*/
            default:
                break;
        }
    }

    private void updateButtonState(Button tb, boolean state){

        if( state ){
            tb.setBackgroundResource(R.drawable.custom_button_on);
            tb.setTextColor(Color.parseColor("#ffffff"));

            if(tb == button_power)
                tb.setText("O N");
            else if(tb == button_auto)
                g_auto = state;

        } else {
            tb.setBackgroundResource(R.drawable.custom_button_off);
            tb.setTextColor(Color.parseColor("#ABD3B4"));

            if(tb == button_power)
                tb.setText("OFF");
            else if(tb == button_auto)
                g_auto = state;

        }
    }

    private void updateView(int color){

        sensor1.setBackgroundColor(color);

    }

    /*   byteArray를 OutputStream에 전송한다.   */
    private void sendBytes(byte[] byteArray) throws IOException {

        try {
            if (socket.isConnected()) {
                DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                dos.write(byteArray);                          // byte array를 TCP Send (output stream에 전송)
            }
        } catch (NullPointerException e) {
            e.printStackTrace();
        }

    }

    /*   InputStream에서 11byte를 읽는다   */
    public byte[] readBytes() throws IOException {

        if (socket.isConnected()) {
            DataInputStream dis = new DataInputStream(socket.getInputStream());

            byte[] data = new byte[11];
            dis.readFully(data);                 // readyFully-> buffer array 사이즈만큼 바이트를 input stream에서 읽어옴(Reads some bytes from an input stream and stores them into the buffer array b)

            if (data[0] != (byte) 0x88)           // 11byte를 읽었얼 때 start byte가 0x88이 아니면 input stream의 남아있는 데이터를 버림.
                while (dis.available() > 0)
                    dis.readByte();
            return data;
        }
        return null;
    }


    public void Disconnect() {
        try{
            if( socket != null) {
                checkUpdate.isInterrupted();           // CheckUpdate Thread 종료시켜주는 interrupt. 안되는 경우도 있음.
                socket.close();
                socket = null;
            }

        } catch (Exception e){
            Log.d(TAG, "disconnect error");
        }
    }

    public void Connect() {
        Runnable runnable = new Runnable() {

            @Override
            public void run() {
                try {
                    if( socket == null) {
                        socket = new Socket(ip, port);          // TCP Socket 생성
                    }
                } catch (Exception e) {
                    Log.d(TAG, "SetConnection " + e.getMessage());
                    e.printStackTrace();
                } finally {
                    if(once) {
                        checkUpdate.start();   // 프로그램이 시작하면 receive 스레드는 단 한번만 실행되서 앱이 종료될때까지 샐행된다.
                        once = false;
                    }

                }

                if(socket != null)
                    room1_iscon.setImageResource(R.drawable.connect);
            }
        };

        connectionThread = new Thread(runnable);
        connectionThread.start();
    }


    /*     TCP Recieve Thread, 무한 루프      */
    private Thread checkUpdate = new Thread() {

        public void run() {

            while(true) {

                try {
                    if (socket != null) {
                        readmsg = readBytes();             // 11byte 읽어옴

                        if (readmsg[0] == (byte) 0x88 && readmsg[10] == (byte) 0x55) {
                            countDownTimer.cancel();
                            countDownTimer.start();
                            mHandler.post(showUpdate);             // 정상적인 11byte로 화면 업데이트
                        } else {
                            readmsg = null;
                        }
                    }

//                    if (checkUpdate.currentThread().isInterrupted())
//                        break;
                } catch (Exception e) {
                    Log.d(TAG, "update fail " + e);
                }



            }
//            Log.d(TAG, "thread dead");
        }
    };

    /*     화면 업데이트      */
    private Runnable showUpdate = new Runnable() {

        public void run() {

            if( readmsg != null )
            {
                Log.d(TAG, "showUpdate");
                room1_iscon.setImageResource(R.drawable.received);

                int color = 0;
                int pm25 = ((readmsg[5] & 0xFF) << 8) + ((readmsg[6] & 0xFF) << 0);   // byte -> int
                int voc =  ((readmsg[7] & 0xFF) << 8) + ((readmsg[8] & 0xFF) << 0);
                int temp = (readmsg[9] & 0xFF);
                g_recv_status = readmsg[4];

                if( pm25 <= 30 ) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        color = getContext().getColor(R.color.standard_good);
                    } else {
                        color = Color.parseColor("#074bf5");
                    }
                }
                else if( pm25 > 30 && pm25 <= 80 ) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        color = getContext().getColor(R.color.standard_normal);
                    } else {
                        color = Color.parseColor("#66af29");
                    }
                }
                else if(pm25 > 80 && pm25 <= 150 ) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        color = getContext().getColor(R.color.standard_bad);
                    } else {
                        color = Color.parseColor("#e19532");
                    }
                }
                else if(pm25 > 150 ) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        color = getContext().getColor(R.color.standard_toobad);
                    } else {
                        color = Color.parseColor("#de1709");
                    }
                }
                if( color != 0 )
                    updateView(color);

                if( g_recv_status == d_onstatus ) {
                    g_current_status = g_recv_status;
                    updateButtonState(button_power, true);
                } else {
                    g_current_status = g_recv_status;
                    updateButtonState(button_power, false);
                }

                sensor1.setText(String.valueOf(pm25));
                sensor2.setText(String.valueOf(voc));
                sensor3.setText(String.valueOf(temp));

            }
        }
    };


    /*     센서값이 임계치 이상일 때, 자동으로 COMMAND를 서버로 전송     */
    private Runnable auto_run = new Runnable() {
        public void run() {
            byte[] temp;
            String sensor1_value;
            sensor1_value = sensor1.getText().toString();

            Log.d(TAG, "auto_run " + g_current_status);

            int s1_val_int = Integer.parseInt(sensor1_value);
            Log.d(TAG,"value " + s1_val_int );
            if( s1_val_int >= 100 && g_current_status == d_offstatus ){

                temp = hexStringToByteArray(device_on_msg);
                Log.d(TAG,"power button - on");

                try {
                    sendBytes(temp);                             //  Send Byte Stream
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else if (s1_val_int <= 50 && g_current_status == d_onstatus){
                temp = hexStringToByteArray(device_off_msg);
                Log.d(TAG,"power button - off");

                try {
                    sendBytes(temp);                             //  Send Byte Stream
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            autoHandler.postDelayed(this, 1000);               // 1초 후 auto_run 재귀
        }
    };

/*     Utility      */
    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }
}