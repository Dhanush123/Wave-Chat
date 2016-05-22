package com.jonas.stopcollaboratelisten;

/*
 * 	This is an Android application based on the pentatonic codec
 *  available at:
 *    http://github.com/diva/digital-voices
 *    
 *  The app uses an acoustic channel for device-to-device communication.
 *  When a broadcast is incorrectly decoded the receiver will signal
 *  for help at which point another device with the correct broadcast
 *  will automatically lend a hand.
 * 
 * Modfied April 2012 by jonasrmichel@mail.utexas.edu
 * 
 * Usage notes:
 * 
 *  - Type something in and hit Play to have it encoded and played
 *  
 *  - Hit Listen to start a collaborative listen session
 *  	A status message will appear below the Listen button.
 *  	When input is decoded, it'll show up below that.
 *  
 */

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.TextView;

import com.google.android.gms.appindexing.Action;
import com.google.android.gms.appindexing.AppIndex;
import com.google.android.gms.common.api.GoogleApiClient;
import com.jonas.stopcollaboratelisten.SessionService.SessionStatus;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class Main extends AppCompatActivity {
	/**
	 * Called when the activity is first created.
	 */
	// Loopback l = null;

	private SessionService mSessionService;
	private boolean mIsBound;

	private ServiceConnection mSessionConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			// This is called when the connection with the service has been
			// established, giving us the service object we can use to
			// interact with the service. Because we have bound to a explicit
			// service that we know is running in our own process, we can
			// cast its IBinder to a concrete class and directly access it.
			mSessionService = ((SessionService.SessionBinder) service).getService();
		}

		public void onServiceDisconnected(ComponentName className) {
			// This is called when the connection with the service has been
			// unexpectedly disconnected -- that is, its process crashed.
			// Because it is running in our same process, we should never
			// see this happen.
			mSessionService = null;
		}
	};
	/**
	 * ATTENTION: This was auto-generated to implement the App Indexing API.
	 * See https://g.co/AppIndexing/AndroidStudio for more information.
	 */
	private GoogleApiClient client;

	void doBindService() {
		// Establish a connection with the service. We use an explicit
		// class name because we want a specific service implementation that
		// we know will be running in our own process (and thus won't be
		// supporting component replacement by other applications).
		bindService(new Intent(this, SessionService.class), mSessionConnection,
				Context.BIND_AUTO_CREATE);
		mIsBound = true;
	}

	void doUnbindService() {
		if (mIsBound) {
			// Detach our existing connection.
			unbindService(mSessionConnection);
			mIsBound = false;
		}
	}

	Timer refreshTimer = null;

	Handler mHandler = new Handler();

	TextView textStatus, textListen;

	List<RadioButton> radioButtons = new ArrayList<RadioButton>();

	Uri mCreateDataUri = null;
	String mCreateDataType = null;
	String mCreateDataExtraText = null;
	private Toolbar toolbar;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		toolbar = (Toolbar) findViewById(R.id.my_toolbar); // Attaching the layout to the toolbar object
		setSupportActionBar(toolbar);                   // Setting toolbar as the ActionBar with setSupportActionBar() call
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
			getWindow().setStatusBarColor(getResources().getColor(R.color.colorPrimaryDark));
		}
		getSupportActionBar().setTitle("Photo Tagger");

		textStatus = (TextView) findViewById(R.id.TextStatus);
		textListen = (TextView) findViewById(R.id.TextListen);

		Button t = (Button) findViewById(R.id.ButtonPlay);
		t.setOnClickListener(mPlayListener);

		t = (Button) findViewById(R.id.ButtonListen);
		t.setOnClickListener(mListenListener);

		radioButtons.add((RadioButton) findViewById(R.id.RadioPlaying));
		radioButtons.add((RadioButton) findViewById(R.id.RadioListening));
		radioButtons.add((RadioButton) findViewById(R.id.RadioHelping));
		radioButtons.add((RadioButton) findViewById(R.id.RadioSOS));
		radioButtons.add((RadioButton) findViewById(R.id.RadioFinished));

		for (RadioButton button : radioButtons) {
			button.setOnCheckedChangeListener(new OnCheckedChangeListener() {

				public void onCheckedChanged(CompoundButton buttonView,
											 boolean isChecked) {
					if (isChecked)
						processRadioButtonClick(buttonView);
				}
			});
		}

		final Intent intent = getIntent();
		final String action = intent.getAction();
		if (Intent.ACTION_SEND.equals(action)) {

			mCreateDataUri = intent.getData();
			mCreateDataType = intent.getType();

			if (mCreateDataUri == null) {
				mCreateDataUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);

			}

			mCreateDataExtraText = intent.getStringExtra(Intent.EXTRA_TEXT);

			if (mCreateDataUri == null)
				mCreateDataType = null;

			// The new entry was created, so assume all will end well and
			// set the result to be returned.
			setResult(RESULT_OK, (new Intent()).setAction(null));
		}

		doBindService();
		// ATTENTION: This was auto-generated to implement the App Indexing API.
		// See https://g.co/AppIndexing/AndroidStudio for more information.
		client = new GoogleApiClient.Builder(this).addApi(AppIndex.API).build();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		doUnbindService();
	}

	View.OnClickListener mPlayListener = new View.OnClickListener() {
		public void onClick(View v) {
			EditText e = (EditText) findViewById(R.id.EditTextToPlay);
			String s = e.getText().toString();
			mSessionService.startSession(s);
		}
	};

	View.OnClickListener mListenListener = new View.OnClickListener() {
		public void onClick(View v) {
			mSessionService.sessionReset();
			if (mSessionService.getStatus() == SessionStatus.NONE
					|| mSessionService.getStatus() == SessionStatus.FINISHED) {
				mSessionService.listen();
				((Button) v).setText("Stop listening");
			} else {
				mSessionService.stopListening();
				((Button) v).setText("Listen");
			}

		}
	};

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onPause()
	 */
	@Override
	protected void onPause() {

		// if( l != null )
		// l.stopLoop();

		super.onPause();

		if (refreshTimer != null) {
			refreshTimer.cancel();
			refreshTimer = null;
		}

		mSessionService.stopListening();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onResume()
	 */
	@Override
	protected void onResume() {
		super.onResume();

		String sent = null;

		if (mCreateDataExtraText != null) {
			sent = mCreateDataExtraText;
		} else if (mCreateDataType != null
				&& mCreateDataType.startsWith("text/")) {
			// read the URI into a string

			byte[] b = readDataFromUri(this.mCreateDataUri);
			if (b != null)
				sent = new String(b);

		}

		if (sent != null) {
			EditText e = (EditText) findViewById(R.id.EditTextToPlay);
			e.setText(sent);
		}

		refreshTimer = new Timer();

		refreshTimer.schedule(new TimerTask() {
			@Override
			public void run() {

				mHandler.post(new Runnable() // have to do this on the UI thread
				{
					public void run() {
						updateResults();
					}
				});

			}
		}, 500, 500);

	}

	private void processRadioButtonClick(CompoundButton buttonView) {
		for (RadioButton button : radioButtons) {
			if (button != buttonView)
				button.setChecked(false);
		}
	}

	private void setRadioGroupUnchecked() {
		for (RadioButton button : radioButtons) {
			button.setChecked(false);
		}
	}

	private void setRadioGroupChecked(SessionStatus s) {
		RadioButton rb = null;
		switch (s) {
			case PLAYING:
				rb = (RadioButton) findViewById(R.id.RadioPlaying);
				break;
			case LISTENING:
				rb = (RadioButton) findViewById(R.id.RadioListening);
				break;
			case HELPING:
				rb = (RadioButton) findViewById(R.id.RadioHelping);
				break;
			case SOS:
				rb = (RadioButton) findViewById(R.id.RadioSOS);
				break;
			case FINISHED:
				rb = (RadioButton) findViewById(R.id.RadioFinished);
				break;
			case NONE:
				setRadioGroupUnchecked();
				return;
		}
		rb.setChecked(true);
	}

	private void updateResults() {
		if (mSessionService.getStatus() == SessionStatus.LISTENING) {
			textStatus.setText(mSessionService.getBacklogStatus());
			textListen.setText(mSessionService.getListenString());

			Button b = (Button) findViewById(R.id.ButtonListen);
			b.setText("Stop listening");
		} else if (mSessionService.getStatus() == SessionStatus.FINISHED) {
			Button b = (Button) findViewById(R.id.ButtonListen);
			b.setText("Listen");
			textStatus.setText("");
		} else {
			textStatus.setText("");
		}
		setRadioGroupChecked(mSessionService.getStatus());
	}

	/*
	 * private void encode( String inputFile, String outputFile ) {
	 * 
	 * try {
	 * 
	 * //There was an output file specified, so we should write the wav
	 * System.out.println("Encoding " + inputFile);
	 * AudioUtils.encodeFileToWav(new File(inputFile), new File(outputFile));
	 * 
	 * } catch (Exception e) { System.out.println("Could not encode " +
	 * inputFile + " because of " + e); }
	 * 
	 * }
	 */

	private byte[] readDataFromUri(Uri uri) {
		byte[] buffer = null;

		try {
			InputStream stream = getContentResolver().openInputStream(uri);

			int bytesAvailable = stream.available();
			// int maxBufferSize = 1024;
			int bufferSize = bytesAvailable; // Math.min(bytesAvailable,
			// maxBufferSize);
			int totalRead = 0;
			buffer = new byte[bufferSize];

			// read file and write it into form...
			int bytesRead = stream.read(buffer, 0, bufferSize);
			while (bytesRead > 0) {
				bytesRead = stream.read(buffer, totalRead, bufferSize);
				totalRead += bytesRead;
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();

		}

		return buffer;
	}

	@Override
	public void onStart() {
		super.onStart();

		// ATTENTION: This was auto-generated to implement the App Indexing API.
		// See https://g.co/AppIndexing/AndroidStudio for more information.
		client.connect();
		Action viewAction = Action.newAction(
				Action.TYPE_VIEW, // TODO: choose an action type.
				"Main Page", // TODO: Define a title for the content shown.
				// TODO: If you have web page content that matches this app activity's content,
				// make sure this auto-generated web page URL is correct.
				// Otherwise, set the URL to null.
				Uri.parse("http://host/path"),
				// TODO: Make sure this auto-generated app URL is correct.
				Uri.parse("android-app://com.jonas.stopcollaboratelisten/http/host/path")
		);
		AppIndex.AppIndexApi.start(client, viewAction);
	}

	@Override
	public void onStop() {
		super.onStop();

		// ATTENTION: This was auto-generated to implement the App Indexing API.
		// See https://g.co/AppIndexing/AndroidStudio for more information.
		Action viewAction = Action.newAction(
				Action.TYPE_VIEW, // TODO: choose an action type.
				"Main Page", // TODO: Define a title for the content shown.
				// TODO: If you have web page content that matches this app activity's content,
				// make sure this auto-generated web page URL is correct.
				// Otherwise, set the URL to null.
				Uri.parse("http://host/path"),
				// TODO: Make sure this auto-generated app URL is correct.
				Uri.parse("android-app://com.jonas.stopcollaboratelisten/http/host/path")
		);
		AppIndex.AppIndexApi.end(client, viewAction);
		client.disconnect();
	}
}