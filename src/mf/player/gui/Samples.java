/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package mf.player.gui;

/**
 * Holds statically defined sample definitions.
 */
/* package */class Samples {

	public static class Sample {

		public final String name;
		public final String uri;

		public Sample(String name, String uri) {
			this.name = name;
			this.uri = uri;
		}

		@Override
		public String toString() {
			return name;
		}

	}

	public static final Sample[] DASH = new Sample[] {
			// new Sample("tos",
			// "https://demo-itec.aau.at/livelab/maiero/testCases/tos_mux_dash.mpd"),
			// new Sample("bunny",
			// "https://demo-itec.aau.at/livelab/maiero/testCases/bbb_mux_dash.mpd"),
			// new Sample("demo",
			// "http://demo.unified-streaming.com/video/ateam/ateam.ism/ateam.mpd"),
			// new Sample("demo",
			// "http://dash.edgesuite.net/envivio/dashpr/clear/Manifest.mpd"),
			new Sample("Google Glasses",
					"https://demo-itec.aau.at/livelab/mf/glass.mpd"),
			new Sample("Google Play",
					"https://demo-itec.aau.at/livelab/mf/play.mpd"), };

	private Samples() {
	}

}
