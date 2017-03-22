package org.shkvortex.huemuse;

import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
//copied from libmuse example
import java.io.File;
import java.lang.ref.WeakReference;
import java.util.List;

import java.util.concurrent.atomic.AtomicReference;

import com.choosemuse.libmuse.Accelerometer;
import com.choosemuse.libmuse.AnnotationData;
import com.choosemuse.libmuse.ConnectionState;
import com.choosemuse.libmuse.Eeg;
import com.choosemuse.libmuse.LibmuseVersion;
import com.choosemuse.libmuse.MessageType;
import com.choosemuse.libmuse.Muse;
import com.choosemuse.libmuse.MuseArtifactPacket;
import com.choosemuse.libmuse.MuseConfiguration;
import com.choosemuse.libmuse.MuseConnectionListener;
import com.choosemuse.libmuse.MuseConnectionPacket;
import com.choosemuse.libmuse.MuseDataListener;
import com.choosemuse.libmuse.MuseDataPacket;
import com.choosemuse.libmuse.MuseDataPacketType;
import com.choosemuse.libmuse.MuseFileFactory;
import com.choosemuse.libmuse.MuseFileReader;
import com.choosemuse.libmuse.MuseFileWriter;
import com.choosemuse.libmuse.MuseListener;
import com.choosemuse.libmuse.MuseManagerAndroid;
import com.choosemuse.libmuse.MuseVersion;
import com.choosemuse.libmuse.Result;
import com.choosemuse.libmuse.ResultLevel;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Looper;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.bluetooth.BluetoothAdapter;

//http stuff
import org.json.JSONObject;

import okhttp3.*;
import java.io.IOException;



public class MainActivity extends AppCompatActivity implements OnClickListener {
    // to detect a Muse
    private MuseManagerAndroid manager;
    // to register listeners
    private Muse muse;
    // inner class to detect change in connection state
    private ConnectionListener connectionListener;
    // inner class: recieve data from Muse
    private DataListener dataListener;
    //buffer for scores
    private final double[] alphaScBuffer = new double[6];
    private boolean alphaScStale;
    private final double[] betaScBuffer = new double[6];
    private boolean betaScStale;
    private final double[] thetaScBuffer = new double[6];
    private boolean thetaScStale;
    private final double[] deltaScBuffer = new double[6];
    private boolean deltaScStale;
    private final double[] gammaScBuffer = new double[6];
    private boolean gammaScStale;
    //buffer for Artifacts
    private final boolean[] artifactBuffer = new boolean[3];
    private boolean artifactStale;
    //handles UI updates?
    private final Handler handler = new Handler();
    //to contain MAC addresses of Muses
    private ArrayAdapter<String> spinnerAdapter;

    //create http values
    //NOTE: These only work for my Hue, you'll need to find your own bridge's IP address and create a whitelisted username to make requests
    public static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    public static final String hueJSON = ("{\"alert\":\"select\"}");
    public static final String hueURL = ("http://192.168.2.15/api/t7GebakdmYOEFltD6NutJZOqkU25cuHAE-0gkTwk/lights/2/state");
    OkHttpClient client = new OkHttpClient();



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //required according to LibMuse
        manager=MuseManagerAndroid.getInstance();
        manager.setContext(this);

        //fully copied from example
        WeakReference<MainActivity> weakActivity =
                new WeakReference<MainActivity>(this);
        // Register a listener to receive connection state changes.
        connectionListener = new ConnectionListener(weakActivity);
        // Register a listener to receive data from a Muse.
        dataListener = new DataListener(weakActivity);
        // Register a listener to receive notifications of what Muse headbands
        // we can connect to.
        manager.setMuseListener(new MuseL(weakActivity));

        // Muse 2016 (MU-02) headbands use Bluetooth Low Energy technology to
        // simplify the connection process.  This requires access to the COARSE_LOCATION
        // or FINE_LOCATION permissions.  Make sure we have these permissions before
        // proceeding.
        ensurePermissions();

        // Load and initialize our UI.
        initUI();

        // Start our asynchronous updates of the UI.
        handler.post(tickUi);



    }
    //assuming I need this
    public boolean isBluetoothEnabled() {
        return BluetoothAdapter.getDefaultAdapter().isEnabled();
    }
    //all the buttons on the UI
    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.refresh) {
            // The user has pressed the "Refresh" button.
            // Start listening for nearby or paired Muse headbands. We call stopListening
            // first to make sure startListening will clear the list of headbands and start fresh.
            manager.stopListening();
            manager.startListening();

        } else if (v.getId() == R.id.connect) {

            // The user has pressed the "Connect" button to connect to
            // the headband in the spinner.

            // Listening is an expensive operation, so now that we know
            // which headband the user wants to connect to we can stop
            // listening for other headbands.
            manager.stopListening();

            List<Muse> availableMuses = manager.getMuses();
            Spinner musesSpinner = (Spinner) findViewById(R.id.muses_spinner);

            // Check that we actually have something to connect to.
            if (availableMuses.size() < 1 || musesSpinner.getAdapter().getCount() < 1) {
            } else {

                // Cache the Muse that the user has selected.
                muse = availableMuses.get(musesSpinner.getSelectedItemPosition());
                // Unregister all prior listeners and register our data listener to
                // receive the MuseDataPacketTypes we are interested in.  If you do
                // not register a listener for a particular data type, you will not
                // receive data packets of that type.
                muse.unregisterAllListeners();
                muse.registerConnectionListener(connectionListener);
                muse.registerDataListener(dataListener, MuseDataPacketType.ARTIFACTS);
                muse.registerDataListener(dataListener, MuseDataPacketType.ALPHA_SCORE);
                muse.registerDataListener(dataListener, MuseDataPacketType.THETA_SCORE);
                muse.registerDataListener(dataListener, MuseDataPacketType.GAMMA_SCORE);
                muse.registerDataListener(dataListener, MuseDataPacketType.DELTA_SCORE);
                muse.registerDataListener(dataListener, MuseDataPacketType.BETA_SCORE);

                // Initiate a connection to the headband and stream the data asynchronously.
                muse.runAsynchronously();
            }

        } else if (v.getId() == R.id.disconnect) {

            // The user has pressed the "Disconnect" button.
            // Disconnect from the selected Muse.
            if (muse != null) {
                muse.disconnect();
            }

        }
    }
    //--------------------------------------
    // Permissions (copied from example)

    /**
     * The ACCESS_COARSE_LOCATION permission is required to use the
     * Bluetooth Low Energy library and must be requested at runtime for Android 6.0+
     * On an Android 6.0 device, the following code will display 2 dialogs,
     * one to provide context and the second to request the permission.
     * On an Android device running an earlier version, nothing is displayed
     * as the permission is granted from the manifest.
     *
     * If the permission is not granted, then Muse 2016 (MU-02) headbands will
     * not be discovered and a SecurityException will be thrown.
     */
    private void ensurePermissions() {

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
        {
            // We don't have the ACCESS_COARSE_LOCATION permission so create the dialogs asking
            // the user to grant us the permission.

            DialogInterface.OnClickListener buttonListener =
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which){
                            dialog.dismiss();
                            ActivityCompat.requestPermissions(MainActivity.this,
                                    new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                                    0);
                        }
                    };

            // This is the context dialog which explains to the user the reason we are requesting
            // this permission.  When the user presses the positive (I Understand) button, the
            // standard Android permission dialog will be displayed (as defined in the button
            // listener above).
            AlertDialog introDialog = new AlertDialog.Builder(this)
                    .setTitle(R.string.permission_dialog_title)
                    .setMessage(R.string.permission_dialog_description)
                    .setPositiveButton(R.string.permission_dialog_understand, buttonListener)
                    .create();
            introDialog.show();
        }
    }

    //--------------------------------------
    // Listeners

    /**
     * You will receive a callback to this method each time a headband is discovered.
     * In this example, we update the spinner with the MAC address of the headband.
     */
    public void museListChanged() {
        final List<Muse> list = manager.getMuses();
        spinnerAdapter.clear();
        for (Muse m : list) {
            spinnerAdapter.add(m.getName() + " - " + m.getMacAddress());
        }
    }

    /**
     * You will receive a callback to this method each time there is a change to the
     * connection state of one of the headbands.
     * @param p     A packet containing the current and prior connection states
     * @param muse  The headband whose state changed.
     */
    public void receiveMuseConnectionPacket(final MuseConnectionPacket p, final Muse muse) {

        final ConnectionState current = p.getCurrentConnectionState();

        // Format a message to show the change of connection state in the UI.
        final String status = p.getPreviousConnectionState() + " -> " + current;

        // Update the UI with the change in connection state.
        handler.post(new Runnable() {
            @Override
            public void run() {

                final TextView statusText = (TextView) findViewById(R.id.con_status);
                statusText.setText(status);


            }
        });



    }
    //blank method to satisfy DataListener Class
    public void receiveMuseDataPacket(final MuseDataPacket p, final Muse muse) {
        // valuesSize returns the number of data values contained in the packet.
        final long n = p.valuesSize();
        switch (p.packetType()) {
            case ALPHA_SCORE:
                assert (alphaScBuffer.length >= n);
                getEegChannelValues(alphaScBuffer, p);
                alphaScStale = true;
                break;
            case BETA_SCORE:
                assert (betaScBuffer.length >= n);
                getEegChannelValues(betaScBuffer, p);
                betaScStale = true;
                break;
            case GAMMA_SCORE:
                assert (gammaScBuffer.length >= n);
                getEegChannelValues(gammaScBuffer, p);
                gammaScStale = true;
                break;
            case DELTA_SCORE:
                assert (deltaScBuffer.length >= n);
                getEegChannelValues(deltaScBuffer, p);
                deltaScStale = true;
                break;
            case THETA_SCORE:
                assert (thetaScBuffer.length >= n);
                getEegChannelValues(thetaScBuffer, p);
                thetaScStale = true;
                break;
            default:
                break;
        }
    }
    /**
     * You will receive a callback to this method each time an artifact packet is generated if you
     * have registered for the ARTIFACTS data type.  MuseArtifactPackets are generated when
     * eye blinks are detected, the jaw is clenched and when the headband is put on or removed.
     * @param p     The artifact packet with the data from the headband.
     * @param muse  The headband that sent the information.
     */
    public void receiveMuseArtifactPacket(final MuseArtifactPacket p, final Muse muse) {
        getArtifactValues(p);
        artifactStale = true;
    }

    //Helper to get Artifact Packets
    private void getArtifactValues(MuseArtifactPacket p) {
        artifactBuffer[0] = p.getBlink();
        artifactBuffer[1] = p.getHeadbandOn();
        artifactBuffer[2] = p.getJawClench();
    }
        //get eeg packets
    private void getEegChannelValues(double[] buffer, MuseDataPacket p) {
        buffer[0] = p.getEegChannelValue(Eeg.EEG1);
        buffer[1] = p.getEegChannelValue(Eeg.EEG2);
        buffer[2] = p.getEegChannelValue(Eeg.EEG3);
        buffer[3] = p.getEegChannelValue(Eeg.EEG4);
        buffer[4] = p.getEegChannelValue(Eeg.AUX_LEFT);
        buffer[5] = p.getEegChannelValue(Eeg.AUX_RIGHT);
    }

    //OkHTTP method
    Call putRequest(String url, String jsonBody, Callback callback) {

        RequestBody body = RequestBody.create(JSON, jsonBody);
        Request request = new Request.Builder()
                .url(url)
                .put(body)
                .build();

        Call call = client.newCall(request);
        call.enqueue(callback);
        return call;
    }

    //--------------------------------------
    // UI Specific methods

    /**
     * Initializes the UI of the example application.
     */
    private void initUI() {
        setContentView(R.layout.activity_main);
        Button refreshButton = (Button) findViewById(R.id.refresh);
        refreshButton.setOnClickListener(this);
        Button connectButton = (Button) findViewById(R.id.connect);
        connectButton.setOnClickListener(this);
        Button disconnectButton = (Button) findViewById(R.id.disconnect);
        disconnectButton.setOnClickListener(this);

        spinnerAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item);
        Spinner musesSpinner = (Spinner) findViewById(R.id.muses_spinner);
        musesSpinner.setAdapter(spinnerAdapter);
    }

    /**
     * The runnable that is used to update the UI at 60Hz.
     *
     * We update the UI from this Runnable instead of in packet handlers
     * because packets come in at high frequency -- 220Hz or more for raw EEG
     * -- and it only makes sense to update the UI at about 60fps. The update
     * functions do some string allocation, so this reduces our memory
     * footprint and makes GC pauses less frequent/noticeable.
     */
    private final Runnable tickUi = new Runnable() {
        @Override
        public void run() {
            if (artifactStale) {
                updateArtifact();
            }
            if (alphaScStale) {
                updateAlphaSc();
            }
            if (betaScStale) {
                updateBetaSc();
            }
            if (thetaScStale) {
                updateThetaSc();
            }
            if (deltaScStale) {
                updateDeltaSc();
            }
            if (gammaScStale) {
                updateGammaSc();
            }
            handler.postDelayed(tickUi, 1000 / 60);
        }
    };

    /**
     * The following methods update the TextViews in the UI with the data
     * from the buffers.
     */
    private void updateArtifact() {
        TextView blink = (TextView) findViewById(R.id.blink);
        TextView headband = (TextView) findViewById(R.id.headband);
        TextView jaw = (TextView) findViewById(R.id.jaw);
        blink.setText(String.valueOf(artifactBuffer[0]));
        headband.setText(String.valueOf(artifactBuffer[1]));
        jaw.setText(String.valueOf(artifactBuffer[2]));

        //execute PUT if blink is true
        if (artifactBuffer[0]) {
            putRequest(hueURL, hueJSON, new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {

                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful()) {
                        //String responseStr = response.body().string();
                        // TextView res = (TextView)findViewById(R.id.res);
                        // res.setText(responseStr);
                    }
                }
            });
        }
    }
    private void updateAlphaSc() {
        TextView A1 = (TextView)findViewById(R.id.A1);
        A1.setText(String.format("%6.2f", alphaScBuffer[0]));
        TextView A2 = (TextView)findViewById(R.id.A2);
        A2.setText(String.format("%6.2f", alphaScBuffer[1]));
        TextView A3 = (TextView)findViewById(R.id.A3);
        A3.setText(String.format("%6.2f", alphaScBuffer[2]));
        TextView A4 = (TextView)findViewById(R.id.A4);
        A4.setText(String.format("%6.2f", alphaScBuffer[3]));
    }
    private void updateThetaSc() {
        TextView T1 = (TextView)findViewById(R.id.T1);
        T1.setText(String.format("%6.2f", thetaScBuffer[0]));
        TextView T2 = (TextView)findViewById(R.id.T2);
        T2.setText(String.format("%6.2f", thetaScBuffer[1]));
        TextView T3 = (TextView)findViewById(R.id.T3);
        T3.setText(String.format("%6.2f", thetaScBuffer[2]));
        TextView T4 = (TextView)findViewById(R.id.T4);
        T4.setText(String.format("%6.2f", thetaScBuffer[3]));
    }
    private void updateDeltaSc() {
        TextView D1 = (TextView)findViewById(R.id.D1);
        D1.setText(String.format("%6.2f", deltaScBuffer[0]));
        TextView D2 = (TextView)findViewById(R.id.D2);
        D2.setText(String.format("%6.2f", deltaScBuffer[1]));
        TextView D3 = (TextView)findViewById(R.id.D3);
        D3.setText(String.format("%6.2f", deltaScBuffer[2]));
        TextView D4 = (TextView)findViewById(R.id.D4);
        D4.setText(String.format("%6.2f", deltaScBuffer[3]));
    }
    private void updateGammaSc() {
        TextView G1 = (TextView)findViewById(R.id.G1);
        G1.setText(String.format("%6.2f", gammaScBuffer[0]));
        TextView G2 = (TextView)findViewById(R.id.G2);
        G2.setText(String.format("%6.2f", gammaScBuffer[1]));
        TextView G3 = (TextView)findViewById(R.id.G3);
        G3.setText(String.format("%6.2f", gammaScBuffer[2]));
        TextView G4 = (TextView)findViewById(R.id.G4);
        G4.setText(String.format("%6.2f", gammaScBuffer[3]));
    }
    private void updateBetaSc() {
        TextView B1 = (TextView)findViewById(R.id.B1);
        B1.setText(String.format("%6.2f", betaScBuffer[0]));
        TextView B2 = (TextView)findViewById(R.id.B2);
        B2.setText(String.format("%6.2f", betaScBuffer[1]));
        TextView B3 = (TextView)findViewById(R.id.B3);
        B3.setText(String.format("%6.2f", betaScBuffer[2]));
        TextView B4 = (TextView)findViewById(R.id.B4);
        B4.setText(String.format("%6.2f", betaScBuffer[3]));
    }
    




    //--------------------------------------
// Listener translators
//
// Each of these classes extend from the appropriate listener and contain a weak reference
// to the activity.  Each class simply forwards the messages it receives back to the Activity.
class MuseL extends MuseListener {
    final WeakReference<MainActivity> activityRef;

    MuseL(final WeakReference<MainActivity> activityRef) {
        this.activityRef = activityRef;
    }

    @Override
    public void museListChanged() {
        activityRef.get().museListChanged();
    }
}

class ConnectionListener extends MuseConnectionListener {
    final WeakReference<MainActivity> activityRef;

    ConnectionListener(final WeakReference<MainActivity> activityRef) {
        this.activityRef = activityRef;
    }

    @Override
    public void receiveMuseConnectionPacket(final MuseConnectionPacket p, final Muse muse) {
        activityRef.get().receiveMuseConnectionPacket(p, muse);
    }
}

class DataListener extends MuseDataListener {
    final WeakReference<MainActivity> activityRef;

    DataListener(final WeakReference<MainActivity> activityRef) {
        this.activityRef = activityRef;
    }

    @Override
    public void receiveMuseDataPacket(final MuseDataPacket p, final Muse muse) {
        activityRef.get().receiveMuseDataPacket(p, muse);
    }

    @Override
    public void receiveMuseArtifactPacket(final MuseArtifactPacket p, final Muse muse) {
        activityRef.get().receiveMuseArtifactPacket(p, muse);
    }
}
}
