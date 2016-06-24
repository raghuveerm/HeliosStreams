/**
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
 */
package com.heliosapm.streams.metrics.processor;

import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.state.KeyValueStore;

import com.heliosapm.streams.metrics.StreamedMetric;
import com.heliosapm.streams.metrics.ValueType;

/**
 * <p>Title: StreamedMetricAccumulator</p>
 * <p>Description: </p> 
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.streams.metrics.processor.StreamedMetricAccumulator</code></p>
 */

public class StreamedMetricAccumulator extends AbstractStreamedMetricProcessor {
	/** The metric accumulator store name */
	public static final String ACC_STORE = "metricAccumulator";
	/** An array of the store names */
	private static final String[] DATA_STORES = {ACC_STORE};
	
	/** The metric accumulator store */
	protected KeyValueStore<String, Long> accumulatorStore;
 
	
	
	
	/**
	 * Creates a new AbstractStreamedMetricProcessor
	 * @param period The punctuation period in ms.
	 * @param sink The name of the sink topic name this processor published to
	 * @param sources The names of source topics name this processor consumes from 
	 */
	public StreamedMetricAccumulator(final long period, final String sink, final String[] sources) {
		super(ValueType.A, period, sink, DATA_STORES, sources);
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.streams.metrics.processor.AbstractStreamedMetricProcessor#init(org.apache.kafka.streams.processor.ProcessorContext)
	 */
	@Override
	public void init(final ProcessorContext context) {
		super.init(context);
		accumulatorStore = (KeyValueStore<String, Long>)context.getStateStore(ACC_STORE);
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.streams.metrics.processor.AbstractStreamedMetricProcessor#doProcess(java.lang.String, com.heliosapm.streams.metrics.StreamedMetric)
	 */
	@Override
	protected void doProcess(final String key, final StreamedMetric value) {
		
	}	

}
