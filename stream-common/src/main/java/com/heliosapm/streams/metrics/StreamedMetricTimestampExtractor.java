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
package com.heliosapm.streams.metrics;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.streams.processor.TimestampExtractor;

/**
 * <p>Title: StreamedMetricTimestampExtractor</p>
 * <p>Description: Timestamp extractor for StreamedMetrics supporting a few different in-flight types </p> 
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.streams.metrics.StreamedMetricTimestampExtractor</code></p>
 */

public class StreamedMetricTimestampExtractor implements TimestampExtractor {


	@Override
	public long extract(final ConsumerRecord<Object, Object> record) {
		try {
			final Object v = record.value();
			if(v==null) return System.currentTimeMillis();
			if(v instanceof StreamedMetric) {
				final StreamedMetric sm = (StreamedMetric)v;
				//System.err.println("[" + Thread.currentThread().getName() + "] ----EX TS:" + sm.getTimestamp());
				return sm.getTimestamp();
			} else if(v instanceof CharSequence) {
				return StreamedMetric.fromString(((CharSequence)v).toString().trim()).getTimestamp();
			} else if(v instanceof long[]) {
				final long[] arr = (long[])v;			
				return arr.length==2 ? arr[0] : arr[1];
			}
			throw new Exception("Unexpected Type: [" + 
				v.getClass().getName() + "]");
		} catch (Throwable t) {
			return System.currentTimeMillis();
		}
		
	}

}
