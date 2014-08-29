/*
 * MainActivity.java
 *
 * Copyright (c) 2014, Stefan Petscharnig. All rights reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301 USA
 */
package mf.player.gui;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import mf.player.gui.Samples.Sample;
import mf.sync.net.MessageHandler;
import mf.sync.utils.SessionInfo;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

	Map<String, String> videos;

	public static final String SERVER_ADDRESS = "https://demo-itec.uni-klu.ac.at/livelab/mf/session/";

	public final static String TAG = "MainActivity";
	public static Context c = null;

	public void altMedia(View v) {

		AlertDialog.Builder alert = new AlertDialog.Builder(this);

		alert.setTitle("Alternative media");
		alert.setMessage("Enter an alternative media source");

		final LinearLayout layout = new LinearLayout(this);

		final LinearLayout r1 = new LinearLayout(this);
		final LinearLayout r2 = new LinearLayout(this);

		final EditText name = new EditText(this);
		final EditText uri = new EditText(this);
		final TextView mName = new TextView(this);
		final TextView mUri = new TextView(this);

		name.setLayoutParams(new LinearLayout.LayoutParams(
				LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT));
		uri.setLayoutParams(new LinearLayout.LayoutParams(
				LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT));
		mName.setLayoutParams(new LinearLayout.LayoutParams(
				LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
		mUri.setLayoutParams(new LinearLayout.LayoutParams(
				LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

		mName.setText("media name: ");
		mUri.setText("uri: ");

		layout.setOrientation(LinearLayout.VERTICAL);
		r1.setOrientation(LinearLayout.HORIZONTAL);
		r2.setOrientation(LinearLayout.HORIZONTAL);

		layout.addView(r1);
		layout.addView(r2);
		r1.addView(mName);
		r1.addView(name);
		r2.addView(mUri);
		r2.addView(uri);

		alert.setView(layout);

		alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int whichButton) {
				String value = uri.getText().toString();
				String value1 = name.getText().toString();
				Spinner fileSpinner = (Spinner) findViewById(R.id.fileChooser_main);
				@SuppressWarnings("unchecked")
				ArrayAdapter<Sample> adap = (ArrayAdapter<Sample>) fileSpinner
						.getAdapter();
				adap.add(new Sample(value1, value));
				Toast.makeText(
						layout.getContext(),
						"you new media was added to the selectable videos for this session",
						Toast.LENGTH_LONG).show();
			}
		});

		alert.setNegativeButton("Cancel",
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int whichButton) {
						// Canceled.
					}
				});

		alert.show();
	}

	private void initChoosableVideos() {

		List<Sample> list = new ArrayList<Sample>();

		EditText e = (EditText) findViewById(R.id.serverAddressET_main);
		e.setText(SERVER_ADDRESS);
		for (Sample s : Samples.DASH) {
			list.add(s);
		}

		ArrayAdapter<Sample> adap = new ArrayAdapter<Sample>(this,
				android.R.layout.simple_spinner_item, list);
		Spinner fileSpinner = (Spinner) findViewById(R.id.fileChooser_main);
		fileSpinner.setAdapter(adap);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		c = this;
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		initChoosableVideos();
	}

	@Override
	protected void onDestroy() {
		SessionInfo.getInstance().log("destoy main activity");
		super.onDestroy();
	}

	public void onOpenButtonClick(View openButton) {

		Sample selection = (Sample) ((Spinner) findViewById(R.id.fileChooser_main))
				.getSelectedItem();

		String uri = selection.uri;

		EditText e = (EditText) findViewById(R.id.serverAddressET_main);
		EditText e1 = (EditText) findViewById(R.id.sessionKeyET_main);
		String srv = e.getText().toString();
		String sKey = e1.getText().toString();
		boolean sD = ((CheckBox) findViewById(R.id.sDebug)).isChecked();

		uri = srv + "simsServer.php?port=" + MessageHandler.PORT
				+ "&mediaSource=" + uri + "&session_key=" + sKey + "&ip="
				+ SessionInfo.getWifiAddress(this).getHostAddress();

		Intent mpdIntent = new Intent(this, PlayerActivity.class)
				.setData(Uri.parse(uri))
				.putExtra(DemoUtil.CONTENT_ID_EXTRA, "")
				.putExtra(DemoUtil.CONTENT_TYPE_EXTRA, DemoUtil.TYPE_DASH_VOD)
				.putExtra("showDebug", sD);
		startActivity(mpdIntent);

	}

	public void onSessionListClick(View v) {
		final StringBuffer sb = new StringBuffer(";");
		AsyncTask<String, Void, String> a = new AsyncTask<String, Void, String>() {

			@Override
			protected String doInBackground(String... params) {
				URL url;

				try {
					url = new URL(params[0] + "listSessions.php");

					HttpURLConnection urlConnection = (HttpURLConnection) url
							.openConnection();
					InputStream in = new BufferedInputStream(
							urlConnection.getInputStream());
					BufferedReader br = new BufferedReader(
							new InputStreamReader(in));
					String sList = br.readLine();

					sb.append(sList);
					urlConnection.disconnect();
					br.close();
				} catch (Exception e) {
					e.printStackTrace();
				}
				return null;
			}
		};
		a.execute(((EditText) findViewById(R.id.serverAddressET_main))
				.getText().toString());
		/* TODO: do it the clean way... */
		do {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		} while (";".equals(sb.toString()));

		/* show dialog to choose the session */

		AlertDialog.Builder alert = new AlertDialog.Builder(MainActivity.this);

		alert.setTitle("Open Session");
		final ArrayAdapter<String> arrayAdapter = new ArrayAdapter<String>(
				MainActivity.this, android.R.layout.select_dialog_singlechoice);
		for (String s : sb.toString().split(";")) {
			if (!"".equals(s)) {
				arrayAdapter.add(s);
			}
		}
		alert.setNegativeButton("cancel",
				new DialogInterface.OnClickListener() {

					@Override
					public void onClick(DialogInterface dialog, int which) {
						dialog.dismiss();
					}
				});
		alert.setAdapter(arrayAdapter, new DialogInterface.OnClickListener() {

			@Override
			public void onClick(DialogInterface dialog, int which) {
				String sessionKey = arrayAdapter.getItem(which);
				((EditText) findViewById(R.id.sessionKeyET_main))
						.setText(sessionKey);
			}
		});
		alert.show();

	}
}