/*
 * Copyright 2019 GridGain Systems, Inc. and Contributors.
 *
 * Licensed under the GridGain Community Edition License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.gridgain.com/products/software/community-edition/gridgain-community-edition-license
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.ignite.development.utils;

import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import org.apache.ignite.internal.util.GridStringBuilder;

/**
 * Class for printing progress of some task.
 * Progress is printed {@code chunksNum} times.
 */
public class ProgressPrinter {
    /** */
    private static final int DEFAULT_CHUNKS_NUM = 50;

    /** */
    private static final int PROGRESS_BAR_LENGTH = 20;

    /** */
    private final long total;

    /** */
    private final int chunksNum;

    /** */
    private final String caption;

    /** */
    private final PrintStream printStream;

    /** */
    private int lastChunkLogged;

    /**
     * Constructor.
     *
     * @param caption Caption.
     * @param total Total count of items to process.
     */
    public ProgressPrinter(PrintStream printStream, String caption, long total) {
        this(printStream, caption, total, DEFAULT_CHUNKS_NUM);
    }

    /**
     * Constructor.
     *
     * @param caption Caption.
     * @param total Total count of items to process.
     * @param chunksNum Number of progress bar chunks to print.
     */
    public ProgressPrinter(PrintStream printStream, String caption, long total, int chunksNum) {
        this.printStream = printStream;
        this.caption = caption;
        this.total = total;
        this.chunksNum = chunksNum;
    }

    /**
     * Prints current progress.
     *
     * @param curr Current count of processed items.
     * @param timeStarted Timestamp when progress started.
     */
    public void printProgress(long curr, long timeStarted) {
        if (curr > total)
            throw new RuntimeException("Current value can't be greater than total value.");

        final double currRatio = (double)curr / total;

        final int currChunk = (int)(currRatio * chunksNum);

        if (currChunk > lastChunkLogged) {
            lastChunkLogged++;

            printProgress0(curr, timeStarted, currRatio);
        }
    }

    /** */
    private void printProgress0(long curr, long timeStarted, double currRatio) {
        String progressBarFmt = "%s: %4s [%" + PROGRESS_BAR_LENGTH + "s] %s/%s (%s / %s)";

        int percentage = (int)(currRatio * 100);
        int progressCurrLen = (int)(currRatio * PROGRESS_BAR_LENGTH);
        long timeRunning = System.currentTimeMillis() - timeStarted;
        long timeEstimated = (long)(timeRunning / currRatio);

        GridStringBuilder progressBuilder = new GridStringBuilder();

        for (int i = 0; i < PROGRESS_BAR_LENGTH; i++)
            progressBuilder.a(i < progressCurrLen ? "=" : " ");

        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");

        timeFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

        String progressBar = String.format(
            progressBarFmt,
            caption,
            percentage + "%",
            progressBuilder.toString(),
            curr,
            total,
            timeFormat.format(new Date(timeRunning)),
            timeFormat.format(new Date(timeEstimated))
        );

        printStream.println(progressBar);
    }
}
