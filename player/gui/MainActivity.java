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

import java.util.ArrayList;
import java.util.List;

import mf.sync.net.MessageHandler;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;

public class MainActivity extends Activity {

	public static final String URI_PLAY = "https://demo-itec.aau.at/livelab/mf/play.mpd";
	public static final String URI_GLASS = "https://demo-itec.aau.at/livelab/mf/glass.mpd";

	public static final String GLASS_ID = "Google Glasses";
	public static final String PLAY_ID = "Google Play";

	public static final String SERVER_ADDRESS = "https://demo-itec.uni-klu.ac.at/livelab/mf/session/simsServer.php";

	public final static String TAG = "MainActivity";
	public static Context c = null;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		c = this;
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		initChoosableVideos();
	}

	private void initChoosableVideos() {
		List<String> list = new ArrayList<String>();

		EditText e = (EditText) findViewById(R.id.serverAddressET_main);
		e.setText(SERVER_ADDRESS);

		list.add(GLASS_ID);
		list.add(PLAY_ID);

		ArrayAdapter<String> adap = new ArrayAdapter<String>(this,
				android.R.layout.simple_spinner_item, list);
		Spinner fileSpinner = (Spinner) findViewById(R.id.fileChooser_main);
		fileSpinner.setAdapter(adap);
	}

	public void onOpenButtonClick(View openButton) {

		String selection = ((Spinner) findViewById(R.id.fileChooser_main))
				.getSelectedItem().toString();

		String uri = "";
		if (PLAY_ID.equals(selection)) {
			uri = URI_PLAY;
		} else if (GLASS_ID.equals(selection)) {
			uri = URI_GLASS;

		}
		EditText e = (EditText) findViewById(R.id.serverAddressET_main);
		EditText e1 = (EditText) findViewById(R.id.sessionKeyET_main);
		String srv = e.getText().toString();
		String sKey = e1.getText().toString();

		uri = srv + "?port=" + MessageHandler.PORT + "&mediaSource=" + uri
				+ "&session_key=" + sKey;

		Intent mpdIntent = new Intent(this, PlayerActivity.class)
				.setData(Uri.parse(uri))
				.putExtra(DemoUtil.CONTENT_ID_EXTRA, "")
				.putExtra(DemoUtil.CONTENT_TYPE_EXTRA, DemoUtil.TYPE_DASH_VOD);
		startActivity(mpdIntent);

	}
}