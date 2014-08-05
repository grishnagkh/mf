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
package mf.com.google.android.exoplayer.dash.mpd;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import mf.com.google.android.exoplayer.ParserException;
import mf.com.google.android.exoplayer.chunk.Format;
import mf.com.google.android.exoplayer.dash.mpd.SegmentBase.SegmentList;
import mf.com.google.android.exoplayer.dash.mpd.SegmentBase.SegmentTemplate;
import mf.com.google.android.exoplayer.dash.mpd.SegmentBase.SegmentTimelineElement;
import mf.com.google.android.exoplayer.dash.mpd.SegmentBase.SingleSegmentBase;
import mf.com.google.android.exoplayer.util.Assertions;
import mf.com.google.android.exoplayer.util.MimeTypes;
import mf.player.gui.MainActivity;
import mf.sync.coarse.CSync;
import mf.sync.net.MessageHandler;
import mf.sync.utils.Peer;
import mf.sync.utils.SessionInfo;
import mf.sync.utils.Utils;

import org.xml.sax.helpers.DefaultHandler;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

/**
 * A parser of media presentation description files.
 */
public class MediaPresentationDescriptionParser extends DefaultHandler {

	// Note: Does not support the date part of ISO 8601
	private static final Pattern DURATION = Pattern
			.compile("^PT(([0-9]*)H)?(([0-9]*)M)?(([0-9.]*)S)?$");

	private static final boolean DEBUG_ON_SCREEN = true;

	private final XmlPullParserFactory xmlParserFactory;

	public MediaPresentationDescriptionParser() {
		try {
			xmlParserFactory = XmlPullParserFactory.newInstance();
		} catch (XmlPullParserException e) {
			throw new RuntimeException(
					"Couldn't create XmlPullParserFactory instance", e);
		}
	}

	// MPD parsing.

	/**
	 * Parses a manifest from the provided {@link InputStream}.
	 *
	 * @param inputStream
	 *            The stream from which to parse the manifest.
	 * @param inputEncoding
	 *            The encoding of the input.
	 * @param contentId
	 *            The content id of the media.
	 * @param baseUrl
	 *            The url that any relative urls defined within the manifest are
	 *            relative to.
	 * @return The parsed manifest.
	 * @throws IOException
	 *             If a problem occurred reading from the stream.
	 * @throws XmlPullParserException
	 *             If a problem occurred parsing the stream as xml.
	 * @throws ParserException
	 *             If a problem occurred parsing the xml as a DASH mpd.
	 */
	public MediaPresentationDescription parseMediaPresentationDescription(
			InputStream inputStream, String inputEncoding, String contentId,
			Uri baseUrl) throws XmlPullParserException, IOException,
			ParserException {

		XmlPullParser xpp = xmlParserFactory.newPullParser();
		xpp.setInput(inputStream, inputEncoding);
		int eventType = xpp.next();
		Log.d("parser", xpp.getName());
		if (eventType != XmlPullParser.START_TAG
				|| !"MPD".equals(xpp.getName())) {
			throw new ParserException(
					"inputStream does not contain a valid media presentation description");
		}
		return parseMediaPresentationDescription(xpp, contentId, baseUrl);
	}

	private MediaPresentationDescription parseMediaPresentationDescription(
			XmlPullParser xpp, String contentId, Uri baseUrl)
			throws XmlPullParserException, IOException {
		long durationMs = parseDurationMs(xpp, "mediaPresentationDuration");
		long minBufferTimeMs = parseDurationMs(xpp, "minBufferTime");
		String typeString = xpp.getAttributeValue(null, "type");
		boolean dynamic = (typeString != null) ? typeString.equals("dynamic")
				: false;
		long minUpdateTimeMs = (dynamic) ? parseDurationMs(xpp,
				"minimumUpdatePeriod", -1) : -1;

		List<Period> periods = new ArrayList<Period>();
		do {
			xpp.next();
			if (isStartTag(xpp, "BaseURL")) {
				baseUrl = parseBaseUrl(xpp, baseUrl);
			} else if (isStartTag(xpp, "Period")) {
				periods.add(parsePeriod(xpp, contentId, baseUrl, durationMs));
			} else if (isStartTag(xpp, "session")) {
				parseSession(xpp); // XXX
			}
		} while (!isEndTag(xpp, "MPD"));

		CSync.getInstance().startSync();// XXX
		return new MediaPresentationDescription(durationMs, minBufferTimeMs,
				dynamic, minUpdateTimeMs, periods);
	}

	/* erweiterter google code.. yey! */
	@SuppressLint("UseSparseArrays")
	private void parseSession(XmlPullParser xpp) throws XmlPullParserException,
			IOException {
		if (DEBUG_ON_SCREEN)
			SessionInfo.getInstance().log("parsing session");

		String validThru = xpp.getAttributeValue(0);
		String sessionId = xpp.getAttributeValue(1);

		SessionInfo.getInstance().setValidThru(validThru);
		SessionInfo.getInstance().setSessionId(sessionId);

		Peer mySelf = null;

		InetAddress ownAddress = Utils.getWifiAddress(MainActivity.c);

		int ctr = 0;

		do {

			xpp.next();

			if (isStartTag(xpp, "peer")) {
				ctr++;
				int id = Integer.parseInt(xpp.getAttributeValue(0));
				int port = Integer.parseInt(xpp.getAttributeValue(2));
				InetAddress addr = InetAddress.getByName(xpp
						.getAttributeValue(1));
				Peer peer = new Peer(id, addr, port);

				if (peer.getAddress().getHostAddress()
						.equals(ownAddress.getHostAddress())) {
					mySelf = peer;
					if (DEBUG_ON_SCREEN)
						SessionInfo.getInstance().log(
								"encountered myself " + mySelf);
				} else {
					if (DEBUG_ON_SCREEN)
						SessionInfo.getInstance().log("add peer " + peer);
					SessionInfo.getInstance().getPeers().put(id, peer);
				}
			} else {

			}
		} while (!isEndTag(xpp, "session"));
		/* set initial sequence number: #of peers without oneself */
		SessionInfo.getInstance().setSeqN(ctr);
		/*
		 * should not happen because of the server, but when it does (testing
		 * with dummy data) we are prepared^^
		 */
		if (mySelf == null)
			mySelf = new Peer(31, ownAddress, MessageHandler.PORT);
		SessionInfo.getInstance().setMySelf(mySelf);

	}/* erweiterter google code.. yey ende! */

	private Period parsePeriod(XmlPullParser xpp, String contentId,
			Uri baseUrl, long mpdDurationMs) throws XmlPullParserException,
			IOException {
		String id = xpp.getAttributeValue(null, "id");
		long startMs = parseDurationMs(xpp, "start", 0);
		long durationMs = parseDurationMs(xpp, "duration", mpdDurationMs);
		SegmentBase segmentBase = null;
		List<AdaptationSet> adaptationSets = new ArrayList<AdaptationSet>();
		do {
			xpp.next();
			if (isStartTag(xpp, "BaseURL")) {
				baseUrl = parseBaseUrl(xpp, baseUrl);
			} else if (isStartTag(xpp, "AdaptationSet")) {
				adaptationSets.add(parseAdaptationSet(xpp, contentId, baseUrl,
						startMs, durationMs, segmentBase));
			} else if (isStartTag(xpp, "SegmentBase")) {
				segmentBase = parseSegmentBase(xpp, baseUrl, null);
			} else if (isStartTag(xpp, "SegmentList")) {
				segmentBase = parseSegmentList(xpp, baseUrl, null, durationMs);
			} else if (isStartTag(xpp, "SegmentTemplate")) {
				segmentBase = parseSegmentTemplate(xpp, baseUrl, null,
						durationMs);
			}
		} while (!isEndTag(xpp, "Period"));
		return new Period(id, startMs, durationMs, adaptationSets);
	}

	// AdaptationSet parsing.

	private AdaptationSet parseAdaptationSet(XmlPullParser xpp,
			String contentId, Uri baseUrl, long periodStartMs,
			long periodDurationMs, SegmentBase segmentBase)
			throws XmlPullParserException, IOException {

		String mimeType = xpp.getAttributeValue(null, "mimeType");
		int contentType = parseAdaptationSetTypeFromMimeType(mimeType);

		int id = -1;
		List<ContentProtection> contentProtections = null;
		List<Representation> representations = new ArrayList<Representation>();
		do {
			xpp.next();
			if (isStartTag(xpp, "BaseURL")) {
				baseUrl = parseBaseUrl(xpp, baseUrl);
			} else if (isStartTag(xpp, "ContentProtection")) {
				if (contentProtections == null) {
					contentProtections = new ArrayList<ContentProtection>();
				}
				contentProtections.add(parseContentProtection(xpp));
			} else if (isStartTag(xpp, "ContentComponent")) {
				id = Integer.parseInt(xpp.getAttributeValue(null, "id"));
				contentType = checkAdaptationSetTypeConsistency(contentType,
						parseAdaptationSetType(xpp.getAttributeValue(null,
								"contentType")));
			} else if (isStartTag(xpp, "Representation")) {
				Representation representation = parseRepresentation(xpp,
						contentId, baseUrl, periodStartMs, periodDurationMs,
						mimeType, segmentBase);
				contentType = checkAdaptationSetTypeConsistency(
						contentType,
						parseAdaptationSetTypeFromMimeType(representation.format.mimeType));
				representations.add(representation);
			} else if (isStartTag(xpp, "SegmentBase")) {
				segmentBase = parseSegmentBase(xpp, baseUrl,
						(SingleSegmentBase) segmentBase);
			} else if (isStartTag(xpp, "SegmentList")) {
				segmentBase = parseSegmentList(xpp, baseUrl,
						(SegmentList) segmentBase, periodDurationMs);
			} else if (isStartTag(xpp, "SegmentTemplate")) {
				segmentBase = parseSegmentTemplate(xpp, baseUrl,
						(SegmentTemplate) segmentBase, periodDurationMs);
			}
		} while (!isEndTag(xpp, "AdaptationSet"));

		return new AdaptationSet(id, contentType, representations,
				contentProtections);
	}

	private int parseAdaptationSetType(String contentType) {
		return TextUtils.isEmpty(contentType) ? AdaptationSet.TYPE_UNKNOWN
				: MimeTypes.BASE_TYPE_AUDIO.equals(contentType) ? AdaptationSet.TYPE_AUDIO
						: MimeTypes.BASE_TYPE_VIDEO.equals(contentType) ? AdaptationSet.TYPE_VIDEO
								: MimeTypes.BASE_TYPE_TEXT.equals(contentType) ? AdaptationSet.TYPE_TEXT
										: AdaptationSet.TYPE_UNKNOWN;
	}

	private int parseAdaptationSetTypeFromMimeType(String mimeType) {
		return TextUtils.isEmpty(mimeType) ? AdaptationSet.TYPE_UNKNOWN
				: MimeTypes.isAudio(mimeType) ? AdaptationSet.TYPE_AUDIO
						: MimeTypes.isVideo(mimeType) ? AdaptationSet.TYPE_VIDEO
								: MimeTypes.isText(mimeType)
										|| MimeTypes.isTtml(mimeType) ? AdaptationSet.TYPE_TEXT
										: AdaptationSet.TYPE_UNKNOWN;
	}

	/**
	 * Checks two adaptation set types for consistency, returning the consistent
	 * type, or throwing an {@link IllegalStateException} if the types are
	 * inconsistent.
	 * <p>
	 * Two types are consistent if they are equal, or if one is
	 * {@link AdaptationSet#TYPE_UNKNOWN}. Where one of the types is
	 * {@link AdaptationSet#TYPE_UNKNOWN}, the other is returned.
	 *
	 * @param firstType
	 *            The first type.
	 * @param secondType
	 *            The second type.
	 * @return The consistent type.
	 */
	private int checkAdaptationSetTypeConsistency(int firstType, int secondType) {
		if (firstType == AdaptationSet.TYPE_UNKNOWN) {
			return secondType;
		} else if (secondType == AdaptationSet.TYPE_UNKNOWN) {
			return firstType;
		} else {
			Assertions.checkState(firstType == secondType);
			return firstType;
		}
	}

	/**
	 * Parses a ContentProtection element.
	 *
	 * @throws XmlPullParserException
	 *             If an error occurs parsing the element.
	 * @throws IOException
	 *             If an error occurs reading the element.
	 **/
	protected ContentProtection parseContentProtection(XmlPullParser xpp)
			throws XmlPullParserException, IOException {
		String schemeUriId = xpp.getAttributeValue(null, "schemeUriId");
		return new ContentProtection(schemeUriId, null);
	}

	// Representation parsing.

	private Representation parseRepresentation(XmlPullParser xpp,
			String contentId, Uri baseUrl, long periodStartMs,
			long periodDurationMs, String mimeType, SegmentBase segmentBase)
			throws XmlPullParserException, IOException {
		String id = xpp.getAttributeValue(null, "id");
		int bandwidth = parseInt(xpp, "bandwidth");
		int audioSamplingRate = parseInt(xpp, "audioSamplingRate");
		int width = parseInt(xpp, "width");
		int height = parseInt(xpp, "height");
		mimeType = parseString(xpp, "mimeType", mimeType);

		int numChannels = -1;
		do {
			xpp.next();
			if (isStartTag(xpp, "BaseURL")) {
				baseUrl = parseBaseUrl(xpp, baseUrl);
			} else if (isStartTag(xpp, "AudioChannelConfiguration")) {
				numChannels = Integer.parseInt(xpp.getAttributeValue(null,
						"value"));
			} else if (isStartTag(xpp, "SegmentBase")) {
				segmentBase = parseSegmentBase(xpp, baseUrl,
						(SingleSegmentBase) segmentBase);
			} else if (isStartTag(xpp, "SegmentList")) {
				segmentBase = parseSegmentList(xpp, baseUrl,
						(SegmentList) segmentBase, periodDurationMs);
			} else if (isStartTag(xpp, "SegmentTemplate")) {
				segmentBase = parseSegmentTemplate(xpp, baseUrl,
						(SegmentTemplate) segmentBase, periodDurationMs);
			}
		} while (!isEndTag(xpp, "Representation"));

		Format format = new Format(id, mimeType, width, height, numChannels,
				audioSamplingRate, bandwidth);
		return Representation.newInstance(periodStartMs, periodDurationMs,
				contentId, -1, format, segmentBase);
	}

	// SegmentBase, SegmentList and SegmentTemplate parsing.

	private SingleSegmentBase parseSegmentBase(XmlPullParser xpp, Uri baseUrl,
			SingleSegmentBase parent) throws XmlPullParserException,
			IOException {

		long timescale = parseLong(xpp, "timescale",
				parent != null ? parent.timescale : 1);
		long presentationTimeOffset = parseLong(xpp, "presentationTimeOffset",
				parent != null ? parent.presentationTimeOffset : 0);

		long indexStart = parent != null ? parent.indexStart : 0;
		long indexLength = parent != null ? parent.indexLength : -1;
		String indexRangeText = xpp.getAttributeValue(null, "indexRange");
		if (indexRangeText != null) {
			String[] indexRange = indexRangeText.split("-");
			indexStart = Long.parseLong(indexRange[0]);
			indexLength = Long.parseLong(indexRange[1]) - indexStart + 1;
		}

		RangedUri initialization = parent != null ? parent.initialization
				: null;
		do {
			xpp.next();
			if (isStartTag(xpp, "Initialization")) {
				initialization = parseInitialization(xpp, baseUrl);
			}
		} while (!isEndTag(xpp, "SegmentBase"));

		return new SingleSegmentBase(initialization, timescale,
				presentationTimeOffset, baseUrl, indexStart, indexLength);
	}

	private SegmentList parseSegmentList(XmlPullParser xpp, Uri baseUrl,
			SegmentList parent, long periodDuration)
			throws XmlPullParserException, IOException {

		long timescale = parseLong(xpp, "timescale",
				parent != null ? parent.timescale : 1);
		long presentationTimeOffset = parseLong(xpp, "presentationTimeOffset",
				parent != null ? parent.presentationTimeOffset : 0);
		long duration = parseLong(xpp, "duration",
				parent != null ? parent.duration : -1);
		int startNumber = parseInt(xpp, "startNumber",
				parent != null ? parent.startNumber : 0);

		RangedUri initialization = null;
		List<SegmentTimelineElement> timeline = null;
		List<RangedUri> segments = null;

		do {
			xpp.next();
			if (isStartTag(xpp, "Initialization")) {
				initialization = parseInitialization(xpp, baseUrl);
			} else if (isStartTag(xpp, "SegmentTimeline")) {
				timeline = parseSegmentTimeline(xpp);
			} else if (isStartTag(xpp, "SegmentURL")) {
				if (segments == null) {
					segments = new ArrayList<RangedUri>();
				}
				segments.add(parseSegmentUrl(xpp, baseUrl));
			}
		} while (!isEndTag(xpp, "SegmentList"));

		if (parent != null) {
			initialization = initialization != null ? initialization
					: parent.initialization;
			timeline = timeline != null ? timeline : parent.segmentTimeline;
			segments = segments != null ? segments : parent.mediaSegments;
		}

		return new SegmentList(initialization, timescale,
				presentationTimeOffset, periodDuration, startNumber, duration,
				timeline, segments);
	}

	private SegmentTemplate parseSegmentTemplate(XmlPullParser xpp,
			Uri baseUrl, SegmentTemplate parent, long periodDuration)
			throws XmlPullParserException, IOException {

		long timescale = parseLong(xpp, "timescale",
				parent != null ? parent.timescale : 1);
		long presentationTimeOffset = parseLong(xpp, "presentationTimeOffset",
				parent != null ? parent.presentationTimeOffset : 0);
		long duration = parseLong(xpp, "duration",
				parent != null ? parent.duration : -1);
		int startNumber = parseInt(xpp, "startNumber",
				parent != null ? parent.startNumber : 0);
		UrlTemplate mediaTemplate = parseUrlTemplate(xpp, "media",
				parent != null ? parent.mediaTemplate : null);
		UrlTemplate initializationTemplate = parseUrlTemplate(xpp,
				"initialization",
				parent != null ? parent.initializationTemplate : null);

		RangedUri initialization = null;
		List<SegmentTimelineElement> timeline = null;

		do {
			xpp.next();
			if (isStartTag(xpp, "Initialization")) {
				initialization = parseInitialization(xpp, baseUrl);
			} else if (isStartTag(xpp, "SegmentTimeline")) {
				timeline = parseSegmentTimeline(xpp);
			}
		} while (!isEndTag(xpp, "SegmentTemplate"));

		if (parent != null) {
			initialization = initialization != null ? initialization
					: parent.initialization;
			timeline = timeline != null ? timeline : parent.segmentTimeline;
		}

		return new SegmentTemplate(initialization, timescale,
				presentationTimeOffset, periodDuration, startNumber, duration,
				timeline, initializationTemplate, mediaTemplate, baseUrl);
	}

	private List<SegmentTimelineElement> parseSegmentTimeline(XmlPullParser xpp)
			throws XmlPullParserException, IOException {
		List<SegmentTimelineElement> segmentTimeline = new ArrayList<SegmentTimelineElement>();
		long elapsedTime = 0;
		do {
			xpp.next();
			if (isStartTag(xpp, "S")) {
				elapsedTime = parseLong(xpp, "t", elapsedTime);
				long duration = parseLong(xpp, "d");
				int count = 1 + parseInt(xpp, "r", 0);
				for (int i = 0; i < count; i++) {
					segmentTimeline.add(new SegmentTimelineElement(elapsedTime,
							duration));
					elapsedTime += duration;
				}
			}
		} while (!isEndTag(xpp, "SegmentTimeline"));
		return segmentTimeline;
	}

	private UrlTemplate parseUrlTemplate(XmlPullParser xpp, String name,
			UrlTemplate defaultValue) {
		String valueString = xpp.getAttributeValue(null, name);
		if (valueString != null) {
			return UrlTemplate.compile(valueString);
		}
		return defaultValue;
	}

	private RangedUri parseInitialization(XmlPullParser xpp, Uri baseUrl) {
		return parseRangedUrl(xpp, baseUrl, "sourceURL", "range");
	}

	private RangedUri parseSegmentUrl(XmlPullParser xpp, Uri baseUrl) {
		return parseRangedUrl(xpp, baseUrl, "media", "mediaRange");
	}

	private RangedUri parseRangedUrl(XmlPullParser xpp, Uri baseUrl,
			String urlAttribute, String rangeAttribute) {
		String urlText = xpp.getAttributeValue(null, urlAttribute);
		long rangeStart = 0;
		long rangeLength = -1;
		String rangeText = xpp.getAttributeValue(null, rangeAttribute);
		if (rangeText != null) {
			String[] rangeTextArray = rangeText.split("-");
			rangeStart = Long.parseLong(rangeTextArray[0]);
			rangeLength = Long.parseLong(rangeTextArray[1]) - rangeStart + 1;
		}
		return new RangedUri(baseUrl, urlText, rangeStart, rangeLength);
	}

	// Utility methods.

	protected static boolean isEndTag(XmlPullParser xpp, String name)
			throws XmlPullParserException {
		return xpp.getEventType() == XmlPullParser.END_TAG
				&& name.equals(xpp.getName());
	}

	protected static boolean isStartTag(XmlPullParser xpp, String name)
			throws XmlPullParserException {
		return xpp.getEventType() == XmlPullParser.START_TAG
				&& name.equals(xpp.getName());
	}

	private static long parseDurationMs(XmlPullParser xpp, String name) {
		return parseDurationMs(xpp, name, -1);
	}

	private static long parseDurationMs(XmlPullParser xpp, String name,
			long defaultValue) {
		String value = xpp.getAttributeValue(null, name);
		if (value != null) {
			Matcher matcher = DURATION.matcher(value);
			if (matcher.matches()) {
				String hours = matcher.group(2);
				double durationSeconds = (hours != null) ? Double
						.parseDouble(hours) * 3600 : 0;
				String minutes = matcher.group(4);
				durationSeconds += (minutes != null) ? Double
						.parseDouble(minutes) * 60 : 0;
				String seconds = matcher.group(6);
				durationSeconds += (seconds != null) ? Double
						.parseDouble(seconds) : 0;
				return (long) (durationSeconds * 1000);
			} else {
				return (long) (Double.parseDouble(value) * 3600 * 1000);
			}
		}
		return defaultValue;
	}

	protected static Uri parseBaseUrl(XmlPullParser xpp, Uri parentBaseUrl)
			throws XmlPullParserException, IOException {
		xpp.next();
		String newBaseUrlText = xpp.getText();
		Uri newBaseUri = Uri.parse(newBaseUrlText);
		if (!newBaseUri.isAbsolute()) {
			newBaseUri = Uri.withAppendedPath(parentBaseUrl, newBaseUrlText);
		}
		return newBaseUri;
	}

	protected static int parseInt(XmlPullParser xpp, String name) {
		return parseInt(xpp, name, -1);
	}

	protected static int parseInt(XmlPullParser xpp, String name,
			int defaultValue) {
		String value = xpp.getAttributeValue(null, name);
		return value == null ? defaultValue : Integer.parseInt(value);
	}

	protected static long parseLong(XmlPullParser xpp, String name) {
		return parseLong(xpp, name, -1);
	}

	protected static long parseLong(XmlPullParser xpp, String name,
			long defaultValue) {
		String value = xpp.getAttributeValue(null, name);
		return value == null ? defaultValue : Long.parseLong(value);
	}

	protected static String parseString(XmlPullParser xpp, String name,
			String defaultValue) {
		String value = xpp.getAttributeValue(null, name);
		return value == null ? defaultValue : value;
	}

}
