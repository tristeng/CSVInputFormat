/**
 * Copyright 2014 Marcelo Elias Del Valle
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.mapreduce.lib.input;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.JobContext;
import org.apache.hadoop.mapreduce.RecordReader;
import org.apache.hadoop.mapreduce.TaskAttemptContext;

/**
 * Configurable CSV line reader. Variant of NLineInputReader that reads CSV
 * lines, even if the CSV has multiple lines inside a single column. Also
 * implements the getSplits method so splits are made by lines
 * 
 * 
 * @author mvallebr, tristeng
 *
 * October, 2015: tristeng (tgeorgiou@phemi.com) updated split functionality based on changes made to the
 * CSVLineRecordReader
 */
public class CSVNLineInputFormat extends CSVFileInputFormat<LongWritable, List<Text>> {

    public static final String LINES_PER_MAP = "mapreduce.input.lineinputformat.linespermap";
    public static final int DEFAULT_LINES_PER_MAP = 1;

    /*
     * (non-Javadoc)
     *
     * @see
     * org.apache.hadoop.mapreduce.InputFormat#createRecordReader(org.apache
     * .hadoop.mapreduce.InputSplit,
     * org.apache.hadoop.mapreduce.TaskAttemptContext)
     */
    @Override
    public RecordReader<LongWritable, List<Text>> createRecordReader(InputSplit split, TaskAttemptContext context)
            throws IOException {
        Configuration conf = context.getConfiguration();
        String delimiter = conf.get(FORMAT_DELIMITER, DEFAULT_DELIMITER);
        String separator = conf.get(FORMAT_SEPARATOR, DEFAULT_SEPARATOR);
        if (null == delimiter || null == separator) {
            throw new IOException("CSVNLineInputFormat: missing parameter delimiter/separator");
        }
        if (delimiter.length() != 1 || separator.length() != 1) {
            throw new IOException("CSVNLineInputFormat: delimiter/separator can only be a single character");
        }
        if (delimiter.equals(separator)) {
            throw new IOException("CSVNLineInputFormat: delimiter and separator cannot be the same character");
        }
        return new CSVLineRecordReader();
    }

    /**
     * Logically splits the set of input files for the job, splits N lines of
     * the input as one split.
     *
     * @see FileInputFormat#getSplits(JobContext)
     */
    @Override
    public List<InputSplit> getSplits(JobContext job) throws IOException {
        List<InputSplit> splits = new ArrayList<InputSplit>();
        int numLinesPerSplit = getNumLinesPerSplit(job);
        for (FileStatus status : listStatus(job)) {
            List<FileSplit> fileSplits = getSplitsForFile(status, job.getConfiguration(), numLinesPerSplit);
            splits.addAll(fileSplits);
        }
        return splits;
    }

    /**
     *
     * Uses CSVLineRecordReader to split the file in lines
     *
     * @param status
     *            file status
     * @param conf
     *            hadoop conf
     * @param numLinesPerSplit
     *            number of lines that should exist on each split
     * @return list of file splits to be processed.
     * @throws IOException
     */
    public List<FileSplit> getSplitsForFile(FileStatus status, Configuration conf, int numLinesPerSplit)
            throws IOException {
        List<FileSplit> splits = new ArrayList<FileSplit>();
        Path fileName = status.getPath();
        if (!status.isFile()) {
            throw new IOException("Not a file: " + fileName);
        }
        FileSystem fs = fileName.getFileSystem(conf);
        CSVRawLineRecordReader lr = null;
        try {
            FSDataInputStream in = fs.open(fileName);
            lr = new CSVRawLineRecordReader(in, conf);
            Text row = new Text();
            int numLines = 0;
            long begin = 0;
            long length = 0;
            int size;
            while ((size = lr.readLine(row)) > 0) {
                numLines++;
                length += size;
                if (numLines == numLinesPerSplit) {
                    splits.add(makeSplit(fileName, begin, length, new String[0]));
                    begin += length;
                    length = 0;
                    numLines = 0;
                }
            }
            if (numLines != 0) {
                splits.add(makeSplit(fileName, begin, length, new String[0]));
            }
        } finally {
            if (lr != null) {
                lr.close();
            }
        }
        return splits;
    }

    /**
     * Set the number of lines per split
     *
     * @param job
     *            the job to modify
     * @param numLines
     *            the number of lines per split
     */
    public static void setNumLinesPerSplit(Job job, int numLines) {
        job.getConfiguration().setInt(LINES_PER_MAP, numLines);
    }

    /**
     * Get the number of lines per split
     *
     * @param job
     *            the job
     * @return the number of lines per split
     */
    public static int getNumLinesPerSplit(JobContext job) {
        return job.getConfiguration().getInt(LINES_PER_MAP, DEFAULT_LINES_PER_MAP);
    }
}