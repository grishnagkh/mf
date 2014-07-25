package mf.player.gui;

import java.util.ArrayList;
import java.util.List;

import mf.at.itec.R;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

public class MainActivity extends Activity {

	public static final String BIG_BUCK = "BigBuckBunny_2s_isoffmain_DIS_23009_1_v_2_1c2_2011_08_30.mpd";
	public static final String ED = "ElephantsDream_2s_isoffmain_DIS_23009_1_v_2_1c2_2011_08_30.mpd";

	public static final String URI_GLASS = "http://www.youtube.com/api/manifest/dash/id/bf5bb2419360daf1/source/youtube?"
			+ "as=fmp4_audio_clear,fmp4_sd_hd_clear&sparams=ip,ipbits,expire,as&ip=0.0.0.0&"
			+ "ipbits=0&expire=19000000000&signature=255F6B3C07C753C88708C07EA31B7A1A10703C8D."
			+ "2D6A28B21F921D0B245CDCF36F7EB54A2B5ABFC2&key=ik0";

	public static final String URI_PLAY = "http://www.youtube.com/api/manifest/dash/id/3aa39fa2cc27967f/source/youtube?"
			+ "as=fmp4_audio_clear,fmp4_sd_hd_clear&sparams=ip,ipbits,expire,as&ip=0.0.0.0&ipbits=0&"
			+ "expire=19000000000&signature=7181C59D0252B285D593E1B61D985D5B7C98DE2A."
			+ "5B445837F55A40E0F28AACAA047982E372D177E2&key=ik0";

	public static final String URI_BUNNY = "http://rdmedia.bbc.co.uk/dash/ondemand/bbb/avc3/1/client_manifest-common_init.mpd";

	public static final String BUNNY_ID = "Big Buck Bunny";
	public static final String GLASS_ID = "Google Glasses";
	public static final String PLAY_ID = "Google Play";

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
		list.add(GLASS_ID);
		list.add(PLAY_ID);
		list.add(BUNNY_ID);

		ArrayAdapter<String> adap = new ArrayAdapter<String>(this,
				android.R.layout.simple_spinner_item, list);

		Spinner fileSpinner = (Spinner) findViewById(R.id.fileChooser_main);

		fileSpinner.setAdapter(adap);
	}

	public void onOpenButtonClick(View openButton) {

		String selection = ((Spinner) findViewById(R.id.fileChooser_main))
				.getSelectedItem().toString();

		String uri;
		if (GLASS_ID.equals(selection)) {
			uri = URI_GLASS;
		} else if (PLAY_ID.equals(selection)) {
			uri = URI_PLAY;
		} else if (BUNNY_ID.equals(selection)) {
			uri = URI_BUNNY;
		} else {
			uri = URI_PLAY;
		}
		uri = "http://grishnagkh.files.wordpress.com/2014/07/test2.key";
		
		Intent mpdIntent = new Intent(this, PlayerActivity.class)
				.setData(Uri.parse(uri))
				.putExtra(DemoUtil.CONTENT_ID_EXTRA, "")
				.putExtra(DemoUtil.CONTENT_TYPE_EXTRA, DemoUtil.TYPE_DASH_VOD);
		startActivity(mpdIntent);

	}
}