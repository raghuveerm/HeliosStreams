/*
 * Copyright 2015 the original author or authors.
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
package com.heliosapm.streams.tracing;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.SortedMap;
import java.util.Stack;
import java.util.TreeMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import javax.management.ObjectName;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferFactory;
import org.jboss.netty.buffer.HeapChannelBufferFactory;

import com.codahale.metrics.Meter;
import com.heliosapm.streams.buffers.BufferManager;
import com.heliosapm.streams.common.metrics.SharedMetricsRegistry;
import com.heliosapm.streams.common.naming.AgentName;
import com.heliosapm.streams.metrics.StreamedMetricValue;
import com.heliosapm.streams.tracing.deltas.DeltaManager;
import com.heliosapm.utils.config.ConfigurationHelper;
import com.heliosapm.utils.lang.StringHelper;
import com.heliosapm.utils.time.SystemClock;
import com.heliosapm.utils.time.SystemClock.ElapsedTime;

import io.netty.buffer.ByteBuf;

/**
 * <p>Title: DefaultTracerImpl</p>
 * <p>Description: The default tracer implementation</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.streams.tracing.DefaultTracerImpl</code></p>
 */

public class DefaultTracerImpl implements ITracer {
	/** Thread pool used to flush tracing buffer */
	private static final Executor flushPool = Executors.newFixedThreadPool(2, new ThreadFactory(){
		final AtomicInteger serial = new AtomicInteger();
		@Override
		public Thread newThread(final Runnable r) {
			final Thread t = new Thread(r, "TracerFlushThread#" + serial.incrementAndGet());
			t.setDaemon(true);
			return t;
		}
	});
	/** Write event timer */
	private static final Meter traceMeter = SharedMetricsRegistry.getInstance().meter("tracer.tracemeter");

	// ===============================================================================
	// Current MetricName Components
	// ===============================================================================
	/** The metric name segments stack */
	private Stack<String> metricNameStack = new Stack<String>();
	/** The tag stack */
	private Stack<String[]> tagStack = new Stack<String[]>();	
	/** The metric tags sorter */
	private final TreeMap<String, String> tags = new TreeMap<String, String>(TagKeySorter.INSTANCE);
	/** The timestamp state in ms time format */
	private Long msTime = null;
	/** The max number of traces before an auto-flush */
	private int maxTracesBeforeFlush;
	/** The current number of un-flushed events in the buffer */
	private int bufferedEvents = 0;
	/** The inquiry event count */
	private long inqCount = 0;
	/** The total number of events generated */
	private long totalEvents = 0;
	/** The configured metric name segment delimiter */
	public final char metricSegDelim; 
	/** The configured max number of tags in a trace */
	public final int maxTags; 
	/** The configured min number of tags in a trace */
	public final int minTags; 
	/** The stateful tracing conditional which, if set to false, will suppress the next trace */
	private boolean traceActive = true;
	
	/** The writer that delivers the buffered metrics to an end-point */
	protected final IMetricWriter writer;
	
	// ===============================================================================
	// Push/Pop for Tags and Tracer State
	// ===============================================================================	
	/** tracks the tag key stack */
	private final LinkedList<String> tagKeyStack = new LinkedList<String>();
	/** The checkpoint stack so we can push/pop this tracer's state */
	private final Stack<TracerState> checkpointStack = new Stack<TracerState>();
	/** Flag tracking if the current tracer state has been modified since the last trace */
	private boolean modified = false;
	
	/** The app/host tags */
	private final Map<String, String> appHostTags;

	
	
	public static final int COMPRESS_OFFSET = 0;
	public static final int COUNT_OFFSET = 1;
	public static final int START_DATA_OFFSET = 5;
	
	// ===============================================================================
	// Where all traces go when their time comnes
	// ===============================================================================
	/** The buffer factory used to create buffers for all tracers */
	private static final ChannelBufferFactory bufferFactory = 
		new HeapChannelBufferFactory();
//	ConfigurationHelper.getIntSystemThenEnvProperty(CONF_INIT_SIZE, DEFAULT_INIT_SIZE),
//	ConfigurationHelper.getFloatSystemThenEnvProperty(CONF_EXT_PCT, DEFAULT_EXT_PCT)
	
	/** The buffer this tracer's traces are written out to before they're flushed */
	private final ByteBuf outBuffer;
	
	/** The ctor of the groovy enhanced tracer. We reference it reflectively so's we don't require Groovy. */
	private static volatile Constructor<? extends ITracer> groovyTracerCtor = null;
	/** Instance logger */
	protected final Logger log = LogManager.getLogger(getClass());
	
	/**
	 * <p>Title: TracerState</p>
	 * <p>Description: Holds the state of a Tracer between checkpoints</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.streams.tracing.DefaultTracerImpl.TracerState</code></p>
	 */
	protected class TracerState {
		// ===============================================================================
		// Current MetricName Components
		// ===============================================================================
		/** The metric name as linked list of metric name segments */
		private LinkedList<String> cpMetricNameSegs = new LinkedList<String>();
		/** The timestamp in ms time format */
		private Long cpMsTime;
		/** tracks the tag key stack */
		private final LinkedList<String> cpTagKeyStack = new LinkedList<String>();
		/** tracks the tag stack */
		private final LinkedList<String[]> cpTagStack = new LinkedList<String[]>();
		
		
		/**
		 * Creates a new TracerState from the passed tracer
		 * @param t The tracer to read the state from
		 */
		private TracerState(final DefaultTracerImpl t) {
			cpMetricNameSegs.addAll(t.metricNameStack);
			cpTagStack.addAll(t.tagStack);
			cpTagKeyStack.addAll(t.tagKeyStack);
			cpMsTime = msTime;
		}
		
		/**
		 * Pops this tracer state back into the passed tracer
		 * @param t The tracer to pop back to
		 */
		private void pop(final DefaultTracerImpl t) {
			t.metricNameStack.clear(); t.metricNameStack.addAll(cpMetricNameSegs);
			t.tagStack.clear(); t.tagStack.addAll(cpTagStack);
			t.tagKeyStack.clear(); t.tagKeyStack.addAll(cpTagKeyStack);
			msTime = cpMsTime;
		}
	}
	
	/**
	 * Creates a new Tracer with an initial metric name and current timestamp
	 */
	protected DefaultTracerImpl(final IMetricWriter writer) {
		this.writer = writer;
		metricSegDelim = ConfigurationHelper.getCharSystemThenEnvProperty(CONF_METRIC_SEG_DELIM, DEFAULT_METRIC_SEG_DELIM);
		maxTags = 8;  // FIXME: config
		minTags = 1;  // FIXME: config... allow zero for graphite et.al.
		maxTracesBeforeFlush = 200;  // FIXME: config a default
		outBuffer = BufferManager.getInstance().buffer(this.maxTracesBeforeFlush * 128); 
		outBuffer.setByte(COMPRESS_OFFSET, 0);
		outBuffer.setInt(COUNT_OFFSET, 0);
		outBuffer.writerIndex(START_DATA_OFFSET);
		appHostTags = Collections.unmodifiableMap(AgentName.defaultTags());
	}
	
	/**
	 * {@inheritDoc}
	 * @see java.io.Closeable#close()
	 */
	@Override
	public void close() throws IOException {
		outBuffer.release();
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.streams.tracing.ITracer#clear()
	 */
	@Override
	public ITracer clear() {
		reset();
		checkpointStack.clear();		
		outBuffer.clear();
		return this;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.streams.tracing.ITracer#reset()
	 */
	@Override
	public ITracer reset() {
		metricNameStack.clear();
		tagStack.clear();
		tagKeyStack.clear();
//		tags.clear();		
		return this;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.streams.tracing.ITracer#checkpoint()
	 */
	@Override
	public ITracer checkpoint() {
		checkpointStack.push(new TracerState(this));
		return this;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.streams.tracing.ITracer#popCheckpoint()
	 */
	@Override
	public ITracer popCheckpoint() {
		checkpointStack.pop().pop(this);
		return this;
	}
	
	/**
	 * Builds the metric name from the metric name stack
	 * @return the metric name
	 */
	protected String buildMetricName() {
		if(metricNameStack.isEmpty()) throw new RuntimeException("No metric name segments");
		final StringBuilder b = new StringBuilder();
		for(String s: metricNameStack) {
			b.append(s).append(metricSegDelim);
		}
		return b.deleteCharAt(b.length()-1).toString();
	}
	
	/**
	 * Builds a tag map based on the current tag stack
	 * @param tagValues The optional tag values which will be paired up with the tag key stack
	 * @return a tag map 
	 */
	protected SortedMap<String, String> buildTags(final String...tagValues) {
		if(tagValues!=null && tagValues.length > 0 && tagKeyStack.size() != tagValues.length) {
			throw new IllegalStateException("TagValue & Tag Key Stack Mismatch. TagKeyStack: " + tagKeyStack.size() + ", TagValues: " + tagValues.length) ;
		}
		final TreeMap<String, String> tmap = new TreeMap<String, String>(TagKeySorter.INSTANCE); 
//		tags.clear();
		if(tagValues != null) {
			final String[] tagKeys = tagKeyStack.toArray(new String[0]);
			for(int i = 0; i < tagKeys.length; i++) {
				tmap.put(tagKeys[i], tagValues[i]);
			}
		}		
		for(String[] pair: tagStack) {
			tmap.put(pair[0], pair[1]);
		}
		return tmap;
	}



	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.streams.tracing.ITracer#seg(java.lang.String)
	 */
	@Override
	public ITracer seg(final String fullSegment) {		
		metricNameStack.clear();
		if(fullSegment==null || fullSegment.trim().isEmpty()) return this;
		Collections.addAll(metricNameStack, StringHelper.splitString(fullSegment, metricSegDelim, true));
		return this;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.streams.tracing.ITracer#pushSeg(java.lang.String)
	 */
	@Override
	public ITracer pushSeg(final String segment) {
		if(segment != null && !segment.trim().isEmpty()) {
			metricNameStack.push(segment.trim());
		}
		return this;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.streams.tracing.ITracer#popSeg()
	 */
	@Override
	public ITracer popSeg() {
		metricNameStack.pop();
		return this;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.streams.tracing.ITracer#popSeg(int)
	 */
	@Override
	public ITracer popSeg(final int n) {
		if(n > 0) {
			for(int i = 0; i < n; i++) {
				metricNameStack.pop();
			}
		}
		return this;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.streams.tracing.ITracer#seg()
	 */
	@Override
	public String seg() {
		return buildMetricName();
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.streams.tracing.ITracer#tags()
	 */
	@Override
	public Map<String, String> tags() {
		return Collections.unmodifiableSortedMap(buildTags());
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.streams.tracing.ITracer#segs()
	 */
	@Override
	public String[] segs() {
		return metricNameStack.toArray(new String[0]);
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.streams.tracing.ITracer#tags(java.util.Map)
	 */
	@Override
	public ITracer tags(final Map<String, String> tags) {
		if(tags!=null && !tags.isEmpty()) {
			tagStack.clear();
			for(Map.Entry<String, String> entry: tags.entrySet()) {
				tagStack.push(new String[]{entry.getKey(), entry.getValue()});
			}
		}
		return this;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.streams.tracing.ITracer#tags(java.lang.String[])
	 */
	@Override
	public ITracer tags(final String... tags) {
		if(tags!=null) {
			if(tags.length==0) {
				tagStack.clear();
			} else {
				if(tags.length%2 != 0) throw new IllegalArgumentException("Odd number of tag pairs " + Arrays.toString(tags));
				for(int i = 0; i < tags.length;) {
					tagStack.push(new String[]{tags[i++].trim(), tags[i++].trim()});
				}				
			}			
		}
		return this;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.streams.tracing.ITracer#pushTagPair(java.lang.String)
	 */
	@Override
	public ITracer pushTagPair(final String tagPair) {
		if(tagPair==null || tagPair.trim().isEmpty()) throw new IllegalArgumentException("Tag pair was null or empty");
		return tags(StringHelper.splitString(tagPair, '=', true));
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.streams.tracing.ITracer#pushTag(java.lang.String, java.lang.String)
	 */
	@Override
	public ITracer pushTag(final String tagKey, final String tagValue) {
		if(tagKey==null || tagKey.trim().isEmpty()) throw new IllegalArgumentException("Tag key was null or empty");
		if(tagValue==null || tagValue.trim().isEmpty()) throw new IllegalArgumentException("Tag value was null or empty");
		tagStack.push(new String[]{tagKey.trim(), tagValue.trim()});
		return this;
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.streams.tracing.ITracer#pushTags(java.util.Map)
	 */
	@Override
	public ITracer pushTags(final Map<String, String> tags) {
		if(tags!=null && !tags.isEmpty()) {
			for(Map.Entry<String, String> entry: tags.entrySet()) {
				pushTag(entry.getKey(), entry.getValue());
			}
		}
		return this;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.streams.tracing.ITracer#popTag()
	 */
	@Override
	public ITracer popTag() {
		tagStack.pop();
		return this;
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.streams.tracing.ITracer#popTags(int)
	 */
	@Override
	public ITracer popTags(final int n) {
		if(n<1) throw new IllegalArgumentException("Invalid number of tags to pop [" + n + "]");
		for(int i = 0; i < n; i++) {
			tagStack.pop();
		}
		return this;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.streams.tracing.ITracer#pushKeys(java.lang.String[])
	 */
	@Override
	public ITracer pushKeys(final String... keys) {
		if(keys != null) {
			for(String key: keys) {
				if(key==null) continue;
				final String k = key.trim();
				if(k.isEmpty()) continue;
				tagKeyStack.push(k);
			}
		}
		return this;
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.streams.tracing.ITracer#objectName(javax.management.ObjectName)
	 */
	@Override
	public ITracer objectName(final ObjectName objectName) {
		if(objectName==null) throw new IllegalArgumentException("The passed ObjectName was null");
		metricNameStack.clear();
		tagStack.clear();
		Collections.addAll(metricNameStack, StringHelper.splitString(objectName.getDomain(), '.', true));
		putTagStack(objectName.getKeyPropertyList());
		return this;
	}
	
	/**
	 * Puts a tag stack
	 * @param tags the tags to put
	 */
	protected void putTagStack(final Map<String, String> tags) {
		for(Map.Entry<String, String> entry: tags.entrySet()) {
			final String key = entry.getKey();
			final String value = entry.getValue();
			if(!tagStack.isEmpty()) {
				checkReplace(key);
			}
			tagStack.push(new String[]{key, value});
		}
	}
	
	/**
	 * Checks the replacement of a key
	 * @param key the key to check
	 */
	protected void checkReplace(final String key) {
		for(final Iterator<String[]> iter = tagStack.iterator(); iter.hasNext();) {
			final String[] pair = iter.next();
			if(key.equals(pair[0])) {
				iter.remove();
				break;
			}
		}
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.streams.tracing.ITracer#objectName(java.lang.String)
	 */
	@Override
	public ITracer objectName(String objectName) {
		if(objectName==null || objectName.trim().isEmpty()) throw new IllegalArgumentException("The passed ObjectName was null or empty");
		try {
			objectName(new ObjectName(objectName));
		} catch (Exception ex) {
			throw new IllegalArgumentException("The passed ObjectName [" + objectName + "] was invalid", ex);
		}
		return this;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.streams.tracing.ITracer#popKeys()
	 */
	@Override
	public ITracer popKeys() {
		tagKeyStack.clear();
		return this;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.streams.tracing.ITracer#popKeys(int)
	 */
	@Override
	public ITracer popKeys(final int n) {
		for(int i = 0; i < n; i++) {
			tagKeyStack.pop();
		}
		return this;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.streams.tracing.ITracer#popKey()
	 */
	@Override
	public ITracer popKey() {
		tagKeyStack.pop();
		return this;
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.streams.tracing.ITracer#popTag(java.lang.String[])
	 */
	@Override
	public ITracer popTag(final String... tagKeys) {
		if(tagKeys!=null && tagKeys.length!=0) {
			for(String tagKey: tagKeys) {
				if(tagKey==null) continue;
				final String _tagKey = tagKey.trim();
				for(Iterator<String[]> iter = tagStack.iterator(); iter.hasNext();) {
					final String[] pair = iter.next();
					if(pair[0].equals(_tagKey)) {
						iter.remove();
						break;
					}
				}
			}
		}
		return this;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.streams.tracing.ITracer#pushTs(long)
	 */
	@Override
	public ITracer pushTs(final long timestamp) {
		msTime = timestamp;
		return this;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.streams.tracing.ITracer#popTs()
	 */
	@Override
	public ITracer popTs() {
		msTime = null;
		return this;
	}
	
	
//	private ITrace _trace(final long value, final long timestamp, final String...tagValues) {
//		return traceOut(TraceFactory.make(value, timestamp, metricId(tagValues)));	
//	}
//	
//	private ITrace _trace(final long value, final String...tagValues) {
//		return _trace(value, msTime==null ? System.currentTimeMillis() : msTime, tagValues);
//	}
//	
//	private ITrace _trace(final double value, final long timestamp, final String...tagValues) {
//		return traceOut(TraceFactory.make(value, timestamp, metricId(tagValues)));	
//	}
//	
//	private ITrace _trace(final double value, final String...tagValues) {
//		return _trace(value, msTime==null ? System.currentTimeMillis() : msTime, tagValues);
//	}
	
	private void traceOut(final long timestamp, final long value, final String...tagValues) {
		final int pos = outBuffer.writerIndex();
		try {			
			modified = false;
			bufferedEvents++;
			totalEvents++;
			inqCount++;
			traceMeter.mark();
			final SortedMap<String, String> outTags;
			if(tagValues!=null && tagValues.length > 0) {
				outTags = new TreeMap<String, String>(TagKeySorter.INSTANCE);
				outTags.putAll(tags);
				outTags.putAll(buildTags(tagValues));
			} else {
				outTags = tags;
			}
			tags.putAll(appHostTags);
			StreamedMetricValue.write(outBuffer, null, buildMetricName(), timestamp, value, tags);
		} catch (Exception ex) {
			outBuffer.writerIndex(pos);
			bufferedEvents--;
			totalEvents--;
			inqCount--;
			traceMeter.mark(-1L);			
			throw new RuntimeException("Failed to trace", ex);
		} finally {
			if(bufferedEvents==maxTracesBeforeFlush) {
				flush();
			}
		}
	}

	private void traceOut(final double value, final long timestamp, final String...tagValues) {
		final int pos = outBuffer.writerIndex();
		try {			
			modified = false;
			bufferedEvents++;
			totalEvents++;
			inqCount++;
			traceMeter.mark();
			final SortedMap<String, String> outTags;
			if(tagValues!=null && tagValues.length > 0) {
				outTags = new TreeMap<String, String>(TagKeySorter.INSTANCE);
				outTags.putAll(tags);
				outTags.putAll(buildTags(tagValues));
			} else {
				outTags = tags;
			}
			tags.putAll(appHostTags);
			StreamedMetricValue.write(outBuffer, null, buildMetricName(), timestamp, value, tags);
		} catch (Exception ex) {
			outBuffer.writerIndex(pos);
			bufferedEvents--;
			totalEvents--;
			inqCount--;
			traceMeter.mark(-1L);			
			throw new RuntimeException("Failed to trace", ex);
		} finally {
			if(bufferedEvents==maxTracesBeforeFlush) {
				flush();
			}
		}
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.streams.tracing.ITracer#trace(long, long, java.lang.String[])
	 */
	@Override
	public ITracer trace(final long value, final long timestamp, final String... tagValues) {
		if(!traceActive) { traceActive=true; return this; }
		traceOut(value, timestamp, tagValues);
		return this;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.streams.tracing.ITracer#trace(double, long, java.lang.String[])
	 */
	@Override
	public ITracer trace(final double value, final long timestamp, final String... tagValues) {
		if(!traceActive) { traceActive=true; return this; }
		traceOut(value, timestamp, tagValues);
		return this;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.streams.tracing.ITracer#dtrace(long, long, java.lang.String[])
	 */
	@Override
	public ITracer dtrace(final long value, final long timestamp, final String... tagValues) {
		if(!traceActive) { traceActive=true; return this; }
		final Long delta = DeltaManager.getInstance().delta(buildMetricName() + buildTags(tagValues).toString(), value);
		if(delta!=null) {
			trace(delta, timestamp, tagValues);
		}
		return this;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.streams.tracing.ITracer#dtrace(double, long, java.lang.String[])
	 */
	@Override
	public ITracer dtrace(final double value, final long timestamp, final String... tagValues) {
		if(!traceActive) { traceActive=true; return this; }
		final Double delta = DeltaManager.getInstance().delta(buildMetricName() + buildTags(tagValues).toString(), value);
		if(delta!=null) {
			trace(delta, timestamp, tagValues);
		}
		return this;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.streams.tracing.ITracer#trace(long, java.lang.String[])
	 */
	@Override
	public ITracer trace(final long value, final String... tagValues) {
		if(!traceActive) { traceActive=true; return this; }		
		return trace(value, msTime==null ? System.currentTimeMillis() : msTime, tagValues);		
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.streams.tracing.ITracer#trace(double, java.lang.String[])
	 */
	@Override
	public ITracer trace(final double value, final String... tagValues) {
		if(!traceActive) { traceActive=true; return this; }
		return trace(value, msTime==null ? System.currentTimeMillis() : msTime, tagValues);
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.streams.tracing.ITracer#dtrace(long, java.lang.String[])
	 */
	@Override
	public ITracer dtrace(final long value, final String... tagValues) {
		if(!traceActive) { traceActive=true; return this; }
		final Long delta = DeltaManager.getInstance().delta(buildMetricName() + buildTags(tagValues).toString(), value);
		if(delta!=null) {
			trace(delta, msTime==null ? System.currentTimeMillis() : msTime, tagValues);
		}
		return this;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.streams.tracing.ITracer#dtrace(double, java.lang.String[])
	 */
	@Override
	public ITracer dtrace(final double value, final String... tagValues) {
		if(!traceActive) { traceActive=true; return this; }
		final Double delta = DeltaManager.getInstance().delta(buildMetricName() + buildTags(tagValues).toString(), value);
		if(delta!=null) {
			trace(delta, msTime==null ? System.currentTimeMillis() : msTime, tagValues);
		}
		return this;
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.streams.tracing.ITracer#getLastDoubleDelta(java.lang.Number[], java.lang.String[])
	 */
	@Override
	public ITracer getLastDoubleDelta(final Number[] slot, final String... tagValues) {
		final Double delta = DeltaManager.getInstance().doubleDeltav(buildMetricName() + buildTags(tagValues).toString());
		if(delta!=null) slot[0] = delta;
		return this;
	}
	
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.streams.tracing.ITracer#getLastLongDelta(java.lang.Number[], java.lang.String[])
	 */
	@Override
	public ITracer getLastLongDelta(final Number[] slot, final String... tagValues) {
		final Long delta = DeltaManager.getInstance().longDeltav(buildMetricName() + buildTags(tagValues).toString());
		if(delta!=null) slot[0] = delta;
		return this;
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.streams.tracing.ITracer#getLastIntegerDelta(java.lang.Number[], java.lang.String[])
	 */
	@Override
	public ITracer getLastIntegerDelta(final Number[] slot, final String... tagValues) {
		final Integer delta = DeltaManager.getInstance().intDeltav(buildMetricName() + buildTags(tagValues).toString());
		if(delta!=null) slot[0] = delta;
		return this;
	}
	
	




	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.streams.tracing.ITracer#flush()
	 */
	@Override
	public ITracer flush() {
		final ElapsedTime et = SystemClock.startClock();
		outBuffer.setInt(COUNT_OFFSET, bufferedEvents);
		final ByteBuf bufferCopy = BufferManager.getInstance().buffer(outBuffer.readableBytes());
		bufferCopy.writeBytes(outBuffer);
		outBuffer.resetReaderIndex();
		outBuffer.writerIndex(START_DATA_OFFSET);
		final int finalCount = bufferedEvents;
		flushPool.execute(new Runnable(){
			@Override
			public void run() {
				writer.onMetrics(bufferCopy);
				log.info(et.printAvg("Metrics flushed", finalCount));
			}
		});
		bufferedEvents = 0;
		return this;
		
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.streams.tracing.ITracer#getMaxTracesBeforeFlush()
	 */
	@Override
	public int getMaxTracesBeforeFlush() {
		return maxTracesBeforeFlush;
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.streams.tracing.ITracer#setMaxTracesBeforeFlush(int)
	 */
	@Override
	public ITracer setMaxTracesBeforeFlush(final int maxTracesBeforeFlush) {
		if(maxTracesBeforeFlush < 1) throw new IllegalArgumentException("Invalid max traces [" + maxTracesBeforeFlush + "]. Must be > 0");
		this.maxTracesBeforeFlush = maxTracesBeforeFlush;
		if(bufferedEvents >= maxTracesBeforeFlush) {
			flush();
		}		
		return this;
	}
	
//	/**
//	 * {@inheritDoc}
//	 * @see com.heliosapm.streams.tracing.ITracer#trace(com.heliosapm.tsdbex.tracing.model.IAnnotation)
//	 */
//	@Override
//	public ITracer trace(final IAnnotation annotation) {
//		if(!traceActive) { traceActive=true; return this; }
//		if(annotation==null) throw new IllegalArgumentException("The passed annotation was null");
//		final int pos = outBuffer.writerIndex();
//		try {
//			traceOut.writeByte(StoreRecordType.ANNOTATION.byteOrdinal);
//			traceOut.writeInt(annotation.length());
//			log.info("Annotation Length:{} bytes", annotation.length());
//			AnnotationFactory.write(traceOut, annotation);
//			traceOut.flush();
//			bufferedEvents++;
//			totalEvents++;
//			modified = false;
//			return this;
//		} catch (Exception ex) {
//			outBuffer.writerIndex(pos);
//			throw new RuntimeException("Failed to trace annotation", ex);
//		} finally {
//			if(bufferedEvents==maxTracesBeforeFlush) {
//				flush();
//			}
//		}		
//	}
	
//	/**
//	 * {@inheritDoc}
//	 * @see com.heliosapm.streams.tracing.ITracer#trace(com.heliosapm.tsdbex.tracing.model.AnnotationFactory.AnnotationBuilder)
//	 */
//	@Override
//	public ITracer trace(final AnnotationBuilder annotationBuilder) {
//		if(!traceActive) { traceActive=true; return this; }
//		if(annotationBuilder!=null) {
//			trace(annotationBuilder.build());
//		}
//		return this;
//	}
//	
//	/**
//	 * {@inheritDoc}
//	 * @see com.heliosapm.streams.tracing.ITracer#trace(java.lang.String, long, long, long, java.lang.String, java.util.Map)
//	 */
//	@Override
//	public ITracer trace(final String description, final long startTime, final long endTime, final long metricNameId, final String notes, final Map<String, String> custom) {
//		if(!traceActive) { traceActive=true; return this; }
//		trace(
//			AnnotationFactory.builder(startTime, description)
//				.endTime(endTime)
//				.metricName(metricNameId)
//				.notes(notes)
//				.custom(custom)
//				.build()
//		);
//		return this;
//	}
	
//	/**
//	 * {@inheritDoc}
//	 * @see com.heliosapm.streams.tracing.ITracer#annotationBuilder(java.lang.String, long)
//	 */
//	@Override
//	public AnnotationBuilder annotationBuilder(final String description, final long startTime) {
//		return AnnotationFactory.builder(startTime, description, this);
//	}
//	
//	/**
//	 * {@inheritDoc}
//	 * @see com.heliosapm.streams.tracing.ITracer#annotationBuilder(java.lang.String)
//	 */
//	@Override
//	public AnnotationBuilder annotationBuilder(final String description) {
//		return AnnotationFactory.builder(System.currentTimeMillis(), description, this);
//	}
	

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.streams.tracing.ITracer#traceMeta()
	 */
	@Override
	public ITracer traceMeta() {
		if(!traceActive) { traceActive=true; return this; }
		// TODO Auto-generated method stub
		return null;
	}
	

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.streams.tracing.ITracer#dump()
	 */
	@Override
	public String dump() {
		final StringBuilder b = new StringBuilder("Tracer State: [");
		b.append("\n\tMetricName:").append(metricNameStack)
		.append("\n\tTags:").append(tagStackToString())
		.append("\n\tTagKeys:").append(tagKeyStack)
		.append("\n\tTimestamp:").append(msTime)
		.append("\n\tFlushCount:").append(maxTracesBeforeFlush)
		.append("\n\tBufferedEvents:").append(bufferedEvents)
		.append("\n\tBuffer:").append(outBuffer)
		.append("\n\tTotalTraces:").append(totalEvents);
		return b.append("\n]").toString();
	}
	
	protected String tagStackToString() {
		if(tagStack.isEmpty()) return "";
		final StringBuilder b = new StringBuilder("{");
		for(String[] pair: tagStack) {
			if(b.length() > 1) {
				b.append(", ");
			}
			b.append(pair[0]).append("=").append(pair[0]);
		}
		return b.append("}").toString();
	}

	

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.streams.tracing.ITracer#getBufferedEvents()
	 */
	@Override
	public int getBufferedEvents() {
		return bufferedEvents;
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.streams.tracing.ITracer#getTotalEvents()
	 */
	@Override
	public long getTotalEvents() {
		return totalEvents;
	}
	
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.streams.tracing.ITracer#active(boolean)
	 */
	@Override
	public ITracer active(boolean active) {
		traceActive = active;
		return this;
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.streams.tracing.ITracer#getSentEventCount()
	 */
	@Override
	public long getSentEventCount() {
		final long x = inqCount;
		inqCount = 0;
		return x;
	}

}
