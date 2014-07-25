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
package mf.com.google.android.exoplayer.parser.webm;

import mf.com.google.android.exoplayer.MediaFormat;
import mf.com.google.android.exoplayer.SampleHolder;
import mf.com.google.android.exoplayer.parser.SegmentIndex;
import mf.com.google.android.exoplayer.upstream.NonBlockingInputStream;

/**
 * Extractor to facilitate data retrieval from the WebM container format.
 *
 * <p>WebM is a subset of the EBML elements defined for Matroska. More information about EBML and
 * Matroska is available <a href="http://www.matroska.org/technical/specs/index.html">here</a>.
 * More info about WebM is <a href="http://www.webmproject.org/code/specs/container/">here</a>.
 */
public interface WebmExtractor {

  /**
   * Whether the has parsed the cues and sample format from the stream.
   *
   * @return True if the extractor is prepared. False otherwise
   */
  public boolean isPrepared();

  /**
   * Consumes data from a {@link NonBlockingInputStream}.
   *
   * <p>If the return value is {@code false}, then a sample may have been partially read into
   * {@code sampleHolder}. Hence the same {@link SampleHolder} instance must be passed
   * in subsequent calls until the whole sample has been read.
   *
   * @param inputStream The input stream from which data should be read
   * @param sampleHolder A {@link SampleHolder} into which the sample should be read
   * @return {@code true} if a sample has been read into the sample holder
   */
  public boolean read(NonBlockingInputStream inputStream, SampleHolder sampleHolder);

  /**
   * Seeks to a position before or equal to the requested time.
   *
   * @param seekTimeUs The desired seek time in microseconds
   * @param allowNoop Allow the seek operation to do nothing if the seek time is in the current
   *     segment, is equal to or greater than the time of the current sample, and if there does not
   *     exist a sync frame between these two times
   * @return True if the operation resulted in a change of state. False if it was a no-op
   */
  public boolean seekTo(long seekTimeUs, boolean allowNoop);

  /**
   * Returns the cues for the media stream.
   *
   * @return The cues in the form of a {@link SegmentIndex}, or null if the extractor is not yet
   *     prepared
   */
  public SegmentIndex getCues();

  /**
   * Returns the format of the samples contained within the media stream.
   *
   * @return The sample media format, or null if the extracted is not yet prepared
   */
  public MediaFormat getFormat();

}
