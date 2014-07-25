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
package mf.com.google.android.exoplayer.dash;

import android.net.Uri;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import mf.com.google.android.exoplayer.MediaFormat;
import mf.com.google.android.exoplayer.ParserException;
import mf.com.google.android.exoplayer.TrackInfo;
import mf.com.google.android.exoplayer.chunk.Chunk;
import mf.com.google.android.exoplayer.chunk.ChunkOperationHolder;
import mf.com.google.android.exoplayer.chunk.ChunkSource;
import mf.com.google.android.exoplayer.chunk.Format;
import mf.com.google.android.exoplayer.chunk.FormatEvaluator;
import mf.com.google.android.exoplayer.chunk.MediaChunk;
import mf.com.google.android.exoplayer.chunk.Mp4MediaChunk;
import mf.com.google.android.exoplayer.chunk.Format.DecreasingBandwidthComparator;
import mf.com.google.android.exoplayer.chunk.FormatEvaluator.Evaluation;
import mf.com.google.android.exoplayer.dash.mpd.RangedUri;
import mf.com.google.android.exoplayer.dash.mpd.Representation;
import mf.com.google.android.exoplayer.parser.mp4.FragmentedMp4Extractor;
import mf.com.google.android.exoplayer.upstream.DataSource;
import mf.com.google.android.exoplayer.upstream.DataSpec;
import mf.com.google.android.exoplayer.upstream.NonBlockingInputStream;

/**
 * An {@link ChunkSource} for Mp4 DASH streams.
 */
public class DashMp4ChunkSource implements ChunkSource {

  private final TrackInfo trackInfo;
  private final DataSource dataSource;
  private final FormatEvaluator evaluator;
  private final Evaluation evaluation;
  private final int maxWidth;
  private final int maxHeight;

  private final Format[] formats;
  private final HashMap<String, Representation> representations;
  private final HashMap<String, FragmentedMp4Extractor> extractors;
  private final HashMap<String, DashSegmentIndex> segmentIndexes;

  private boolean lastChunkWasInitialization;

  /**
   * @param dataSource A {@link DataSource} suitable for loading the media data.
   * @param evaluator Selects from the available formats.
   * @param representations The representations to be considered by the source.
   */
  public DashMp4ChunkSource(DataSource dataSource, FormatEvaluator evaluator,
      Representation... representations) {
    this.dataSource = dataSource;
    this.evaluator = evaluator;
    this.formats = new Format[representations.length];
    this.extractors = new HashMap<String, FragmentedMp4Extractor>();
    this.segmentIndexes = new HashMap<String, DashSegmentIndex>();
    this.representations = new HashMap<String, Representation>();
    this.trackInfo = new TrackInfo(representations[0].format.mimeType,
        representations[0].periodDurationMs * 1000);
    this.evaluation = new Evaluation();
    int maxWidth = 0;
    int maxHeight = 0;
    for (int i = 0; i < representations.length; i++) {
      formats[i] = representations[i].format;
      maxWidth = Math.max(formats[i].width, maxWidth);
      maxHeight = Math.max(formats[i].height, maxHeight);
      extractors.put(formats[i].id, new FragmentedMp4Extractor());
      this.representations.put(formats[i].id, representations[i]);
      DashSegmentIndex segmentIndex = representations[i].getIndex();
      if (segmentIndex != null) {
        segmentIndexes.put(formats[i].id, segmentIndex);
      }
    }
    this.maxWidth = maxWidth;
    this.maxHeight = maxHeight;
    Arrays.sort(formats, new DecreasingBandwidthComparator());
  }

  @Override
  public final void getMaxVideoDimensions(MediaFormat out) {
    if (trackInfo.mimeType.startsWith("video")) {
      out.setMaxVideoDimensions(maxWidth, maxHeight);
    }
  }

  @Override
  public final TrackInfo getTrackInfo() {
    return trackInfo;
  }

  @Override
  public void enable() {
    evaluator.enable();
  }

  @Override
  public void disable(List<? extends MediaChunk> queue) {
    evaluator.disable();
  }

  @Override
  public void continueBuffering(long playbackPositionUs) {
    // Do nothing
  }

  @Override
  public final void getChunkOperation(List<? extends MediaChunk> queue, long seekPositionUs,
      long playbackPositionUs, ChunkOperationHolder out) {
    evaluation.queueSize = queue.size();
    if (evaluation.format == null || !lastChunkWasInitialization) {
      evaluator.evaluate(queue, playbackPositionUs, formats, evaluation);
    }
    Format selectedFormat = evaluation.format;
    out.queueSize = evaluation.queueSize;

    if (selectedFormat == null) {
      out.chunk = null;
      return;
    } else if (out.queueSize == queue.size() && out.chunk != null
        && out.chunk.format.id.equals(selectedFormat.id)) {
      // We already have a chunk, and the evaluation hasn't changed either the format or the size
      // of the queue. Leave unchanged.
      return;
    }

    Representation selectedRepresentation = representations.get(selectedFormat.id);
    FragmentedMp4Extractor extractor = extractors.get(selectedRepresentation.format.id);

    RangedUri pendingInitializationUri = null;
    RangedUri pendingIndexUri = null;
    if (extractor.getTrack() == null) {
      pendingInitializationUri = selectedRepresentation.getInitializationUri();
    }
    if (!segmentIndexes.containsKey(selectedRepresentation.format.id)) {
      pendingIndexUri = selectedRepresentation.getIndexUri();
    }
    if (pendingInitializationUri != null || pendingIndexUri != null) {
      // We have initialization and/or index requests to make.
      Chunk initializationChunk = newInitializationChunk(pendingInitializationUri, pendingIndexUri,
          selectedRepresentation, extractor, dataSource, evaluation.trigger);
      lastChunkWasInitialization = true;
      out.chunk = initializationChunk;
      return;
    }

    int nextSegmentNum;
    DashSegmentIndex segmentIndex = segmentIndexes.get(selectedRepresentation.format.id);
    if (queue.isEmpty()) {
      nextSegmentNum = segmentIndex.getSegmentNum(seekPositionUs);
    } else {
      nextSegmentNum = queue.get(out.queueSize - 1).nextChunkIndex;
    }

    if (nextSegmentNum == -1) {
      out.chunk = null;
      return;
    }

    Chunk nextMediaChunk = newMediaChunk(selectedRepresentation, segmentIndex, extractor,
        dataSource, nextSegmentNum, evaluation.trigger);
    lastChunkWasInitialization = false;
    out.chunk = nextMediaChunk;
  }

  @Override
  public IOException getError() {
    return null;
  }

  @Override
  public void onChunkLoadError(Chunk chunk, Exception e) {
    // Do nothing.
  }

  private Chunk newInitializationChunk(RangedUri initializationUri, RangedUri indexUri,
      Representation representation, FragmentedMp4Extractor extractor, DataSource dataSource,
      int trigger) {
    int expectedExtractorResult = FragmentedMp4Extractor.RESULT_END_OF_STREAM;
    long indexAnchor = 0;
    RangedUri requestUri;
    if (initializationUri != null) {
      // It's common for initialization and index data to be stored adjacently. Attempt to merge
      // the two requests together to request both at once.
      expectedExtractorResult |= FragmentedMp4Extractor.RESULT_READ_MOOV;
      requestUri = initializationUri.attemptMerge(indexUri);
      if (requestUri != null) {
        expectedExtractorResult |= FragmentedMp4Extractor.RESULT_READ_SIDX;
        indexAnchor = indexUri.start + indexUri.length;
      } else {
        requestUri = initializationUri;
      }
    } else {
      requestUri = indexUri;
      indexAnchor = indexUri.start + indexUri.length;
      expectedExtractorResult |= FragmentedMp4Extractor.RESULT_READ_SIDX;
    }
    DataSpec dataSpec = new DataSpec(requestUri.getUri(), requestUri.start, requestUri.length,
        representation.getCacheKey());
    return new InitializationMp4Loadable(dataSource, dataSpec, trigger, representation.format,
        extractor, expectedExtractorResult, indexAnchor);
  }

  private Chunk newMediaChunk(Representation representation, DashSegmentIndex segmentIndex,
      FragmentedMp4Extractor extractor, DataSource dataSource, int segmentNum, int trigger) {
    int lastSegmentNum = segmentIndex.getLastSegmentNum();
    int nextSegmentNum = segmentNum == lastSegmentNum ? -1 : segmentNum + 1;
    long startTimeUs = segmentIndex.getTimeUs(segmentNum);
    long endTimeUs = segmentNum < lastSegmentNum ? segmentIndex.getTimeUs(segmentNum + 1)
        : startTimeUs + segmentIndex.getDurationUs(segmentNum);
    RangedUri segmentUri = segmentIndex.getSegmentUrl(segmentNum);
    DataSpec dataSpec = new DataSpec(segmentUri.getUri(), segmentUri.start, segmentUri.length,
        representation.getCacheKey());
    return new Mp4MediaChunk(dataSource, dataSpec, representation.format, trigger, startTimeUs,
        endTimeUs, nextSegmentNum, extractor, false, 0);
  }

  private class InitializationMp4Loadable extends Chunk {

    private final FragmentedMp4Extractor extractor;
    private final int expectedExtractorResult;
    private final long indexAnchor;
    private final Uri uri;

    public InitializationMp4Loadable(DataSource dataSource, DataSpec dataSpec, int trigger,
        Format format, FragmentedMp4Extractor extractor, int expectedExtractorResult,
        long indexAnchor) {
      super(dataSource, dataSpec, format, trigger);
      this.extractor = extractor;
      this.expectedExtractorResult = expectedExtractorResult;
      this.indexAnchor = indexAnchor;
      this.uri = dataSpec.uri;
    }

    @Override
    protected void consumeStream(NonBlockingInputStream stream) throws IOException {
      int result = extractor.read(stream, null);
      if (result != expectedExtractorResult) {
        throw new ParserException("Invalid extractor result. Expected "
            + expectedExtractorResult + ", got " + result);
      }
      if ((result & FragmentedMp4Extractor.RESULT_READ_SIDX) != 0) {
        segmentIndexes.put(format.id,
            new DashWrappingSegmentIndex(extractor.getSegmentIndex(), uri, indexAnchor));
      }
    }

  }

}
